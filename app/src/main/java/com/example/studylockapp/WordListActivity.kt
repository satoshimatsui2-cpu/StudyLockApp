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
    private lateinit var headerWord: TextView
    private lateinit var headerGrade: TextView
    private lateinit var headerMLevel: TextView
    private lateinit var headerLLevel: TextView
    private lateinit var headerMDue: TextView
    private lateinit var headerLDue: TextView

    private var displayCache: List<WordDisplayItem> = emptyList()
    private var currentSort: String = "並び替えなし"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_list)

        recycler = findViewById(R.id.recycler_words)
        spinnerGrade = findViewById(R.id.spinner_grade)

        headerWord = findViewById(R.id.header_word)
        headerGrade = findViewById(R.id.header_grade)
        headerMLevel = findViewById(R.id.header_m_level)
        headerLLevel = findViewById(R.id.header_l_level)
        headerMDue = findViewById(R.id.header_m_due)
        headerLDue = findViewById(R.id.header_l_due)

        adapter = WordAdapter(emptyList())
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        setupGradeFilterSpinner()

        // ヘッダタップでソート切替
        headerWord.setOnClickListener { currentSort = "Word"; applyFilterAndSort() }
        headerGrade.setOnClickListener { currentSort = "Grade"; applyFilterAndSort() }
        headerMLevel.setOnClickListener { currentSort = "M-Level"; applyFilterAndSort() }
        headerLLevel.setOnClickListener { currentSort = "L-Level"; applyFilterAndSort() }
        headerMDue.setOnClickListener { currentSort = "M-NextDue"; applyFilterAndSort() }
        headerLDue.setOnClickListener { currentSort = "L-NextDue"; applyFilterAndSort() }

        // ヘッダ＆リスト幅を画面幅の1.5倍にする（横スクロール対応）
        val headerRow = findViewById<LinearLayout>(R.id.header_row)
        headerRow.post {
            val newWidth = (resources.displayMetrics.widthPixels * 1.5f).toInt()
            headerRow.layoutParams = headerRow.layoutParams.apply { width = newWidth }
            recycler.layoutParams = recycler.layoutParams.apply { width = newWidth }
        }

        lifecycleScope.launch {
            loadDisplayItems()
            applyFilterAndSort()
        }
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
            android.R.layout.simple_spinner_dropdown_item,
            gradeLabels
        )
        spinnerGrade.adapter = adapter

        spinnerGrade.setSelection(0, false)
        spinnerGrade.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilterAndSort()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // no-op
            }
        }
    }

    private suspend fun loadDisplayItems() {
        val db = AppDatabase.getInstance(this)
        val words = db.wordDao().getAll()
        val progressDao = db.wordProgressDao()

        val settings = AppSettings(this)
        val zoneId = settings.getAppZoneId()
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        // null due のフォールバック用に nowSec を取得
        val nowSec = System.currentTimeMillis() / 1000L

        displayCache = words.map { w ->
            val pm = progressDao.getProgress(w.no, "meaning")
            val pl = progressDao.getProgress(w.no, "listening")

            val mDueSec = pm?.nextDueAtSec
            val lDueSec = pl?.nextDueAtSec

            WordDisplayItem(
                no = w.no,
                word = w.word,
                japanese = w.japanese,
                grade = w.grade,
                pos = w.pos,
                category = w.category,

                mLevel = pm?.level,
                mDue = mDueSec,

                lLevel = pl?.level,
                lDue = lDueSec,

                // 日付表記に統一（未学習なら「未学習」）
                mDueText = if (pm == null) "未学習"
                else Instant.ofEpochSecond(mDueSec ?: nowSec)
                    .atZone(zoneId)
                    .toLocalDate()
                    .format(dateFormatter),

                lDueText = if (pl == null) "未学習"
                else Instant.ofEpochSecond(lDueSec ?: nowSec)
                    .atZone(zoneId)
                    .toLocalDate()
                    .format(dateFormatter),
            )
        }
    }

    private fun applyFilterAndSort() {
        val gradeValues = resources.getStringArray(R.array.grade_filter_items)
        val pos = spinnerGrade.selectedItemPosition.takeIf { it in gradeValues.indices } ?: 0
        val gradeFilter = gradeValues[pos] // "All" or "5"/"4"/"3"/"2.5"/"2"/"1.5"/"1"

        // Grade のみフィルタ
        val filtered = displayCache.filter { item ->
            gradeFilter == "All" || item.grade == gradeFilter
        }

        // ソート（Dueのソートは epochSec の mDue/lDue を使うので今まで通り）
        val sorted = when (currentSort) {
            "Word"      -> filtered.sortedBy { it.word.lowercase() }
            "Grade"     -> filtered.sortedBy { it.grade }
            "M-Level"   -> filtered.sortedByDescending { it.mLevel ?: 0 }
            "L-Level"   -> filtered.sortedByDescending { it.lLevel ?: 0 }
            "M-NextDue" -> filtered.sortedBy { it.mDue ?: Long.MAX_VALUE }
            "L-NextDue" -> filtered.sortedBy { it.lDue ?: Long.MAX_VALUE }
            else        -> filtered
        }

        adapter.submitList(sorted)
    }
}