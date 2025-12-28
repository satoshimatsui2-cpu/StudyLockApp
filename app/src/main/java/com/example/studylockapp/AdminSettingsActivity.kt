package com.example.studylockapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.ads.AdAudioManager
import com.example.studylockapp.data.AdminAuthManager
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.service.AccessibilityUtils
import com.example.studylockapp.service.AppLockAccessibilityService
import com.example.studylockapp.ui.applock.AppLockSettingsActivity
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AdminSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private var isAuthenticated: Boolean = false

    // スイッチ用（濃い目）
    private val switchTextColor by lazy {
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    }

    // ダイアログ入力用：タイトルも入力文字も白固定、ヒントは薄いグレー
    private val dialogTitleColor: Int = Color.WHITE
    private val dialogTextColor: Int = Color.WHITE
    private val dialogHintColor: Int = Color.LTGRAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)
        settings = AppSettings(this)

        isAuthenticated = savedInstanceState?.getBoolean("authenticated", false) ?: false

        setupAdminSecurityViews()
        setupExistingControls()
    }

    override fun onResume() {
        super.onResume()
        ensureAuthenticatedOrFinish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("authenticated", isAuthenticated)
    }

    /**
     * 管理者画面ロックがONなら毎回PINを要求。失敗/キャンセルでActivity終了。
     */
    private fun ensureAuthenticatedOrFinish() {
        if (!AdminAuthManager.isAdminLockEnabled(this)) return
        if (isAuthenticated) return

        promptPinAndDo(
            title = getString(R.string.admin_enter_pin_title),
            onSuccess = {
                isAuthenticated = true
                showToast(getString(R.string.admin_pin_ok))
            },
            onFailure = { finish() },
            onCancel = { finish() }
        )
    }

    /**
     * 既存のシークバー等の設定UI（音量関連は削除済み）
     */
    private fun setupExistingControls() {
        // SeekBar / TextView 群
        val textInterval = findViewById<TextView>(R.id.text_interval)
        val seekInterval = findViewById<SeekBar>(R.id.seek_interval)

        val textWrongRetry = findViewById<TextView>(R.id.text_wrong_retry)
        val seekWrongRetry = findViewById<SeekBar>(R.id.seek_wrong_retry)

        val textLevel1Retry = findViewById<TextView>(R.id.text_level1_retry)
        val seekLevel1Retry = findViewById<SeekBar>(R.id.seek_level1_retry)

        // 10pt あたり分数（SeekBar）
        val textUnlockMinPer10Pt = findViewById<TextView>(R.id.text_unlock_min_per_10pt_value)
        val seekUnlockMinPer10Pt = findViewById<SeekBar>(R.id.seek_unlock_min_per_10pt)

        val btnSave = findViewById<MaterialButton>(R.id.btn_save)

        // タイムゾーン設定へ
        findViewById<MaterialButton>(R.id.button_open_timezone_setup)?.setOnClickListener {
            startActivity(Intent(this, TimeZoneSetupActivity::class.java))
        }

        // アプリロック設定へ
        findViewById<MaterialButton>(R.id.button_app_lock_settings)?.setOnClickListener {
            startActivity(Intent(this, AppLockSettingsActivity::class.java))
        }

        fun intervalMsToProgress(ms: Long): Int {
            val clamped = ms.coerceIn(500L, 10_000L)
            return ((clamped - 500L) / 100L).toInt() // 0..95
        }
        fun progressToIntervalMs(progress: Int): Long =
            500L + (progress.coerceIn(0, 95) * 100L)

        // リトライ秒（10..600）
        fun secToProgress(sec: Long): Int {
            val clamped = sec.coerceIn(10L, 600L)
            return (clamped - 10L).toInt() // 0..590
        }
        fun progressToSec(progress: Int): Long =
            (progress.coerceIn(0, 590) + 10).toLong() // 10..600

        // 10ptあたり分数（1..10）: progress 0..9 → 値 1..10
        fun minPer10PtToProgress(value: Int): Int = value.coerceIn(1, 10) - 1
        fun progressToMinPer10Pt(progress: Int): Int = progress.coerceIn(0, 9) + 1

        // 初期値反映
        seekInterval.progress = intervalMsToProgress(settings.answerIntervalMs)
        seekWrongRetry.progress = secToProgress(settings.wrongRetrySec)
        seekLevel1Retry.progress = secToProgress(settings.level1RetrySec)
        seekUnlockMinPer10Pt.progress = minPer10PtToProgress(settings.getUnlockMinutesPer10Pt())

        fun refreshLabels() {
            val sec = progressToIntervalMs(seekInterval.progress) / 1000f
            textInterval.text = getString(R.string.admin_label_interval_sec, sec)

            textWrongRetry.text =
                getString(R.string.admin_label_wrong_retry_sec, progressToSec(seekWrongRetry.progress))
            textLevel1Retry.text =
                getString(R.string.admin_label_level1_retry_sec, progressToSec(seekLevel1Retry.progress))

            textUnlockMinPer10Pt.text = getString(
                R.string.admin_label_unlock_min_per_10pt_value,
                progressToMinPer10Pt(seekUnlockMinPer10Pt.progress)
            )
        }
        refreshLabels()

        val commonListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        listOf(
            seekInterval,
            seekWrongRetry,
            seekLevel1Retry,
            seekUnlockMinPer10Pt
        ).forEach { it.setOnSeekBarChangeListener(commonListener) }

        btnSave.setOnClickListener {
            settings.answerIntervalMs = progressToIntervalMs(seekInterval.progress)
            settings.wrongRetrySec = progressToSec(seekWrongRetry.progress)
            settings.level1RetrySec = progressToSec(seekLevel1Retry.progress)

            val minPer10Pt = progressToMinPer10Pt(seekUnlockMinPer10Pt.progress)
            settings.setUnlockMinutesPer10Pt(minPer10Pt)

            AdAudioManager.apply(settings)
            finish()
        }
    }

    /**
     * 管理者ロック／アプリロック必須／PIN変更／復旧コードのUI初期化
     * （対応するViewがレイアウトに無い場合は安全にreturn）
     */
    private fun setupAdminSecurityViews() {
        val switchAdminLock = findViewById<SwitchMaterial>(R.id.switch_admin_lock) ?: return
        val switchAppLockRequired = findViewById<SwitchMaterial>(R.id.switch_app_lock_required)
        val buttonChangePin = findViewById<MaterialButton>(R.id.button_change_pin) ?: return
        val buttonForgotPin = findViewById<MaterialButton>(R.id.button_forgot_pin) ?: return

        // スイッチ文字色を濃く
        switchAdminLock.setTextColor(switchTextColor)
        switchAppLockRequired?.setTextColor(switchTextColor)

        switchAdminLock.isChecked = AdminAuthManager.isAdminLockEnabled(this)
        switchAppLockRequired?.isChecked = AdminAuthManager.isAppLockRequired(this)

        // 管理者画面ロック ON/OFF
        switchAdminLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!AdminAuthManager.isPinSet(this)) {
                    promptSetNewPin(onSuccess = {
                        AdminAuthManager.setAdminLockEnabled(this, true)
                        showToast(getString(R.string.admin_lock_enabled))
                    }, onCancel = {
                        switchAdminLock.isChecked = false
                    })
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
            if (isChecked) {
                // 必須ONと同時にアプリロック自体も有効化
                settings.setAppLockEnabled(true)
            }
            if (isChecked && !AccessibilityUtils.isServiceEnabled(this, AppLockAccessibilityService::class.java)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                showToast(getString(R.string.admin_app_lock_required_on))
            } else if (isChecked) {
                showToast(getString(R.string.admin_app_lock_required_on))
            }
        }

        // PIN変更
        buttonChangePin.setOnClickListener {
            promptPinAndDo(
                title = getString(R.string.admin_enter_pin_title),
                onSuccess = { promptSetNewPin() },
                onFailure = { showToast(getString(R.string.admin_pin_incorrect)) }
            )
        }

        // PIN忘れ（復旧コードでリセット）
        buttonForgotPin.setOnClickListener {
            promptRecoveryAndResetPin()
        }
    }

    /** PIN入力ダイアログ */
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

        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(title))
            .setView(inputLayout)
            .setPositiveButton(R.string.ok) { _, _ ->
                val pin = edit.text?.toString().orEmpty()
                if (AdminAuthManager.verifyPin(this, pin)) {
                    onSuccess()
                } else {
                    onFailure?.invoke()
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel?.invoke() }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
    }

    /** 新しいPIN設定（確認入力あり） */
    private fun promptSetNewPin(
        onSuccess: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_set_new_pin, null)
        val input1 = view.findViewById<TextInputEditText>(R.id.edit_new_pin).apply {
            setTextColor(dialogTextColor); setHintTextColor(dialogHintColor)
        }
        val input2 = view.findViewById<TextInputEditText>(R.id.edit_new_pin_confirm).apply {
            setTextColor(dialogTextColor); setHintTextColor(dialogHintColor)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(getString(R.string.admin_set_new_pin_title)))
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val p1 = input1.text?.toString().orEmpty()
                val p2 = input2.text?.toString().orEmpty()
                if (p1.length < 4) {
                    showToast(getString(R.string.admin_pin_length_error))
                    onCancel?.invoke()
                    return@setPositiveButton
                }
                if (p1 != p2) {
                    showToast(getString(R.string.admin_pin_mismatch))
                    onCancel?.invoke()
                    return@setPositiveButton
                }
                AdminAuthManager.setPin(this, p1)

                // 復旧コードが未設定なら生成して保存 → ダイアログで表示
                if (!AdminAuthManager.isRecoveryCodeSet(this)) {
                    val code = AdminAuthManager.generateRecoveryCode()
                    AdminAuthManager.setRecoveryCode(this, code)
                    showRecoveryCodeDialog(code)
                }
                onSuccess?.invoke()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel?.invoke() }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
    }

    /** 復旧コード→新PIN設定 */
    private fun promptRecoveryAndResetPin() {
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.admin_enter_recovery_hint)
        }
        val edit = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setTextColor(dialogTextColor)
            setHintTextColor(dialogHintColor)
        }
        inputLayout.addView(edit)

        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(getString(R.string.admin_recovery_title)))
            .setView(inputLayout)
            .setPositiveButton(R.string.ok) { _, _ ->
                val code = edit.text?.toString().orEmpty()
                if (AdminAuthManager.verifyRecoveryCode(this, code)) {
                    promptSetNewPin()
                } else {
                    showToast(getString(R.string.admin_recovery_incorrect))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 復旧コード表示ダイアログ（コピー可能） */
    private fun showRecoveryCodeDialog(code: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(getString(R.string.admin_recovery_title)))
            .setMessage(getString(R.string.admin_recovery_show_dialog, code))
            .setCancelable(false)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.copy) { _, _ ->
                cm.setPrimaryClip(ClipData.newPlainText("recovery", code))
                showToast(getString(R.string.copied))
            }
            .show()
    }

    /** タイトルを明るい色で表示 */
    private fun coloredTitle(text: String): CharSequence {
        val s = SpannableString(text)
        s.setSpan(ForegroundColorSpan(dialogTitleColor), 0, s.length, 0)
        return s
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}