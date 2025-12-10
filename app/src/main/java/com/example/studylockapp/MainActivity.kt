package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.data.CsvImporter
import com.example.studylockapp.data.AppDatabase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerGradeTop: Spinner
    private lateinit var buttonToLearning: Button
    private var selectedGradeValue: String? = null

    // 表示名 → 内部値のマッピング
    private val gradeMap = mapOf(
        "4級" to "4",
        "3級" to "3",
        "準2級" to "2.5",
        "2級" to "2",
        "準1級" to "1.5",
        "1級" to "1"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            Log.d("CSV_IMPORT", "start import")
            CsvImporter.importIfNeeded(this@MainActivity)

            // 件数をログに出す
            val count = AppDatabase.getInstance(this@MainActivity)
                .wordDao().getAll().size
            Log.d("CSV_IMPORT", "words count=$count")
        }

        spinnerGradeTop = findViewById(R.id.spinner_grade_top)
        buttonToLearning = findViewById(R.id.button_to_learning)
        // 他のボタンやポイント表示などは既存のまま

        // 初期は無効
        buttonToLearning.isEnabled = false

        spinnerGradeTop.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val label = parent.getItemAtPosition(position)?.toString() ?: "選択してください"
                selectedGradeValue = gradeMap[label] // 該当しない場合は null
                buttonToLearning.isEnabled = (selectedGradeValue != null)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                selectedGradeValue = null
                buttonToLearning.isEnabled = false
            }
        }

        // 学習画面へ
        buttonToLearning.setOnClickListener {
            val gradeSelected = selectedGradeValue ?: return@setOnClickListener
            val intent = Intent(this, LearningActivity::class.java)
            intent.putExtra("gradeFilter", gradeSelected)
            startActivity(intent)
        }

        // 一覧画面へ（ある場合はそのまま）
        val buttonToList: Button = findViewById(R.id.button_to_list)
        buttonToList.setOnClickListener {
            startActivity(Intent(this, WordListActivity::class.java))
        }
    }
}