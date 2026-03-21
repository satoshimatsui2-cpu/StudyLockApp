package com.example.studylockapp

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AdminAuthManager
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.service.AppLockAccessibilityService
import com.example.studylockapp.ui.GradeBottomSheet
import com.example.studylockapp.ui.LearningHistoryActivity
import com.example.studylockapp.ui.PointHistoryActivity
import com.example.studylockapp.worker.DailyReportWorker
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var gradeButton: MaterialButton
    private lateinit var buttonToLearning: Button
    private lateinit var settings: AppSettings

    private lateinit var textPointsTop: TextView
    private lateinit var textPointStatsTop: TextView
    private lateinit var textGradeStatsTop: TextView
    private lateinit var textTargetGrade: TextView

    private var gradeStatsMap: Map<String, String> = emptyMap()
    private var selectedGradeKey: String? = null

    private var accessibilityDialog: AlertDialog? = null
    private var notificationDialog: AlertDialog? = null
    private var permissionDialogsJob: Job? = null
    
    private var isInitialGradeDialogShowing = false

    data class GradeSpinnerItem(
        val gradeKey: String,   // 例: "5", "2.5"
        val label: String       // 表示用: "5級", "準2級"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // アプリの表示領域をシステムバー（ステータスバーなど）の裏側まで広げる設定
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)
        settings = AppSettings(this)

        // ✅ 前回選んだグレードを復元（なければ null のまま）
        val savedGrade = settings.lastGradeFilter
        selectedGradeKey = savedGrade.takeIf { it.isNotBlank() }

        createNotificationChannel()
        DailyReportWorker.schedule(this)
        ensureUserRole()

        gradeButton = findViewById(R.id.spinner_grade_top)
        buttonToLearning = findViewById(R.id.button_to_learning)
        textPointsTop = findViewById(R.id.text_points_top)
        textPointStatsTop = findViewById(R.id.text_point_stats_top)
        textGradeStatsTop = findViewById(R.id.text_grade_stats_top)
        textTargetGrade = findViewById(R.id.text_target_grade)

        setupLongPressForAdmin()

        // 初期状態
        gradeButton.text = getString(R.string.hint_select_grade)
        gradeButton.setOnClickListener { showGradePickerDialog() }
        buttonToLearning.isEnabled = false
        textGradeStatsTop.text = "復習 0 • 新規 0/0"

        // 右上の設定バッジ
        findViewById<ImageButton>(R.id.button_admin_settings_top)?.setOnClickListener {
            openAdminSettings(isLongPressRoute = false)
        }
        // 旧「管理設定」ボタン（非表示でも安全）
        findViewById<Button>(R.id.button_admin_settings)?.setOnClickListener {
            openAdminSettings(isLongPressRoute = false)
        }

        buttonToLearning.setOnClickListener {
            if (settings.currentLearningGrade.isBlank()) {
                lifecycleScope.launch {
                    awaitInitialGradeSelection()
                }
                return@setOnClickListener
            }

            val gradeSelected = selectedGradeKey ?: return@setOnClickListener
            settings.lastGradeFilter = gradeSelected

            startActivity(
                Intent(this, LearningActivity::class.java).apply {
                    putExtra("gradeFilter", gradeSelected)
                }
            )
        }

        // ▼▼▼ 追加: ポイントカード（矢印付き）をクリックした時の処理 ▼▼▼
        findViewById<View>(R.id.card_to_point_history)?.setOnClickListener {
            startActivity(Intent(this, PointHistoryActivity::class.java))
        }
        // ▲▲▲ 追加ここまで ▲▲▲

        // ポイント履歴ボタン（既存の青いボタン）
        findViewById<Button>(R.id.button_to_point_history)?.setOnClickListener {
            startActivity(Intent(this, PointHistoryActivity::class.java))
        }

        // 学習履歴画面へ
        findViewById<Button>(R.id.button_to_learning_history)?.setOnClickListener {
            startActivity(Intent(this, LearningHistoryActivity::class.java))
        }

        // 起動時にポイントとグレード表示を更新
        updatePointView()
        updateGradeDropdownLabels()
    }


     private fun setupLongPressForAdmin() {
        if (!settings.isEnableAdminLongPress()) return

        val titleView = findViewById<View>(R.id.title_top) ?: return
        val longPressDuration = (ViewConfiguration.getLongPressTimeout() * 2).toLong()
        val handler = Handler(Looper.getMainLooper())
        var longPressRunnable: Runnable? = null

        titleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressRunnable = Runnable { openAdminSettings(isLongPressRoute = true) }
                    handler.postDelayed(longPressRunnable!!, longPressDuration)
                    true 
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // ★改善: サービスが無効なら、通知済みフラグを確実にリセットしておく
        if (!isAppLockServiceEnabled()) {
            settings.setAccessibilityEnabledNotified(false)
        }

        // 連打 / 戻ってきた時の多重起動防止
        permissionDialogsJob?.cancel()
        permissionDialogsJob = lifecycleScope.launch {
            // 1) 通知チェック → 表示したならここで終了（アクセは出さない）
            val shownNotification = maybeShowNotificationPermissionDialogSequential()
            if (shownNotification) return@launch

            // 2) 次にアクセシビリティチェック
            val shownAccessibility = maybeShowAccessibilityDialogSequential()
            if (shownAccessibility) return@launch
            
            // 3) 初回の目標レベル選択
            if (settings.currentLearningGrade.isEmpty() && !isInitialGradeDialogShowing) {
                isInitialGradeDialogShowing = true
                awaitInitialGradeSelection()
            }
        }

        updateTargetGradeDisplay()
        updatePointView()
        updateGradeDropdownLabels()
    }

    private fun updateTargetGradeDisplay() {
        val targetRank = GradeUtils.toRank(settings.safeLearningGrade)
        val targetDisplay = GradeUtils.toDisplay(settings.safeLearningGrade)

        val currentRank = GradeUtils.toRank(selectedGradeKey)
        val currentDisplay = selectedGradeKey?.let { GradeUtils.toDisplay(it) } ?: "未選択"

        val isReview = currentRank < targetRank && currentRank > 0
        val suffix = if (isReview) "（復習）" else ""

        textTargetGrade.text = "目標：$targetDisplay / 学習：$currentDisplay$suffix"
    }

    private suspend fun awaitInitialGradeSelection() = suspendCancellableCoroutine<Unit> { continuation ->
        var resumed = false

        fun resumeOnce() {
            if (!resumed && continuation.isActive) {
                resumed = true
                continuation.resume(Unit)
            }
        }

        val dialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("目標レベルを設定してください")
            .setMessage("あなたの学習状況に合わせてポイント計算を最適化するため、目標とする英検級を選択してください。")
            .setCancelable(false)
            .setPositiveButton("選択する") { _, _ ->
                val sheet = GradeBottomSheet { selectedGrade ->
                    // ① 目標レベル保存
                    settings.currentLearningGrade = GradeUtils.normalize(selectedGrade)
                    
                    // ② 学習レベルも同期
                    settings.lastGradeFilter = selectedGrade
                    selectedGradeKey = selectedGrade

                    // ③ UI反映
                    applyGradeSelection(selectedGrade)

                    isInitialGradeDialogShowing = false
                    resumeOnce()
                }
                sheet.show(supportFragmentManager, "InitialGradePicker")
            }
            .setOnDismissListener {
                isInitialGradeDialogShowing = false
                resumeOnce()
            }
            .create()

        dialog.show()

        continuation.invokeOnCancellation {
            dialog.dismiss()
            isInitialGradeDialogShowing = false
        }
    }

    private fun openAdminSettings(isLongPressRoute: Boolean = false) {
        startActivity(Intent(this, AdminSettingsActivity::class.java).apply {
            putExtra("isLongPressRoute", isLongPressRoute)
        })
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPointsTop.text = "保有ポイント: $total"
        lifecycleScope.launch { updatePointStats() }
    }

    /** 今日 / 前日比（タイムゾーン設定に従う） */
    private suspend fun updatePointStats() {
        val db = AppDatabase.getInstance(this@MainActivity)
        val histDao = db.pointHistoryDao()

        val zone = AppSettings(this@MainActivity).getAppZoneId()
        val today = LocalDate.now(zone).toEpochDay()
        val yesterday = today - 1

        val todaySum = histDao.getSumByDate(today)
        val yesterdaySum = histDao.getSumByDate(yesterday)
        val diff = todaySum - yesterdaySum
        val diffSign = if (diff >= 0) "+" else "-"
        val diffAbs = abs(diff)

        textPointStatsTop.text = "今日: $todaySum / 前日比: $diffSign$diffAbs"
    }

    private fun gradeKeyToLabel(gradeKey: String): String = when (gradeKey) {
        "2.5" -> "準2級"
        "1.5" -> "準1級"
        else -> "${gradeKey}級"
    }

    private fun updateGradeDropdownLabels() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@MainActivity)
            val words = db.wordDao().getAll()
            val progressDao = db.wordProgressDao()
            val nowSec = System.currentTimeMillis() / 1000L

            val dueMeaning: List<Long> =
                progressDao.getDueWordIdsOrdered("meaning", nowSec).map { it.toLong() }
            val dueListening: List<Long> =
                progressDao.getDueWordIdsOrdered("listening", nowSec).map { it.toLong() }

            val startedMeaning: Set<Long> =
                progressDao.getProgressIds("meaning").map { it.toLong() }.toSet()
            val startedListening: Set<Long> =
                progressDao.getProgressIds("listening").map { it.toLong() }.toSet()
            val startedUnion: Set<Long> = startedMeaning union startedListening

            val byGrade: Map<String, List<Long>> =
                words.groupBy { it.grade }.mapValues { (_, list) -> list.map { it.no.toLong() } }

            val gradeKeys = listOf("5", "4", "3", "2.5", "2", "1.5", "1")

            val statsBuilder = linkedMapOf<String, String>()
            gradeKeys.forEach { gradeKey ->
                val wordIds = byGrade[gradeKey].orEmpty()
                val idSet = wordIds.toSet()
                val total = wordIds.size

                val review = dueMeaning.count { it in idSet } + dueListening.count { it in idSet }
                val newUntouched = idSet.count { it !in startedUnion }

                statsBuilder[gradeKey] = "復習 $review • 新規 $newUntouched/$total"
            }
            gradeStatsMap = statsBuilder

            val saved = settings.lastGradeFilter.takeIf { it.isNotBlank() && it in gradeKeys }
            val keepKey = saved ?: selectedGradeKey
            if (keepKey != null) {
                selectedGradeKey = keepKey
                gradeButton.text = GradeUtils.toDisplay(keepKey)
                buttonToLearning.isEnabled = true
                textGradeStatsTop.text = gradeStatsMap[keepKey] ?: "復習 0 • 新規 0/0"
            } else {
                gradeButton.text = getString(R.string.hint_select_grade)
                selectedGradeKey = null
                buttonToLearning.isEnabled = false
                textGradeStatsTop.text = "復習 0 • 新規 0/0"
            }
            updateTargetGradeDisplay()
        }
    }

    // 既存の showGradePickerDialog をこれに書き換え
    private fun showGradePickerDialog() {
        // BottomSheetを表示
        val sheet = GradeBottomSheet { selectedGrade ->
            applyGradeSelection(selectedGrade)
        }
        sheet.show(supportFragmentManager, "GradeBottomSheet")
    }

    private fun applyGradeSelection(grade: String) {
        selectedGradeKey = grade
        settings.lastGradeFilter = grade

        val label = GradeUtils.toDisplay(grade)
        gradeButton.text = label
        buttonToLearning.isEnabled = true
        textGradeStatsTop.text = gradeStatsMap[grade] ?: "復習 0 • 新規 0/0"

        updateTargetGradeDisplay()

        // 目標レベルより上を選んだ場合のみ確認
        val currentGoalRank = GradeUtils.toRank(settings.safeLearningGrade)
        val selectedRank = GradeUtils.toRank(grade)

        if (selectedRank > currentGoalRank && currentGoalRank > 0) {
            showGoalUpdateConfirmDialog(grade)
        }
    }

    private fun showGoalUpdateConfirmDialog(newGrade: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("目標レベルの更新")
            .setMessage("現在の目標（${GradeUtils.toDisplay(settings.safeLearningGrade)}）より高い級です。\n目標を更新しますか？")
            .setPositiveButton("更新する") { _, _ ->
                settings.currentLearningGrade = GradeUtils.normalize(newGrade)
                updateTargetGradeDisplay()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private suspend fun maybeShowAccessibilityDialogSequential(): Boolean {
        if (accessibilityDialog?.isShowing == true || notificationDialog?.isShowing == true) return true
    
        val svcEnabled = isAppLockServiceEnabled()
        val isRequired = AdminAuthManager.isAppLockRequired(this)
    
        val shouldForce = withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MainActivity)
            val lockedCount = db.lockedAppDao().countLocked()
            settings.isAppLockEnabled() || lockedCount > 0
        }
    
        val isFirstIntro = !settings.hasShownAccessibilityIntro()
    
        if (!svcEnabled && (shouldForce || isFirstIntro)) {
            settings.setHasShownAccessibilityIntro(true)
    
            withContext(Dispatchers.Main) {
                val msg = getString(R.string.app_lock_accessibility_message)
                val builder = AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.app_lock_accessibility_title)
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.app_lock_accessibility_go_settings) { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
    
                if (!isRequired && shouldForce) {
                    builder.setNegativeButton(R.string.app_lock_accessibility_disable_all) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getInstance(this@MainActivity)
                            settings.setAppLockEnabled(false)
                            db.lockedAppDao().disableAllLocks()
                        }
                    }
                }
                accessibilityDialog = builder.show()
            }
            return true
        }
        return false
    }

    private fun isAppLockServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(this, AppLockAccessibilityService::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo?.serviceInfo?.packageName == packageName &&
                        it.resolveInfo?.serviceInfo?.name == expected.className
            }
    }

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private suspend fun maybeShowNotificationPermissionDialogSequential(): Boolean {
        if (notificationDialog?.isShowing == true || accessibilityDialog?.isShowing == true) return true

        val notificationsEnabled = areNotificationsEnabled()

        val shouldForce = withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MainActivity)
            val lockedCount = db.lockedAppDao().countLocked()
            settings.isAppLockEnabled() || lockedCount > 0
        }

        if (!notificationsEnabled && shouldForce) {
            withContext(Dispatchers.Main) {
                val msg = "利用制限の状態（制限の開始/終了、家族共有、レポート等）を通知でお知らせします。\n" +"通知を許可しない場合でも、アプリ内では確認できます。"
                val builder = AlertDialog.Builder(this@MainActivity)
                    .setTitle("通知の許可（おすすめ）")
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton("設定を開く") { _, _ ->
                        val intent = Intent().apply {
                            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                        startActivity(intent)
                    }
                notificationDialog = builder.show()
            }
            return true
        }
        return false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val reportChannelName = "学習レポート"
            val reportChannelDesc = "日々の学習状況やポイントをお知らせします"
            val reportChannelImportance = NotificationManager.IMPORTANCE_DEFAULT
            val reportChannel = NotificationChannel("REPORT_CHANNEL", reportChannelName, reportChannelImportance).apply {
                description = reportChannelDesc
            }
            notificationManager.createNotificationChannel(reportChannel)

            val securityChannelName = "セキュリティアラート"
            val securityChannelDesc = "アプリのセキュリティに関する重要な通知です。"
            val securityChannelImportance = NotificationManager.IMPORTANCE_HIGH
            val securityChannel = NotificationChannel("SECURITY_ALERTS", securityChannelName, securityChannelImportance).apply {
                description = securityChannelDesc
            }
            notificationManager.createNotificationChannel(securityChannel)
        }
    }

    private fun ensureUserRole() {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            checkAndSetRole(currentUser)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    result.user?.let { checkAndSetRole(it) }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("Setup", "ログイン失敗", e)
                }
        }
    }

    private fun checkAndSetRole(user: FirebaseUser) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("users").document(user.uid)
        docRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists() && snapshot.contains("role")) {
                return@addOnSuccessListener 
            }
            val data = hashMapOf("role" to "child")
            docRef.set(data, SetOptions.merge())
        }
    }
}
