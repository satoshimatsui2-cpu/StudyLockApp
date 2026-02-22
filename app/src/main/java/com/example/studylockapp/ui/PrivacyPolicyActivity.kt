package com.example.studylockapp.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.R

class PrivacyPolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.privacy_policy_layout)

        // アクションバーに戻るボタンを設置（任意）
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "プライバシーポリシー"
    }

    // 戻るボタンの挙動
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}