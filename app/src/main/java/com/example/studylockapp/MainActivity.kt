package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * アプリ起動時のメイン画面。
 * activity_main.xml を表示し、学習画面への遷移を管理します。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 既存のレイアウトをそのまま使用
        setContentView(R.layout.activity_main)

        // STARTボタン（ID: button_to_learning）を取得してクリックリスナーを設定
        val startButton = findViewById<Button>(R.id.button_to_learning)
        startButton.setOnClickListener {
            // LearningActivity への遷移
            val intent = Intent(this, LearningActivity::class.java)
            startActivity(intent)
        }
    }
}
