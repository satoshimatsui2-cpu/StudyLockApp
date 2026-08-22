package com.stulab.studylockapp.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.WordHistoryItem
import com.stulab.studylockapp.databinding.ActivityLearningHistoryBinding
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
    private var currentPeriod: Int = 0 // 0: Daily, 1: Weekly

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLearningHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentPeriod = intent.getIntExtra("EXTRA_PERIOD", 0)

        // Status bar & Display cutout handling (Maintaining initial padding)
        val initialAppBarTopPadding = binding.appBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.appBar.updatePadding(top = initialAppBarTopPadding + bars.top)
            insets
        }

        mapper = WordHistoryMapper(this)
        
        setupToolbar()
        setupNavigationTabs()
        setupPeriodButtons()
        setupRecyclerView()
        setupFilters()
        setupChart()
        
        loadHistory()
        loadChartData(currentPeriod)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val period = intent.getIntExtra("EXTRA_PERIOD", currentPeriod)
        if (period != currentPeriod) {
            currentPeriod = period
            updatePeriodUI()
            loadChartData(currentPeriod)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(null) 
        title = getString(R.string.learning_history_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * 単語/テストの切り替えタブを設定
     */
    private fun setupNavigationTabs() {
        binding.layoutTabs.buttonSwitchHistory.setOnClickListener {
            Log.d("HistoryTabs", "Switch to Test clicked")
            val intent = Intent(this, PracticalHistoryActivity::class.java).apply {
                putExtra("EXTRA_PERIOD", currentPeriod)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        
        binding.layoutTabs.textSwitchLabel.text = getString(R.string.history_label_test)
        binding.layoutTabs.buttonSwitchHistory.contentDescription = getString(R.string.history_cd_switch_test)
    }

    private fun setupPeriodButtons() {
        updatePeriodUI()

        binding.layoutTabs.buttonDaily.setOnClickListener {
            if (currentPeriod != 0) {
                currentPeriod = 0
                updatePeriodUI()
                loadChartData(0)
            }
        }

        binding.layoutTabs.buttonWeekly.setOnClickListener {
            if (currentPeriod != 1) {
                currentPeriod = 1
                updatePeriodUI()
                loadChartData(1)
            }
        }
    }

    private fun updatePeriodUI() {
        val selectedBg = R.drawable.bg_badge_navy_soft
        val unselectedBg = R.drawable.sl_button_bg
        val selectedTextColor = ContextCompat.getColor(this, R.color.navy_primary)
        val unselectedTextColor = ContextCompat.getColor(this, R.color.text_sub)

        binding.layoutTabs.buttonDaily.apply {
            setBackgroundResource(if (currentPeriod == 0) selectedBg else unselectedBg)
            setTextColor(if (currentPeriod == 0) selectedTextColor else unselectedTextColor)
            isSelected = currentPeriod == 0
        }

        binding.layoutTabs.buttonWeekly.apply {
            setBackgroundResource(if (currentPeriod == 1) selectedBg else unselectedBg)
            setTextColor(if (currentPeriod == 1) selectedTextColor else unselectedTextColor)
            isSelected = currentPeriod == 1
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupRecyclerView() {
        adapter = LearningHistoryAdapter(
            onEditClick = { item ->
                // Edit action if needed
            },
            onWordCheckClick = { item ->
                startVoiceCheck(item, "word")
            },
            onSentenceCheckClick = { item ->
                startVoiceCheck(item, "sentence")
            },
            onSpellingCheckClick = { item ->
                startSpellingCheck(item)
            }
        )
        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(this@LearningHistoryActivity)
            adapter = this@LearningHistoryActivity.adapter
        }
    }

    private fun startVoiceCheck(item: WordHistoryItem, type: String) {
        val intent = Intent(this, PronunciationChallengeActivity::class.java).apply {
            putExtra(PronunciationChallengeActivity.EXTRA_MODE, PronunciationChallengeActivity.MODE_SINGLE)
            putExtra(PronunciationChallengeActivity.EXTRA_WORD_ID, item.id)
        }
        startActivity(intent)
    }

    private fun startSpellingCheck(item: WordHistoryItem) {
        // CP方式へ移行のため、単発のスペルチェックは廃止しスキルチャレンジ画面へ誘導
        Toast.makeText(this, "スペルチェックは「スキルチャレンジ」から挑戦できます", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, SkillChallengeActivity::class.java))
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
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@LearningHistoryActivity)
                val results = withContext(Dispatchers.IO) {
                    db.wordDao().getLearningHistory()
                }
                
                val itemsWithoutVoice = results.map { mapper.map(it) }
                val wordIds = itemsWithoutVoice.map { it.id }

                val voiceResults = if (wordIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        db.voiceCheckDao().getAllResultsByIds(wordIds)
                    }
                } else emptyList()

                val spellingResults = if (wordIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        db.spellingProgressDao().getProgressByIds(wordIds)
                    }
                } else emptyList()

                val wordCheckedSet = voiceResults.filter { it.checkType == "word" && it.checked }.map { it.wordId }.toSet()
                val sentenceCheckedSet = voiceResults.filter { it.checkType == "sentence" && it.checked }.map { it.wordId }.toSet()

                val spellingMap = spellingResults.associateBy { it.wordId }
                
                val allWords = withContext(Dispatchers.IO) {
                    db.wordDao().getWordsByIds(wordIds.map { it.toInt() })
                }.associateBy { it.no.toLong() }

                allHistoryItems = itemsWithoutVoice.map { item ->
                    val spellingProgress = spellingMap[item.id]
                    val wordEntity = allWords[item.id]
                    item.copy(
                        isWordVoiceChecked = wordCheckedSet.contains(item.id),
                        isSentenceVoiceChecked = sentenceCheckedSet.contains(item.id),
                        spellingStatus = spellingProgress?.status ?: com.stulab.studylockapp.data.SpellingStatus.NOT_STARTED,
                        isSpellingEligible = wordEntity?.let { com.stulab.studylockapp.learning.SpellingEligibilityChecker.isEligible(it) } ?: false
                    )
                }

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
