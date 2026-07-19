package com.stulab.studylockapp.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.PointHistoryEntity
import com.stulab.studylockapp.data.PointManager
import com.stulab.studylockapp.data.StudyHistoryRepository
import com.stulab.studylockapp.databinding.ActivityPronunciationCheckBinding
import com.stulab.studylockapp.ui.alert.AppDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.*

class PronunciationCheckActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityPronunciationCheckBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var wordId: Long = -1L
    private var wordText: String = ""
    private var wordMeaning: String = ""
    private var wordSentence: String = ""
    private var wordSentenceJa: String = ""
    private var wordGrade: String = ""
    private var checkType: String = "word" // "word" or "sentence"

    // 短い単語救済モード用フラグ
    private var isRescueMode = false
    private var rescueSuggestionVisible = false

    // ボーナスポイント二重付与防止フラグ
    private var wordBonusGrantedInThisSession = false
    private var sentenceBonusGrantedInThisSession = false

    // 発音チェック記録の二重保存防止フラグ（1回の録音につき1レコード）
    private var voiceCheckRecordedForCurrentAttempt = false

    // 効果音再生用
    private lateinit var soundPool: SoundPool
    private var soundSuccess: Int = 0
    private var soundFailure: Int = 0

    enum class UIState {
        IDLE,       // 待機中
        RECORDING,  // 録音中
        SUCCESS,    // 判定OK
        FAILURE     // 判定NG
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            Toast.makeText(this, "録音権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPronunciationCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wordId = intent.getLongExtra("WORD_ID", -1L)
        wordText = intent.getStringExtra("WORD_TEXT") ?: ""
        wordMeaning = intent.getStringExtra("WORD_MEANING") ?: ""
        wordSentence = intent.getStringExtra("WORD_SENTENCE") ?: ""
        wordSentenceJa = intent.getStringExtra("WORD_SENTENCE_JA") ?: ""
        wordGrade = intent.getStringExtra("WORD_GRADE") ?: ""
        checkType = intent.getStringExtra("CHECK_TYPE") ?: "word"

        if (wordId == -1L || wordText.isBlank()) {
            Toast.makeText(this, "データが正しくありません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 例文チェック時のバリデーション
        if (checkType == "sentence") {
            if (!hasValidSentence()) {
                Toast.makeText(this, "例文が短すぎるためチェックできません", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        setupDisplay()
        setupSoundPool()
        tts = TextToSpeech(this, this)

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "音声認識が利用できません", Toast.LENGTH_SHORT).show()
            binding.buttonStartPronounce.isEnabled = false
        } else {
            setupSpeechRecognizer()
        }

        binding.buttonListen.setOnClickListener {
            speakTarget()
        }

        binding.buttonStartPronounce.setOnClickListener {
            checkPermissionAndStart()
        }

        binding.buttonResultListen.setOnClickListener {
            // checkType で読み上げ対象を判断 (救済表示中なら例文を聞かせたい)
            speakTarget(forceSentence = (checkType == "sentence" || rescueSuggestionVisible))
        }

        binding.buttonRetry.setOnClickListener {
            if (rescueSuggestionVisible) {
                startRescueSentenceMode()
            } else {
                checkPermissionAndStart()
            }
        }

        binding.buttonSecondaryRetry.setOnClickListener {
            // 救済UIから単語チェックを再試行する場合
            isRescueMode = false
            rescueSuggestionVisible = false
            if (checkType == "sentence") {
                checkType = "word"
                setupDisplay()
            }
            checkPermissionAndStart()
        }

        binding.buttonClose.setOnClickListener {
            finish()
        }

        updateUI(UIState.IDLE)
    }

    private fun startRescueSentenceMode() {
        isRescueMode = true
        rescueSuggestionVisible = false
        checkType = "sentence"
        setupDisplay()
        updateUI(UIState.IDLE)
    }

    private fun setupDisplay() {
        if (checkType == "sentence") {
            title = "📖 例文チェック"
            binding.textWord.text = wordSentence
            binding.textMeaning.text = wordSentenceJa
            binding.buttonListen.text = "例文を聞く"
            binding.buttonStartPronounce.text = "例文を読む"
        } else {
            title = "🎙 単語チェック"
            binding.textWord.text = wordText
            binding.textMeaning.text = wordMeaning
            binding.buttonListen.text = "お手本を聞く"
            binding.buttonStartPronounce.text = "発音する"
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
        
        try {
            soundSuccess = soundPool.load(this, R.raw.se_correct, 1)
            soundFailure = soundPool.load(this, R.raw.se_wrong, 1)
        } catch (e: Exception) {
            Log.e("PronunciationCheck", "Failed to load sounds", e)
        }
    }

    private fun playSound(isSuccess: Boolean) {
        if (!::soundPool.isInitialized) return
        val soundId = if (isSuccess) soundSuccess else soundFailure
        if (soundId != 0) {
            try {
                soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
            } catch (e: Exception) {
                Log.e("PronunciationCheck", "Failed to play sound", e)
            }
        }
    }

    private fun updateUI(
        state: UIState,
        recognizedText: String = "",
        bonusPoints: Int = 0,
        newlyWord: Boolean = false,
        newlySentence: Boolean = false,
        hadWord: Boolean = false,
        hadSentence: Boolean = false
    ) {
        val isFromBonus = intent.getBooleanExtra("FROM_LEVEL5_BONUS", false)

        when (state) {
            UIState.IDLE -> {
                rescueSuggestionVisible = false
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.buttonStartPronounce.isEnabled = true
                binding.buttonStartPronounce.text = if (checkType == "sentence") "例文を読む" else "発音する"
                binding.buttonListen.isEnabled = ttsReady
                
                binding.textInstruction.visibility = View.VISIBLE
                binding.textInstruction.text = "ボタンを押して発音してください"
                binding.resultCard.visibility = View.GONE
            }
            UIState.RECORDING -> {
                rescueSuggestionVisible = false
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.buttonStartPronounce.isEnabled = false
                binding.buttonStartPronounce.text = "聞き取り中..."
                binding.buttonListen.isEnabled = false
                
                binding.textInstruction.visibility = View.VISIBLE
                val targetText = if (checkType == "sentence") "例文" else "\"$wordText\""
                binding.textInstruction.text = "$targetText と発音してください"
                binding.resultCard.visibility = View.GONE
            }
            UIState.SUCCESS -> {
                rescueSuggestionVisible = false
                binding.layoutActionButtons.visibility = View.GONE
                binding.textInstruction.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE

                binding.textResultStatus.text = "✅ 発音クリア！"
                binding.textResultStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                binding.textRecognizedValue.text = recognizedText
                
                binding.textResultMessage.setTextColor(Color.parseColor("#424242"))
                binding.textResultMessage.setTypeface(null, android.graphics.Typeface.BOLD)

                // バッジ獲得メッセージの構築
                val badgeMsg = when {
                    newlyWord && newlySentence -> "🎙 単語OK + 📖 例文OK バッジを獲得しました"
                    newlyWord -> "🎙 単語OKバッジを獲得しました"
                    newlySentence -> "📖 例文OKバッジを獲得しました"
                    else -> {
                        val name = if (checkType == "sentence") "📖 例文" else "🎙 単語"
                        "$name バッジ取得済み"
                    }
                }

                // ポイント表示の構築
                val pointsMsg = if (bonusPoints > 0) {
                    "🎁 ボーナス +${bonusPoints}pt"
                } else {
                    "追加ポイントはありません"
                }

                binding.textResultMessage.text = "$badgeMsg\n$pointsMsg"

                binding.buttonResultListen.visibility = View.GONE
                binding.buttonSecondaryRetry.visibility = View.GONE
                
                // 成功時ボタン優先度
                binding.buttonRetry.apply {
                    visibility = View.VISIBLE
                    text = "もう一度試す"
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
                    setTextColor(Color.parseColor("#424242"))
                    strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#BDBDBD"))
                    setIconTintResource(android.R.color.darker_gray)
                    setIconResource(android.R.drawable.ic_btn_speak_now)
                }

                binding.buttonClose.apply {
                    visibility = View.VISIBLE
                    text = if (isFromBonus) "学習に戻る" else "戻る"
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3F51B5"))
                    setTextColor(Color.WHITE)
                    strokeWidth = 0
                }
                
                playSound(true)
            }
            UIState.FAILURE -> {
                binding.layoutActionButtons.visibility = View.GONE
                binding.textInstruction.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE

                binding.textResultStatus.text = "😅 もう少し！"
                binding.textResultStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                binding.textRecognizedValue.text = if (recognizedText.isEmpty()) "(聞き取れませんでした)" else recognizedText
                binding.textResultMessage.setTextColor(Color.parseColor("#424242"))

                val canRescue = checkType == "word" && isShortWord(wordText) && hasValidSentence()
                rescueSuggestionVisible = canRescue

                if (canRescue) {
                    binding.textResultMessage.text = "短い単語は聞き取りが不安定なことがあります。\n例文でチェックしてみよう。"
                    binding.buttonResultListen.visibility = View.GONE

                    binding.buttonRetry.apply {
                        visibility = View.VISIBLE
                        text = "例文でチェックする"
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                        setTextColor(Color.WHITE)
                        strokeWidth = 0
                        setIconTintResource(android.R.color.white)
                        setIconResource(android.R.drawable.ic_menu_edit)
                    }

                    binding.buttonSecondaryRetry.apply {
                        visibility = View.VISIBLE
                        text = "もう一度単語を発音する"
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E91E63"))
                        setTextColor(Color.WHITE)
                        strokeWidth = 0
                        setIconTintResource(android.R.color.white)
                        setIconResource(android.R.drawable.ic_btn_speak_now)
                    }
                } else {
                    binding.textResultMessage.text = "お手本を聞いて、もう一度チャレンジしてみよう"
                    
                    binding.buttonResultListen.apply {
                        visibility = View.VISIBLE
                        text = if (checkType == "sentence") "例文を聞く" else "お手本を聞く"
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3F51B5"))
                        setTextColor(Color.WHITE)
                        strokeWidth = 0
                    }

                    binding.buttonRetry.apply {
                        visibility = View.VISIBLE
                        text = "もう一度発音する"
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E91E63"))
                        setTextColor(Color.WHITE)
                        strokeWidth = 0
                        setIconTintResource(android.R.color.white)
                        setIconResource(android.R.drawable.ic_btn_speak_now)
                    }
                    
                    binding.buttonSecondaryRetry.visibility = View.GONE
                }

                binding.buttonClose.apply {
                    visibility = View.VISIBLE
                    text = if (isFromBonus) "学習に戻る" else "戻る"
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
                    setTextColor(Color.parseColor("#757575"))
                    strokeWidth = 0
                }
                
                playSound(false)
            }
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    updateUI(UIState.RECORDING)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "聞き取れませんでした"
                        SpeechRecognizer.ERROR_AUDIO -> "オーディオエラー"
                        SpeechRecognizer.ERROR_NETWORK -> "ネットワークエラー"
                        else -> "エラーが発生しました ($error)"
                    }
                    updateUI(UIState.FAILURE, message)
                    recordVoiceCheck(false) // 失敗として記録
                    recordResultInternal(false, 0f)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    
                    Log.d("PronunciationCheck", "onResults: matches=$matches")

                    val matchedText = matches.firstOrNull { candidate ->
                        if (checkType == "sentence") {
                            checkSentencePronunciation(candidate, wordSentence, wordText)
                        } else {
                            checkWordPronunciation(candidate, wordText)
                        }
                    }

                    val isSuccess = matchedText != null
                    val displayText = matchedText ?: matches.firstOrNull().orEmpty()
                    val confidence = confidences?.firstOrNull() ?: 0f
                    
                    Log.d("PronunciationCheck", "Final Result: isSuccess=$isSuccess, displayText=$displayText")

                    recordVoiceCheck(isSuccess) // 成功/失敗を記録

                    if (isSuccess) {
                        handleProcessResult(true, displayText, confidence)
                    } else {
                        updateUI(UIState.FAILURE, displayText)
                        handleProcessResult(false, displayText, confidence)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun handleProcessResult(isSuccess: Boolean, displayText: String, confidence: Float) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@PronunciationCheckActivity)
            val pm = PointManager(this@PronunciationCheckActivity)
            val settings = AppSettings(this@PronunciationCheckActivity)

            val result = withContext(Dispatchers.IO) {
                // 1. 既存状態を取得
                val existingWordBefore = db.voiceCheckDao().getResult(wordId, "word")
                val existingSentenceBefore = db.voiceCheckDao().getResult(wordId, "sentence")

                val alreadyWordChecked = existingWordBefore?.checked == true
                val alreadySentenceChecked = existingSentenceBefore?.checked == true

                // 2. 今回成功でどのバッジが新たに checked になるか
                val willMarkWordAsChecked = isSuccess && (checkType == "word" || (isRescueMode && checkType == "sentence"))
                val willMarkSentenceAsChecked = isSuccess && checkType == "sentence"

                val shouldGiveWordBonus = willMarkWordAsChecked && !alreadyWordChecked && !wordBonusGrantedInThisSession
                val shouldGiveSentenceBonus = willMarkSentenceAsChecked && !alreadySentenceChecked && !sentenceBonusGrantedInThisSession

                val totalBonus = (if (shouldGiveWordBonus) 10 else 0) + (if (shouldGiveSentenceBonus) 10 else 0)

                // 3. recordResult を実行
                db.voiceCheckDao().recordResult(wordId, isSuccess, confidence, checkType)
                if (isSuccess && checkType == "sentence" && isRescueMode) {
                    db.voiceCheckDao().recordResult(wordId, true, confidence, "word")
                }

                // 4. ボーナス付与
                if (totalBonus > 0) {
                    pm.add(totalBonus)
                    val zone = settings.getAppZoneId()
                    val todayEpochDay = LocalDate.now(zone).toEpochDay()
                    val latestTotal = pm.getTotal()
                    
                    if (shouldGiveWordBonus) {
                        wordBonusGrantedInThisSession = true
                        db.pointHistoryDao().insert(PointHistoryEntity(mode = "voice_bonus", dateEpochDay = todayEpochDay, delta = 10))
                        lifecycleScope.launch(Dispatchers.IO) {
                            StudyHistoryRepository.addVoiceBonusPoints(wordGrade, wordText, 10, "word", latestTotal)
                        }
                    }
                    if (shouldGiveSentenceBonus) {
                        sentenceBonusGrantedInThisSession = true
                        db.pointHistoryDao().insert(PointHistoryEntity(mode = "voice_bonus", dateEpochDay = todayEpochDay, delta = 10))
                        lifecycleScope.launch(Dispatchers.IO) {
                            StudyHistoryRepository.addVoiceBonusPoints(wordGrade, wordText, 10, "sentence", latestTotal)
                        }
                    }

                    setResult(Activity.RESULT_OK, Intent().apply { putExtra("VOICE_BONUS_POINTS", totalBonus) })
                }
                
                object {
                    val bonusPoints = totalBonus
                    val newlyWord = shouldGiveWordBonus
                    val newlySentence = shouldGiveSentenceBonus
                    val hadWord = alreadyWordChecked
                    val hadSentence = alreadySentenceChecked
                }
            }
            
            if (isSuccess) {
                updateUI(
                    UIState.SUCCESS, 
                    displayText, 
                    bonusPoints = result.bonusPoints,
                    newlyWord = result.newlyWord,
                    newlySentence = result.newlySentence,
                    hadWord = result.hadWord,
                    hadSentence = result.hadSentence
                )
            }
        }
    }

    private fun recordResultInternal(isSuccess: Boolean, confidence: Float) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@PronunciationCheckActivity)
                db.voiceCheckDao().recordResult(wordId, isSuccess, confidence, checkType)
            }
        }
    }

    /**
     * Firestoreに発音チェックの試行を記録する。
     * 1回の録音セッションにつき1件のみ保存する。
     */
    private fun recordVoiceCheck(success: Boolean) {
        if (!voiceCheckRecordedForCurrentAttempt) {
            voiceCheckRecordedForCurrentAttempt = true
            lifecycleScope.launch(Dispatchers.IO) {
                StudyHistoryRepository.addVoiceCheckRecord(
                    grade = wordGrade,
                    word = wordText,
                    checkType = checkType,
                    success = success
                )
            }
        }
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startListening()
            }
            else -> {
                showRecordingDisclosure()
            }
        }
    }

    private fun showRecordingDisclosure() {
        AppDialogHelper.showConfirm(
            context = this,
            title = getString(R.string.recording_disclosure_title),
            message = getString(R.string.recording_disclosure_message),
            positiveText = getString(R.string.ok),
            negativeText = getString(R.string.cancel),
            onPositive = {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
    }

    private fun startListening() {
        voiceCheckRecordedForCurrentAttempt = false // フラグリセット
        updateUI(UIState.RECORDING)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun speakTarget(forceSentence: Boolean = false) {
        val text = getListenText(forceSentence)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "master")
    }

    private fun getListenText(forceSentence: Boolean = false): String {
        return if (forceSentence || checkType == "sentence" || rescueSuggestionVisible) {
            wordSentence
        } else {
            wordText
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = true
                runOnUiThread {
                    if (binding.resultCard.visibility != View.VISIBLE) {
                        binding.buttonListen.isEnabled = true
                    }
                    binding.buttonResultListen.isEnabled = true
                }
            }
        }
    }

    /**
     * 数字および時刻表現の表記ゆれを正規化する。
     * 例: "7:00", "7.00", "7 o'clock" -> "seven"
     */
    private fun normalizeNumberAndTime(text: String): String {
        var s = text.lowercase()

        val numWords = mapOf(
            "0" to "zero",
            "1" to "one",
            "2" to "two",
            "3" to "three",
            "4" to "four",
            "5" to "five",
            "6" to "six",
            "7" to "seven",
            "8" to "eight",
            "9" to "nine",
            "10" to "ten",
            "11" to "eleven",
            "12" to "twelve"
        )

        // 7:00 / 7.00 → seven
        s = s.replace(Regex("\\b(1[0-2]|[0-9])[:.](00)\\b")) { match ->
            numWords[match.groupValues[1]] ?: match.value
        }

        // 7:30 / 7.30 → seven thirty
        s = s.replace(Regex("\\b(1[0-2]|[0-9])[:.](30)\\b")) { match ->
            val hour = numWords[match.groupValues[1]] ?: match.groupValues[1]
            "$hour thirty"
        }

        // 7 o'clock → seven
        s = s.replace(Regex("\\b(1[0-2]|[0-9])\\s*o'?clock\\b")) { match ->
            numWords[match.groupValues[1]] ?: match.value
        }

        // seven o'clock → seven
        s = s.replace(Regex("\\b(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s*o'?clock\\b"), "$1")

        // standalone 7 → seven
        s = s.replace(Regex("\\b(1[0-2]|[0-9])\\b")) { match ->
            numWords[match.groupValues[1]] ?: match.value
        }

        return s
    }

    /**
     * 比較用の正規化処理。
     * 数字正規化、句読点除去、小文字化、空白の整理を行う。
     */
    private fun normalizeForComparison(s: String): String {
        // 1. 小文字化
        var normalized = s.lowercase()
        // 2. 数字・時刻の正規化 (記号除去前に実行)
        normalized = normalizeNumberAndTime(normalized)
        // 3. 記号除去 (アルファベットとスペースのみ残す)
        normalized = normalized.replace(Regex("[^a-z\\s]"), " ")
        // 4. 空白の正規化
        normalized = normalized.replace(Regex("\\s+"), " ").trim()
        return normalized
    }

    private fun normalizeMinimal(s: String): String {
        // 単語チェック用：数字正規化も含めた最小化
        val withNumbers = normalizeNumberAndTime(s.lowercase())
        return withNumbers.replace(Regex("[^a-z]"), "").trim()
    }

    private fun isShortWord(word: String): Boolean {
        return normalizeMinimal(word).length <= 4
    }

    private fun hasValidSentence(): Boolean {
        val words = normalizeForComparison(wordSentence)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        return words.size >= 3
    }

    private fun checkWordPronunciation(input: String, target: String): Boolean {
        val normInput = normalizeMinimal(input)
        val normTarget = normalizeMinimal(target)
        return normInput == normTarget || normInput == "${normTarget}s" || normInput == "${normTarget}es"
    }

    private fun checkSentencePronunciation(recognized: String, targetSentence: String, targetWord: String): Boolean {
        val normRecognized = normalizeForComparison(recognized)
        val normTarget = normalizeForComparison(targetSentence)

        // 1. 完全一致は即OK
        if (normRecognized == normTarget) return true

        val recognizedWords = normRecognized.split(" ").filter { it.isNotBlank() }.toSet()
        val allTargetWords = normTarget.split(" ").filter { it.isNotBlank() }
        if (allTargetWords.isEmpty()) return false

        // 2. 重要語リストの作成 (機能語を除外)
        val functionalWords = setOf("a", "an", "the", "is", "am", "are", "to", "of", "in", "on", "at")
        val filteredTargetWords = allTargetWords.filter { it !in functionalWords }
        val wordsToMatch = if (filteredTargetWords.size < 2) allTargetWords else filteredTargetWords
        
        // 3 & 4. 許容される欠損数の決定 (厳格化)
        val allowedMissing = when {
            wordsToMatch.size <= 5 -> 0
            wordsToMatch.size <= 8 -> 1
            else -> 2
        }

        val missingWords = wordsToMatch.filter { it !in recognizedWords }
        
        // 6. 対象単語が含まれているか (単語単位、フレーズ対応)
        val normTargetWord = normalizeForComparison(targetWord)
        val normTargetWordList = normTargetWord.split(" ").filter { it.isNotBlank() }
        val containsTargetWord = normTargetWordList.all { tw ->
            recognizedWords.any { rw ->
                rw == tw || rw == "${tw}s" || rw == "${tw}es"
            }
        }

        // 5. 最後の重要語が含まれているか
        val lastImportantWord = wordsToMatch.lastOrNull()
        val containsLastImportantWord = lastImportantWord == null || lastImportantWord in recognizedWords

        Log.d("PronunciationCheck", "wordsToMatch=$wordsToMatch recognizedWords=$recognizedWords missingWords=$missingWords allowedMissing=$allowedMissing containsTargetWord=$containsTargetWord containsLastImportantWord=$containsLastImportantWord")

        // 判定 (欠損数ベース)
        return missingWords.size <= allowedMissing && containsTargetWord && containsLastImportantWord
    }

    override fun onDestroy() {
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        tts?.apply {
            stop()
            shutdown()
        }
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
        super.onDestroy()
    }
}
