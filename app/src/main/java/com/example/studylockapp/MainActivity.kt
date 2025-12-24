package com.example.studylockapp

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.service.AppLockAccessibilityService
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var gradeDropdown: MaterialAutoCompleteTextView
    private lateinit var buttonToLearning: Button

    private lateinit var textPointsTop: TextView
    private lateinit var textPointStatsTop: TextView

    // ★追加：級の統計（プルダウンの下に出す用）
    private lateinit var textGradeStatsTop: TextView
    private var gradeStatsMap: Map<String, String> = emptyMap()

    // DBのgradeと一致する値（例: "5"）
    private var selectedGradeKey: String? = null

    // アクセシビリティ誘導ダイアログ
    private var accessibilityDialog: AlertDialog? = null

    data class GradeSpinnerItem(
        val gradeKey: String,   // 例: "5", "2.5"
        val label: String       // 表示用: "5級", "準2級"
    ) {
        override fun toString(): String = label
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gradeDropdown = findViewById(R.id.spinner_grade_top)
        buttonToLearning = findViewById(R.id.button_to_learning)
        textPointsTop = findViewById(R.id.text_points_top)
        textPointStatsTop = findViewById(R.id.text_point_stats_top)
        textGradeStatsTop = findViewById(R.id.text_grade_stats_top) // activity_main.xml にある前提

        // カラー＆サイズ設定
        val orange = ContextCompat.getColor(this, R.color.sl_button_bg)
        val redHint = MaterialColors.getColor(gradeDropdown, com.google.android.material.R.attr.colorError)
        gradeDropdown.setTextColor(orange)                 // 選択後はオレンジ
        gradeDropdown.setHintTextColor(redHint)            // 未選択のヒントは赤
        gradeDropdown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f) // 約1.25倍

        buttonToLearning.isEnabled = false
        textGradeStatsTop.text = "復習 0 • 新規 0/0"

        // 右上の設定バッジ
        findViewById<ImageButton>(R.id.button_admin_settings_top)?.setOnClickListener {
            openAdminSettings()
        }
        // 旧「管理設定」ボタン（非表示でも安全）
        findViewById<Button>(R.id.button_admin_settings)?.setOnClickListener {
            openAdminSettings()
        }

        // ドロップダウン選択時：級だけ選ぶ。統計は別テキストに出す
        gradeDropdown.setOnItemClickListener { parent, _, position, _ ->
            val item = parent.getItemAtPosition(position) as? GradeSpinnerItem
            selectedGradeKey = item?.gradeKey
            buttonToLearning.isEnabled = (selectedGradeKey != null)

            val key = selectedGradeKey
            if (key != null) {
                textGradeStatsTop.text = gradeStatsMap[key] ?: "復習 0 • 新規 0/0"
            }
            // 選択後の文字色はオレンジのまま
            gradeDropdown.setTextColor(orange)
            gradeDropdown.setHintTextColor(redHint)
        }

        // 学習画面へ（gradeFilter は DB の grade と一致する値を渡す：例 "5"）
        buttonToLearning.setOnClickListener {
            val gradeSelected = selectedGradeKey ?: return@setOnClickListener
            startActivity(
                Intent(this, LearningActivity::class.java).apply {
                    putExtra("gradeFilter", gradeSelected)
                }
            )
        }

        // 一覧画面へ
        findViewById<Button>(R.id.button_to_list).setOnClickListener {
            startActivity(Intent(this, WordListActivity::class.java))
        }

        // 起動時にポイントとプルダウン表示を更新
        updatePointView()
        updateGradeDropdownLabels()
    }

    override fun onResume() {
        super.onResume()

        val settings = AppSettings(this)
        if (!settings.hasChosenTimeZone()) {
            startActivity(Intent(this, TimeZoneSetupActivity::class.java))
            return
        }

        // アクセシビリティOFFでロックが有効/対象がある場合は強制誘導
        maybeShowAccessibilityDialog()

        updatePointView()
        updateGradeDropdownLabels()
    }

    private fun openAdminSettings() {
        startActivity(Intent(this, AdminSettingsActivity::class.java))
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPointsTop.text = "ポイント: $total"

        lifecycleScope.launch { updatePointStats() }
    }

    /** 今日 / 前日比（タイムゾーン設定に従う） */
    private suspend fun updatePointStats() {
        val db = AppDatabase.getInstance(this@MainActivity)
        val histDao = db.pointHistoryDao()

        val zone = AppSettings(this@MainActivity).getAppZoneId()
        val today = LocalDate.now(zone).toEpochDay()
        val yesterday = today - 1

        val todaySum = histDao.getSumByDate(today)
        val yesterdaySum = histDao.getSumByDate(yesterday)
        val diff = todaySum - yesterdaySum
        val diffSign = if (diff >= 0) "+" else "-"
        val diffAbs = abs(diff)

        textPointStatsTop.text = "今日: $todaySum / 前日比: $diffSign$diffAbs"
    }

    private fun gradeKeyToLabel(gradeKey: String): String = when (gradeKey) {
        "2.5" -> "準2級"
        "1.5" -> "準1級"
        else -> "${gradeKey}級"
    }

    private fun updateGradeDropdownLabels() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@MainActivity)
            val words = db.wordDao().getAll()
            val progressDao = db.wordProgressDao()
            val nowSec = System.currentTimeMillis() / 1000L

            // Due/Started（型ズレ対策で Long に統一）
            val dueMeaning: List<Long> =
                progressDao.getDueWordIdsOrdered("meaning", nowSec).map { it.toLong() }
            val dueListening: List<Long> =
                progressDao.getDueWordIdsOrdered("listening", nowSec).map { it.toLong() }

            val startedMeaning: Set<Long> =
                progressDao.getProgressIds("meaning").map { it.toLong() }.toSet()
            val startedListening: Set<Long> =
                progressDao.getProgressIds("listening").map { it.toLong() }.toSet()
            val startedUnion: Set<Long> = startedMeaning union startedListening

            // gradeごとに wordId（Long）をまとめる
            val byGrade: Map<String, List<Long>> =
                words.groupBy { it.grade }.mapValues { (_, list) -> list.map { it.no.toLong() } }

            // DBのgradeが文字列なので、準級も含めたキー順序で表示
            val gradeKeys = listOf("5", "4", "3", "2.5", "2", "1.5", "1")

            val statsBuilder = linkedMapOf<String, String>()
            val items: List<GradeSpinnerItem> = gradeKeys.map { gradeKey ->
                val wordIds = byGrade[gradeKey].orEmpty()
                val idSet = wordIds.toSet()
                val total = wordIds.size

                val review = dueMeaning.count { it in idSet } + dueListening.count { it in idSet }
                val newUntouched = idSet.count { it !in startedUnion }

                statsBuilder[gradeKey] = "復習 $review • 新規 $newUntouched/$total"
                GradeSpinnerItem(gradeKey = gradeKey, label = gradeKeyToLabel(gradeKey))
            }
            gradeStatsMap = statsBuilder

            // 現在選択を保持
            val keepKey = selectedGradeKey ?: items.firstOrNull()?.gradeKey
            val selectedItem = items.firstOrNull { it.gradeKey == keepKey } ?: items.firstOrNull()

            // Exposed Dropdown は setAdapter + setText で反映
            val adapter = ArrayAdapter(
                this@MainActivity,
                R.layout.item_grade_dropdown,   // 項目も濃い色・大きめ
                items
            ).apply {
                setDropDownViewResource(R.layout.item_grade_dropdown)
            }
            gradeDropdown.setAdapter(adapter)

            if (selectedItem != null) {
                gradeDropdown.setText(selectedItem.label, false)
                selectedGradeKey = selectedItem.gradeKey
                buttonToLearning.isEnabled = true

                textGradeStatsTop.text = gradeStatsMap[selectedItem.gradeKey] ?: "復習 0 • 新規 0/0"
            } else {
                gradeDropdown.setText("", false)
                selectedGradeKey = null
                buttonToLearning.isEnabled = false
                textGradeStatsTop.text = "復習 0 • 新規 0/0"
            }
        }
    }

    // --- アクセシビリティ誘導 ---
    private fun maybeShowAccessibilityDialog() {
        val settings = AppSettings(this)
        // すでに表示中なら再表示しない
        if (accessibilityDialog?.isShowing == true) return

        val svcEnabled = isAppLockServiceEnabled()
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MainActivity)
            val lockedCount = db.lockedAppDao().countLocked()
            val shouldForce = settings.isAppLockEnabled() || lockedCount > 0
            if (!svcEnabled && shouldForce) {
                withContext(Dispatchers.Main) {
                    val msg = getString(R.string.app_lock_accessibility_message)
                    accessibilityDialog = AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.app_lock_accessibility_title)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(R.string.app_lock_accessibility_go_settings) { _, _ ->
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                        .setNegativeButton(R.string.app_lock_accessibility_disable_all) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                settings.setAppLockEnabled(false)
                                db.lockedAppDao().disableAllLocks()
                            }
                        }
                        .show()
                }
            }
        }
    }

    private fun isAppLockServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(this, AppLockAccessibilityService::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo?.serviceInfo?.packageName == packageName &&
                        it.resolveInfo?.serviceInfo?.name == expected.className
            }
    }
}