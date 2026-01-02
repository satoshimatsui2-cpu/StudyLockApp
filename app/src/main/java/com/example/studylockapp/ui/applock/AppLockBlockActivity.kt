package com.example.studylockapp.ui.applock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.studylockapp.LearningActivity
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointHistoryEntity
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.UnlockHistoryEntity
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
    private lateinit var imageGeorge: ImageView

    private var lockedPkg: String = ""
    private var lockedLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_app_lock_block)

            settings = AppSettings(this)
            pointManager = PointManager(this)
            imageGeorge = findViewById(R.id.image_george_reaction)

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

            // アプリアイコンを表示する処理を追加
            val iconView = findViewById<ImageView>(R.id.icon_blocked_app)
            try {
                val icon = packageManager.getApplicationIcon(lockedPkg)
                iconView.setImageDrawable(icon)
            } catch (e: Exception) {
                // アイコン取得失敗時はデフォルト画像を表示するなどのフォールバック（XMLのtools:srcで代用）
                Log.w(TAG, "Failed to load app icon for $lockedPkg", e)
            }

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

            fun updateGeorgeImage(points: Int) {
                val imageResId = when {
                    points >= 500 -> R.drawable.george_7 // george_8 がなかったので 7 に
                    points >= 400 -> R.drawable.george_6
                    points >= 300 -> R.drawable.george_5
                    points >= 200 -> R.drawable.george_4
                    points >= 100 -> R.drawable.george_3
                    points >= 10 -> R.drawable.george_2
                    else -> R.drawable.george_1
                }
                imageGeorge.setImageResource(imageResId)
            }

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

                // Georgeの画像更新
                updateGeorgeImage(usePt)
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
        val nowSec = Instant.now().epochSecond
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

        // 新しい履歴テーブルにも書き込む
        db.unlockHistoryDao().insert(
            UnlockHistoryEntity(
                packageName = lockedPkg,
                usedPoints = usePoints,
                unlockDurationSec = durationSec,
                unlockedAt = nowSec
            )
        )

        // app_unlocks に期限を書き込む（秒）
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