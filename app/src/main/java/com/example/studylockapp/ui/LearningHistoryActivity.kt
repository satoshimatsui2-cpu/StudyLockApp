package com.example.studylockapp.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.WordHistoryItem
import com.example.studylockapp.databinding.ActivityLearningHistoryBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class LearningHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLearningHistoryBinding
    private lateinit var adapter: LearningHistoryAdapter
    private lateinit var mapper: WordHistoryMapper
    
    private var allHistoryItems: List<WordHistoryItem> = emptyList()
    private var currentFilterGrade: Int? = null 
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearningHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapper = WordHistoryMapper(this)
        
        setupToolbar()
        setupRecyclerView()
        setupFilters()
        setupChart()
        
        loadHistory()
        loadChartData(0) // Default: Daily
    }

    private fun setupToolbar() {
        setSupportActionBar(null) // Clear any default if exists
        supportActionBar?.apply {
            setTitle(R.string.learning_history_title)
            setDisplayHomeAsUpEnabled(true)
        }
        // If not using SupportActionBar from theme, we can just use the activity title
        title = getString(R.string.learning_history_title)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupRecyclerView() {
        adapter = LearningHistoryAdapter { item ->
            // Edit action if needed
        }
        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(this@LearningHistoryActivity)
            adapter = this@LearningHistoryActivity.adapter
        }
    }

    private fun setupFilters() {
        binding.gradeFilterGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                currentFilterGrade = null
            } else {
                val chip = group.findViewById<Chip>(checkedIds[0])
                currentFilterGrade = chip?.tag?.toString()?.toIntOrNull()
            }
            applyFilters()
        }

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText ?: ""
                applyFilters()
                return true
            }
        })
    }

    private fun setupChart() {
        binding.barChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            setScaleEnabled(false)
            legend.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = Color.GRAY
            }

            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                textColor = Color.GRAY
            }
            axisRight.isEnabled = false
        }

        binding.tabLayoutPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                loadChartData(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@LearningHistoryActivity)
                val results = withContext(Dispatchers.IO) {
                    db.wordDao().getLearningHistory()
                }
                
                allHistoryItems = results.map { mapper.map(it) }
                applyFilters()
                
                binding.textTotalLearned.text = getString(R.string.total_learned_count, allHistoryItems.size)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@LearningHistoryActivity, R.string.error_loading_history, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilters() {
        var filtered = allHistoryItems

        if (currentFilterGrade != null) {
            filtered = filtered.filter { it.grade == currentFilterGrade }
        }

        if (currentSearchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.word.contains(currentSearchQuery, ignoreCase = true) ||
                it.japanese.contains(currentSearchQuery, ignoreCase = true) ||
                it.description.contains(currentSearchQuery, ignoreCase = true) ||
                it.sentence.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        adapter.submitList(filtered)
    }

    private fun loadChartData(periodType: Int) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningHistoryActivity)
            val entries = mutableListOf<BarEntry>()
            val labels = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                val calendar = Calendar.getInstance()
                if (periodType == 0) { // Daily - Last 7 days
                    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
                    for (i in 6 downTo 0) {
                        calendar.time = Date()
                        calendar.add(Calendar.DAY_OF_YEAR, -i)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        val startTime = calendar.timeInMillis
                        
                        val endCal = calendar.clone() as Calendar
                        endCal.add(Calendar.DAY_OF_YEAR, 1)
                        val endTime = endCal.timeInMillis
                        
                        val count = db.studyLogDao().getStudyCountInTerm(startTime, endTime)
                        entries.add(BarEntry((6 - i).toFloat(), count.toFloat()))
                        labels.add(sdf.format(Date(startTime)))
                    }
                } else { // Weekly - Last 8 weeks
                    for (i in 7 downTo 0) {
                        calendar.time = Date()
                        calendar.add(Calendar.WEEK_OF_YEAR, -i)
                        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        val startTime = calendar.timeInMillis
                        
                        val endCal = calendar.clone() as Calendar
                        endCal.add(Calendar.WEEK_OF_YEAR, 1)
                        val endTime = endCal.timeInMillis
                        
                        val count = db.studyLogDao().getStudyCountInTerm(startTime, endTime)
                        entries.add(BarEntry((7 - i).toFloat(), count.toFloat()))
                        labels.add("W${8-i}")
                    }
                }
            }

            updateChart(entries, labels)
        }
    }

    private fun updateChart(entries: List<BarEntry>, labels: List<String>) {
        val dataSet = BarDataSet(entries, "学習回数")
        dataSet.color = Color.parseColor("#1A237E")
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.valueTextSize = 10f
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getBarLabel(barEntry: BarEntry?): String {
                return barEntry?.y?.toInt()?.toString() ?: "0"
            }
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.6f

        binding.barChart.apply {
            data = barData
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return if (index >= 0 && index < labels.size) labels[index] else ""
                }
            }
            invalidate()
            animateY(1000)
        }
    }
}
