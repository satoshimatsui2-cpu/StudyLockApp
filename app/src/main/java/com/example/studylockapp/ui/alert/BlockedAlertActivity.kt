package com.example.studylockapp.ui.alert

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class BlockedAlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MaterialAlertDialogBuilder(this)
            .setTitle("制限されています")
            .setMessage("テザリング機能は使用できません。\nこちらのアラート解除は保護者の方へ確認してください。")
            .setPositiveButton("確認") { _, _ ->
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .show()
    }
}
