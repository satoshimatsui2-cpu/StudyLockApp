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
            engine.speak(nextLine.text, TextToSpeech.QUEUE_ADD, params, utteranceId)
        }
    }

    private fun parseScript(script: String): List<TtsSpeechLine> {
        val result = mutableListOf<TtsSpeechLine>()
        val lines = script.split("\n")

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // --- ラベルによる条件分岐 ---
            when {
                // ★パターン1: 「Question:」で始まる場合 -> "Question" と発声してから読む
                trimmedLine.startsWith("Question:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    // 1. "Question" (1.2秒待機後)
                    result.add(TtsSpeechLine("Question", 1.0f, 1200L))
                    // 2. 本文 (Questionと言った後、1.0秒待機)
                    result.add(TtsSpeechLine(content, 1.0f, 1000L))
                }

                // ★パターン2: 「Narrator:」で始まる場合 -> 普通に読む (説明文など)
                trimmedLine.startsWith("Narrator:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    // ナレーターの声は標準(1.0f)、会話の後なので少し間(1000ms)を開ける
                    result.add(TtsSpeechLine(content, 1.0f, 1000L))
                }

                // ★パターン3: 男性/男の子
                trimmedLine.startsWith("Man:") || trimmedLine.startsWith("Boy:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    result.add(TtsSpeechLine(content, 0.7f, 600L))
                }

                // ★パターン4: 女性/女の子
                trimmedLine.startsWith("Woman:") || trimmedLine.startsWith("Girl:") -> {
                    val content = trimmedLine.substringAfter(":").trim()
                    result.add(TtsSpeechLine(content, 1.4f, 600L))
                }

                // それ以外（ラベルなしなど）
                else -> {
                    result.add(TtsSpeechLine(trimmedLine, 1.0f, 600L))
                }
            }
        }
        return result
    }
}