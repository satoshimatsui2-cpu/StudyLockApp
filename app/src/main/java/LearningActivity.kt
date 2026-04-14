package com.example.studylockapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
 * 学習画面のActivity (一本化・最終版)
 */
class LearningActivity : AppCompatActivity(), QuizUiProvider {

    private lateinit var binding: ActivityLearningBinding
    private lateinit var animationManager: AnimationManager
    private lateinit var soundEffectManager: SoundEffectManager
    private lateinit var ttsController: LearningTtsController
    
    private var currentQuizId: String? = null

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

        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.loadNextQuiz()
        }

        setupListeners()
    }

    private fun setupListeners() {
        // 学習音声設定の切り替え (通常 / サイレント)
        binding.buttonToggleSilentMode.setOnClickListener {
            viewModel.toggleSilentMode()
        }

        binding.buttonNextQuestion.setOnClickListener {
            viewModel.onNextAfterReview()
        }

        binding.buttonPlayReviewWord.setOnClickListener {
            viewModel.requestAudioPlayback()
        }

        binding.buttonPlayReviewSentence.setOnClickListener {
            viewModel.uiState.value.currentWord?.sentence?.let { 
                viewModel.requestAudioPlayback(it) 
            }
        }

        binding.buttonPlayCompareWrong.setOnClickListener {
            viewModel.uiState.value.reviewUserAnswerText.let {
                if (it.isNotEmpty()) viewModel.requestAudioPlayback(it)
            }
        }

        binding.buttonPlayCompareCorrect.setOnClickListener {
            viewModel.uiState.value.reviewCorrectAnswerText.let {
                if (it.isNotEmpty()) viewModel.requestAudioPlayback(it)
            }
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
        val isSilent = state.silentMode == SilentMode.ON

        // 1. Mastery Journey Card
        binding.textJourneyTitle.text = if (state.currentLevel > 5) getString(R.string.label_until_long_term_master) else getString(R.string.label_until_basic_master)
        binding.textTargetLevel.text = getString(R.string.label_goal) + ": " + GradeLabelFormatter.format(state.targetLevel)
        binding.textSessionPointsSummary.text = state.sessionPoints.toString() + " PT"
        
        state.quiz?.word?.let { word ->
            binding.textWordGradeLabel.text = getString(R.string.review_label_learning_word, GradeLabelFormatter.format(word.grade))
        }

        binding.chipCurrentLevel.text = "LV " + state.currentLevel
        val relativeLevel = if (state.currentLevel > 5) state.currentLevel - 5 else state.currentLevel
        binding.progressMasteryRail.progress = (relativeLevel * 100) / 5
        binding.textJourneySubtitle.text = getString(R.string.label_next_step_hint, (5 - relativeLevel).coerceAtLeast(0))
        binding.textMasteryStatsSummary.text = getString(R.string.label_mastery_stats, state.basicMasterCount, state.longTermMasterCount)

        // 2. セッション進捗
        binding.progressHorizontal.progress = state.progress
        binding.textProgressPercent.text = getString(R.string.label_progress_step, state.currentStep, state.totalSteps)

        // 3. レビューカード表示
        if (state.isReviewing && state.currentWord != null) {
            binding.cardAnswerReview.visibility = View.VISIBLE
            binding.textReviewModeLabel.text = state.reviewModeLabel
            binding.textReviewQuestionText.text = state.reviewQuestionText
            binding.textReviewAnswerCorrect.text = getString(R.string.review_label_correct_word, state.reviewCorrectAnswerText)
            binding.textReviewAnswerWrong.visibility = if (!state.isLastAnswerCorrect) View.VISIBLE else View.GONE
            binding.textReviewAnswerWrong.text = getString(R.string.review_label_wrong_answer, state.reviewUserAnswerText)

            if (state.showListeningCompare) {
                binding.layoutListeningCompare.visibility = View.VISIBLE
                binding.textCompareWrongWord.text = state.reviewUserAnswerText
                binding.textCompareWrongPhonetic.text = state.wrongWord?.phonetic ?: ""
                binding.textCompareCorrectWord.text = state.reviewCorrectAnswerText
                binding.textCompareCorrectPhonetic.text = state.currentWord.phonetic ?: ""
            } else {
                binding.layoutListeningCompare.visibility = View.GONE
            }

            binding.textReviewPhonetic.text = state.currentWord.phonetic ?: ""
            binding.textReviewSentence.text = state.currentWord.sentence ?: ""
            binding.textReviewSentenceJp.text = state.currentWord.japaneseSentence ?: ""
            
            // サイレント時はレビュー内の再生ボタンも無効化・半透明化
            val playButtonsEnabled = !isSilent
            listOf(binding.buttonPlayReviewWord, binding.buttonPlayReviewSentence, 
                   binding.buttonPlayCompareWrong, binding.buttonPlayCompareCorrect).forEach {
                it.isEnabled = playButtonsEnabled
                it.alpha = if (playButtonsEnabled) 1.0f else 0.3f
            }
            
        } else {
            binding.cardAnswerReview.visibility = View.GONE
        }

        // 4. 学習音声UI (通常 / サイレント)
        binding.buttonToggleSilentMode.text = if (isSilent) getString(R.string.audio_mode_status_silent) else getString(R.string.audio_mode_status_normal)
        binding.buttonToggleSilentMode.setIconResource(if (isSilent) R.drawable.outline_volume_off_24 else R.drawable.ic_volume_up_24)

        state.quiz?.let { quiz ->
            if (currentQuizId != quiz.id) {
                currentQuizId = quiz.id
                resetAllChoiceButtons()
                RendererFactory.getRenderer(quiz.mode).render(this, quiz)
            }
        }
    }

    private fun handleEvent(event: LearningUiEvent) {
        when (event) {
            is LearningUiEvent.ShowCorrect -> {
                if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
                    soundEffectManager.playCorrect(1.0f)
                }
                val correctBtn = choiceButtons.find { it.text == event.answer }
                animationManager.playCorrectSequence(correctBtn, event.gainedPoints, event.tierChanged, viewModel.uiState.value.currentTier.label) {
                    viewModel.startReview()
                }
            }
            is LearningUiEvent.ShowWrong -> {
                if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
                    soundEffectManager.playWrong(1.0f)
                }
                val selectedBtn = choiceButtons.find { it.text == event.selected }
                val correctBtn = choiceButtons.find { it.text == event.correct }
                animationManager.playWrongSequence(selectedBtn, correctBtn) {
                    viewModel.startReview()
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
        }
    }

    private fun showSilentModeExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.silent_mode_explanation_title)
            .setMessage(R.string.silent_mode_explanation_body)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.action_dont_show_again) { _, _ ->
                viewModel.markSilentExplanationShown()
            }
            .show()
    }

    private fun resetChoiceButton(btn: MaterialButton) {
        btn.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.choice_button_background_default))
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.choice_button_text_default))
            strokeColor = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.choice_button_stroke_default))
            strokeWidth = resources.getDimensionPixelSize(R.dimen.choice_button_stroke_width_default)
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

    @SuppressLint("ClickableViewAccessibility")
    override fun showBasicQuiz(title: String, body: String, choices: List<String>) {
        resetAllChoiceButtons()
        binding.textQuestionTitle.text = title
        binding.textQuestionBody.text = body
        binding.choicesContainer.visibility = View.VISIBLE
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
