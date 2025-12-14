package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.CsvImporter
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var gradeDropdown: MaterialAutoCompleteTextView
    private lateinit var buttonToLearning: Button
    private lateinit var buttonAdminSettings: Button

    private lateinit var textPointsTop: TextView
    private lateinit var textPointStatsTop: TextView

    // ★追加：級の統計（プルダウンの下に出す用）
    private lateinit var textGradeStatsTop: TextView
    private var gradeStatsMap: Map<String, String> = emptyMap()

    // DBのgradeと一致する値（例: "5"）
    private var selectedGradeKey: String? = null

    data class GradeSpinnerItem(
        val gradeKey: String,   // "5"
        val label: String       // "5級"（短く）
    ) {
        override fun toString(): String = label
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gradeDropdown = findViewById(R.id.spinner_grade_top)
        buttonToLearning = findViewById(R.id.button_to_learning)
        buttonAdminSettings = findViewById(R.id.button_admin_settings)

        textPointsTop = findViewById(R.id.text_points_top)
        textPointStatsTop = findViewById(R.id.text_point_stats_top)
        textGradeStatsTop = findViewById(R.id.text_grade_stats_top) // ★activity_main.xmlに追加した前提

        buttonToLearning.isEnabled = false
        textGradeStatsTop.text = "復習 0 • 新規 0/0"

        // ドロップダウン選択時：級だけ選ぶ。統計は別テキストに出す
        gradeDropdown.setOnItemClickListener { parent, _, position, _ ->
            val item = parent.getItemAtPosition(position) as? GradeSpinnerItem
            selectedGradeKey = item?.gradeKey
            buttonToLearning.isEnabled = (selectedGradeKey != null)

            val key = selectedGradeKey
            if (key != null) {
                textGradeStatsTop.text = gradeStatsMap[key] ?: "復習 0 • 新規 0/0"
            }
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

        // 管理者設定へ
        buttonAdminSettings.setOnClickListener {
            startActivity(Intent(this, AdminSettingsActivity::class.java))
        }

        // CSVインポート（初回のみ）
        lifecycleScope.launch {
            Log.d("CSV_IMPORT", "start import")
            CsvImporter.importIfNeeded(this@MainActivity)

            val count = AppDatabase.getInstance(this@MainActivity).wordDao().getAll().size
            Log.d("CSV_IMPORT", "words count=$count")

            updateGradeDropdownLabels()
            updatePointView()
        }

        updatePointView()
    }

    override fun onResume() {
        super.onResume()

        val settings = AppSettings(this)
        if (!settings.hasChosenTimeZone()) {
            startActivity(Intent(this, TimeZoneSetupActivity::class.java))
            return
        }

        updatePointView()
        updateGradeDropdownLabels()
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

    /**
     * TOPの級プルダウンは「5級」だけ表示。
     * 統計は別TextView（text_grade_stats_top）に
     * 例: 「復習 12 • 新規 34/200」
     *
     * 復習 = meaning Due + listening Due の合計
     * 新規 = meaning/listening 両方progress無し（どちらも未着手）
     */
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

            // DBのgradeが "5" "4" ... なのでこちらも数値文字列で
            val gradeKeys = listOf("5", "4", "3", "2", "1")

            val statsBuilder = linkedMapOf<String, String>()
            val items: List<GradeSpinnerItem> = gradeKeys.map { gradeKey ->
                val wordIds = byGrade[gradeKey].orEmpty()
                val idSet = wordIds.toSet()
                val total = wordIds.size

                val review = dueMeaning.count { it in idSet } + dueListening.count { it in idSet }
                val newUntouched = idSet.count { it !in startedUnion }

                statsBuilder[gradeKey] = "復習 $review • 新規 $newUntouched/$total"
                GradeSpinnerItem(gradeKey = gradeKey, label = "${gradeKey}級")
            }
            gradeStatsMap = statsBuilder

            // 現在選択を保持
            val keepKey = selectedGradeKey ?: items.firstOrNull()?.gradeKey
            val selectedItem = items.firstOrNull { it.gradeKey == keepKey } ?: items.firstOrNull()

            // Exposed Dropdown は setAdapter + setText で反映
            gradeDropdown.setAdapter(
                ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
            )

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
}