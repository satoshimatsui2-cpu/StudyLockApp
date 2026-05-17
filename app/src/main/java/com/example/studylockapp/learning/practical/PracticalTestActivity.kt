package com.example.studylockapp.learning.practical

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.studylockapp.GradeLabelFormatter
import com.example.studylockapp.R
import com.example.studylockapp.data.practical.PracticalQuizMode
import com.example.studylockapp.databinding.ActivityPracticalTestBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 実践テスト（穴埋め ＆ リスニング問題）を表示するActivity。
 */
class PracticalTestActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GRADE = "extra_grade"
    }

    private lateinit var binding: ActivityPracticalTestBinding
    private val viewModel: PracticalTestViewModel by viewModels {
        PracticalTestViewModelFactory(this)
    }

    private var ttsController: PracticalListeningTtsController? = null

    private val choiceButtons by lazy {
        listOf(
            binding.buttonChoice1,
            binding.buttonChoice2,
            binding.buttonChoice3,
            binding.buttonChoice4
        )
    }

    // A. B. C. D. ラベル用のリスト
    private val choiceLabels = listOf("A. ", "B. ", "C. ", "D. ")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPracticalTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        // TTSの初期化
        ttsController = PracticalListeningTtsController(this).apply {
            onSegmentStart = { id -> viewModel.onListeningSegmentChanged(id) }
            onComplete = { viewModel.onListeningPlayCompleted() }
            onError = { viewModel.onListeningPlayCompleted() }
        }

        setupListeners()
        observeViewModel()

        if (savedInstanceState == null) {
            val grade = intent.getIntExtra(EXTRA_GRADE, 3)
            viewModel.loadQuestion(grade)
            binding.textGradeLabel.text = GradeLabelFormatter.format(grade)
        }
    }

    override fun onDestroy() {
        ttsController?.shutdown()
        super.onDestroy()
    }

    private fun setupListeners() {
        choiceButtons.forEach { button ->
            button.setOnClickListener {
                val rawText = button.tag as? String ?: return@setOnClickListener
                viewModel.submitAnswer(rawText)
            }
        }

        // リスニング初回再生
        binding.buttonPlayFirst.setOnClickListener {
            val script = viewModel.uiState.value.listeningScript ?: return@setOnClickListener
            val grade = viewModel.uiState.value.question?.grade ?: 3
            viewModel.onListeningPlayStarted(isReplay = false)
            ttsController?.play(script, grade)
        }

        // もう一度聞く
        binding.buttonReplayQuestion.setOnClickListener {
            val script = viewModel.uiState.value.listeningScript ?: return@setOnClickListener
            val grade = viewModel.uiState.value.question?.grade ?: 3
            viewModel.onListeningPlayStarted(isReplay = true)
            ttsController?.play(script, grade)
        }

        // 回答後の復習再生
        binding.buttonPlayScript.setOnClickListener {
            val script = viewModel.uiState.value.listeningScript ?: return@setOnClickListener
            val grade = viewModel.uiState.value.question?.grade ?: 3
            ttsController?.play(script, grade)
        }

        binding.buttonFinish.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: PracticalUiState) {
        // 調査用ログ
        Log.d(
            "PracticalTestActivity",
            "updateUi isListening=${state.isListeningQuestion}, type=${state.question?.type}, playback=${state.listeningPlaybackState}, answered=${state.isAnswered}"
        )

        if (state.error) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        // 1. 表示タイプの判定 (state.isListeningQuestionを優先)
        binding.textQuestionType.text = when {
            state.isListeningQuestion -> "リスニング問題"
            state.question?.type == PracticalQuizMode.REARRANGE -> "並べ替え問題"
            else -> "穴埋め問題"
        }

        state.question?.let { q ->
            // 2. リスニング未回答時のテキスト非表示制御
            if (state.isListeningQuestion && !state.isAnswered) {
                binding.textQuestionBody.visibility = View.GONE
                binding.textQuestionLabel.visibility = View.GONE
            } else {
                binding.textQuestionBody.visibility = View.VISIBLE
                binding.textQuestionLabel.visibility = View.VISIBLE
                binding.textQuestionBody.text = q.question.replace("\\n", "\n")
            }

            // 3. リスニングUIの分岐
            if (state.isListeningQuestion) {
                Log.d("PracticalTestActivity", "render listening UI")
                updateListeningUi(state)
            } else {
                Log.d("PracticalTestActivity", "render fill/rearrange UI")
                binding.layoutListeningInitial.visibility = View.GONE
                binding.layoutListeningAnswering.visibility = View.GONE
                binding.textReplayPenaltyNote.visibility = View.GONE
            }

            // 選択肢の制御
            state.shuffledChoices.forEachIndexed { index, choice ->
                if (index < choiceButtons.size) {
                    val btn = choiceButtons[index]
                    btn.text = "${choiceLabels[index]}$choice"
                    btn.tag = choice
                    btn.visibility = View.VISIBLE

                    // リスニング再生中、または回答前かつ再生未開始の場合は無効化
                    val isListeningLocked = state.isListeningQuestion && (
                        state.listeningPlaybackState == ListeningPlaybackState.WAITING_TO_START ||
                        state.listeningPlaybackState == ListeningPlaybackState.PLAYING_FIRST ||
                        state.listeningPlaybackState == ListeningPlaybackState.PLAYING_AGAIN
                    )
                    
                    btn.isEnabled = !state.isAnswered && !isListeningLocked
                    
                    if (state.isAnswered) {
                        updateButtonStyle(btn, choice, state.correctChoice, state.selectedAnswer)
                    } else {
                        resetButtonStyle(btn)
                    }
                }
            }
        }

        if (state.isAnswered) {
            binding.cardExplanation.visibility = View.VISIBLE
            binding.buttonFinish.visibility = View.VISIBLE
            
            binding.textResultHeader.apply {
                when {
                    !state.isScored -> {
                        text = "ポイント対象外です。解説を確認しましょう。"
                        setTextColor(ContextCompat.getColor(context, R.color.text_sub))
                    }
                    state.isCorrect -> {
                        text = "正解！ (+${state.pointsGained}pt)"
                        setTextColor(ContextCompat.getColor(context, R.color.choice_correct_text))
                    }
                    else -> {
                        text = "不正解..."
                        setTextColor(ContextCompat.getColor(context, R.color.choice_wrong_text))
                    }
                }
            }
            binding.textExplanationBody.text = state.question?.explanation

            // 回答後はスクリプト表示
            if (state.isListeningQuestion) {
                binding.layoutListeningScript.visibility = View.VISIBLE
                renderScriptLines(state)
            } else {
                binding.layoutListeningScript.visibility = View.GONE
            }
        } else {
            binding.cardExplanation.visibility = View.GONE
            binding.buttonFinish.visibility = View.GONE
            binding.layoutListeningScript.visibility = View.GONE
        }
    }

    private fun updateListeningUi(state: PracticalUiState) {
        when (state.listeningPlaybackState) {
            ListeningPlaybackState.WAITING_TO_START -> {
                binding.layoutListeningInitial.visibility = View.VISIBLE
                binding.layoutListeningAnswering.visibility = View.GONE
                binding.textReplayPenaltyNote.visibility = View.GONE
                binding.buttonPlayFirst.visibility = View.VISIBLE
                binding.buttonPlayFirst.isEnabled = true
                binding.buttonPlayFirst.text = "問題を再生する"

                val repeatNotice = if ((state.question?.grade ?: 3) <= 3) {
                    "※この問題は2回再生されます"
                } else {
                    "※この問題は1回だけ再生されます"
                }
                binding.textRepeatNotice.text = repeatNotice
            }
            ListeningPlaybackState.PLAYING_FIRST,
            ListeningPlaybackState.PLAYING_AGAIN -> {
                binding.layoutListeningInitial.visibility = View.VISIBLE
                binding.layoutListeningAnswering.visibility = View.GONE
                binding.textReplayPenaltyNote.visibility = View.GONE
                binding.buttonPlayFirst.isEnabled = false
                binding.buttonPlayFirst.text = "再生中..."
            }
            ListeningPlaybackState.ANSWERING -> {
                binding.layoutListeningInitial.visibility = View.GONE
                binding.layoutListeningAnswering.visibility = View.VISIBLE
                binding.textReplayPenaltyNote.visibility = View.VISIBLE
                
                binding.buttonReplayQuestion.isEnabled = true
                if (state.hasUsedReplay) {
                    binding.buttonReplayQuestion.text = "もう一度聞く (ポイント対象外)"
                    binding.buttonReplayQuestion.alpha = 0.5f
                } else {
                    binding.buttonReplayQuestion.text = "もう一度聞く"
                    binding.buttonReplayQuestion.alpha = 1.0f
                }
            }
            ListeningPlaybackState.FINISHED -> {
                binding.layoutListeningInitial.visibility = View.GONE
                binding.layoutListeningAnswering.visibility = View.GONE
                binding.textReplayPenaltyNote.visibility = View.GONE
            }
            else -> {}
        }
    }

    private fun renderScriptLines(state: PracticalUiState) {
        val container = binding.containerScriptLines
        val script = state.listeningScript ?: return
        
        if (state.listeningDisplaySegments.isEmpty()) {
            val segments = ttsController?.parseScript(script) ?: emptyList()
            viewModel.setListeningDisplaySegments(segments)
            return
        }

        container.removeAllViews()
        state.listeningDisplaySegments.forEach { segment ->
            if (segment.displayText.isBlank()) return@forEach
            if (container.findViewWithTag<View>(segment.id) != null) return@forEach

            val textView = TextView(this).apply {
                tag = segment.id
                text = segment.displayText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ContextCompat.getColor(context, R.color.text_main))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                
                if (state.currentPlayingSegmentId == segment.id) {
                    setBackgroundResource(R.drawable.bg_badge_navy_soft)
                    setTypeface(null, Typeface.BOLD)
                } else {
                    setBackgroundColor(Color.TRANSPARENT)
                    setTypeface(null, Typeface.NORMAL)
                }
            }
            container.addView(textView)
        }
    }

    private fun updateButtonStyle(
        button: MaterialButton,
        choice: String,
        correctChoice: String,
        selectedAnswer: String
    ) {
        when {
            choice == correctChoice -> {
                button.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.choice_correct_bg)
                )
                button.strokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.choice_correct_stroke)
                )
                button.strokeWidth = dp(2)
                button.setTextColor(ContextCompat.getColor(this, R.color.choice_correct_text))
                button.alpha = 1.0f
            }
            choice == selectedAnswer && choice != correctChoice -> {
                button.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.choice_wrong_bg)
                )
                button.strokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.choice_wrong_stroke)
                )
                button.strokeWidth = dp(1)
                button.setTextColor(ContextCompat.getColor(this, R.color.choice_wrong_text))
                button.alpha = 1.0f
            }
            else -> {
                resetButtonStyle(button)
                button.alpha = 0.4f
            }
        }
    }

    private fun resetButtonStyle(button: MaterialButton) {
        button.alpha = 1.0f
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
        button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.app_outline))
        button.strokeWidth = dp(1)
        button.setTextColor(ContextCompat.getColor(this, R.color.navy_primary))
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
