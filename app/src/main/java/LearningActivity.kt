package com.example.studylockapp

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
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
 * 学習画面のActivity (Mastery Logic & Progress UI 完全復旧版)
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

        binding.buttonRepeatAudio.setOnClickListener {
            viewModel.requestAudioPlayback()
        }

        binding.buttonToggleAudioMode.setOnClickListener {
            val current = viewModel.uiState.value.audioStudyMode
            val next = if (current == QuizManager.AudioStudyMode.NORMAL) 
                QuizManager.AudioStudyMode.AUDIO_RESTRICTED else QuizManager.AudioStudyMode.NORMAL
            viewModel.setAudioStudyMode(next)
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
        // マスター件数とポイント
        binding.textPoints.text = getString(R.string.label_points_value, state.sessionPoints)
        binding.textCurrentGrade.text = getString(R.string.label_mastery_stats, state.basicMasterCount, state.longTermMasterCount)
        
        binding.progressHorizontal.progress = state.progress
        binding.textProgressPercent.text = getString(R.string.label_progress_step, state.currentStep, state.totalSteps)

        // 進捗レールの更新
        binding.masteryProgressRail.setProgress(state.currentLevel, state.isLevelJustIncreased)

        // 自動再生アイコン
        val autoPlayIcon = if (state.isAutoPlayEnabled) R.drawable.ic_volume_up_24 else R.drawable.outline_volume_off_24
        binding.buttonToggleAutoPlay.setImageResource(autoPlayIcon)
        
        // 習得ティアの色設定
        updateTierUi(state.currentTier)

        // 音声制限モードの反映
        val isRestricted = (state.audioStudyMode == QuizManager.AudioStudyMode.AUDIO_RESTRICTED)
        binding.textAudioModeLabel.text = if (isRestricted) getString(R.string.label_audio_mode_restricted) else getString(R.string.label_audio_mode_normal)
        binding.buttonToggleAudioMode.setImageResource(if (isRestricted) R.drawable.outline_volume_off_24 else R.drawable.ic_volume_up_24)
        
        binding.cardQuestion.strokeWidth = if (isRestricted) 4 else 0
        binding.cardQuestion.strokeColor = ContextCompat.getColor(this, R.color.md_outline_variant)

        if (state.audioWarning != null) {
            binding.layoutAudioWarning.visibility = View.VISIBLE
            binding.textAudioWarning.text = state.audioWarning.message
        } else {
            binding.layoutAudioWarning.visibility = View.GONE
        }

        val importance = state.quiz?.mode?.getAudioImportance() ?: QuizMode.AudioImportance.NONE
        binding.buttonRepeatAudio.visibility = if (importance != QuizMode.AudioImportance.NONE && !isRestricted) View.VISIBLE else View.GONE

        state.quiz?.let { quiz ->
            if (currentQuizId != quiz.id) {
                currentQuizId = quiz.id
                binding.textCurrentMode.text = quiz.mode.name
                RendererFactory.getRenderer(quiz.mode).render(this, quiz)
            }
        }
    }

    private fun updateTierUi(tier: MasteryTier) {
        binding.chipMastery.text = tier.label
        val tierColors: Map<MasteryTier, Pair<Int, Int>> = mapOf(
            MasteryTier.LEARNING to Pair(R.color.mastery_bg_learning, R.color.mastery_text_learning),
            MasteryTier.BASIC_MASTER to Pair(R.color.mastery_bg_basic, R.color.mastery_text_basic),
            MasteryTier.LONG_TERM_MASTER to Pair(R.color.mastery_bg_longterm, R.color.mastery_text_longterm)
        )
        val colorPair = tierColors[tier] ?: Pair(R.color.mastery_bg_learning, R.color.mastery_text_learning)
        binding.chipMastery.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this, colorPair.first))
        binding.chipMastery.setTextColor(ContextCompat.getColor(this, colorPair.second))
    }

    private fun handleEvent(event: LearningUiEvent) {
        when (event) {
            is LearningUiEvent.ShowCorrect -> {
                if (viewModel.uiState.value.isAutoPlayEnabled && viewModel.uiState.value.audioStudyMode == QuizManager.AudioStudyMode.NORMAL) {
                    soundEffectManager.playCorrect(1.0f)
                }
                choiceButtons.find { it.text == event.answer }?.let {
                    animationManager.playCorrectSequence(it, event.gainedPoints, event.tierChanged, viewModel.uiState.value.currentTier.label) {
                        viewModel.loadNextQuiz()
                    }
                }
            }
            is LearningUiEvent.ShowWrong -> {
                if (viewModel.uiState.value.isAutoPlayEnabled && viewModel.uiState.value.audioStudyMode == QuizManager.AudioStudyMode.NORMAL) {
                    soundEffectManager.playWrong(1.0f)
                }
                val selectedBtn = choiceButtons.find { it.text == event.selected }
                val correctBtn = choiceButtons.find { it.text == event.correct }
                if (selectedBtn != null && correctBtn != null) {
                    animationManager.showWrong(selectedBtn, correctBtn)
                }
                binding.rootLayout.postDelayed({ viewModel.loadNextQuiz() }, 1500)
            }
            is LearningUiEvent.PlayAudio -> {
                if (viewModel.uiState.value.audioStudyMode == QuizManager.AudioStudyMode.NORMAL) {
                    ttsController.speak(event.text)
                }
            }
            is LearningUiEvent.QuizFinished -> {
                Toast.makeText(this, getString(R.string.message_session_finished), Toast.LENGTH_SHORT).show()
                finish()
            }
            is LearningUiEvent.ShowMasteryBadge -> {
                // 節目演出（基礎/長期マスター到達）
                animationManager.playTierUpAnimation(event.tier.label)
                Toast.makeText(this, getString(R.string.toast_tier_up, event.tier.label), Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun showBasicQuiz(title: String, body: String, choices: List<String>) {
        binding.textQuestionTitle.text = title
        binding.textQuestionBody.text = body
        binding.choicesContainer.visibility = View.VISIBLE
        binding.sortQuestionLayout.root.visibility = View.GONE
        binding.iconTts.visibility = View.GONE

        choiceButtons.forEachIndexed { i, btn ->
            resetChoiceButton(btn)
            if (i < choices.size) {
                btn.text = choices[i]
                btn.visibility = View.VISIBLE
                btn.setOnTouchListener { v, motionEvent ->
                    if (viewModel.uiState.value.isAnswering) return@setOnTouchListener false
                    if (motionEvent.action == MotionEvent.ACTION_DOWN) animationManager.pressDown(v)
                    else if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_CANCEL) animationManager.release(v)
                    false
                }
                btn.setOnClickListener {
                    if (viewModel.uiState.value.isAnswering) return@setOnClickListener
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
        btn.translationX = 0f
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
