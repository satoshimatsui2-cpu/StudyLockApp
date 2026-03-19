package com.example.studylockapp.learning

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.studylockapp.LearningModes
import com.example.studylockapp.LegacyQuestionContext

/**
 * 従来の単語学習モードのUI描画を担当するクラス
 */
class LegacyQuestionRenderer(private val context: Context) {

    /**
     * 与えられたコンテキストに基づいてUIを更新する
     */
    fun render(
        ctx: LegacyQuestionContext,
        currentMode: String,
        views: RendererViews,
        speakAction: (String) -> Unit,
        applyTtsDrawableAction: (Boolean, Boolean) -> Unit
    ) {
        val isListeningMode = currentMode == LearningModes.LISTENING || currentMode == LearningModes.LISTENING_JP
        
        // 1. タイトルと本文の更新
        views.textQuestionTitle.text = ctx.title
        if (isListeningMode) {
            views.textQuestionBody.text = ""
            views.textQuestionBody.visibility = View.VISIBLE
        } else {
            views.textQuestionBody.text = ctx.body
            views.textQuestionBody.visibility = if (ctx.body.isEmpty()) View.GONE else View.VISIBLE
        }

        // 2. レイアウトスタイル（重力・サイズ）の設定
        if (currentMode == LearningModes.TEST_FILL_BLANK) {
            views.textQuestionBody.gravity = android.view.Gravity.START
            views.textQuestionBody.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            views.textQuestionBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
        } else {
            views.textQuestionBody.gravity = android.view.Gravity.CENTER
            views.textQuestionBody.textAlignment = View.TEXT_ALIGNMENT_CENTER
            views.textQuestionBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        }
        views.textQuestionBody.isSingleLine = false
        views.textQuestionBody.setHorizontallyScrolling(false)

        // 3. 選択肢ボタンの表示更新
        views.choiceButtons.forEachIndexed { _, btn ->
            btn.textSize = if (currentMode == LearningModes.EN_EN_1) 12f else 14f
            btn.visibility = View.GONE
            btn.text = ""
        }
        ctx.options.forEachIndexed { i, txt ->
            val btn = views.choiceButtons.getOrNull(i) ?: return@forEachIndexed
            btn.text = txt
            btn.visibility = View.VISIBLE
        }

        // 4. 自動再生トグルの表示制御
        views.buttonToggleAutoPlay.visibility = when (currentMode) {
            LearningModes.MEANING, LearningModes.EN_EN_1, LearningModes.EN_EN_2 -> View.VISIBLE
            else -> View.GONE
        }

        // 5. カバー（回答を隠す）の表示制御
        val allowHideInThisMode = currentMode != LearningModes.TEST_LISTEN_Q2 && currentMode != LearningModes.TEST_SORT
        val hasVisibleChoice = views.choiceButtons.any { it.visibility == View.VISIBLE }
        views.coverLayout.visibility = if (views.isHideChoicesChecked && allowHideInThisMode && hasVisibleChoice) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // 6. TTSアイコンの設定 (Activity側のapplyTtsDrawableを呼び出し)
        val showTtsIcon = currentMode == LearningModes.EN_EN_1 ||
                currentMode == LearningModes.EN_EN_2 ||
                currentMode == LearningModes.MEANING ||
                isListeningMode

        applyTtsDrawableAction(showTtsIcon, isListeningMode)

        if (showTtsIcon) {
            views.textQuestionBody.setOnClickListener {
                ctx.audioText?.let { speakAction(it) }
            }
        } else {
            views.textQuestionBody.setOnClickListener(null)
        }
    }

    /**
     * Rendererが必要とするView群を保持するデータクラス
     */
    data class RendererViews(
        val textQuestionTitle: TextView,
        val textQuestionBody: TextView,
        val choiceButtons: List<Button>,
        val buttonToggleAutoPlay: View,
        val coverLayout: View,
        val isHideChoicesChecked: Boolean
    )
}
