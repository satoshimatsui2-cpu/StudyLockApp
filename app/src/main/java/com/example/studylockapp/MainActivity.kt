package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // レイアウトは activity_main.xml

        // 1) ボタン参照を取得（XML の ID に合わせる）
        val buttonToLearning: Button = findViewById(R.id.button_to_learning)

        // 2) クリックリスナーで LearningActivity を起動
        buttonToLearning.setOnClickListener {
            val intent = Intent(this, LearningActivity::class.java)
            startActivity(intent)
        }
    }
}