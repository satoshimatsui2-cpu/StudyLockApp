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
 *
 * 【設計方針】
 * 1. ハイブリッド構成:
 * - 新機能 (会話モード) は [LearningViewModel] と [QuestionUiState] によるリアクティブな設計。
 * - 既存機能 (単語学習) は Activity 内の [loadNextQuestionLegacy] 周辺に集約された命令的な設計。
 * * 2. データフロー (単語学習):
 * - [prepareQuestionData]: DB/計算処理 (IOスレッド) -> [LegacyQuestionContext] を生成
 * - [renderLegacyQuestion]: UI描画処理 (Mainスレッド)
 * - [loadingJob]: 非同期処理の競合（連打など）を防止
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

    // region ViewModel & Data
    private val viewModel: LearningViewModel by viewModels()

    // Activity State
    private var currentMode = MODE_MEANING
    private var gradeFilter: String = "All"
    private var includeOtherGradesReview: Boolean = false
    private var loadingJob: Job? = null // 非同期処理の重複防止用

    // Legacy Data Cache
    private var allWords: List<WordEntity> = emptyList()
    private var allWordsFull: List<WordEntity> = emptyList()
    private var listeningQuestions: List<ListeningQuestion> = emptyList()

    // Current Question Context (Legacy)
    private var currentLegacyContext: LegacyQuestionContext? = null

    /**
     * レガシーモードの1問分のデータを保持するクラス。
     * これにより、DB取得(IO)と描画(Main)の間で受け渡す情報を一元化する。
     */
    private data class LegacyQuestionContext(
        val word: WordEntity,
        val title: String,
        val body: String,
        val options: List<String>,
        val correctIndex: Int,
        val shouldAutoPlay: Boolean,
        val audioText: String
    )

    // Statistics Cache
    private var currentStats: Map<String, ModeStats> = emptyMap()
    // endregion

    // region System Services
    private var tts: TextToSpeech? = null
    private var conversationTts: ConversationTtsManager? = null
    private var soundPool: SoundPool? = null
    private var seCorrectId: Int = 0
    private var seWrongId: Int = 0
    private lateinit var settings: AppSettings
    // endregion

    // region UI Components
    private lateinit var textQuestionTitle: TextView
    private lateinit var textQuestionBody: TextView
    private lateinit var textPoints: TextView
    private lateinit var textPointStats: TextView
    private var textCurrentGrade: TextView? = null
    private var textTotalWords: TextView? = null
    private lateinit var textFeedback: TextView

    private lateinit var textScriptDisplay: TextView
    private lateinit var layoutActionButtons: LinearLayout
    private lateinit var buttonNextQuestion: Button
    private lateinit var buttonReplayAudio: Button
    private lateinit var choiceButtons: List<Button>
    private lateinit var defaultChoiceTints: List<ColorStateList?>

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

    private val greenTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_correct)) }
    private val redTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_wrong)) }
    // endregion

    private data class ModeStats(
        val review: Int,
        val newCount: Int,
        val total: Int,
        val mastered: Int
    )

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_learning)
        setupWindowInsets()

        settings = AppSettings(this)
        gradeFilter = intent.getStringExtra("gradeFilter") ?: "All"

        initViews()
        initMediaServices()
        setupObservers()
        setupListeners()

        // 初期データの読み込み開始
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
        loadingJob?.cancel() // 画面を離れるときはロード処理をキャンセル
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

    // region Initialization
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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

        checkIncludeOtherGrades = findViewById<CheckBox?>(R.id.checkbox_include_other_grades)?.apply {
            isChecked = true
            includeOtherGradesReview = true
        }
        checkboxAutoPlayAudio = findViewById<CheckBox?>(R.id.checkbox_auto_play_audio)?.apply {
            isChecked = true
        }

        textFeedback.visibility = View.GONE
        updatePointView()
    }

    private fun initMediaServices() {
        tts = TextToSpeech(this, this)
        conversationTts = ConversationTtsManager(this)

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
        val imported = withContext(Dispatchers.IO) {
            if (gradeFilter != "All") importMissingWordsForGrade(gradeFilter) else 0
        }
        if (imported > 0) {
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.imported_count_message, imported), Snackbar.LENGTH_SHORT).show()
        }

        listeningQuestions = loadListeningQuestionsFromCsv()
        viewModel.listeningQuestions = listeningQuestions

        // 単語リストロード
        val db = AppDatabase.getInstance(this@LearningActivity)
        allWordsFull = withContext(Dispatchers.IO) { db.wordDao().getAll() }
        allWords = if (gradeFilter == "All") allWordsFull else allWordsFull.filter { it.grade == gradeFilter }
        viewModel.setGradeInfo(gradeFilter, allWords)

        updateStudyStatsView()

        // モードに応じた初期表示
        routeNextQuestionAction()
    }
    // endregion

    // region Events & Observers
    private fun setupListeners() {
        layoutModeSelector.setOnClickListener { showModeSelectionSheet() }

        choiceButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { onChoiceSelected(index) }
        }

        buttonPlayAudio.setOnClickListener { speakCurrentLegacyAudio() }
        buttonReplayAudio.setOnClickListener { speakCurrentLegacyAudio() }

        buttonSoundSettings.setOnClickListener {
            runCatching {
                startActivity(Intent().apply { setClassName(this@LearningActivity, "com.example.studylockapp.SoundSettingsActivity") })
            }.onFailure { Toast.makeText(this, "起動に失敗", Toast.LENGTH_SHORT).show() }
        }

        buttonNextQuestion.setOnClickListener {
            routeNextQuestionAction()
        }

        checkIncludeOtherGrades?.setOnCheckedChangeListener { _, _ ->
            if (currentMode != MODE_TEST_LISTEN_Q2) loadNextQuestionLegacy()
        }
    }

    private fun setupObservers() {
        viewModel.gradeName.observe(this) { textCurrentGrade?.text = it }
        viewModel.wordCount.observe(this) { textTotalWords?.text = getString(R.string.label_word_count, it) }

        // 会話モード用のState監視
        lifecycleScope.launch {
            viewModel.questionUiState.collect { state ->
                if (state !is QuestionUiState.Loading) resetUiForNewQuestion()

                when (state) {
                    is QuestionUiState.Conversation -> renderConversationUi(state)
                    is QuestionUiState.Empty -> showNoQuestion()
                    else -> {} // Loading or others
                }
            }
        }

        // 会話モードの回答監視
        lifecycleScope.launch {
            viewModel.answerResult.collect { result ->
                showFeedbackSnackbar(result)
                updatePointView()

                // ▼▼▼ この1行を追加してください！ ▼▼▼
                updateStudyStatsView()
                // ▲▲▲ これで正解した瞬間にNEWが減ります ▲▲▲
            }
        }
    }
    // endregion

    // region Logic: Routing & Display
    private fun routeNextQuestionAction() {
        if (currentMode == MODE_TEST_LISTEN_Q2) {
            viewModel.loadNextQuestion()
        } else {
            loadNextQuestionLegacy()
        }
    }

    // 会話モード(ViewModel主導)の描画
    private fun renderConversationUi(state: QuestionUiState.Conversation) {
        textQuestionTitle.text = "会話を聞いて質問に答えてください"
        textQuestionBody.text = state.question
        textQuestionBody.visibility = View.VISIBLE
        textScriptDisplay.visibility = View.GONE

        // 固有UI設定
        checkIncludeOtherGrades?.visibility = View.GONE
        checkboxAutoPlayAudio?.visibility = View.GONE
        buttonPlayAudio.visibility = View.GONE
        layoutActionButtons.visibility = View.GONE

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

        val repeatScript = state.script + "\nWait: 2000\n" + state.script
        conversationTts?.playScript(repeatScript)
    }
    // endregion

    // region Logic: Legacy Word Learning (Main Pipeline)

    /**
     * レガシーモード（単語学習）の次の問題を読み込むメインパイプライン。
     * [loadingJob] により、連打時などは前の処理をキャンセルして最新の要求のみを処理する。
     */
    private fun loadNextQuestionLegacy() {
        loadingJob?.cancel()
        loadingJob = lifecycleScope.launch {
            // 1. UI Reset
            stopMedia()
            resetStandardUi()

            if (allWordsFull.isEmpty()) {
                showNoQuestion()
                return@launch
            }

            // 2. Data Preparation (Heavy Logic on IO)
            val nextContext = withContext(Dispatchers.IO) {
                prepareQuestionData()
            }

            // 3. UI Rendering (Main)
            if (nextContext == null) {
                showNoQuestion()
            } else {
                currentLegacyContext = nextContext
                renderLegacyQuestion(nextContext)

                // 4. Audio Playback
                if (nextContext.shouldAutoPlay) {
                    speakText(nextContext.audioText)
                }
            }
        }
    }

    /**
     * 次の問題データを決定・生成する純粋なロジック部分。
     * UIへの依存を持たず、計算結果([LegacyQuestionContext])を返すのみ。
     */
    private suspend fun prepareQuestionData(): LegacyQuestionContext? {
        // Step A: 単語選択
        val nextWord = selectNextWord() ?: return null

        // Step B: 選択肢生成
        val choicePool = getChoicePool()
        val choices = buildChoices(nextWord, choicePool, 6)

        // Step C: 文字列フォーマット
        val (title, body, options) = formatQuestionAndOptions(nextWord, choices, currentMode)
        val correctStr = getCorrectStringForMode(nextWord, currentMode)
        val correctIndex = options.indexOf(correctStr)

        // Step D: 自動再生判定
        val shouldAuto = when (currentMode) {
            MODE_JA_TO_EN -> false
            MODE_LISTENING, MODE_LISTENING_JP -> true
            else -> checkboxAutoPlayAudio?.isChecked == true && checkboxAutoPlayAudio?.visibility == View.VISIBLE
        }

        val audioText = if (currentMode == MODE_EN_EN_2) nextWord.description ?: "" else nextWord.word

        return LegacyQuestionContext(
            word = nextWord,
            title = title,
            body = body,
            options = options,
            correctIndex = correctIndex,
            shouldAutoPlay = shouldAuto,
            audioText = audioText
        )
    }

    /**
     * 生成されたデータをUIに反映する。
     */
    private fun renderLegacyQuestion(ctx: LegacyQuestionContext) {
        textQuestionTitle.text = ctx.title
        textQuestionBody.text = ctx.body
        textQuestionBody.visibility = if (ctx.body.isEmpty()) View.GONE else View.VISIBLE

        choiceButtons.forEach { it.textSize = if (currentMode == MODE_EN_EN_1) 12f else 14f }
        choiceButtons.zip(ctx.options).forEach { (btn, txt) -> btn.text = txt }

        // 再生ボタンの表示制御
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
    }

    private fun onChoiceSelected(selectedIndex: Int) {
        if (currentMode == MODE_TEST_LISTEN_Q2) {
            viewModel.submitAnswer(selectedIndex)
            return
        }

        // レガシーモード判定
        val ctx = currentLegacyContext ?: return
        val isCorrect = selectedIndex == ctx.correctIndex

        // 視覚フィードバック
        choiceButtons.forEach { it.isClickable = false }
        if (ctx.correctIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[ctx.correctIndex], greenTint)
        }
        if (!isCorrect && selectedIndex != ctx.correctIndex && selectedIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[selectedIndex], redTint)
        }

        if (isCorrect) playCorrectEffect() else playWrongEffect()

        // 結果処理
        processAnswerResultLegacy(ctx.word.no, isCorrect)
    }

    private fun processAnswerResultLegacy(wordId: Int, isCorrect: Boolean) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val pointManager = PointManager(this@LearningActivity)
            val nowSec = nowEpochSec()

            // DB更新 (IO)
            val addPoint = withContext(Dispatchers.IO) {
                val current = progressDao.getProgress(wordId, currentMode)
                val currentLevel = current?.level ?: 0
                val (newLevel, nextDueAtSec) = calcNextDueAtSec(isCorrect, currentLevel, nowSec)
                val points = ProgressCalculator.calcPoint(isCorrect, currentLevel)

                pointManager.add(points)
                if (points > 0) {
                    db.pointHistoryDao().insert(PointHistoryEntity(mode = currentMode, dateEpochDay = LocalDate.now(settings.getAppZoneId()).toEpochDay(), delta = points))
                }

                progressDao.upsert(WordProgressEntity(
                    wordId = wordId, mode = currentMode, level = newLevel, nextDueAtSec = nextDueAtSec,
                    lastAnsweredAt = System.currentTimeMillis(), studyCount = (current?.studyCount ?: 0) + 1
                ))
                db.studyLogDao().insert(WordStudyLogEntity(wordId = wordId, mode = currentMode, learnedAt = System.currentTimeMillis()))

                points
            }

            // UI更新 (Main)
            updateStudyStatsView() // 統計更新
            showFeedbackSnackbarInternal(isCorrect, addPoint)
            updatePointView()

            if (!isCorrect) {
                layoutActionButtons.visibility = View.VISIBLE
            } else {
                delay(settings.answerIntervalMs)
                loadNextQuestionLegacy()
            }
        }
    }
    // endregion

    // region Helper Logic (Selection, CSV, Time)
    private suspend fun selectNextWord(): WordEntity? {
        val db = AppDatabase.getInstance(this)
        val progressDao = db.wordProgressDao()
        val nowSec = nowEpochSec()
        val dueIdsOrdered = progressDao.getDueWordIdsOrdered(currentMode, nowSec)

        val wordMapFiltered = allWords.associateBy { it.no }
        val wordMapAll = allWordsFull.associateBy { it.no }

        // 1. 復習対象
        val dueWords = if (includeOtherGradesReview && gradeFilter != "All") {
            dueIdsOrdered.mapNotNull { wordMapAll[it] }
        } else {
            dueIdsOrdered.mapNotNull { wordMapFiltered[it] }
        }
        if (dueWords.isNotEmpty()) return dueWords.first()

        // 2. 新規学習対象
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

    private fun buildChoices(correct: WordEntity, pool: List<WordEntity>, count: Int): List<WordEntity> {
        val candidates = pool.filter { it.no != correct.no }
        if (candidates.isEmpty()) return listOf(correct)

        val distractors = when (currentMode) {
            MODE_LISTENING, MODE_LISTENING_JP -> getListeningChoices(correct, candidates, count - 1)
            else -> getStandardChoices(correct, candidates, count - 1)
        }
        return (distractors + correct).shuffled()
    }

    // ※ 既存の複雑な選択肢生成ロジックはそのまま維持
    private fun getStandardChoices(correct: WordEntity, candidates: List<WordEntity>, count: Int): List<WordEntity> {
        val sameGradePool = candidates.filter { it.grade == correct.grade }
        if (sameGradePool.isEmpty()) return candidates.shuffled().take(count)

        // (省略なしで記述)
        val correctSmallTopic = correct.smallTopicId
        val sameSmallTopic = if (!correctSmallTopic.isNullOrEmpty()) {
            sameGradePool.filter { !it.smallTopicId.isNullOrEmpty() && it.smallTopicId == correctSmallTopic }.shuffled()
        } else emptyList()
        val pickedIds = sameSmallTopic.map { it.no }.toSet()
        val correctMediumCategory = correct.mediumCategoryId
        val sameMediumCategory = if (!correctMediumCategory.isNullOrEmpty()) {
            sameGradePool.filter { it.no !in pickedIds && !it.mediumCategoryId.isNullOrEmpty() && it.mediumCategoryId == correctMediumCategory }.shuffled()
        } else emptyList()
        val others = sameGradePool.filter { it.no !in pickedIds && (it.mediumCategoryId != correctMediumCategory || correctMediumCategory.isNullOrEmpty()) }.shuffled()
        return (sameSmallTopic + sameMediumCategory + others).take(count)
    }

    private fun getListeningChoices(correct: WordEntity, candidates: List<WordEntity>, count: Int): List<WordEntity> {
        // (省略なしで記述)
        val correctGradeVal = correct.grade?.toIntOrNull() ?: 0
        val correctLen = correct.word.length
        val validPool = candidates.filter {
            val gVal = it.grade?.toIntOrNull() ?: 0
            gVal >= correctGradeVal && abs(it.word.length - correctLen) <= 3
        }
        val poolToUse = if (validPool.size < count) candidates else validPool

        val p2 = correct.word.take(2).lowercase()
        val p1 = correct.word.take(1).lowercase()

        val priority1 = poolToUse.filter { it.word.lowercase().startsWith(p2) }.shuffled()
        val p1Ids = priority1.map { it.no }.toSet()
        val priority2 = poolToUse.filter { it.no !in p1Ids && it.word.lowercase().startsWith(p1) }.shuffled()
        val p1p2Ids = p1Ids + priority2.map { it.no }
        val priority3 = poolToUse.filter { it.no !in p1p2Ids && abs(it.word.length - correctLen) <= 1 }.shuffled()
        val others = poolToUse.filter { it.no !in p1p2Ids && it.no !in priority3.map { w -> w.no } }.shuffled()

        val result = (priority1 + priority2 + priority3 + others).take(count)
        return if (result.size < count) result + candidates.filter { it.no !in result.map { w -> w.no } && it.no != correct.no }.shuffled().take(count - result.size) else result
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

    private fun getCorrectStringForMode(word: WordEntity, mode: String): String {
        return when (mode) {
            MODE_MEANING, MODE_LISTENING_JP -> word.japanese ?: ""
            MODE_LISTENING, MODE_JA_TO_EN, MODE_EN_EN_2 -> word.word
            MODE_EN_EN_1 -> word.description ?: ""
            else -> word.japanese ?: ""
        }
    }

    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1000L

    private fun calcNextDueAtSec(isCorrect: Boolean, currentLevel: Int, nowSec: Long): Pair<Int, Long> {
        val newLevel = if (isCorrect) currentLevel + 1 else maxOf(0, currentLevel - 2)
        val zone = settings.getAppZoneId()
        if (!isCorrect) return newLevel to (nowSec + settings.wrongRetrySec)
        if (newLevel == 1) return newLevel to (nowSec + settings.level1RetrySec)
        val days = when (newLevel) { 2 -> 1; 3 -> 3; 4 -> 7; 5 -> 14; 6 -> 30; 7 -> 60; else -> 90 }
        val dueDate = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate().plusDays(days.toLong())
        return newLevel to dueDate.atStartOfDay(zone).toEpochSecond()
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
        runCatching {
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
        }.onFailure { Log.e("LearningActivity", "Error reading CSV", it) }
        return result
    }

    private suspend fun loadListeningQuestionsFromCsv(): List<ListeningQuestion> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ListeningQuestion>()
        runCatching {
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
        }.onFailure { Log.e("LearningActivity", "Error reading listening CSV", it) }
        result
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }
    // endregion

    // region UI Utilities (Stats, Effects, Sound)
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
        if (choiceButtons.size >= 6) { (choiceButtons[4].parent as? View)?.visibility = View.VISIBLE }
        checkIncludeOtherGrades?.visibility = View.VISIBLE
        buttonPlayAudio.visibility = View.VISIBLE
    }

    private fun showNoQuestion() {
        textQuestionTitle.text = getString(R.string.no_question_available)
        textQuestionBody.text = ""
        textQuestionBody.visibility = View.GONE
        choiceButtons.forEach { it.text = "----"; it.isEnabled = false }
    }

    private fun showFeedbackSnackbar(result: AnswerResult) {
        val vol = if (result.isCorrect) settings.seCorrectVolume else settings.seWrongVolume
        val seId = if (result.isCorrect) seCorrectId else seWrongId
        if (seId != 0) soundPool?.play(seId, vol, vol, 1, 0, 1f)

        val bgColor = ContextCompat.getColor(this, if (result.isCorrect) R.color.snackbar_correct_bg else R.color.snackbar_wrong_bg)
        choiceButtons.forEach { it.isEnabled = false }

        currentSnackbar?.dismiss()
        currentSnackbar = Snackbar.make(findViewById(android.R.id.content), result.feedback, Snackbar.LENGTH_INDEFINITE).apply {
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

    private fun playCorrectEffect() {
        // 背景フラッシュ
        val root = findViewById<View>(android.R.id.content)
        val flashColor = ContextCompat.getColor(this, R.color.correct_flash)
        if (root.width > 0 && root.height > 0) {
            val drawable = ColorDrawable(flashColor).apply {
                setBounds(0, 0, root.width, root.height)
                alpha = 0
            }
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

        val vol = settings.seCorrectVolume
        if (seCorrectId != 0) soundPool?.play(seCorrectId, vol, vol, 1, 0, 1f)

        val ctx = currentLegacyContext ?: return
        val v = choiceButtons.getOrNull(ctx.correctIndex) ?: return
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

    private fun showModeSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        val view = layoutInflater.inflate(R.layout.layout_mode_selection_sheet, null)
        dialog.setContentView(view)

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
            ViewCompat.setBackgroundTintList(card.findViewById(R.id.icon_container), ColorStateList.valueOf((color and 0x00FFFFFF) or (38 shl 24))) // alpha ~0.15

            val stats = currentStats[modeKey]
            textReview.text = stats?.review?.toString() ?: "-"
            textNew.text = stats?.newCount?.toString() ?: "-"
            textMaster.text = if ((stats?.total ?: 0) > 0) "${stats!!.mastered * 100 / stats.total}%" else "-"

            if (isTestMode && modeKey != MODE_TEST_LISTEN_Q2) card.alpha = 0.5f

            if (currentMode == modeKey) {
                card.strokeColor = color
                card.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                card.setCardBackgroundColor((color and 0x00FFFFFF) or (13 shl 24)) // alpha ~0.05
            }

            card.setOnClickListener {
                currentMode = modeKey
                updateStudyStatsView()
                if (modeKey == MODE_TEST_LISTEN_Q2) viewModel.setMode(modeKey) else loadNextQuestionLegacy()
                dialog.dismiss()
            }
        }

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
        lifecycleScope.launch(Dispatchers.Default) {
            val wordIdSet: Set<Int> = allWords.map { it.no }.toSet()
            val nowSec = nowEpochSec()

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
            val dueCount = progressDao.getDueWordIdsOrdered(mode, nowSec).count { it in allQIds }
            val masteredCount = progresses.count { it.wordId in allQIds && it.level >= 6 }
            val startedCount = progresses.filter { it.wordId in allQIds }.size
            return ModeStats(review = dueCount, newCount = allQIds.size - startedCount, total = allQIds.size, mastered = masteredCount)
        }

        val progresses = progressDao.getAllProgressForMode(mode)
        val targetProgresses = progresses.filter { it.wordId in wordIdSet }
        val dueCount = progressDao.getDueWordIdsOrdered(mode, nowSec).count { it in wordIdSet }
        val masteredCount = targetProgresses.count { it.level >= 6 }
        return ModeStats(review = dueCount, newCount = wordIdSet.size - targetProgresses.size, total = wordIdSet.size, mastered = masteredCount)
    }

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

    private fun speakCurrentLegacyAudio() {
        val ctx = currentLegacyContext ?: return
        speakText(ctx.audioText)
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
    // endregion
}