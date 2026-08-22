package com.stulab.studylockapp.ui

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.stulab.studylockapp.R
import com.stulab.studylockapp.SoundEffectManager
import com.stulab.studylockapp.databinding.ActivitySpellingCheckBinding
import com.stulab.studylockapp.sanitizeForTts
import com.stulab.studylockapp.ui.alert.AppDialogHelper
import kotlinx.coroutines.launch
import java.util.*

class SpellingCheckActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivitySpellingCheckBinding
    private val viewModel: SpellingCheckViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private lateinit var soundEffectManager: SoundEffectManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySpellingCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())

            val topInset = maxOf(systemBars.top, displayCutout.top)
            val bottomInset = maxOf(systemBars.bottom, ime.bottom)

            binding.layoutHeader.updatePadding(top = topInset)
            binding.scrollViewMain.updatePadding(bottom = bottomInset)
            
            insets
        }

        soundEffectManager = SoundEffectManager(this)
        tts = TextToSpeech(this, this)

        val wordIds = intent.getLongArrayExtra("WORD_IDS")
        val grade = intent.getIntExtra("GRADE", -1).takeIf { it != -1 }
        val isChallenge = intent.getBooleanExtra("IS_SESSION", false)
        
        if (isChallenge && (wordIds == null || wordIds.size != 5)) {
            android.util.Log.e("SpellingCheck", "Activity started as challenge but invalid wordIds. Closing.")
            finish()
            return
        }

        viewModel.loadQuestions(wordIds, grade, isChallenge)

        binding.editSpelling.addTextChangedListener {
            viewModel.onUserInputChange(it?.toString() ?: "")
        }

        binding.editSpelling.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (binding.buttonSubmit.isEnabled) {
                    viewModel.submitAnswer()
                }
                true
            } else {
                false
            }
        }

        binding.buttonSubmit.setOnClickListener {
            viewModel.submitAnswer()
        }

        binding.buttonHint.setOnClickListener {
            viewModel.showHint()
        }

        binding.buttonListen.setOnClickListener {
            handlePlayClick()
        }

        binding.buttonClose.setOnClickListener {
            finish()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun showChallengeResult(state: SpellingCheckUiState) {
        AppDialogHelper.showInfo(
            context = this,
            title = getString(R.string.session_summary_title),
            message = getString(R.string.challenge_result_spelling, state.correctCount, state.totalQuestions) + 
                      "\n" + getString(R.string.challenge_remaining_cp, state.challengePoints),
            positiveText = getString(R.string.action_close),
            onPositive = { finish() }
        )
    }

    private fun handlePlayClick() {
        val settings = com.stulab.studylockapp.data.AppSettings(this)
        if (settings.silentMode == com.stulab.studylockapp.data.SilentMode.ON) {
            showSilentModeConfirmation(settings)
        } else {
            speakCurrentWord()
        }
    }

    private fun showSilentModeConfirmation(settings: com.stulab.studylockapp.data.AppSettings) {
        AppDialogHelper.showConfirm(
            context = this,
            title = getString(R.string.spelling_check_silent_mode_title),
            message = getString(R.string.spelling_check_silent_mode_message),
            positiveText = getString(R.string.spelling_check_silent_mode_unlock),
            negativeText = getString(R.string.cancel),
            onPositive = {
                settings.silentMode = com.stulab.studylockapp.data.SilentMode.OFF
                speakCurrentWord()
            }
        )
    }

    private fun render(state: SpellingCheckUiState) {
        if (state.isFinished) {
            if (state.isChallengeSession) {
                showChallengeResult(state)
            } else {
                finish()
            }
            return
        }

        val question = state.currentQuestion ?: return
        
        binding.textProgress.text = getString(R.string.spelling_check_progress, state.currentQuestionIndex + 1, state.totalQuestions)
        binding.textMeaning.text = question.japanese
        binding.textPos.text = PartOfSpeechFormatter.toJapanese(this, question.pos)
        binding.textPos.visibility = if (binding.textPos.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        binding.buttonSubmit.isEnabled = state.userInput.isNotBlank() && state.isCorrect == null && !state.isAnswering
        binding.buttonHint.isEnabled = state.isCorrect == null && !state.isAnswering

        if (state.hintText != null) {
            binding.textHint.visibility = View.VISIBLE
            binding.textHint.text = state.hintText
            binding.textHintWarning.visibility = View.VISIBLE
        } else {
            binding.textHint.visibility = View.GONE
            binding.textHintWarning.visibility = View.GONE
        }

        if (state.isCorrect != null) {
            val correctSpelling = viewModel.getCurrentCorrectSpelling() ?: ""
            showResultDialog(state.isCorrect, correctSpelling, question.japanese, state.hintUsed)
        }
        
        // Sync input field if needed
        if (binding.editSpelling.text.toString() != state.userInput) {
            binding.editSpelling.setText(state.userInput)
        }
    }

    private fun showResultDialog(isCorrect: Boolean, correctSpelling: String, meaning: String, hintUsed: Boolean) {
        if (isCorrect) {
            soundEffectManager.playCorrect()
            val footer = if (hintUsed) {
                getString(R.string.spelling_check_practice_result)
            } else {
                getString(R.string.spelling_check_cleared)
            }
            
            AppDialogHelper.showInfo(
                context = this,
                title = getString(R.string.spelling_check_correct),
                message = "$correctSpelling ($meaning)\n\n$footer",
                positiveText = getString(R.string.spelling_check_next),
                onPositive = {
                    viewModel.nextQuestion()
                }
            )
        } else {
            soundEffectManager.playWrong()
            AppDialogHelper.showConfirm(
                context = this,
                title = getString(R.string.spelling_check_incorrect),
                message = "${getString(R.string.spelling_check_your_answer, viewModel.uiState.value.userInput)}\n${getString(R.string.spelling_check_correct_answer, correctSpelling)}",
                positiveText = getString(R.string.spelling_check_next),
                negativeText = "再入力する",
                onPositive = {
                    viewModel.nextQuestion()
                },
                onNegative = {
                    viewModel.onUserInputChange("")
                }
            )
        }
    }

    private fun speakCurrentWord() {
        val spelling = viewModel.getCurrentCorrectSpelling() ?: return
        val sanitized = sanitizeForTts(spelling)
        if (sanitized.isNotEmpty()) {
            tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, null, "spelling")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.US)
            ttsReady = true
            speakCurrentWord()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        soundEffectManager.release()
        super.onDestroy()
    }
}
