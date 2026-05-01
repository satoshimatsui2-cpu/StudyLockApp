package com.example.studylockapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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

    private var wordId: Long = -1L
    private var wordText: String = ""
    private var wordMeaning: String = ""

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

        if (wordId == -1L || wordText.isBlank()) {
            Toast.makeText(this, "データが正しくありません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.textWord.text = wordText
        binding.textMeaning.text = wordMeaning

        // 4. TTS初期化前はお手本再生ボタンを無効化
        binding.buttonSpeakMaster.isEnabled = false

        tts = TextToSpeech(this, this)

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "音声認識が利用できません", Toast.LENGTH_SHORT).show()
            binding.fabMic.isEnabled = false
        } else {
            setupSpeechRecognizer()
        }

        binding.buttonSpeakMaster.setOnClickListener {
            speakMaster()
        }

        binding.fabMic.setOnClickListener {
            checkPermissionAndStart()
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    binding.textStatus.text = "聞いています..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    binding.textStatus.text = "判定中..."
                }
                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "聞き取れませんでした"
                        SpeechRecognizer.ERROR_AUDIO -> "オーディオエラー"
                        SpeechRecognizer.ERROR_NETWORK -> "ネットワークエラー"
                        else -> "エラーが発生しました ($error)"
                    }
                    binding.textStatus.text = "ボタンを押して発音してください"
                    binding.textResult.text = message
                    
                    // 3. エラー時に録音ボタンを再有効化
                    binding.fabMic.isEnabled = true
                    
                    recordResult(false, 0f)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    
                    // 2. 全候補を確認して判定
                    val isSuccess = matches.any { checkPronunciation(it, wordText) }
                    val input = matches.firstOrNull() ?: ""
                    val confidence = confidences?.firstOrNull() ?: 0f
                    
                    if (isSuccess) {
                        binding.textResult.text = "発音OK！"
                        binding.textResult.setTextColor(ContextCompat.getColor(this@PronunciationCheckActivity, android.R.color.holo_green_dark))
                    } else {
                        binding.textResult.text = "もう一回！: $input"
                        binding.textResult.setTextColor(ContextCompat.getColor(this@PronunciationCheckActivity, android.R.color.holo_red_dark))
                    }
                    binding.textStatus.text = "ボタンを押して発音してください"
                    
                    // 3. 判定終了後に録音ボタンを再有効化
                    binding.fabMic.isEnabled = true

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
        // 3. 録音開始時にボタンを無効化（連打対策）
        binding.fabMic.isEnabled = false

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun speakMaster() {
        tts?.speak(wordText, TextToSpeech.QUEUE_FLUSH, null, "master")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            // 4. Locale.US が利用可能な場合のみボタンを有効化
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                binding.buttonSpeakMaster.isEnabled = true
            }
        }
    }

    private fun checkPronunciation(input: String, target: String): Boolean {
        val normalizedInput = input.lowercase().replace(Regex("[^a-z ]"), "").trim()
        val normalizedTarget = target.lowercase().replace(Regex("[^a-z ]"), "").trim()
        
        if (normalizedInput == normalizedTarget) return true
        if (normalizedInput == "${normalizedTarget}s") return true
        if (normalizedInput == "${normalizedTarget}es") return true
        
        return false
    }

    private fun recordResult(isSuccess: Boolean, confidence: Float) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@PronunciationCheckActivity).voiceCheckDao()
                    .recordResult(wordId, isSuccess, confidence)
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
        super.onDestroy()
    }
}
