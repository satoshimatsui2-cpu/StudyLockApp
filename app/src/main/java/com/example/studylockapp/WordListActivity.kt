package com.example.studylockapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.ui.WordAdapter
import kotlinx.coroutines.launch

class WordListActivity : AppCompatActivity() {

    private lateinit var adapter: WordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_list)

        val recycler = findViewById<RecyclerView>(R.id.recycler_words)
        adapter = WordAdapter(emptyList())
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@WordListActivity).wordDao()
            val all = dao.getAll()
            adapter.submitList(all)
        }
    }
}

