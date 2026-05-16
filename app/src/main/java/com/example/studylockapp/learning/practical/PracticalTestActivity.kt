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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPracticalTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        observeViewModel()

        if (savedInstanceState == null) {
            // Intent から grade を取得し、ViewModel に渡す
            val grade = intent.getIntExtra(EXTRA_GRADE, 3)
            viewModel.loadQuestion(grade)
        }
    }

    private fun setupListeners() {
        choiceButtons.forEach { button ->
            button.setOnClickListener {
                viewModel.submitAnswer(button.text.toString())
            }
        }

        binding.buttonFinish.setOnClickListener {
            // 正常終了時は RESULT_OK を設定して閉じる
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
            // 問題がない、読み込み失敗などの場合は RESULT_CANCELED で戻る
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        state.question?.let { q ->
            binding.textQuestionBody.text = q.question
            
            // 選択肢の表示
            state.shuffledChoices.forEachIndexed { index, choice ->
                if (index < choiceButtons.size) {
                    val btn = choiceButtons[index]
                    btn.text = choice
                    btn.visibility = View.VISIBLE
                    // 回答後はActivity側でもボタンを無効化
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
                    text = "不正解"
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
                // 正解の選択肢
                button.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.choice_correct_bg)
                )
                button.strokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.choice_correct_stroke)
                )
                button.setTextColor(ContextCompat.getColor(this, R.color.choice_correct_text))
                button.alpha = 1.0f
            }
            choice == selectedAnswer && choice != correctChoice -> {
                // 自分が選んだ不正解
                button.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.choice_wrong_bg)
                )
                button.strokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.choice_wrong_stroke)
                )
                button.setTextColor(ContextCompat.getColor(this, R.color.choice_wrong_text))
                button.alpha = 1.0f
            }
            else -> {
                // それ以外の選択肢は半透明に
                resetButtonStyle(button)
                button.alpha = 0.5f
            }
        }
    }

    private fun resetButtonStyle(button: MaterialButton) {
        button.alpha = 1.0f
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
        button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.navy_primary))
        button.setTextColor(ContextCompat.getColor(this, R.color.navy_primary))
    }
}
