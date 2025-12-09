package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.CsvImporter   // ★ 追加
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 画面遷移ボタン
        val buttonToLearning: Button = findViewById(R.id.button_to_learning)
        buttonToLearning.setOnClickListener {
            startActivity(Intent(this, LearningActivity::class.java))
        }
        val buttonToList: Button = findViewById(R.id.button_to_list)
        buttonToList.setOnClickListener {
            startActivity(Intent(this, WordListActivity::class.java))
        }

        // ★ CSV を初回だけ DB に取り込む
        lifecycleScope.launch {
            CsvImporter.importIfNeeded(this@MainActivity)

            // 取り込み後の中身を確認したければログに出す
            val db = AppDatabase.getInstance(this@MainActivity)
            val all = db.wordDao().getAll()
            all.forEach { word ->
                Log.d("DB_TEST", "${word.no} ${word.word} ${word.japanese}")
            }
        }
    }
}