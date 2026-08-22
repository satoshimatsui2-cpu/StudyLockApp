package com.stulab.studylockapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.stulab.studylockapp.R
import com.stulab.studylockapp.SoundEffectManager
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.ChallengePointManager
import com.stulab.studylockapp.data.SilentMode
import com.stulab.studylockapp.data.WordEntity
import com.stulab.studylockapp.databinding.ActivityPronunciationChallengeBinding
import com.stulab.studylockapp.sanitizeForTts
import com.stulab.studylockapp.ui.alert.AppDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class PronunciationChallengeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_MODE = "EXTRA_MODE"
        const val MODE_SESSION = "SESSION"
        const val MODE_SINGLE = "SINGLE"
        const val EXTRA_WORD_ID = "WORD_ID"
        const val EXTRA_WORD_IDS = "WORD_IDS"
    }

    private lateinit var binding: ActivityPronunciationChallengeBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var soundEffectManager: SoundEffectManager
    private lateinit var appSettings: AppSettings
    private lateinit var cpManager: ChallengePointManager

    private var words: List<WordEntity> = emptyList()
    private var currentIndex: Int = 0
    private var currentMode = MODE_SESSION

    enum class ChallengeStatus {
        NOT_STARTED, RECORDING, RECOGNIZING, OK, RETRYABLE
    }

    enum class JobState {
        IDLE, STARTING, PLAYING, RECORDING, RECOGNIZING
    }

    private var currentJobState = JobState.IDLE
    private var wordStatus = ChallengeStatus.NOT_STARTED
    private var sentenceStatus = ChallengeStatus.NOT_STARTED
    private var currentRecognizingType: String = "word" // "word" or "sentence"

    private var activeAttemptId: Int = 0
    private var isAttemptFinished = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition()
        } else {
            Toast.makeText(this, "録音権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPronunciationChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appSettings = AppSettings(this)
        cpManager = ChallengePointManager(this)
        soundEffectManager = SoundEffectManager(this)
        tts = TextToSpeech(this, this)

        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SESSION
        
        val intentIds = intent.getLongArrayExtra(EXTRA_WORD_IDS)?.toList()
        val singleId = intent.getLongExtra(EXTRA_WORD_ID, -1L).takeIf { it != -1L }
        
        val wordIds = when {
            currentMode == MODE_SINGLE && singleId != null -> listOf(singleId)
            intentIds != null -> intentIds
            else -> appSettings.activePronunciationChallengeIds
        }

        if (wordIds == null || (currentMode == MODE_SESSION && wordIds.size != 3) || (currentMode == MODE_SINGLE && wordIds.isEmpty())) {
            Log.e("PronChallenge", "Invalid session on Activity start. Closing. mode=$currentMode ids=$wordIds")
            finish()
            return
        }

        currentIndex = if (currentMode == MODE_SESSION) appSettings.pronChallengeIndex else 0

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@PronunciationChallengeActivity)
            words = withContext(Dispatchers.IO) { db.wordDao().getWordsByIds(wordIds.map { it.toInt() }) }
            
            if (words.isEmpty() || (currentMode == MODE_SESSION && words.size != 3)) {
                finish()
                return@launch
            }

            setupUI()
            loadCurrentWord()
        }
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener { finish() }
        binding.btnFinish.setOnClickListener { 
            if (currentMode == MODE_SESSION) appSettings.clearPronunciationChallenge()
            finish() 
        }

        if (currentMode == MODE_SINGLE) {
            binding.textProgress.visibility = View.GONE
            binding.layoutSteps.visibility = View.GONE
            binding.btnNextWord.visibility = View.GONE
            binding.textTitle.text = "発音チェック"
        }

        binding.btnNextWord.setOnClickListener {
            if (currentIndex < words.size - 1) {
                currentIndex++
                if (currentMode == MODE_SESSION) appSettings.pronChallengeIndex = currentIndex
                loadCurrentWord()
            } else {
                showResult()
            }
        }

        val card = binding.currentWordCard
        card.btnPronounceWord.setOnClickListener { handlePronounceClick("word") }
        card.btnListenWord.setOnClickListener { handleListenClick(words[currentIndex].word, "word") }

        card.btnPronounceSentence.setOnClickListener { handlePronounceClick("sentence") }
        card.btnListenSentence.setOnClickListener { handleListenClick(words[currentIndex].sentence, "sentence") }
    }

    private fun loadCurrentWord() {
        val word = words[currentIndex]
        if (currentMode == MODE_SESSION) {
            binding.textProgress.text = "${currentIndex + 1} / ${words.size}"
        }
        
        binding.currentWordCard.textWord.text = word.word
        binding.currentWordCard.textMeaning.text = word.japanese
        binding.currentWordCard.textSentence.text = word.sentence
        binding.currentWordCard.textSentence.visibility = if (word.sentence.isNotBlank()) View.VISIBLE else View.GONE
        
        binding.currentWordCard.textWordRecognitionResult.visibility = View.GONE
        binding.currentWordCard.textSentenceRecognitionResult.visibility = View.GONE

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@PronunciationChallengeActivity)
            val wordOk = withContext(Dispatchers.IO) { db.voiceCheckDao().getResult(word.no.toLong(), "word")?.checked ?: false }
            val sentOk = withContext(Dispatchers.IO) { db.voiceCheckDao().getResult(word.no.toLong(), "sentence")?.checked ?: false }

            wordStatus = if (wordOk) ChallengeStatus.OK else ChallengeStatus.NOT_STARTED
            sentenceStatus = if (sentOk) ChallengeStatus.OK else ChallengeStatus.NOT_STARTED

            updateButtonStates()
        }
    }

    private fun updateButtonStates() {
        val word = words[currentIndex]
        val card = binding.currentWordCard

        // Steps Indicator (only for SESSION mode)
        if (currentMode == MODE_SESSION) {
            binding.layoutSteps.removeAllViews()
            for (i in words.indices) {
                val view = View(this).apply {
                    val size = 12.dpToPx()
                    val params = android.widget.LinearLayout.LayoutParams(size, size)
                    params.marginEnd = 8.dpToPx()
                    layoutParams = params
                    val color = if (i < currentIndex) {
                        ContextCompat.getColor(this@PronunciationChallengeActivity, R.color.navy_primary)
                    } else if (i == currentIndex) {
                        ContextCompat.getColor(this@PronunciationChallengeActivity, R.color.mustard_primary)
                    } else {
                        Color.LTGRAY
                    }
                    background = ContextCompat.getDrawable(this@PronunciationChallengeActivity, R.drawable.bg_circle_indicator)
                    background?.setTint(color)
                }
                binding.layoutSteps.addView(view)
            }
        }

        val isBusy = currentJobState != JobState.IDLE

        // Word Section
        updateButton(card.btnPronounceWord, wordStatus, "単語")
        card.btnListenWord.isEnabled = !isBusy

        // Sentence Section
        val hasSentence = word.sentence.isNotBlank()
        card.labelSentence.visibility = if (hasSentence) View.VISIBLE else View.GONE
        card.btnListenSentence.visibility = if (hasSentence) View.VISIBLE else View.GONE
        card.btnPronounceSentence.visibility = if (hasSentence) View.VISIBLE else View.GONE
        card.layoutSentenceHeader.visibility = if (hasSentence) View.VISIBLE else View.GONE
        card.textSentence.visibility = if (hasSentence && wordStatus == ChallengeStatus.OK) View.VISIBLE else View.GONE
        
        updateButton(card.btnPronounceSentence, sentenceStatus, "例文")
        card.btnListenSentence.isEnabled = !isBusy && (wordStatus == ChallengeStatus.OK)

        card.textLockHint.visibility = if (hasSentence && wordStatus != ChallengeStatus.OK) View.VISIBLE else View.GONE
        
        // Next Button
        binding.btnNextWord.isEnabled = (wordStatus == ChallengeStatus.OK) && !isBusy
    }

    private fun updateButton(button: com.google.android.material.button.MaterialButton, status: ChallengeStatus, typeLabel: String) {
        val white = Color.WHITE
        val black = Color.parseColor("#1B1B1B")
        val navy = ContextCompat.getColor(this, R.color.navy_primary)
        val recordingRed = Color.parseColor("#B42318")
        val recognizingYellow = Color.parseColor("#D4A72C")
        val disabledBg = Color.parseColor("#F1F3F6")
        val disabledText = Color.parseColor("#475467")
        val disabledStroke = Color.parseColor("#98A2B3")
        
        val okBg = ContextCompat.getColor(this, R.color.choice_correct_bg)
        val okText = ContextCompat.getColor(this, R.color.choice_correct_text)
        val okStroke = ContextCompat.getColor(this, R.color.choice_correct_stroke)
        val retryBg = ContextCompat.getColor(this, R.color.choice_wrong_bg)
        val retryText = ContextCompat.getColor(this, R.color.choice_wrong_text)
        val retryStroke = ContextCompat.getColor(this, R.color.choice_wrong_stroke)

        // 1. Determine Logic State
        val isLocked = (button.id == R.id.btn_pronounce_sentence && wordStatus != ChallengeStatus.OK)
        val isBusy = currentJobState != JobState.IDLE
        val isSelfRecording = (status == ChallengeStatus.RECORDING)
        val isSelfRecognizing = (status == ChallengeStatus.RECOGNIZING)

        // 2. Determine Colors and Content
        val finalBg: Int
        val finalText: Int
        val finalIcon: Int
        val finalStroke: Int
        val finalStrokeWidth: Int
        val finalEnabled: Boolean
        val label: String
        val iconRes: Int

        when {
            isLocked -> {
                label = "例文はロックされています"
                iconRes = R.drawable.ic_lock_24
                finalBg = disabledBg
                finalText = disabledText
                finalIcon = disabledText
                finalStroke = disabledStroke
                finalStrokeWidth = 1.dpToPx()
                finalEnabled = false
            }
            isBusy && !isSelfRecording && !isSelfRecognizing -> {
                // "Busy" state (another action in progress)
                label = when (status) {
                    ChallengeStatus.OK -> "OK"
                    ChallengeStatus.RETRYABLE -> "再挑戦"
                    else -> "${typeLabel}を発音する"
                }
                iconRes = if (status == ChallengeStatus.OK) R.drawable.ic_check_circle_24 else R.drawable.ic_mic_24
                finalBg = disabledBg
                finalText = disabledText
                finalIcon = disabledText
                finalStroke = disabledStroke
                finalStrokeWidth = 1.dpToPx()
                finalEnabled = false
            }
            else -> {
                // Active or Self action
                label = when (status) {
                    ChallengeStatus.NOT_STARTED -> "${typeLabel}を発音する"
                    ChallengeStatus.RECORDING -> {
                        if (currentJobState == JobState.STARTING) "録音を開始しています..."
                        else "録音中..."
                    }
                    ChallengeStatus.RECOGNIZING -> "判定中..."
                    ChallengeStatus.OK -> "OK"
                    ChallengeStatus.RETRYABLE -> "再挑戦"
                    else -> ""
                }
                iconRes = when (status) {
                    ChallengeStatus.OK -> R.drawable.ic_check_circle_24
                    ChallengeStatus.RECOGNIZING -> R.drawable.ic_refresh_24
                    else -> R.drawable.ic_mic_24
                }
                finalEnabled = (status != ChallengeStatus.RECOGNIZING)

                when (status) {
                    ChallengeStatus.NOT_STARTED -> {
                        finalBg = navy
                        finalText = white
                        finalIcon = white
                        finalStroke = Color.TRANSPARENT
                        finalStrokeWidth = 0
                    }
                    ChallengeStatus.RECORDING -> {
                        finalBg = recordingRed
                        finalText = white
                        finalIcon = white
                        finalStroke = Color.TRANSPARENT
                        finalStrokeWidth = 0
                    }
                    ChallengeStatus.RECOGNIZING -> {
                        finalBg = recognizingYellow
                        finalText = black
                        finalIcon = black
                        finalStroke = Color.TRANSPARENT
                        finalStrokeWidth = 0
                    }
                    ChallengeStatus.OK -> {
                        finalBg = okBg
                        finalText = okText
                        finalIcon = okText
                        finalStroke = okStroke
                        finalStrokeWidth = 2.dpToPx()
                    }
                    ChallengeStatus.RETRYABLE -> {
                        finalBg = retryBg
                        finalText = retryText
                        finalIcon = retryText
                        finalStroke = retryStroke
                        finalStrokeWidth = 2.dpToPx()
                    }
                }
            }
        }

        // 3. Apply everything using ColorStateList to bypass default disabled-alpha
        button.text = label
        button.setIconResource(iconRes)
        
        val cslBg = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)), intArrayOf(finalBg, finalBg))
        val cslText = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)), intArrayOf(finalText, finalText))
        val cslIcon = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)), intArrayOf(finalIcon, finalIcon))

        button.backgroundTintList = cslBg
        button.setTextColor(cslText)
        button.iconTint = cslIcon
        button.setStrokeColor(ColorStateList.valueOf(finalStroke))
        button.strokeWidth = finalStrokeWidth
        button.isEnabled = finalEnabled
    }

    private fun handlePronounceClick(type: String) {
        if (appSettings.silentMode == SilentMode.ON) {
            AppDialogHelper.showInfo(this, "サイレントモード中です", "発音チェックは、声を出せる環境で利用できます。", "OK")
            return
        }

        if (currentJobState != JobState.IDLE) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            currentRecognizingType = type
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            currentRecognizingType = type
            // Clear result text for new attempt
            if (type == "word") {
                binding.currentWordCard.textWordRecognitionResult.visibility = View.GONE
            } else {
                binding.currentWordCard.textSentenceRecognitionResult.visibility = View.GONE
            }
            startSpeechRecognition()
        }
    }

    private fun handleListenClick(text: String, type: String) {
        if (currentJobState != JobState.IDLE) return
        if (ttsReady && text.isNotBlank()) {
            currentJobState = JobState.PLAYING
            updateButtonStates()
            
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "pron_listen")
            tts?.speak(sanitizeForTts(text), TextToSpeech.QUEUE_FLUSH, params, "pron_listen")
            
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        currentJobState = JobState.IDLE
                        updateButtonStates()
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        currentJobState = JobState.IDLE
                        updateButtonStates()
                    }
                }
            })
        }
    }

    private fun startSpeechRecognition() {
        activeAttemptId++
        val attemptId = activeAttemptId
        isAttemptFinished = false
        
        currentJobState = JobState.STARTING
        if (currentRecognizingType == "word") {
            wordStatus = ChallengeStatus.RECORDING
        } else {
            sentenceStatus = ChallengeStatus.RECORDING
        }
        updateButtonStates()

        Log.d("PronChallenge", "attempt=$attemptId target=$currentRecognizingType state=STARTING action=startSpeechRecognition")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }

        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(ChallengeRecognitionListener(attemptId))
            startListening(intent)
        }
    }

    private fun showResult() {
        if (currentMode == MODE_SESSION) appSettings.pronChallengeStatus = "COMPLETED"
        binding.scrollChallenge.visibility = View.GONE
        binding.textProgress.visibility = View.GONE
        binding.layoutSteps.visibility = View.GONE
        binding.btnNextWord.visibility = View.GONE
        binding.layoutResult.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@PronunciationChallengeActivity)
            val ids = words.map { it.no.toLong() }
            val results = withContext(Dispatchers.IO) { db.voiceCheckDao().getAllResultsByIds(ids) }
            
            val wordOkCount = ids.count { id -> results.any { it.wordId == id && it.checkType == "word" && it.checked } }
            val sentOkCount = ids.count { id -> results.any { it.wordId == id && it.checkType == "sentence" && it.checked } }
            
            binding.textResultSummary.text = "単語 $wordOkCount / ${words.size} OK\n例文 $sentOkCount / ${words.size} OK"
            binding.textCpSummary.text = "例文は学習履歴からいつでも続けられます"
        }
    }

    private fun finishAttempt(attemptId: Int, matches: ArrayList<String>?, error: Int?) {
        if (attemptId != activeAttemptId || isAttemptFinished) return
        isAttemptFinished = true

        val capturedWord = words[currentIndex]
        val capturedType = currentRecognizingType

        Log.d("PronChallenge", "attempt=$attemptId target=$capturedType state=$currentJobState callback=finishAttempt error=$error")

        if (error != null) {
            currentJobState = JobState.IDLE
            if (capturedType == "word") wordStatus = ChallengeStatus.RETRYABLE else sentenceStatus = ChallengeStatus.RETRYABLE
            updateButtonStates()
            
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "認識できませんでした。もう一度お試しください"
                else -> "音声認識を開始できませんでした。もう一度お試しください"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        val matchedCandidate = matches?.firstOrNull { candidate ->
            if (capturedType == "word") {
                PronunciationUtils.checkWordPronunciation(candidate, capturedWord.word)
            } else {
                PronunciationUtils.checkSentencePronunciation(candidate, capturedWord.sentence, capturedWord.word)
            }
        }
        val isSuccess = matchedCandidate != null
        val displayRecognized = matchedCandidate ?: matches?.firstOrNull() ?: ""
        
        currentJobState = JobState.IDLE

        lifecycleScope.launch {
            if (isSuccess) {
                soundEffectManager.playCorrect()
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@PronunciationChallengeActivity).voiceCheckDao().recordResult(capturedWord.no.toLong(), true, 0.9f, capturedType)
                }
            } else {
                soundEffectManager.playWrong()
                // record failure attempt too
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@PronunciationChallengeActivity).voiceCheckDao().recordResult(capturedWord.no.toLong(), false, 0.1f, capturedType)
                }
            }

            if (capturedType == "word") {
                // NEVER revert OK status to RETRYABLE
                if (isSuccess || wordStatus != ChallengeStatus.OK) {
                    wordStatus = if (isSuccess) ChallengeStatus.OK else ChallengeStatus.RETRYABLE
                }
                
                showRecognitionResult(binding.currentWordCard.textWordRecognitionResult, capturedWord.word, displayRecognized, isSuccess)
                if (isSuccess) {
                    binding.currentWordCard.textUnlockFeedback.visibility = View.VISIBLE
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.currentWordCard.textUnlockFeedback.visibility = View.GONE
                    }, 2000)
                }
            } else {
                if (isSuccess || sentenceStatus != ChallengeStatus.OK) {
                    sentenceStatus = if (isSuccess) ChallengeStatus.OK else ChallengeStatus.RETRYABLE
                }
                showRecognitionResult(binding.currentWordCard.textSentenceRecognitionResult, capturedWord.sentence, displayRecognized, isSuccess)
            }
            
            updateButtonStates()
        }
    }

    inner class ChallengeRecognitionListener(private val attemptId: Int) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (attemptId != activeAttemptId) return
            Log.d("PronChallenge", "attempt=$attemptId state=RECORDING callback=onReadyForSpeech")
            currentJobState = JobState.RECORDING
            updateButtonStates()
        }
        override fun onBeginningOfSpeech() {
            if (attemptId != activeAttemptId) return
            Log.d("PronChallenge", "attempt=$attemptId state=RECORDING callback=onBeginningOfSpeech")
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            if (attemptId != activeAttemptId) return
            Log.d("PronChallenge", "attempt=$attemptId state=RECOGNIZING callback=onEndOfSpeech")
            currentJobState = JobState.RECOGNIZING
            if (currentRecognizingType == "word") wordStatus = ChallengeStatus.RECOGNIZING else sentenceStatus = ChallengeStatus.RECOGNIZING
            updateButtonStates()
        }
        override fun onError(error: Int) {
            if (attemptId != activeAttemptId) {
                Log.d("PronChallenge", "attempt=$attemptId IGNORED callback=onError code=$error")
                return
            }
            Log.d("PronChallenge", "attempt=$attemptId state=$currentJobState callback=onError code=$error")
            
            // Client error or busy often happens when stopping previous session
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                // One-time internal retry if it's the active attempt and just starting
                if (currentJobState == JobState.STARTING) {
                    Log.w("PronChallenge", "attempt=$attemptId Internal retry due to BUSY/CLIENT error")
                    startSpeechRecognition()
                    return
                }
            }
            
            finishAttempt(attemptId, null, error)
        }

        override fun onResults(results: Bundle?) {
            if (attemptId != activeAttemptId) return
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d("PronChallenge", "attempt=$attemptId state=$currentJobState callback=onResults matches=$matches")
            finishAttempt(attemptId, matches, null)
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun showRecognitionResult(textView: android.widget.TextView, target: String, recognized: String, isSuccess: Boolean) {
        textView.visibility = View.VISIBLE
        if (recognized.isBlank()) {
            textView.text = "認識結果: (聞き取れませんでした)"
            textView.setTextColor(Color.RED)
            return
        }

        val resultPrefix = if (isSuccess) "一致: " else "違いあり: "
        val fullText = "$resultPrefix$recognized"
        val spannable = SpannableString(fullText)
        
        val color = if (isSuccess) Color.parseColor("#1B7F3A") else Color.parseColor("#B3261E")
        spannable.setSpan(ForegroundColorSpan(color), 0, resultPrefix.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        // Simple highlighting: if not success, color the whole recognized text differently? 
        // Or just the prefix. For sentences, word-by-word diff would be better but complex.
        // Let's at least color the recognized part.
        textView.text = spannable
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        soundEffectManager.release()
        super.onDestroy()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
