package com.stulab.studylockapp.ui.alert

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class BlockedAlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppDialogHelper.showInfo(
            context = this,
            title = "制限されています",
            message = "テザリング機能は使用できません。\nこちらのアラート解除は保護者の方へ確認してください。",
            positiveText = "確認",
            onPositive = {
                finish()
            }
        )
    }
}
