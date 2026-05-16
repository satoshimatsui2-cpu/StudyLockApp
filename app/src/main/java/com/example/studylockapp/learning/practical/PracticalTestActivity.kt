package com.example.studylockapp.learning.practical

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
import com.example.studylockapp.GradeLabelFormatter
import com.example.studylockapp.R
import com.example.studylockapp.databinding.ActivityPracticalTestBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 実践テスト（MVP: 穴埋め問題）を表示するActivity。
 */
class PracticalTestActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GRADE = "extra_grade"
    }

    private lateinit var binding: ActivityPracticalTestBinding
    private val viewModel: PracticalTestViewModel by viewModels {
        PracticalTestViewModelFactory(this)
    }

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
            // 下部のボタンエリアを考慮しつつPaddingを設定
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        observeViewModel()

        if (savedInstanceState == null) {
            val grade = intent.getIntExtra(EXTRA_GRADE, 3)
            viewModel.loadQuestion(grade)
            // ヘッダーの級表示をフォーマッターを使用して設定
            binding.textGradeLabel.text = GradeLabelFormatter.format(grade)
        }
    }

    private fun setupListeners() {
        choiceButtons.forEach { button ->
            button.setOnClickListener {
                // 表示テキスト（ラベル付き）ではなく、Tagに保存した元の値を使用する
                val rawText = button.tag as? String ?: return@setOnClickListener
                viewModel.submitAnswer(rawText)
            }
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
        if (state.error) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        state.question?.let { q ->
            binding.textQuestionBody.text = q.question.replace("\\n", "\n")
            
            state.shuffledChoices.forEachIndexed { index, choice ->
                if (index < choiceButtons.size) {
                    val btn = choiceButtons[index]
                    // 表示はラベル付き
                    btn.text = "${choiceLabels[index]}$choice"
                    // 回答判定用に元の値をTagに保持
                    btn.tag = choice
                    
                    btn.visibility = View.VISIBLE
                    btn.isEnabled = !state.isAnswered
                    
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
                if (state.isCorrect) {
                    text = "正解！ (+${state.pointsGained}pt)"
                    setTextColor(ContextCompat.getColor(context, R.color.choice_correct_text))
                } else {
                    text = "不正解..."
                    setTextColor(ContextCompat.getColor(context, R.color.choice_wrong_text))
                }
            }
            binding.textExplanationBody.text = state.question?.explanation
        } else {
            binding.cardExplanation.visibility = View.GONE
            binding.buttonFinish.visibility = View.GONE
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
                // 正解の選択肢：緑背景 + 太い枠線
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
                // 自分が選んだ不正解：赤背景
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
                // それ以外の選択肢は薄く表示
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
