package com.stulab.studylockapp.ui

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.stulab.studylockapp.R
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

    private lateinit var soundPool: SoundPool
    private var soundSuccess: Int = 0
    private var soundFailure: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpellingCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSoundPool()
        tts = TextToSpeech(this, this)

        val wordIds = intent.getLongArrayExtra("WORD_IDS")
        viewModel.loadQuestions(wordIds)

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
            speakCurrentWord()
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

    private fun render(state: SpellingCheckUiState) {
        if (state.isFinished) {
            finish()
            return
        }

        val word = state.currentWord ?: return
        
        binding.imageCharacterMini.setImageResource(
            CharacterDisplayUtils.getMiniIconDrawable(this, state.selectedCharacterId, "joy")
        )

        binding.textProgress.text = getString(R.string.spelling_check_progress, state.currentQuestionIndex + 1, state.totalQuestions)
        binding.textMeaning.text = word.japanese
        binding.textPos.text = word.pos

        binding.buttonSubmit.isEnabled = state.userInput.isNotBlank() && state.isCorrect == null

        if (state.hintText != null) {
            binding.textHint.visibility = View.VISIBLE
            binding.textHint.text = state.hintText
        } else {
            binding.textHint.visibility = View.GONE
        }

        if (state.isCorrect != null) {
            showResultDialog(state.isCorrect, word.word, word.japanese)
        }
        
        // 入力欄の同期（初回表示時や次へ進んだ時など）
        if (binding.editSpelling.text.toString() != state.userInput) {
            binding.editSpelling.setText(state.userInput)
        }
    }

    private fun showResultDialog(isCorrect: Boolean, correctSpelling: String, meaning: String) {
        playSound(isCorrect)
        if (isCorrect) {
            AppDialogHelper.showInfo(
                context = this,
                title = getString(R.string.spelling_check_correct),
                message = "$correctSpelling ($meaning)\n\n${getString(R.string.spelling_check_cleared)}",
                positiveText = getString(R.string.spelling_check_next),
                onPositive = {
                    viewModel.nextQuestion()
                }
            )
        } else {
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
                    // 再入力させるために状態をリセット
                    viewModel.onUserInputChange("")
                }
            )
        }
    }

    private fun setupSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
        
        soundSuccess = soundPool.load(this, R.raw.se_correct, 1)
        soundFailure = soundPool.load(this, R.raw.se_wrong, 1)
    }

    private fun playSound(isSuccess: Boolean) {
        val soundId = if (isSuccess) soundSuccess else soundFailure
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
        }
    }

    private fun speakCurrentWord() {
        val word = viewModel.uiState.value.currentWord?.word ?: return
        val sanitized = sanitizeForTts(word)
        if (sanitized.isNotEmpty()) {
            tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, null, "spelling")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.US)
            ttsReady = true
            // 初回発声
            speakCurrentWord()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        soundPool.release()
        super.onDestroy()
    }
}
