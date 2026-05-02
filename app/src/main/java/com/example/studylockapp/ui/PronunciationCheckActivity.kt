package com.example.studylockapp.ui

import android.Manifest
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
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.databinding.ActivityPronunciationCheckBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var checkType: String = "word" // "word" or "sentence"

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
        checkType = intent.getStringExtra("CHECK_TYPE") ?: "word"

        if (wordId == -1L || wordText.isBlank()) {
            Toast.makeText(this, "データが正しくありません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 例文チェック時のバリデーション: 3語以上あるか
        if (checkType == "sentence") {
            val words = wordSentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (wordSentence.isBlank() || words.size < 3) {
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
            speakTarget()
        }

        binding.buttonRetry.setOnClickListener {
            checkPermissionAndStart()
        }

        binding.buttonClose.setOnClickListener {
            finish()
        }

        updateUI(UIState.IDLE)
    }

    private fun setupDisplay() {
        if (checkType == "sentence") {
            title = "📖 例文チェック"
            binding.textWord.text = wordSentence
            binding.textMeaning.text = wordSentenceJa
            binding.buttonListen.text = "例文を聞く"
            binding.buttonStartPronounce.text = "例文を読む"
            binding.buttonResultListen.text = "例文を聞く"
        } else {
            title = "🎙 単語チェック"
            binding.textWord.text = wordText
            binding.textMeaning.text = wordMeaning
            binding.buttonListen.text = "お手本を聞く"
            binding.buttonStartPronounce.text = "発音する"
            binding.buttonResultListen.text = "お手本を聞く"
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

    private fun updateUI(state: UIState, recognizedText: String = "") {
        when (state) {
            UIState.IDLE -> {
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.buttonStartPronounce.isEnabled = true
                binding.buttonStartPronounce.text = if (checkType == "sentence") "例文を読む" else "発音する"
                binding.buttonListen.isEnabled = ttsReady
                
                binding.textInstruction.visibility = View.VISIBLE
                binding.textInstruction.text = "ボタンを押して発音してください"
                binding.resultCard.visibility = View.GONE
            }
            UIState.RECORDING -> {
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
                binding.layoutActionButtons.visibility = View.GONE
                binding.textInstruction.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE

                binding.textResultStatus.text = "✅ 発音クリア！"
                binding.textResultStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                binding.textRecognizedValue.text = recognizedText
                
                val badgeName = if (checkType == "sentence") "例文" else "単語"
                binding.textResultMessage.text = "🎉 ${badgeName}OKバッジを獲得しました"
                binding.textResultMessage.setTextColor(Color.parseColor("#424242"))

                // 成功時ボタン: 戻る(メイン), もう一度試す(サブ)
                binding.buttonResultListen.visibility = View.GONE
                
                binding.buttonRetry.visibility = View.VISIBLE
                binding.buttonRetry.text = "もう一度試す"
                binding.buttonRetry.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
                binding.buttonRetry.setTextColor(Color.parseColor("#424242"))
                binding.buttonRetry.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
                binding.buttonRetry.strokeColor = ColorStateList.valueOf(Color.parseColor("#BDBDBD"))
                binding.buttonRetry.setIconTintResource(android.R.color.darker_gray)

                binding.buttonClose.visibility = View.VISIBLE
                binding.buttonClose.text = "戻る"
                binding.buttonClose.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3F51B5"))
                binding.buttonClose.setTextColor(Color.WHITE)
                binding.buttonClose.strokeWidth = 0
                
                playSound(true)
            }
            UIState.FAILURE -> {
                binding.layoutActionButtons.visibility = View.GONE
                binding.textInstruction.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE

                binding.textResultStatus.text = "😅 もう少し！"
                binding.textResultStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                binding.textRecognizedValue.text = if (recognizedText.isEmpty()) "(聞き取れませんでした)" else recognizedText
                binding.textResultMessage.text = "お手本を聞いて、もう一度チャレンジしてみよう"
                binding.textResultMessage.setTextColor(Color.parseColor("#424242"))

                // 失敗時ボタン: お手本(メイン), もう一度(メイン), 戻る(サブ)
                binding.buttonResultListen.visibility = View.VISIBLE
                binding.buttonResultListen.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3F51B5"))
                binding.buttonResultListen.setTextColor(Color.WHITE)
                binding.buttonResultListen.strokeWidth = 0

                binding.buttonRetry.visibility = View.VISIBLE
                binding.buttonRetry.text = "もう一度発音する"
                binding.buttonRetry.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E91E63"))
                binding.buttonRetry.setTextColor(Color.WHITE)
                binding.buttonRetry.strokeWidth = 0
                binding.buttonRetry.setIconTintResource(android.R.color.white)

                binding.buttonClose.visibility = View.VISIBLE
                binding.buttonClose.text = "戻る"
                binding.buttonClose.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
                binding.buttonClose.setTextColor(Color.parseColor("#757575"))
                binding.buttonClose.strokeWidth = 0
                
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
                    recordResult(false, 0f)
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

                    if (isSuccess) {
                        updateUI(UIState.SUCCESS, displayText)
                    } else {
                        updateUI(UIState.FAILURE, displayText)
                    }
                    recordResult(isSuccess, confidence)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startListening()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startListening() {
        updateUI(UIState.RECORDING)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun speakTarget() {
        val text = if (checkType == "sentence") wordSentence else wordText
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "master")
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

    private fun checkWordPronunciation(input: String, target: String): Boolean {
        fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z]"), "").trim()
        val normInput = normalize(input)
        val normTarget = normalize(target)
        
        return normInput == normTarget ||
               normInput == "${normTarget}s" ||
               normInput == "${normTarget}es"
    }

    private fun checkSentencePronunciation(recognized: String, targetSentence: String, targetWord: String): Boolean {
        Log.d("PronunciationCheck", "--- Start Sentence Check ---")
        Log.d("PronunciationCheck", "checkType=$checkType")
        Log.d("PronunciationCheck", "targetSentence=$targetSentence")
        Log.d("PronunciationCheck", "targetWord=$targetWord")
        Log.d("PronunciationCheck", "recognized=$recognized")

        // 記号をスペースに置換し、連続スペースを1つにまとめてトリム
        fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z]"), " ").replace(Regex("\\s+"), " ").trim()
        
        val normRecognized = normalize(recognized)
        val normTarget = normalize(targetSentence)
        val normWord = normalize(targetWord).replace(" ", "") // 単語チェック用

        Log.d("PronunciationCheck", "normRecognized=$normRecognized")
        Log.d("PronunciationCheck", "normTarget=$normTarget")
        Log.d("PronunciationCheck", "normWord=$normWord")

        // 1. 正規化後の完全一致を最優先
        if (normRecognized == normTarget) {
            Log.d("PronunciationCheck", "Exact normalized match! SUCCESS")
            return true
        }

        val recognizedWords = normRecognized.split(" ").filter { it.isNotBlank() }.toSet()
        val allTargetWords = normTarget.split(" ").filter { it.isNotBlank() }
        
        if (allTargetWords.isEmpty()) return false

        val functionalWords = setOf("a", "an", "the", "is", "am", "are", "to", "of", "in", "on", "at")
        
        // 機能語を除外して判定用リストを作成
        val filteredTargetWords = allTargetWords.filter { it !in functionalWords }
        val wordsToMatch = if (filteredTargetWords.size < 2) allTargetWords else filteredTargetWords
        
        // 一致率 70% 以上
        val matchedCount = wordsToMatch.count { it in recognizedWords }
        val matchRate = matchedCount.toFloat() / wordsToMatch.size.toFloat()
        
        // 2. 対象単語が含まれているか（単語単位で判定）
        val containsTargetWord = recognizedWords.any {
            it == normWord || it == "${normWord}s" || it == "${normWord}es"
        }

        Log.d("PronunciationCheck", "matchRate=$matchRate (matched=$matchedCount / total=${wordsToMatch.size})")
        Log.d("PronunciationCheck", "containsTargetWord=$containsTargetWord")
        
        val isOk = matchRate >= 0.7f && containsTargetWord
        Log.d("PronunciationCheck", "--- Final Result: $isOk ---")
        
        return isOk
    }

    private fun recordResult(isSuccess: Boolean, confidence: Float) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@PronunciationCheckActivity).voiceCheckDao()
                    .recordResult(wordId, isSuccess, confidence, checkType)
            }
        }
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
