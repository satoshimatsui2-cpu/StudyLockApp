package com.stulab.studylockapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.transition.TransitionManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.PointManager
import com.stulab.studylockapp.data.StudyHistoryRepository
import com.stulab.studylockapp.service.NotificationPermissionHelper
import com.stulab.studylockapp.ui.LearningHistoryActivity
import com.stulab.studylockapp.ui.PointHistoryActivity
import com.stulab.studylockapp.ui.alert.AppDialogHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.stulab.studylockapp.data.ChallengePointManager
import com.stulab.studylockapp.data.FcmTokenRepository
import com.stulab.studylockapp.data.ProgressRepository
import com.stulab.studylockapp.ui.GradeSelectionDialogFragment
import com.stulab.studylockapp.ui.ProgressMetricUiModel
import com.stulab.studylockapp.ui.TopProgressUiModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
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
    private lateinit var challengePointManager: ChallengePointManager
    private lateinit var notificationHelper: NotificationPermissionHelper
    private lateinit var progressRepository: ProgressRepository
    
    private var hasShownTargetGradeSetupAlert = false
    private var hasShownNotificationPrompt = false
    private var hasShownNamePrompt = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-Edgeを有効化して、背景をステータスバー領域まで広げる
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        appSettings = AppSettings(this)
        pointManager = PointManager(this)
        challengePointManager = ChallengePointManager(this)
        notificationHelper = NotificationPermissionHelper(this)
        progressRepository = ProgressRepository(AppDatabase.getInstance(this))

        // 匿名ログインの実行（Firestore連携に必須）
        ensureAuth()

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
        setupProgressAccordion()
        setupSpellingCheckStart()
        setupChallengePointDisplay()
        updatePointDisplay()
        updateProgressDisplay()
    }

    private fun setupChallengePointDisplay() {
        val cpText = findViewById<TextView>(R.id.text_cp_balance_top)
        val cpLayout = findViewById<View>(R.id.layout_cp_top)
        
        lifecycleScope.launch {
            challengePointManager.getCPFlow().collectLatest { cp ->
                cpText?.text = "$cp CP"
                cpLayout?.contentDescription = "チャレンジポイント：${cp}CP"
            }
        }
    }

    private fun ensureAuth() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnSuccessListener {
                syncFcmToken()
                syncCurrentCharacterId()
                lifecycleScope.launch {
                    StudyHistoryRepository.updateLastActiveStatus()
                }
            }
        } else {
            syncFcmToken()
            syncCurrentCharacterId()
        }
    }

    /**
     * 現在選択されているパートナーキャラクターIDをFirestoreに同期する
     */
    private fun syncCurrentCharacterId() {
        val charId = appSettings.selectedCharacterId
        lifecycleScope.launch {
            StudyHistoryRepository.syncSelectedCharacterId(charId)
        }
    }

    /**
     * FCMトークンを現在のユーザーUIDと紐づけて同期する
     */
    private fun syncFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                if (token != null) {
                    lifecycleScope.launch {
                        FcmTokenRepository.updateToken(token)
                    }
                }
            } else {
                Log.w("MainActivity", "FCM token fetch failed")
            }
        }
    }

    /**
     * キャラ選択とフレンド画面への遷移セットアップ
     */
    private fun setupCharacterAndFriendNavigation() {
        findViewById<View>(R.id.button_to_friends)?.setOnClickListener {
            startActivity(Intent(this, com.stulab.studylockapp.ui.FriendConnectionActivity::class.java))
        }
        findViewById<View>(R.id.button_to_characters)?.setOnClickListener {
            startActivity(Intent(this, com.stulab.studylockapp.ui.CharacterSelectActivity::class.java))
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
        
        var nextCheck = false
        
        // 1. 通知誘導
        if (!hasShownNotificationPrompt) {
            hasShownNotificationPrompt = true
            val prompted = notificationHelper.checkAndPromptNotification()
            if (!prompted) nextCheck = true
        } else {
            nextCheck = true
        }

        // 2. 名前登録 (通知のあと)
        if (nextCheck && appSettings.userName == null && !hasShownNamePrompt) {
            hasShownNamePrompt = true
            showNameRegistrationDialog()
            nextCheck = false
        }

        // 3. 目標級
        if (nextCheck && !appSettings.isTargetLearningGradeSet && !hasShownTargetGradeSetupAlert) {
            hasShownTargetGradeSetupAlert = true
            showTargetGradeSetupAlert()
        }

        // 管理者設定で級が変更された場合に、おすすめ学習数ダイアログを強制表示する
        val statePrefs = getSharedPreferences("app_state", MODE_PRIVATE)
        if (statePrefs.getBoolean("force_show_quota_dialog", false)) {
            statePrefs.edit().putBoolean("force_show_quota_dialog", false).apply()
            val targetRank = appSettings.targetLearningGrade.toIntOrNull() ?: 3
            showRecommendedWordCountDialog(targetRank, true)
        }

        // 最終アクティブ更新 (6時間以上の間隔を空ける)
        val now = System.currentTimeMillis()
        val sixHoursMillis = 6 * 60 * 60 * 1000L
        if (now - appSettings.lastActiveUpdateMillis > sixHoursMillis) {
            lifecycleScope.launch {
                StudyHistoryRepository.updateLastActiveStatus(appSettings.userName) {
                    // サーバー書き込み成功時のみ、ローカルの次回判定用時刻を更新
                    appSettings.lastActiveUpdateMillis = now
                }
            }
        }

        updateGradeDisplay()
        updatePointDisplay()
        updateQuotaDisplay()
        updateProgressDisplay()
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

            val (newDone, reviewRemainingNow, reviewTotalToday) = withContext(Dispatchers.IO) {
                val startedToday = db.wordMasteryDao().countStartedNewWordsToday(startOfDay)
                
                val currentGrade = appSettings.safeLearningGrade.toIntOrNull() ?: 3
                val includeOthers = if (appSettings.includeOtherGrades) 1 else 0
                val isSilent = if (appSettings.silentMode == com.stulab.studylockapp.data.SilentMode.ON) 1 else 0
                
                val remainingNow = db.wordMasteryDao().countRemainingReviewsAvailable(
                    now = System.currentTimeMillis(),
                    currentGrade = currentGrade,
                    includeOtherGrades = includeOthers,
                    isSilentMode = isSilent
                )

                // ★ 総数は常に当日期限のものすべて（isSilentMode = 0）
                val totalToday = db.wordMasteryDao().countRemainingReviewsAvailable(
                    now = endOfDay,
                    currentGrade = currentGrade,
                    includeOtherGrades = includeOthers,
                    isSilentMode = 0
                )

                Triple(startedToday, remainingNow, totalToday)
            }

            val target = appSettings.dailyNewWordTarget
            val newRemaining = (target - newDone).coerceAtLeast(0)

            textGoalNew.text = newRemaining.toString()
            textGoalReview.text = reviewRemainingNow.toString()
            textGoalReviewTotal.text = " / $reviewTotalToday"
        }
    }

    /**
     * 名前登録ダイアログを表示
     */
    private fun showNameRegistrationDialog() {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val inputLayout = TextInputLayout(this).apply {
            setPadding(padding, (8 * resources.displayMetrics.density).toInt(), padding, 0)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = "名前 (15文字以内)"
            // ボックス内の背景を白系に固定して視認性を確保
            boxBackgroundColor = androidx.core.content.ContextCompat.getColor(context, R.color.dialog_surface)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(15))
            maxLines = 1
            isSingleLine = true
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_main))
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle("あなたの名前")
            .setMessage("学習記録やフレンド通知に使用されます。これから目指す級と一緒に登録しましょう。")
            .setView(inputLayout)
            .setPositiveButton("登録") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    appSettings.userName = name
                    lifecycleScope.launch {
                        StudyHistoryRepository.updateLastActiveStatus(name)
                    }
                    Toast.makeText(this, "名前を登録しました", Toast.LENGTH_SHORT).show()
                    
                    // 名前登録後に目標級チェックを再度走らせるためにonResume相当の処理を継続
                    if (!appSettings.isTargetLearningGradeSet && !hasShownTargetGradeSetupAlert) {
                        hasShownTargetGradeSetupAlert = true
                        showTargetGradeSetupAlert()
                    }
                } else {
                    // 空なら再度表示 (再帰的だがDialogなので安全)
                    hasShownNamePrompt = false 
                    onResume()
                }
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 目標級設定を促すダイアログを表示
     * @param isChange 目標変更モードかどうか
     */
    /**
     * 目標級設定を促すリスト選択ダイアログを表示
     * @param isChange 目標変更モードかどうか
     */
    private fun showTargetGradeSetupAlert(isChange: Boolean = false) {
        val grades = (1..7).toList()
        val items = grades.map { GradeLabelFormatter.format(it) }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("これから目指す級を選択してください")
            .setItems(items) { dialog, which ->
                val selectedRank = grades[which]
                // 1. 目標級を保存
                appSettings.targetLearningGrade = selectedRank.toString()
                
                // 2. 初回設定（または未設定）時は現在の学習級もこれに合わせる
                if (!isChange && (appSettings.currentLearningGrade == "0" || appSettings.currentLearningGrade == "3")) {
                    appSettings.currentLearningGrade = selectedRank.toString()
                }

                // 3. UIの更新
                Toast.makeText(this, "${items[which]}を目標に設定しました", Toast.LENGTH_SHORT).show()
                updateGradeDisplay()
                updateQuotaDisplay()
                
                dialog.dismiss()

                // ★ おすすめ学習数の提案ダイアログを表示
                showRecommendedWordCountDialog(selectedRank, isChange)
            }
            .setCancelable(isChange) // 初回設定時は強制、変更時はキャンセル可能
            .show()
    }

    /**
     * 目標級に応じたおすすめ学習数を提案するダイアログを表示
     */
    private fun showRecommendedWordCountDialog(rank: Int, isChange: Boolean) {
        val recommended = when (rank) {
            1, 2 -> 5    // 5級, 4級
            3, 4 -> 10   // 3級, 準2級
            5 -> 15      // 2級
            6, 7 -> 20   // 準1級, 1級
            else -> 10
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recommended_quota, null)
        val textValue = dialogView.findViewById<TextView>(R.id.text_quota_value)
        val btnMinus = dialogView.findViewById<View>(R.id.btn_quota_minus)
        val btnPlus = dialogView.findViewById<View>(R.id.btn_quota_plus)
        val textMsg = dialogView.findViewById<TextView>(R.id.text_recommended_message)

        var currentVal = recommended
        textValue.text = currentVal.toString()
        
        val gradeLabel = GradeLabelFormatter.format(rank)
        textMsg.text = "${gradeLabel}合格に向けた、おすすめの1日の学習数です。調整も可能です。"

        btnMinus.setOnClickListener {
            if (currentVal > 1) {
                currentVal--
                textValue.text = currentVal.toString()
            }
        }
        btnPlus.setOnClickListener {
            if (currentVal < 100) {
                currentVal++
                textValue.text = currentVal.toString()
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("学習目標の設定")
            .setView(dialogView)
            .setPositiveButton("設定する") { _, _ ->
                appSettings.dailyNewWordTarget = currentVal
                Toast.makeText(this, "1日の目標を${currentVal}個に設定しました", Toast.LENGTH_SHORT).show()
                updateQuotaDisplay()
            }
            .setNegativeButton("おすすめ(${recommended}個)") { _, _ ->
                appSettings.dailyNewWordTarget = recommended
                Toast.makeText(this, "おすすめの${recommended}個に設定しました", Toast.LENGTH_SHORT).show()
                updateQuotaDisplay()
            }
            .setCancelable(isChange)
            .show()
    }

    /**
     * 級選択に関連するUIのセットアップ
     */
    private fun setupGradeSection() {
        val selectGradeButton = findViewById<MaterialButton>(R.id.button_select_grade)
        val goalStamp = findViewById<View>(R.id.layout_goal_stamp)
        
        selectGradeButton?.setOnClickListener {
            val dialog = GradeSelectionDialogFragment.newInstance { selectedGrade ->
                appSettings.currentLearningGrade = selectedGrade
                updateGradeDisplay()
                updateQuotaDisplay()
                updateProgressDisplay()
            }
            dialog.show(supportFragmentManager, "GradeSelectionDialog")
        }

        if (goalStamp != null) {
            // 合格スタンプ（目標級）のクリックリスナー
            goalStamp.setOnClickListener {
                showTargetGradeSetupAlert(isChange = true)
            }
        }
    }

    /**
     * 表示されている級のテキストを更新
     */
    private fun updateGradeDisplay() {
        val selectGradeButton = findViewById<MaterialButton>(R.id.button_select_grade)
        val goalPrefixText = findViewById<TextView>(R.id.text_goal_prefix)

        if (selectGradeButton != null && goalPrefixText != null) {
            // 学習中の級を表示
            val learningDisplay = GradeUtils.toDisplay(appSettings.safeLearningGrade, appSettings)
            selectGradeButton.text = learningDisplay
            selectGradeButton.contentDescription = "現在の出題レベルは${learningDisplay}。変更"

            // ポイント計算の基準となる目標級を表示
            if (appSettings.isTargetLearningGradeSet) {
                val targetDisplay = GradeUtils.toDisplay(appSettings.targetLearningGrade, appSettings)
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
     * スキルチャレンジ開始ボタンのセットアップ
     */
    private fun setupSpellingCheckStart() {
        val button = findViewById<MaterialButton>(R.id.button_to_spelling_check)
        button?.text = "スキルチャレンジ"
        button?.setOnClickListener {
            startActivity(Intent(this, com.stulab.studylockapp.ui.SkillChallengeActivity::class.java))
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
     * 進捗アコーディオンのセットアップ
     */
    private fun setupProgressAccordion() {
        val toggleButton = findViewById<MaterialButton>(R.id.button_toggle_progress)
        val accordion = findViewById<View>(R.id.layout_progress_accordion)
        val card = findViewById<ViewGroup>(R.id.card_mission)

        toggleButton?.contentDescription = "学習進捗を開く"

        toggleButton?.setOnClickListener {
            TransitionManager.beginDelayedTransition(card)
            if (accordion?.visibility == View.VISIBLE) {
                accordion.visibility = View.GONE
                toggleButton.setIconResource(R.drawable.ic_expand_more_24)
                toggleButton.contentDescription = "学習進捗を開く"
            } else {
                accordion?.visibility = View.VISIBLE
                toggleButton.setIconResource(R.drawable.ic_expand_less_24)
                toggleButton.contentDescription = "学習進捗を閉じる"
                updateProgressDisplay()
            }
        }
    }

    /**
     * 学習進捗の表示を更新
     */
    private fun updateProgressDisplay() {
        val accordion = findViewById<View>(R.id.layout_progress_accordion)
        if (accordion?.visibility != View.VISIBLE) return

        val gradeStr = appSettings.safeLearningGrade
        val gradeInt = gradeStr.toIntOrNull() ?: 3

        lifecycleScope.launch {
            val progress = withContext(Dispatchers.IO) {
                progressRepository.getTopProgress(this@MainActivity, gradeInt)
            }

            updateProgressRow(R.id.row_short_term, progress.shortTerm)
            updateProgressRow(R.id.row_long_term, progress.longTerm)
            updateProgressRow(R.id.row_spelling, progress.spelling)
            updateProgressRow(R.id.row_word_pron, progress.wordPronunciation)
            updateProgressRow(R.id.row_sentence_pron, progress.sentencePronunciation)
        }
    }

    private fun updateProgressRow(rowId: Int, metric: ProgressMetricUiModel) {
        val row = findViewById<View>(rowId) ?: return
        row.findViewById<ImageView>(R.id.image_metric_icon).setImageResource(metric.iconResId)
        row.findViewById<TextView>(R.id.text_metric_label).text = metric.label
        row.findViewById<TextView>(R.id.text_metric_value).text = metric.displayText
        row.findViewById<LinearProgressIndicator>(R.id.progress_metric).progress = metric.percentage
        
        row.contentDescription = "${metric.label}、${metric.completedCount}件、${metric.percentage}パーセント"
    }

    /**
     * 保有ポイントの表示を更新
     */
    private fun updatePointDisplay() {
        // TOP画面は「ポイント」というラベルのみ表示するため、数値の更新処理は不要
    }
}
