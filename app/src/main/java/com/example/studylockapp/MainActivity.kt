package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.ui.GradeBottomSheet
import com.example.studylockapp.ui.PointHistoryActivity

/**
 * アプリ起動時のメイン画面。
 * 級選択、ポイント履歴、学習開始の導線を管理します。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appSettings = AppSettings(this)

        setupGradeSection()
        setupLearningStart()
        setupPointHistoryNavigation()
    }

    /**
     * 級選択に関連するUIのセットアップ
     */
    private fun setupGradeSection() {
        val gradeButton = findViewById<TextView>(R.id.spinner_grade_top)
        val targetGradeText = findViewById<TextView>(R.id.text_target_grade)

        // 初期表示の反映
        updateGradeDisplay(gradeButton, targetGradeText)

        // 級選択ボタンのクリックリスナー
        gradeButton.setOnClickListener {
            // GradeBottomSheetを表示
            val bottomSheet = GradeBottomSheet { selectedGrade ->
                // 選択された級をAppSettingsに保存
                appSettings.currentLearningGrade = selectedGrade
                // 保存された級に基づいて表示を更新
                updateGradeDisplay(gradeButton, targetGradeText)
            }
            bottomSheet.show(supportFragmentManager, "GradeBottomSheet")
        }
    }

    /**
     * 表示されている級のテキストを更新
     */
    private fun updateGradeDisplay(gradeButton: TextView, targetGradeText: TextView) {
        val gradeValue = appSettings.safeLearningGrade
        val displayStr = GradeUtils.toDisplay(gradeValue)

        gradeButton.text = displayStr
        targetGradeText.text = "目標：$displayStr"
    }

    /**
     * 学習開始ボタンのセットアップ
     */
    private fun setupLearningStart() {
        val startButton = findViewById<TextView>(R.id.button_to_learning)
        startButton.setOnClickListener {
            startActivity(Intent(this, LearningActivity::class.java))
        }
    }

    /**
     * ポイント履歴画面への遷移セットアップ
     */
    private fun setupPointHistoryNavigation() {
        val openPointHistory = {
            startActivity(Intent(this, PointHistoryActivity::class.java))
        }

        // ポイントボタン
        findViewById<View>(R.id.button_to_point_history).setOnClickListener {
            openPointHistory()
        }

        // ポイント表示カード
        findViewById<View>(R.id.card_to_point_history).setOnClickListener {
            openPointHistory()
        }
    }
}
