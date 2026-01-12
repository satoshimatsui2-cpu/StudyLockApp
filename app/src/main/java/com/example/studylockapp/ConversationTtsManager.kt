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
import java.util.regex.Pattern

/**
 * 1行ごとの読み上げデータを保持するクラス
 */
data class TtsSpeechLine(
    val text: String,
    val pitch: Float,
    val preDelayMs: Long
)

class ConversationTtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val speechQueue: Queue<TtsSpeechLine> = LinkedList()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRate: Float = 1.0f
    private var basePitch: Float = 1.0f

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
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
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

            // Wait処理
            if (nextLine.text.isEmpty()) {
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

            // preDelay処理
            if (nextLine.preDelayMs > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    engine.playSilentUtterance(nextLine.preDelayMs, TextToSpeech.QUEUE_ADD, "${utteranceId}_silence")
                } else {
                    @Suppress("DEPRECATION")
                    engine.playSilence(nextLine.preDelayMs, TextToSpeech.QUEUE_ADD, null)
                }
            }

            // 読み上げ処理（最終防衛ラインとしてここでも不要文字を削除）
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

            val textToRead = nextLine.text
                .replace("\\", " ")
                .replace("¥", " ")
                .replace("￥", " ")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("p.m.", "PM", ignoreCase = true)
                .replace("a.m.", "AM", ignoreCase = true)
                .replace("P.E.", "PE", ignoreCase = true)

            engine.speak(textToRead, TextToSpeech.QUEUE_ADD, params, utteranceId)
        }
    }

    // ▼▼▼ 修正: データ解析時に改行コード(\n)を確実に削除する ▼▼▼
    private fun parseScript(script: String): List<TtsSpeechLine> {
        val result = mutableListOf<TtsSpeechLine>()

        // キーワード一覧
        val keywords = listOf("Wait:", "Question:", "Narrator:", "Man:", "Boy:", "Woman:", "Girl:")

        // 正規表現: キーワード〜次のキーワード直前までを取得
        val patternString = "(${keywords.joinToString("|")})(.*?)(?=${keywords.joinToString("|")}|$)"
        val pattern = Pattern.compile(patternString, Pattern.DOTALL)

        val matcher = pattern.matcher(script)

        while (matcher.find()) {
            val label = matcher.group(1)?.trim() ?: ""
            // ここで改行コードをスペースに置換してからトリムする
            val rawContent = matcher.group(2) ?: ""
            val content = rawContent.replace("\n", " ").replace("\\n", " ").trim()

            if (content.isEmpty() && label != "Wait:") continue

            when (label) {
                "Wait:" -> {
                    val ms = content.toLongOrNull() ?: 1000L
                    result.add(TtsSpeechLine("", 1.0f, ms))
                }
                "Question:" -> {
                    // Questionの場合は少し間をあける
                    result.add(TtsSpeechLine("Question", 1.0f, 1200L))
                    result.add(TtsSpeechLine(content, 1.0f, 1000L))
                }
                "Narrator:" -> result.add(TtsSpeechLine(content, 1.0f, 1000L))
                "Man:"      -> result.add(TtsSpeechLine(content, 0.6f, 600L))
                "Boy:"      -> result.add(TtsSpeechLine(content, 0.90f, 600L))
                "Woman:"    -> result.add(TtsSpeechLine(content, 1.15f, 600L))
                "Girl:"     -> result.add(TtsSpeechLine(content, 1.35f, 600L))
                else        -> result.add(TtsSpeechLine(content, 1.0f, 600L))
            }
        }

        // 正規表現にマッチしない（キーワードがない）データの場合の救済処置
        if (result.isEmpty() && script.isNotEmpty()) {
            // ここでも改行は消す
            val cleanScript = script.replace("\n", " ").replace("\\n", " ")
            result.add(TtsSpeechLine(cleanScript, 1.0f, 0L))
        }

        return result
    }
}