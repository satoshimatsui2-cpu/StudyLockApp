package com.example.studylockapp

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 1行ごとの読み上げデータを保持するクラス
 */
data class TtsSpeechLine(
    val displayText: String, // 画面表示検索用（改行などを含む）
    val ttsText: String,     // 読み上げ用（記号などを除去）
    val pitch: Float,
    val preDelayMs: Long
)

class ConversationTtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val speechQueue: Queue<TtsSpeechLine> = LinkedList()
    private val mainHandler = Handler(Looper.getMainLooper())

    // 発話IDと表示テキストを紐付けるマップ
    private val utteranceTextMap = ConcurrentHashMap<String, String>()

    private var speechRate: Float = 1.0f
    private var basePitch: Float = 1.0f

    // 読み上げ開始時に呼ばれるリスナー
    var onSpeakListener: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "US English is not supported")
            } else {
                isTtsReady = true
                tts?.setSpeechRate(speechRate)
                setupListener()
            }
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            // ▼▼▼ 修正: 実際に音声が始まったタイミングでハイライト通知を送る ▼▼▼
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { id ->
                    val textToHighlight = utteranceTextMap[id]
                    if (!textToHighlight.isNullOrEmpty()) {
                        mainHandler.post {
                            onSpeakListener?.invoke(textToHighlight)
                        }
                    }
                }
            }
            // ▲▲▲ 修正ここまで ▲▲▲

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { utteranceTextMap.remove(it) }
                mainHandler.post {
                    playNextLine()
                }
            }

            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) {
                Log.e("TTS", "Error during playback: $utteranceId")
            }
        })
    }

    fun playScript(rawScript: String) {
        if (!isTtsReady) return
        stop()

        val lines = parseScript(rawScript)
        speechQueue.addAll(lines)
        playNextLine()
    }

    fun stop() {
        speechQueue.clear()
        utteranceTextMap.clear()
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }

    fun setSpeechRate(rate: Float) {
        this.speechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        this.basePitch = pitch
        tts?.setPitch(pitch)
    }

    private fun playNextLine() {
        val nextLine = speechQueue.poll() ?: return

        tts?.let { engine ->
            val effectivePitch = nextLine.pitch * basePitch
            engine.setSpeechRate(speechRate)
            engine.setPitch(effectivePitch)

            val utteranceId = "id_${System.currentTimeMillis()}_${nextLine.hashCode()}"

            // Wait処理 (テキストが空の場合は無音)
            if (nextLine.ttsText.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    engine.playSilentUtterance(nextLine.preDelayMs, TextToSpeech.QUEUE_ADD, utteranceId)
                } else {
                    @Suppress("DEPRECATION")
                    engine.playSilence(nextLine.preDelayMs, TextToSpeech.QUEUE_ADD, HashMap<String, String>().apply {
                        put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    })
                }
                return
            }

            // preDelay処理 (文頭の無音)
            if (nextLine.preDelayMs > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    engine.playSilentUtterance(nextLine.preDelayMs, TextToSpeech.QUEUE_ADD, "${utteranceId}_silence")
                } else {
                    @Suppress("DEPRECATION")
                    engine.playSilence(nextLine.preDelayMs, TextToSpeech.QUEUE_ADD, null)
                }
            }

            // マップに登録（onStartで取り出すため）
            utteranceTextMap[utteranceId] = nextLine.displayText

            // 読み上げ処理
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

            engine.speak(nextLine.ttsText, TextToSpeech.QUEUE_ADD, params, utteranceId)
        }
    }

    private fun parseScript(script: String): List<TtsSpeechLine> {
        val result = mutableListOf<TtsSpeechLine>()

        val keywords = listOf("Wait:", "Question:", "Narrator:", "Man:", "Boy:", "Woman:", "Girl:")
        val patternString = "(${keywords.joinToString("|")})(.*?)(?=${keywords.joinToString("|")}|$)"
        val pattern = Pattern.compile(patternString, Pattern.DOTALL)

        val matcher = pattern.matcher(script)

        while (matcher.find()) {
            val label = matcher.group(1)?.trim() ?: ""
            val rawContent = matcher.group(2) ?: ""

            // ▼▼▼ 修正: 表示用(改行維持) と 読み上げ用(改行削除) を分ける ▼▼▼
            // Activity側で replace("\\n", "\n") しているので、ここでも合わせる
            val displayText = rawContent.replace("\\n", "\n").trim()

            // TTS用は徹底的にクリーニング
            val ttsText = rawContent
                .replace("\\n", " ")
                .replace("\n", " ")
                .replace("\\", " ")
                .replace("¥", " ")
                .replace("￥", " ")
                .replace("\r", " ")
                .replace("p.m.", "PM", ignoreCase = true)
                .replace("a.m.", "AM", ignoreCase = true)
                .replace("P.E.", "PE", ignoreCase = true)
                .trim()

            if (ttsText.isEmpty() && label != "Wait:") continue

            when (label) {
                "Wait:" -> {
                    val ms = displayText.toLongOrNull() ?: 1000L
                    result.add(TtsSpeechLine("", "", 1.0f, ms))
                }
                "Question:" -> {
                    result.add(TtsSpeechLine("", "Question", 1.0f, 1200L))
                    result.add(TtsSpeechLine(displayText, ttsText, 1.0f, 1000L))
                }
                "Narrator:" -> result.add(TtsSpeechLine(displayText, ttsText, 1.0f, 1000L))
                "Man:"      -> result.add(TtsSpeechLine(displayText, ttsText, 0.6f, 600L))
                "Boy:"      -> result.add(TtsSpeechLine(displayText, ttsText, 0.90f, 600L))
                "Woman:"    -> result.add(TtsSpeechLine(displayText, ttsText, 1.15f, 600L))
                "Girl:"     -> result.add(TtsSpeechLine(displayText, ttsText, 1.35f, 600L))
                else        -> result.add(TtsSpeechLine(displayText, ttsText, 1.0f, 600L))
            }
        }

        if (result.isEmpty() && script.isNotEmpty()) {
            // 救済処置
            val disp = script.replace("\\n", "\n")
            val tts = script.replace("\\n", " ").replace("\n", " ")
            result.add(TtsSpeechLine(disp, tts, 1.0f, 0L))
        }

        return result
    }
}