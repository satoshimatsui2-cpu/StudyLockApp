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
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.WordProgressEntity
import kotlinx.coroutines.launch

class LearningActivity : AppCompatActivity() {

    // 出題モード: 意味なら "meaning", リスニングなら "listening" など
    private val currentMode = "meaning"

    // 現在出題中の単語を保持
    private var currentWord: WordEntity? = null

    private lateinit var textQuestion: TextView
    private lateinit var textPoints: TextView
    private lateinit var buttonCorrect: Button
    private lateinit var buttonWrong: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        // View の取得
        textQuestion = findViewById(R.id.text_question)
        textPoints = findViewById(R.id.text_points)
        buttonCorrect = findViewById(R.id.button_correct)
        buttonWrong = findViewById(R.id.button_wrong)

        // ボタンに回答処理を紐付け
        buttonCorrect.setOnClickListener { onAnswered(isCorrect = true) }
        buttonWrong.setOnClickListener { onAnswered(isCorrect = false) }

        // 起動時にポイント表示 & 次の問題をロード
        updatePointView()
        loadNextQuestion()
    }

    override fun onResume() {
        super.onResume()
        updatePointView()
    }

    /** 次の問題をロードして表示（nextDueDate <= 今日 のものから1件） */
    private fun loadNextQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val wordDao = db.wordDao()
            val today = ProgressCalculator.todayEpochDay()

            // 1) 期限到来の単語IDを取得
            val ids = progressDao.getDueWordIds(currentMode, today)

            // 2) IDから単語を取得（空なら空リスト）
            val dueWords = if (ids.isEmpty()) emptyList() else wordDao.getByIds(ids)

            // 3) 出題リストが空なら全件から出す（フォールバック）
            val targetList = if (dueWords.isNotEmpty()) dueWords else wordDao.getAll()

            if (targetList.isEmpty()) {
                textQuestion.text = "出題する単語がありません"
                currentWord = null
                return@launch
            }

            // 4) ランダムに1件出題
            val next = targetList.random()
            currentWord = next
            textQuestion.text = next.word  // ここでは英単語をそのまま表示（意味問題）
        }
    }

    /**
     * 回答が確定したときに呼ぶ
     * @param isCorrect 正解なら true, 不正解なら false
     */
    private fun onAnswered(isCorrect: Boolean) {
        val wordId = currentWord?.no ?: run {
            Log.d("ANSWER", "No current word.")
            return
        }
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

            // ログとポイント表示
            Log.d(
                "ANSWER_TEST",
                "wordId=$wordId isCorrect=$isCorrect addPoint=$addPoint " +
                        "newLevel=$newLevel nextDue=$nextDue totalPoint=${pointManager.getTotal()}"
            )
            updatePointView()

            // 次の問題をロード
            loadNextQuestion()
        }
    }

    /** ポイント表示を更新するヘルパー */
    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPoints.text = "ポイント: $total"
    }
}