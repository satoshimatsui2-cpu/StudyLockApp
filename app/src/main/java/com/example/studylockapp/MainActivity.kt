package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.PointManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var textPointsTop: TextView
    private lateinit var textPointStatsTop: TextView
    private lateinit var buttonToLearning: Button
    private lateinit var buttonToList: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textPointsTop = findViewById(R.id.text_points_top)
        textPointStatsTop = findViewById(R.id.text_point_stats_top)

        // 学習画面へ
        buttonToLearning = findViewById(R.id.button_to_learning)
        buttonToLearning.setOnClickListener {
            startActivity(Intent(this, LearningActivity::class.java))
        }

        // 一覧画面へ（ある場合）
        buttonToList = findViewById(R.id.button_to_list)
        buttonToList.setOnClickListener {
            startActivity(Intent(this, WordListActivity::class.java))
        }

        // 初期表示
        updatePointView()
    }

    override fun onResume() {
        super.onResume()
        updatePointView()
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPointsTop.text = "ポイント: $total"
        lifecycleScope.launch { updatePointStats() }
    }

    /** 今日 / 前日比 を表示 */
    private suspend fun updatePointStats() {
        val db = AppDatabase.getInstance(this)
        val histDao = db.pointHistoryDao()
        val today = LocalDate.now().toEpochDay()
        val yesterday = today - 1
        val todaySum = histDao.getSumByDate(today)
        val yesterdaySum = histDao.getSumByDate(yesterday)
        val diff = todaySum - yesterdaySum
        val diffSign = if (diff >= 0) "+" else "-"
        val diffAbs = abs(diff)
        textPointStatsTop.text = "今日: $todaySum / 前日比: $diffSign$diffAbs"
    }
}