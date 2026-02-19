package com.example.studylockapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
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
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.text.toIntOrNull


class LearningActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // region ViewModel & Data
    private val viewModel: LearningViewModel by viewModels()
    private var currentMode = LearningModes.MEANING
    private var gradeFilter: String = ""
    private var includeOtherGradesReview: Boolean = false
    private var loadingJob: Job? = null
    private var allWords: List<WordEntity> = emptyList()
    private var allWordsFull: List<WordEntity> = emptyList()
    private var listeningQuestions: List<ListeningQuestion> = emptyList()
    private var fillBlankQuestions: List<FillBlankQuestion> = emptyList()
    private var sortQuestions: List<SortQuestion> = emptyList()
    private var sortQuestionIndex: Int = 0

    private var currentLegacyContext: LegacyQuestionContext? = null

    // 再生ボタン用に現在の会話スクリプトを保持する変数
    private var currentConversationScript: String = ""

    // ハイライト検索用の現在位置カーソル
    private var currentHighlightSearchIndex: Int = 0

    // 現在の質問文（本文）を保持して、ボタン有効化の判定に使う
    private var currentQuestionText: String = ""
    private var currentStats: Map<String, ModeStats> = emptyMap()
    // endregion

    // region System Services
    private var tts: TextToSpeech? = null
    private var conversationTts: ConversationTtsManager? = null
    private lateinit var soundEffectManager: SoundEffectManager
    private lateinit var settings: AppSettings
    // endregion

    // region UI Components
    private lateinit var textQuestionTitle: TextView
    private lateinit var textQuestionBody: TextView
    private lateinit var textPoints: TextView
    private var textCurrentGrade: TextView? = null
    private lateinit var textFeedback: TextView
    private val sortViewModel: com.example.studylockapp.learning.SortQuestionViewModel by viewModels()


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

    // トロフィー5種＋アイコン用変数
    private lateinit var textMasterBronze: TextView
    private lateinit var textMasterSilver: TextView
    private lateinit var textMasterGold: TextView
    private lateinit var textMasterCrystal: TextView
    private lateinit var textMasterPurple: TextView

    private lateinit var iconMasterBronze: ImageView
    private lateinit var iconMasterSilver: ImageView
    private lateinit var iconMasterGold: ImageView
    private lateinit var iconMasterCrystal: ImageView
    private lateinit var iconMasterPurple: ImageView

    private lateinit var buttonPlayAudio: ImageButton
    private lateinit var buttonSoundSettings: ImageButton
    private var checkIncludeOtherGrades: CheckBox? = null
    private var checkboxAutoPlayAudio: CheckBox? = null
    private var currentSnackbar: Snackbar? = null
    private var sortJapaneseBaseTextSizePx: Float? = null
    private var sortCorrectBaseTextSizePx: Float? = null
    // ▼▼▼ 追加：選択肢を隠すUI ▼▼▼
    private var checkboxHideChoices: CheckBox? = null
    private var coverLayout: View? = null
    private var buttonShowChoices: Button? = null
    private var buttonDontKnow: Button? = null
// ▲▲▲ 追加ここまで ▲▲▲


    private val greenTint by lazy {
        ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                R.color.choice_correct
            )
        )
    }
    private val redTint by lazy {
        ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                R.color.choice_wrong
            )
        )
    }
    // endregion

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_learning)
        setupWindowInsets()

        settings = AppSettings(this)

        // ★ここから追加（gradeFilterガード）
        val grade = intent.getStringExtra("gradeFilter")
        val allowed = setOf("5","4","3","2.5","2","1.5","1")

        if (grade.isNullOrBlank() || grade !in allowed) {
            android.util.Log.w("LearningActivity", "invalid gradeFilter=$grade, intent=$intent")

            // ★「その場合だけ、強制的にMainActivityへ」
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
            return
        }

        gradeFilter = grade
        // ★ここまで追加

        initViews()
        initMediaServices()
        setupObservers()
        setupListeners()
        applyUiVisibilityForMode()

        lifecycleScope.launch {
            loadInitialData()
            if (currentMode == LearningModes.TEST_SORT) {
                showFirstSortQuestion()
            }
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
        loadingJob?.cancel()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        conversationTts?.shutdown()

        // ★ここがポイント：初期化されてる時だけrelease
        if (::soundEffectManager.isInitialized) {
            soundEffectManager.release()
        }

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
        textCurrentGrade = findViewById(R.id.text_current_grade)
        textFeedback = findViewById(R.id.text_feedback)

        textScriptDisplay = findViewById(R.id.text_script_display)

        // 会話文と質問文のテキストサイズを大きく設定
        textQuestionBody.textSize = 24f
        textScriptDisplay.textSize = 18f

        layoutActionButtons = findViewById(R.id.layout_action_buttons)
        buttonNextQuestion = findViewById(R.id.button_next_question)
        buttonReplayAudio = findViewById(R.id.button_replay_audio)

        layoutModeSelector = findViewById(R.id.layout_mode_selector)
        selectorIconMode = findViewById(R.id.selector_icon_mode)
        selectorTextTitle = findViewById(R.id.selector_text_title)
        selectorTextReview = findViewById(R.id.selector_text_review)
        selectorTextNew = findViewById(R.id.selector_text_new)

        textMasterBronze = findViewById(R.id.text_master_bronze)
        textMasterSilver = findViewById(R.id.text_master_silver)
        textMasterGold = findViewById(R.id.text_master_gold)
        textMasterCrystal = findViewById(R.id.text_master_crystal)
        textMasterPurple = findViewById(R.id.text_master_purple)

        iconMasterBronze = findViewById(R.id.icon_master_bronze)
        iconMasterSilver = findViewById(R.id.icon_master_silver)
        iconMasterGold = findViewById(R.id.icon_master_gold)
        iconMasterCrystal = findViewById(R.id.icon_master_crystal)
        iconMasterPurple = findViewById(R.id.icon_master_purple)

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

        checkIncludeOtherGrades =
            findViewById<CheckBox?>(R.id.checkbox_include_other_grades)?.apply {
                isChecked = false
                includeOtherGradesReview = false
            }
        checkboxAutoPlayAudio = findViewById<CheckBox?>(R.id.checkbox_auto_play_audio)?.apply {
            isChecked = true
        }
        // ▼▼▼ 追加：隠し機能UI取得 ▼▼▼
        checkboxHideChoices = findViewById<CheckBox?>(R.id.checkbox_hide_choices)?.apply {
            isChecked = false
        }
        coverLayout = findViewById(R.id.cover_layout)
        buttonShowChoices = findViewById(R.id.button_show_choices)
        buttonDontKnow = findViewById(R.id.button_dont_know)

        // 起動時は念のため隠す
        coverLayout?.visibility = View.GONE
        // ▲▲▲ 追加ここまで ▲▲▲

        textFeedback.visibility = View.GONE
        updatePointView()
    }

    private fun initMediaServices() {
        tts = TextToSpeech(this, this)
        conversationTts = ConversationTtsManager(this)

        conversationTts?.onSpeakListener = { spokenText ->
            val sText = spokenText.trim()
            if (sText.startsWith("Question", ignoreCase = true) ||
                (currentQuestionText.isNotEmpty() && sText.contains(
                    currentQuestionText,
                    ignoreCase = true
                ))
            ) {
                runOnUiThread {
                    enableConversationButtons()
                }
            }
            highlightCurrentSpeakingText(spokenText)
        }
        soundEffectManager = SoundEffectManager(this)
    }

    private fun enableConversationButtons() {
        choiceButtons.forEach { btn ->
            if (btn.visibility == View.VISIBLE) {
                btn.isEnabled = true
                btn.alpha = 1.0f
            }
        }
    }

    private fun highlightCurrentSpeakingText(spokenText: String) {
        if (spokenText.isEmpty() || textScriptDisplay.visibility != View.VISIBLE) return

        val fullText = textScriptDisplay.text.toString()
        var start = fullText.indexOf(spokenText, startIndex = currentHighlightSearchIndex)

        if (start == -1) {
            start = fullText.indexOf(spokenText, startIndex = 0)
        }

        if (start != -1) {
            val end = start + spokenText.length
            val spannable = SpannableString(fullText)
            val highlightColor = Color.parseColor("#80FFEB3B")
            spannable.setSpan(
                MarkerSpan(highlightColor),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            textScriptDisplay.text = spannable
            currentHighlightSearchIndex = end
        }
    }

    private suspend fun loadInitialData() {
        val targetDbGrade = normalizeGrade(gradeFilter)

        val imported = withContext(Dispatchers.IO) {
            if (gradeFilter != "All") importMissingWordsForGrade(targetDbGrade) else 0
        }
        if (imported > 0) {
            Snackbar.make(
                findViewById(android.R.id.content),
                getString(R.string.imported_count_message, imported),
                Snackbar.LENGTH_SHORT
            ).show()
        }

        listeningQuestions = withContext(Dispatchers.IO) {
            CsvDataLoader(this@LearningActivity).loadListeningQuestions()
        }

        fillBlankQuestions = withContext(Dispatchers.IO) {
            CsvDataLoader(this@LearningActivity).loadFillBlankQuestions()
        }
        sortQuestions = withContext(Dispatchers.IO) {
            CsvDataLoader(this@LearningActivity).loadSortQuestions()
        }
        refreshConversationQueue()

        val db = AppDatabase.getInstance(this@LearningActivity)
        allWordsFull = withContext(Dispatchers.IO) { db.wordDao().getAll() }

        allWords = if (gradeFilter == "All") {
            allWordsFull
        } else {
            allWordsFull.filter {
                it.grade == gradeFilter || it.grade == targetDbGrade
            }
        }

        viewModel.setGradeInfo(gradeFilter, allWords)

        updateStudyStatsView()

        routeNextQuestionAction()
    }
    // endregion

    // region Events & Observers
    private fun setupListeners() {
        layoutModeSelector.setOnClickListener { showModeSelectionSheet() }

        choiceButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { onChoiceSelected(index) }
        }

        val audioClickListener = View.OnClickListener {
            if (currentMode == LearningModes.TEST_LISTEN_Q2) {
                currentHighlightSearchIndex = 0
                val cleanText = textScriptDisplay.text.toString()
                textScriptDisplay.text = cleanText

                disableConversationButtons()

                val repeatScript =
                    currentConversationScript + "\nWait: 2000\n" + currentConversationScript
                conversationTts?.playScript(repeatScript)
            } else {
                speakCurrentLegacyAudio()
            }
        }

        buttonPlayAudio.setOnClickListener(audioClickListener)
        buttonReplayAudio.setOnClickListener(audioClickListener)

        buttonSoundSettings.setOnClickListener {
            runCatching {
                startActivity(Intent().apply {
                    setClassName(
                        this@LearningActivity,
                        "com.example.studylockapp.SoundSettingsActivity"
                    )
                })
            }.onFailure { Toast.makeText(this, "起動に失敗", Toast.LENGTH_SHORT).show() }
        }

        buttonNextQuestion.setOnClickListener {
            routeNextQuestionAction()
        }

        checkIncludeOtherGrades?.setOnCheckedChangeListener { _, isChecked ->
            includeOtherGradesReview = isChecked
            if (currentMode == LearningModes.TEST_LISTEN_Q2) {
                lifecycleScope.launch {
                    refreshConversationQueue()
                    viewModel.setMode(currentMode)
                }
            } else {
                loadNextQuestionLegacy()
            }
        }
        // ▼▼▼ 追加：選択肢を隠すの挙動 ▼▼▼
        checkboxHideChoices?.setOnCheckedChangeListener { _, isChecked ->
            // 会話モード・並び替えモードでは使わない（事故防止）
            if (currentMode == LearningModes.TEST_LISTEN_Q2 || currentMode == LearningModes.TEST_SORT) {
                coverLayout?.visibility = View.GONE
                return@setOnCheckedChangeListener
            }

            // まだ問題が無い / 選択肢が表示されてないなら無理に出さない
            val hasVisibleChoice = choiceButtons.any { it.visibility == View.VISIBLE }
            coverLayout?.visibility = if (isChecked && hasVisibleChoice) View.VISIBLE else View.GONE
        }

        buttonShowChoices?.setOnClickListener {
            // 「回答する」＝カバーを外して選択肢を見せる
            coverLayout?.visibility = View.GONE
        }

        buttonDontKnow?.setOnClickListener {
            // 「わからない」＝不正解処理（赤ボタンは塗らない）
            processAsIncorrect()
        }
    // ▲▲▲ 追加ここまで ▲▲▲
    }

    // 正解・不正解を受け取って、適切な音量設定で再生する共通関数
    private fun playAnswerSound(isCorrect: Boolean) {
        if (isCorrect) {
            soundEffectManager.playCorrect(settings.seCorrectVolume)
        } else {
            soundEffectManager.playWrong(settings.seWrongVolume)
        }
    }

    private fun setupObservers() {
        viewModel.gradeName.observe(this) { textCurrentGrade?.text = it }

        lifecycleScope.launch {
            viewModel.questionUiState.collect { state ->
                if (state !is QuestionUiState.Loading) resetUiForNewQuestion()

                when (state) {
                    is QuestionUiState.Conversation -> renderConversationUi(state)
                    is QuestionUiState.Empty -> showNoQuestion()
                    else -> {}
                }
            }
        }
        lifecycleScope.launch {
            sortViewModel.uiState.collect { state ->
                renderSortUi(state)

                val q = state.question ?: return@collect

                // 判定が出ていて、まだ採点していない場合だけポイント処理を実行
                if (state.isCorrect != null && !state.hasScored) {
                    val isCorrect = (state.isCorrect == true)

                    // まず履歴保存（穴埋め等と同じ）
                    StudyHistoryRepository.save(gradeFilter, LearningModes.TEST_SORT, isCorrect)

                    val basePoint = settings.getBasePoint(LearningModes.TEST_SORT)
                    var points = if (isCorrect) {
                        basePoint
                    } else {
                        -((basePoint * 0.25).toInt())
                    }

                    if (isCorrect) {
                        val userGradeStr = settings.currentLearningGrade
                        val gradeMap = mapOf(
                            "1級" to 1,
                            "準1級" to 2,
                            "2級" to 3,
                            "準2級" to 4,
                            "3級" to 5,
                            "4級" to 6,
                            "5級" to 7,
                            "1" to 1,
                            "1.5" to 2,
                            "2" to 3,
                            "2.5" to 4,
                            "3" to 5,
                            "4" to 6,
                            "5" to 7
                        )
                        val userGrade = gradeMap[userGradeStr] ?: 0
                        val questionGrade = gradeMap[q.grade.trim().replace("英検", "")] ?: 0

                        if (userGrade > 0 && questionGrade > 0) {
                            val gradeDiff = questionGrade - userGrade
                            points = when {
                                gradeDiff == 1 -> points * settings.pointReductionOneGradeDown / 100
                                gradeDiff >= 2 -> points * settings.pointReductionTwoGradesDown / 100
                                else -> points
                            }
                        }
                    }
                    val deltaPoint = points

                    // ★TEST_SORT用 progressId（SortQuestionをDBのprogressと紐づける）
                    val progressId = sortProgressId(q)

                    // ポイント反映＆履歴保存（DB）＋ TEST_SORT進捗保存
                    lifecycleScope.launch(Dispatchers.IO) {

                        val db = AppDatabase.getInstance(this@LearningActivity)

                        // ---- 1) ポイント反映＆履歴 ----
                        if (deltaPoint != 0) {
                            PointManager(this@LearningActivity).add(deltaPoint)

                            db.pointHistoryDao().insert(
                                PointHistoryEntity(
                                    mode = LearningModes.TEST_SORT,
                                    dateEpochDay = LocalDate.now(settings.getAppZoneId())
                                        .toEpochDay(),
                                    delta = deltaPoint
                                )
                            )
                        }

                        // ---- 2) TEST_SORT 進捗（due/new用） ----
                        val progressDao = db.wordProgressDao()
                        val nowSec = System.currentTimeMillis() / 1000L

                        val current = progressDao.getProgress(progressId, LearningModes.TEST_SORT)
                        val currentLevel = current?.level ?: 0

                        // 既存の共通ロジックを流用（テストモードは翌日扱いになる）
                        val (newLevel, nextDueAtSec) = calcNextDueAtSec(
                            isCorrect,
                            currentLevel,
                            nowSec
                        )

                        progressDao.upsert(
                            WordProgressEntity(
                                wordId = progressId,
                                mode = LearningModes.TEST_SORT,
                                level = newLevel,
                                nextDueAtSec = nextDueAtSec,
                                lastAnsweredAt = System.currentTimeMillis(),
                                studyCount = (current?.studyCount ?: 0) + 1
                            )
                        )

                        db.studyLogDao().insert(
                            WordStudyLogEntity(
                                wordId = progressId,
                                mode = LearningModes.TEST_SORT,
                                learnedAt = System.currentTimeMillis()
                            )
                        )

                        // ---- 3) UI更新 ----
                        withContext(Dispatchers.Main) {
                            updatePointView()
                            // ▼▼▼ ここに移動 ▼▼▼
                            updateStudyStatsView()

                            val msg = if (isCorrect) {
                                "正解！ +${deltaPoint}pt"
                            } else {
                                "不正解… ${deltaPoint}pt"
                            }
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                msg,
                                Snackbar.LENGTH_SHORT
                            ).show()

                            // ▼▼▼ 二重加点防止の採点済みマークもIO完了後に移動 ▼▼▼
                            sortViewModel.markScored()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.answerResult.collect { result ->
                if (currentMode == LearningModes.TEST_LISTEN_Q2) {

                    // ▼▼▼ 新しい共通リポジトリを使って保存 ▼▼▼
                    StudyHistoryRepository.save(gradeFilter, currentMode, result.isCorrect)

                    // --- 会話モード用の回答後処理 ---
                    var earnedPoints = result.points

                    // ペナルティ対象モードを定義
                    val penaltyModes = setOf(
                        LearningModes.TEST_FILL_BLANK,
                        LearningModes.TEST_SORT,
                        LearningModes.TEST_LISTEN_Q1,
                        LearningModes.TEST_LISTEN_Q2
                    )

                    // 不正解かつ、指定されたテストモードの場合のみ減点
                    if (currentMode in penaltyModes && !result.isCorrect) {
                        val db = AppDatabase.getInstance(this@LearningActivity)
                        val basePoint = settings.getBasePoint(currentMode)

                        // 25%のペナルティ計算
                        val penalty = (basePoint * 0.25).toInt()

                        if (penalty > 0) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val pointManager = PointManager(this@LearningActivity)
                                pointManager.add(-penalty)
                                db.pointHistoryDao().insert(
                                    PointHistoryEntity(
                                        mode = currentMode,
                                        dateEpochDay = LocalDate.now(settings.getAppZoneId())
                                            .toEpochDay(),
                                        delta = -penalty
                                    )
                                )
                            }
                            earnedPoints = -penalty
                        }
                    } else if (result.isCorrect && result.points > 0) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            PointManager(this@LearningActivity).add(result.points)
                            withContext(Dispatchers.Main) {
                                updatePointView()
                            }
                        }
                    }

                    textScriptDisplay.visibility = View.VISIBLE

                    textFeedback.text = result.feedback.replace("\\n", "\n")
                    textFeedback.visibility = View.VISIBLE

                    playAnswerSound(result.isCorrect)

                    val bgColor = ContextCompat.getColor(
                        this@LearningActivity,
                        if (result.isCorrect) R.color.snackbar_correct_bg else R.color.snackbar_wrong_bg
                    )

                    // ★修正: 正解時にもポイントを表示するように変更
                    val msg = if (result.isCorrect) "正解！ +${earnedPoints}pt" else {
                        if (earnedPoints < 0) "不正解… ${earnedPoints}pt" else "不正解…"
                    }

                    choiceButtons.forEach { it.isEnabled = false }
                    currentSnackbar?.dismiss()
                    currentSnackbar = Snackbar.make(
                        findViewById(android.R.id.content),
                        msg,
                        Snackbar.LENGTH_INDEFINITE
                    ).apply {
                        setBackgroundTint(bgColor)
                        setTextColor(android.graphics.Color.WHITE)
                        setAction("次へ") { viewModel.loadNextQuestion() }
                        show()
                    }
                    layoutActionButtons.visibility = View.VISIBLE

                } else {
                    // --- 通常モード ---
                    showFeedbackSnackbar(result)
                }

                updatePointView()
                updateStudyStatsView()
            }
        }
    }
    // endregion

    // region Logic: Routing & Display
    private fun routeNextQuestionAction() {
        if (currentMode == LearningModes.TEST_LISTEN_Q2) {
            viewModel.loadNextQuestion()
        } else {
            loadNextQuestionLegacy()
        }
    }

    private suspend fun refreshConversationQueue() {
        if (listeningQuestions.isEmpty()) return

        val filteredList = withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val nowSec = System.currentTimeMillis() / 1000L

            val dueIdsInOtherGrades = if (includeOtherGradesReview && gradeFilter != "All") {
                progressDao.getDueWordIdsOrdered(LearningModes.TEST_LISTEN_Q2, nowSec).toSet()
            } else {
                emptySet()
            }

            val targetDbGrade = normalizeGrade(gradeFilter)

            if (gradeFilter == "All") {
                listeningQuestions
            } else {
                val gfRaw = gradeFilter.trim()
                val gfNorm = targetDbGrade.trim()

                listeningQuestions.filter { q ->
                    val qgRaw = q.grade.trim()
                    // "英検"などのプレフィックスがあっても対応できるように、問題の級も正規化する
                    val qgNorm = normalizeGrade(qgRaw).trim()

                    // raw/正規化後の表記を相互に比較し、マッチの精度を上げる
                    val isMatch =
                        qgRaw == gfRaw || qgRaw == gfNorm || qgNorm == gfRaw || qgNorm == gfNorm
                    isMatch || (q.id in dueIdsInOtherGrades)
                }
            }
        }

        viewModel.listeningQuestions = filteredList
    }

    private fun disableConversationButtons() {
        choiceButtons.forEach {
            it.isEnabled = false
            it.alpha = 0.6f
        }
    }

    private fun renderConversationUi(state: QuestionUiState.Conversation) {
        textQuestionTitle.text = "会話を聞いて質問に答えてください"

        textQuestionBody.text = state.question.replace("\\n", "\n")
        textQuestionBody.visibility = View.GONE

        textScriptDisplay.text = state.script.replace("\\n", "\n")
        textScriptDisplay.visibility = View.GONE

        textFeedback.visibility = View.GONE

        currentConversationScript = state.script
        currentHighlightSearchIndex = 0
        currentQuestionText = state.question

        checkIncludeOtherGrades?.visibility = View.GONE
        checkboxAutoPlayAudio?.visibility = View.GONE
        buttonPlayAudio.visibility = View.GONE
        layoutActionButtons.visibility = View.GONE

        choiceButtons.forEachIndexed { index, btn ->
            if (index < state.choices.size) {
                btn.text = state.choices[index]
                btn.visibility = View.VISIBLE
                btn.isEnabled = false
                btn.isClickable = true
                btn.alpha = 0.6f

                ViewCompat.setBackgroundTintList(btn, defaultChoiceTints[index])
            } else {
                btn.visibility = View.GONE
            }
        }

        applyTtsParams()

        val repeatScript = state.script + "\nWait: 2000\n" + state.script
        conversationTts?.playScript(repeatScript)
    }
    // endregion

    // region Logic: Legacy Word Learning (Main Pipeline)
    private fun loadNextQuestionLegacy() {
        setSortLayoutVisible(false) // ★保険：TEST_SORT以外の経路で必ず消す
        loadingJob?.cancel()
        loadingJob = lifecycleScope.launch {
            stopMedia()
            resetStandardUi()

            if (allWordsFull.isEmpty()) {
                showNoQuestion()
                return@launch
            }

            val nextContext = withContext(Dispatchers.IO) {
                prepareQuestionData()
            }

            if (nextContext == null) {
                showNoQuestion()
            } else {
                currentLegacyContext = nextContext
                renderLegacyQuestion(nextContext)

                if (nextContext.shouldAutoPlay) {
                    speakText(nextContext.audioText)
                }
            }
        }
    }

    private suspend fun prepareQuestionData(): LegacyQuestionContext? {
        if (currentMode == LearningModes.TEST_FILL_BLANK) {
            val progressDao = AppDatabase.getInstance(this).wordProgressDao()
            val nowSec = System.currentTimeMillis() / 1000L

            val questionsForGrade = fillBlankQuestions.filter { it.grade == gradeFilter }
            val idsForGrade = questionsForGrade.map { it.id }.toSet()
            if (idsForGrade.isEmpty()) return null

            val dueIds = progressDao.getDueWordIdsOrdered(currentMode, nowSec)
            val nextDueId = dueIds.find { it in idsForGrade }
            if (nextDueId != null) {
                val question = questionsForGrade.find { it.id == nextDueId }
                val ctx =
                    question?.let { QuestionLogic.prepareFillBlankQuestion(it) } ?: return null
                return shrinkOptionsTo4(ctx) // ★追加：4択化
            }

            val progressedIds =
                progressDao.getAllProgressForMode(currentMode).map { it.wordId }.toSet()
            val newQuestions = questionsForGrade.filter { it.id !in progressedIds }
            if (newQuestions.isNotEmpty()) {
                val question = newQuestions.random()
                val ctx = QuestionLogic.prepareFillBlankQuestion(question)
                return shrinkOptionsTo4(ctx) // ★追加：4択化
            }

            return null
        }

        val nextWord = selectNextWord() ?: return null
        val choicePool = getChoicePool()

        // 通常は6択のまま
        val choices = QuestionLogic.buildChoices(nextWord, choicePool, 6, currentMode)

        val (title, body, options) = QuestionLogic.formatQuestionAndOptions(
            this, nextWord, choices, currentMode
        )

        val correctStr = QuestionLogic.getCorrectStringForMode(nextWord, currentMode)
        val correctIndex = options.indexOf(correctStr)

        val shouldAuto = when (currentMode) {
            LearningModes.JA_TO_EN -> false
            LearningModes.LISTENING, LearningModes.LISTENING_JP -> true
            else -> checkboxAutoPlayAudio?.isChecked == true && checkboxAutoPlayAudio?.visibility == View.VISIBLE
        }

        val audioText =
            if (currentMode == LearningModes.EN_EN_2) nextWord.description ?: "" else nextWord.word

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
     * 穴埋め用：選択肢を4つに圧縮（正解1つ + 他3つ）
     */
    private fun shrinkOptionsTo4(ctx: LegacyQuestionContext): LegacyQuestionContext {
        if (ctx.options.size <= 4) return ctx

        val correct = ctx.options.getOrNull(ctx.correctIndex) ?: return ctx

        val others = ctx.options.filterIndexed { i, _ -> i != ctx.correctIndex }
        val picked = others.shuffled().take(3).toMutableList()
        picked.add(correct)

        val newOptions = picked.shuffled()
        val newCorrectIndex = newOptions.indexOf(correct)

        return ctx.copy(
            options = newOptions,
            correctIndex = newCorrectIndex
        )
    }

    private fun renderLegacyQuestion(ctx: LegacyQuestionContext) {

        textQuestionTitle.text = ctx.title
        textQuestionBody.text = ctx.body
        textQuestionBody.visibility = if (ctx.body.isEmpty()) View.GONE else View.VISIBLE

        // ---- ここが大事：毎回スタイルを「完全に」決め打ちする ----
        val lp = textQuestionBody.layoutParams

        if (currentMode == LearningModes.TEST_FILL_BLANK) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            textQuestionBody.layoutParams = lp

            // 穴埋め：左寄せ（2行でも左揃え）
            textQuestionBody.gravity = android.view.Gravity.START
            textQuestionBody.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textQuestionBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)

            textQuestionBody.isSingleLine = false
            textQuestionBody.setHorizontallyScrolling(false)

        } else {
            // それ以外：センター寄りに戻す（複数行も中央揃え）
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            textQuestionBody.layoutParams = lp

            textQuestionBody.gravity = android.view.Gravity.CENTER
            textQuestionBody.textAlignment = View.TEXT_ALIGNMENT_CENTER
            textQuestionBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)

            textQuestionBody.isSingleLine = false
            textQuestionBody.setHorizontallyScrolling(false)
        }

        textQuestionBody.requestLayout()

        // ---- ボタン表示：options数だけ出す（穴埋め4択対応）----
        choiceButtons.forEachIndexed { index, btn ->
            btn.textSize = if (currentMode == LearningModes.EN_EN_1) 12f else 14f
            btn.visibility = View.GONE
            btn.text = "" // 残り文字対策
        }

        ctx.options.forEachIndexed { i, txt ->
            val btn = choiceButtons.getOrNull(i) ?: return@forEachIndexed
            btn.text = txt
            btn.visibility = View.VISIBLE
        }

        when (currentMode) {
            LearningModes.JA_TO_EN -> {
                checkboxAutoPlayAudio?.visibility = View.GONE
                buttonPlayAudio.visibility = View.GONE
            }

            LearningModes.MEANING, LearningModes.EN_EN_1, LearningModes.EN_EN_2 -> {
                checkboxAutoPlayAudio?.visibility = View.VISIBLE
                buttonPlayAudio.visibility = View.VISIBLE
            }

            else -> {
                checkboxAutoPlayAudio?.visibility = View.GONE
                buttonPlayAudio.visibility = View.VISIBLE
            }
        }
        // ▼▼▼ 追加：チェックONならカバーを出す（毎回ここで決め打ち） ▼▼▼
        val enableHide = (checkboxHideChoices?.isChecked == true)
        val allowHideInThisMode =
            currentMode != LearningModes.TEST_LISTEN_Q2 && currentMode != LearningModes.TEST_SORT

        val hasVisibleChoice = choiceButtons.any { it.visibility == View.VISIBLE }
        coverLayout?.visibility = if (enableHide && allowHideInThisMode && hasVisibleChoice) {
            View.VISIBLE
        } else {
            View.GONE
        }
    // ▲▲▲ 追加ここまで ▲▲▲
    }


    private fun onChoiceSelected(selectedIndex: Int) {
        if (currentMode == LearningModes.TEST_LISTEN_Q2) {
            viewModel.submitAnswer(selectedIndex)
            return
        }

        val ctx = currentLegacyContext ?: return

        // 「分からない」経由は -1 を渡す（ボタンは赤くしない）
        val isDontKnow = (selectedIndex !in choiceButtons.indices)
        val isCorrect = (!isDontKnow && selectedIndex == ctx.correctIndex)

        // 選択した瞬間にカバーは閉じる（テンポ良く）
        coverLayout?.visibility = View.GONE

        choiceButtons.forEach { it.isClickable = false }

        // 正解ボタンは必ず緑
        if (ctx.correctIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[ctx.correctIndex], greenTint)
        }

        // 不正解のときだけ「押したボタン」を赤にする（ただし dontKnow では赤にしない）
        if (!isCorrect && !isDontKnow && selectedIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[selectedIndex], redTint)
        }

        if (isCorrect) playCorrectEffect() else playWrongEffect()

        // ★保存＆共通処理
        StudyHistoryRepository.save(ctx.word.grade, currentMode, isCorrect)
        processAnswerResultLegacy(ctx.word, isCorrect)
    }

    private fun processAnswerResultLegacy(word: WordEntity, isCorrect: Boolean) {
        lifecycleScope.launch {
            val (addPoint, levelUpInfo) = registerAnswerToDb(word, isCorrect)

            if (isCorrect) {
                checkAndAnimateTrophy(levelUpInfo.first, levelUpInfo.second)
            }

            updateStudyStatsView()
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
    private fun processAsIncorrect() {
        val ctx = currentLegacyContext ?: return

        // カバーを外す
        coverLayout?.visibility = View.GONE

        // ボタン無効化 + 正解だけ緑表示（赤は出さない）
        choiceButtons.forEach { it.isClickable = false }
        if (ctx.correctIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[ctx.correctIndex], greenTint)
        }

        playWrongEffect()

        // 保存＆共通の不正解処理
        StudyHistoryRepository.save(ctx.word.grade, currentMode, false)
        processAnswerResultLegacy(ctx.word, false)
    }

    private suspend fun registerAnswerToDb(
        word: WordEntity,
        isCorrect: Boolean
    ): Pair<Int, Pair<Int, Int>> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val pointManager = PointManager(this@LearningActivity)
            val nowSec = nowEpochSec()

            val current = progressDao.getProgress(word.no, currentMode)
            val currentLevel = current?.level ?: 0
            val (newLevel, nextDueAtSec) = calcNextDueAtSec(isCorrect, currentLevel, nowSec)

            val basePoint = settings.getBasePoint(currentMode)

            var points = ProgressCalculator.calcPoint(isCorrect, currentLevel, basePoint)

            val userGradeStr = settings.currentLearningGrade
            val gradeMap = mapOf(
                "1級" to 1,
                "準1級" to 2,
                "2級" to 3,
                "準2級" to 4,
                "3級" to 5,
                "4級" to 6,
                "5級" to 7,
                "1" to 1,
                "1.5" to 2,
                "2" to 3,
                "2.5" to 4,
                "3" to 5,
                "4" to 6,
                "5" to 7
            )

            val userGrade = gradeMap[userGradeStr] ?: 0
            val wordGrade = gradeMap[word.grade.trim()] ?: 0

            if (userGrade > 0 && wordGrade > 0) {
                val gradeDiff = wordGrade - userGrade
                points = when {
                    gradeDiff == 1 -> points * settings.pointReductionOneGradeDown / 100
                    gradeDiff >= 2 -> points * settings.pointReductionTwoGradesDown / 100
                    else -> points
                }
            }

            // ★修正: 指定されたテストモードの場合のみ減点処理を行う
            val penaltyModes = setOf(
                LearningModes.TEST_FILL_BLANK,
                LearningModes.TEST_SORT,
                LearningModes.TEST_LISTEN_Q1,
                LearningModes.TEST_LISTEN_Q2
            )

            if (currentMode in penaltyModes && !isCorrect) {
                // 正解だった場合のポイントを仮計算
                var potentialPoints = ProgressCalculator.calcPoint(true, currentLevel, basePoint)
                if (userGrade > 0 && wordGrade > 0) {
                    val gradeDiff = wordGrade - userGrade
                    potentialPoints = when {
                        gradeDiff == 1 -> potentialPoints * settings.pointReductionOneGradeDown / 100
                        gradeDiff >= 2 -> potentialPoints * settings.pointReductionTwoGradesDown / 100
                        else -> potentialPoints
                    }
                }

                // 25%のペナルティ
                val penalty = (potentialPoints * 0.25).toInt()
                if (penalty > 0) {
                    pointManager.add(-penalty)
                    db.pointHistoryDao().insert(
                        PointHistoryEntity(
                            mode = currentMode,
                            dateEpochDay = LocalDate.now(settings.getAppZoneId()).toEpochDay(),
                            delta = -penalty
                        )
                    )
                }
                // 表示用にマイナス値を返す
                points = -penalty
            } else {
                // 通常の加算（テスト以外、または正解時）
                pointManager.add(points)
                if (points > 0) {
                    db.pointHistoryDao().insert(
                        PointHistoryEntity(
                            mode = currentMode,
                            dateEpochDay = LocalDate.now(settings.getAppZoneId()).toEpochDay(),
                            delta = points
                        )
                    )
                }
            }

            progressDao.upsert(
                WordProgressEntity(
                    wordId = word.no,
                    mode = currentMode,
                    level = newLevel,
                    nextDueAtSec = nextDueAtSec,
                    lastAnsweredAt = System.currentTimeMillis(),
                    studyCount = (current?.studyCount ?: 0) + 1
                )
            )
            db.studyLogDao().insert(
                WordStudyLogEntity(
                    wordId = word.no,
                    mode = currentMode,
                    learnedAt = System.currentTimeMillis()
                )
            )

            points to (currentLevel to newLevel)
        }
    }

    private fun checkAndAnimateTrophy(oldLevel: Int, newLevel: Int) {
        if (oldLevel < 2 && newLevel >= 2) animateTrophy(iconMasterBronze)
        if (oldLevel < 3 && newLevel >= 3) animateTrophy(iconMasterSilver)
        if (oldLevel < 4 && newLevel >= 4) animateTrophy(iconMasterGold)
        if (oldLevel < 5 && newLevel >= 5) animateTrophy(iconMasterCrystal)
        if (oldLevel < 6 && newLevel >= 6) animateTrophy(iconMasterPurple)
    }

    private fun animateTrophy(targetView: ImageView) {
        targetView.animate().cancel()
        targetView.scaleX = 1.0f
        targetView.scaleY = 1.0f
        targetView.alpha = 1.0f

        targetView.animate()
            .scaleX(1.7f)
            .scaleY(1.7f)
            .alpha(0.5f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                targetView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }
    // endregion

    // region Helper Logic (Selection, Time)
    private suspend fun selectNextWord(): WordEntity? {
        val db = AppDatabase.getInstance(this)
        val progressDao = db.wordProgressDao()
        val nowSec = nowEpochSec()
        val dueIdsOrdered = progressDao.getDueWordIdsOrdered(currentMode, nowSec)

        val wordMapFiltered = allWords.associateBy { it.no }
        val wordMapAll = allWordsFull.associateBy { it.no }

        val dueWords = if (includeOtherGradesReview && gradeFilter != "All") {
            dueIdsOrdered.mapNotNull { wordMapAll[it] }
        } else {
            dueIdsOrdered.mapNotNull { wordMapFiltered[it] }
        }
        if (dueWords.isNotEmpty()) return dueWords.first()

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

    private fun calcNextDueAtSec(
        isCorrect: Boolean,
        currentLevel: Int,
        nowSec: Long
    ): Pair<Int, Long> {
        val newLevel = if (isCorrect) currentLevel + 1 else maxOf(0, currentLevel - 2)
        val zone = settings.getAppZoneId()

        // ★追加: テストモードかどうかを判定
        val isTestMode = currentMode in setOf(
            LearningModes.TEST_FILL_BLANK,
            LearningModes.TEST_SORT,
            LearningModes.TEST_LISTEN_Q1,
            LearningModes.TEST_LISTEN_Q2
        )

        // 翌日の開始時刻（00:00:00）を計算
        val nextDaySec = Instant.ofEpochSecond(nowSec).atZone(zone)
            .toLocalDate().plusDays(1).atStartOfDay(zone).toEpochSecond()

        if (!isCorrect) {
            // --- 不正解の場合 ---
            return if (isTestMode) {
                // テストモードなら、間違えてもすぐには出さず「翌日」に回す
                newLevel to nextDaySec
            } else {
                // 通常モードなら、設定された短い時間（数分後など）で再出題
                newLevel to (nowSec + settings.wrongRetrySec)
            }
        }

        if (newLevel == 1) {
            // --- 正解したがレベルが低い(Lv1)場合 ---
            return if (isTestMode) {
                // テストモードなら、ここでも「翌日」まで空ける
                newLevel to nextDaySec
            } else {
                // 通常モードなら、短時間で復習
                newLevel to (nowSec + settings.level1RetrySec)
            }
        }

        // --- レベル2以上（間隔反復） ---
        // 元々「1日後、3日後...」という設定なので、そのまま適用（最低でも翌日になる）
        val days = when (newLevel) {
            2 -> 1; 3 -> 3; 4 -> 7; 5 -> 14; 6 -> 30; 7 -> 60; else -> 90
        }
        val dueDate =
            Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate().plusDays(days.toLong())
        return newLevel to dueDate.atStartOfDay(zone).toEpochSecond()
    }

    private suspend fun importMissingWordsForGrade(grade: String): Int =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val wordDao = db.wordDao()
            val csvWords =
                CsvDataLoader(this@LearningActivity).loadWords().filter { it.grade == grade }
            if (csvWords.isEmpty()) return@withContext 0
            val existing = wordDao.getAll().filter { it.grade == grade }.associateBy { it.word }
            val missing = csvWords.filter { existing[it.word] == null }
            if (missing.isNotEmpty()) wordDao.insertAll(missing)
            missing.size
        }
    // endregion

    // region UI Utilities (Stats, Effects, Sound)
    private fun resetUiForNewQuestion() {
        textFeedback.visibility = View.GONE
        textScriptDisplay.visibility = View.GONE
        choiceButtons.forEachIndexed { index, button ->
            ViewCompat.setBackgroundTintList(button, defaultChoiceTints[index])
            button.isEnabled = true
            button.isClickable = true
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

        if (currentMode.startsWith("test_")) {
            checkIncludeOtherGrades?.visibility = View.GONE
        } else {
            checkIncludeOtherGrades?.visibility = View.VISIBLE
        }

        buttonPlayAudio.visibility = View.VISIBLE
    }

    private fun showNoQuestion() {
        textQuestionTitle.text = getString(R.string.no_question_available)
        textQuestionBody.text = ""
        textQuestionBody.visibility = View.GONE
        choiceButtons.forEach { it.text = "----"; it.isEnabled = false }

        if (currentMode.startsWith("test_")) {
            checkIncludeOtherGrades?.visibility = View.GONE
            checkboxAutoPlayAudio?.visibility = View.GONE
            buttonPlayAudio.visibility = View.GONE
        } else {
            checkIncludeOtherGrades?.visibility = View.VISIBLE
        }
    }

    private fun showFeedbackSnackbar(result: AnswerResult) {
        playAnswerSound(result.isCorrect)
        val bgColor = ContextCompat.getColor(
            this,
            if (result.isCorrect) R.color.snackbar_correct_bg else R.color.snackbar_wrong_bg
        )
        choiceButtons.forEach { it.isEnabled = false }

        currentSnackbar?.dismiss()
        currentSnackbar = Snackbar.make(
            findViewById(android.R.id.content),
            result.feedback,
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setBackgroundTint(bgColor)
            setTextColor(android.graphics.Color.WHITE)
            setAction("次へ") { viewModel.loadNextQuestion() }
            show()
        }
        layoutActionButtons.visibility = View.VISIBLE
    }

    private fun showFeedbackSnackbarInternal(isCorrect: Boolean, addPoint: Int) {
        val bgColor = ContextCompat.getColor(
            this,
            if (isCorrect) R.color.snackbar_correct_bg else R.color.snackbar_wrong_bg
        )

        val msg: String = if (currentMode == LearningModes.TEST_FILL_BLANK) {
            val explanation = currentLegacyContext?.word?.japanese ?: ""

            if (isCorrect) {
                "正解！ +${addPoint}pt\n$explanation"
            } else {
                if (addPoint < 0) "不正解… ${addPoint}pt\n$explanation" else "不正解…\n$explanation"
            }
        } else {
            if (isCorrect) {
                val praise =
                    listOf("すごい！", "その調子！", "天才！", "完璧！", "いいね！", "ナイス！").random()
                "$praise +${addPoint}pt"
            } else {
                if (addPoint < 0) "不正解… ${addPoint}pt" else "不正解…"
            }
        }

        currentSnackbar?.dismiss()
        currentSnackbar =
            Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).apply {
                setBackgroundTint(bgColor)
                setTextColor(android.graphics.Color.WHITE)
                duration =
                    if (currentMode == LearningModes.TEST_FILL_BLANK) 5000
                    else settings.answerIntervalMs.toInt().coerceIn(600, 4000)
                show()
            }
    }

    private fun playCorrectEffect() {
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
                    override fun onAnimationEnd(animation: Animator) {
                        root.overlay.remove(drawable)
                    }
                })
                start()
            }
        }
        playAnswerSound(true)

        val ctx = currentLegacyContext ?: return
        val v = choiceButtons.getOrNull(ctx.correctIndex) ?: return
        v.animate().cancel()
        v.scaleX = 1f; v.scaleY = 1f
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
    }

    private fun playWrongEffect() {
        playAnswerSound(false)
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPoints.text = "保有ポイント: $total"
    }

    private fun showModeSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        val view = layoutInflater.inflate(R.layout.layout_mode_selection_sheet, null)
        dialog.setContentView(view)

        fun setupRow(
            rowId: Int,
            modeKey: String,
            title: String,
            iconRes: Int,
            colorRes: Int,
            isTestMode: Boolean = false
        ) {
            val card = view.findViewById<MaterialCardView>(rowId) ?: return
            val icon = card.findViewById<ImageView>(R.id.icon_mode)
            val textTitle = card.findViewById<TextView>(R.id.text_mode_title)
            val textReview = card.findViewById<TextView>(R.id.text_stat_review)
            val textNew = card.findViewById<TextView>(R.id.text_stat_new)

            val textBronze = card.findViewById<TextView>(R.id.stat_bronze)
            val textSilver = card.findViewById<TextView>(R.id.stat_silver)
            val textGold = card.findViewById<TextView>(R.id.stat_gold)
            val textCrystal = card.findViewById<TextView>(R.id.stat_crystal)

            textTitle.text = title
            icon.setImageResource(iconRes)
            val color = ContextCompat.getColor(this, colorRes)
            icon.setColorFilter(color)
            ViewCompat.setBackgroundTintList(
                card.findViewById(R.id.icon_container),
                ColorStateList.valueOf((color and 0x00FFFFFF) or (38 shl 24))
            ) // alpha ~0.15

            val stats = currentStats[modeKey]
            textReview.text = stats?.review?.toString() ?: "-"
            textNew.text = stats?.newCount?.toString() ?: "-"

            val total = stats?.total?.coerceAtLeast(1) ?: 1
            textBronze?.text = "${(stats?.bronze ?: 0) * 100 / total}%"
            textSilver?.text = "${(stats?.silver ?: 0) * 100 / total}%"
            textGold?.text = "${(stats?.gold ?: 0) * 100 / total}%"
            textCrystal?.text = "${(stats?.crystal ?: 0) * 100 / total}%"

            if (isTestMode && modeKey != LearningModes.TEST_LISTEN_Q2) card.alpha = 0.5f

            if (currentMode == modeKey) {
                card.strokeColor = color
                card.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                card.setCardBackgroundColor((color and 0x00FFFFFF) or (13 shl 24)) // alpha ~0.05
            }

            card.setOnClickListener {
                currentMode = modeKey

                if (currentMode.startsWith("test_")) {
                    includeOtherGradesReview = false
                    checkIncludeOtherGrades?.isChecked = false
                }

                // ★追加：モードに応じて表示/非表示を切り替える
                applyUiVisibilityForMode()

                updateStudyStatsView()

                when (modeKey) {
                    LearningModes.TEST_SORT -> {
                        // 並び替えはあなたの実装している開始処理へ
                        showFirstSortQuestion()
                    }

                    LearningModes.TEST_LISTEN_Q2 -> {
                        viewModel.setMode(modeKey)
                    }

                    else -> {
                        loadNextQuestionLegacy()
                    }
                }

                dialog.dismiss()
            }


        }

        setupRow(
            R.id.row_meaning,
            LearningModes.MEANING,
            getString(R.string.mode_meaning),
            R.drawable.ic_flash_cards_24,
            R.color.mode_indigo
        )
        setupRow(
            R.id.row_listening,
            LearningModes.LISTENING,
            getString(R.string.mode_listening),
            R.drawable.ic_headphones_24,
            R.color.mode_teal
        )
        setupRow(
            R.id.row_listening_jp,
            LearningModes.LISTENING_JP,
            getString(R.string.mode_listening_jp),
            R.drawable.ic_headphones_24,
            R.color.mode_teal
        )
        setupRow(
            R.id.row_ja_to_en,
            LearningModes.JA_TO_EN,
            getString(R.string.mode_japanese_to_english),
            R.drawable.ic_outline_cards_stack_24,
            R.color.mode_indigo
        )
        setupRow(
            R.id.row_en_en_1,
            LearningModes.EN_EN_1,
            getString(R.string.mode_english_english_1),
            R.drawable.ic_outline_cards_stack_24,
            R.color.mode_orange
        )
        setupRow(
            R.id.row_en_en_2,
            LearningModes.EN_EN_2,
            getString(R.string.mode_english_english_2),
            R.drawable.ic_outline_cards_stack_24,
            R.color.mode_orange
        )
        setupRow(
            R.id.row_test_fill,
            LearningModes.TEST_FILL_BLANK,
            "穴埋め",
            R.drawable.ic_edit_24,
            R.color.mode_pink,
            true
        )
        setupRow(
            R.id.row_test_sort,
            LearningModes.TEST_SORT,
            "並び替え",
            R.drawable.ic_sort_24,
            R.color.mode_pink,
            true
        )
        setupRow(
            R.id.row_test_listen_q1,
            LearningModes.TEST_LISTEN_Q1,
            "リスニング質問",
            R.drawable.ic_headphones_24,
            R.color.mode_teal,
            true
        )
        setupRow(
            R.id.row_test_listen_q2,
            LearningModes.TEST_LISTEN_Q2,
            "会話文リスニング",
            R.drawable.ic_outline_conversation_24,
            R.color.mode_teal,
            true
        )

        dialog.show()
    }

    private fun updateStudyStatsView() {
        lifecycleScope.launch(Dispatchers.Default) {

            val nowSec = System.currentTimeMillis() / 1000L
            val db = AppDatabase.getInstance(this@LearningActivity)

            // 単語系モード用
            val wordIdSet: Set<Int> = allWords.map { it.no }.toSet()

            val fillBlankIdSet: Set<Int> = fillBlankQuestions
                .filter { q -> q.grade.trim() == gradeFilter.trim() }
                .mapNotNull { q ->
                    when (val id = q.id) {
                        is Int -> id
                        is Long -> id.toInt()
                        is String -> id.toIntOrNull()
                        is Number -> id.toInt()
                        else -> null
                    }
                }
                .toSet()

            // 並び替え問題用
            val sortQuestionIdSet = if (gradeFilter == "All") {
                sortQuestions.map { sortProgressId(it) }.toSet()
            } else {
                val gfRaw = gradeFilter.trim()
                val gfNorm = normalizeGrade(gfRaw).trim()

                fun gradeMatches(questionGrade: String): Boolean {
                    val qgRaw = questionGrade.trim()
                    val qgNorm = normalizeGrade(qgRaw).trim()
                    return qgRaw == gfRaw || qgRaw == gfNorm || qgNorm == gfRaw || qgNorm == gfNorm
                }
                sortQuestions.filter { gradeMatches(it.grade) }.map { sortProgressId(it) }.toSet()
            }

            // ▼▼▼ 会話文リスニング用のロジックを修正 ▼▼▼
            // 1. まず、選択された級でリスニング問題を絞り込む
            val filteredListeningQuestions = if (gradeFilter == "All") {
                listeningQuestions
            } else {
                val gfRaw = gradeFilter.trim()
                val gfNorm = normalizeGrade(gfRaw).trim()
                fun gradeMatches(questionGrade: String): Boolean {
                    val qgRaw = questionGrade.trim()
                    val qgNorm = normalizeGrade(qgRaw).trim()
                    return qgRaw == gfRaw || qgRaw == gfNorm || qgNorm == gfRaw || qgNorm == gfNorm
                }
                listeningQuestions.filter { gradeMatches(it.grade) }
            }

            // 2. 絞り込んだリストからIDセットを生成する
            val listeningQuestionIdSet =
                filteredListeningQuestions.map { listeningProgressId(it) }.toSet()


            currentStats = mapOf(
                LearningModes.MEANING to LearningStatsLogic.computeModeStats(
                    db, wordIdSet, LearningModes.MEANING, nowSec, listeningQuestions
                ),
                LearningModes.LISTENING to LearningStatsLogic.computeModeStats(
                    db, wordIdSet, LearningModes.LISTENING, nowSec, listeningQuestions
                ),
                LearningModes.LISTENING_JP to LearningStatsLogic.computeModeStats(
                    db, wordIdSet, LearningModes.LISTENING_JP, nowSec, listeningQuestions
                ),
                LearningModes.JA_TO_EN to LearningStatsLogic.computeModeStats(
                    db, wordIdSet, LearningModes.JA_TO_EN, nowSec, listeningQuestions
                ),
                LearningModes.EN_EN_1 to LearningStatsLogic.computeModeStats(
                    db, wordIdSet, LearningModes.EN_EN_1, nowSec, listeningQuestions
                ),
                LearningModes.EN_EN_2 to LearningStatsLogic.computeModeStats(
                    db, wordIdSet, LearningModes.EN_EN_2, nowSec, listeningQuestions
                ),
                LearningModes.TEST_FILL_BLANK to LearningStatsLogic.computeModeStats(
                    db, fillBlankIdSet, LearningModes.TEST_FILL_BLANK, nowSec, listeningQuestions
                ),
                LearningModes.TEST_SORT to LearningStatsLogic.computeModeStats(
                    db, sortQuestionIdSet, LearningModes.TEST_SORT, nowSec, listeningQuestions
                ),
                // ▼▼▼ computeModeStatsに「絞り込んだ後」のリストを渡すように修正 ▼▼▼
                LearningModes.TEST_LISTEN_Q2 to LearningStatsLogic.computeModeStats(
                    db,
                    listeningQuestionIdSet,
                    LearningModes.TEST_LISTEN_Q2,
                    nowSec,
                    filteredListeningQuestions
                )
            )

            withContext(Dispatchers.Main) { updateModeUi() }
        }
    }

    private fun listeningProgressId(q: ListeningQuestion): Int {
        // IDが衝突しないように、モード名と問題IDを組み合わせてハッシュ化する
        return kotlin.math.abs("listening_q2:${q.id}".hashCode())
    }

    private fun updateModeUi() {
        val modeName = when (currentMode) {
            LearningModes.MEANING -> getString(R.string.mode_meaning)
            LearningModes.LISTENING -> getString(R.string.mode_listening)
            LearningModes.LISTENING_JP -> getString(R.string.mode_listening_jp)
            LearningModes.JA_TO_EN -> getString(R.string.mode_japanese_to_english)
            LearningModes.EN_EN_1 -> getString(R.string.mode_english_english_1)
            LearningModes.EN_EN_2 -> getString(R.string.mode_english_english_2)
            LearningModes.TEST_LISTEN_Q2 -> "会話文リスニング"
            LearningModes.TEST_SORT -> "並び替え"
            LearningModes.TEST_FILL_BLANK -> "穴埋め"
            LearningModes.TEST_LISTEN_Q1 -> "リスニング質問"
            else -> "選択中"
        }
        val iconRes = when (currentMode) {
            LearningModes.LISTENING, LearningModes.LISTENING_JP -> R.drawable.ic_headphones_24
            LearningModes.TEST_LISTEN_Q2 -> R.drawable.ic_outline_conversation_24
            LearningModes.TEST_FILL_BLANK -> R.drawable.ic_edit_24
            LearningModes.TEST_SORT -> R.drawable.ic_sort_24
            else -> R.drawable.ic_outline_cards_stack_24
        }

        selectorIconMode.setImageResource(iconRes)
        selectorTextTitle.text = modeName

        val stat = currentStats[currentMode]
        selectorTextReview.text = stat?.review?.toString() ?: "-"
        selectorTextNew.text = stat?.newCount?.toString() ?: "-"

        val total = stat?.total?.coerceAtLeast(1) ?: 1
        textMasterBronze.text = "${(stat?.bronze ?: 0) * 100 / total}%"
        textMasterSilver.text = "${(stat?.silver ?: 0) * 100 / total}%"
        textMasterGold.text = "${(stat?.gold ?: 0) * 100 / total}%"
        textMasterCrystal.text = "${(stat?.crystal ?: 0) * 100 / total}%"
        textMasterPurple.text = "${(stat?.purple ?: 0) * 100 / total}%"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            applyTtsParams()
        }
    }

    private fun applyTtsParams() {
        var speed = settings.getTtsSpeed()
        val pitch = settings.getTtsPitch()

        if (currentMode == LearningModes.TEST_LISTEN_Q2) {
            speed = when (gradeFilter) {
                "5級", "4級" -> 0.7f
                "3級" -> 0.8f
                else -> 0.9f
            }
        }

        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)

        conversationTts?.setSpeechRate(speed)
        conversationTts?.setPitch(pitch)
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
    private fun sortProgressId(q: SortQuestion): Int {
        // grade + id を混ぜて衝突しにくくする（例: 5級のso1 と 4級のso1 が別扱いになる）
        return kotlin.math.abs("${q.grade}:${q.id}".hashCode())
    }

    private suspend fun selectNextSortQuestion(): SortQuestion? = withContext(Dispatchers.IO) {
        if (sortQuestions.isEmpty()) return@withContext null

        // 1. gradeFilter に基づいて問題を絞り込む
        val filteredQuestions = if (gradeFilter == "All") {
            sortQuestions
        } else {
            val gfRaw = gradeFilter.trim()
            val gfNorm = normalizeGrade(gfRaw).trim()

            fun gradeMatches(questionGrade: String): Boolean {
                val qgRaw = questionGrade.trim()
                val qgNorm = normalizeGrade(qgRaw).trim()

                return qgRaw == gfRaw ||
                        qgRaw == gfNorm ||
                        qgNorm == gfRaw ||
                        qgNorm == gfNorm
            }
            sortQuestions.filter { gradeMatches(it.grade) }
        }

        if (filteredQuestions.isEmpty()) return@withContext null

        // progressId -> question のマップ
        val qMap: Map<Int, SortQuestion> = filteredQuestions.associateBy { sortProgressId(it) }
        val idSet: Set<Int> = qMap.keys

        val db = AppDatabase.getInstance(this@LearningActivity)
        val progressDao = db.wordProgressDao()
        val nowSec = System.currentTimeMillis() / 1000L

        // 1) due（復習）優先
        val dueIds = progressDao.getDueWordIdsOrdered(LearningModes.TEST_SORT, nowSec)
        val nextDueId = dueIds.firstOrNull { it in idSet }
        if (nextDueId != null) {
            return@withContext qMap[nextDueId]
        }

        // 2) 新規（まだ進捗が無いもの）
        val progressedIds = progressDao.getProgressIds(LearningModes.TEST_SORT).toSet()
        val newQuestions = filteredQuestions.filter { sortProgressId(it) !in progressedIds }
        if (newQuestions.isNotEmpty()) {
            return@withContext newQuestions.random()
        }

        // 3) dueも新規もない
        return@withContext null
    }

    private fun applyUiVisibilityForMode() {
        val sortRoot = findViewById<View?>(R.id.sort_question_layout)

        val isSortMode = (currentMode == LearningModes.TEST_SORT)

        // --- 並び替えUIの表示/非表示 ---
        sortRoot?.visibility = if (isSortMode) View.VISIBLE else View.GONE

        // --- 既存（レガシー）UIを並び替え中は隠す ---
        // 既存の問題テキスト類
        textQuestionTitle.visibility = if (isSortMode) View.GONE else View.VISIBLE
        textQuestionBody.visibility = if (isSortMode) View.GONE else View.VISIBLE
        textScriptDisplay.visibility =
            if (isSortMode) View.GONE else View.GONE // 通常も状況で出るので一旦GONEに寄せる
        textFeedback.visibility = if (isSortMode) View.GONE else View.GONE // 同上

        // 6択ボタンは全て隠す
        choiceButtons.forEach { it.visibility = if (isSortMode) View.GONE else View.VISIBLE }

        // アクションボタン群
        layoutActionButtons.visibility = if (isSortMode) View.GONE else View.VISIBLE
        buttonNextQuestion.visibility = if (isSortMode) View.GONE else View.VISIBLE

        // 音声系
        buttonPlayAudio.visibility = if (isSortMode) View.GONE else View.VISIBLE
        buttonReplayAudio.visibility = if (isSortMode) View.GONE else View.VISIBLE
        buttonSoundSettings.visibility = if (isSortMode) View.GONE else View.VISIBLE

        // チェックボックス類
        checkIncludeOtherGrades?.visibility = if (isSortMode) View.GONE else View.VISIBLE
        checkboxAutoPlayAudio?.visibility = if (isSortMode) View.GONE else View.VISIBLE
    }

    private fun showFirstSortQuestion() {
        val root = sortRoot() ?: return

        lifecycleScope.launch {
            val q = selectNextSortQuestion()
            if (q == null) {
                root.visibility = View.GONE
                return@launch
            }

            root.visibility = View.VISIBLE
            sortViewModel.setQuestion(q)
        }
    }

    private fun showNextSortQuestion() {
        val root = sortRoot() ?: return

        lifecycleScope.launch {
            val q = selectNextSortQuestion()
            if (q == null) {
                root.visibility = View.GONE
                return@launch
            }

            root.visibility = View.VISIBLE
            sortViewModel.setQuestion(q)
        }
    }


    private fun sortRoot(): View? = findViewById(R.id.sort_question_layout)

    private fun setSortLayoutVisible(visible: Boolean) {
        sortRoot()?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun renderSortUi(state: com.example.studylockapp.learning.SortQuestionUiState) {
        val root = sortRoot() ?: return
        val choicesContainer =
            root.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.flexbox_choices) ?: return
        val answersContainer =
            root.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.flexbox_answers) ?: return
        val checkButton =
            root.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_sort_check) ?: return
        val japaneseText = root.findViewById<TextView>(R.id.japanese_text) ?: return
        val correctAnswerText = root.findViewById<TextView>(R.id.correct_answer_text) ?: return
        val q = state.question ?: run {
            root.visibility = View.GONE
            return
        }
        root.visibility = View.VISIBLE
        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        // ---------------------------
        // 問題文（日本語）: 紺 + 1.2倍（増殖しない）
        // ---------------------------
        japaneseText.text = q.japaneseText

        if (sortJapaneseBaseTextSizePx == null) {
            sortJapaneseBaseTextSizePx = japaneseText.textSize
        }
        japaneseText.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            (sortJapaneseBaseTextSizePx ?: japaneseText.textSize) * 1.1f
        )
        japaneseText.setTextColor(Color.parseColor("#0D1B3D"))

        // ---------------------------
        // ▼ Gemini風：状態に応じてUIを更新（※既存stateから導出）
        // ---------------------------
        val isComplete = state.answerWords.size == q.words.size
        val isJudged = (state.isCorrect != null)

        if (!isJudged) {
            // 未判定
            checkButton.text = "判定"
            // 既存の「全部選んだら判定OK」ロジックを維持
            checkButton.isEnabled = isComplete && !state.hasScored
            correctAnswerText.visibility = View.GONE
        } else {
            // 判定後（正解/不正解）
            checkButton.text = "次へ"
            checkButton.isEnabled = true

            correctAnswerText.text = q.englishSentence

            // 正解英文：少しだけ大きく（増殖しない）
            if (sortCorrectBaseTextSizePx == null) {
                sortCorrectBaseTextSizePx = correctAnswerText.textSize
            }
            correctAnswerText.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                (sortCorrectBaseTextSizePx ?: correctAnswerText.textSize) * 1.10f
            )
            correctAnswerText.setTextColor(Color.parseColor("#0D1B3D"))

            correctAnswerText.visibility = View.VISIBLE
        }

        checkButton.setOnClickListener {
            if (!isJudged) {
                if (!isComplete) {
                    Toast.makeText(this, "全部選んでから判定してね", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                sortViewModel.checkAnswer()
            } else {
                showNextSortQuestion()
            }
        }

        // ---------------------------
        // コンテナ見た目（ここが重要：既存維持）
        // ---------------------------

        // choices（下）端の余白
        val choicesEdge = dp(16)

        // answers（上）端の余白：左右は半分（16→8）
        val answersEdgeH = dp(4)

        // answers（上）上下は 16dp をベースに「1.25倍」＋ 下だけ追加（2行でも潰れない）
        val answersBaseV = dp(4)
        val answersEdgeV = (answersBaseV * 1.25f).toInt() // 1.25倍
        val answersExtraBottom = dp(12)                   // “2行で下が潰れる”対策

        // チップ間隔（今の半分）
        val gap = dp(3)

        choicesContainer.setPadding(choicesEdge, choicesEdge, choicesEdge, choicesEdge)
        answersContainer.setPadding(answersEdgeH, answersEdgeV, answersEdgeH, answersEdgeV + answersExtraBottom)
        choicesContainer.clipChildren = false
        answersContainer.clipChildren = false
        (root as? ViewGroup)?.clipChildren = false
        (root as? ViewGroup)?.clipToPadding = false

        choicesContainer.clipToPadding = false
        answersContainer.clipToPadding = false

        // ---------------------------
        // チップ描画（変更なし）
        // ---------------------------
        choicesContainer.removeAllViews()
        answersContainer.removeAllViews()

        // Chip: テキスト少し小さく、padding 80%
        val textScale = 1.25f
        val padScale = 0.7f

        fun makeChip(word: String, onClick: () -> Unit): com.google.android.material.chip.Chip {
            return com.google.android.material.chip.Chip(this).apply {
                text = word
                isClickable = true
                isCheckable = false
                setEnsureMinTouchTargetSize(false)
                setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
                setTextColor(Color.parseColor("#0D1B3D"))

                val currentPx = textSize
                setTextSize(TypedValue.COMPLEX_UNIT_PX, currentPx * textScale)

                chipCornerRadius = dp(14).toFloat()
                chipStrokeWidth = dp(1).toFloat()
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#D6DAE3"))
                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F7F8FB"))
                rippleColor = ColorStateList.valueOf(Color.parseColor("#1A000000"))

                chipMinHeight = (dp(56) * padScale).toFloat().coerceAtLeast(dp(44).toFloat())
                chipStartPadding = (dp(10) * padScale).toFloat()
                chipEndPadding = (dp(10) * padScale).toFloat()
                textStartPadding = (dp(5) * padScale).toFloat()
                textEndPadding = (dp(5) * padScale).toFloat()

                elevation = dp(2).toFloat()

                setOnClickListener { onClick() }
            }
        }

        state.choiceWords.forEach { word ->
            val chip = makeChip(word) { sortViewModel.selectWord(word) }

            val lp = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(gap, gap, gap, gap)
            chip.layoutParams = lp

            choicesContainer.addView(chip)
        }

        state.answerWords.forEach { word ->
            val chip = makeChip(word) { sortViewModel.deselectWord(word) }

            val lp = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(gap, gap, gap, gap)
            chip.layoutParams = lp

            answersContainer.addView(chip)
        }
    }



}