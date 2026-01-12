package com.example.studylockapp

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.LinkedList
import java.util.Locale
import java.util.Queue

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
                playNextLine()
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

    private fun playNextLine() {
        val nextLine = speechQueue.poll() ?: return

        tts?.let { engine ->
            engine.setPitch(nextLine.pitch)
            val utteranceId = "id_${System.currentTimeMillis()}_${nextLine.hashCode()}"

            // テキストが空の場合は、Waitコマンドとして無音再生のみを行い、完了後に次へ進む
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

            // 通常の再生処理
            if (nextLine.preDelayMs > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    engine.playSilentUtterance(nextLine.preDelayMs, TextToSpeech.QUEUE_ADD, "${utteranceId}_silence")
                } else {
                    @Suppress("DEPRECATION")
                    engine.playSilence(nextLine.preDelayMs, TextToSpeech.QUEUE_ADD, null)
                }
            }

            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            val textToRead = nextLine.text
                .replace("p.m.", "PM", ignoreCase = true)
                .replace("a.m.", "AM", ignoreCase = true)
                .replace("P.E.", "PE", ignoreCase = true)
            engine.speak(textToRead, TextToSpeech.QUEUE_ADD, params, utteranceId)
        }
    }

    private fun parseScript(script: String): List<TtsSpeechLine> {
        val result = mutableListOf<TtsSpeechLine>()
        val lines = script.split("\n")

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // --- 話者・ラベルごとのピッチ設定 ---
            when {
                // パターン0: Wait (待機)
                trimmedLine.startsWith("Wait:") -> {
                    val ms = trimmedLine.substringAfter(":").trim().toLongOrNull() ?: 1000L
                    // テキストを空にすることで playNextLine で無音再生のみを行う
                    result.add(TtsSpeechLine("", 1.0f, ms))
                }

                // パターン1: Question (質問)
                trimmedLine.startsWith("Question:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    result.add(TtsSpeechLine("Question", 1.0f, 1200L))
                    result.add(TtsSpeechLine(content, 1.0f, 1000L))
                }

                // パターン2: Narrator (ナレーター)
                trimmedLine.startsWith("Narrator:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    result.add(TtsSpeechLine(content, 1.0f, 1000L))
                }

                // パターン3: Man (成人男性) -> かなり低い
                trimmedLine.startsWith("Man:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    result.add(TtsSpeechLine(content, 0.6f, 600L))
                }

                // パターン4: Boy (男の子) -> 少し低い〜中くらい
                trimmedLine.startsWith("Boy:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    result.add(TtsSpeechLine(content, 0.90f, 600L))
                }

                // パターン5: Woman (成人女性) -> 少し高い
                trimmedLine.startsWith("Woman:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    result.add(TtsSpeechLine(content, 1.15f, 600L))
                }

                // パターン6: Girl (女の子) -> かなり高い
                trimmedLine.startsWith("Girl:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    result.add(TtsSpeechLine(content, 1.35f, 600L))
                }

                // それ以外
                else -> {
                    result.add(TtsSpeechLine(trimmedLine, 1.0f, 600L))
                }
            }
        }
        return result
    }
}