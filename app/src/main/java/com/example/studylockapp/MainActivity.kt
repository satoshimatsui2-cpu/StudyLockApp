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
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity
import com.example.studylockapp.worker.DailyReportWorker
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var gradeButton: MaterialButton
    private lateinit var buttonToLearning: Button

    private lateinit var textPointsTop: TextView
    private lateinit var textPointStatsTop: TextView
    private lateinit var textGradeStatsTop: TextView

    private var gradeStatsMap: Map<String, String> = emptyMap()
    private var selectedGradeKey: String? = null

    private var accessibilityDialog: AlertDialog? = null
    private var notificationDialog: AlertDialog? = null
    private var permissionDialogsJob: Job? = null

    data class GradeSpinnerItem(
        val gradeKey: String,   // 例: "5", "2.5"
        val label: String       // 表示用: "5級", "準2級"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // アプリの表示領域をシステムバー（ステータスバーなど）の裏側まで広げる設定
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        // ▼▼▼ ＋追加: 通知の通り道を作る（これを忘れると通知が来ません！） ▼▼▼
        createNotificationChannel()
        // ▲▲▲ 追加ここまで ▲▲▲

        // ▼▼▼ 追加: 日次レポートWorkerのスケジュール登録 ▼▼▼
        DailyReportWorker.schedule(this)
        // ▲▲▲ 追加ここまで ▲▲▲

        // ▼▼▼ ★追加: 起動時に必ず「自分は子供だ」とサーバーに教える ▼▼▼
        ensureUserRole()

        gradeButton = findViewById(R.id.spinner_grade_top)
        buttonToLearning = findViewById(R.id.button_to_learning)
        textPointsTop = findViewById(R.id.text_points_top)
        textPointStatsTop = findViewById(R.id.text_point_stats_top)
        textGradeStatsTop = findViewById(R.id.text_grade_stats_top)

        // タイトルを長押しで管理者画面へ（長押し時間を倍にする）
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

        // 学習画面へ（gradeFilter は DB の grade と一致する値：例 "5"）
        buttonToLearning.setOnClickListener {
            val gradeSelected = selectedGradeKey ?: return@setOnClickListener
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
        val settings = AppSettings(this)
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
                    true // イベントを消費して、クリックなどが発火しないようにする
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

        val settings = AppSettings(this)
        if (!settings.hasChosenTimeZone()) {
            startActivity(Intent(this, TimeZoneSetupActivity::class.java))
            return
        }

        // 連打 / 戻ってきた時の多重起動防止
        permissionDialogsJob?.cancel()
        permissionDialogsJob = lifecycleScope.launch {
            // 1) 通知チェック → 表示したならここで終了（アクセは出さない）
            val shownNotification = maybeShowNotificationPermissionDialogSequential()
            if (shownNotification) return@launch

            // 2) 次にアクセシビリティチェック
            maybeShowAccessibilityDialogSequential()
        }

        updatePointView()
        updateGradeDropdownLabels()
    }

    private fun openAdminSettings(isLongPressRoute: Boolean = false) {
        startActivity(Intent(this, AdminSettingsActivity::class.java).apply {
            putExtra("isLongPressRoute", isLongPressRoute)
            // タイトル長押し経由なら認証済みとして扱うフラグ
            // ただし、管理者設定側でもこのIntentを受け取って認証フラグを立てる必要がある
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

            // Due/Started（型ズレ対策で Long に統一）
            val dueMeaning: List<Long> =
                progressDao.getDueWordIdsOrdered("meaning", nowSec).map { it.toLong() }
            val dueListening: List<Long> =
                progressDao.getDueWordIdsOrdered("listening", nowSec).map { it.toLong() }

            val startedMeaning: Set<Long> =
                progressDao.getProgressIds("meaning").map { it.toLong() }.toSet()
            val startedListening: Set<Long> =
                progressDao.getProgressIds("listening").map { it.toLong() }.toSet()
            val startedUnion: Set<Long> = startedMeaning union startedListening

            // gradeごとに wordId（Long）をまとめる
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

            // 現在選択を保持
            val keepKey = selectedGradeKey ?: gradeKeys.firstOrNull()
            if (keepKey != null) {
                selectedGradeKey = keepKey
                gradeButton.text = gradeKeyToLabel(keepKey)
                buttonToLearning.isEnabled = true
                textGradeStatsTop.text = gradeStatsMap[keepKey] ?: "復習 0 • 新規 0/0"
            } else {
                gradeButton.text = getString(R.string.hint_select_grade)
                selectedGradeKey = null
                buttonToLearning.isEnabled = false
                textGradeStatsTop.text = "復習 0 • 新規 0/0"
            }
        }
    }

    // 既存の showGradePickerDialog をこれに書き換え
    private fun showGradePickerDialog() {
        // BottomSheetを表示
        val sheet = GradeBottomSheet { selectedGrade ->
            // 選ばれた時の処理（既存の処理を呼ぶだけ）
            applyGradeSelection(selectedGrade)
        }
        sheet.show(supportFragmentManager, "GradeBottomSheet")
    }

    private fun applyGradeSelection(grade: String) {
        selectedGradeKey = grade
        val label = gradeKeyToLabel(grade)
        gradeButton.text = label
        buttonToLearning.isEnabled = true
        textGradeStatsTop.text = gradeStatsMap[grade] ?: "復習 0 • 新規 0/0"
    }

    private suspend fun maybeShowAccessibilityDialogSequential(): Boolean {
        // 既に何か出てたら何もしない（＝他を優先）
        if (accessibilityDialog?.isShowing == true || notificationDialog?.isShowing == true) return true

        val settings = AppSettings(this)
        val svcEnabled = isAppLockServiceEnabled()
        val isRequired = AdminAuthManager.isAppLockRequired(this)

        // DBチェックはIOで待つ（launchしない）
        val shouldForce = withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MainActivity)
            val lockedCount = db.lockedAppDao().countLocked()
            settings.isAppLockEnabled() || lockedCount > 0
        }

        if (!svcEnabled && shouldForce) {
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

                // 必須ONなら全解除ボタンを出さない
                if (!isRequired) {
                    builder.setNegativeButton(R.string.app_lock_accessibility_disable_all) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getInstance(this@MainActivity)
                            settings.setAppLockEnabled(false)
                            db.lockedAppDao().disableAllLocks()
                        }
                    }
                }

                // show() は1回だけ
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

        val settings = AppSettings(this)
        val notificationsEnabled = areNotificationsEnabled()

        val shouldForce = withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MainActivity)
            val lockedCount = db.lockedAppDao().countLocked()
            settings.isAppLockEnabled() || lockedCount > 0
        }

        if (!notificationsEnabled && shouldForce) {
            withContext(Dispatchers.Main) {
                val msg = "本端末で学習レポート等を受信するため通知をONにしてください。保護者端末の場合、ONにしないと子端末からのセキュリティアラート、学習記録は届きません。"
                val builder = AlertDialog.Builder(this@MainActivity)
                    .setTitle("通知の許可が必要です")
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton("設定を開く") { _, _ ->
                        val intent = Intent().apply {
                            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                        startActivity(intent)
                    }

                // show() は1回だけ
                notificationDialog = builder.show()
            }
            return true
        }
        return false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // チャンネル1: 学習レポート
            val reportChannelName = "学習レポート"
            val reportChannelDesc = "日々の学習状況やポイントをお知らせします"
            val reportChannelImportance = NotificationManager.IMPORTANCE_DEFAULT
            val reportChannel = NotificationChannel("REPORT_CHANNEL", reportChannelName, reportChannelImportance).apply {
                description = reportChannelDesc
            }
            notificationManager.createNotificationChannel(reportChannel)

            // チャンネル2: セキュリティアラート
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
            // 未ログインなら、匿名ログインしてから設定する
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
                return@addOnSuccessListener // 設定済みなら何もしない
            }
            // roleがない時だけ child を書き込む
            val data = hashMapOf("role" to "child")
            docRef.set(data, SetOptions.merge())
        }
    }
}