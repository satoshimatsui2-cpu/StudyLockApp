package com.stulab.studylockapp.ui.alert

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stulab.studylockapp.R

/**
 * ダイアログ表示を補助するクラス
 */
object AppDialogHelper {
    fun showInfo(
        context: Context,
        title: String,
        message: String,
        positiveText: String,
        onPositive: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onPositive?.invoke() }
            .show()
    }

    fun showConfirm(
        context: Context,
        title: String,
        message: String,
        positiveText: String,
        negativeText: String,
        onPositive: () -> Unit,
        onNegative: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onPositive() }
            .setNegativeButton(negativeText) { _, _ -> onNegative?.invoke() }
            .show()
    }

    /**
     * 発音セッション未完了時のダイアログを表示する
     */
    fun showPronunciationIncomplete(
        context: Context,
        unclearedCount: Int,
        onContinue: () -> Unit,
        onLater: () -> Unit,
        onCancel: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_pronunciation_incomplete, null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setOnCancelListener { onCancel() }
            .create()

        // Window背景を透明に設定 (Cardの角丸を正しく表示するため)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val textMessage = view.findViewById<TextView>(R.id.text_dialog_message)
        val btnContinue = view.findViewById<MaterialButton>(R.id.button_continue)
        val btnLater = view.findViewById<MaterialButton>(R.id.button_later)

        // 本文の組み立て (数字部分を強調)
        val rawMessage = "あと${unclearedCount}語残っています。このまま続けて挑戦しますか？"
        val spannable = SpannableString(rawMessage)
        val start = rawMessage.indexOf(unclearedCount.toString())
        if (start != -1) {
            val end = start + unclearedCount.toString().length
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.mustard_primary)),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        textMessage.text = spannable

        btnContinue.setOnClickListener {
            dialog.dismiss()
            onContinue()
        }

        btnLater.setOnClickListener {
            dialog.dismiss()
            onLater()
        }

        dialog.show()
    }
}
