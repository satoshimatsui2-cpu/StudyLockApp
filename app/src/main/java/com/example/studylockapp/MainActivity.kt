package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.util.Log           // 追加
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope  // 追加
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.WordEntity
import kotlinx.coroutines.launch  // 追加

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 画面遷移ボタン
        val buttonToLearning: Button = findViewById(R.id.button_to_learning)
        buttonToLearning.setOnClickListener {
            startActivity(Intent(this, LearningActivity::class.java))
        }

        // ★ Room の簡易テスト：サンプルデータ挿入→取得→ログ出力
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@MainActivity)
            val dao = db.wordDao()

            // サンプルデータ（同じ no でも OnConflictStrategy.REPLACE なら上書き）
            val sample = listOf(
                WordEntity(1, "4", "apple", "りんご", "noun", "word"),
                WordEntity(2, "3", "run", "走る", "verb", "word")
            )
            dao.insertAll(sample)

            val all = dao.getAll()
            all.forEach { word ->
                Log.d("DB_TEST", "${word.no} ${word.word} ${word.japanese}")
            }
        }
    }
}