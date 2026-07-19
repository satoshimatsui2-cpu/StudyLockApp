package com.stulab.studylockapp.ui.alert

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
}
