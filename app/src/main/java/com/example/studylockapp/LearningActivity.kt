package com.example.studylockapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.ProgressCalculator
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.WordProgressEntity
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.PointHistoryEntity
import com.example.studylockapp.data.db.PointHistoryDao
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

class LearningActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val MODE_MEANING = "meaning" // 英 -> 日
        const val MODE_LISTENING = "listening" // 音 -> 英
        const val MODE_JA_TO_EN = "japanese_to_english" // 日 -> 英
        const val MODE_EN_EN_1 = "english_english_1" // 英(単語) -> 英(意味)
        const val MODE_EN_EN_2 = "english_english_2" // 英(意味) -> 英(単語)
    }

    private val viewModel: LearningViewModel by viewModels()

    private var currentMode = MODE_MEANING
    private var gradeFilter: String = "All"
    private var includeOtherGradesReview: Boolean = false
    private var currentWord: WordEntity? = null
    private var allWords: List<WordEntity> = emptyList()
    private var allWordsFull: List<WordEntity> = emptyList()
    private var tts: TextToSpeech? = null

    private lateinit var settings: AppSettings
    private var currentCorrectIndex: Int = -1
    private lateinit var defaultChoiceTints: List<ColorStateList?>

    private val greenTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_correct)) }
    private val redTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_wrong)) }

    private var soundPool: SoundPool? = null
    private var seCorrectId: Int = 0
    private var seWrongId: Int = 0
    private var currentSnackbar: Snackbar? = null

    private lateinit var textQuestionTitle: TextView
    private lateinit var textQuestionBody: TextView
    private lateinit var textPoints: TextView
    private lateinit var textPointStats: TextView
    private var textCurrentGrade: TextView? = null
    private var textTotalWords: TextView? = null
    private lateinit var textFeedback: TextView

    // Chips (for selection and stats)
    private lateinit var chipGroupMode: ChipGroup
    private lateinit var chipModeMeaning: Chip
    private lateinit var chipModeListening: Chip
    private lateinit var chipModeJaToEn: Chip
    private lateinit var chipModeEnEn1: Chip
    private lateinit var chipModeEnEn2: Chip

    private lateinit var choiceButtons: List<Button>
    private lateinit var buttonPlayAudio: ImageButton
    private lateinit var buttonSoundSettings: ImageButton
    private var checkIncludeOtherGrades: CheckBox? = null
    private var checkboxAutoPlayAudio: CheckBox? = null

    private data class ModeStats(
        val review: Int,
        val newCount: Int,
        val total: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        settings = AppSettings(this)
        gradeFilter = intent.getStringExtra("gradeFilter") ?: "All"

        initViews()
        observeViewModel()

        checkIncludeOtherGrades = findViewById<CheckBox?>(R.id.checkbox_include_other_grades)?.apply {
            isChecked = true
            includeOtherGradesReview = true
            setOnCheckedChangeListener { _, isChecked ->
                includeOtherGradesReview = isChecked
                loadNextQuestion()
            }
        }

        checkboxAutoPlayAudio = findViewById<CheckBox?>(R.id.checkbox_auto_play_audio)?.apply {
            isChecked = true
        }

        textFeedback.visibility = View.GONE
        defaultChoiceTints = choiceButtons.map { ViewCompat.getBackgroundTintList(it) }

        tts = TextToSpeech(this, this)
        initSoundPool()

        // チップ選択リスナー
        chipGroupMode.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val checkedId = checkedIds[0]
            val newMode = when (checkedId) {
                R.id.chip_mode_meaning -> MODE_MEANING
                R.id.chip_mode_listening -> MODE_LISTENING
                R.id.chip_mode_ja_to_en -> MODE_JA_TO_EN
                R.id.chip_mode_en_en_1 -> MODE_EN_EN_1
                R.id.chip_mode_en_en_2 -> MODE_EN_EN_2
                else -> MODE_MEANING
            }

            if (currentMode != newMode) {
                currentMode = newMode
                updateStudyStatsView()
                loadNextQuestion()
            }
        }

        choiceButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { onChoiceSelected(index) }
        }

        buttonPlayAudio.setOnClickListener { speakCurrentWord() }

        buttonSoundSettings.setOnClickListener {
            try {
                startActivity(Intent().apply {
                    setClassName(this@LearningActivity, "com.example.studylockapp.SoundSettingsActivity")
                })
            } catch (e: Exception) {
                Toast.makeText(this, "起動に失敗", Toast.LENGTH_SHORT).show()
            }
        }

        updatePointView()

        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                if (gradeFilter != "All") importMissingWordsForGrade(gradeFilter) else 0
            }
            if (imported > 0) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.imported_count_message, imported),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            loadAllWordsThenQuestion()
        }
    }

    private fun initViews() {
        textQuestionTitle = findViewById(R.id.text_question_title)
        textQuestionBody = findViewById(R.id.text_question_body)
        textPoints = findViewById(R.id.text_points)
        textPointStats = findViewById(R.id.text_point_stats)
        textCurrentGrade = findViewById(R.id.text_current_grade)
        textTotalWords = findViewById(R.id.text_total_words)
        textFeedback = findViewById(R.id.text_feedback)

        chipGroupMode = findViewById(R.id.chip_group_mode)
        chipModeMeaning = findViewById(R.id.chip_mode_meaning)
        chipModeListening = findViewById(R.id.chip_mode_listening)
        chipModeJaToEn = findViewById(R.id.chip_mode_ja_to_en)
        chipModeEnEn1 = findViewById(R.id.chip_mode_en_en_1)
        chipModeEnEn2 = findViewById(R.id.chip_mode_en_en_2)

        buttonPlayAudio = findViewById(R.id.button_play_audio)
        buttonSoundSettings = findViewById(R.id.button_sound_settings)

        choiceButtons = listOf(
            findViewById(R.id.button_choice_1),
            findViewById(R.id.button_choice_2),
            findViewById(R.id.button_choice_3),
            findViewById(R.id.button_choice_4),
            findViewById(R.id.button_choice_5),
            findViewById(R.id.button_choice_6)
        )
    }

    private fun observeViewModel() {
        viewModel.gradeName.observe(this) { gradeName ->
            textCurrentGrade?.text = gradeName
        }
        viewModel.wordCount.observe(this) { count ->
            textTotalWords?.text = getString(R.string.label_word_count, count)
        }
    }

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()
        seCorrectId = loadSeIfExists("se_correct")
        seWrongId = loadSeIfExists("se_wrong")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        applyTtsParams()
        updatePointView()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        soundPool?.release()
        soundPool = null
        currentSnackbar?.dismiss()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            applyTtsParams()
        }
    }

    private fun applyTtsParams() {
        val rate = settings.getTtsSpeed()
        val pitch = settings.getTtsPitch()
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
    }

    private fun loadSeIfExists(rawName: String): Int {
        val resId = resources.getIdentifier(rawName, "raw", packageName)
        return if (resId != 0) soundPool?.load(this, resId, 1) ?: 0 else 0
    }

    private fun flashCorrectBackground() {
        val root = findViewById<View>(android.R.id.content)
        val flashColor = ContextCompat.getColor(this, R.color.correct_flash)
        if (root.width == 0 || root.height == 0) return

        val drawable = ColorDrawable(flashColor)
        drawable.setBounds(0, 0, root.width, root.height)
        drawable.alpha = 0
        root.overlay.add(drawable)

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

    private fun playCorrectEffect() {
        flashCorrectBackground()
        val vol = settings.seCorrectVolume
        if (seCorrectId != 0) soundPool?.play(seCorrectId, vol, vol, 1, 0, 1f)

        val idx = currentCorrectIndex
        val v = choiceButtons.getOrNull(idx) ?: return
        v.animate().cancel()
        v.scaleX = 1f; v.scaleY = 1f
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
    }

    private fun playWrongEffect() {
        val vol = settings.seWrongVolume
        if (seWrongId != 0) soundPool?.play(seWrongId, vol, vol, 1, 0, 1f)
    }

    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1000L

    private fun calcNextDueAtSec(isCorrect: Boolean, currentLevel: Int, nowSec: Long): Pair<Int, Long> {
        val newLevel = if (isCorrect) currentLevel + 1 else maxOf(0, currentLevel - 2)
        val zone = settings.getAppZoneId()
        val result: Pair<Int, Long> = when {
            !isCorrect -> newLevel to (nowSec + settings.wrongRetrySec)
            newLevel == 1 -> newLevel to (nowSec + settings.level1RetrySec)
            else -> {
                val days = when (newLevel) {
                    2 -> 1; 3 -> 3; 4 -> 7; 5 -> 14; 6 -> 30; 7 -> 60; else -> 90
                }
                val baseDate = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
                val dueDate = baseDate.plusDays(days.toLong())
                val dueAtSec = dueDate.atStartOfDay(zone).toEpochSecond()
                newLevel to dueAtSec
            }
        }
        return result
    }

    private suspend fun importMissingWordsForGrade(grade: String): Int = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(this@LearningActivity)
        val wordDao = db.wordDao()
        val csvWords = readCsvWords().filter { it.grade == grade }
        if (csvWords.isEmpty()) return@withContext 0
        val existing = wordDao.getAll().filter { it.grade == grade }.associateBy { it.word }
        val missing = csvWords.filter { existing[it.word] == null }
        if (missing.isNotEmpty()) wordDao.insertAll(missing)
        missing.size
    }

    private fun readCsvWords(): List<WordEntity> {
        val result = mutableListOf<WordEntity>()
        try {
            resources.openRawResource(R.raw.words).use { input ->
                BufferedReader(InputStreamReader(input)).useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val cols = line.split(",")
                        if (cols.size >= 8) {
                            result.add(WordEntity(
                                no = cols[0].toIntOrNull() ?: 0,
                                grade = cols[1],
                                word = cols[2],
                                japanese = cols[3],
                                english = cols[4],
                                pos = cols[5],
                                category = cols[6],
                                actors = cols[7]
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("LearningActivity", "Error reading CSV", e) }
        return result
    }

    private fun loadAllWordsThenQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val words = db.wordDao().getAll()
            allWordsFull = words
            allWords = if (gradeFilter == "All") words else words.filter { it.grade == gradeFilter }

            viewModel.setGradeInfo(gradeFilter, allWords)

            updateStudyStatsView()
            loadNextQuestion()
        }
    }

    private suspend fun computeModeStats(wordIdSet: Set<Int>, mode: String, nowSec: Long): ModeStats {
        val db = AppDatabase.getInstance(this@LearningActivity)
        val progressDao = db.wordProgressDao()
        val dueCount = progressDao.getDueWordIdsOrdered(mode, nowSec).count { it in wordIdSet }
        val startedIds = progressDao.getProgressIds(mode).toSet()
        val newCount = wordIdSet.count { it !in startedIds }
        return ModeStats(
            review = dueCount,
            newCount = newCount,
            total = wordIdSet.size
        )
    }

    private fun updateStudyStatsView() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (allWords.isEmpty()) return@launch
            val wordIdSet: Set<Int> = allWords.map { it.no }.toSet()
            val nowSec = nowEpochSec()

            val mStats = computeModeStats(wordIdSet, MODE_MEANING, nowSec)
            val lStats = computeModeStats(wordIdSet, MODE_LISTENING, nowSec)
            val jeStats = computeModeStats(wordIdSet, MODE_JA_TO_EN, nowSec)
            val ee1Stats = computeModeStats(wordIdSet, MODE_EN_EN_1, nowSec)
            val ee2Stats = computeModeStats(wordIdSet, MODE_EN_EN_2, nowSec)

            withContext(Dispatchers.Main) {
                chipModeMeaning.text = "${getString(R.string.mode_meaning)} (復:${mStats.review})"
                chipModeListening.text = "${getString(R.string.mode_listening)} (復:${lStats.review})"
                chipModeJaToEn.text = "${getString(R.string.mode_japanese_to_english)} (復:${jeStats.review})"
                chipModeEnEn1.text = "${getString(R.string.mode_english_english_1)} (復:${ee1Stats.review})"
                chipModeEnEn2.text = "${getString(R.string.mode_english_english_2)} (復:${ee2Stats.review})"
            }
        }
    }

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
            val nowSec = nowEpochSec()

            resetChoiceButtons()

            if (allWordsFull.isEmpty()) {
                showNoQuestion()
                return@launch
            }

            val wordMapFiltered = allWords.associateBy { it.no }
            val wordMapAll = allWordsFull.associateBy { it.no }
            val dueIdsOrdered = progressDao.getDueWordIdsOrdered(currentMode, nowSec)
            
            val dueWords = if (includeOtherGradesReview && gradeFilter != "All") {
                dueIdsOrdered.mapNotNull { wordMapAll[it] }
            } else {
                dueIdsOrdered.mapNotNull { wordMapFiltered[it] }
            }

            val progressedIds = progressDao.getProgressIds(currentMode).toSet()
            val newWords = if (gradeFilter == "All") {
                allWordsFull.filter { it.no !in progressedIds }
            } else {
                allWords.filter { it.no !in progressedIds }
            }

            val nextWord = when {
                dueWords.isNotEmpty() -> dueWords.first()
                newWords.isNotEmpty() -> newWords.random()
                else -> null
            }

            if (nextWord == null) {
                showNoQuestion()
                return@launch
            }

            currentWord = nextWord
            val choicePool = if (includeOtherGradesReview && gradeFilter != "All") allWordsFull else {
                if (gradeFilter == "All") allWordsFull else allWords
            }

            val choices = buildChoices(nextWord, choicePool, 6)
            val (title, body, options) = formatQuestionAndOptions(nextWord, choices, currentMode)
            
            textQuestionTitle.text = title
            textQuestionBody.text = body
            textQuestionBody.visibility = if (body.isEmpty()) View.GONE else View.VISIBLE
            
            choiceButtons.forEach { it.textSize = if (currentMode == MODE_EN_EN_1) 12f else 14f }
            
            choiceButtons.zip(options).forEach { (btn, txt) -> btn.text = txt }

            val correctStr = when (currentMode) {
                MODE_MEANING -> nextWord.japanese ?: ""
                MODE_LISTENING -> nextWord.word
                MODE_JA_TO_EN -> nextWord.word
                MODE_EN_EN_1 -> nextWord.english ?: "" // Word -> Meaning
                MODE_EN_EN_2 -> nextWord.word // Meaning -> Word
                else -> nextWord.japanese ?: ""
            }
            currentCorrectIndex = options.indexOf(correctStr)

            // ImageButtonの表示制御
            when (currentMode) {
                MODE_JA_TO_EN -> {
                    checkboxAutoPlayAudio?.visibility = View.GONE
                    buttonPlayAudio.visibility = View.GONE
                }
                MODE_MEANING, MODE_EN_EN_1, MODE_EN_EN_2 -> {
                    checkboxAutoPlayAudio?.visibility = View.VISIBLE
                    buttonPlayAudio.visibility = View.VISIBLE
                }
                else -> {
                    checkboxAutoPlayAudio?.visibility = View.GONE
                    buttonPlayAudio.visibility = View.VISIBLE
                }
            }

            if (currentMode == MODE_LISTENING) {
                speakCurrentWord()
            } else if (checkboxAutoPlayAudio?.isChecked == true && checkboxAutoPlayAudio?.visibility == View.VISIBLE) {
                if (currentMode == MODE_EN_EN_2) {
                    val textToSpeak = nextWord.english ?: ""
                    speakText(textToSpeak)
                } else {
                    speakCurrentWord()
                }
            }
        }
    }

    private fun showNoQuestion() {
        textQuestionTitle.text = getString(R.string.no_question_available)
        textQuestionBody.text = ""
        textQuestionBody.visibility = View.GONE
        currentWord = null
        choiceButtons.forEach { it.text = "----"; it.isEnabled = false }
    }

    private fun buildChoices(correct: WordEntity, pool: List<WordEntity>, count: Int): List<WordEntity> {
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

    private fun formatQuestionAndOptions(
        correct: WordEntity,
        choices: List<WordEntity>,
        mode: String
    ): Triple<String, String, List<String>> {
        return when (mode) {
            MODE_MEANING -> {
                Triple("この英単語の意味は？", correct.word, choices.map { it.japanese ?: "" })
            }
            MODE_LISTENING -> {
                Triple("音声を聞いて正しい英単語を選んでください", "", choices.map { it.word })
            }
            MODE_JA_TO_EN -> {
                Triple("この日本語に対応する英単語は？", correct.japanese ?: "", choices.map { it.word })
            }
            MODE_EN_EN_1 -> {
                Triple("この単語の意味(定義)は？", correct.word, choices.map { it.english ?: "" })
            }
            MODE_EN_EN_2 -> {
                Triple("この意味(定義)に対応する単語は？", correct.english ?: "", choices.map { it.word })
            }
            else -> Triple("", "", emptyList())
        }
    }

    private fun onChoiceSelected(selectedIndex: Int) {
        val cw = currentWord ?: return
        val selectedText = choiceButtons.getOrNull(selectedIndex)?.text?.toString() ?: return

        val isCorrect = when (currentMode) {
            MODE_MEANING -> selectedText == (cw.japanese ?: "")
            MODE_LISTENING -> selectedText == cw.word
            MODE_JA_TO_EN -> selectedText == cw.word
            MODE_EN_EN_1 -> selectedText == (cw.english ?: "")
            MODE_EN_EN_2 -> selectedText == cw.word
            else -> false
        }

        choiceButtons.forEach { it.isClickable = false }
        if (currentCorrectIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[currentCorrectIndex], greenTint)
        }
        if (!isCorrect && selectedIndex != currentCorrectIndex && selectedIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[selectedIndex], redTint)
        }

        if (isCorrect) playCorrectEffect() else playWrongEffect()
        onAnsweredInternal(cw.no, isCorrect)
    }

    private fun onAnsweredInternal(wordId: Int, isCorrect: Boolean) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val pointManager = PointManager(this@LearningActivity)
            
            val nowSec = nowEpochSec()
            val current = progressDao.getProgress(wordId, currentMode)
            val currentLevel = current?.level ?: 0
            val newCount = (current?.studyCount ?: 0) + 1

            val (newLevel, nextDueAtSec) = calcNextDueAtSec(isCorrect, currentLevel, nowSec)
            val addPoint = ProgressCalculator.calcPoint(isCorrect, currentLevel)
            pointManager.add(addPoint)

            if (addPoint > 0) {
                db.pointHistoryDao().insert(
                    PointHistoryEntity(
                        mode = currentMode,
                        dateEpochDay = LocalDate.now(settings.getAppZoneId()).toEpochDay(),
                        delta = addPoint
                    )
                )
            }

            progressDao.upsert(
                WordProgressEntity(
                    wordId = wordId,
                    mode = currentMode,
                    level = newLevel,
                    nextDueAtSec = nextDueAtSec,
                    lastAnsweredAt = System.currentTimeMillis(),
                    studyCount = newCount
                )
            )

            updateStudyStatsView()
            showFeedbackSnackbar(isCorrect, addPoint)
            updatePointView()

            delay(settings.answerIntervalMs)
            loadNextQuestion()
        }
    }

    private fun showFeedbackSnackbar(isCorrect: Boolean, addPoint: Int) {
        val bgColor = ContextCompat.getColor(
            this,
            if (isCorrect) R.color.snackbar_correct_bg else R.color.snackbar_wrong_bg
        )
        val msg = if (isCorrect) {
            val praise = listOf("すごい！", "その調子！", "天才！", "完璧！", "いいね！", "ナイス！").random()
            "$praise +${addPoint}pt"
        } else "不正解…"

        currentSnackbar?.dismiss()
        val root = findViewById<View>(android.R.id.content)
        currentSnackbar = Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).apply {
            setBackgroundTint(bgColor)
            setTextColor(android.graphics.Color.WHITE)
            duration = settings.answerIntervalMs.toInt().coerceIn(600, 4000)
            show()
        }
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPoints.text = "保有ポイント: $total"
        lifecycleScope.launch { updatePointStats() }
    }

    private suspend fun updatePointStats() {
        val db = AppDatabase.getInstance(this@LearningActivity)
        val histDao = db.pointHistoryDao()
        val zone = settings.getAppZoneId()
        val today = LocalDate.now(zone).toEpochDay()
        val yesterday = today - 1
        val todaySum = histDao.getSumByDate(today)
        val yesterdaySum = histDao.getSumByDate(yesterday)
        val diff = todaySum - yesterdaySum
        val diffSign = if (diff >= 0) "+" else "-"
        textPointStats.text = "今日: $todaySum / 前日比: $diffSign${abs(diff)}"
    }

    private fun speakCurrentWord() {
        val cw = currentWord ?: return
        speakText(cw.word)
    }

    private fun speakText(text: String) {
        if (text.isEmpty()) return
        applyTtsParams()
        val rawVol = settings.ttsVolume
        val vol = if (rawVol > 1f) (rawVol / 100f).coerceIn(0f, 1f) else rawVol.coerceIn(0f, 1f)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts-${System.currentTimeMillis()}")
    }
}