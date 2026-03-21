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
import android.widget.LinearLayout
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class AdminSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private var isAuthenticated: Boolean = false
    private lateinit var scrollView: ScrollView

    private lateinit var textManager: TextView
    private lateinit var containerManagedChildren: LinearLayout

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
            val childUid = result.contents
            promptForChildNameAndRegister(childUid)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)
        settings = AppSettings(this)
        scrollView = findViewById(R.id.scroll_admin)

        textManager = findViewById(R.id.text_connected_manager)
        containerManagedChildren = findViewById(R.id.container_managed_children)

        isAuthenticated = savedInstanceState?.getBoolean("authenticated", false) ?: false

        if (intent.getBooleanExtra("isLongPressRoute", false)) {
            isAuthenticated = true
        }

        setupAdminSecurityViews()
        setupExistingControls()
        setupAccordions()

        findViewById<MaterialButton>(R.id.button_scan_parent_qr)?.setOnClickListener {
            val options = ScanOptions()
            options.setPrompt("枠内にお子様のQRコードを写してください")
            options.setBeepEnabled(false)
            options.setOrientationLocked(true)
            options.setCaptureActivity(CaptureActivityPortrait::class.java)
            barcodeLauncher.launch(options)
        }
        findViewById<TextView>(R.id.text_privacy_policy)?.setOnClickListener {
            startActivity(Intent(this, com.example.studylockapp.ui.PrivacyPolicyActivity::class.java))
        }
    }

    private fun setupAccordions() {
        val groups = listOf(
            Triple(R.id.header_study_points, R.id.content_study_points, R.id.arrow_study_points),
            Triple(R.id.header_test_points, R.id.content_test_points, R.id.arrow_test_points),
            Triple(R.id.header_time_settings, R.id.content_time_settings, R.id.arrow_time_settings),
            Triple(R.id.header_app_lock_block, R.id.content_app_lock_block, R.id.arrow_app_lock_block),
            Triple(R.id.header_pairing, R.id.content_pairing, R.id.arrow_pairing),
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
        updateConnectionStatus()
        refreshGradeSpinner()
    }

    private fun refreshGradeSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinner_current_learning_grade)
        val grades = listOf("未設定", "1級", "準1級", "2級", "準2級", "3級", "4級", "5級")

        val current = GradeUtils.toDisplay(settings.safeLearningGrade)

        val index = grades.indexOf(current).takeIf { it >= 0 } ?: 0

        spinner.setSelection(index)
    }


    private fun updateConnectionStatus() {
        if (!::textManager.isInitialized || !::containerManagedChildren.isInitialized) return

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            textManager.text = getString(R.string.pairing_parent_none)
            containerManagedChildren.removeAllViews()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val myUid = user.uid

        // 1. 親端末の取得
        db.collection("users").document(myUid).collection("parents").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null || snapshot.isEmpty) {
                    textManager.text = getString(R.string.pairing_parent_none)
                    settings.setParentUid(null)
                } else {
                    val count = snapshot.size()
                    textManager.text = getString(R.string.pairing_parent_device, count)
                    settings.setParentUid(snapshot.documents[0].id)
                }
            }
            .addOnFailureListener {
                textManager.text = getString(R.string.pairing_parent_error)
            }

        // 2. 子端末のリスト表示
        containerManagedChildren.removeAllViews()
        db.collection("users").document(myUid).collection("children").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null || snapshot.isEmpty) {
                    val emptyView = TextView(this).apply {
                        text = getString(R.string.pairing_child_none)
                        setTextColor(switchTextColor)
                        setPadding(0, 16, 0, 16)
                        textSize = 14f
                    }
                    containerManagedChildren.addView(emptyView)
                } else {
                    snapshot.documents.forEach { doc ->
                        val childId = doc.id
                        val childName = doc.getString("displayName") 
                            ?: doc.getString("name") 
                            ?: "不明なデバイス"

                        val itemView = layoutInflater.inflate(R.layout.item_child_device, containerManagedChildren, false)
                        itemView.findViewById<TextView>(R.id.text_child_name).text = childName
                        itemView.findViewById<TextView>(R.id.text_child_id).text = getString(R.string.pairing_child_id_format, childId.take(10))
                        
                        itemView.findViewById<View>(R.id.btn_delete_child).setOnClickListener {
                            it.isEnabled = false // 二重クリック防止
                            MaterialAlertDialogBuilder(this)
                                .setTitle(coloredTitle(getString(R.string.pairing_delete_confirm_title)))
                                .setMessage(getString(R.string.pairing_delete_confirm_msg, childName))
                                .setPositiveButton(R.string.pairing_delete_action) { _, _ ->
                                    deleteChildRelationship(myUid, childId)
                                }
                                .setNegativeButton(R.string.cancel) { _, _ ->
                                    it.isEnabled = true
                                }
                                .setOnCancelListener { _ -> it.isEnabled = true }
                                .show()
                        }
                        containerManagedChildren.addView(itemView)
                    }
                }
            }
            .addOnFailureListener {
                val errorView = TextView(this).apply {
                    text = getString(R.string.pairing_child_error)
                    setTextColor(switchTextColor)
                }
                containerManagedChildren.addView(errorView)
            }
    }

    private fun deleteChildRelationship(parentUid: String, childUid: String) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        val parentRef = db.collection("users").document(childUid).collection("parents").document(parentUid)
        val childRef = db.collection("users").document(parentUid).collection("children").document(childUid)

        batch.delete(parentRef)
        batch.delete(childRef)

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.pairing_delete_success), Toast.LENGTH_SHORT).show()
                updateConnectionStatus()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.pairing_delete_failure), Toast.LENGTH_SHORT).show()
                updateConnectionStatus() // ボタンを有効に戻すため
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
        val spinnerCurrentGrade = findViewById<Spinner>(R.id.spinner_current_learning_grade)
        val grades = listOf("未設定", "1級", "準1級", "2級", "準2級", "3級", "4級", "5級")
        val gradeAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, grades) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.BLACK)
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setBackgroundColor(Color.WHITE)
                if (position == 0) {
                    view.setTextColor(Color.GRAY)
                } else {
                    view.setTextColor(Color.BLACK)
                }
                return view
            }
        }
        gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrentGrade.adapter = gradeAdapter

        val currentGrade = GradeUtils.toDisplay(settings.safeLearningGrade)
        val index = if (currentGrade.isBlank()) {
            0 // 未設定
        } else {
            grades.indexOf(currentGrade).takeIf { it >= 0 } ?: 0
        }
        spinnerCurrentGrade.setSelection(index)

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
        val textDontKnowRetry = findViewById<TextView>(R.id.text_dont_know_retry)
        val seekDontKnowRetry = findViewById<SeekBar>(R.id.seek_dont_know_retry)
        val textUnlockMinPer10Pt = findViewById<TextView>(R.id.text_unlock_min_per_10pt_value)
        val seekUnlockMinPer10Pt = findViewById<SeekBar>(R.id.seek_unlock_min_per_10pt)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)

        findViewById<MaterialButton>(R.id.button_open_timezone_setup)?.apply { visibility = View.GONE }
        findViewById<MaterialButton>(R.id.button_app_lock_settings)?.setOnClickListener { startActivity(Intent(this, AppLockSettingsActivity::class.java)) }
        findViewById<MaterialButton>(R.id.button_show_qr)?.setOnClickListener { startActivity(Intent(this, QrCodeActivity::class.java)) }

        seekInterval.max = 19
        seekWrongRetry.max = 118
        seekLevel1Retry.max = 118
        seekDontKnowRetry.max = 19

        fun intervalMsToProgress(ms: Long): Int = ((ms.coerceIn(500L, 10_000L) - 500L) / 500L).toInt()
        fun progressToIntervalMs(progress: Int): Long = 500L + (progress.coerceIn(0, 19) * 500L)
        fun secToProgress(sec: Long): Int = ((sec.coerceIn(10L, 600L) - 10L) / 5L).toInt()
        fun progressToSec(progress: Int): Long = 10L + (progress.coerceIn(0, 118) * 5L)
        fun minPer10PtToProgress(value: Int): Int = value.coerceIn(1, 10) - 1
        fun progressToMinPer10Pt(progress: Int): Int = progress.coerceIn(0, 9) + 1
        fun dontKnowSecToProgress(sec: Long): Int = ((sec.coerceIn(5L, 100L) - 5L) / 5L).toInt()
        fun progressToDontKnowSec(progress: Int): Long = 5L + (progress.coerceIn(0, 19) * 5L)

        seekInterval.progress = intervalMsToProgress(settings.answerIntervalMs)
        seekWrongRetry.progress = secToProgress(settings.wrongRetrySec)
        seekLevel1Retry.progress = secToProgress(settings.level1RetrySec)
        seekUnlockMinPer10Pt.progress = minPer10PtToProgress(settings.getUnlockMinutesPer10Pt())
        seekDontKnowRetry.progress = dontKnowSecToProgress(settings.dontKnowRetrySec)

        fun refreshLabels() {
            val sec = progressToIntervalMs(seekInterval.progress) / 1000f
            textInterval.text = getString(R.string.admin_label_interval_sec, sec)
            textWrongRetry.text = getString(R.string.admin_label_wrong_retry_sec, progressToSec(seekWrongRetry.progress))
            textLevel1Retry.text = getString(R.string.admin_label_level1_retry_sec, progressToSec(seekLevel1Retry.progress))
            textDontKnowRetry.text = getString(R.string.admin_label_dont_know_retry_sec, progressToDontKnowSec(seekDontKnowRetry.progress))
            textUnlockMinPer10Pt.text = getString(R.string.admin_label_unlock_min_per_10pt_value, progressToMinPer10Pt(seekUnlockMinPer10Pt.progress))
        }
        refreshLabels()

        val commonListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { refreshLabels() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        listOf(seekInterval, seekWrongRetry, seekLevel1Retry, seekDontKnowRetry, seekUnlockMinPer10Pt).forEach { it.setOnSeekBarChangeListener(commonListener) }

        btnSave.setOnClickListener {
            val selected = spinnerCurrentGrade.selectedItem?.toString() ?: "未設定"
            settings.currentLearningGrade =
                if (selected == "未設定") "" else GradeUtils.normalize(selected)
            settings.pointReductionOneGradeDown = 50
            settings.pointReductionTwoGradesDown = 25
            modes.forEach { (mode, views) -> val (_, seekBar) = views; if (seekBar != null) settings.setBasePoint(mode, progressToPoint(seekBar.progress)) }
            settings.answerIntervalMs = progressToIntervalMs(seekInterval.progress)
            settings.wrongRetrySec = progressToSec(seekWrongRetry.progress)
            settings.level1RetrySec = progressToSec(seekLevel1Retry.progress)
            settings.dontKnowRetrySec = progressToDontKnowSec(seekDontKnowRetry.progress)
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
            "test_fill_blank" -> getString(R.string.mode_test_fill_blank)
            "test_sort" -> getString(R.string.mode_test_sort)
            "test_listen_q1" -> getString(R.string.mode_test_listen_q1)
            "test_listen_q2" -> getString(R.string.mode_test_listen_q2)
            else -> mode.replace("_", " ").replaceFirstChar { it.uppercase() }
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

        // 色設定
        switchAdminLock.setTextColor(switchTextColor)
        switchAppLockRequired?.setTextColor(switchTextColor)
        switchEnableLongPress.setTextColor(switchTextColor)
        switchAccessibilityLock?.setTextColor(switchTextColor)
        switchTetheringLock?.setTextColor(switchTextColor)
        switchUninstallLock?.setTextColor(switchTextColor)

        // 初期状態セット
        switchAdminLock.isChecked = AdminAuthManager.isAdminLockEnabled(this)
        switchAppLockRequired?.isChecked = AdminAuthManager.isAppLockRequired(this)
        switchEnableLongPress.isChecked = settings.isEnableAdminLongPress()

        // ⭐ PrefsManager → settings に変更
        switchAccessibilityLock?.isChecked = settings.isAccessibilityLockEnabled
        switchTetheringLock?.isChecked = settings.isTetheringLockEnabled

        switchUninstallLock?.isChecked = settings.isUninstallLockEnabled()

        // 管理者ロック
        switchAdminLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!AdminAuthManager.isPinSet(this)) {
                    promptSetNewPin(
                        onSuccess = {
                            AdminAuthManager.setAdminLockEnabled(this, true)
                            showToast(getString(R.string.admin_lock_enabled))
                        },
                        onCancel = { switchAdminLock.isChecked = false }
                    )
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

        // アプリロック必須
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

        // 各設定保存
        switchEnableLongPress.setOnCheckedChangeListener { _, isChecked ->
            settings.setEnableAdminLongPress(isChecked)
        }

        switchAccessibilityLock?.setOnCheckedChangeListener { _, isChecked ->
            settings.isAccessibilityLockEnabled = isChecked
        }

        switchTetheringLock?.setOnCheckedChangeListener { _, isChecked ->
            settings.isTetheringLockEnabled = isChecked
        }

        switchUninstallLock?.setOnCheckedChangeListener { _, isChecked ->
            settings.setUninstallLockEnabled(isChecked)
        }

        // PIN変更
        buttonChangePin.setOnClickListener {
            promptPinAndDo(
                title = getString(R.string.admin_enter_pin_title),
                onSuccess = { promptSetNewPin() },
                onFailure = { showToast(getString(R.string.admin_pin_incorrect)) }
            )
        }

        // 認証アプリ
        buttonSetupAuthenticator?.setOnClickListener {
            startActivity(Intent(this, AuthenticatorSetupActivity::class.java))
        }
    }

    private fun promptPinAndDo(title: String, onSuccess: () -> Unit, onFailure: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
        val inputLayout = TextInputLayout(this).apply { hint = getString(R.string.admin_enter_pin_hint); endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE }
        val edit = TextInputEditText(inputLayout.context).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; setTextColor(dialogTextColor); setHintTextColor(dialogHintColor) }
        inputLayout.addView(edit)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(coloredTitle(title)).setView(inputLayout).setPositiveButton(R.string.ok) { _, _ -> val pin = edit.text?.toString().orEmpty(); if (AdminAuthManager.verifyPin(this, pin)) onSuccess() else onFailure?.invoke() }.setNegativeButton(R.string.cancel) { _, _ -> onCancel?.invoke() }.setOnCancelListener { onCancel?.invoke() }
        if (settings.hasParent()) { dialog.setNeutralButton("管理者に聞く") { _, _ -> promptRemoteUnlock(onSuccess) } } else if (AdminAuthManager.isTotpSet(this)) { dialog.setNeutralButton(R.string.admin_forgot_pin) { _, _ -> promptTotpAndResetPin(onSuccess) } }
        dialog.show()
    }

    private fun promptRemoteUnlock(onSuccess: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) { auth.signInAnonymously().addOnSuccessListener { promptRemoteUnlock(onSuccess) }.addOnFailureListener { Toast.makeText(this, "ログイン失敗", Toast.LENGTH_SHORT).show() }; return }
        val randomCode = (100000..999999).random().toString()
        val functions = FirebaseFunctions.getInstance("asia-northeast1")
        val data = hashMapOf("code" to randomCode, "uid" to user.uid)
        Toast.makeText(this, "管理者に解除コードを送信中...", Toast.LENGTH_SHORT).show()
        functions.getHttpsCallable("requestUnlockCode").call(data).addOnSuccessListener { Toast.makeText(this, "通知を送信しました！", Toast.LENGTH_SHORT).show() }.addOnFailureListener { e -> Toast.makeText(this, "送信エラー: ${e.message}", Toast.LENGTH_LONG).show() }
        showUnlockDialog(randomCode, onSuccess)
    }

    private fun showUnlockDialog(correctCode: String, onSuccess: () -> Unit) {
        val inputLayout = TextInputLayout(this).apply { hint = "届いたコードを入力" }
        val edit = TextInputEditText(inputLayout.context).apply { inputType = InputType.TYPE_CLASS_NUMBER; setTextColor(dialogTextColor); setHintTextColor(dialogHintColor) }
        inputLayout.addView(edit)
        MaterialAlertDialogBuilder(this).setTitle(coloredTitle("解除コード入力")).setMessage("管理者の端末に通知された6桁の数字を入力してください。").setView(inputLayout).setPositiveButton("解除") { _, _ -> if (edit.text?.toString().orEmpty() == correctCode) { showToast("認証成功！PINをリセットします。"); promptSetNewPin(onSuccess) } else { showToast("コードが違います") } }.setNegativeButton("キャンセル", null).show()
    }

    private fun promptTotpAndResetPin(onSuccess: () -> Unit) {
        val inputLayout = TextInputLayout(this).apply { hint = getString(R.string.totp_verify_hint) }
        val edit = TextInputEditText(inputLayout.context).apply { inputType = InputType.TYPE_CLASS_NUMBER; setTextColor(dialogTextColor); setHintTextColor(dialogHintColor) }
        inputLayout.addView(edit)
        MaterialAlertDialogBuilder(this).setTitle(coloredTitle(getString(R.string.totp_enter_code_title))).setView(inputLayout).setPositiveButton(R.string.ok) { _, _ -> if (AdminAuthManager.verifyTotp(this, edit.text?.toString().orEmpty())) { showToast(getString(R.string.totp_reset_pin_success)); promptSetNewPin(onSuccess) } else { showToast(getString(R.string.totp_incorrect)); promptTotpAndResetPin(onSuccess) } }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun promptSetNewPin(onSuccess: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_set_new_pin, null)
        val input1 = view.findViewById<TextInputEditText>(R.id.edit_new_pin).apply { setTextColor(dialogTextColor); setHintTextColor(dialogHintColor) }
        val input2 = view.findViewById<TextInputEditText>(R.id.edit_new_pin_confirm).apply { setTextColor(dialogTextColor); setHintTextColor(dialogHintColor) }
        MaterialAlertDialogBuilder(this).setTitle(coloredTitle(getString(R.string.admin_set_new_pin_title))).setView(view).setPositiveButton(R.string.ok) { _, _ -> val p1 = input1.text?.toString().orEmpty(); val p2 = input2.text?.toString().orEmpty(); if (p1.length < 4) { showToast(getString(R.string.admin_pin_length_error)); onCancel?.invoke(); return@setPositiveButton }; if (p1 != p2) { showToast(getString(R.string.admin_pin_mismatch)); onCancel?.invoke(); return@setPositiveButton }; AdminAuthManager.setPin(this, p1); onSuccess?.invoke() }.setNegativeButton(R.string.cancel) { _, _ -> onCancel?.invoke() }.setOnCancelListener { onCancel?.invoke() }.show()
    }

    private fun coloredTitle(text: String): CharSequence { val s = SpannableString(text); s.setSpan(ForegroundColorSpan(dialogTitleColor), 0, s.length, 0); return s }
    private fun showToast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    private fun registerAsParent(childUid: String, childName: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val myUid = user.uid
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result
            val parentData = hashMapOf("uid" to myUid, "role" to "parent", "fcmToken" to token, "childDisplayName" to childName, "timestamp" to com.google.firebase.Timestamp.now())
            val task1 = db.collection("users").document(childUid).collection("parents").document(myUid).set(parentData)
            val childData = hashMapOf("uid" to childUid, "role" to "child", "displayName" to childName, "timestamp" to com.google.firebase.Timestamp.now())
            val task2 = db.collection("users").document(myUid).collection("children").document(childUid).set(childData)
            val task3 = db.collection("users").document(myUid).set(hashMapOf("role" to "parent"), com.google.firebase.firestore.SetOptions.merge())
            com.google.android.gms.tasks.Tasks.whenAll(task1, task2, task3)
                .addOnSuccessListener { 
                    Toast.makeText(this, "ペアリング完了！\n${childName}さんを登録しました", Toast.LENGTH_LONG).show()
                    updateConnectionStatus() 
                }
                .addOnFailureListener {
                    Toast.makeText(this, getString(R.string.pairing_register_failure), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun promptForChildNameAndRegister(childUid: String) {
        val inputLayout = TextInputLayout(this).apply { hint = "お子様の名前 (通知に表示されます)"; isErrorEnabled = true }
        val editText = TextInputEditText(inputLayout.context).apply { inputType = InputType.TYPE_CLASS_TEXT; filters = arrayOf(android.text.InputFilter.LengthFilter(20)); setTextColor(dialogTextColor); setHintTextColor(dialogHintColor) }
        inputLayout.addView(editText)
        val msg = SpannableString("通知に表示されるお子様の名前を入力してください。").apply { setSpan(ForegroundColorSpan(dialogTextColor), 0, length, 0) }
        val dialog = MaterialAlertDialogBuilder(this).setTitle(coloredTitle("管理対象の追加")).setMessage(msg).setView(inputLayout).setPositiveButton("登録", null).setNegativeButton("キャンセル", null).create()
        dialog.setOnShowListener { val okBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE); okBtn.setOnClickListener { val (normalized, err) = validateAndNormalizeChildName(editText.text?.toString().orEmpty()); if (err != null) { inputLayout.error = err; return@setOnClickListener }; inputLayout.error = null; registerAsParent(childUid, normalized!!); dialog.dismiss() }; editText.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (inputLayout.error != null) inputLayout.error = null }; override fun afterTextChanged(s: android.text.Editable?) {} }) }
        dialog.show()
    }

    private fun validateAndNormalizeChildName(input: String): Pair<String?, String?> {
        val raw = input.trim().replace(Regex("""[ 　]+"""), " ")
        if (raw.isEmpty()) return Pair(null, "名前を入力してください。")
        if (raw.length !in 1..15) return Pair(null, "名前は1〜15文字で入力してください。")
        if (!Regex("""^[0-9A-Za-zぁ-んァ-ン一-龥ー・ 　]+$""").matches(raw)) return Pair(null, "使える文字は「英数字/ひらがな/カタカナ/漢字/スペース/・/ー」のみです。")
        return Pair(raw, null)
    }
}

class CaptureActivityPortrait : com.journeyapps.barcodescanner.CaptureActivity()