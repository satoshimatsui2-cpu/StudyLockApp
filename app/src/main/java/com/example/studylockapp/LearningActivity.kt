package com.example.studylockapp

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
    private var tts: TextToSpeech? = null

    private lateinit var textQuestionTitle: TextView
    private lateinit var textQuestionBody: TextView
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

        textQuestionTitle = findViewById(R.id.text_question_title)
        textQuestionBody = findViewById(R.id.text_question_body)
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
            findViewById(R.id.button_choice_4),
            findViewById(R.id.button_choice_5),
            findViewById(R.id.button_choice_6)
        )

        tts = TextToSpeech(this, this)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentMode = if (checkedId == radioMeaning.id) "meaning" else "listening"
            loadNextQuestion()
        }
        choiceButtons.forEach { btn ->
            btn.setOnClickListener { onChoiceSelected(btn.text.toString()) }
        }
        buttonPlayAudio.setOnClickListener { speakCurrentWord() }

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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    private fun loadAllWordsThenQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            allWords = db.wordDao().getAll()
            loadNextQuestion()
        }
    }

    private fun loadNextQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val wordDao = db.wordDao()
            val today = ProgressCalculator.todayEpochDay()

            // 期限到来＋未学習を優先
            val dueIds = progressDao.getDueWordIds(currentMode, today)
            val dueWords = if (dueIds.isEmpty()) emptyList() else wordDao.getByIds(dueIds)
            val progressedIds = progressDao.getProgressIds(currentMode).toSet()
            val untouched = allWords.filter { it.no !in progressedIds }
            val targetList = (dueWords + untouched).ifEmpty { allWords }

            if (targetList.isEmpty()) {
                textQuestionTitle.text = "出題する単語がありません"
                textQuestionBody.text = ""
                currentWord = null
                choiceButtons.forEach { it.text = "----" }
                return@launch
            }

            val next = targetList.random()
            currentWord = next

        // 出題時（loadNextQuestion 内）
            val choices = buildChoices(next, allWords, currentMode, count = 6)
            val (title, body, options) = formatQuestionAndOptions(next, choices, currentMode)
            textQuestionTitle.text = title
            textQuestionBody.text = body
            textQuestionBody.visibility = if (body.isEmpty()) View.GONE else View.VISIBLE
            choiceButtons.zip(options).forEach { (btn, txt) -> btn.text = txt }

            if (currentMode == "listening") speakCurrentWord()
        }
    }

    private fun buildChoices(correct: WordEntity, pool: List<WordEntity>, mode: String, count: Int): List<WordEntity> {
        if (pool.isEmpty()) return listOf(correct)
        val candidates = pool.filter { it.no != correct.no }
        val samePos = candidates.filter { it.pos != null && it.pos == correct.pos }
        val sameHead = candidates.filter { it.word.take(1).equals(correct.word.take(1), ignoreCase = true) }
        val combined = (samePos + sameHead).distinct()
        val distractors = when {
            combined.size >= count - 1 -> combined.shuffled().take(count - 1)
            samePos.size >= count - 1 -> samePos.shuffled().take(count - 1)
            else -> candidates.shuffled().take(count - 1)
        }
        return (distractors + correct).shuffled()
    }

    /** タイトル（問題文）と本文（単語or日本語）を分けて返す */
    private fun formatQuestionAndOptions(
        correct: WordEntity,
        choices: List<WordEntity>,
        mode: String
    ): Triple<String, String, List<String>> {
        return if (mode == "meaning") {
            val title = "この英単語の意味は？"
            val body = correct.word                // 大きく表示
            val options = choices.map { it.japanese }
            Triple(title, body, options)
        } else {
            val title = "音声を聞いて正しい英単語を選んでください"
            val body = ""            // ★ 日本語は表示しない
            val options = choices.map { it.word }
            Triple(title, body, options)
        }
    }

    private fun onChoiceSelected(selectedText: String) {
        val cw = currentWord ?: return
        val isCorrect = if (currentMode == "meaning") {
            selectedText == cw.japanese
        } else {
            selectedText == cw.word
        }
        onAnsweredInternal(cw.no, isCorrect)
    }

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

            showFeedback(isCorrect, addPoint)
            Log.d("ANSWER_TEST", "wordId=$wordId isCorrect=$isCorrect addPoint=$addPoint newLevel=$newLevel nextDue=$nextDue totalPoint=${pointManager.getTotal()}")

            updatePointView()
            delay(800)          // 少し待ってから
            textFeedback.text = ""
            loadNextQuestion()
        }
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPoints.text = "ポイント: $total"
    }

    private fun showFeedback(isCorrect: Boolean, addPoint: Int) {
        val color = if (isCorrect) android.R.color.holo_green_dark else android.R.color.holo_red_dark
        val msg = if (isCorrect) "正解！ +${addPoint}pt" else "不正解..."
        textFeedback.text = msg
        textFeedback.setTextColor(ContextCompat.getColor(this, color))
        textFeedback.textSize = 22f
        textFeedback.setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun speakCurrentWord() {
        val cw = currentWord ?: return
        tts?.speak(cw.word, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
    }
}