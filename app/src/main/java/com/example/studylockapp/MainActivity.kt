package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.CsvImporter
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerGradeTop: Spinner
    private lateinit var buttonToLearning: Button
    private lateinit var buttonAdminSettings: Button

    private lateinit var textPointsTop: TextView
    private lateinit var textPointStatsTop: TextView

    // Spinnerで選ばれた「級」(DBのgradeと一致する値) 例: "5"
    private var selectedGradeKey: String? = null

    data class GradeSpinnerItem(
        val gradeKey: String,   // 例: "5"
        val label: String       // 例: "5級[復:0,新:0/0]"
    ) {
        override fun toString(): String = label
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerGradeTop = findViewById(R.id.spinner_grade_top)
        buttonToLearning = findViewById(R.id.button_to_learning)
        buttonAdminSettings = findViewById(R.id.button_admin_settings)

        textPointsTop = findViewById(R.id.text_points_top)
        textPointStatsTop = findViewById(R.id.text_point_stats_top)

        buttonToLearning.isEnabled = false

        spinnerGradeTop.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val item = parent.getItemAtPosition(position) as? GradeSpinnerItem
                selectedGradeKey = item?.gradeKey
                buttonToLearning.isEnabled = (selectedGradeKey != null)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedGradeKey = null
                buttonToLearning.isEnabled = false
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

            updateGradeSpinnerLabels()
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
        updateGradeSpinnerLabels()
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPointsTop.text = "ポイント: $total"

        lifecycleScope.launch {
            updatePointStats()
        }
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
     * TOPのプルダウン表示：
     * 5級[復:x,新:y/z]
     *
     * 復習 = meaning Due + listening Due の合計
     * 新規 = meaning/listening 両方progress無し（どちらも未着手）
     */
    private fun updateGradeSpinnerLabels() {
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

            fun makeItem(gradeKey: String, wordIds: List<Long>): GradeSpinnerItem {
                val idSet = wordIds.toSet()
                val total = wordIds.size

                val review = dueMeaning.count { it in idSet } + dueListening.count { it in idSet }
                val newUntouched = idSet.count { it !in startedUnion }

                val label = "${gradeKey}級[復:$review,新:$newUntouched/$total]"
                return GradeSpinnerItem(gradeKey = gradeKey, label = label)
            }

            // DBのgradeが "5" "4" ... なのでこちらも数値文字列で
            val gradeKeys = listOf("5", "4", "3", "2", "1")
            val items = gradeKeys.map { g -> makeItem(g, byGrade[g].orEmpty()) }

            // 現在選択を保持（selectedGradeKey を優先）
            val currentSelectedKey: String =
                selectedGradeKey
                    ?: (spinnerGradeTop.selectedItem as? GradeSpinnerItem)?.gradeKey
                    ?: "5"

            spinnerGradeTop.adapter = android.widget.ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                items
            )

            val newIndex = items.indexOfFirst { it.gradeKey == currentSelectedKey }.let { if (it >= 0) it else 0 }
            spinnerGradeTop.setSelection(newIndex)

            selectedGradeKey = items.getOrNull(newIndex)?.gradeKey
            buttonToLearning.isEnabled = (selectedGradeKey != null)
        }
    }
}