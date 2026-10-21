package health.cogniware.analysis

/**
 * Swappable inference backend. Phase 1 = RuleBasedEngine (already exists as RiskScoreEngine).
 * Phase 2 = TFLiteEngine (stub, not yet implemented).
 * PassiveSensingService always calls through this interface — never directly to RiskScoreEngine.
 */
interface InferenceEngine {
    /**
     * Compute a risk score from the given input.
     * Implementations must be thread-safe and non-blocking (may be called on any thread).
     */
    fun infer(input: RiskInput): RiskScore

    /** Human-readable name for logging/debug overlay */
    val engineName: String
}

/** Phase 1 — wraps the existing RiskScoreEngine */
class RuleBasedEngine : InferenceEngine {
    override val engineName = "RuleBasedEngine v1"
    override fun infer(input: RiskInput): RiskScore = RiskScoreEngine.compute(input)
}

/**
 * Phase 2 stub — will load a TFLite model from assets/cogniware_risk_model.tflite
 * Not yet implemented; falls back to RuleBasedEngine.
 */
class TFLiteEngine(private val fallback: InferenceEngine = RuleBasedEngine()) : InferenceEngine {
    override val engineName = "TFLiteEngine (stub — using fallback)"
    // TODO: load TFLite interpreter from assets, run model, return RiskScore
    override fun infer(input: RiskInput): RiskScore = fallback.infer(input)
}

/** Singleton factory — swap the engine here without touching service code */
object InferenceEngineFactory {
    @Volatile private var instance: InferenceEngine = RuleBasedEngine()
    fun get(): InferenceEngine = instance
    fun set(engine: InferenceEngine) { instance = engine }
}
