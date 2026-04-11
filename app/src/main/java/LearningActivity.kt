package com.example.studylockapp

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.tts.TextToSpeech
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
import java.util.Locale

/**
 * 学習画面のActivity
 */
class LearningActivity : AppCompatActivity(), TextToSpeech.OnInitListener, QuizUiProvider {

    private lateinit var binding: ActivityLearningBinding
    private lateinit var animationManager: AnimationManager
    private lateinit var soundEffectManager: SoundEffectManager
    private var tts: TextToSpeech? = null
    
    // クイズの切り替わり判定用
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
        tts = TextToSpeech(this, this)

        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.loadNextQuiz()
        }

        // 音声アイコンのクリックイベントを ViewModel に通知
        binding.iconTts.setOnClickListener {
            viewModel.requestAudioPlayback()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // UI状態の監視
                launch {
                    viewModel.uiState.collectLatest { state ->
                        updateUi(state)
                    }
                }
                // 一回性演出イベントの監視 (Channel由来のFlow)
                launch {
                    viewModel.uiEvent.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUi(state: LearningUiState) {
        // ポイント・コンボの数値表示
        binding.textPoints.text = getString(R.string.label_points_value, state.sessionPoints)
        binding.textCurrentGrade.text = getString(R.string.label_combo_value, state.comboCount)
        
        // 進捗表示 (プログレスバーと "x / 10" のテキスト)
        binding.progressHorizontal.progress = state.progress
        binding.textProgressPercent.text = getString(R.string.label_progress_step, state.currentStep, state.totalSteps)

        // クイズコンテンツの描画 (IDが変更された場合のみ Renderer を実行)
        state.quiz?.let { quiz ->
            if (currentQuizId != quiz.id) {
                currentQuizId = quiz.id
                binding.textCurrentMode.text = getString(R.string.label_current_mode, quiz.mode.toString())
                RendererFactory.getRenderer(quiz.mode).render(this, quiz)
            }
        }
    }

    private fun handleEvent(event: LearningUiEvent) {
        when (event) {
            is LearningUiEvent.ShowCorrect -> {
                soundEffectManager.playCorrect(1.0f)
                choiceButtons.find { it.text == event.answer }?.let {
                    animationManager.playCorrectSequence(it, event.gainedPoints) {
                        viewModel.loadNextQuiz()
                    }
                }
            }
            is LearningUiEvent.ShowWrong -> {
                soundEffectManager.playWrong(1.0f)
                val selectedBtn = choiceButtons.find { it.text == event.selected }
                val correctBtn = choiceButtons.find { it.text == event.correct }
                
                if (selectedBtn != null && correctBtn != null) {
                    animationManager.showWrong(selectedBtn, correctBtn)
                }
                
                binding.rootLayout.postDelayed({
                    viewModel.loadNextQuiz()
                }, 1500)
            }
            is LearningUiEvent.PlayAudio -> {
                playAudio(event.text)
            }
            is LearningUiEvent.QuizFinished -> {
                Toast.makeText(this, R.string.message_session_finished, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun showBasicQuiz(title: String, body: String, choices: List<String>) {
        binding.textQuestionTitle.text = title
        binding.textQuestionBody.text = body

        binding.choicesContainer.visibility = View.VISIBLE
        binding.sortQuestionLayout.root.visibility = View.GONE
        
        // リスニングモードなら音声アイコンを表示
        val isAudioMode = viewModel.uiState.value.quiz?.mode == QuizMode.LISTEN_EN
        binding.iconTts.visibility = if (isAudioMode) View.VISIBLE else View.GONE

        choiceButtons.forEachIndexed { i, btn ->
            resetChoiceButton(btn)

            if (i < choices.size) {
                btn.text = choices[i]
                btn.visibility = View.VISIBLE

                btn.setOnTouchListener { v, event ->
                    if (viewModel.uiState.value.isAnswering) return@setOnTouchListener false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> animationManager.pressDown(v)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animationManager.release(v)
                    }
                    false
                }

                btn.setOnClickListener {
                    if (viewModel.uiState.value.isAnswering) return@setOnClickListener
                    viewModel.submitAnswer(btn.text.toString())
                }
            } else {
                btn.visibility = View.GONE
                btn.setOnTouchListener(null)
                btn.setOnClickListener(null)
            }
        }
    }

    private fun resetChoiceButton(btn: MaterialButton) {
        btn.scaleX = 1f
        btn.scaleY = 1f
        btn.translationX = 0f
        btn.alpha = 1f
        btn.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_button_bg))
        btn.setTextColor(ContextCompat.getColor(this, R.color.choice_button_text))
    }

    override fun playAudio(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        soundEffectManager.release()
        super.onDestroy()
    }
}
