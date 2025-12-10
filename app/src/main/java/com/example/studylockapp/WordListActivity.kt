package com.example.studylockapp

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.ui.WordAdapter
import com.example.studylockapp.ui.WordDisplayItem
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

    private suspend fun loadDisplayItems() {
        val db = AppDatabase.getInstance(this)
        val words = db.wordDao().getAll()
        val progressDao = db.wordProgressDao()

        displayCache = words.map { w ->
            val pm = progressDao.getProgress(w.no, "meaning")
            val pl = progressDao.getProgress(w.no, "listening")
            WordDisplayItem(
                no = w.no,
                word = w.word,
                japanese = w.japanese,
                grade = w.grade,
                pos = w.pos,
                category = w.category,
                mLevel = pm?.level,
                mDue = pm?.nextDueDate,
                lLevel = pl?.level,
                lDue = pl?.nextDueDate
            )
        }
    }

    private fun applyFilterAndSort() {
        val gradeFilter = spinnerGrade.selectedItem?.toString() ?: "All"

        // Grade のみフィルタ
        val filtered = displayCache.filter { item ->
            gradeFilter == "All" || item.grade == gradeFilter
        }

        // ソート
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