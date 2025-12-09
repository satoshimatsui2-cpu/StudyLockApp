package com.example.studylockapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
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

    private var currentMode = "meaning" // "meaning" / "listening"
    private var currentWord: WordEntity? = null
    private var allWords: List<WordEntity> = emptyList()

    private lateinit var textQuestion: TextView
    private lateinit var textPoints: TextView
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioMeaning: RadioButton
    private lateinit var radioListening: RadioButton
    private lateinit var choiceButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        // View 取得
        textQuestion = findViewById(R.id.text_question)
        textPoints = findViewById(R.id.text_points)
        radioGroup = findViewById(R.id.radio_group_mode)
        radioMeaning = findViewById(R.id.radio_meaning)
        radioListening = findViewById(R.id.radio_listening)
        choiceButtons = listOf(
            findViewById(R.id.button_choice_1),
            findViewById(R.id.button_choice_2),
            findViewById(R.id.button_choice_3),
            findViewById(R.id.button_choice_4)
        )

        // モード切替
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentMode = if (checkedId == radioMeaning.id) "meaning" else "listening"
            loadNextQuestion()
        }

        // 選択肢ボタンのクリック
        choiceButtons.forEach { btn ->
            btn.setOnClickListener { onChoiceSelected(btn.text.toString()) }
        }

        // ポイント表示を更新 & 初期ロード
        updatePointView()
        loadAllWordsThenQuestion()
    }

    override fun onResume() {
        super.onResume()
        updatePointView()
    }

    /** DBから全単語を読み込んでから次の問題をセットする */
    private fun loadAllWordsThenQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            allWords = db.wordDao().getAll()
            loadNextQuestion()
        }
    }

    /** 次の問題をロードして表示（nextDueDate <= 今日 を優先、空なら全件） */
    private fun loadNextQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val wordDao = db.wordDao()
            val today = ProgressCalculator.todayEpochDay()

            // 期限到来の単語IDを取得
            val dueIds = progressDao.getDueWordIds(currentMode, today)
            val dueWords = if (dueIds.isEmpty()) emptyList() else wordDao.getByIds(dueIds)
            val targetList = if (dueWords.isNotEmpty()) dueWords else allWords

            if (targetList.isEmpty()) {
                textQuestion.text = "出題する単語がありません"
                currentWord = null
                choiceButtons.forEach { it.text = "----" }
                return@launch
            }

            // 1件ランダム出題
            val next = targetList.random()
            currentWord = next

            // 選択肢を作る（3つダミー + 正解）
            val choices = buildChoices(next, allWords, currentMode, count = 4)
            val (questionText, optionTexts) = formatQuestionAndOptions(next, choices, currentMode)

            // 表示更新
            textQuestion.text = questionText
            choiceButtons.zip(optionTexts).forEach { (btn, txt) -> btn.text = txt }
        }
    }

    /** 選択肢を生成（count 個、足りない場合はあるだけ） */
    private fun buildChoices(correct: WordEntity, pool: List<WordEntity>, mode: String, count: Int): List<WordEntity> {
        if (pool.isEmpty()) return listOf(correct)
        val distractors = pool.filter { it.no != correct.no }.shuffled().take(count - 1)
        return (distractors + correct).shuffled()
    }

    /** mode に応じて問題文と選択肢テキストを作る */
    private fun formatQuestionAndOptions(
        correct: WordEntity,
        choices: List<WordEntity>,
        mode: String
    ): Pair<String, List<String>> {
        return if (mode == "meaning") {
            // 問題: 英単語 → 選択肢: 日本語
            val question = "この英単語の意味は？\n${correct.word}"
            val options = choices.map { it.japanese }
            question to options
        } else {
            // リスニングモード（簡易版）：問題: 日本語 → 選択肢: 英単語
            val question = "次の日本語に合う英単語は？\n${correct.japanese}"
            val options = choices.map { it.word }
            question to options
        }
    }

    /** 選択肢がタップされたとき */
    private fun onChoiceSelected(selectedText: String) {
        val cw = currentWord ?: return
        val isCorrect = if (currentMode == "meaning") {
            selectedText == cw.japanese
        } else {
            selectedText == cw.word
        }
        onAnsweredInternal(wordId = cw.no, isCorrect = isCorrect)
    }

    /** 回答処理（DB更新 + ポイント加算 + 次の問題へ） */
    private fun onAnsweredInternal(wordId: Int, isCorrect: Boolean) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val pointManager = PointManager(this@LearningActivity)
            val today = ProgressCalculator.todayEpochDay()

            // 現在の進捗を取得（なければ初期値）
            val current = progressDao.getProgress(wordId, currentMode)
                ?: WordProgressEntity(wordId, currentMode, 0, today, 0L)

            // レベル更新
            val (newLevel, nextDue) = ProgressCalculator.update(isCorrect, current.level, today)

            // ポイント加算
            val addPoint = ProgressCalculator.calcPoint(isCorrect, current.level)
            pointManager.add(addPoint)

            // 保存
            val updated = current.copy(
                level = newLevel,
                nextDueDate = nextDue,
                lastAnsweredAt = System.currentTimeMillis()
            )
            progressDao.upsert(updated)

            // ログ
            Log.d("ANSWER_TEST", "wordId=$wordId isCorrect=$isCorrect addPoint=$addPoint newLevel=$newLevel nextDue=$nextDue totalPoint=${pointManager.getTotal()}")

            // 表示更新
            updatePointView()
            loadNextQuestion()
        }
    }

    /** ポイント表示を更新 */
    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPoints.text = "ポイント: $total"
    }
}