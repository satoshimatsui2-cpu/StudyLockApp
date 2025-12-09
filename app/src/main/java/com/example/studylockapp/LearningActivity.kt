package com.example.studylockapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.ProgressCalculator
import com.example.studylockapp.data.WordProgressEntity
import kotlinx.coroutines.launch

class LearningActivity : AppCompatActivity() {

    // 出題モード: 意味なら "meaning", リスニングなら "listening" など
    private val currentMode = "meaning"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        // 【テスト用】回答処理を呼び出すボタン
        val testButton: Button = findViewById(R.id.button_test_answer)
        testButton.setOnClickListener {
            // 仮の単語ID=1を「正解」として扱う例
            onAnswered(wordId = 1, isCorrect = true)
        }

        // ポイント表示を更新
        updatePointView()

        // ★ 本日の出題対象（nextDueDate <= 今日）をログに出す
        loadDueWords(mode = currentMode)
    }

    override fun onResume() {
        super.onResume()
        updatePointView()
    }

    /**
     * 回答が確定したときに呼ぶ
     * @param wordId WordEntity.no に合わせる
     * @param isCorrect 正解なら true, 不正解なら false
     */
    private fun onAnswered(wordId: Int, isCorrect: Boolean) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val pointManager = PointManager(this@LearningActivity)

            val today = ProgressCalculator.todayEpochDay()

            // 現在の進捗を取得（なければ初期値を作る）
            val current = progressDao.getProgress(wordId, currentMode)
                ?: WordProgressEntity(
                    wordId = wordId,
                    mode = currentMode,
                    level = 0,
                    nextDueDate = today,
                    lastAnsweredAt = 0L
                )

            // レベル更新
            val (newLevel, nextDue) = ProgressCalculator.update(isCorrect, current.level, today)

            // ポイント計算＆加算
            val addPoint = ProgressCalculator.calcPoint(isCorrect, current.level)
            pointManager.add(addPoint)

            // 進捗保存
            val updated = current.copy(
                level = newLevel,
                nextDueDate = nextDue,
                lastAnsweredAt = System.currentTimeMillis()
            )
            progressDao.upsert(updated)

            // （任意）ログで確認
            Log.d("ANSWER_TEST", "wordId=$wordId isCorrect=$isCorrect addPoint=$addPoint newLevel=$newLevel nextDue=$nextDue totalPoint=${pointManager.getTotal()}")

            // ポイント表示を更新
            updatePointView()
        }
    }

    /** ポイント表示を更新するヘルパー */
    private fun updatePointView() {
        val pointView: TextView = findViewById(R.id.text_points)
        val total = PointManager(this).getTotal()
        pointView.text = "ポイント: $total"
    }

    /** mode の出題対象（nextDueDate <= 今日）を取得してログに出す */
    private fun loadDueWords(mode: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val wordDao = db.wordDao()

            val today = ProgressCalculator.todayEpochDay()

            // 1) 期限到来の単語IDを取得
            val ids = progressDao.getDueWordIds(mode, today)

            // 2) IDから単語を取得（空なら空リスト）
            val dueWords = if (ids.isEmpty()) emptyList() else wordDao.getByIds(ids)

            // 3) ログで確認
            Log.d("DUE_TEST", "mode=$mode dueCount=${dueWords.size}")
            dueWords.forEach { w ->
                Log.d("DUE_TEST", "${w.no} ${w.word} ${w.japanese}")
            }
        }
    }
}