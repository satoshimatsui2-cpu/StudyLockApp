package com.example.studylockapp

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.ui.WordAdapter
import com.example.studylockapp.ui.WordDisplayItem
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class WordListActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: WordAdapter
    private lateinit var spinnerGrade: Spinner

    // Headers
    private lateinit var headerWord: TextView
    private lateinit var headerGrade: TextView
    private lateinit var headerMLevel: TextView
    private lateinit var headerMDue: TextView
    private lateinit var headerLLevel: TextView
    private lateinit var headerLDue: TextView
    private lateinit var headerJeLevel: TextView
    private lateinit var headerJeDue: TextView
    private lateinit var headerEe1Level: TextView
    private lateinit var headerEe1Due: TextView
    private lateinit var headerEe2Level: TextView
    private lateinit var headerEe2Due: TextView

    private var displayCache: List<WordDisplayItem> = emptyList()
    private var currentSort: String = "並び替えなし"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_list)

        recycler = findViewById(R.id.recycler_words)
        spinnerGrade = findViewById(R.id.spinner_grade)

        // Initialize headers
        headerWord = findViewById(R.id.header_word)
        headerGrade = findViewById(R.id.header_grade)
        headerMLevel = findViewById(R.id.header_m_level)
        headerMDue = findViewById(R.id.header_m_due)
        headerLLevel = findViewById(R.id.header_l_level)
        headerLDue = findViewById(R.id.header_l_due)
        headerJeLevel = findViewById(R.id.header_je_level)
        headerJeDue = findViewById(R.id.header_je_due)
        headerEe1Level = findViewById(R.id.header_ee1_level)
        headerEe1Due = findViewById(R.id.header_ee1_due)
        headerEe2Level = findViewById(R.id.header_ee2_level)
        headerEe2Due = findViewById(R.id.header_ee2_due)

        adapter = WordAdapter(emptyList())
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        setupGradeFilterSpinner()

        // Set click listeners for sorting
        headerWord.setOnClickListener { setSort("Word") }
        headerGrade.setOnClickListener { setSort("Grade") }
        headerMLevel.setOnClickListener { setSort("M-Level") }
        headerMDue.setOnClickListener { setSort("M-Due") }
        headerLLevel.setOnClickListener { setSort("L-Level") }
        headerLDue.setOnClickListener { setSort("L-Due") }
        headerJeLevel.setOnClickListener { setSort("JE-Level") }
        headerJeDue.setOnClickListener { setSort("JE-Due") }
        headerEe1Level.setOnClickListener { setSort("EE1-Level") }
        headerEe1Due.setOnClickListener { setSort("EE1-Due") }
        headerEe2Level.setOnClickListener { setSort("EE2-Level") }
        headerEe2Due.setOnClickListener { setSort("EE2-Due") }

        // Adjust header and recycler width for horizontal scrolling
        // Calculate total width based on column widths defined in layout
        // Word: 120, Grade: 60, 5 modes * (Level: 50 + Due: 80) = 130 * 5 = 650
        // Total = 120 + 60 + 650 = 830dp (approx)
        // Converting dp to pixels
        val density = resources.displayMetrics.density
        val totalWidthPx = (830 * density).toInt()

        val headerRow = findViewById<LinearLayout>(R.id.header_row)
        headerRow.post {
            headerRow.layoutParams = headerRow.layoutParams.apply { width = totalWidthPx }
            recycler.layoutParams = recycler.layoutParams.apply { width = totalWidthPx }
        }

        lifecycleScope.launch {
            loadDisplayItems()
            applyFilterAndSort()
        }
    }

    private fun setSort(sortMode: String) {
        currentSort = sortMode
        applyFilterAndSort()
    }

    private fun gradeKeyToLabel(gradeKey: String): String = when (gradeKey) {
        "2.5" -> "準2級"
        "1.5" -> "準1級"
        "All" -> "All"
        else -> "${gradeKey}級"
    }

    private fun setupGradeFilterSpinner() {
        val gradeValues = resources.getStringArray(R.array.grade_filter_items).toList()
        val gradeLabels = gradeValues.map { gradeKeyToLabel(it) }

        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item_selected, // カスタムレイアウトを指定
            gradeLabels
        )
        // ドロップダウンリスト（開いた時）の見た目は標準のものを使う
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGrade.adapter = adapter

        spinnerGrade.setSelection(0, false)
        spinnerGrade.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilterAndSort()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
    }

    private suspend fun loadDisplayItems() {
        val db = AppDatabase.getInstance(this)
        val words = db.wordDao().getAll()
        val progressDao = db.wordProgressDao()

        val settings = AppSettings(this)
        val zoneId = settings.getAppZoneId()
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        // val nowSec = System.currentTimeMillis() / 1000L // Removed unused

        displayCache = words.map { w ->
            // Retrieve progress for all modes
            val pm = progressDao.getProgress(w.no, "meaning")
            val pl = progressDao.getProgress(w.no, "listening")
            val pJe = progressDao.getProgress(w.no, "japanese_to_english")
            val pEe1 = progressDao.getProgress(w.no, "english_english_1")
            val pEe2 = progressDao.getProgress(w.no, "english_english_2")

            fun formatDue(dueSec: Long?): String {
                if (dueSec == null) return "未学習"
                return Instant.ofEpochSecond(dueSec)
                    .atZone(zoneId)
                    .toLocalDate()
                    .format(dateFormatter)
            }

            WordDisplayItem(
                no = w.no,
                word = w.word,
                grade = w.grade,

                mLevel = pm?.level,
                mDue = pm?.nextDueAtSec,
                mDueText = formatDue(pm?.nextDueAtSec),

                lLevel = pl?.level,
                lDue = pl?.nextDueAtSec,
                lDueText = formatDue(pl?.nextDueAtSec),

                jeLevel = pJe?.level,
                jeDue = pJe?.nextDueAtSec,
                jeDueText = formatDue(pJe?.nextDueAtSec),

                ee1Level = pEe1?.level,
                ee1Due = pEe1?.nextDueAtSec,
                ee1DueText = formatDue(pEe1?.nextDueAtSec),

                ee2Level = pEe2?.level,
                ee2Due = pEe2?.nextDueAtSec,
                ee2DueText = formatDue(pEe2?.nextDueAtSec)
            )
        }
    }

    private fun applyFilterAndSort() {
        val gradeValues = resources.getStringArray(R.array.grade_filter_items)
        val pos = spinnerGrade.selectedItemPosition.takeIf { it in gradeValues.indices } ?: 0
        val gradeFilter = gradeValues[pos]

        val filtered = displayCache.filter { item ->
            gradeFilter == "All" || item.grade == gradeFilter
        }

        val sorted = when (currentSort) {
            "Word"      -> filtered.sortedBy { it.word.lowercase() }
            "Grade"     -> filtered.sortedBy { it.grade }
            
            "M-Level"   -> filtered.sortedByDescending { it.mLevel ?: -1 }
            "M-Due"     -> filtered.sortedBy { it.mDue ?: Long.MAX_VALUE }
            
            "L-Level"   -> filtered.sortedByDescending { it.lLevel ?: -1 }
            "L-Due"     -> filtered.sortedBy { it.lDue ?: Long.MAX_VALUE }
            
            "JE-Level"  -> filtered.sortedByDescending { it.jeLevel ?: -1 }
            "JE-Due"    -> filtered.sortedBy { it.jeDue ?: Long.MAX_VALUE }
            
            "EE1-Level" -> filtered.sortedByDescending { it.ee1Level ?: -1 }
            "EE1-Due"   -> filtered.sortedBy { it.ee1Due ?: Long.MAX_VALUE }
            
            "EE2-Level" -> filtered.sortedByDescending { it.ee2Level ?: -1 }
            "EE2-Due"   -> filtered.sortedBy { it.ee2Due ?: Long.MAX_VALUE }
            
            else        -> filtered
        }

        adapter.submitList(sorted)
    }
}