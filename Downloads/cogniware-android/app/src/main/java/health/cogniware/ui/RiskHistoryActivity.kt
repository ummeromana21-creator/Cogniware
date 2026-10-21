package health.cogniware.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import health.cogniware.R
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.data.models.RiskScoreRecord
import health.cogniware.databinding.ActivityRiskHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class RiskHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRiskHistoryBinding
    private val db by lazy { CogniwareDatabase.getInstance(this) }
    private val timeFmt = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiskHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val patientId = withContext(Dispatchers.IO) {
                db.profileDao().getAny()?.patientId ?: return@withContext null
            } ?: return@launch

            val since7d = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            val records = withContext(Dispatchers.IO) {
                db.riskScoreDao().getRecent(patientId, since7d)
            }

            if (records.isEmpty()) return@launch

            // Summary stats
            val avg = records.map { it.score }.average().toInt()
            val peak = records.maxOf { it.score }
            binding.tvAvgScore.text = avg.toString()
            binding.tvAvgScore.setTextColor(colorForScore(avg))
            binding.tvPeakScore.text = peak.toString()
            binding.tvPeakScore.setTextColor(colorForScore(peak))

            // Latest breakdown
            val latest = records.first()
            buildBreakdown(latest)

            // History list
            buildHistoryList(records.take(50))
        }
    }

    private fun buildBreakdown(record: RiskScoreRecord) {
        val container = binding.layoutBreakdown
        container.removeAllViews()

        // Parse componentScoresJson: "factor:0.123,factor2:0.456"
        val components = record.componentScoresJson
            .split(",")
            .mapNotNull {
                val parts = it.trim().split(":")
                if (parts.size == 2) parts[0] to (parts[1].toDoubleOrNull() ?: 0.0)
                else null
            }
            .sortedByDescending { it.second }

        if (components.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No component data available"
            tv.setTextColor(getColor(R.color.colorTextSecondary))
            container.addView(tv)
            return
        }

        components.forEach { (factor, value) ->
            val row = LayoutInflater.from(this)
                .inflate(android.R.layout.simple_list_item_2, container, false)

            // Reuse simple_list_item_2: text1 = label, text2 = bar + value
            val text1 = row.findViewById<TextView>(android.R.id.text1)
            val text2 = row.findViewById<TextView>(android.R.id.text2)

            text1.text = factor.replace("_", " ")
                .replaceFirstChar { it.uppercase() }
            text1.textSize = 13f

            val pct = (value * 100).toInt()
            val bar = "█".repeat((value * 20).toInt().coerceIn(0, 20))
            text2.text = "$bar  $pct%"
            text2.textSize = 12f
            text2.setTextColor(colorForValue(value))

            container.addView(row)
        }
    }

    private fun buildHistoryList(records: List<RiskScoreRecord>) {
        val container = binding.layoutHistory
        container.removeAllViews()

        records.forEach { record ->
            val card = android.widget.LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }

            val levelDot = View(this).apply {
                val size = (12 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (12 * resources.displayMetrics.density).toInt()
                    topMargin = (4 * resources.displayMetrics.density).toInt()
                }
                background = getDrawable(R.drawable.circle_status_green)?.also {
                    it.setTint(colorForScore(record.score))
                }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvTime = TextView(this).apply {
                text = timeFmt.format(Date(record.timestampMs))
                textSize = 12f
                setTextColor(getColor(R.color.colorTextSecondary))
            }
            val tvInfo = TextView(this).apply {
                text = "${record.level.replaceFirstChar { it.uppercase() }}  •  " +
                    record.dominantFactor.replace("_", " ")
                        .replaceFirstChar { it.uppercase() }
                textSize = 13f
            }

            val tvScore = TextView(this).apply {
                text = record.score.toString()
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(colorForScore(record.score))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            }

            textCol.addView(tvTime)
            textCol.addView(tvInfo)
            card.addView(levelDot)
            card.addView(textCol)
            card.addView(tvScore)

            // Divider
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 8 }
                setBackgroundColor(getColor(R.color.colorDivider))
            }

            container.addView(card)
            container.addView(divider)
        }
    }

    private fun colorForScore(score: Int) = when {
        score >= 81 -> getColor(R.color.colorRiskCritical)
        score >= 61 -> getColor(R.color.colorRiskHigh)
        score >= 31 -> getColor(R.color.colorRiskElevated)
        else        -> getColor(R.color.colorRiskLow)
    }

    private fun colorForValue(value: Double) = when {
        value >= 0.8 -> getColor(R.color.colorRiskCritical)
        value >= 0.6 -> getColor(R.color.colorRiskHigh)
        value >= 0.3 -> getColor(R.color.colorRiskElevated)
        else         -> getColor(R.color.colorTextSecondary)
    }
}
