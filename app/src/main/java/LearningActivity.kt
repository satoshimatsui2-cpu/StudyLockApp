package com.example.studylockapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
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
            ReviewCardBinder.bind(binding.layoutReviewCard, ReviewCardMapper.map(state))
            
            // 並び替えモードでは単語再生ボタンを非表示
            val isSortMode = state.quiz?.mode == QuizMode.SENTENCE_SORT
            binding.layoutReviewCard.buttonPlayReviewWord.visibility = if (isSortMode) View.GONE else View.VISIBLE
        } else {
            binding.layoutReviewCard.rootReviewCard.visibility = View.GONE
            binding.cardQuestion.visibility = View.VISIBLE
        }

        binding.progressHorizontal.progress = state.progress
        binding.textProgressPercent.text = getString(R.string.label_progress_step, state.currentStep, state.totalSteps)

        renderQuizIfNeeded(state)
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
        binding.choicesContainer.visibility = View.VISIBLE
        binding.layoutSortContainer.visibility = View.GONE
        binding.flexboxAnswer.removeAllViews()
        binding.flexboxCandidates.removeAllViews()
        setQuestionBodyTextScale(1.0f)
    }

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
                // 並び替えモードの場合：遅延させて英文読み上げ。単語ボタンなし。
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
                if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
                    soundEffectManager.playWrong(1.0f)
                }
                if (viewModel.uiState.value.quiz?.mode == QuizMode.SENTENCE_SORT) {
                    playSentenceAudioDelayed()
                    viewModel.startReview()
                } else {
                    val selectedBtn = choiceButtons.find { it.text == event.selected }
                    val correctBtn = choiceButtons.find { it.text == event.correct }
                    animationManager.playWrongSequence(selectedBtn, correctBtn) {
                        viewModel.startReview()
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

    private fun resetChoiceButton(btn: MaterialButton) {
        btn.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.choice_button_background_default))
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.choice_button_text_default))
            strokeColor = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.choice_button_stroke_default))
            strokeWidth = resources.getDimensionPixelSize(R.dimen.choice_button_stroke_width_default)
            alpha = 1.0f; scaleX = 1.0f; scaleY = 1.0f; translationX = 0f; translationY = 0f
            isEnabled = true; isClickable = true; clearAnimation()
        }
    }

    private fun resetAllChoiceButtons() { choiceButtons.forEach { resetChoiceButton(it) } }

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
