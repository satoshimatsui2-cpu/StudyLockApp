package com.example.studylockapp.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.UnlockHistoryEntity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.TimeUnit

class PointHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: PointHistoryAdapter
    private lateinit var db: AppDatabase
    private lateinit var textEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_point_history)

        db = AppDatabase.getInstance(this)

        setupToolbar()
        setupRecyclerView()
        loadHistoryData()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish() // 戻るボタンでActivity終了
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_history)
        textEmpty = findViewById(R.id.text_empty_history)

        adapter = PointHistoryAdapter { historyItem ->
            cancelUnlock(historyItem)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadHistoryData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val historyList = db.unlockHistoryDao().getLatest100()

            withContext(Dispatchers.Main) {
                if (historyList.isEmpty()) {
                    textEmpty.visibility = View.VISIBLE
                } else {
                    textEmpty.visibility = View.GONE
                    adapter.submitList(historyList)
                }
            }
        }
    }

    private fun cancelUnlock(item: UnlockHistoryEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            // 1. ポイント返却処理
            val now = Instant.now().epochSecond
            val usedSec = now - item.unlockedAt
            val remainingSec = (item.unlockedAt + item.unlockDurationSec) - now

            if (remainingSec > 0) {
                val refundRatio = remainingSec.toDouble() / item.unlockDurationSec
                val refundPoints = (item.usedPoints * refundRatio).toInt()

                if (refundPoints > 0) {
                    val pointManager = PointManager(this@PointHistoryActivity)
                    pointManager.add(refundPoints)
                }
            }

            // 2. DBのステータスをキャンセルに更新
            item.cancelled = true
            db.unlockHistoryDao().update(item)

            // 3. アプリのロックを即時再開
            db.appUnlockDao().clear(item.packageName)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@PointHistoryActivity, "ロック解除をキャンセルし、ポイントを一部返却しました", Toast.LENGTH_SHORT).show()
                loadHistoryData()
            }
        }
    }
}