package com.example.studylockapp.learning.practical

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * リスニング再生の1単位を表すデータクラス。
 * 1つのスクリプト行が複数の音声セグメントやポーズに分解される場合があります。
 */
data class ListeningTtsSegment(
    val id: Int,             // 行のインデックス（ハイライト表示用ID）
    val speaker: String?,    // 話者ラベル（プロファイル選択用）
    val displayText: String, // 画面表示用テキスト（ラベル等を含む全文）
    val speakText: String,   // 実際に読み上げるテキスト（ラベル等を含まない）
    val pauseMs: Long = 0L,  // このセグメントでの無音時間
    val isQuestionCue: Boolean = false // "Question." 読み上げ用の目印
)

/**
 * 実践リスニング問題専用のTTSコントローラ。
 * 台本の解析、話者ごとのパラメータ切替、規定回数再生、およびハイライト用の状態通知を管理します。
 */
class PracticalListeningTtsController(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // リピート再生用のRunnable
    private val repeatRunnable = Runnable { playNext() }

    // 外部から設定するコールバック
    var onSegmentStart: (Int) -> Unit = {}
    var onSegmentDone: (Int) -> Unit = {}
    var onComplete: () -> Unit = {}
    var onError: () -> Unit = {}

    // 内部再生状態
    private var segments = listOf<ListeningTtsSegment>()
    private var currentGrade: Int = 3
    private var repeatCount: Int = 1
    private var currentRepeat = 0
    private var currentIndex = 0
    private var isStopped = false

    /**
     * 話者ごとの音声設定プロファイル
     */
    data class SpeakerProfile(
        val pitch: Float,
        val rateMultiplier: Float,
        val locale: Locale = Locale.US
    )

    private val speakerProfiles = mapOf(
        "Girl" to SpeakerProfile(pitch = 1.3f, rateMultiplier = 1.05f),
        "Boy" to SpeakerProfile(pitch = 1.15f, rateMultiplier = 1.05f),
        "Woman" to SpeakerProfile(pitch = 0.95f, rateMultiplier = 0.95f),
        "Man" to SpeakerProfile(pitch = 0.75f, rateMultiplier = 0.95f),
        "Teacher" to SpeakerProfile(pitch = 1.0f, rateMultiplier = 0.95f),
        "Mother" to SpeakerProfile(pitch = 0.95f, rateMultiplier = 0.95f),
        "Father" to SpeakerProfile(pitch = 0.8f, rateMultiplier = 0.95f),
        "Student" to SpeakerProfile(pitch = 1.1f, rateMultiplier = 1.0f),
        "Clerk" to SpeakerProfile(pitch = 1.0f, rateMultiplier = 1.0f),
        "Announcer" to SpeakerProfile(pitch = 1.0f, rateMultiplier = 1.05f),
        "Narrator" to SpeakerProfile(pitch = 1.0f, rateMultiplier = 1.0f),
        "Question" to SpeakerProfile(pitch = 1.0f, rateMultiplier = 1.0f)
    )

    private val defaultProfile = SpeakerProfile(pitch = 1.0f, rateMultiplier = 1.0f)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
            
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isReady = true
                setupProgressListener()
                onInitComplete(true)
            } else {
                onInitComplete(false)
            }
        } else {
            Log.e("PracticalListeningTts", "TTS Initialization failed")
            onInitComplete(false)
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null && utteranceId.startsWith("seg_")) {
                    // seg_{id}_... 形式から id を抽出して通知
                    val id = utteranceId.substringAfter("seg_").substringBefore("_").toIntOrNull() ?: -1
                    mainHandler.post { onSegmentStart(id) }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && utteranceId.startsWith("seg_")) {
                    val id = utteranceId.substringAfter("seg_").substringBefore("_").toIntOrNull() ?: -1
                    mainHandler.post { onSegmentDone(id) }
                }
                // 次のセグメントへ
                mainHandler.post { playNext() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("PracticalListeningTts", "TTS Error on: $utteranceId")
                mainHandler.post { onError() }
                mainHandler.post { playNext() }
            }
        })
    }

    /**
     * 台本テキストを解析し、再生用セグメントのリストを生成します。
     */
    fun parseScript(script: String): List<ListeningTtsSegment> {
        val result = mutableListOf<ListeningTtsSegment>()
        val lines = script.lines()

        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                // 空行は500msのポーズとして扱う
                result.add(ListeningTtsSegment(index, null, "", "", pauseMs = 500L))
                continue
            }

            if (trimmedLine.startsWith("Question:", ignoreCase = true)) {
                // Question行の特殊パース
                val questionText = trimmedLine.substringAfter(":").trim()
                // 1. 設問前のポーズ
                result.add(ListeningTtsSegment(index, null, trimmedLine, "", pauseMs = 1000L))
                // 2. "Question." と発音
                result.add(ListeningTtsSegment(index, "Question", trimmedLine, "Question.", isQuestionCue = true))
                // 3. 発話後の短いポーズ
                result.add(ListeningTtsSegment(index, null, trimmedLine, "", pauseMs = 800L))
                // 4. 実際の設問本文
                result.add(ListeningTtsSegment(index, "Question", trimmedLine, questionText))
            } else if (trimmedLine.contains(":")) {
                // 話者ラベルあり行 (Speaker: Text)
                val label = trimmedLine.substringBefore(":").trim()
                val body = trimmedLine.substringAfter(":").trim()
                result.add(ListeningTtsSegment(index, label, trimmedLine, body))
            } else {
                // ラベルなし行
                result.add(ListeningTtsSegment(index, "Narrator", trimmedLine, trimmedLine))
            }
        }
        return result
    }

    /**
     * 台本を再生します。級による速度・回数制御を自動で行います。
     */
    fun play(script: String, grade: Int) {
        if (!isReady) return
        stopPlaybackOnly()
        
        this.segments = parseScript(script)
        this.currentGrade = grade
        this.repeatCount = if (grade <= 3) 2 else 1
        this.currentRepeat = 0
        this.currentIndex = 0
        isStopped = false
        
        playNext()
    }

    private fun stopPlaybackOnly() {
        isStopped = true
        mainHandler.removeCallbacks(repeatRunnable)
        tts?.stop()
    }

    private fun playNext() {
        if (isStopped) return
        
        if (currentIndex < segments.size) {
            val segment = segments[currentIndex]
            currentIndex++
            executeSegment(segment)
        } else {
            // 全セグメント終了後、リピート判定
            currentRepeat++
            if (currentRepeat < repeatCount) {
                currentIndex = 0
                // 周回間に長めのポーズを置いてから再開
                mainHandler.postDelayed(repeatRunnable, 2000L)
            } else {
                // 全ての再生が完了
                mainHandler.post { onComplete() }
            }
        }
    }

    private fun baseSpeechRateForGrade(grade: Int): Float {
        return when (grade) {
            1 -> 0.75f
            2 -> 0.85f
            else -> 1.0f
        }
    }

    private fun executeSegment(segment: ListeningTtsSegment) {
        val ttsInstance = tts ?: return
        
        // ポーズ（無音）セグメントの場合
        if (segment.pauseMs > 0) {
            val utteranceId = "seg_${segment.id}_pause_${System.currentTimeMillis()}"
            ttsInstance.playSilentUtterance(segment.pauseMs, TextToSpeech.QUEUE_FLUSH, utteranceId)
            return
        }

        // 音声セグメントの場合
        val profile = speakerProfiles[segment.speaker] ?: defaultProfile
        
        // 級によるベース速度の決定
        val baseRate = baseSpeechRateForGrade(currentGrade)
        
        // パラメータ適用
        ttsInstance.setPitch(profile.pitch)
        ttsInstance.setSpeechRate(baseRate * profile.rateMultiplier)
        ttsInstance.setLanguage(profile.locale)

        // 再生実行
        val utteranceId = "seg_${segment.id}_text_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        ttsInstance.speak(segment.speakText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * 再生を即時停止します。
     */
    fun stop() {
        stopPlaybackOnly()
    }

    /**
     * リソースを解放します。
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
