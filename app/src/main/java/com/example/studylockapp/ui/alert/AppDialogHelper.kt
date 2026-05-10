package com.example.studylockapp.ui.alert

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.studylockapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object AppDialogHelper {

    fun showInfo(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "OK",
        onPositive: (() -> Unit)? = null
    ) {
        showCustom(
            context = context,
            title = title,
            message = message,
            positiveText = positiveText,
            onPositive = { 
                it.dismiss()
                onPositive?.invoke()
            }
        )
    }

    fun showConfirm(
        context: Context,
        title: String,
        message: String,
        positiveText: String,
        negativeText: String? = "キャンセル",
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null
    ) {
        showCustom(
            context = context,
            title = title,
            message = message,
            positiveText = positiveText,
            negativeText = negativeText,
            onPositive = {
                it.dismiss()
                onPositive?.invoke()
            },
            onNegative = {
                it.dismiss()
                onNegative?.invoke()
            }
        )
    }

    fun showCustom(
        context: Context,
        title: String,
        message: String? = null,
        customView: View? = null,
        positiveText: String? = "OK",
        negativeText: String? = null,
        neutralText: String? = null,
        onPositive: ((AlertDialog) -> Unit)? = null,
        onNegative: ((AlertDialog) -> Unit)? = null,
        onNeutral: ((AlertDialog) -> Unit)? = null,
        cancelable: Boolean = true,
        onShow: ((AlertDialog, View) -> Unit)? = null
    ) {
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_app_message, null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(root)
            .setCancelable(cancelable)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        root.findViewById<TextView>(R.id.text_dialog_title).text = title
        
        val messageView = root.findViewById<TextView>(R.id.text_dialog_message)
        val scrollView = root.findViewById<View>(R.id.scroll_message)
        if (!message.isNullOrEmpty()) {
            messageView.text = message
            scrollView.visibility = View.VISIBLE
        } else {
            scrollView.visibility = View.GONE
        }

        val container = root.findViewById<FrameLayout>(R.id.container_custom_view)
        if (customView != null) {
            container.visibility = View.VISIBLE
            container.removeAllViews()
            if (customView.parent != null) {
                (customView.parent as ViewGroup).removeView(customView)
            }
            container.addView(customView)
        } else {
            container.visibility = View.GONE
        }

        val btnNegative = root.findViewById<MaterialButton>(R.id.button_dialog_negative)
        val btnPositive = root.findViewById<MaterialButton>(R.id.button_dialog_positive)
        val btnNeutral = root.findViewById<MaterialButton>(R.id.button_dialog_neutral)

        if (!negativeText.isNullOrEmpty()) {
            btnNegative.visibility = View.VISIBLE
            btnNegative.text = negativeText
            btnNegative.setOnClickListener {
                if (onNegative != null) onNegative(dialog) else dialog.dismiss()
            }
        } else {
            btnNegative.visibility = View.GONE
        }

        if (!positiveText.isNullOrEmpty()) {
            btnPositive.visibility = View.VISIBLE
            btnPositive.text = positiveText
            btnPositive.setOnClickListener {
                if (onPositive != null) onPositive(dialog) else dialog.dismiss()
            }
        } else {
            btnPositive.visibility = View.GONE
        }

        if (!neutralText.isNullOrEmpty()) {
            btnNeutral.visibility = View.VISIBLE
            btnNeutral.text = neutralText
            btnNeutral.setOnClickListener {
                if (onNeutral != null) onNeutral(dialog) else dialog.dismiss()
            }
        } else {
            btnNeutral.visibility = View.GONE
        }

        if (onShow != null) {
            dialog.setOnShowListener { onShow(dialog, root) }
        }

        dialog.show()
    }
}
