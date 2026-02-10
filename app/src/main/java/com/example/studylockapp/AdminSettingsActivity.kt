package com.example.studylockapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.ads.AdAudioManager
import com.example.studylockapp.data.AdminAuthManager
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.service.AccessibilityUtils
import com.example.studylockapp.service.AppLockAccessibilityService
import com.example.studylockapp.ui.QrCodeActivity
import com.example.studylockapp.ui.applock.AppLockSettingsActivity
import com.example.studylockapp.ui.setup.AuthenticatorSetupActivity
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions // 追加
import com.google.firebase.messaging.FirebaseMessaging
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class AdminSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private var isAuthenticated: Boolean = false
    private lateinit var scrollView: ScrollView

    // ▼▼▼ 追加: 接続状態表示用 ▼▼▼
    private lateinit var textManager: TextView
    private lateinit var textTarget: TextView

    // スイッチ用（濃い目）
    private val switchTextColor by lazy {
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    }

    private val dialogTitleColor: Int = Color.WHITE
    private val dialogTextColor: Int = Color.WHITE
    private val dialogHintColor: Int = Color.LTGRAY

    // QRコードスキャナーの登録
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, "スキャンをキャンセルしました", Toast.LENGTH_SHORT).show()
        } else {
            // ▼▼▼ 修正: 名前入力ダイアログを呼び出す ▼▼▼
            val childUid = result.contents
            promptForChildNameAndRegister(childUid)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)
        settings = AppSettings(this)
        scrollView = findViewById(R.id.scroll_admin)

        // ▼▼▼ 追加: 表示用TextViewの取得 ▼▼▼
        textManager = findViewById(R.id.text_connected_manager)
        textTarget = findViewById(R.id.text_managed_target)

        isAuthenticated = savedInstanceState?.getBoolean("authenticated", false) ?: false

        // Check if launched via long press
        if (intent.getBooleanExtra("isLongPressRoute", false)) {
            isAuthenticated = true
        }

        setupAdminSecurityViews()
        setupExistingControls()
        setupAccordions()

        // 親用読み取りボタンの設定
        findViewById<MaterialButton>(R.id.button_scan_parent_qr)?.setOnClickListener {
            // スキャナーの設定と起動
            val options = ScanOptions()
            options.setPrompt("枠内にお子様のQRコードを写してください")
            options.setBeepEnabled(false)
            options.setOrientationLocked(true)
            options.setCaptureActivity(CaptureActivityPortrait::class.java) // 縦画面固定
            barcodeLauncher.launch(options)
        }
    }

    private fun setupAccordions() {
        val groups = listOf(
            Triple(R.id.header_study_points, R.id.content_study_points, R.id.arrow_study_points),
            Triple(R.id.header_test_points, R.id.content_test_points, R.id.arrow_test_points),
            Triple(R.id.header_time_settings, R.id.content_time_settings, R.id.arrow_time_settings),
            Triple(R.id.header_security, R.id.content_security, R.id.arrow_security)
        )

        groups.forEach { (headerId, contentId, arrowId) ->
            val header = findViewById<View>(headerId)
            val content = findViewById<View>(contentId)
            val arrow = findViewById<View>(arrowId)

            if (content.visibility == View.VISIBLE) {
                arrow.rotation = 180f
            } else {
                arrow.rotation = 0f
            }

            header.setOnClickListener {
                val isVisible = content.visibility == View.VISIBLE
                if (isVisible) {
                    content.visibility = View.GONE
                    arrow.animate().rotation(0f).setDuration(200).start()
                } else {
                    content.visibility = View.VISIBLE
                    arrow.animate().rotation(180f).setDuration(200).start()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensureAuthenticatedOrFinish()
        // ▼▼▼ 追加: 画面に戻るたびに接続状態を更新 ▼▼▼
        updateConnectionStatus()
    }

    // ▼▼▼ 追加: 接続状態の表示更新ロジック ▼▼▼
    private fun updateConnectionStatus() {
        // TextViewが見つからない場合（XML修正漏れなど）のエラー回避
        if (!::textManager.isInitialized || !::textTarget.isInitialized) return

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            textManager.text = "管理者: 未ログイン"
            textTarget.text = "管理対象: 未ログイン"
            return
        }

        val db = FirebaseFirestore.getInstance()
        val myUid = user.uid

        // 1. 私は誰に管理されている？ (users/{me}/parents)
        db.collection("users").document(myUid).collection("parents").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null || snapshot.isEmpty) {
                    textManager.text = "管理者: なし"
                    settings.setParentUid(null) // キャッシュクリア
                } else {
                    val count = snapshot.size()
                    val firstParentId = snapshot.documents[0].id
                    settings.setParentUid(firstParentId) // IDをキャッシュ保存

                    val parentIds = snapshot.documents.joinToString(", ") { it.id.take(4) + "..." }
                    textManager.text = "管理者: 接続済み ($count)\nID: $parentIds"
                }
            }
            .addOnFailureListener {
                textManager.text = "管理者: 取得エラー"
            }

        // 2. 私は誰を管理している？ (users/{me}/children)
        db.collection("users").document(myUid).collection("children").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null || snapshot.isEmpty) {
                    textTarget.text = "管理対象: なし"
                } else {
                    val count = snapshot.size()
                    val childIds = snapshot.documents.joinToString(", ") { it.id.take(4) + "..." }
                    textTarget.text = "管理対象: 接続済み ($count)\nID: $childIds"
                }
            }
            .addOnFailureListener {
                textTarget.text = "管理対象: 取得エラー"
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("authenticated", isAuthenticated)
    }

    private fun ensureAuthenticatedOrFinish() {
        if (!AdminAuthManager.isAdminLockEnabled(this)) {
            scrollView.visibility = View.VISIBLE
            return
        }
        if (isAuthenticated) {
            scrollView.visibility = View.VISIBLE
            return
        }

        scrollView.visibility = View.INVISIBLE

        promptPinAndDo(
            title = getString(R.string.admin_enter_pin_title),
            onSuccess = {
                isAuthenticated = true
                showToast(getString(R.string.admin_pin_ok))
                scrollView.visibility = View.VISIBLE
            },
            onFailure = { finish() },
            onCancel = { finish() }
        )
    }

    private fun setupExistingControls() {
        // --- Grade Setup ---
        val spinnerCurrentGrade = findViewById<Spinner>(R.id.spinner_current_learning_grade)
        val grades = arrayOf("1級", "準1級", "2級", "準2級", "3級", "4級", "5級")
        val gradeAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, grades) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.BLACK)
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.BLACK)
                view.setBackgroundColor(Color.WHITE)
                return view
            }
        }
        gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrentGrade.adapter = gradeAdapter

        val currentGradePosition = grades.indexOf(settings.currentLearningGrade)
        spinnerCurrentGrade.setSelection(if (currentGradePosition != -1) currentGradePosition else 0)

        // --- Point SeekBars ---
        val modes = mapOf(
            "meaning" to (findViewById<TextView>(R.id.text_point_meaning) to findViewById<SeekBar>(R.id.seek_point_meaning)),
            "listening" to (findViewById<TextView>(R.id.text_point_listening) to findViewById<SeekBar>(R.id.seek_point_listening)),
            "listening_jp" to (findViewById<TextView>(R.id.text_point_listening_jp) to findViewById<SeekBar>(R.id.seek_point_listening_jp)),
            "japanese_to_english" to (findViewById<TextView>(R.id.text_point_ja_to_en) to findViewById<SeekBar>(R.id.seek_point_ja_to_en)),
            "english_english_1" to (findViewById<TextView>(R.id.text_point_en_en_1) to findViewById<SeekBar>(R.id.seek_point_en_en_1)),
            "english_english_2" to (findViewById<TextView>(R.id.text_point_en_en_2) to findViewById<SeekBar>(R.id.seek_point_en_en_2)),
            "test_fill_blank" to (findViewById<TextView>(R.id.text_point_test_fill_blank) to findViewById<SeekBar>(R.id.seek_point_test_fill_blank)),
            "test_sort" to (findViewById<TextView>(R.id.text_point_test_sort) to findViewById<SeekBar>(R.id.seek_point_test_sort)),
            "test_listen_q1" to (findViewById<TextView>(R.id.text_point_test_listen_q1) to findViewById<SeekBar>(R.id.seek_point_test_listen_q1)),
            "test_listen_q2" to (findViewById<TextView>(R.id.text_point_test_listen_q2) to findViewById<SeekBar>(R.id.seek_point_test_listen_q2))
        )

        fun progressToPoint(progress: Int): Int = 4 + progress * 4
        fun pointToProgress(point: Int): Int = (point - 4) / 4

        modes.forEach { (mode, views) ->
            val (textView, seekBar) = views
            if (textView != null && seekBar != null) {
                seekBar.max = 7
                seekBar.progress = pointToProgress(settings.getBasePoint(mode))
                textView.text = "${getModeDisplayName(mode)}: ${progressToPoint(seekBar.progress)} pt"
                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        textView.text = "${getModeDisplayName(mode)}: ${progressToPoint(progress)} pt"
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
        }

        val textInterval = findViewById<TextView>(R.id.text_interval)
        val seekInterval = findViewById<SeekBar>(R.id.seek_interval)
        val textWrongRetry = findViewById<TextView>(R.id.text_wrong_retry)
        val seekWrongRetry = findViewById<SeekBar>(R.id.seek_wrong_retry)
        val textLevel1Retry = findViewById<TextView>(R.id.text_level1_retry)
        val seekLevel1Retry = findViewById<SeekBar>(R.id.seek_level1_retry)
        val textUnlockMinPer10Pt = findViewById<TextView>(R.id.text_unlock_min_per_10pt_value)
        val seekUnlockMinPer10Pt = findViewById<SeekBar>(R.id.seek_unlock_min_per_10pt)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)

        findViewById<MaterialButton>(R.id.button_open_timezone_setup)?.setOnClickListener {
            startActivity(Intent(this, TimeZoneSetupActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.button_app_lock_settings)?.setOnClickListener {
            startActivity(Intent(this, AppLockSettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.button_show_qr)?.setOnClickListener {
            startActivity(Intent(this, QrCodeActivity::class.java))
        }

        seekInterval.max = 19
        seekWrongRetry.max = 118
        seekLevel1Retry.max = 118

        fun intervalMsToProgress(ms: Long): Int = ((ms.coerceIn(500L, 10_000L) - 500L) / 500L).toInt()
        fun progressToIntervalMs(progress: Int): Long = 500L + (progress.coerceIn(0, 19) * 500L)
        fun secToProgress(sec: Long): Int = ((sec.coerceIn(10L, 600L) - 10L) / 5L).toInt()
        fun progressToSec(progress: Int): Long = 10L + (progress.coerceIn(0, 118) * 5L)
        fun minPer10PtToProgress(value: Int): Int = value.coerceIn(1, 10) - 1
        fun progressToMinPer10Pt(progress: Int): Int = progress.coerceIn(0, 9) + 1

        seekInterval.progress = intervalMsToProgress(settings.answerIntervalMs)
        seekWrongRetry.progress = secToProgress(settings.wrongRetrySec)
        seekLevel1Retry.progress = secToProgress(settings.level1RetrySec)
        seekUnlockMinPer10Pt.progress = minPer10PtToProgress(settings.getUnlockMinutesPer10Pt())

        fun refreshLabels() {
            val sec = progressToIntervalMs(seekInterval.progress) / 1000f
            textInterval.text = getString(R.string.admin_label_interval_sec, sec)
            textWrongRetry.text = getString(R.string.admin_label_wrong_retry_sec, progressToSec(seekWrongRetry.progress))
            textLevel1Retry.text = getString(R.string.admin_label_level1_retry_sec, progressToSec(seekLevel1Retry.progress))
            textUnlockMinPer10Pt.text = getString(R.string.admin_label_unlock_min_per_10pt_value, progressToMinPer10Pt(seekUnlockMinPer10Pt.progress))
        }
        refreshLabels()

        val commonListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { refreshLabels() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        listOf(seekInterval, seekWrongRetry, seekLevel1Retry, seekUnlockMinPer10Pt).forEach { it.setOnSeekBarChangeListener(commonListener) }

        btnSave.setOnClickListener {
            val selectedGrade = spinnerCurrentGrade.selectedItem?.toString() ?: "1級"
            settings.currentLearningGrade = selectedGrade
            settings.pointReductionOneGradeDown = 50
            settings.pointReductionTwoGradesDown = 25

            modes.forEach { (mode, views) ->
                val (_, seekBar) = views
                if (seekBar != null) settings.setBasePoint(mode, progressToPoint(seekBar.progress))
            }

            settings.answerIntervalMs = progressToIntervalMs(seekInterval.progress)
            settings.wrongRetrySec = progressToSec(seekWrongRetry.progress)
            settings.level1RetrySec = progressToSec(seekLevel1Retry.progress)
            settings.setUnlockMinutesPer10Pt(progressToMinPer10Pt(seekUnlockMinPer10Pt.progress))
            AdAudioManager.apply(settings)
            finish()
        }
    }

    private fun getModeDisplayName(mode: String): String {
        return when (mode) {
            "meaning" -> getString(R.string.mode_meaning)
            "listening" -> getString(R.string.mode_listening)
            "listening_jp" -> getString(R.string.mode_listening_jp)
            "japanese_to_english" -> getString(R.string.mode_japanese_to_english)
            "english_english_1" -> getString(R.string.mode_english_english_1)
            "english_english_2" -> getString(R.string.mode_english_english_2)
            "test_fill_blank" -> "穴埋め"
            "test_sort" -> "並び替え"
            "test_listen_q1" -> "リスニング質問"
            "test_listen_q2" -> "会話文リスニング"
            else -> mode.replace("_", " ").capitalize()
        }
    }

    private fun setupAdminSecurityViews() {
        val switchAdminLock = findViewById<SwitchMaterial>(R.id.switch_admin_lock) ?: return
        val switchAppLockRequired = findViewById<SwitchMaterial>(R.id.switch_app_lock_required)
        val buttonChangePin = findViewById<MaterialButton>(R.id.button_change_pin) ?: return
        val switchEnableLongPress = findViewById<SwitchMaterial>(R.id.switch_enable_long_press) ?: return
        val buttonSetupAuthenticator = findViewById<MaterialButton>(R.id.button_setup_authenticator)
        val switchAccessibilityLock = findViewById<SwitchMaterial>(R.id.switch_accessibility_lock)
        val switchTetheringLock = findViewById<SwitchMaterial>(R.id.switch_tethering_lock)
        val switchUninstallLock = findViewById<SwitchMaterial>(R.id.switch_uninstall_lock)

        switchAdminLock.setTextColor(switchTextColor)
        switchAppLockRequired?.setTextColor(switchTextColor)
        switchEnableLongPress.setTextColor(switchTextColor)
        switchAccessibilityLock?.setTextColor(switchTextColor)
        switchTetheringLock?.setTextColor(switchTextColor)
        switchUninstallLock?.setTextColor(switchTextColor)

        switchAdminLock.isChecked = AdminAuthManager.isAdminLockEnabled(this)
        switchAppLockRequired?.isChecked = AdminAuthManager.isAppLockRequired(this)
        switchEnableLongPress.isChecked = settings.isEnableAdminLongPress()
        switchAccessibilityLock?.isChecked = PrefsManager.isAccessibilityLockEnabled(this)
        switchTetheringLock?.isChecked = PrefsManager.isTetheringLockEnabled(this)
        switchUninstallLock?.isChecked = settings.isUninstallLockEnabled()

        switchAdminLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!AdminAuthManager.isPinSet(this)) {
                    promptSetNewPin(onSuccess = {
                        AdminAuthManager.setAdminLockEnabled(this, true)
                        showToast(getString(R.string.admin_lock_enabled))
                    }, onCancel = { switchAdminLock.isChecked = false })
                } else {
                    AdminAuthManager.setAdminLockEnabled(this, true)
                    showToast(getString(R.string.admin_lock_enabled))
                }
            } else {
                promptPinAndDo(
                    title = getString(R.string.admin_enter_pin_title),
                    onSuccess = {
                        AdminAuthManager.setAdminLockEnabled(this, false)
                        showToast(getString(R.string.admin_lock_disabled))
                    },
                    onFailure = {
                        switchAdminLock.isChecked = true
                        showToast(getString(R.string.admin_pin_incorrect))
                    },
                    onCancel = { switchAdminLock.isChecked = true }
                )
            }
        }

        switchAppLockRequired?.setOnCheckedChangeListener { _, isChecked ->
            AdminAuthManager.setAppLockRequired(this, isChecked)
            if (isChecked) settings.setAppLockEnabled(true)
            if (isChecked && !AccessibilityUtils.isServiceEnabled(this, AppLockAccessibilityService::class.java)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                showToast(getString(R.string.admin_app_lock_required_on))
            } else if (isChecked) {
                showToast(getString(R.string.admin_app_lock_required_on))
            }
        }

        switchEnableLongPress.setOnCheckedChangeListener { _, isChecked -> settings.setEnableAdminLongPress(isChecked) }
        switchAccessibilityLock?.setOnCheckedChangeListener { _, isChecked -> PrefsManager.setAccessibilityLockEnabled(this, isChecked) }
        switchTetheringLock?.setOnCheckedChangeListener { _, isChecked -> PrefsManager.setTetheringLockEnabled(this, isChecked) }
        switchUninstallLock?.setOnCheckedChangeListener { _, isChecked -> settings.setUninstallLockEnabled(isChecked) }

        buttonChangePin.setOnClickListener {
            promptPinAndDo(
                title = getString(R.string.admin_enter_pin_title),
                onSuccess = { promptSetNewPin() },
                onFailure = { showToast(getString(R.string.admin_pin_incorrect)) }
            )
        }
        buttonSetupAuthenticator?.setOnClickListener { startActivity(Intent(this, AuthenticatorSetupActivity::class.java)) }
    }

    // ▼▼▼ 修正: 管理者に聞く機能を追加 ▼▼▼
    private fun promptPinAndDo(
        title: String,
        onSuccess: () -> Unit,
        onFailure: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.admin_enter_pin_hint)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val edit = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(dialogTextColor)
            setHintTextColor(dialogHintColor)
        }
        inputLayout.addView(edit)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(title))
            .setView(inputLayout)
            .setPositiveButton(R.string.ok) { _, _ ->
                val pin = edit.text?.toString().orEmpty()
                if (AdminAuthManager.verifyPin(this, pin)) onSuccess() else onFailure?.invoke()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel?.invoke() }
            .setOnCancelListener { onCancel?.invoke() }

        // ペアリング済みの親がいる場合のみ「管理者に聞く」ボタンを表示
        if (settings.hasParent()) {
            dialog.setNeutralButton("管理者に聞く") { _, _ ->
                promptRemoteUnlock(onSuccess)
            }
        } else if (AdminAuthManager.isTotpSet(this)) {
            // 親がいないがAuthenticator設定済みの場合はそちらを表示
            dialog.setNeutralButton(R.string.admin_forgot_pin) { _, _ ->
                promptTotpAndResetPin(onSuccess)
            }
        }
        dialog.show()
    }

    // ▼▼▼ 修正版: ID手渡し機能付き ▼▼▼
    private fun promptRemoteUnlock(onSuccess: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            auth.signInAnonymously().addOnSuccessListener {
                promptRemoteUnlock(onSuccess)
            }.addOnFailureListener {
                Toast.makeText(this, "ログイン失敗", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val randomCode = (100000..999999).random().toString()
        // 東京を指定
        val functions = FirebaseFunctions.getInstance("asia-northeast1")

        // ★修正ポイント: "uid" を手動で詰め込む
        val data = hashMapOf(
            "code" to randomCode,
            "uid" to user.uid  // ← これで身分証明書を手渡し！
        )

        Toast.makeText(this, "管理者に解除コードを送信中...", Toast.LENGTH_SHORT).show()

        functions.getHttpsCallable("requestUnlockCode").call(data)
            .addOnSuccessListener {
                Toast.makeText(this, "通知を送信しました！", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "送信エラー: ${e.message}", Toast.LENGTH_LONG).show()
                android.util.Log.e("FunctionsError", "送信失敗", e)
            }

        // 入力画面を表示
        showUnlockDialog(randomCode, onSuccess)
    }

    // ダイアログ部分を分離（見やすくするため）
    private fun showUnlockDialog(correctCode: String, onSuccess: () -> Unit) {
        val inputLayout = TextInputLayout(this).apply {
            hint = "届いたコードを入力"
        }
        val edit = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(dialogTextColor)
            setHintTextColor(dialogHintColor)
        }
        inputLayout.addView(edit)

        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle("解除コード入力"))
            .setMessage("管理者の端末に通知された6桁の数字を入力してください。")
            .setView(inputLayout)
            .setPositiveButton("解除") { _, _ ->
                val input = edit.text?.toString().orEmpty()
                if (input == correctCode) {
                    showToast("認証成功！PINをリセットします。")
                    promptSetNewPin(onSuccess)
                } else {
                    showToast("コードが違います")
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun promptTotpAndResetPin(onSuccess: () -> Unit) {
        val inputLayout = TextInputLayout(this).apply { hint = getString(R.string.totp_verify_hint) }
        val edit = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(dialogTextColor)
            setHintTextColor(dialogHintColor)
        }
        inputLayout.addView(edit)
        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(getString(R.string.totp_enter_code_title)))
            .setView(inputLayout)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (AdminAuthManager.verifyTotp(this, edit.text?.toString().orEmpty())) {
                    showToast(getString(R.string.totp_reset_pin_success))
                    promptSetNewPin(onSuccess)
                } else {
                    showToast(getString(R.string.totp_incorrect))
                    promptTotpAndResetPin(onSuccess)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptSetNewPin(onSuccess: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_set_new_pin, null)
        val input1 = view.findViewById<TextInputEditText>(R.id.edit_new_pin).apply { setTextColor(dialogTextColor); setHintTextColor(dialogHintColor) }
        val input2 = view.findViewById<TextInputEditText>(R.id.edit_new_pin_confirm).apply { setTextColor(dialogTextColor); setHintTextColor(dialogHintColor) }

        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(getString(R.string.admin_set_new_pin_title)))
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val p1 = input1.text?.toString().orEmpty()
                val p2 = input2.text?.toString().orEmpty()
                if (p1.length < 4) { showToast(getString(R.string.admin_pin_length_error)); onCancel?.invoke(); return@setPositiveButton }
                if (p1 != p2) { showToast(getString(R.string.admin_pin_mismatch)); onCancel?.invoke(); return@setPositiveButton }
                AdminAuthManager.setPin(this, p1)
                onSuccess?.invoke()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel?.invoke() }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
    }

    private fun coloredTitle(text: String): CharSequence {
        val s = SpannableString(text)
        s.setSpan(ForegroundColorSpan(dialogTitleColor), 0, s.length, 0)
        return s
    }

    private fun showToast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }


    private fun registerAsParent(childUid: String, childName: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Toast.makeText(this, "未ログインです", Toast.LENGTH_SHORT).show()
            return
        }
        val db = FirebaseFirestore.getInstance()
        val myUid = user.uid

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(this, "トークン取得失敗", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                val token = task.result

                // 1) 子の parents サブコレ：親のトークン + 子の表示名(親が付けた呼び名) を保存
                val parentData = hashMapOf(
                    "uid" to myUid,
                    "role" to "parent",
                    "fcmToken" to token,
                    "childDisplayName" to childName, // ★ここに保存する
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                val task1 = db.collection("users").document(childUid)
                    .collection("parents").document(myUid)
                    .set(parentData)

                // 2) 親の children サブコレ：子のUID + 表示名（親側UI用）
                val childData = hashMapOf(
                    "uid" to childUid,
                    "role" to "child",
                    "displayName" to childName, // ★親側UI用にここへ
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                val task2 = db.collection("users").document(myUid)
                    .collection("children").document(childUid)
                    .set(childData)

                // 3) 親自身の role を parent に（displayName は触らない）
                val meUpdate = hashMapOf("role" to "parent")
                val task3 = db.collection("users").document(myUid)
                    .set(meUpdate, com.google.firebase.firestore.SetOptions.merge())

                com.google.android.gms.tasks.Tasks.whenAll(task1, task2, task3)
                    .addOnSuccessListener {
                        Toast.makeText(this, "ペアリング完了！\n${childName}さんを登録しました", Toast.LENGTH_LONG).show()
                        updateConnectionStatus()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "登録失敗: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
    }
    private fun promptForChildNameAndRegister(childUid: String) {
        val inputLayout = TextInputLayout(this).apply {
            hint = "お子様の名前 (通知に表示されます)"
            isErrorEnabled = true
        }

        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(20))

            // ★ 追加：見やすさ改善
            setTextColor(dialogTextColor)
            setHintTextColor(dialogHintColor)
        }
        inputLayout.addView(editText)

        // ★ 追加：メッセージも白に
        val msg = SpannableString("通知に表示されるお子様の名前を入力してください。").apply {
            setSpan(ForegroundColorSpan(dialogTextColor), 0, length, 0)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            // ★ 変更：タイトルを白に（既存の coloredTitle を使う）
            .setTitle(coloredTitle("管理対象の追加"))
            // ★ 変更：メッセージを白に
            .setMessage(msg)
            .setView(inputLayout)
            .setPositiveButton("登録", null)
            .setNegativeButton("キャンセル", null)
            .create()

        dialog.setOnShowListener {
            // ★ 念のため：メッセージ TextView が取れたら強制で白
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(dialogTextColor)

            val okBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            okBtn.setOnClickListener {
                val (normalized, err) = validateAndNormalizeChildName(
                    editText.text?.toString().orEmpty()
                )
                if (err != null) {
                    inputLayout.error = err
                    return@setOnClickListener
                }
                inputLayout.error = null

                registerAsParent(childUid, normalized!!)
                dialog.dismiss()
            }

            editText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (inputLayout.error != null) inputLayout.error = null
                }

                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        dialog.show()

    }

    private fun validateAndNormalizeChildName(input: String): Pair<String?, String?> {
        // return: (normalizedName, errorMessage)
        val raw = input.trim().replace(Regex("""[ 　]+"""), " ") // 半角/全角スペースを連続→1個

        if (raw.isEmpty()) return Pair(null, "名前を入力してください。")
        if (raw.length !in 1..15) return Pair(null, "名前は1〜15文字で入力してください。")

        val re = Regex("""^[0-9A-Za-zぁ-んァ-ン一-龥ー・ 　]+$""")
        if (!re.matches(raw)) {
            return Pair(null, "使える文字は「英数字/ひらがな/カタカナ/漢字/スペース/・/ー」のみです。")
        }

        return Pair(raw, null)
    }

    // ▼▼▼ 修正: 親子双方にデータを書き込む ▼▼▼
    private fun registerTokenToFirestore(childUid: String, myUid: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener

            val token = task.result
            val db = FirebaseFirestore.getInstance()
            val timestamp = FieldValue.serverTimestamp()

            // 1. 子供のデータに「私のトークン」を登録 (通知用)
            val parentData = hashMapOf(
                "fcmToken" to token,
                "role" to "parent",
                "registeredAt" to timestamp
            )
            val task1 = db.collection("users").document(childUid)
                .collection("parents").document(myUid)
                .set(parentData)

            // 2. 自分のデータに「管理している子供」を登録 (表示用)
            val childData = hashMapOf(
                "role" to "child",
                "registeredAt" to timestamp
            )
            val task2 = db.collection("users").document(myUid)
                .collection("children").document(childUid)
                .set(childData)

            // 両方完了したら成功
            com.google.android.gms.tasks.Tasks.whenAll(task1, task2)
                .addOnSuccessListener {
                    Toast.makeText(this, "ペアリング完了！\n通知が届くようになりました。", Toast.LENGTH_LONG).show()
                    updateConnectionStatus() // 画面を即座に更新
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "登録失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}

class CaptureActivityPortrait : com.journeyapps.barcodescanner.CaptureActivity()