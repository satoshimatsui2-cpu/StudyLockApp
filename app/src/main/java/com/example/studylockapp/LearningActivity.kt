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
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.*
import com.example.studylockapp.learning.AnswerResult
import com.example.studylockapp.learning.QuestionUiState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * 学習画面のアクティビティ。
 * * 役割:
 * 1. 従来の単語学習モード（Meaning, Listening等）のロジック実行
 * 2. 新しい会話学習モード（ViewModel主導）のUIホスティング
 * 3. TTS (TextToSpeech) と SoundPool の管理
 * 4. 学習履歴（DB）の更新と統計表示
 */
class LearningActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // region Constants & Modes
    companion object {
        // Standard Modes
        const val MODE_MEANING = "meaning"
        const val MODE_LISTENING = "listening"
        const val MODE_LISTENING_JP = "listening_jp"
        const val MODE_JA_TO_EN = "japanese_to_english"
        const val MODE_EN_EN_1 = "english_english_1"
        const val MODE_EN_EN_2 = "english_english_2"

        // Test/Beta Modes
        const val MODE_TEST_FILL_BLANK = "test_fill_blank"
        const val MODE_TEST_SORT = "test_sort"
        const val MODE_TEST_LISTEN_Q1 = "test_listen_q1"
        const val MODE_TEST_LISTEN_Q2 = "test_listen_q2" // Conversation Listening
    }
    // endregion

    // region ViewModel & State
    private val viewModel: LearningViewModel by viewModels()

    private var currentMode = MODE_MEANING
    private var gradeFilter: String = "All"
    private var includeOtherGradesReview: Boolean = false

    // Vocabulary Data (Legacy Logic)
    private var currentWord: WordEntity? = null
    private var allWords: List<WordEntity> = emptyList()
    private var allWordsFull: List<WordEntity> = emptyList()
    private var listeningQuestions: List<ListeningQuestion> = emptyList()

    // Answer State
    private var currentCorrectIndex: Int = -1
    private var currentStats: Map<String, ModeStats> = emptyMap()
    // endregion

    // region System Services (TTS, Sound, DB)
    private var tts: TextToSpeech? = null
    private var conversationTts: ConversationTtsManager? = null
    private var soundPool: SoundPool? = null
    private var seCorrectId: Int = 0
    private var seWrongId: Int = 0
    private lateinit var settings: AppSettings
    // endregion

    // region UI Components
    // Headers & Stats
    private lateinit var textQuestionTitle: TextView
    private lateinit var textQuestionBody: TextView
    private lateinit var textPoints: TextView
    private lateinit var textPointStats: TextView
    private var textCurrentGrade: TextView? = null
    private var textTotalWords: TextView? = null
    private lateinit var textFeedback: TextView

    // Question Area
    private lateinit var textScriptDisplay: TextView
    private lateinit var layoutActionButtons: LinearLayout
    private lateinit var buttonNextQuestion: Button
    private lateinit var buttonReplayAudio: Button
    private lateinit var choiceButtons: List<Button>
    private lateinit var defaultChoiceTints: List<ColorStateList?>

    // Controls
    private lateinit var layoutModeSelector: View
    private lateinit var selectorIconMode: ImageView
    private lateinit var selectorTextTitle: TextView
    private lateinit var selectorTextReview: TextView
    private lateinit var selectorTextNew: TextView
    private lateinit var selectorTextMaster: TextView
    private lateinit var buttonPlayAudio: ImageButton
    private lateinit var buttonSoundSettings: ImageButton
    private var checkIncludeOtherGrades: CheckBox? = null
    private var checkboxAutoPlayAudio: CheckBox? = null
    private var currentSnackbar: Snackbar? = null

    // Helper for Choice Colors
    private val greenTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_correct)) }
    private val redTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_wrong)) }
    // endregion

    private data class ModeStats(
        val review: Int,
        val newCount: Int,
        val total: Int,
        val mastered: Int
    )

    // region Lifecycle Methods
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_learning)
        setupWindowInsets()

        settings = AppSettings(this)
        gradeFilter = intent.getStringExtra("gradeFilter") ?: "All"

        // Initialize Components
        initViews()
        initMediaServices()

        // Setup Logic
        setupObservers()
        setupListeners()

        // Load Initial Data
        lifecycleScope.launch {
            loadInitialData()
        }
    }

    override fun onResume() {
        super.onResume()
        applyTtsParams()
        updatePointView()
        updateStudyStatsView()
    }

    override fun onPause() {
        super.onPause()
        conversationTts?.stop()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        conversationTts?.shutdown()
        soundPool?.release()
        currentSnackbar?.dismiss()
        super.onDestroy()
    }
    // endregion

    // region Initialization & Setup
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initViews() {
        // Find Views
        textQuestionTitle = findViewById(R.id.text_question_title)
        textQuestionBody = findViewById(R.id.text_question_body)
        textPoints = findViewById(R.id.text_points)
        textPointStats = findViewById(R.id.text_point_stats)
        textCurrentGrade = findViewById(R.id.text_current_grade)
        textTotalWords = findViewById(R.id.text_total_words)
        textFeedback = findViewById(R.id.text_feedback)

        textScriptDisplay = findViewById(R.id.text_script_display)
        layoutActionButtons = findViewById(R.id.layout_action_buttons)
        buttonNextQuestion = findViewById(R.id.button_next_question)
        buttonReplayAudio = findViewById(R.id.button_replay_audio)

        layoutModeSelector = findViewById(R.id.layout_mode_selector)
        selectorIconMode = findViewById(R.id.selector_icon_mode)
        selectorTextTitle = findViewById(R.id.selector_text_title)
        selectorTextReview = findViewById(R.id.selector_text_review)
        selectorTextNew = findViewById(R.id.selector_text_new)
        selectorTextMaster = findViewById(R.id.selector_text_master)

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
        defaultChoiceTints = choiceButtons.map { ViewCompat.getBackgroundTintList(it) }

        // Optional/Checkboxes
        checkIncludeOtherGrades = findViewById<CheckBox?>(R.id.checkbox_include_other_grades)?.apply {
            isChecked = true
            includeOtherGradesReview = true
        }
        checkboxAutoPlayAudio = findViewById<CheckBox?>(R.id.checkbox_auto_play_audio)?.apply {
            isChecked = true
        }

        // Initial Visibility
        textFeedback.visibility = View.GONE
        updatePointView()
    }

    private fun initMediaServices() {
        tts = TextToSpeech(this, this)
        conversationTts = ConversationTtsManager(this)
        initSoundPool()
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

    private suspend fun loadInitialData() {
        // Import Data
        val imported = withContext(Dispatchers.IO) {
            if (gradeFilter != "All") importMissingWordsForGrade(gradeFilter) else 0
        }
        if (imported > 0) {
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.imported_count_message, imported), Snackbar.LENGTH_SHORT).show()
        }

        // Load CSVs
        listeningQuestions = loadListeningQuestionsFromCsv()
        viewModel.listeningQuestions = listeningQuestions // Pass to ViewModel

        // Load Words and Start
        loadAllWordsThenQuestion()
    }
    // endregion

    // region Event Listeners & Observers
    private fun setupListeners() {
        layoutModeSelector.setOnClickListener { showModeSelectionSheet() }

        choiceButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { onChoiceSelected(index) }
        }

        buttonPlayAudio.setOnClickListener { speakCurrentWord() }

        buttonSoundSettings.setOnClickListener {
            runCatching {
                startActivity(Intent().apply { setClassName(this@LearningActivity, "com.example.studylockapp.SoundSettingsActivity") })
            }.onFailure { Toast.makeText(this, "起動に失敗", Toast.LENGTH_SHORT).show() }
        }

        buttonNextQuestion.setOnClickListener {
            routeNextQuestionAction()
        }

        buttonReplayAudio.setOnClickListener { speakCurrentWord() }

        checkIncludeOtherGrades?.setOnCheckedChangeListener { _, _ ->
            if (currentMode != MODE_TEST_LISTEN_Q2) loadNextQuestionLegacy()
        }
    }

    private fun setupObservers() {
        // ViewModel Data Observers
        viewModel.gradeName.observe(this) { textCurrentGrade?.text = it }
        viewModel.wordCount.observe(this) { textTotalWords?.text = getString(R.string.label_word_count, it) }

        // ViewModel State Observers (for Conversation Mode)
        lifecycleScope.launch {
            viewModel.questionUiState.collect { state ->
                handleQuestionUiState(state)
            }
        }
        lifecycleScope.launch {
            viewModel.answerResult.collect { result ->
                handleAnswerResult(result)
            }
        }
    }
    // endregion

    // region Logic: Routing & Mode Handling
    /**
     * Determines whether to use Legacy Logic or ViewModel Logic based on Current Mode.
     */
    private fun routeNextQuestionAction() {
        if (currentMode == MODE_TEST_LISTEN_Q2) {
            viewModel.loadNextQuestion()
        } else {
            loadNextQuestionLegacy()
        }
    }

    private fun handleQuestionUiState(state: QuestionUiState) {
        if (state !is QuestionUiState.Loading) {
            resetUiForNewQuestion()
        }

        when (state) {
            is QuestionUiState.Conversation -> renderConversationUi(state)
            is QuestionUiState.Loading -> { /* Show Loading if needed */ }
            is QuestionUiState.Empty -> showNoQuestion()
            else -> {
                // Other states handled by legacy logic for now
            }
        }
    }

    private fun handleAnswerResult(result: AnswerResult) {
        showFeedbackSnackbar(result)
        updatePointView()
        // Note: Legacy logic updates UI directly inside onAnsweredInternal
    }

    private fun renderConversationUi(state: QuestionUiState.Conversation) {
        textQuestionTitle.text = "会話を聞いて質問に答えてください"
        textQuestionBody.text = state.question
        textQuestionBody.visibility = View.VISIBLE
        textScriptDisplay.visibility = View.GONE

        // Hide standard controls
        checkIncludeOtherGrades?.visibility = View.GONE
        checkboxAutoPlayAudio?.visibility = View.GONE
        buttonPlayAudio.visibility = View.GONE
        layoutActionButtons.visibility = View.GONE

        // Setup Choices
        choiceButtons.forEachIndexed { index, btn ->
            if (index < state.choices.size) {
                btn.text = state.choices[index]
                btn.visibility = View.VISIBLE
                btn.isEnabled = true
                ViewCompat.setBackgroundTintList(btn, defaultChoiceTints[index])
            } else {
                btn.visibility = View.GONE
            }
        }

        // Auto Play
        val repeatScript = state.script + "\nWait: 2000\n" + state.script
        conversationTts?.playScript(repeatScript)
    }
    // endregion

    // region Logic: Legacy Word Learning (Standard Modes)
    private fun loadAllWordsThenQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val words = db.wordDao().getAll()

            allWordsFull = words
            allWords = if (gradeFilter == "All") words else words.filter { it.grade == gradeFilter }

            viewModel.setGradeInfo(gradeFilter, allWords)
            updateStudyStatsView()

            if (currentMode != MODE_TEST_LISTEN_Q2) {
                loadNextQuestionLegacy()
            }
        }
    }

    private fun loadNextQuestionLegacy() {
        lifecycleScope.launch {
            // UI Reset
            stopMedia()
            resetStandardUi()

            if (allWordsFull.isEmpty()) {
                showNoQuestion()
                return@launch
            }

            // Word Selection Logic
            val nextWord = selectNextWord()
            if (nextWord == null) {
                showNoQuestion()
                return@launch
            }
            currentWord = nextWord

            // Choices & Display Generation
            val choicePool = getChoicePool()
            val choices = buildChoices(nextWord, choicePool, 6)
            val (title, body, options) = formatQuestionAndOptions(nextWord, choices, currentMode)

            // Update Views
            textQuestionTitle.text = title
            textQuestionBody.text = body
            textQuestionBody.visibility = if (body.isEmpty()) View.GONE else View.VISIBLE

            choiceButtons.forEach { it.textSize = if (currentMode == MODE_EN_EN_1) 12f else 14f }
            choiceButtons.zip(options).forEach { (btn, txt) -> btn.text = txt }

            // Set Correct Answer Index
            val correctStr = getCorrectStringForMode(nextWord, currentMode)
            currentCorrectIndex = options.indexOf(correctStr)

            // Auto Play Audio Handling
            handleAutoPlayAudio(nextWord)
        }
    }

    private fun onChoiceSelected(selectedIndex: Int) {
        // Branch to ViewModel if needed
        if (currentMode == MODE_TEST_LISTEN_Q2) {
            viewModel.submitAnswer(selectedIndex)
            return
        }

        // Legacy Processing
        val cw = currentWord ?: return
        val selectedText = choiceButtons.getOrNull(selectedIndex)?.text?.toString() ?: return

        val isCorrect = checkLegacyAnswer(cw, selectedText)

        // Visual Feedback (Button Colors)
        choiceButtons.forEach { it.isClickable = false }
        if (currentCorrectIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[currentCorrectIndex], greenTint)
        }
        if (!isCorrect && selectedIndex != currentCorrectIndex && selectedIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[selectedIndex], redTint)
        }

        if (isCorrect) playCorrectEffect() else playWrongEffect()
        processAnswerResultLegacy(cw.no, isCorrect)
    }

    private fun processAnswerResultLegacy(wordId: Int, isCorrect: Boolean) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val pointManager = PointManager(this@LearningActivity)
            val nowSec = nowEpochSec()

            // Calculate Progress (Spaced Repetition)
            val current = withContext(Dispatchers.IO) { progressDao.getProgress(wordId, currentMode) }
            val currentLevel = current?.level ?: 0
            val (newLevel, nextDueAtSec) = calcNextDueAtSec(isCorrect, currentLevel, nowSec)
            val addPoint = ProgressCalculator.calcPoint(isCorrect, currentLevel)

            // Save to DB
            pointManager.add(addPoint)
            if (addPoint > 0) {
                withContext(Dispatchers.IO) {
                    db.pointHistoryDao().insert(PointHistoryEntity(mode = currentMode, dateEpochDay = LocalDate.now(settings.getAppZoneId()).toEpochDay(), delta = addPoint))
                }
            }

            withContext(Dispatchers.IO) {
                progressDao.upsert(WordProgressEntity(wordId = wordId, mode = currentMode, level = newLevel, nextDueAtSec = nextDueAtSec, lastAnsweredAt = System.currentTimeMillis(), studyCount = (current?.studyCount ?: 0) + 1))
                db.studyLogDao().insert(WordStudyLogEntity(wordId = wordId, mode = currentMode, learnedAt = System.currentTimeMillis()))
            }

            // Post-Save UI Update
            updateStudyStatsView()
            showFeedbackSnackbarInternal(isCorrect, addPoint)
            updatePointView()

            if (!isCorrect) {
                layoutActionButtons.visibility = View.VISIBLE
            } else {
                delay(settings.answerIntervalMs)
                loadNextQuestionLegacy() // Loop
            }
        }
    }
    // endregion

    // region Logic: Word Selection & Formatting (Helper Methods)
    private suspend fun selectNextWord(): WordEntity? {
        val db = AppDatabase.getInstance(this)
        val progressDao = db.wordProgressDao()
        val nowSec = nowEpochSec()
        val dueIdsOrdered = progressDao.getDueWordIdsOrdered(currentMode, nowSec)

        val wordMapFiltered = allWords.associateBy { it.no }
        val wordMapAll = allWordsFull.associateBy { it.no }

        // 1. Check Review Words
        val dueWords = if (includeOtherGradesReview && gradeFilter != "All") {
            dueIdsOrdered.mapNotNull { wordMapAll[it] }
        } else {
            dueIdsOrdered.mapNotNull { wordMapFiltered[it] }
        }
        if (dueWords.isNotEmpty()) return dueWords.first()

        // 2. Check New Words
        val progressedIds = progressDao.getProgressIds(currentMode).toSet()
        val newWords = if (gradeFilter == "All") {
            allWordsFull.filter { it.no !in progressedIds }
        } else {
            allWords.filter { it.no !in progressedIds }
        }
        return if (newWords.isNotEmpty()) newWords.random() else null
    }

    private fun getChoicePool(): List<WordEntity> {
        return if (includeOtherGradesReview && gradeFilter != "All") allWordsFull else {
            if (gradeFilter == "All") allWordsFull else allWords
        }
    }

    private fun checkLegacyAnswer(word: WordEntity, selectedText: String): Boolean {
        return when (currentMode) {
            MODE_MEANING, MODE_LISTENING_JP -> selectedText == (word.japanese ?: "")
            MODE_LISTENING, MODE_JA_TO_EN, MODE_EN_EN_2 -> selectedText == word.word
            MODE_EN_EN_1 -> selectedText == (word.description ?: "")
            else -> false
        }
    }

    private fun getCorrectStringForMode(word: WordEntity, mode: String): String {
        return when (mode) {
            MODE_MEANING, MODE_LISTENING_JP -> word.japanese ?: ""
            MODE_LISTENING, MODE_JA_TO_EN, MODE_EN_EN_2 -> word.word
            MODE_EN_EN_1 -> word.description ?: ""
            else -> word.japanese ?: ""
        }
    }

    private fun buildChoices(correct: WordEntity, pool: List<WordEntity>, count: Int): List<WordEntity> {
        val candidates = pool.filter { it.no != correct.no }
        if (candidates.isEmpty()) return listOf(correct)

        val distractors = when (currentMode) {
            MODE_LISTENING, MODE_LISTENING_JP -> getListeningChoices(correct, candidates, count - 1)
            else -> getStandardChoices(correct, candidates, count - 1)
        }
        return (distractors + correct).shuffled()
    }

    // Existing choice generation logic maintained (getStandardChoices, getListeningChoices, formatQuestionAndOptions)
    // ... [These methods are pure logic and kept as is, but moved to this region for organization] ...
    private fun getStandardChoices(correct: WordEntity, candidates: List<WordEntity>, count: Int): List<WordEntity> {
        val sameGradePool = candidates.filter { it.grade == correct.grade }
        if (sameGradePool.isEmpty()) return candidates.shuffled().take(count)

        val correctSmallTopic = correct.smallTopicId
        val sameSmallTopic = if (!correctSmallTopic.isNullOrEmpty()) {
            sameGradePool.filter { !it.smallTopicId.isNullOrEmpty() && it.smallTopicId == correctSmallTopic }.shuffled()
        } else emptyList()

        val pickedIds = sameSmallTopic.map { it.no }.toSet()
        val correctMediumCategory = correct.mediumCategoryId
        val sameMediumCategory = if (!correctMediumCategory.isNullOrEmpty()) {
            sameGradePool.filter {
                it.no !in pickedIds && !it.mediumCategoryId.isNullOrEmpty() && it.mediumCategoryId == correctMediumCategory
            }.shuffled()
        } else emptyList()

        val others = sameGradePool.filter {
            it.no !in pickedIds && (it.mediumCategoryId != correctMediumCategory || correctMediumCategory.isNullOrEmpty())
        }.shuffled()

        return (sameSmallTopic + sameMediumCategory + others).take(count)
    }

    private fun getListeningChoices(correct: WordEntity, candidates: List<WordEntity>, count: Int): List<WordEntity> {
        val correctGradeVal = correct.grade?.toIntOrNull() ?: 0
        val correctWord = correct.word
        val correctLen = correctWord.length
        val prefix2 = correctWord.take(2).lowercase()
        val prefix1 = correctWord.take(1).lowercase()

        val validPool = candidates.filter { word ->
            val wWord = word.word
            val gVal = word.grade?.toIntOrNull() ?: 0
            val lenDiff = abs(wWord.length - correctLen)
            gVal >= correctGradeVal && lenDiff <= 3
        }

        val poolToUse = if (validPool.size < count) {
            candidates.filter { abs(it.word.length - correctLen) <= 3 }
        } else {
            validPool
        }

        val priority1 = poolToUse.filter { it.word.lowercase().startsWith(prefix2) }.shuffled()
        val p1Ids = priority1.map { it.no }.toSet()
        val priority2 = poolToUse.filter { it.no !in p1Ids && it.word.lowercase().startsWith(prefix1) }.shuffled()
        val p1p2Ids = p1Ids + priority2.map { it.no }
        val priority3 = poolToUse.filter { it.no !in p1p2Ids && abs(it.word.length - correctLen) <= 1 }.shuffled()
        val others = poolToUse.filter { it.no !in p1p2Ids && it.no !in priority3.map { w -> w.no } }.shuffled()

        val result = (priority1 + priority2 + priority3 + others).take(count)
        if (result.size < count) {
            val existingIds = result.map { it.no }.toSet() + correct.no
            val remainder = candidates.filter { it.no !in existingIds }.shuffled().take(count - result.size)
            return result + remainder
        }
        return result
    }

    private fun formatQuestionAndOptions(correct: WordEntity, choices: List<WordEntity>, mode: String): Triple<String, String, List<String>> {
        return when (mode) {
            MODE_MEANING -> Triple("この英単語の意味は？", correct.word, choices.map { it.japanese ?: "" })
            MODE_LISTENING -> Triple("音声を聞いて正しい英単語を選んでください", "", choices.map { it.word })
            MODE_LISTENING_JP -> Triple("音声を聞いて正しい意味を選んでください", "", choices.map { it.japanese ?: "" })
            MODE_JA_TO_EN -> Triple("この日本語に対応する英単語は？", correct.japanese ?: "", choices.map { it.word })
            MODE_EN_EN_1 -> Triple("この単語の意味(定義)は？", correct.word, choices.map { it.description ?: "" })
            MODE_EN_EN_2 -> Triple("この意味(定義)に対応する単語は？", correct.description ?: "", choices.map { it.word })
            else -> Triple("", "", emptyList())
        }
    }
    // endregion

    // region UI Helpers (Update, Reset, Effects)
    private fun resetUiForNewQuestion() {
        textFeedback.visibility = View.GONE
        textScriptDisplay.visibility = View.GONE
        choiceButtons.forEachIndexed { index, button ->
            ViewCompat.setBackgroundTintList(button, defaultChoiceTints[index])
            button.isEnabled = true
        }
        layoutActionButtons.visibility = View.GONE
    }

    private fun resetStandardUi() {
        resetUiForNewQuestion()
        choiceButtons.forEach { btn ->
            btn.isClickable = true
            btn.alpha = 1f
            btn.visibility = View.VISIBLE
        }
        if (choiceButtons.size >= 6) {
            (choiceButtons[4].parent as? View)?.visibility = View.VISIBLE
        }
        currentCorrectIndex = -1
        checkIncludeOtherGrades?.visibility = View.VISIBLE
        buttonPlayAudio.visibility = View.VISIBLE
    }

    private fun showNoQuestion() {
        textQuestionTitle.text = getString(R.string.no_question_available)
        textQuestionBody.text = ""
        textQuestionBody.visibility = View.GONE
        currentWord = null
        choiceButtons.forEach { it.text = "----"; it.isEnabled = false }
    }

    private fun showFeedbackSnackbar(result: AnswerResult) {
        // Shared logic for sound and visual feedback
        val vol = if (result.isCorrect) settings.seCorrectVolume else settings.seWrongVolume
        val seId = if (result.isCorrect) seCorrectId else seWrongId
        if (seId != 0) soundPool?.play(seId, vol, vol, 1, 0, 1f)

        val bgColor = ContextCompat.getColor(this, if (result.isCorrect) R.color.snackbar_correct_bg else R.color.snackbar_wrong_bg)
        choiceButtons.forEach { it.isEnabled = false }

        currentSnackbar?.dismiss()
        val root = findViewById<View>(android.R.id.content)
        currentSnackbar = Snackbar.make(root, result.feedback, Snackbar.LENGTH_INDEFINITE).apply {
            setBackgroundTint(bgColor)
            setTextColor(android.graphics.Color.WHITE)
            setAction("次へ") { viewModel.loadNextQuestion() }
            show()
        }
        layoutActionButtons.visibility = View.VISIBLE
    }

    private fun showFeedbackSnackbarInternal(isCorrect: Boolean, addPoint: Int) {
        val bgColor = ContextCompat.getColor(this, if (isCorrect) R.color.snackbar_correct_bg else R.color.snackbar_wrong_bg)
        val msg = if (isCorrect) {
            val praise = listOf("すごい！", "その調子！", "天才！", "完璧！", "いいね！", "ナイス！").random()
            "$praise +${addPoint}pt"
        } else "不正解…"

        currentSnackbar?.dismiss()
        currentSnackbar = Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).apply {
            setBackgroundTint(bgColor)
            setTextColor(android.graphics.Color.WHITE)
            duration = settings.answerIntervalMs.toInt().coerceIn(600, 4000)
            show()
        }
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
                override fun onAnimationEnd(animation: Animator) { root.overlay.remove(drawable) }
            })
            start()
        }
    }

    private fun playCorrectEffect() {
        flashCorrectBackground()
        val vol = settings.seCorrectVolume
        if (seCorrectId != 0) soundPool?.play(seCorrectId, vol, vol, 1, 0, 1f)

        val v = choiceButtons.getOrNull(currentCorrectIndex) ?: return
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

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPoints.text = "保有ポイント: $total"
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val today = LocalDate.now(settings.getAppZoneId()).toEpochDay()
            val todaySum = db.pointHistoryDao().getSumByDate(today)
            val yesterdaySum = db.pointHistoryDao().getSumByDate(today - 1)
            val diff = todaySum - yesterdaySum
            textPointStats.text = "今日: $todaySum / 前日比: ${if (diff >= 0) "+" else "-"}${abs(diff)}"
        }
    }
    // endregion

    // region Statistics & Mode Selection
    private fun showModeSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        val view = layoutInflater.inflate(R.layout.layout_mode_selection_sheet, null)
        dialog.setContentView(view)

        // Helper to configure each row
        fun setupRow(rowId: Int, modeKey: String, title: String, iconRes: Int, colorRes: Int, isTestMode: Boolean = false) {
            val card = view.findViewById<MaterialCardView>(rowId) ?: return
            val icon = card.findViewById<ImageView>(R.id.icon_mode)
            val textTitle = card.findViewById<TextView>(R.id.text_mode_title)
            val textReview = card.findViewById<TextView>(R.id.text_stat_review)
            val textNew = card.findViewById<TextView>(R.id.text_stat_new)
            val textMaster = card.findViewById<TextView>(R.id.text_stat_master)

            textTitle.text = title
            icon.setImageResource(iconRes)
            val color = ContextCompat.getColor(this, colorRes)
            icon.setColorFilter(color)
            ViewCompat.setBackgroundTintList(card.findViewById(R.id.icon_container), ColorStateList.valueOf(adjustAlpha(color, 0.15f)))

            val stats = currentStats[modeKey]
            textReview.text = stats?.review?.toString() ?: "-"
            textNew.text = stats?.newCount?.toString() ?: "-"
            textMaster.text = if ((stats?.total ?: 0) > 0) "${stats!!.mastered * 100 / stats.total}%" else "-"

            if (isTestMode && modeKey != MODE_TEST_LISTEN_Q2) card.alpha = 0.5f

            if (currentMode == modeKey) {
                card.strokeColor = color
                card.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                card.setCardBackgroundColor(adjustAlpha(color, 0.05f))
            }

            card.setOnClickListener {
                currentMode = modeKey
                updateStudyStatsView()
                if (modeKey == MODE_TEST_LISTEN_Q2) viewModel.setMode(modeKey) else loadNextQuestionLegacy()
                dialog.dismiss()
            }
        }

        // Setup Rows
        setupRow(R.id.row_meaning, MODE_MEANING, getString(R.string.mode_meaning), R.drawable.ic_flash_cards_24, R.color.mode_indigo)
        setupRow(R.id.row_listening, MODE_LISTENING, getString(R.string.mode_listening), R.drawable.ic_headphones_24, R.color.mode_teal)
        setupRow(R.id.row_listening_jp, MODE_LISTENING_JP, getString(R.string.mode_listening_jp), R.drawable.ic_headphones_24, R.color.mode_teal)
        setupRow(R.id.row_ja_to_en, MODE_JA_TO_EN, getString(R.string.mode_japanese_to_english), R.drawable.ic_outline_cards_stack_24, R.color.mode_indigo)
        setupRow(R.id.row_en_en_1, MODE_EN_EN_1, getString(R.string.mode_english_english_1), R.drawable.ic_outline_cards_stack_24, R.color.mode_orange)
        setupRow(R.id.row_en_en_2, MODE_EN_EN_2, getString(R.string.mode_english_english_2), R.drawable.ic_outline_cards_stack_24, R.color.mode_orange)

        setupRow(R.id.row_test_fill, MODE_TEST_FILL_BLANK, "穴埋め", R.drawable.ic_edit_24, R.color.mode_pink, true)
        setupRow(R.id.row_test_sort, MODE_TEST_SORT, "並び替え", R.drawable.ic_sort_24, R.color.mode_pink, true)
        setupRow(R.id.row_test_listen_q1, MODE_TEST_LISTEN_Q1, "リスニング質問", R.drawable.ic_headphones_24, R.color.mode_teal, true)
        setupRow(R.id.row_test_listen_q2, MODE_TEST_LISTEN_Q2, "会話文リスニング", R.drawable.ic_outline_conversation_24, R.color.mode_teal, true)

        dialog.show()
    }

    private fun updateStudyStatsView() {
        lifecycleScope.launch(Dispatchers.IO) {
            val wordIdSet: Set<Int> = allWords.map { it.no }.toSet()
            val nowSec = nowEpochSec()

            // Parallel calculation could be better, but sequential is safe here
            currentStats = mapOf(
                MODE_MEANING to computeModeStats(wordIdSet, MODE_MEANING, nowSec),
                MODE_LISTENING to computeModeStats(wordIdSet, MODE_LISTENING, nowSec),
                MODE_LISTENING_JP to computeModeStats(wordIdSet, MODE_LISTENING_JP, nowSec),
                MODE_JA_TO_EN to computeModeStats(wordIdSet, MODE_JA_TO_EN, nowSec),
                MODE_EN_EN_1 to computeModeStats(wordIdSet, MODE_EN_EN_1, nowSec),
                MODE_EN_EN_2 to computeModeStats(wordIdSet, MODE_EN_EN_2, nowSec),
                MODE_TEST_LISTEN_Q2 to computeModeStats(emptySet(), MODE_TEST_LISTEN_Q2, nowSec)
            )

            withContext(Dispatchers.Main) { updateModeUi() }
        }
    }

    private fun updateModeUi() {
        val modeName = when(currentMode) {
            MODE_MEANING -> getString(R.string.mode_meaning)
            MODE_LISTENING -> getString(R.string.mode_listening)
            MODE_LISTENING_JP -> getString(R.string.mode_listening_jp)
            MODE_JA_TO_EN -> getString(R.string.mode_japanese_to_english)
            MODE_EN_EN_1 -> getString(R.string.mode_english_english_1)
            MODE_EN_EN_2 -> getString(R.string.mode_english_english_2)
            MODE_TEST_LISTEN_Q2 -> "会話文リスニング"
            else -> "選択中"
        }
        val iconRes = when(currentMode) {
            MODE_LISTENING, MODE_LISTENING_JP -> R.drawable.ic_headphones_24
            MODE_TEST_LISTEN_Q2 -> R.drawable.ic_outline_conversation_24
            MODE_TEST_FILL_BLANK -> R.drawable.ic_edit_24
            MODE_TEST_SORT -> R.drawable.ic_sort_24
            else -> R.drawable.ic_outline_cards_stack_24
        }

        selectorIconMode.setImageResource(iconRes)
        selectorTextTitle.text = modeName

        val stat = currentStats[currentMode]
        selectorTextReview.text = stat?.review?.toString() ?: "-"
        selectorTextNew.text = stat?.newCount?.toString() ?: "-"
        selectorTextMaster.text = if ((stat?.total ?: 0) > 0) "${stat!!.mastered * 100 / stat.total}%" else "-"
    }

    private suspend fun computeModeStats(wordIdSet: Set<Int>, mode: String, nowSec: Long): ModeStats {
        val db = AppDatabase.getInstance(this@LearningActivity)
        val progressDao = db.wordProgressDao()

        if (mode == MODE_TEST_LISTEN_Q2) {
            val allQIds = listeningQuestions.map { it.id }.toSet()
            val progresses = progressDao.getAllProgressForMode(mode)
            return ModeStats(
                review = progressDao.getDueWordIdsOrdered(mode, nowSec).count { it in allQIds },
                newCount = allQIds.size - progresses.filter { it.wordId in allQIds }.size,
                total = allQIds.size,
                mastered = progresses.count { it.wordId in allQIds && it.level >= 6 }
            )
        }

        val progresses = progressDao.getAllProgressForMode(mode)
        val targetProgresses = progresses.filter { it.wordId in wordIdSet }
        return ModeStats(
            review = progressDao.getDueWordIdsOrdered(mode, nowSec).count { it in wordIdSet },
            newCount = wordIdSet.size - targetProgresses.size,
            total = wordIdSet.size,
            mastered = targetProgresses.count { it.level >= 6 }
        )
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (255 * factor).toInt()
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
    // endregion

    // region Audio & TTS
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            applyTtsParams()
        }
    }

    private fun applyTtsParams() {
        tts?.setSpeechRate(settings.getTtsSpeed())
        tts?.setPitch(settings.getTtsPitch())
    }

    private fun loadSeIfExists(rawName: String): Int {
        val resId = resources.getIdentifier(rawName, "raw", packageName)
        return if (resId != 0) soundPool?.load(this, resId, 1) ?: 0 else 0
    }

    private fun speakCurrentWord() {
        if (currentMode == MODE_TEST_LISTEN_Q2) {
            // Replay logic for conversation handled by ViewModel or Strategy if implemented
            return
        }
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

    private fun stopMedia() {
        conversationTts?.stop()
        tts?.stop()
    }

    private fun handleAutoPlayAudio(word: WordEntity) {
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

        if (currentMode == MODE_LISTENING || currentMode == MODE_LISTENING_JP) {
            speakCurrentWord()
        } else if (checkboxAutoPlayAudio?.isChecked == true && checkboxAutoPlayAudio?.visibility == View.VISIBLE) {
            if (currentMode == MODE_EN_EN_2) speakText(word.description ?: "") else speakCurrentWord()
        }
    }
    // endregion

    // region Utilities (CSV, Time, Math)
    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1000L

    /**
     * Spaced Repetition Logic.
     * Calculates the next due date based on correctness and current level.
     */
    private fun calcNextDueAtSec(isCorrect: Boolean, currentLevel: Int, nowSec: Long): Pair<Int, Long> {
        val newLevel = if (isCorrect) currentLevel + 1 else maxOf(0, currentLevel - 2)
        val zone = settings.getAppZoneId()

        if (!isCorrect) return newLevel to (nowSec + settings.wrongRetrySec)
        if (newLevel == 1) return newLevel to (nowSec + settings.level1RetrySec)

        val days = when (newLevel) {
            2 -> 1; 3 -> 3; 4 -> 7; 5 -> 14; 6 -> 30; 7 -> 60; else -> 90
        }
        val baseDate = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
        val dueDate = baseDate.plusDays(days.toLong())
        return newLevel to dueDate.atStartOfDay(zone).toEpochSecond()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }

    // CSV Imports (run on IO context)
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
                        val cols = parseCsvLine(line)
                        if (cols.size >= 7) {
                            result.add(WordEntity(
                                no = cols[0].trim().toIntOrNull() ?: 0,
                                grade = cols[1].trim(),
                                word = cols[2].trim(),
                                japanese = cols[3].trim(),
                                description = cols[4].trim(),
                                smallTopicId = cols[5].trim(),
                                mediumCategoryId = cols[6].trim()
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("LearningActivity", "Error reading CSV", e) }
        return result
    }

    private suspend fun loadListeningQuestionsFromCsv(): List<ListeningQuestion> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ListeningQuestion>()
        try {
            val resId = resources.getIdentifier("listening2", "raw", packageName)
            if (resId != 0) {
                resources.openRawResource(resId).use { input ->
                    BufferedReader(InputStreamReader(input)).useLines { lines ->
                        lines.drop(1).forEach { line ->
                            val cols = parseCsvLine(line)
                            if (cols.size >= 11) {
                                result.add(ListeningQuestion(
                                    id = cols[0].toIntOrNull() ?: 0,
                                    grade = cols[1],
                                    script = cols[3].replace("\\n", "\n"),
                                    question = cols[4],
                                    options = listOf(cols[5], cols[6], cols[7], cols[8]),
                                    correctIndex = (cols[9].toIntOrNull() ?: 1) - 1,
                                    explanation = cols[10]
                                ))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("LearningActivity", "Error reading listening CSV", e) }
        result
    }
    // endregion
}