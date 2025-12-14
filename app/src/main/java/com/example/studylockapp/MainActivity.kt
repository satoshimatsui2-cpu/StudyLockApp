package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.CsvImporter
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    private lateinit var spinnerGradeTop: Spinner
    private lateinit var buttonToLearning: Button
    private lateinit var buttonAdminSettings: Button

    // ★追加：TOPのポイント表示
    private lateinit var textPointsTop: TextView
    private lateinit var textPointStatsTop: TextView

    private var selectedGradeValue: String? = null

    // 表示名 → 内部値のマッピング
    private val gradeMap = mapOf(
        "5級" to "5",
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

            val count = AppDatabase.getInstance(this@MainActivity)
                .wordDao().getAll().size
            Log.d("CSV_IMPORT", "words count=$count")

            // CSVインポート後にも一応更新
            updatePointView()
        }

        spinnerGradeTop = findViewById(R.id.spinner_grade_top)
        buttonToLearning = findViewById(R.id.button_to_learning)
        buttonAdminSettings = findViewById(R.id.button_admin_settings)

        // ★追加：TextView取得
        textPointsTop = findViewById(R.id.text_points_top)
        textPointStatsTop = findViewById(R.id.text_point_stats_top)

        // 初期は無効
        buttonToLearning.isEnabled = false

        spinnerGradeTop.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val label = parent.getItemAtPosition(position)?.toString() ?: "選択してください"
                selectedGradeValue = gradeMap[label]
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

        // 一覧画面へ
        val buttonToList: Button = findViewById(R.id.button_to_list)
        buttonToList.setOnClickListener {
            startActivity(Intent(this, WordListActivity::class.java))
        }

        // 管理者設定へ
        buttonAdminSettings.setOnClickListener {
            startActivity(Intent(this, AdminSettingsActivity::class.java))
        }

        // ★初回表示
        updatePointView()
    }

    override fun onResume() {
        super.onResume()
        // ★初回：タイムゾーン未選択ならセットアップ画面へ
        val settings = com.example.studylockapp.data.AppSettings(this)
        if (!settings.hasChosenTimeZone()) {
            startActivity(android.content.Intent(this, com.example.studylockapp.ui.setup.TimeZoneSetupActivity::class.java))
            return
        }
        // ★学習画面から戻ったタイミングで更新される
        updatePointView()
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPointsTop.text = "ポイント: $total"

        lifecycleScope.launch {
            updatePointStats()
        }
    }

    /** 今日 / 前日比 */
    private suspend fun updatePointStats() {
        val db = AppDatabase.getInstance(this@MainActivity)
        val histDao = db.pointHistoryDao()

        val today = LocalDate.now().toEpochDay()
        val yesterday = today - 1
        val todaySum = histDao.getSumByDate(today)
        val yesterdaySum = histDao.getSumByDate(yesterday)
        val diff = todaySum - yesterdaySum
        val diffSign = if (diff >= 0) "+" else "-"
        val diffAbs = abs(diff)

        textPointStatsTop.text = "今日: $todaySum / 前日比: $diffSign$diffAbs"
    }
}