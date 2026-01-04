package com.example.studylockapp.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.ModeStatus
import com.example.studylockapp.data.WordHistoryItem
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class LearningHistoryActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var tabLayout: TabLayout
    private lateinit var gradeChipGroup: ChipGroup
    private lateinit var sortChipGroup: ChipGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LearningHistoryAdapter
    private lateinit var textTotalLearned: TextView // 追加

    private var fullList: List<WordHistoryItem> = emptyList()
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_history)
        db = AppDatabase.getInstance(this)

        setupViews()
        setupChart()
        setupRecyclerView()
        
        loadChartData(period = 0)
        loadListData()
    }

    private fun setupViews() {
        barChart = findViewById(R.id.bar_chart)
        tabLayout = findViewById(R.id.tab_layout_period)
        gradeChipGroup = findViewById(R.id.grade_filter_group)
        sortChipGroup = findViewById(R.id.sort_chip_group)
        recyclerView = findViewById(R.id.recycler_history)
        textTotalLearned = findViewById(R.id.text_total_learned) // 追加

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { loadChartData(it.position) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        gradeChipGroup.setOnCheckedStateChangeListener { _, _ -> applyFiltersAndSort() }
        sortChipGroup.setOnCheckedStateChangeListener { _, _ -> applyFiltersAndSort() }
    }

    private fun setupChart() {
        barChart.description.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setFitBars(true)
        barChart.animateY(1000)
        
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        
        barChart.axisRight.isEnabled = false
        barChart.axisLeft.axisMinimum = 0f
    }

    private fun loadChartData(period: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val entries = ArrayList<BarEntry>()
            val labels = ArrayList<String>()
            val calendar = Calendar.getInstance()
            var totalCount = 0 // 合計カウント用

            when (period) {
                0 -> { // Daily
                    for (i in 6 downTo 0) {
                        calendar.timeInMillis = System.currentTimeMillis()
                        calendar.add(Calendar.DAY_OF_YEAR, -i)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        val startTime = calendar.timeInMillis

                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        calendar.set(Calendar.SECOND, 59)
                        val endTime = calendar.timeInMillis

                        // 変更後: ログテーブルから取得 (履歴として正しく残っている)
                        val count = db.studyLogDao().getLearnedWordCountInTerm(startTime, endTime)
                        totalCount += count // 合計に加算
                        entries.add(BarEntry((6 - i).toFloat(), count.toFloat()))
                        labels.add("${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}")
                    }
                }
                1 -> { // Weekly
                    for (i in 3 downTo 0) {
                        calendar.timeInMillis = System.currentTimeMillis()
                        calendar.add(Calendar.WEEK_OF_YEAR, -i)
                        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        val startTime = calendar.timeInMillis

                        calendar.add(Calendar.DAY_OF_YEAR, 6)
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        val endTime = calendar.timeInMillis

                        // 変更後: ログテーブルから取得
                        val count = db.studyLogDao().getLearnedWordCountInTerm(startTime, endTime)
                        totalCount += count // 合計に加算
                        entries.add(BarEntry((3-i).toFloat(), count.toFloat()))
                        labels.add("${calendar.get(Calendar.WEEK_OF_YEAR)}週")
                    }
                }
                2 -> { // Monthly
                    for (i in 5 downTo 0) {
                        calendar.timeInMillis = System.currentTimeMillis()
                        calendar.add(Calendar.MONTH, -i)
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        val startTime = calendar.timeInMillis

                        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        val endTime = calendar.timeInMillis

                        // 変更後: ログテーブルから取得
                        val count = db.studyLogDao().getLearnedWordCountInTerm(startTime, endTime)
                        totalCount += count // 合計に加算
                        entries.add(BarEntry((5-i).toFloat(), count.toFloat()))
                        labels.add("${calendar.get(Calendar.MONTH) + 1}月")
                    }
                }
            }

            val dataSet = BarDataSet(entries, "学習単語数")
            dataSet.color = Color.parseColor("#2196F3")
            dataSet.valueTextSize = 10f

            val data = BarData(dataSet)
            data.barWidth = 0.7f

            withContext(Dispatchers.Main) {
                barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                barChart.data = data
                barChart.invalidate()
                textTotalLearned.text = "Total Learned: $totalCount Words" // テキスト更新
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = LearningHistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadListData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val words = db.wordDao().getAll()
            val progresses = db.wordProgressDao().getAll().groupBy { it.wordId }
            val now = System.currentTimeMillis()

            fullList = words.map { word ->
                val wordProgresses = progresses[word.no] ?: emptyList()
                val modes = listOf("meaning", "listening", "japanese_to_english", "english_english_1", "english_english_2")
                val statuses = modes.map { mode ->
                    val progress = wordProgresses.find { p -> p.mode == mode }
                    ModeStatus(
                        modeName = getDisplayModeName(mode), 
                        level = progress?.level ?: 0,
                        nextReviewDate = progress?.nextDueAtSec ?: 0L,
                        isReviewNeeded = (progress?.nextDueAtSec ?: Long.MAX_VALUE) < now / 1000
                    )
                }

                WordHistoryItem(
                    id = word.no.toLong(),
                    word = word.word,
                    meaning = word.japanese ?: "",
                    grade = word.grade,
                    statuses = statuses
                )
            }

            withContext(Dispatchers.Main) {
                applyFiltersAndSort()
            }
        }
    }

    private fun getDisplayModeName(mode: String): String {
        return when (mode) {
            "meaning" -> "英日"
            "listening" -> "リスニング"
            "japanese_to_english" -> "日英"
            "english_english_1" -> "英英1"
            "english_english_2" -> "英英2"
            else -> mode
        }
    }

    private fun applyFiltersAndSort() {
        var filteredList = fullList

        // Gradeでのフィルタリング
        val checkedGradeChipId = gradeChipGroup.checkedChipId
        if (checkedGradeChipId != View.NO_ID) {
            val selectedGrade = gradeChipGroup.findViewById<Chip>(checkedGradeChipId).tag.toString()
            filteredList = fullList.filter { it.grade == selectedGrade }
        }

        // Sort
        if (sortChipGroup.findViewById<Chip>(R.id.chip_sort_review)?.isChecked == true) {
            filteredList = filteredList.sortedByDescending { item -> item.statuses.any { it.isReviewNeeded } }
        }

        adapter.submitList(filteredList)
    }
}