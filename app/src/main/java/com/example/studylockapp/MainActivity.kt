package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.ui.GradeBottomSheet
import com.example.studylockapp.ui.PointHistoryActivity

/**
 * アプリ起動時のメイン画面。
 * 級選択、ポイント履歴、管理者設定、学習開始の導線を管理します。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var appSettings: AppSettings
    private lateinit var pointManager: PointManager
    private var hasShownTargetGradeSetupAlert = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appSettings = AppSettings(this)
        pointManager = PointManager(this)

        setupGradeSection()
        setupLearningStart()
        setupPointHistoryNavigation()
        setupAdminSettingsNavigation()
        updatePointDisplay()
    }

    override fun onResume() {
        super.onResume()
        
        // 目標級が未設定の場合、一度だけアラートを表示
        if (!appSettings.isTargetLearningGradeSet && !hasShownTargetGradeSetupAlert) {
            hasShownTargetGradeSetupAlert = true
            showTargetGradeSetupAlert()
        }

        updateGradeDisplay()
        updatePointDisplay()
    }

    /**
     * 目標級設定を促すアラートを表示
     */
    private fun showTargetGradeSetupAlert() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("目標設定が必要です")
            .setMessage("学習を始める前に、管理者設定から目標とする級を設定してください。")
            .setPositiveButton("設定へ") { _, _ ->
                startActivity(Intent(this, AdminSettingsActivity::class.java))
            }
            .setNegativeButton("あとで", null)
            .show()
    }

    /**
     * 級選択に関連するUIのセットアップ
     */
    private fun setupGradeSection() {
        val gradeButton = findViewById<TextView>(R.id.spinner_grade_top)
        
        if (gradeButton != null) {
            // 級選択ボタンのクリックリスナー
            gradeButton.setOnClickListener {
                // GradeBottomSheetを表示
                val bottomSheet = GradeBottomSheet { selectedGrade ->
                    // 選択された「学習級」を保存
                    appSettings.currentLearningGrade = selectedGrade
                    // 表示を更新
                    updateGradeDisplay()
                }
                bottomSheet.show(supportFragmentManager, "GradeBottomSheet")
            }
        }
    }

    /**
     * 表示されている級のテキストを更新
     */
    private fun updateGradeDisplay() {
        val gradeButton = findViewById<TextView>(R.id.spinner_grade_top)
        val targetGradeText = findViewById<TextView>(R.id.text_target_grade)

        if (gradeButton != null && targetGradeText != null) {
            // 学習中の級を表示
            val learningDisplay = GradeUtils.toDisplay(appSettings.safeLearningGrade)
            gradeButton.text = learningDisplay

            // ポイント計算の基準となる目標級を表示
            targetGradeText.text = if (appSettings.isTargetLearningGradeSet) {
                val targetDisplay = GradeUtils.toDisplay(appSettings.targetLearningGrade)
                "目標：$targetDisplay"
            } else {
                "目標：未設定"
            }
        }
    }

    /**
     * 学習開始ボタンのセットアップ
     */
    private fun setupLearningStart() {
        val startButton = findViewById<TextView>(R.id.button_to_learning)
        startButton?.setOnClickListener {
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
        findViewById<View>(R.id.button_to_point_history)?.setOnClickListener {
            openPointHistory()
        }

        // ポイント表示カード
        findViewById<View>(R.id.card_to_point_history)?.setOnClickListener {
            openPointHistory()
        }
    }

    /**
     * 管理者設定画面への遷移セットアップ
     */
    private fun setupAdminSettingsNavigation() {
        findViewById<View>(R.id.button_admin_settings_top)?.setOnClickListener {
            startActivity(Intent(this, AdminSettingsActivity::class.java))
        }
    }

    /**
     * 保有ポイントの表示を更新
     */
    private fun updatePointDisplay() {
        val totalPoints = pointManager.getTotal()
        findViewById<TextView>(R.id.text_points_top)?.text = "保有ポイント: $totalPoints"
    }
}
