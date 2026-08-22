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
import kotlinx.coroutines.CancellationException
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
    private var wordHasEverPassed = false
    private var sentenceHasEverPassed = false
    private var isLoadingWordState = false
    private var sessionPassedWordIds: Set<Long> = emptySet()
    private var currentRecognizingType: String = "word" // "word" or "sentence"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingStart: Runnable? = null
    private var activeAttemptId: Int = 0
    private var finishedAttemptId: Int = -1
    private var internalRetryCount = 0

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

        val savedIndex = if (currentMode == MODE_SESSION) appSettings.pronChallengeIndex else 0
        Log.d("PronChallenge", "Activity onCreate: mode=$currentMode, savedIndex=$savedIndex, wordIds=$wordIds")

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@PronunciationChallengeActivity)
            val loadedWords = withContext(Dispatchers.IO) {
                db.wordDao().getWordsByIds(wordIds.map { it.toInt() })
            }

            // RoomのIN句は入力順を保証しないため、保存済みセッションIDの順番に戻す。
            words = wordIds.mapNotNull { id ->
                loadedWords.firstOrNull { it.no.toLong() == id }
            }
            Log.d("PronChallenge", "Words loaded: count=${words.size}, IDs=${words.map { it.no }}")

            if (words.isEmpty() || (currentMode == MODE_SESSION && words.size != 3)) {
                Log.e("PronChallenge", "Failed to prepare session words. count=${words.size}")
                finish()
                return@launch
            }

            currentIndex = savedIndex.coerceIn(0, words.lastIndex)
            Log.d("PronChallenge", "currentIndex set to $currentIndex (1-based: ${currentIndex + 1})")
            if (currentMode == MODE_SESSION) {
                appSettings.pronChallengeIndex = currentIndex
                val sessionIds = words.map { it.no.toLong() }
                sessionPassedWordIds = withContext(Dispatchers.IO) {
                    db.voiceCheckDao()
                        .getResultsByIds(sessionIds, "word")
                        .filter { it.checked }
                        .map { it.wordId }
                        .toSet()
                }
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
            if (currentMode != MODE_SESSION || currentJobState != JobState.IDLE || isLoadingWordState) {
                return@setOnClickListener
            }

            Log.d("PronChallenge", "Next button clicked. currentIndex=$currentIndex, lastIndex=${words.lastIndex}")
            if (currentIndex < words.lastIndex) {
                moveToWord(currentIndex + 1)
            } else {
                handleSessionEnd()
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
        val loadingIndex = currentIndex

        isLoadingWordState = true
        wordHasEverPassed = false
        sentenceHasEverPassed = false
        wordStatus = ChallengeStatus.NOT_STARTED
        sentenceStatus = ChallengeStatus.NOT_STARTED

        if (currentMode == MODE_SESSION) {
            binding.textProgress.text = "${currentIndex + 1} / ${words.size}"
        }

        binding.currentWordCard.textWord.text = word.word
        binding.currentWordCard.textMeaning.text = word.japanese
        binding.currentWordCard.textSentence.text = word.sentence
        binding.currentWordCard.textSentence.visibility = if (word.sentence.isNotBlank()) View.VISIBLE else View.GONE

        binding.currentWordCard.textWordRecognitionResult.visibility = View.GONE
        binding.currentWordCard.textSentenceRecognitionResult.visibility = View.GONE
        binding.currentWordCard.textUnlockFeedback.visibility = View.GONE
        updateButtonStates()

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@PronunciationChallengeActivity)
                val states = withContext(Dispatchers.IO) {
                    val wordOk = db.voiceCheckDao().getResult(word.no.toLong(), "word")?.checked ?: false
                    val sentenceOk = db.voiceCheckDao().getResult(word.no.toLong(), "sentence")?.checked ?: false
                    wordOk to sentenceOk
                }

                // 画面遷移後に古いDB結果が戻ってきても、新しい単語を上書きしない。
                if (currentIndex != loadingIndex || words[currentIndex].no != word.no) return@launch

                wordHasEverPassed = states.first
                sentenceHasEverPassed = states.second
                if (wordHasEverPassed) {
                    sessionPassedWordIds = sessionPassedWordIds + word.no.toLong()
                }
                wordStatus = if (wordHasEverPassed) ChallengeStatus.OK else ChallengeStatus.NOT_STARTED
                sentenceStatus = if (sentenceHasEverPassed) ChallengeStatus.OK else ChallengeStatus.NOT_STARTED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e("PronChallenge", "Failed to load pronunciation state", t)
                if (currentIndex == loadingIndex) {
                    Toast.makeText(this@PronunciationChallengeActivity, "発音状態を読み込めませんでした", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (currentIndex == loadingIndex && words[currentIndex].no == word.no) {
                    isLoadingWordState = false
                    updateButtonStates()
                }
            }
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
                    val color = if (i == currentIndex) {
                        ContextCompat.getColor(this@PronunciationChallengeActivity, R.color.mustard_primary)
                    } else if (words[i].no.toLong() in sessionPassedWordIds) {
                        ContextCompat.getColor(this@PronunciationChallengeActivity, R.color.navy_primary)
                    } else {
                        Color.LTGRAY
                    }
                    background = ContextCompat.getDrawable(this@PronunciationChallengeActivity, R.drawable.bg_circle_indicator)
                    background?.setTint(color)
                }
                binding.layoutSteps.addView(view)
            }
        }

        val isBusy = currentJobState != JobState.IDLE || isLoadingWordState

        // Word Section
        updateButton(card.btnPronounceWord, wordStatus, "単語")
        card.btnListenWord.isEnabled = !isBusy

        // Sentence Section
        val hasSentence = word.sentence.isNotBlank()
        card.labelSentence.visibility = if (hasSentence) View.VISIBLE else View.GONE
        card.btnListenSentence.visibility = if (hasSentence) View.VISIBLE else View.GONE
        card.btnPronounceSentence.visibility = if (hasSentence) View.VISIBLE else View.GONE
        card.layoutSentenceHeader.visibility = if (hasSentence) View.VISIBLE else View.GONE
        card.textSentence.visibility = if (hasSentence && wordHasEverPassed) View.VISIBLE else View.GONE

        updateButton(card.btnPronounceSentence, sentenceStatus, "例文")
        card.btnListenSentence.isEnabled = !isBusy && wordHasEverPassed

        card.textLockHint.visibility = if (hasSentence && !wordHasEverPassed) View.VISIBLE else View.GONE

        // Next Button
        binding.btnNextWord.isEnabled = currentMode == MODE_SESSION && !isBusy
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
        val isLocked = button.id == R.id.btn_pronounce_sentence && !wordHasEverPassed
        val isBusy = currentJobState != JobState.IDLE || isLoadingWordState
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

        if (currentJobState != JobState.IDLE || isLoadingWordState) return

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
        if (currentJobState != JobState.IDLE || isLoadingWordState) return
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

    private fun startSpeechRecognition(isInternalRetry: Boolean = false) {
        if (isFinishing || isDestroyed) return

        // 予約済みの開始処理があればキャンセル
        pendingStart?.let { mainHandler.removeCallbacks(it) }

        if (!isInternalRetry) internalRetryCount = 0

        activeAttemptId++
        val attemptId = activeAttemptId

        currentJobState = JobState.STARTING
        if (currentRecognizingType == "word") {
            wordStatus = ChallengeStatus.RECORDING
        } else {
            sentenceStatus = ChallengeStatus.RECORDING
        }
        updateButtonStates()

        Log.d("PronChallenge", "attempt=$attemptId target=$currentRecognizingType state=STARTING action=startSpeechRecognition isRetry=$isInternalRetry")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }

        // 既存のRecognizerを破棄
        val existed = speechRecognizer != null
        speechRecognizer?.apply {
            try {
                cancel()
                destroy()
            } catch (e: Exception) {
                Log.w("PronChallenge", "attempt=$attemptId Failed to cleanup old recognizer", e)
            }
        }
        speechRecognizer = null

        // 再生成までの待機時間を決定
        val delayMs = when {
            isInternalRetry -> 500L // 内部リトライ時はしっかり待機
            existed -> 400L         // 既存破棄時は解放を待つ
            else -> 0L              // 初回起動時は即時
        }

        val runnable = Runnable {
            if (attemptId != activeAttemptId || isFinishing || isDestroyed) {
                Log.d("PronChallenge", "attempt=$attemptId IGNORED pendingStart (stale or finished)")
                return@Runnable
            }

            try {
                Log.d("PronChallenge", "attempt=$attemptId Creating SpeechRecognizer and startListening")
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@PronunciationChallengeActivity).apply {
                    setRecognitionListener(ChallengeRecognitionListener(attemptId))
                    startListening(intent)
                }
            } catch (e: Exception) {
                Log.e("PronChallenge", "attempt=$attemptId Failed to create/start recognizer", e)
                finishAttempt(attemptId, null, SpeechRecognizer.ERROR_CLIENT)
            }
        }
        pendingStart = runnable
        if (delayMs > 0) {
            Log.d("PronChallenge", "attempt=$attemptId Scheduling start in ${delayMs}ms")
            mainHandler.postDelayed(runnable, delayMs)
        } else {
            runnable.run()
        }
    }

    private fun moveToWord(index: Int) {
        if (currentMode != MODE_SESSION || currentJobState != JobState.IDLE || isLoadingWordState) return

        // 予約済みの開始処理をキャンセル
        pendingStart?.let { mainHandler.removeCallbacks(it) }
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        speechRecognizer = null

        currentIndex = index.coerceIn(0, words.lastIndex)
        Log.d("PronChallenge", "Moving to word index $currentIndex")
        appSettings.pronChallengeIndex = currentIndex
        appSettings.pronChallengeStatus = "IN_PROGRESS"
        loadCurrentWord()
    }

    private fun handleSessionEnd() {
        if (currentMode != MODE_SESSION || currentJobState != JobState.IDLE || isLoadingWordState) return

        // DB確認中の連打を防ぐ。
        isLoadingWordState = true
        updateButtonStates()

        lifecycleScope.launch {
            try {
                val ids = words.map { it.no.toLong() }
                val results = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@PronunciationChallengeActivity)
                        .voiceCheckDao()
                        .getResultsByIds(ids, "word")
                }
                val passedIds = results
                    .filter { it.checked }
                    .map { it.wordId }
                    .toSet()
                sessionPassedWordIds = passedIds

                val firstUnclearedIndex = words.indexOfFirst { it.no.toLong() !in passedIds }
                if (firstUnclearedIndex == -1) {
                    isLoadingWordState = false
                    showResult()
                    return@launch
                }

                val unclearedCount = words.count { it.no.toLong() !in passedIds }
                isLoadingWordState = false
                updateButtonStates()
                showIncompleteSessionDialog(unclearedCount, firstUnclearedIndex)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e("PronChallenge", "Failed to check session completion", t)
                isLoadingWordState = false
                updateButtonStates()
                Toast.makeText(
                    this@PronunciationChallengeActivity,
                    "セッション状態を確認できませんでした",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showIncompleteSessionDialog(unclearedCount: Int, firstUnclearedIndex: Int) {
        AppDialogHelper.showPronunciationIncomplete(
            context = this,
            unclearedCount = unclearedCount,
            onContinue = {
                moveToWord(firstUnclearedIndex)
            },
            onLater = {
                // セッションを終了
                val failedIds = appSettings.currentPronunciationFailedIds
                appSettings.deferredPronunciationIds = failedIds
                appSettings.clearPronunciationChallenge()
                
                Toast.makeText(this, "セッションを終了しました。苦手な単語は少し間を空けて再出題します。", Toast.LENGTH_LONG).show()
                finish()
            },
            onCancel = {
                updateButtonStates()
            }
        )
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
        if (attemptId != activeAttemptId || attemptId == finishedAttemptId) {
            Log.d("PronChallenge", "attempt=$attemptId IGNORED finishAttempt (active=$activeAttemptId finished=$finishedAttemptId)")
            return
        }
        finishedAttemptId = attemptId

        val capturedWord = words[currentIndex]
        val capturedType = currentRecognizingType

        val errorName = error?.let { getErrorName(it) }
        Log.d("PronChallenge", "attempt=$attemptId target=$capturedType state=$currentJobState callback=finishAttempt error=$errorName")

        if (error != null) {
            currentJobState = JobState.IDLE
            if (capturedType == "word") {
                wordStatus = if (wordHasEverPassed) ChallengeStatus.OK else ChallengeStatus.RETRYABLE
            } else {
                sentenceStatus = if (sentenceHasEverPassed) ChallengeStatus.OK else ChallengeStatus.RETRYABLE
            }
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

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@PronunciationChallengeActivity)
                        .voiceCheckDao()
                        .recordResult(
                            capturedWord.no.toLong(),
                            isSuccess,
                            if (isSuccess) 0.9f else 0.1f,
                            capturedType
                        )
                }

                if (isSuccess) {
                    soundEffectManager.playCorrect()
                    if (currentMode == MODE_SESSION && capturedType == "word") {
                        appSettings.currentPronunciationFailedIds = appSettings.currentPronunciationFailedIds - capturedWord.no.toLong()
                    }
                } else {
                    soundEffectManager.playWrong()
                    if (currentMode == MODE_SESSION && capturedType == "word") {
                        appSettings.currentPronunciationFailedIds = appSettings.currentPronunciationFailedIds + capturedWord.no.toLong()
                    }
                }

                if (capturedType == "word") {
                    if (isSuccess) {
                        wordHasEverPassed = true
                        sessionPassedWordIds = sessionPassedWordIds + capturedWord.no.toLong()
                    }
                    wordStatus = if (wordHasEverPassed) ChallengeStatus.OK else ChallengeStatus.RETRYABLE

                    showRecognitionResult(
                        binding.currentWordCard.textWordRecognitionResult,
                        capturedWord.word,
                        displayRecognized,
                        isSuccess
                    )
                    if (isSuccess) {
                        binding.currentWordCard.textUnlockFeedback.visibility = View.VISIBLE
                        Handler(Looper.getMainLooper()).postDelayed({
                            binding.currentWordCard.textUnlockFeedback.visibility = View.GONE
                        }, 2000)
                    }
                } else {
                    if (isSuccess) sentenceHasEverPassed = true
                    sentenceStatus = if (sentenceHasEverPassed) ChallengeStatus.OK else ChallengeStatus.RETRYABLE
                    showRecognitionResult(
                        binding.currentWordCard.textSentenceRecognitionResult,
                        capturedWord.sentence,
                        displayRecognized,
                        isSuccess
                    )
                }

                currentJobState = JobState.IDLE
                updateButtonStates()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e("PronChallenge", "Failed to save pronunciation result", t)
                currentJobState = JobState.IDLE
                if (capturedType == "word") {
                    wordStatus = if (wordHasEverPassed) ChallengeStatus.OK else ChallengeStatus.RETRYABLE
                } else {
                    sentenceStatus = if (sentenceHasEverPassed) ChallengeStatus.OK else ChallengeStatus.RETRYABLE
                }
                updateButtonStates()
                Toast.makeText(
                    this@PronunciationChallengeActivity,
                    "発音結果を保存できませんでした",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
                Log.d("PronChallenge", "attempt=$attemptId IGNORED callback=onError code=$error (${getErrorName(error)})")
                return
            }
            val errorName = getErrorName(error)
            Log.d("PronChallenge", "attempt=$attemptId state=$currentJobState callback=onError code=$error ($errorName)")

            // Client error or busy often happens when stopping previous session
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                // One-time internal retry
                if (internalRetryCount < 1) {
                    internalRetryCount++
                    Log.w("PronChallenge", "attempt=$attemptId Internal retry scheduled for $errorName")
                    startSpeechRecognition(isInternalRetry = true)
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

    private fun getErrorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        else -> "UNKNOWN($error)"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
        }
    }

    override fun onDestroy() {
        pendingStart?.let { mainHandler.removeCallbacks(it) }
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        speechRecognizer = null
        tts?.shutdown()
        soundEffectManager.release()
        super.onDestroy()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}