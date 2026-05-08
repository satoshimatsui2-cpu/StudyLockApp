package com.example.studylockapp.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.databinding.ActivityAccessibilityDisclosureBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.studylockapp.R

class AccessibilityDisclosureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccessibilityDisclosureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccessibilityDisclosureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonAgree.setOnClickListener {
            // 同意してOS設定画面へ
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            finish()
        }

        binding.buttonCancel.setOnClickListener {
            // 同意せずに戻る
            finish()
        }
    }
}
