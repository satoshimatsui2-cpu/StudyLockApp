package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.StudyHistoryRepository
import com.example.studylockapp.service.NotificationPermissionHelper
import com.example.studylockapp.ui.GradeBottomSheet
import com.example.studylockapp.ui.LearningHistoryActivity
import com.example.studylockapp.ui.PointHistoryActivity
import com.example.studylockapp.ui.alert.AppDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * アプリ起動時のメイン画面。
 * 級選択、ポイント履歴、管理者設定、学習開始の導線を管理します。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var appSettings: AppSettings
    private lateinit var pointManager: PointManager
    private lateinit var notificationHelper: NotificationPermissionHelper
    
    private var hasShownTargetGradeSetupAlert = false
    private var hasShownNotificationPrompt = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-Edgeを有効化して、背景をステータスバー領域まで広げる
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        appSettings = AppSettings(this)
        pointManager = PointManager(this)
        notificationHelper = NotificationPermissionHelper(this)

        // アプリロック V2 への移行処理
        migrateAppLockV2()

        // システムバーのインセット（ステータスバー等）に合わせてコンテンツのパディングを調整
        val rootLayout = findViewById<View>(R.id.root_layout_main)
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                insets
            }
        }

        setupGradeSection()
        setupLearningStart()
        setupPointHistoryNavigation()
        setupAdminSettingsNavigation()
        setupLearningHistoryNavigation()
        setupCharacterAndFriendNavigation()
        updatePointDisplay()
    }

    /**
     * キャラ選択とフレンド画面への遷移セットアップ
     */
    private fun setupCharacterAndFriendNavigation() {
        findViewById<View>(R.id.button_to_friends)?.setOnClickListener {
            startActivity(Intent(this, com.example.studylockapp.ui.FriendConnectionActivity::class.java))
        }
        findViewById<View>(R.id.button_to_characters)?.setOnClickListener {
            startActivity(Intent(this, com.example.studylockapp.ui.CharacterSelectActivity::class.java))
        }
    }

    /**
     * アプリロック V2 への移行処理 (1回のみ)
     * 旧マスタースイッチがOFFだった場合、既存のロック対象をすべて解除する。
     */
    private fun migrateAppLockV2() {
        if (appSettings.isMigratedAppLockV2()) return

        val oldEnabled = appSettings.isAppLockEnabled()
        if (!oldEnabled) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@MainActivity)
                db.lockedAppDao().disableAllLocks()
            }
        }
        // 移行後は内部的に true 扱い（UIでは非表示）
        appSettings.setAppLockEnabled(true)
        appSettings.setMigratedAppLockV2(true)
    }

    override fun onResume() {
        super.onResume()
        
        // 優先度1: 目標級が未設定の場合、一度だけアラートを表示
        if (!appSettings.isTargetLearningGradeSet && !hasShownTargetGradeSetupAlert) {
            hasShownTargetGradeSetupAlert = true
            showTargetGradeSetupAlert()
        } 
        // 優先度2: 通知許可がオフの場合、一度だけ誘導を表示 (目標級アラートと重ならないように)
        else if (!hasShownNotificationPrompt) {
            hasShownNotificationPrompt = true
            notificationHelper.checkAndPromptNotification()
        }

        // 最終アクティブ更新 (6時間以上の間隔を空ける)
        val now = System.currentTimeMillis()
        val sixHoursMillis = 6 * 60 * 60 * 1000L
        if (now - appSettings.lastActiveUpdateMillis > sixHoursMillis) {
            lifecycleScope.launch {
                StudyHistoryRepository.updateLastActiveStatus {
                    // サーバー書き込み成功時のみ、ローカルの次回判定用時刻を更新
                    appSettings.lastActiveUpdateMillis = now
                }
            }
        }

        updateGradeDisplay()
        updatePointDisplay()
        updateQuotaDisplay()
    }

    /**
     * 本日のノルマ残り表示を更新
     */
    private fun updateQuotaDisplay() {
        val textGoalNew = findViewById<TextView>(R.id.text_goal_new) ?: return
        val textGoalReview = findViewById<TextView>(R.id.text_goal_review) ?: return
        val textGoalReviewTotal = findViewById<TextView>(R.id.text_goal_review_total) ?: return

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@MainActivity)
            val cal = Calendar.getInstance()
            
            // 今日の開始時刻 (00:00:00.000)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis

            // 今日の終了時刻 (23:59:59.999)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val endOfDay = cal.timeInMillis

            val (newDone, reviewRemainingNow, reviewRemainingToday) = withContext(Dispatchers.IO) {
                val startedToday = db.wordMasteryDao().countStartedNewWordsToday(startOfDay)
                
                val currentGrade = appSettings.safeLearningGrade.toIntOrNull() ?: 3
                val includeOthers = if (appSettings.includeOtherGrades) 1 else 0
                val isSilent = if (appSettings.silentMode == com.example.studylockapp.data.SilentMode.ON) 1 else 0
                
                val remainingNow = db.wordMasteryDao().countRemainingReviewsAvailable(
                    now = System.currentTimeMillis(),
                    currentGrade = currentGrade,
                    includeOtherGrades = includeOthers,
                    isSilentMode = isSilent
                )

                val remainingToday = db.wordMasteryDao().countRemainingReviewsAvailable(
                    now = endOfDay,
                    currentGrade = currentGrade,
                    includeOtherGrades = includeOthers,
                    isSilentMode = isSilent
                )

                Triple(startedToday, remainingNow, remainingToday)
            }

            val target = appSettings.dailyNewWordTarget
            val newRemaining = (target - newDone).coerceAtLeast(0)

            textGoalNew.text = newRemaining.toString()
            textGoalReview.text = reviewRemainingNow.toString()
            textGoalReviewTotal.text = " / $reviewRemainingToday"
        }
    }

    /**
     * 目標級設定を促すアラートを表示
     */
    private fun showTargetGradeSetupAlert() {
        AppDialogHelper.showConfirm(
            context = this,
            title = "目標設定が必要です",
            message = "学習を始める前に、管理者設定から目標とする級を設定してください。",
            positiveText = "設定へ",
            negativeText = "あとで",
            onPositive = {
                startActivity(Intent(this, AdminSettingsActivity::class.java))
            }
        )
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
        val goalPrefixText = findViewById<TextView>(R.id.text_goal_prefix)

        if (gradeButton != null && goalPrefixText != null) {
            // 学習中の級を表示
            val learningDisplay = GradeUtils.toDisplay(appSettings.safeLearningGrade)
            gradeButton.text = learningDisplay

            // ポイント計算の基準となる目標級を表示
            if (appSettings.isTargetLearningGradeSet) {
                val targetDisplay = GradeUtils.toDisplay(appSettings.targetLearningGrade)
                goalPrefixText.text = "目指せ $targetDisplay"
            } else {
                goalPrefixText.text = "目標：未設定"
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

        // ポイント表示ボタン
        findViewById<View>(R.id.button_to_point_history)?.setOnClickListener {
            openPointHistory()
        }
    }

    /**
     * 学習履歴画面への遷移セットアップ
     */
    private fun setupLearningHistoryNavigation() {
        findViewById<View>(R.id.button_to_learning_history)?.setOnClickListener {
            startActivity(Intent(this, LearningHistoryActivity::class.java))
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
        // TOP画面は「ポイント」というラベルのみ表示するため、数値の更新処理は不要
    }
}
