package com.example.studylockapp

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AdminAuthManager
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.service.AppLockAccessibilityService
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var gradeButton: MaterialButton
    private lateinit var buttonToLearning: Button

    private lateinit var textPointsTop: TextView
    private lateinit var textPointStatsTop: TextView
    private lateinit var textGradeStatsTop: TextView

    private var gradeStatsMap: Map<String, String> = emptyMap()
    private var selectedGradeKey: String? = null

    private var accessibilityDialog: AlertDialog? = null

    data class GradeSpinnerItem(
        val gradeKey: String,   // 例: "5", "2.5"
        val label: String       // 表示用: "5級", "準2級"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gradeButton = findViewById(R.id.spinner_grade_top)
        buttonToLearning = findViewById(R.id.button_to_learning)
        textPointsTop = findViewById(R.id.text_points_top)
        textPointStatsTop = findViewById(R.id.text_point_stats_top)
        textGradeStatsTop = findViewById(R.id.text_grade_stats_top)

        // 初期状態
        gradeButton.text = getString(R.string.hint_select_grade)
        gradeButton.setOnClickListener { showGradePickerDialog() }
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

        // 学習画面へ（gradeFilter は DB の grade と一致する値：例 "5"）
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

        // 起動時にポイントとグレード表示を更新
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
        textPointsTop.text = "保有ポイント: $total"
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

            val gradeKeys = listOf("5", "4", "3", "2.5", "2", "1.5", "1")

            val statsBuilder = linkedMapOf<String, String>()
            gradeKeys.forEach { gradeKey ->
                val wordIds = byGrade[gradeKey].orEmpty()
                val idSet = wordIds.toSet()
                val total = wordIds.size

                val review = dueMeaning.count { it in idSet } + dueListening.count { it in idSet }
                val newUntouched = idSet.count { it !in startedUnion }

                statsBuilder[gradeKey] = "復習 $review • 新規 $newUntouched/$total"
            }
            gradeStatsMap = statsBuilder

            // 現在選択を保持
            val keepKey = selectedGradeKey ?: gradeKeys.firstOrNull()
            if (keepKey != null) {
                selectedGradeKey = keepKey
                gradeButton.text = gradeKeyToLabel(keepKey)
                buttonToLearning.isEnabled = true
                textGradeStatsTop.text = gradeStatsMap[keepKey] ?: "復習 0 • 新規 0/0"
            } else {
                gradeButton.text = getString(R.string.hint_select_grade)
                selectedGradeKey = null
                buttonToLearning.isEnabled = false
                textGradeStatsTop.text = "復習 0 • 新規 0/0"
            }
        }
    }

    // モーダルでグレード選択
    private fun showGradePickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_grade_picker, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialog.setOnShowListener {
            val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
            dialog.window?.let { w ->
                w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                w.setGravity(Gravity.CENTER)
            }
        }

        val gradeMap = mapOf(
            R.id.button_grade_5 to "5",
            R.id.button_grade_4 to "4",
            R.id.button_grade_3 to "3",
            R.id.button_grade_25 to "2.5",
            R.id.button_grade_2 to "2",
            R.id.button_grade_15 to "1.5",
            R.id.button_grade_1 to "1"
        )
        gradeMap.forEach { (id, grade) ->
            dialogView.findViewById<MaterialButton>(id)?.setOnClickListener {
                applyGradeSelection(grade)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun applyGradeSelection(grade: String) {
        selectedGradeKey = grade
        val label = gradeKeyToLabel(grade)
        gradeButton.text = label
        buttonToLearning.isEnabled = true
        textGradeStatsTop.text = gradeStatsMap[grade] ?: "復習 0 • 新規 0/0"
    }

    // --- アクセシビリティ誘導 ---
    private fun maybeShowAccessibilityDialog() {
        val settings = AppSettings(this)
        if (accessibilityDialog?.isShowing == true) return

        val svcEnabled = isAppLockServiceEnabled()
        val isRequired = AdminAuthManager.isAppLockRequired(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MainActivity)
            val lockedCount = db.lockedAppDao().countLocked()
            val shouldForce = settings.isAppLockEnabled() || lockedCount > 0
            if (!svcEnabled && shouldForce) {
                withContext(Dispatchers.Main) {
                    val msg = getString(R.string.app_lock_accessibility_message)
                    val builder = AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.app_lock_accessibility_title)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(R.string.app_lock_accessibility_go_settings) { _, _ ->
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    // 必須ONなら全解除ボタンを出さない
                    if (!isRequired) {
                        builder.setNegativeButton(R.string.app_lock_accessibility_disable_all) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                settings.setAppLockEnabled(false)
                                db.lockedAppDao().disableAllLocks()
                            }
                        }
                    }
                    accessibilityDialog = builder.show()
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