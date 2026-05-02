package com.example.studylockapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.studylockapp.databinding.ActivityLearningBinding
import com.example.studylockapp.learning.*
import com.example.studylockapp.data.SilentMode
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.ui.PronunciationCheckActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 学習画面のActivity
 */
class LearningActivity : AppCompatActivity(), QuizUiProvider {

    private lateinit var binding: ActivityLearningBinding
    private lateinit var animationManager: AnimationManager
    private lateinit var soundEffectManager: SoundEffectManager
    private lateinit var ttsController: LearningTtsController
    
    private var currentQuizId: String? = null
    private var defaultQuestionBodyTextSize: Float = 0f

    // 選択肢表示状態管理 (Persistence は ViewModel/State 側で実施)
    private var choicesRevealedForCurrentQuiz: Boolean = true

    private val viewModel: LearningViewModel by viewModels {
        LearningViewModelFactory(this)
    }

    private val choiceButtons by lazy {
        listOf(
            binding.buttonChoice1,
            binding.buttonChoice2,
            binding.buttonChoice3,
            binding.buttonChoice4
        )
    }

    private val pronunciationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            viewModel.refreshPoints()
        }

    // Rendererから安全にアクセスするためのブリッジメソッド
    fun exposeBinding(): ActivityLearningBinding = binding
    fun exposeLearningViewModel(): LearningViewModel = viewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLearningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        animationManager = AnimationManager(binding)
        soundEffectManager = SoundEffectManager(this)
        ttsController = LearningTtsController(this)

        defaultQuestionBodyTextSize = binding.textQuestionBody.textSize

        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.loadInitialQuiz()
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.layoutJourneyHeader.layoutModePill.rootModePill.setOnClickListener {
            animationManager.playModeToggleClick(it)
            viewModel.toggleSilentMode()
        }

        // 補助操作バー: サウンド設定
        binding.buttonSoundSettingsAssist.setOnClickListener {
            val intent = Intent(this, SoundSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.layoutReviewCard.buttonNextQuestion.setOnClickListener {
            viewModel.onNextAfterReview()
        }

        binding.layoutReviewCard.buttonPlayReviewWord.setOnClickListener {
            viewModel.requestAudioPlayback()
        }

        binding.layoutReviewCard.buttonPlayReviewSentence.setOnClickListener {
            viewModel.uiState.value.currentWord?.sentence?.let { 
                viewModel.requestAudioPlayback(it) 
            }
        }
        
        binding.layoutReviewCard.includeWrong.buttonPlay.setOnClickListener {
            viewModel.uiState.value.reviewUserAnswerText.let {
                if (it.isNotEmpty()) viewModel.requestAudioPlayback(it)
            }
        }

        binding.layoutReviewCard.includeCorrect.buttonPlay.setOnClickListener {
            viewModel.uiState.value.reviewCorrectAnswerText.let {
                if (it.isNotEmpty()) viewModel.requestAudioPlayback(it)
            }
        }

        // 選択肢コンテナ内の「わからない」ボタン
        binding.buttonUnknownAnswer.setOnClickListener {
            if (!viewModel.uiState.value.isAnswering && !viewModel.uiState.value.isReviewing) {
                viewModel.submitUnknownAnswer()
            }
        }

        // 補助操作バー: 他級復習トグル
        binding.buttonToggleOtherGradeReviews.setOnClickListener {
            viewModel.setIncludeOtherGradeReviews(!viewModel.uiState.value.includeOtherGradeReviews)
        }

        // 補助操作バー: 選択肢ON/OFF
        binding.buttonToggleChoices.setOnClickListener {
            toggleChoicesInitialVisibility()
        }

        // カバーカードの「回答する」ボタン
        binding.buttonRevealChoices.setOnClickListener {
            revealChoicesForCurrentQuiz()
        }

        // 補助操作バー: 再生
        binding.buttonReplayQuestionAudio.setOnClickListener {
            viewModel.requestAudioPlayback()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        updateUi(state)
                    }
                }
                launch {
                    viewModel.uiEvent.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUi(state: LearningUiState) {
        ModePillBinder.bind(binding.layoutJourneyHeader.layoutModePill, ModePillMapper.map(state.silentMode))
        JourneyHeaderBinder.bind(binding.layoutJourneyHeader, JourneyHeaderMapper.map(state))

        // 設定の見た目を更新
        updateOtherGradeReviewButton(state.includeOtherGradeReviews)
        updateToggleChoicesButton(state.choicesInitiallyVisible)

        if (state.isReviewing && state.currentWord != null) {
            binding.cardQuestion.visibility = View.GONE
            binding.layoutReviewCard.rootReviewCard.visibility = View.VISIBLE
            binding.layoutAssistButtons.visibility = View.GONE 
            ReviewCardBinder.bind(binding.layoutReviewCard, ReviewCardMapper.map(state))
            
            val isSortMode = state.quiz?.mode == QuizMode.SENTENCE_SORT
            binding.layoutReviewCard.buttonPlayReviewWord.visibility = if (isSortMode) View.GONE else View.VISIBLE
        } else {
            binding.layoutReviewCard.rootReviewCard.visibility = View.GONE
            binding.cardQuestion.visibility = View.VISIBLE
            
            state.quiz?.let { quiz ->
                updateAssistButtonsForQuiz(quiz.mode)
            }
        }

        binding.progressHorizontal.progress = state.progress
        binding.textProgressPercent.text = getString(R.string.label_progress_step, state.currentStep, state.totalSteps)

        renderQuizIfNeeded(state)
        updateChoiceCoverVisibility(state)
    }

    private fun updateOtherGradeReviewButton(enabled: Boolean) {
        binding.buttonToggleOtherGradeReviews.apply {
            text = if (enabled) "他級ON" else "他級OFF"
            
            val bgColor = ContextCompat.getColor(context, if (enabled) R.color.mustard_soft else R.color.white)
            val strokeColor = ContextCompat.getColor(context, if (enabled) R.color.mustard_accent else R.color.outline)
            val textColor = ContextCompat.getColor(context, if (enabled) R.color.unknown_text else R.color.text_sub)

            backgroundTintList = ColorStateList.valueOf(bgColor)
            this.strokeColor = ColorStateList.valueOf(strokeColor)
            setTextColor(textColor)
            iconTint = ColorStateList.valueOf(textColor)
        }
    }

    private fun updateAssistButtonsForQuiz(mode: QuizMode) {
        if (mode == QuizMode.SENTENCE_SORT) {
            binding.layoutAssistButtons.visibility = View.GONE
            binding.layoutAnswerAssistButtons.visibility = View.GONE
        } else {
            binding.layoutAssistButtons.visibility = View.VISIBLE
            binding.layoutAnswerAssistButtons.visibility = View.VISIBLE
            
            val canReplay = canReplayQuestionAudio(mode)
            updateReplayButtonState(canReplay)
        }
    }

    private fun updateReplayButtonState(canReplay: Boolean) {
        binding.buttonReplayQuestionAudio.apply {
            isEnabled = canReplay
            alpha = if (canReplay) 1.0f else 0.4f

            val strokeCol = ContextCompat.getColor(context, if (canReplay) R.color.navy_primary else R.color.assist_disabled)
            val iconCol = ContextCompat.getColor(context, if (canReplay) R.color.navy_primary else R.color.assist_disabled)
            val bgCol = ContextCompat.getColor(context, if (canReplay) R.color.white else R.color.assist_disabled_bg)

            backgroundTintList = ColorStateList.valueOf(bgCol)
            strokeColor = ColorStateList.valueOf(strokeCol)
            iconTint = ColorStateList.valueOf(iconCol)
            setTextColor(iconCol)
        }
    }

    private fun canReplayQuestionAudio(mode: QuizMode): Boolean {
        return when (mode) {
            QuizMode.EN_TO_JP,
            QuizMode.LISTEN_EN,
            QuizMode.LISTEN_FILL_BLANK -> true
            else -> false
        }
    }

    private fun renderQuizIfNeeded(state: LearningUiState) {
        state.quiz?.let { quiz ->
            if (currentQuizId != quiz.id) {
                currentQuizId = quiz.id
                resetUiForNewQuiz(state)
                RendererFactory.getRenderer(quiz.mode).render(this, quiz)
            }
        }
    }

    private fun resetUiForNewQuiz(state: LearningUiState) {
        ttsController.stop()
        resetAllChoiceButtons()
        resetAssistButtons()
        
        // 選択肢表示状態のリセット (新しい問題開始時に保存設定を読み込む)
        choicesRevealedForCurrentQuiz = state.choicesInitiallyVisible
        updateChoiceCoverVisibility(state)

        binding.choicesContainer.visibility = View.VISIBLE
        binding.layoutSortContainer.visibility = View.GONE
        binding.flexboxAnswer.removeAllViews()
        binding.flexboxCandidates.removeAllViews()
        setQuestionBodyTextScale(1.0f)
    }

    private fun resetAssistButtons() {
        binding.buttonUnknownAnswer.apply {
            isEnabled = true
            alpha = 1.0f
        }
        binding.buttonToggleChoices.apply {
            isEnabled = true
            alpha = 1.0f
        }
    }

    private fun resetChoiceButton(btn: MaterialButton) {
        btn.apply {
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.white)
            )
            setTextColor(
                ContextCompat.getColor(context, R.color.navy_primary)
            )
            strokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.navy_primary)
            )

            alpha = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            translationX = 0f
            translationY = 0f
            isEnabled = true
            isClickable = true
            clearAnimation()
        }
    }

    private fun resetAllChoiceButtons() { choiceButtons.forEach { resetChoiceButton(it) } }

    private fun handleEvent(event: LearningUiEvent) {
        when (event) {
            is LearningUiEvent.NoAvailableWords -> {
                Toast.makeText(this, "現在学習出来る単語がありません", Toast.LENGTH_LONG).show()
                finish()
            }
            is LearningUiEvent.ShowCorrect -> {
                if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
                    soundEffectManager.playCorrect()
                }
                if (viewModel.uiState.value.quiz?.mode == QuizMode.SENTENCE_SORT) {
                    viewModel.startReview()
                } else {
                    val correctBtn = choiceButtons.find { it.text == event.answer }
                    animationManager.playCorrectSequence(correctBtn, event.gainedPoints, event.tierChanged, viewModel.uiState.value.currentTier.label) {
                        viewModel.startReview()
                    }
                }
            }
            is LearningUiEvent.ShowWrong -> {
                if (!event.isUnknown && viewModel.uiState.value.silentMode == SilentMode.OFF) {
                    soundEffectManager.playWrong()
                }
                if (viewModel.uiState.value.quiz?.mode == QuizMode.SENTENCE_SORT) {
                    viewModel.startReview()
                } else {
                    val selectedBtn = choiceButtons.find { it.text == event.selected }
                    val correctBtn = choiceButtons.find { it.text == event.correct }
                    
                    if (event.isUnknown) {
                        binding.buttonUnknownAnswer.isEnabled = false
                        viewModel.startReview()
                    } else {
                        animationManager.playWrongSequence(selectedBtn, correctBtn) {
                            viewModel.startReview()
                        }
                    }
                }
            }
            is LearningUiEvent.PlayAudio -> {
                if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
                    ttsController.speak(event.text)
                }
            }
            is LearningUiEvent.QuizFinished -> finish()
            is LearningUiEvent.ShowMasteryBadge -> animationManager.playTierUpAnimation(event.tier.label)
            is LearningUiEvent.ShowSilentModeExplanation -> showSilentModeExplanationDialog()
            is LearningUiEvent.ShowFlyingLevelUp -> animationManager.playFlyingLevelUp(event.oldLevel, event.newLevel)
            is LearningUiEvent.ShowBasicMasterCelebration -> animationManager.playMasterCelebration(isLongTerm = false)
            is LearningUiEvent.ShowLongTermMasterCelebration -> animationManager.playMasterCelebration(isLongTerm = true)
            is LearningUiEvent.ShowLevel5BonusInduction -> showLevel5BonusInductionDialog(event.word)
        }
    }

    private fun showSilentModeExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.silent_mode_explanation_title)
            .setMessage(R.string.silent_mode_explanation_body)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showLevel5BonusInductionDialog(word: WordEntity) {
        AlertDialog.Builder(this)
            .setTitle("🎉 LV5達成！")
            .setMessage("発音チェックに挑戦すると、🎙 発音OKバッジと10ptがもらえます。\nあとで履歴画面から挑戦することもできます。")
            .setPositiveButton("発音チェックする") { _, _ ->
                val intent = Intent(this, PronunciationCheckActivity::class.java).apply {
                    putExtra("WORD_ID", word.no.toLong())
                    putExtra("WORD_TEXT", word.word)
                    putExtra("WORD_MEANING", word.japanese)
                    putExtra("WORD_SENTENCE", word.sentence)
                    putExtra("WORD_SENTENCE_JA", word.japaneseSentence)
                    putExtra("WORD_GRADE", word.grade.toString())
                    putExtra("CHECK_TYPE", "word")
                    putExtra("FROM_LEVEL5_BONUS", true)
                }
                pronunciationLauncher.launch(intent)
            }
            .setNegativeButton("あとで", null)
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun showBasicQuiz(title: String, body: String, choices: List<String>) {
        binding.textQuestionTitle.text = title
        binding.textQuestionBody.text = body
        
        viewModel.uiState.value.quiz?.word?.let { word ->
            binding.textQuestionGradeBadge.text = GradeLabelFormatter.format(word.grade)
            binding.cardQuestionGradeBadge.visibility = View.VISIBLE
        }

        binding.choicesContainer.visibility = View.VISIBLE
        binding.layoutSortContainer.visibility = View.GONE
        
        choiceButtons.forEachIndexed { i, btn ->
            if (i < choices.size) {
                btn.text = choices[i]
                btn.visibility = View.VISIBLE
                btn.setOnClickListener {
                    if (!viewModel.uiState.value.isAnswering && !viewModel.uiState.value.isReviewing) {
                        viewModel.submitAnswer(btn.text.toString())
                    }
                }
            } else {
                btn.visibility = View.GONE
            }
        }
        
        // 「わからない」ボタンは常に末尾に表示（showBasicQuiz時）
        binding.buttonUnknownAnswer.visibility = View.VISIBLE
        updateChoiceCoverVisibility(viewModel.uiState.value)
    }

    override fun setQuestionBodyTextScale(scale: Float) {
        binding.textQuestionBody.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultQuestionBodyTextSize * scale)
    }

    override fun playAudio(text: String) {
        if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
            ttsController.speak(text)
        }
    }

    // --- 選択肢カバー関連のメソッド ---

    private fun toggleChoicesInitialVisibility() {
        val nextValue = !viewModel.uiState.value.choicesInitiallyVisible
        viewModel.setChoicesInitiallyVisible(nextValue)
        
        // トグルした瞬間に現在のクイズの状態も同期させる
        choicesRevealedForCurrentQuiz = nextValue
        updateChoiceCoverVisibility(viewModel.uiState.value)
    }

    private fun revealChoicesForCurrentQuiz() {
        choicesRevealedForCurrentQuiz = true
        updateChoiceCoverVisibility(viewModel.uiState.value)
    }

    private fun updateChoiceCoverVisibility(state: LearningUiState) {
        val shouldShowCover = isChoiceCoverAvailableForCurrentQuiz(state)
        binding.choiceCoverCard.visibility = if (shouldShowCover) View.VISIBLE else View.GONE
        
        // カバー表示中は4択ボタンを無効化
        choiceButtons.forEach { it.isEnabled = !shouldShowCover }
    }

    private fun updateToggleChoicesButton(enabled: Boolean) {
        binding.buttonToggleChoices.apply {
            text = if (enabled) "選択肢ON" else "選択肢OFF"
            setIconResource(if (enabled) R.drawable.ic_visibility_24 else R.drawable.ic_visibility_off_24)

            val bgColor = ContextCompat.getColor(context, if (enabled) R.color.navy_soft else R.color.white)
            val strokeColor = ContextCompat.getColor(context, if (enabled) R.color.navy_primary else R.color.outline)
            val textColor = ContextCompat.getColor(context, if (enabled) R.color.navy_primary else R.color.text_sub)

            backgroundTintList = ColorStateList.valueOf(bgColor)
            this.strokeColor = ColorStateList.valueOf(strokeColor)
            setTextColor(textColor)
            iconTint = ColorStateList.valueOf(textColor)
        }
    }

    private fun isChoiceCoverAvailableForCurrentQuiz(state: LearningUiState): Boolean {
        val mode = state.quiz?.mode
        
        // カバーを表示する条件：
        // 1. 4択系問題である (SENTENCE_SORTではない、かつ選択肢が存在する)
        // 2. choicesInitiallyVisible == false
        // 3. choicesRevealedForCurrentQuiz == false
        // 4. レビュー中ではない
        // 5. 回答中ではない
        
        val isChoiceQuiz = mode != null && mode != QuizMode.SENTENCE_SORT
        return isChoiceQuiz && 
                !state.choicesInitiallyVisible &&
                !choicesRevealedForCurrentQuiz && 
                !state.isReviewing && 
                !state.isAnswering
    }

    override fun onDestroy() {
        ttsController.release()
        soundEffectManager.release()
        super.onDestroy()
    }
}
