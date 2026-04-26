package com.example.studylockapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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

        // 右上の設定ボタン
        binding.layoutJourneyHeader.buttonAdminSettingsHeader.setOnClickListener {
            val intent = Intent(this, AdminSettingsActivity::class.java)
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

        // 補助操作バー: わからない
        binding.buttonUnknownAnswer.setOnClickListener {
            if (!viewModel.uiState.value.isAnswering && !viewModel.uiState.value.isReviewing) {
                viewModel.submitUnknownAnswer()
            }
        }

        // 補助操作バー: 選択肢
        binding.buttonToggleChoices.setOnClickListener {
            // TODO: 選択肢の表示/非表示切り替えを実装する
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
            
            state.quiz?.mode?.let { mode ->
                updateAssistButtonsForQuiz(mode)
            }
        }

        binding.progressHorizontal.progress = state.progress
        binding.textProgressPercent.text = getString(R.string.label_progress_step, state.currentStep, state.totalSteps)

        renderQuizIfNeeded(state)
    }

    private fun updateAssistButtonsForQuiz(mode: QuizMode) {
        if (mode == QuizMode.SENTENCE_SORT) {
            binding.layoutAssistButtons.visibility = View.GONE
        } else {
            binding.layoutAssistButtons.visibility = View.VISIBLE
            
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
                resetUiForNewQuiz()
                RendererFactory.getRenderer(quiz.mode).render(this, quiz)
            }
        }
    }

    private fun resetUiForNewQuiz() {
        resetAllChoiceButtons()
        resetAssistButtons()
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
                    soundEffectManager.playCorrect(1.0f)
                }
                if (viewModel.uiState.value.quiz?.mode == QuizMode.SENTENCE_SORT) {
                    playSentenceAudioDelayed()
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
                    soundEffectManager.playWrong(1.0f)
                }
                if (viewModel.uiState.value.quiz?.mode == QuizMode.SENTENCE_SORT) {
                    playSentenceAudioDelayed()
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
        }
    }

    private fun playSentenceAudioDelayed() {
        binding.rootLayout.postDelayed({
            if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
                viewModel.uiState.value.currentWord?.sentence?.let { 
                    ttsController.speak(it) 
                }
            }
        }, 700)
    }

    private fun showSilentModeExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.silent_mode_explanation_title)
            .setMessage(R.string.silent_mode_explanation_body)
            .setPositiveButton(R.string.ok, null)
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
    }

    override fun setQuestionBodyTextScale(scale: Float) {
        binding.textQuestionBody.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultQuestionBodyTextSize * scale)
    }

    override fun playAudio(text: String) {
        if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
            ttsController.speak(text)
        }
    }

    override fun onDestroy() {
        ttsController.release()
        soundEffectManager.release()
        super.onDestroy()
    }
}
