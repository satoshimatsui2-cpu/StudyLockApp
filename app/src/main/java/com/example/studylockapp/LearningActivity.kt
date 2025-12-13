package com.example.studylockapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.PointHistoryEntity
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.ProgressCalculator
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.WordProgressEntity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

class LearningActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var currentMode = "meaning"
    private var gradeFilter: String = "All"   // TOPで選んだ級
    private var currentWord: WordEntity? = null
    private var allWords: List<WordEntity> = emptyList()
    private var tts: TextToSpeech? = null

    // 今回の問題で「正解が入っているボタンのindex」
    private var currentCorrectIndex: Int = -1

    // ボタンの元の色に戻すため
    private lateinit var defaultChoiceTints: List<ColorStateList?>

    private val greenTint by lazy {
        ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_green_light))
    }
    private val redTint by lazy {
        ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_red_light))
    }

    // SoundPool
    private var soundPool: SoundPool? = null
    private var seCorrectId: Int = 0
    private var seWrongId: Int = 0

    // 褒め言葉
    private val praiseMessages = listOf(
        "すごい！", "その調子！", "天才！", "完璧！", "いいね！", "ナイス！"
    )

    // Snackbarが重ならないように保持
    private var currentSnackbar: Snackbar? = null

    private lateinit var textQuestionTitle: TextView
    private lateinit var textQuestionBody: TextView
    private lateinit var textPoints: TextView
    private lateinit var textPointStats: TextView
    private lateinit var textFeedback: TextView // 互換のため残す（基本使わない）
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioMeaning: RadioButton
    private lateinit var radioListening: RadioButton
    private lateinit var choiceButtons: List<Button>
    private lateinit var buttonPlayAudio: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        // TOPから渡された gradeFilter を受け取る
        gradeFilter = intent.getStringExtra("gradeFilter") ?: "All"

        textQuestionTitle = findViewById(R.id.text_question_title)
        textQuestionBody = findViewById(R.id.text_question_body)
        textPoints = findViewById(R.id.text_points)
        textPointStats = findViewById(R.id.text_point_stats)
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

        // TextViewのフィードバックは使わないので隠す（XML変更不要）
        textFeedback.visibility = View.GONE

        // デフォルトの背景Tintを保存（次の問題で戻す用）
        defaultChoiceTints = choiceButtons.map { ViewCompat.getBackgroundTintList(it) }

        tts = TextToSpeech(this, this)

        // SoundPool 初期化
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        // rawにある場合だけロード（無くても落ちない）
        seCorrectId = loadSeIfExists("se_correct")
        seWrongId = loadSeIfExists("se_wrong") // 無くてもOK

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentMode = if (checkedId == radioMeaning.id) "meaning" else "listening"
            loadNextQuestion()
        }

        // indexでクリック処理（正解ボタンを確実に緑にするため）
        choiceButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { onChoiceSelected(index) }
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

        soundPool?.release()
        soundPool = null

        currentSnackbar?.dismiss()
        currentSnackbar = null

        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    private fun loadSeIfExists(rawName: String): Int {
        val resId = resources.getIdentifier(rawName, "raw", packageName)
        if (resId == 0) {
            Log.w("SoundPool", "raw/$rawName が見つかりません（効果音なしで続行）")
            return 0
        }
        return soundPool?.load(this, resId, 1) ?: 0
    }

    // 正解時に画面全体を一瞬フラッシュ（黄色っぽいキラッ）
    private fun flashCorrectBackground() {
        val root = findViewById<View>(android.R.id.content)
        val flashColor = ContextCompat.getColor(this, android.R.color.holo_orange_light)

        fun runFlash() {
            val drawable = ColorDrawable(flashColor)
            drawable.setBounds(0, 0, root.width, root.height)
            drawable.alpha = 0
            root.overlay.add(drawable)

            // 控えめ設定
            ValueAnimator.ofInt(0, 90, 0).apply {
                duration = 160L
                addUpdateListener { drawable.alpha = it.animatedValue as Int }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        root.overlay.remove(drawable)
                    }
                })
                start()
            }
        }

        if (root.width == 0 || root.height == 0) root.post { runFlash() } else runFlash()
    }

    private fun playCorrectEffect() {
        flashCorrectBackground()

        if (seCorrectId != 0) soundPool?.play(seCorrectId, 1f, 1f, 1, 0, 1f)

        // 正解ボタンをバウンス
        val idx = currentCorrectIndex
        val v = choiceButtons.getOrNull(idx) ?: return
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        v.animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .setDuration(120)
            .withEndAction {
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
            .start()
    }

    private fun playWrongEffect() {
        if (seWrongId != 0) soundPool?.play(seWrongId, 1f, 1f, 1, 0, 1f)
    }

    private fun loadAllWordsThenQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val words = db.wordDao().getAll()
            allWords = if (gradeFilter == "All") words else words.filter { it.grade == gradeFilter }
            loadNextQuestion()
        }
    }

    // 次の問題の前に、ボタンの色/状態を元に戻す
    private fun resetChoiceButtons() {
        choiceButtons.forEachIndexed { i, btn ->
            btn.isClickable = true
            btn.isEnabled = true
            btn.alpha = 1f
            ViewCompat.setBackgroundTintList(btn, defaultChoiceTints[i])
        }
        currentCorrectIndex = -1
    }

    private fun loadNextQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val wordDao = db.wordDao()
            val today = ProgressCalculator.todayEpochDay()

            resetChoiceButtons()

            val dueIds = progressDao.getDueWordIds(currentMode, today)
            val dueWords = if (dueIds.isEmpty()) emptyList() else wordDao.getByIds(dueIds)
            val progressedIds = progressDao.getProgressIds(currentMode).toSet()
            val untouched = allWords.filter { it.no !in progressedIds }
            val targetList = (dueWords + untouched).filter { it in allWords }.ifEmpty { allWords }

            if (targetList.isEmpty()) {
                textQuestionTitle.text = "出題する単語がありません"
                textQuestionBody.text = ""
                textQuestionBody.visibility = View.GONE
                currentWord = null
                choiceButtons.forEach {
                    it.text = "----"
                    it.isEnabled = false
                }
                return@launch
            }

            val next = targetList.random()
            currentWord = next

            val choices = if (currentMode == "meaning") {
                buildChoicesMeaning(next, allWords, count = 6)
            } else {
                buildChoicesListening(next, allWords, count = 6)
            }

            val (title, body, options) = formatQuestionAndOptions(next, choices, currentMode)
            textQuestionTitle.text = title
            textQuestionBody.text = body
            textQuestionBody.visibility = if (body.isEmpty()) View.GONE else View.VISIBLE
            choiceButtons.zip(options).forEach { (btn, txt) -> btn.text = txt }

            val correctText = if (currentMode == "meaning") next.japanese else next.word
            currentCorrectIndex = options.indexOf(correctText)

            if (currentMode == "listening") speakCurrentWord()
        }
    }

    /** 意味用 */
    private fun buildChoicesMeaning(correct: WordEntity, pool: List<WordEntity>, count: Int): List<WordEntity> {
        if (pool.isEmpty()) return listOf(correct)
        val candidates = pool.filter { it.no != correct.no }

        val sameGradePosHeadLen = candidates.filter {
            it.grade == correct.grade &&
                    it.pos == correct.pos &&
                    it.word.take(1).equals(correct.word.take(1), ignoreCase = true) &&
                    abs(it.word.length - correct.word.length) <= 1
        }
        val sameGradePos = candidates.filter { it.grade == correct.grade && it.pos == correct.pos }
        val sameGrade = candidates.filter { it.grade == correct.grade }
        val samePos = candidates.filter { it.pos == correct.pos }
        val sameHead = candidates.filter { it.word.take(1).equals(correct.word.take(1), ignoreCase = true) }
        val lenNear = candidates.filter { abs(it.word.length - correct.word.length) <= 2 }

        val merged = (sameGradePosHeadLen + sameGradePos + sameGrade + samePos + sameHead + lenNear + candidates).distinct()
        val distractors = merged.shuffled().take(count - 1)
        return (distractors + correct).shuffled()
    }

    /** リスニング用 */
    private fun buildChoicesListening(correct: WordEntity, pool: List<WordEntity>, count: Int): List<WordEntity> {
        if (pool.isEmpty()) return listOf(correct)
        val candidates = pool.filter { it.no != correct.no }

        val sameGradeHeadLen = candidates.filter {
            it.grade == correct.grade &&
                    it.word.take(1).equals(correct.word.take(1), ignoreCase = true) &&
                    abs(it.word.length - correct.word.length) <= 1
        }
        val sameGrade = candidates.filter { it.grade == correct.grade }
        val sameHead = candidates.filter { it.word.take(1).equals(correct.word.take(1), ignoreCase = true) }
        val lenNear = candidates.filter { abs(it.word.length - correct.word.length) <= 2 }

        val merged = (sameGradeHeadLen + sameGrade + sameHead + lenNear + candidates).distinct()
        val distractors = merged.shuffled().take(count - 1)
        return (distractors + correct).shuffled()
    }

    private fun formatQuestionAndOptions(
        correct: WordEntity,
        choices: List<WordEntity>,
        mode: String
    ): Triple<String, String, List<String>> {
        return if (mode == "meaning") {
            Triple("この英単語の意味は？", correct.word, choices.map { it.japanese })
        } else {
            Triple("音声を聞いて正しい英単語を選んでください", "", choices.map { it.word })
        }
    }

    private fun onChoiceSelected(selectedIndex: Int) {
        val cw = currentWord ?: return

        val selectedText = choiceButtons.getOrNull(selectedIndex)?.text?.toString() ?: return
        val isCorrect = if (currentMode == "meaning") {
            selectedText == cw.japanese
        } else {
            selectedText == cw.word
        }

        // 連打防止（見た目をグレーにしない）
        choiceButtons.forEach { it.isClickable = false }

        // 正解/不正解に関係なく「正解の選択肢」を必ずグリーンに
        if (currentCorrectIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[currentCorrectIndex], greenTint)
        }

        // 不正解のときだけ、押したボタンを赤にする
        if (!isCorrect && selectedIndex != currentCorrectIndex && selectedIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[selectedIndex], redTint)
        }

        // 演出（正解：フラッシュ+音+バウンス / 不正解：音）
        if (isCorrect) playCorrectEffect() else playWrongEffect()

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

            // ポイント履歴を記録（正解時のみ）
            if (addPoint > 0) {
                db.pointHistoryDao().insert(
                    PointHistoryEntity(
                        mode = currentMode,
                        dateEpochDay = today,
                        delta = addPoint
                    )
                )
            }

            val updated = current.copy(
                level = newLevel,
                nextDueDate = nextDue,
                lastAnsweredAt = System.currentTimeMillis()
            )
            progressDao.upsert(updated)

            // ★ここをSnackbarに変更
            showFeedbackSnackbar(isCorrect, addPoint)

            Log.d(
                "ANSWER_TEST",
                "wordId=$wordId isCorrect=$isCorrect addPoint=$addPoint newLevel=$newLevel nextDue=$nextDue totalPoint=${pointManager.getTotal()}"
            )

            updatePointView()

            delay(1000)
            loadNextQuestion()
        }
    }

    private fun showFeedbackSnackbar(isCorrect: Boolean, addPoint: Int) {
        val baseColorRes = if (isCorrect) android.R.color.holo_green_dark else android.R.color.holo_red_dark
        val bgColor = ContextCompat.getColor(this, baseColorRes)

        val msg = if (isCorrect) {
            val praise = praiseMessages.random()
            "$praise +${addPoint}pt"
        } else {
            "不正解…"
        }

        // 前のSnackbarが残っていれば消す
        currentSnackbar?.dismiss()

        val root = findViewById<View>(android.R.id.content)
        currentSnackbar = Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).apply {
            // 視認性UP
            setBackgroundTint(bgColor)
            setTextColor(android.graphics.Color.WHITE)
            // 回答テンポに合わせて少し短めに
            duration = 900
            show()
        }
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPoints.text = "ポイント: $total"
        lifecycleScope.launch { updatePointStats() }
    }

    private suspend fun updatePointStats() {
        val db = AppDatabase.getInstance(this@LearningActivity)
        val histDao = db.pointHistoryDao()
        val today = LocalDate.now().toEpochDay()
        val yesterday = today - 1
        val todaySum = histDao.getSumByDate(today)
        val yesterdaySum = histDao.getSumByDate(yesterday)
        val diff = todaySum - yesterdaySum
        val diffSign = if (diff >= 0) "+" else "-"
        val diffAbs = kotlin.math.abs(diff)
        textPointStats.text = "今日: $todaySum / 前日比: $diffSign$diffAbs"
    }

    private fun speakCurrentWord() {
        val cw = currentWord ?: return
        tts?.speak(cw.word, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
    }
}