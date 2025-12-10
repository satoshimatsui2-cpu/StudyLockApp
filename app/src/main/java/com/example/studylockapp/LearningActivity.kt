package com.example.studylockapp

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.graphics.Typeface
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.ProgressCalculator
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.WordProgressEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class LearningActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var currentMode = "meaning" // "meaning" / "listening"
    private var currentWord: WordEntity? = null
    private var allWords: List<WordEntity> = emptyList()

    // TTS
    private var tts: TextToSpeech? = null

    private lateinit var textQuestion: TextView
    private lateinit var textPoints: TextView
    private lateinit var textFeedback: TextView
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioMeaning: RadioButton
    private lateinit var radioListening: RadioButton
    private lateinit var choiceButtons: List<Button>
    private lateinit var buttonPlayAudio: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        // View 取得
        textQuestion = findViewById(R.id.text_question)
        textPoints = findViewById(R.id.text_points)
        textFeedback = findViewById(R.id.text_feedback)
        radioGroup = findViewById(R.id.radio_group_mode)
        radioMeaning = findViewById(R.id.radio_meaning)
        radioListening = findViewById(R.id.radio_listening)
        buttonPlayAudio = findViewById(R.id.button_play_audio)
        choiceButtons = listOf(
            findViewById(R.id.button_choice_1),
            findViewById(R.id.button_choice_2),
            findViewById(R.id.button_choice_3),
            findViewById(R.id.button_choice_4)
        )

        // TTS 初期化
        tts = TextToSpeech(this, this)

        // モード切替
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentMode = if (checkedId == radioMeaning.id) "meaning" else "listening"
            loadNextQuestion()
        }

        // 選択肢ボタンのクリック
        choiceButtons.forEach { btn ->
            btn.setOnClickListener { onChoiceSelected(btn.text.toString()) }
        }

        // 音声再生ボタン
        buttonPlayAudio.setOnClickListener { speakCurrentWord() }

        // ポイント表示を更新 & 初期ロード
        updatePointView()
        loadAllWordsThenQuestion()
    }

    override fun onResume() {
        super.onResume()
        updatePointView()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    /** TTS 初期化結果 */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    /** DBから全単語を読み込んでから次の問題をセットする */
    private fun loadAllWordsThenQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            allWords = db.wordDao().getAll()
            loadNextQuestion()
        }
    }

    /** 次の問題をロードして表示（期限到来＋未学習を優先、空なら全件） */
    private fun loadNextQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val wordDao = db.wordDao()
            val today = ProgressCalculator.todayEpochDay()

            // 1) 期限到来の単語
            val dueIds = progressDao.getDueWordIds(currentMode, today)
            val dueWords = if (dueIds.isEmpty()) emptyList() else wordDao.getByIds(dueIds)

            // 2) 進捗の無い（未学習）単語を拾う
            val progressedIds = progressDao.getProgressIds(currentMode).toSet()
            val untouchedWords = allWords.filter { it.no !in progressedIds }

            // 3) 出題候補を作る：期限到来 + 未学習 を優先、空なら全件
            val targetList = (dueWords + untouchedWords).ifEmpty { allWords }

            if (targetList.isEmpty()) {
                textQuestion.text = "出題する単語がありません"
                currentWord = null
                choiceButtons.forEach { it.text = "----" }
                return@launch
            }

            // 1件ランダム出題
            val next = targetList.random()
            currentWord = next

            // 選択肢を作る（ダミー3 + 正解）
            val choices = buildChoices(next, allWords, currentMode, count = 4)
            val (questionText, optionTexts) = formatQuestionAndOptions(next, choices, currentMode)

            // 表示更新
            textQuestion.text = questionText
            choiceButtons.zip(optionTexts).forEach { (btn, txt) -> btn.text = txt }

            // リスニングモードは自動再生
            if (currentMode == "listening") {
                speakCurrentWord()
            }
        }
    }

    /** 選択肢を生成（同じ品詞・頭文字を優先、足りなければフォールバック） */
    private fun buildChoices(
        correct: WordEntity,
        pool: List<WordEntity>,
        mode: String,
        count: Int
    ): List<WordEntity> {
        if (pool.isEmpty()) return listOf(correct)

        val candidates = pool.filter { it.no != correct.no }
        val samePos = candidates.filter { it.pos != null && it.pos == correct.pos }
        val sameHead = candidates.filter { it.word.take(1).equals(correct.word.take(1), ignoreCase = true) }
        val combined = (samePos + sameHead).distinct()

        val distractors = when {
            combined.size >= count - 1 -> combined.shuffled().take(count - 1)
            samePos.size >= count - 1   -> samePos.shuffled().take(count - 1)
            else                        -> candidates.shuffled().take(count - 1)
        }
        return (distractors + correct).shuffled()
    }

    /** mode に応じて問題文と選択肢テキストを作る */
    private fun formatQuestionAndOptions(
        correct: WordEntity,
        choices: List<WordEntity>,
        mode: String
    ): Pair<String, List<String>> {
        return if (mode == "meaning") {
            val question = "この英単語の意味は？\n${correct.word}"
            val options = choices.map { it.japanese }
            question to options
        } else {
            val question = "音声を聞いて正しい英単語を選んでください"
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

            val current = progressDao.getProgress(wordId, currentMode)
                ?: WordProgressEntity(wordId, currentMode, 0, today, 0L)

            val (newLevel, nextDue) = ProgressCalculator.update(isCorrect, current.level, today)
            val addPoint = ProgressCalculator.calcPoint(isCorrect, current.level)
            pointManager.add(addPoint)

            val updated = current.copy(
                level = newLevel,
                nextDueDate = nextDue,
                lastAnsweredAt = System.currentTimeMillis()
            )
            progressDao.upsert(updated)

            // フィードバック表示 → 少し待ってから次の問題
            showFeedback(isCorrect, addPoint)
            Log.d(
                "ANSWER_TEST",
                "wordId=$wordId isCorrect=$isCorrect addPoint=$addPoint newLevel=$newLevel nextDue=$nextDue totalPoint=${pointManager.getTotal()}"
            )

            updatePointView()
            delay(800)            // 0.8秒待って見えるように
            textFeedback.text = "" // 次の問題前にクリア
            loadNextQuestion()
        }
    }

    /** ポイント表示を更新 */
    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPoints.text = "ポイント: $total"
    }

    /** フィードバック表示 */
    private fun showFeedback(isCorrect: Boolean, addPoint: Int) {
        val color = if (isCorrect) android.R.color.holo_green_dark else android.R.color.holo_red_dark
        val msg = if (isCorrect) "正解！ +${addPoint}pt" else "不正解..."
        textFeedback.text = msg
        textFeedback.setTextColor(ContextCompat.getColor(this, color))
        textFeedback.textSize = 22f              // 文字を大きく
        textFeedback.setTypeface(null, Typeface.BOLD) // 太字
    }

    /** 現在の単語を TTS で読み上げ */
    private fun speakCurrentWord() {
        val cw = currentWord ?: return
        tts?.speak(cw.word, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
    }
}