package com.example.studylockapp.ui.applock

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.AdminSettingsActivity
import com.example.studylockapp.LearningActivity
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointHistoryEntity
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.db.AppUnlockEntity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

class AppLockBlockActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var settings: AppSettings
    private lateinit var pointManager: PointManager

    private var lockedPkg: String = ""
    private var lockedLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock_block)

        settings = AppSettings(this)
        pointManager = PointManager(this)

        lockedPkg = intent.getStringExtra("lockedPackage") ?: ""
        lockedLabel = intent.getStringExtra("lockedLabel") ?: lockedPkg

        findViewById<TextView>(R.id.text_block_app).text = lockedLabel

        val textPointsInfo = findViewById<TextView>(R.id.text_points_info)
        val btnUnlock = findViewById<MaterialButton>(R.id.button_unlock_with_points)

        // 所持/コスト表示（解除時間を 1 分固定に短縮）
        val cost = settings.getUnlockCostPoints10Min() // コストは従来の設定を利用
        val durationMin = 1 // ここを 10 → 1 分に短縮
        val currentPt = pointManager.getTotal()
        textPointsInfo.text = "所持: ${currentPt}pt / コスト: ${cost}pt（${durationMin}分解放）"
        btnUnlock.isEnabled = currentPt >= cost

        btnUnlock.setOnClickListener {
            if (pointManager.getTotal() < cost) {
                Snackbar.make(it, "ポイントが足りません", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            uiScope.launch {
                doUnlock(cost, durationMin)
            }
        }

        findViewById<MaterialButton>(R.id.button_back_to_learning).setOnClickListener {
            startActivity(Intent(this, LearningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
            finish()
        }
        findViewById<MaterialButton>(R.id.button_to_settings).setOnClickListener {
            startActivity(Intent(this, AdminSettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
            finish()
        }
        findViewById<MaterialButton>(R.id.button_close).setOnClickListener {
            finish()
        }
    }

    private suspend fun doUnlock(cost: Int, durationMin: Int) {
        // ポイント減算 & 履歴追加
        pointManager.add(-cost)
        val db = AppDatabase.getInstance(this)
        val zone = settings.getAppZoneId()
        val today = LocalDate.now(zone).toEpochDay()
        db.pointHistoryDao().insert(
            PointHistoryEntity(
                mode = "unlock",
                dateEpochDay = today,
                delta = -cost
            )
        )

        // app_unlocks に期限を書き込む
        val nowSec = Instant.now().epochSecond
        val untilSec = nowSec + durationMin * 60L
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
                "${durationMin}分 解放しました",
                Snackbar.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}