package health.cogniware.keyboard

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import health.cogniware.data.models.*
import health.cogniware.analysis.BaselineEngine
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.sensors.ContextProvider
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.*

/**
 * CogniwareKeyboardService
 *
 * Android Input Method Service that captures ONLY keystroke timing metadata.
 * NEVER captures, stores, logs, or transmits key content or text.
 *
 * What we collect per keystroke:
 *   - Key-down timestamp (ms)
 *   - Key-up timestamp (ms) → flight time = key-up minus key-down
 *   - Inter-keystroke interval = this key-down minus previous key-down
 *   - Whether the key is a backspace (for error rate)
 *   - Whether the key is a space/punctuation (for word-boundary detection)
 *
 * What we NEVER collect:
 *   - Which key was pressed
 *   - Any text content
 *   - App being typed in (package name)
 *   - Any identifying information beyond timing
 */
class CogniwareKeyboardService : InputMethodService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Timing state ──
    private var sessionStartMs: Long = 0L
    private var lastKeyDownMs: Long = 0L
    private var lastKeyUpMs: Long = 0L
    private var lastKeyWasSpace: Boolean = false

    // ── Accumulated timing lists for this session ──
    private val ikiList = mutableListOf<Double>()           // inter-keystroke intervals
    private val flightTimeList = mutableListOf<Double>()    // key-down to key-up durations
    private val wordBoundaryPauses = mutableListOf<Double>()// IKI before first letter of word
    private val withinWordPauses = mutableListOf<Double>()  // IKI within a word
    private var totalKeystrokes = 0
    private var backspaceCount = 0
    private var correctionBurstActive = false
    private var correctionBurstLength = 0
    private var correctionBurstCount = 0
    private var longPauseCount = 0                          // IKI > 800ms

    // ── Session management ──
    private val SESSION_GAP_MS = 30_000L        // 30s gap = new session
    private val MIN_SESSION_KEYSTROKES = 20     // ignore micro-sessions
    private var sessionId = UUID.randomUUID().toString()

    private lateinit var db: CogniwareDatabase
    private lateinit var contextProvider: ContextProvider
    private lateinit var baselineEngine: BaselineEngine

    override fun onCreate() {
        super.onCreate()
        db = CogniwareDatabase.getInstance(applicationContext)
        contextProvider = ContextProvider.getInstance(applicationContext)
        baselineEngine = BaselineEngine(db.personalBaselineDao())
        resetSession()
    }

    override fun onCreateInputView(): View {
        // Return null — we don't render our own keyboard UI.
        // This service sits alongside the user's existing keyboard
        // and captures timing events from the system IME.
        // In production, this is implemented as a transparent overlay
        // service rather than a replacement keyboard.
        return View(this)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val now = SystemClock.elapsedRealtime()
        if (now - lastKeyDownMs > SESSION_GAP_MS && totalKeystrokes >= MIN_SESSION_KEYSTROKES) {
            flushSession()
        }
        if (!restarting) {
            resetSession()
            sessionStartMs = now
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        val now = SystemClock.elapsedRealtime()
        if (totalKeystrokes >= MIN_SESSION_KEYSTROKES) {
            flushSession()
        }
    }

    /**
     * onKeyDown — record key-down timestamp.
     * We do NOT record which key this is.
     * We only record whether it is a backspace or space
     * (these are structural punctuation events, not content).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isBackspace = keyCode == KeyEvent.KEYCODE_DEL
        val isSpace = keyCode == KeyEvent.KEYCODE_SPACE ||
                      keyCode == KeyEvent.KEYCODE_ENTER

        // ── IKI calculation ──
        if (lastKeyDownMs > 0L) {
            val iki = (now - lastKeyDownMs).toDouble()

            // Only count IKIs in a plausible human range (50ms–5000ms)
            if (iki in 50.0..5000.0) {
                ikiList.add(iki)

                // Long pause detection
                if (iki > 800.0) longPauseCount++

                // Word-boundary vs within-word pause
                if (lastKeyWasSpace && !isSpace && !isBackspace) {
                    wordBoundaryPauses.add(iki)
                } else if (!lastKeyWasSpace && !isSpace && !isBackspace) {
                    withinWordPauses.add(iki)
                }
            }
        }

        // ── Error / correction tracking ──
        if (isBackspace) {
            backspaceCount++
            correctionBurstLength++
            if (correctionBurstLength >= 3 && !correctionBurstActive) {
                correctionBurstActive = true
                correctionBurstCount++
            }
        } else {
            if (correctionBurstActive) correctionBurstActive = false
            correctionBurstLength = 0
        }

        lastKeyDownMs = now
        lastKeyWasSpace = isSpace
        totalKeystrokes++

        return super.onKeyDown(keyCode, event)
    }

    /**
     * onKeyUp — record flight time (key-down to key-up duration).
     * Bradykinesia marker — Parkinson's patients have prolonged flight times.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastKeyDownMs > 0L) {
            val flightTime = (now - lastKeyDownMs).toDouble()
            // Plausible flight time range: 30ms–500ms
            if (flightTime in 30.0..500.0) {
                flightTimeList.add(flightTime)
            }
        }
        lastKeyUpMs = now
        return super.onKeyUp(keyCode, event)
    }

    // ── Feature computation ──

    private fun flushSession() {
        if (ikiList.size < 10) { resetSession(); return }

        val session = computeSession()
        serviceScope.launch {
            db.keystrokeDao().insert(session)
            baselineEngine.updateFromKeystroke(session)
        }
        resetSession()
    }

    private fun computeSession(): KeystrokeSession {
        val ikiMean = ikiList.average()
        val ikiStd = ikiList.standardDeviation()
        val ikiCv = if (ikiMean > 0) (ikiStd / ikiMean) * 100.0 else 0.0
        val sortedIki = ikiList.sorted()
        val ikiP10 = sortedIki[(sortedIki.size * 0.10).toInt().coerceAtLeast(0)]
        val ikiP90 = sortedIki[(sortedIki.size * 0.90).toInt().coerceAtMost(sortedIki.size - 1)]

        val flightMean = flightTimeList.let { if (it.isEmpty()) 0.0 else it.average() }
        val flightCv = if (flightMean > 0)
            (flightTimeList.standardDeviation() / flightMean) * 100.0 else 0.0

        val sessionDurationMs = SystemClock.elapsedRealtime() - sessionStartMs
        val estimatedWpm = if (sessionDurationMs > 0)
            (totalKeystrokes / 5.0) / (sessionDurationMs / 60_000.0) else 0.0

        val pausePerLetter = if (withinWordPauses.isEmpty()) 0.0 else withinWordPauses.average()
        val evenIkis = ikiList.filterIndexed { i, _ -> i % 2 == 0 }
        val oddIkis  = ikiList.filterIndexed { i, _ -> i % 2 == 1 }
        val bilateralAsymmetry = if (evenIkis.isNotEmpty() && oddIkis.isNotEmpty()) {
            val mE = evenIkis.average(); val mO = oddIkis.average()
            if ((mE + mO) > 0) kotlin.math.abs(mE - mO) / ((mE + mO) / 2.0) else 0.0
        } else 0.0
        val backspaceRate = if (totalKeystrokes > 0)
            (backspaceCount.toDouble() / totalKeystrokes) * 100.0 else 0.0

        return KeystrokeSession(
            sessionId = sessionId,
            patientId = getPatientId(),
            startMs = sessionStartMs,
            endMs = SystemClock.elapsedRealtime(),
            ikiMeanMs = ikiMean,
            ikiCvPercent = ikiCv,
            ikiP10Ms = ikiP10,
            ikiP90Ms = ikiP90,
            flightTimeMeanMs = flightMean,
            flightTimeCvPercent = flightCv,
            backspaceRatePercent = backspaceRate,
            correctionBurstCount = correctionBurstCount,
            wordBoundaryPauseMeanMs = wordBoundaryPauses.let { if (it.isEmpty()) 0.0 else it.average() },
            withinWordPauseMeanMs = withinWordPauses.let { if (it.isEmpty()) 0.0 else it.average() },
            longPauseCount = longPauseCount,
            pausePerLetterMs = pausePerLetter,
            bilateralAsymmetryIndex = bilateralAsymmetry,
            estimatedWpm = estimatedWpm,
            totalKeystrokes = totalKeystrokes,
            context = contextProvider.currentContext(),
            syncedToCloud = false
        )
    }

    private fun resetSession() {
        sessionId = UUID.randomUUID().toString()
        sessionStartMs = SystemClock.elapsedRealtime()
        lastKeyDownMs = 0L
        lastKeyUpMs = 0L
        lastKeyWasSpace = false
        ikiList.clear()
        flightTimeList.clear()
        wordBoundaryPauses.clear()
        withinWordPauses.clear()
        totalKeystrokes = 0
        backspaceCount = 0
        correctionBurstActive = false
        correctionBurstLength = 0
        correctionBurstCount = 0
        longPauseCount = 0
    }

    private fun getPatientId(): String {
        return applicationContext
            .getSharedPreferences("cogniware_secure", MODE_PRIVATE)
            .getString("patient_id", "") ?: ""
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Extension functions ──
    private fun List<Double>.standardDeviation(): Double {
        if (size < 2) return 0.0
        val mean = average()
        return sqrt(map { (it - mean).pow(2) }.average())
    }
}
