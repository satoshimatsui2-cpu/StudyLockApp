package com.example.studylockapp.ui.applock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.studylockapp.LearningActivity
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointHistoryEntity
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.db.AppUnlockEntity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil

class AppLockBlockActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AppLockBlockActivity"
    }

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var settings: AppSettings
    private lateinit var pointManager: PointManager

    private var lockedPkg: String = ""
    private var lockedLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_app_lock_block)

            settings = AppSettings(this)
            pointManager = PointManager(this)

            lockedPkg = intent.getStringExtra("lockedPackage") ?: ""
            lockedLabel = intent.getStringExtra("lockedLabel")?.takeIf { it.isNotBlank() } ?: run {
                try {
                    val pm = packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(lockedPkg, 0)).toString()
                } catch (_: Exception) {
                    lockedPkg
                }
            }
            findViewById<TextView>(R.id.text_block_app).text = lockedLabel

            val textPointsInfo = findViewById<TextView>(R.id.text_points_info)
            val textUnlockTime = findViewById<TextView>(R.id.text_unlock_time)
            val editPoints = findViewById<TextInputEditText>(R.id.edit_points_to_use)

            val buttonClear = findViewById<MaterialButton>(R.id.button_points_clear)
            val buttonPlus10 = findViewById<MaterialButton>(R.id.button_points_plus_10)
            val buttonPlus100 = findViewById<MaterialButton>(R.id.button_points_plus_100)
            val buttonUnlock = findViewById<MaterialButton>(R.id.button_unlock_with_points)
            val buttonBack = findViewById<MaterialButton>(R.id.button_back_to_learning)

            val currentPt = pointManager.getTotal()
            val minPer10Pt = settings.getUnlockMinutesPer10Pt() // 1〜10 分

            // 初期値は 0
            editPoints.setText("0")

            fun recalcAndRender() {
                val raw = editPoints.text?.toString()?.toIntOrNull() ?: 0
                val usePt = if (currentPt > 0) raw.coerceIn(0, currentPt) else 0
                val durationMin = if (usePt > 0) (usePt * minPer10Pt) / 10f else 0f
                val durationSec = ceil(durationMin * 60f).toLong() // 0.1分=6秒刻み

                textPointsInfo.text = getString(
                    R.string.block_points_info,
                    currentPt,
                    usePt,
                    durationMin.toDouble(), // 小数1桁表示
                    minPer10Pt
                )
                textUnlockTime.text = getString(
                    R.string.block_unlocked_message,  // 表示用に流用（%1$.1f分）
                    durationMin.toDouble()
                )
                buttonUnlock.isEnabled = usePt in 1..currentPt && durationSec > 0
            }

            recalcAndRender()

            buttonClear.setOnClickListener {
                editPoints.setText("0")
                recalcAndRender()
            }
            buttonPlus10.setOnClickListener {
                addPoints(editPoints, 10, currentPt)
                recalcAndRender()
            }
            buttonPlus100.setOnClickListener {
                addPoints(editPoints, 100, currentPt)
                recalcAndRender()
            }

            editPoints.setOnFocusChangeListener { _, _ -> recalcAndRender() }
            editPoints.addTextChangedListener { recalcAndRender() }

            buttonUnlock.setOnClickListener {
                val raw = editPoints.text?.toString()?.toIntOrNull() ?: 0
                val usePt = if (currentPt > 0) raw.coerceIn(1, currentPt) else 0
                if (usePt <= 0) {
                    Snackbar.make(it, getString(R.string.block_invalid_points), Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val durationMin = (usePt * minPer10Pt) / 10f
                val durationSec = ceil(durationMin * 60f).toLong()
                if (durationSec <= 0) {
                    Snackbar.make(it, getString(R.string.block_invalid_points), Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                uiScope.launch {
                    doUnlock(usePt, durationSec, durationMin)
                }
            }

            buttonBack.setOnClickListener {
                startActivity(Intent(this, LearningActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                finish()
            }

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to init AppLockBlockActivity", t)
            // クラッシュループを防ぐ
            finishAndRemoveTask()
        }
    }

    private fun addPoints(edit: TextInputEditText, delta: Int, maxPt: Int) {
        val current = edit.text?.toString()?.toIntOrNull() ?: 0
        val newVal = (current + delta).coerceIn(0, maxPt)
        edit.setText(newVal.toString())
    }

    private suspend fun doUnlock(usePoints: Int, durationSec: Long, durationMin: Float) {
        // ポイント減算 & 履歴追加
        pointManager.add(-usePoints)
        val db = AppDatabase.getInstance(this)
        val zone = settings.getAppZoneId()
        val today = LocalDate.now(zone).toEpochDay()
        db.pointHistoryDao().insert(
            PointHistoryEntity(
                mode = "unlock",
                dateEpochDay = today,
                delta = -usePoints
            )
        )

        // app_unlocks に期限を書き込む（秒）
        val nowSec = Instant.now().epochSecond
        val untilSec = nowSec + durationSec
        db.appUnlockDao().upsert(
            AppUnlockEntity(
                packageName = lockedPkg,
                unlockedUntilSec = untilSec
            )
        )

        // 画面を閉じて元アプリに戻る
        runOnUiThread {
            Snackbar.make(
                findViewById(android.R.id.content),
                getString(R.string.block_unlocked_message, durationMin.toDouble()),
                Snackbar.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}