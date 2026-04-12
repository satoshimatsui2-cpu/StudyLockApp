package com.example.studylockapp

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
 * 学習画面のActivity (UX刷新 & Safety Fallback版)
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
            val current = viewModel.uiState.value.audioStudyMode
            val next = if (current == QuizManager.AudioStudyMode.NORMAL) 
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
            val sentence = viewModel.uiState.value.currentWord?.sentence
            if (!sentence.isNullOrBlank()) {
                viewModel.requestAudioPlayback(sentence)
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
            binding.textWordGradeLabel.text = "${gradeLabel} を学習中"
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

        // 3. レビューカードの表示制御
        if (state.isReviewing && state.currentWord != null) {
            binding.cardAnswerReview.visibility = View.VISIBLE
            binding.textReviewWord.text = state.currentWord.word
            binding.textReviewPhonetic.text = state.currentWord.phonetic ?: ""
            binding.textReviewSentence.text = state.currentWord.sentence ?: ""
            binding.textReviewSentenceJp.text = state.currentWord.japaneseSentence ?: ""
            
            // 答え合わせ表示
            binding.textReviewAnswerCorrect.text = getString(R.string.review_label_correct_word, state.correctAnswerText)
            if (!state.isLastAnswerCorrect) {
                binding.textReviewAnswerWrong.visibility = View.VISIBLE
                binding.textReviewAnswerWrong.text = getString(R.string.review_label_wrong_answer, state.lastUserAnswer)
            } else {
                binding.textReviewAnswerWrong.visibility = View.GONE
            }
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
                if (correctBtn != null) {
                    animationManager.playCorrectSequence(correctBtn, event.gainedPoints, event.tierChanged, viewModel.uiState.value.currentTier.label) {
                        viewModel.startReview()
                    }
                } else {
                    // フォールバック: ボタンが見つからない場合もレビューへ進む
                    binding.rootLayout.postDelayed({ viewModel.startReview() }, AnimationManager.DURATION_CORRECT)
                }
            }
            is LearningUiEvent.ShowWrong -> {
                if (viewModel.uiState.value.isAutoPlayEnabled && viewModel.uiState.value.audioStudyMode == QuizManager.AudioStudyMode.NORMAL) {
                    soundEffectManager.playWrong(1.0f)
                }
                val selectedBtn = choiceButtons.find { it.text == event.selected }
                val correctBtn = choiceButtons.find { it.text == event.correct }
                
                // nullable対応された AnimationManager を呼び出す
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

    @SuppressLint("ClickableViewAccessibility")
    override fun showBasicQuiz(title: String, body: String, choices: List<String>) {
        binding.textQuestionTitle.text = title
        binding.textQuestionBody.text = body
        binding.choicesContainer.visibility = View.VISIBLE

        choiceButtons.forEachIndexed { i, btn ->
            resetChoiceButton(btn)
            if (i < choices.size) {
                btn.text = choices[i]
                btn.visibility = View.VISIBLE
                btn.setOnClickListener {
                    if (viewModel.uiState.value.isAnswering || viewModel.uiState.value.isReviewing) return@setOnClickListener
                    viewModel.submitAnswer(btn.text.toString())
                }
            } else {
                btn.visibility = View.GONE
            }
        }
    }

    private fun resetChoiceButton(btn: MaterialButton) {
        btn.scaleX = 1f
        btn.scaleY = 1f
        btn.alpha = 1f
        btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_button_bg))
        btn.setTextColor(ContextCompat.getColor(this, R.color.choice_button_text))
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
