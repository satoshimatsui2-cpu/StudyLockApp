package com.example.studylockapp

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.studylockapp.databinding.ActivityLearningBinding
import com.example.studylockapp.learning.*
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
        binding.buttonToggleAutoPlay.setOnClickListener {
            viewModel.toggleAutoPlay()
        }

        binding.buttonToggleAudioMode.setOnClickListener {
            val next = if (viewModel.uiState.value.audioStudyMode == QuizManager.AudioStudyMode.NORMAL) 
                QuizManager.AudioStudyMode.AUDIO_RESTRICTED else QuizManager.AudioStudyMode.NORMAL
            viewModel.setAudioStudyMode(next)
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
        // 1. Mastery Journey Card
        val isLongTerm = state.currentLevel > 5
        binding.textJourneyTitle.text = if (isLongTerm) {
            getString(R.string.label_until_long_term_master)
        } else {
            getString(R.string.label_until_basic_master)
        }
        
        binding.textTargetLevel.text = "目標: ${GradeLabelFormatter.format(state.targetLevel)}"
        binding.textSessionPointsSummary.text = "${state.sessionPoints} PT"
        
        state.quiz?.word?.let { word ->
            val gradeLabel = GradeLabelFormatter.format(word.grade)
            binding.textWordGradeLabel.text = getString(R.string.review_label_learning_word, gradeLabel)
        }

        binding.chipCurrentLevel.text = "LV ${state.currentLevel}"
        
        val relativeLevel = if (isLongTerm) state.currentLevel - 5 else state.currentLevel
        binding.progressMasteryRail.progress = (relativeLevel * 100) / 5
        
        val stepsToNext = (5 - relativeLevel).coerceAtLeast(0)
        binding.textJourneySubtitle.text = getString(R.string.label_next_step_hint, stepsToNext)
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
            if (!state.isLastAnswerCorrect) {
                binding.textReviewAnswerWrong.visibility = View.VISIBLE
                binding.textReviewAnswerWrong.text = getString(R.string.review_label_wrong_answer, state.reviewUserAnswerText)
            } else {
                binding.textReviewAnswerWrong.visibility = View.GONE
            }

            // 聞き比べ表示
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
            
        } else {
            binding.cardAnswerReview.visibility = View.GONE
        }

        // 4. サウンドUI
        binding.buttonToggleAutoPlay.text = if (state.isAutoPlayEnabled) "自動再生 ON" else "自動再生 OFF"
        val isRestricted = (state.audioStudyMode == QuizManager.AudioStudyMode.AUDIO_RESTRICTED)
        binding.buttonToggleAudioMode.setImageResource(if (isRestricted) R.drawable.outline_volume_off_24 else R.drawable.ic_volume_up_24)
        binding.textAudioModeLabel.text = if (isRestricted) getString(R.string.label_audio_mode_restricted) else getString(R.string.label_audio_mode_normal)

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
                if (viewModel.uiState.value.isAutoPlayEnabled && viewModel.uiState.value.audioStudyMode == QuizManager.AudioStudyMode.NORMAL) {
                    soundEffectManager.playCorrect(1.0f)
                }
                val correctBtn = choiceButtons.find { it.text == event.answer }
                animationManager.playCorrectSequence(correctBtn, event.gainedPoints, event.tierChanged, viewModel.uiState.value.currentTier.label) {
                    viewModel.startReview()
                }
            }
            is LearningUiEvent.ShowWrong -> {
                if (viewModel.uiState.value.isAutoPlayEnabled && viewModel.uiState.value.audioStudyMode == QuizManager.AudioStudyMode.NORMAL) {
                    soundEffectManager.playWrong(1.0f)
                }
                val selectedBtn = choiceButtons.find { it.text == event.selected }
                val correctBtn = choiceButtons.find { it.text == event.correct }

                animationManager.playWrongSequence(selectedBtn, correctBtn) {
                    viewModel.startReview()
                }
            }
            is LearningUiEvent.PlayAudio -> {
                if (viewModel.uiState.value.audioStudyMode == QuizManager.AudioStudyMode.NORMAL) {
                    ttsController.speak(event.text)
                }
            }
            is LearningUiEvent.QuizFinished -> finish()
            is LearningUiEvent.ShowMasteryBadge -> animationManager.playTierUpAnimation(event.tier.label)
        }
    }

    private fun resetChoiceButton(btn: MaterialButton) {
        btn.apply {
            // 背景色とテキスト色のリセット
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.choice_button_background_default))
            setTextColor(ContextCompat.getColor(context, R.color.choice_button_text_default))
            
            // 枠線のリセット
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.choice_button_stroke_default))
            strokeWidth = resources.getDimensionPixelSize(R.dimen.choice_button_stroke_width_default)
            
            // 透過度とスケールのリセット
            alpha = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            
            // 座標のリセット
            translationX = 0f
            translationY = 0f
            
            // 状態のリセット
            isEnabled = true
            isClickable = true
            
            // アニメーションの停止
            clearAnimation()
        }
    }

    private fun resetAllChoiceButtons() {
        choiceButtons.forEach { resetChoiceButton(it) }
    }

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
        if (viewModel.uiState.value.audioStudyMode == QuizManager.AudioStudyMode.NORMAL) {
            ttsController.speak(text)
        }
    }

    override fun onDestroy() {
        ttsController.release()
        soundEffectManager.release()
        super.onDestroy()
    }
}
