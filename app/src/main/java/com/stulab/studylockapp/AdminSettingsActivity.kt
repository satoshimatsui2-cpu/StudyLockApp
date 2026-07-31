package com.stulab.studylockapp

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.stulab.studylockapp.R
import com.stulab.studylockapp.ads.AdAudioManager
import com.stulab.studylockapp.data.AdminAuthManager
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.learning.QuizMode
import com.stulab.studylockapp.service.AccessibilityUtils
import com.stulab.studylockapp.service.AppLockAccessibilityService
import com.stulab.studylockapp.ui.PrivacyPolicyActivity
import com.stulab.studylockapp.ui.QrCodeActivity
import com.stulab.studylockapp.ui.alert.AppDialogHelper
import com.stulab.studylockapp.ui.applock.AppLockSettingsActivity
import com.stulab.studylockapp.ui.setup.AuthenticatorSetupActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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

    private val switchTextColor by lazy {
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    }

    private val dialogTitleColor: Int by lazy { ContextCompat.getColor(this, R.color.text_main) }
    private val dialogTextColor: Int by lazy { ContextCompat.getColor(this, R.color.text_main) }
    private val dialogHintColor: Int by lazy { ContextCompat.getColor(this, R.color.text_sub) }

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        val payload = result.contents
        if (payload == null) {
            Toast.makeText(this, "スキャンをキャンセルしました", Toast.LENGTH_SHORT).show()
        } else {
            if (isValidChildUid(payload)) {
                promptForChildNameAndRegister(payload)
            } else {
                Toast.makeText(this, "無効なQRコードです", Toast.LENGTH_LONG).show()
                Log.e("AdminSettings", "Invalid QR payload: $payload")
            }
        }
    }

    private fun isValidChildUid(uid: String): Boolean {
        if (uid.isBlank()) return false
        if (uid.contains("/") || uid.contains("\\")) return false
        if (uid.length < 10 || uid.length > 128) return false
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)

        val root = findViewById<View>(R.id.admin_settings_root)
        val initialTop = root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                initialTop + bars.top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        settings = AppSettings(this)
        scrollView = findViewById(R.id.scroll_admin)

        textManager = findViewById(R.id.text_connected_manager)
        containerManagedChildren = findViewById(R.id.container_managed_children)

        isAuthenticated = savedInstanceState?.getBoolean("authenticated", false) ?: false

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
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        findViewById<TextView>(R.id.text_privacy_policy_web)?.setOnClickListener {
            val url = "https://eigoforslacker-tokyo.web.app/privacy-policy.html"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "ブラウザを開けませんでした", Toast.LENGTH_SHORT).show()
                Log.e("AdminSettings", "Failed to open browser", e)
            }
        }

        ensureFirebaseAuthenticated()
    }

    private fun ensureFirebaseAuthenticated() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("AdminSettings", "Anonymous login success: ${it.user?.uid}")
                    updateConnectionStatus()
                }
                .addOnFailureListener { e ->
                    Log.e("AdminSettings", "Firebase Auth Failed", e)
                    Toast.makeText(this, "Firebase認証に失敗しました。接続を確認してください。", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun setupAccordions() {
        val groups = listOf(
            Triple(R.id.header_study_points, R.id.content_study_points, R.id.arrow_study_points),
            Triple(R.id.header_daily_goal, R.id.content_daily_goal, R.id.arrow_daily_goal),
            Triple(R.id.header_time_settings, R.id.content_time_settings, R.id.arrow_time_settings),
            Triple(R.id.header_app_lock_block, R.id.content_app_lock_block, R.id.arrow_app_lock_block),
            Triple(R.id.header_personal_wordbook, R.id.content_personal_wordbook, R.id.arrow_personal_wordbook),
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
        updateRecoveryStatusDisplay()
    }

    private fun refreshGradeSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinner_current_learning_grade)
        val grades = listOf("未設定", "1級", "準1級", "2級", "準2級", "3級", "4級", "5級")
        val current = GradeUtils.toDisplay(settings.targetLearningGrade)
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
                updateRecoveryStatusDisplay()
            }
            .addOnFailureListener { e ->
                textManager.text = getString(R.string.pairing_parent_error)
                Log.e("AdminSettings", "Failed to fetch parents", e)
            }

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
                            it.isEnabled = false
                            AppDialogHelper.showConfirm(
                                context = this,
                                title = getString(R.string.pairing_delete_confirm_title),
                                message = getString(R.string.pairing_delete_confirm_msg, childName),
                                positiveText = getString(R.string.pairing_delete_action),
                                negativeText = getString(R.string.cancel),
                                onPositive = {
                                    deleteChildRelationship(myUid, childId)
                                },
                                onNegative = {
                                    it.isEnabled = true
                                }
                            )
                        }
                        containerManagedChildren.addView(itemView)
                    }
                }
            }
            .addOnFailureListener { e ->
                val errorView = TextView(this).apply {
                    text = getString(R.string.pairing_child_error)
                    setTextColor(switchTextColor)
                }
                containerManagedChildren.addView(errorView)
                Log.e("AdminSettings", "Failed to fetch children for $myUid", e)
            }
    }

    private fun registerAsParent(childUid: String, childName: String) {
        val auth = FirebaseAuth.getInstance()
        var user = auth.currentUser

        if (user == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    registerAsParent(childUid, childName)
                }
                .addOnFailureListener { e ->
                    Log.e("AdminSettings", "Anonymous Auth failed in registerAsParent", e)
                    Toast.makeText(this, "認証エラーにより登録できません", Toast.LENGTH_SHORT).show()
                }
            return
        }

        val db = FirebaseFirestore.getInstance()
        val myUid = user.uid

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = if (task.isSuccessful) task.result else null
            if (!task.isSuccessful) Log.w("AdminSettings", "FCM token fetch failed", task.exception)

            performPairingWrites(db, myUid, childUid, childName, token)
        }
    }

    private fun performPairingWrites(db: FirebaseFirestore, parentUid: String, childUid: String, childName: String, parentToken: String?) {
        val timestamp = com.google.firebase.Timestamp.now()

        // 1. 親端末側のリスト用データ (最優先)
        val childData = hashMapOf(
            "uid" to childUid,
            "role" to "child",
            "displayName" to childName,
            "timestamp" to timestamp
        )

        db.collection("users").document(parentUid).collection("children").document(childUid)
            .set(childData)
            .addOnSuccessListener {
                Log.d("AdminSettings", "SUCCESS: users/$parentUid/children/$childUid written")
                updateConnectionStatus()
                Toast.makeText(this, "子端末を登録しました", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("AdminSettings", "FAILURE: users/$parentUid/children/$childUid", e)
                Toast.makeText(this, "親端末への登録に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }

        // 2. 子端末側の管理者データ
        val parentData = hashMapOf(
            "uid" to parentUid,
            "role" to "parent",
            "fcmToken" to parentToken,
            "childDisplayName" to childName,
            "timestamp" to timestamp
        )

        db.collection("users").document(childUid).collection("parents").document(parentUid)
            .set(parentData)
            .addOnSuccessListener {
                Log.d("AdminSettings", "SUCCESS: users/$childUid/parents/$parentUid written")
            }
            .addOnFailureListener { e ->
                Log.e("AdminSettings", "FAILURE: users/$childUid/parents/$parentUid. Check security rules.", e)
                Toast.makeText(this, "管理者連携（遠隔解除・通知等）の登録に失敗しました。子端末の設定や権限を確認してください。", Toast.LENGTH_LONG).show()
            }

        // 3. 親自身のロール更新
        db.collection("users").document(parentUid)
            .set(hashMapOf("role" to "parent"), SetOptions.merge())
            .addOnSuccessListener { Log.d("AdminSettings", "SUCCESS: parent role set") }
            .addOnFailureListener { e -> Log.e("AdminSettings", "FAILURE: setting parent role", e) }
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
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.pairing_delete_failure), Toast.LENGTH_SHORT).show()
                Log.e("AdminSettings", "Delete failed", e)
                updateConnectionStatus()
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
                return (super.getView(position, convertView, parent) as TextView).apply { setTextColor(Color.BLACK) }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setBackgroundColor(Color.WHITE)
                    setTextColor(if (position == 0) Color.GRAY else Color.BLACK)
                }
            }
        }
        gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrentGrade.adapter = gradeAdapter

        val currentTarget = GradeUtils.toDisplay(settings.targetLearningGrade)
        spinnerCurrentGrade.setSelection(grades.indexOf(currentTarget).takeIf { it >= 0 } ?: 0)

        val modes = mapOf(
            QuizMode.EN_TO_JP to (findViewById<TextView>(R.id.text_point_meaning) to findViewById<SeekBar>(R.id.seek_point_meaning)),
            QuizMode.LISTEN_EN to (findViewById<TextView>(R.id.text_point_listening) to findViewById<SeekBar>(R.id.seek_point_listening)),
            QuizMode.LISTEN_FILL_BLANK to (findViewById<TextView>(R.id.text_point_listening_jp) to findViewById<SeekBar>(R.id.seek_point_listening_jp)),
            QuizMode.JP_TO_EN to (findViewById<TextView>(R.id.text_point_ja_to_en) to findViewById<SeekBar>(R.id.seek_point_ja_to_en)),
            QuizMode.SYNONYM_PICK to (findViewById<TextView>(R.id.text_point_en_en_1) to findViewById<SeekBar>(R.id.seek_point_en_en_1)),
            QuizMode.ANTONYM_PICK to (findViewById<TextView>(R.id.text_point_en_en_2) to findViewById<SeekBar>(R.id.seek_point_en_en_2)),
        )

        fun progressToPoint(progress: Int): Int = 4 + progress * 4
        fun pointToProgress(point: Int): Int = (point - 4) / 4

        modes.forEach { (mode, views) ->
            val (textView, seekBar) = views
            if (textView != null && seekBar != null) {
                seekBar.max = 7
                seekBar.progress = pointToProgress(settings.getBasePoint(mode))
                textView.text = "${getQuizModeDisplayName(mode)}: ${progressToPoint(seekBar.progress)} pt"
                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { textView.text = "${getQuizModeDisplayName(mode)}: ${progressToPoint(progress)} pt" }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
        }

        val textWrongRetry = findViewById<TextView>(R.id.text_wrong_retry)
        val seekWrongRetry = findViewById<SeekBar>(R.id.seek_wrong_retry)
        val textLevel1Retry = findViewById<TextView>(R.id.text_level1_retry)
        val seekLevel1Retry = findViewById<SeekBar>(R.id.seek_level1_retry)
        val textDontKnowRetry = findViewById<TextView>(R.id.text_dont_know_retry)
        val seekDontKnowRetry = findViewById<SeekBar>(R.id.seek_dont_know_retry)
        val textUnlockMinPer10Pt = findViewById<TextView>(R.id.text_unlock_min_per_10pt_value)
        val seekUnlockMinPer10Pt = findViewById<SeekBar>(R.id.seek_unlock_min_per_10pt)
        
        val textTargetCount = findViewById<TextView>(R.id.text_target_count)
        val btnTargetMinus = findViewById<MaterialButton>(R.id.btn_target_minus)
        val btnTargetPlus = findViewById<MaterialButton>(R.id.btn_target_plus)
        val textSimTotalQuestions = findViewById<TextView>(R.id.text_sim_total_questions)
        val textSimTotalTime = findViewById<TextView>(R.id.text_sim_total_time)

        val btnSave = findViewById<MaterialButton>(R.id.btn_save)

        findViewById<View>(R.id.text_interval)?.visibility = View.GONE
        findViewById<View>(R.id.seek_interval)?.visibility = View.GONE
        findViewById<View>(R.id.button_open_timezone_setup)?.visibility = View.GONE

        findViewById<View>(R.id.button_app_lock_settings)?.setOnClickListener {
            val intent = Intent(this, AppLockSettingsActivity::class.java).apply {
                val isAuth = isAuthenticated || !AdminAuthManager.isAdminLockEnabled(this@AdminSettingsActivity)
                putExtra("isAuthenticated", isAuth)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.button_show_qr)?.setOnClickListener { startActivity(Intent(this, QrCodeActivity::class.java)) }

        fun secToProgress(sec: Long): Int = ((sec.coerceIn(10L, 600L) - 10L) / 5L).toInt()
        fun progressToSec(progress: Int): Long = 10L + (progress.coerceIn(0, 118) * 5L)
        fun minPer10PtToProgress(value: Int): Int = value.coerceIn(1, 10) - 1
        fun progressToMinPer10Pt(progress: Int): Int = progress.coerceIn(0, 9) + 1
        fun dontKnowSecToProgress(sec: Long): Int = ((sec.coerceIn(5L, 100L) - 5L) / 5L).toInt()
        fun progressToSecForDontKnow(progress: Int): Long = 5L + (progress.coerceIn(0, 19) * 5L)

        seekWrongRetry.progress = secToProgress(settings.wrongRetrySec)
        seekLevel1Retry.progress = secToProgress(settings.level1RetrySec)
        seekUnlockMinPer10Pt.progress = minPer10PtToProgress(settings.getUnlockMinutesPer10Pt())
        seekDontKnowRetry.progress = dontKnowSecToProgress(settings.dontKnowRetrySec)

        textTargetCount.text = settings.dailyNewWordTarget.toString()

        fun refreshLabels() {
            textWrongRetry.text = "当日再出題（不正解）: ${progressToSec(seekWrongRetry.progress)} 秒"
            textLevel1Retry.text = "当日再出題（正解済）: ${progressToSec(seekLevel1Retry.progress)} 秒"
            textDontKnowRetry.text = "当日再出題（わからない）: ${progressToSecForDontKnow(seekDontKnowRetry.progress)} 秒"
            textUnlockMinPer10Pt.text = getString(R.string.admin_label_unlock_min_per_10pt_value, progressToMinPer10Pt(seekUnlockMinPer10Pt.progress))
            
            val newWords = textTargetCount.text.toString().toIntOrNull() ?: 5
            val totalQuestions = newWords * 15
            val totalTimeMinutes = (totalQuestions * 15) / 60
            textSimTotalQuestions.text = getString(R.string.target_sim_total_questions, totalQuestions)
            textSimTotalTime.text = getString(R.string.target_sim_total_time, totalTimeMinutes)
        }
        refreshLabels()

        btnTargetMinus.setOnClickListener {
            val current = textTargetCount.text.toString().toIntOrNull() ?: 5
            if (current > 1) {
                textTargetCount.text = (current - 1).toString()
                refreshLabels()
            }
        }
        btnTargetPlus.setOnClickListener {
            val current = textTargetCount.text.toString().toIntOrNull() ?: 5
            if (current < 100) {
                textTargetCount.text = (current + 1).toString()
                refreshLabels()
            }
        }

        val commonListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { refreshLabels() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        listOf(seekWrongRetry, seekLevel1Retry, seekDontKnowRetry, seekUnlockMinPer10Pt).forEach { it.setOnSeekBarChangeListener(commonListener) }

        btnSave.setOnClickListener {
            val selected = spinnerCurrentGrade.selectedItem?.toString() ?: "未設定"
            val newTargetGrade = if (selected == "未設定") "0" else GradeUtils.normalize(selected)
            
            // 目標級が変更された場合
            if (settings.targetLearningGrade != newTargetGrade && newTargetGrade != "0") {
                settings.targetLearningGrade = newTargetGrade
                // 目標級が変わったらメイン画面でおすすめ設定ダイアログを出すようフラグをリセット
                // (MainActivityのonResumeで検知させるために、あえてhasShownを倒す)
                getSharedPreferences("app_state", MODE_PRIVATE).edit().putBoolean("force_show_quota_dialog", true).apply()
            } else {
                settings.targetLearningGrade = newTargetGrade
            }

            modes.forEach { (mode, views) -> views.second?.let { settings.setBasePoint(mode, progressToPoint(it.progress)) } }
            settings.wrongRetrySec = progressToSec(seekWrongRetry.progress)
            settings.level1RetrySec = progressToSec(seekLevel1Retry.progress)
            settings.dontKnowRetrySec = progressToSecForDontKnow(seekDontKnowRetry.progress)
            settings.setUnlockMinutesPer10Pt(progressToMinPer10Pt(seekUnlockMinPer10Pt.progress))
            settings.dailyNewWordTarget = textTargetCount.text.toString().toIntOrNull() ?: 5
            AdAudioManager.apply(settings)
            finish()
        }

        setupPersonalWordbookSection()
    }

    /**
     * マイ単語帳セクションのセットアップ
     */
    private fun setupPersonalWordbookSection() {
        val spinnerPersonalSelect = findViewById<Spinner>(R.id.spinner_personal_wordbook_select) ?: return
        val spinnerTargetGrade = findViewById<Spinner>(R.id.spinner_personal_wordbook_target_grade) ?: return
        val btnManage = findViewById<View>(R.id.button_manage_personal_words) ?: return

        // 1. 編集する単語帳を選択 (Grade 90-99)
        val personalGrades = (90..99).toList()
        val personalItems = personalGrades.map { GradeUtils.toDisplay(it.toString()) }
        val personalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, personalItems)
        personalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPersonalSelect.adapter = personalAdapter

        // 2. 紐付け級を選択 (1-7)
        val targetGrades = (1..7).toList()
        val targetItems = targetGrades.map { GradeUtils.toDisplay(it.toString()) }
        val targetAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, targetItems)
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTargetGrade.adapter = targetAdapter

        // 現在の選択に基づきターゲット級を更新
        fun updateTargetGradeSpinner(personalGrade: Int) {
            val currentTarget = settings.getPersonalGradeTarget(personalGrade)
            val index = targetGrades.indexOf(currentTarget)
            if (index >= 0) {
                spinnerTargetGrade.setSelection(index)
            }
        }

        spinnerPersonalSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateTargetGradeSpinner(personalGrades[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerTargetGrade.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPersonalGrade = personalGrades[spinnerPersonalSelect.selectedItemPosition]
                val selectedTargetGrade = targetGrades[position]
                settings.setPersonalGradeTarget(selectedPersonalGrade, selectedTargetGrade)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnManage.setOnClickListener {
            val selectedPersonalGrade = personalGrades[spinnerPersonalSelect.selectedItemPosition]
            showToast("マイ単語帳 ${selectedPersonalGrade - 89} の管理機能を準備中...")
        }
    }

    private fun getQuizModeDisplayName(mode: QuizMode): String {
        return when (mode) {
            QuizMode.JP_TO_EN -> getString(R.string.mode_japanese_to_english)
            QuizMode.EN_TO_JP -> getString(R.string.mode_meaning)
            QuizMode.LISTEN_EN -> getString(R.string.mode_listening)
            QuizMode.FILL_BLANK -> getString(R.string.mode_test_fill_blank)
            QuizMode.LISTEN_FILL_BLANK -> "穴埋めリスニング"
            QuizMode.SYNONYM_PICK -> "類義語選び"
            QuizMode.ANTONYM_PICK -> "対義語選び"
            QuizMode.SENTENCE_SORT -> getString(R.string.mode_test_sort)
            else -> mode.name
        }
    }

    private fun updateRecoveryStatusDisplay() {
        val textParent = findViewById<TextView>(R.id.text_recovery_status_parent) ?: return
        val textAuthApp = findViewById<TextView>(R.id.text_recovery_status_auth_app) ?: return

        val parentConfigured = settings.hasParent()
        val authAppConfigured = AdminAuthManager.isTotpSet(this)

        textParent.text = getString(R.string.admin_recovery_parent, if (parentConfigured) getString(R.string.admin_status_configured) else getString(R.string.admin_status_not_configured))
        textParent.setTextColor(if (parentConfigured) Color.parseColor("#4CAF50") else Color.GRAY)

        textAuthApp.text = getString(R.string.admin_recovery_auth_app, if (authAppConfigured) getString(R.string.admin_status_configured) else getString(R.string.admin_status_not_configured))
        textAuthApp.setTextColor(if (authAppConfigured) Color.parseColor("#4CAF50") else Color.GRAY)
    }

    private fun setupAdminSecurityViews() {
        val switchAdminLock = findViewById<SwitchMaterial>(R.id.switch_admin_lock) ?: return
        val buttonChangePin = findViewById<MaterialButton>(R.id.button_change_pin) ?: return
        val buttonDeletePin = findViewById<MaterialButton>(R.id.button_delete_pin) ?: return
        val buttonSetupAuthenticator = findViewById<MaterialButton>(R.id.button_setup_authenticator)
        val switchAccessibilityLock = findViewById<SwitchMaterial>(R.id.switch_accessibility_lock)
        val switchUninstallLock = findViewById<SwitchMaterial>(R.id.switch_uninstall_lock)

        switchAdminLock.setTextColor(switchTextColor)
        switchAccessibilityLock?.setTextColor(switchTextColor)
        switchUninstallLock?.setTextColor(switchTextColor)

        switchAdminLock.isChecked = AdminAuthManager.isAdminLockEnabled(this)
        switchAccessibilityLock?.isChecked = settings.isAccessibilityLockEnabled
        switchUninstallLock?.isChecked = settings.isUninstallLockEnabled()

        switchAdminLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!AdminAuthManager.isPinSet(this)) {
                    promptSetNewPin(onSuccess = { 
                        AdminAuthManager.setAdminLockEnabled(this, true)
                        showToast(getString(R.string.admin_lock_enabled))
                    }, onCancel = { switchAdminLock.isChecked = false })
                } else {
                    val hasRecovery = settings.hasParent() || AdminAuthManager.isTotpSet(this)
                    if (!hasRecovery) {
                        showNoRecoveryWarning(
                            onConfirm = {
                                AdminAuthManager.setAdminLockEnabled(this, true)
                                showToast(getString(R.string.admin_lock_enabled))
                            },
                            onCancel = { switchAdminLock.isChecked = false }
                        )
                    } else {
                        AdminAuthManager.setAdminLockEnabled(this, true)
                        showToast(getString(R.string.admin_lock_enabled))
                    }
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

        switchAccessibilityLock?.setOnCheckedChangeListener { _, isChecked -> settings.isAccessibilityLockEnabled = isChecked }
        switchUninstallLock?.setOnCheckedChangeListener { _, isChecked -> settings.setUninstallLockEnabled(isChecked) }

        buttonChangePin.setOnClickListener {
            promptPinAndDo(
                title = getString(R.string.admin_enter_pin_title),
                onSuccess = { promptSetNewPin() },
                onFailure = { showToast(getString(R.string.admin_pin_incorrect)) }
            )
        }

        buttonDeletePin.setOnClickListener {
            promptPinAndDo(
                title = getString(R.string.admin_enter_pin_title),
                onSuccess = { promptDeletePin() },
                onFailure = { showToast(getString(R.string.admin_pin_incorrect)) }
            )
        }

        buttonSetupAuthenticator?.setOnClickListener { startActivity(Intent(this, AuthenticatorSetupActivity::class.java)) }
    }

    private fun showNoRecoveryWarning(onConfirm: () -> Unit, onCancel: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(getString(R.string.admin_warning_no_recovery_title)))
            .setMessage(getString(R.string.admin_warning_no_recovery_msg))
            .setPositiveButton(R.string.admin_action_enable_anyway) { _, _ -> onConfirm() }
            .setNeutralButton(R.string.admin_action_setup_recovery) { _, _ -> onCancel() /* User wants to setup first */ }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }

    private fun promptDeletePin() {
        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(getString(R.string.admin_delete_pin_confirm_title)))
            .setMessage(getString(R.string.admin_delete_pin_confirm_msg))
            .setPositiveButton(R.string.admin_delete_pin_action) { _, _ ->
                AdminAuthManager.clearPinAndRecovery(this)
                AdminAuthManager.setAdminLockEnabled(this, false)
                isAuthenticated = false
                showToast(getString(R.string.admin_delete_pin_success))
                // Refresh views
                findViewById<SwitchMaterial>(R.id.switch_admin_lock)?.isChecked = false
                updateRecoveryStatusDisplay()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptPinAndDo(title: String, onSuccess: () -> Unit, onFailure: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
        val inputLayout = TextInputLayout(this).apply { hint = getString(R.string.admin_enter_pin_hint); endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE }
        val edit = TextInputEditText(inputLayout.context).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; setTextColor(dialogTextColor); setHintTextColor(dialogHintColor) }
        inputLayout.addView(edit)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(coloredTitle(title)).setView(inputLayout).setPositiveButton(R.string.ok) { _, _ -> if (AdminAuthManager.verifyPin(this, edit.text?.toString().orEmpty())) onSuccess() else onFailure?.invoke() }.setNegativeButton(R.string.cancel) { _, _ -> onCancel?.invoke() }.setOnCancelListener { onCancel?.invoke() }
        
        val hasParent = settings.hasParent()
        val hasTotp = AdminAuthManager.isTotpSet(this)
        
        if (hasParent || hasTotp) {
            dialog.setNeutralButton(R.string.admin_forgot_pin) { _, _ -> 
                showRecoveryChoiceDialog(onSuccess)
            }
        } else {
            // Optional: show a message that no recovery is set
            // dialog.setNeutralButton("復旧不可", null)
        }
        dialog.show()
    }

    private fun showRecoveryChoiceDialog(onSuccess: () -> Unit) {
        val hasParent = settings.hasParent()
        val hasTotp = AdminAuthManager.isTotpSet(this)
        
        val options = mutableListOf<String>()
        if (hasParent) options.add(getString(R.string.admin_recovery_method_parent))
        if (hasTotp) options.add(getString(R.string.admin_recovery_method_auth_app))
        
        if (options.isEmpty()) {
            showToast(getString(R.string.admin_recovery_no_methods))
            return
        }
        
        if (options.size == 1) {
            if (hasParent) promptRemoteUnlock(onSuccess) else promptTotpAndResetPin(onSuccess)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle(getString(R.string.admin_recovery_choice_title)))
            .setItems(options.toTypedArray()) { _, which ->
                val selected = options[which]
                if (selected == getString(R.string.admin_recovery_method_parent)) {
                    promptRemoteUnlock(onSuccess)
                } else {
                    promptTotpAndResetPin(onSuccess)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptRemoteUnlock(onSuccess: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Toast.makeText(this, "認証が必要です", Toast.LENGTH_LONG).show()
            return
        }

        val randomCode = (100000..999999).random().toString()
        val functions = FirebaseFunctions.getInstance("asia-northeast1")
        val data = hashMapOf("code" to randomCode)

        Toast.makeText(this, "管理者に解除コードを送信中...", Toast.LENGTH_SHORT).show()

        functions.getHttpsCallable("requestUnlockCode").call(data)
            .addOnSuccessListener { result ->
                val map = result.getData() as? Map<*, *>
                val success = map?.get("success") as? Boolean ?: false
                val message = map?.get("message") as? String

                if (success) {
                    Toast.makeText(this, "通知を送信しました！", Toast.LENGTH_SHORT).show()
                    showUnlockDialog(randomCode, onSuccess)
                } else {
                    Toast.makeText(this, message ?: "通知を送信できませんでした", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "送信エラー: ${e.message}", Toast.LENGTH_LONG).show()
            }
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

    private fun promptForChildNameAndRegister(childUid: String) {
        val inputLayout = TextInputLayout(this).apply {
            hint = "お子様の名前 (通知に表示されます)"
            isErrorEnabled = true
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = ContextCompat.getColor(context, R.color.dialog_surface)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(20))
            setTextColor(dialogTextColor)
            setHintTextColor(dialogHintColor)
        }
        inputLayout.addView(editText)
        val msg = SpannableString("通知に表示されるお子様の名前を入力してください。").apply { setSpan(ForegroundColorSpan(dialogTextColor), 0, length, 0) }
        val dialog = MaterialAlertDialogBuilder(this).setTitle(coloredTitle("管理対象の追加")).setMessage(msg).setView(inputLayout).setPositiveButton("登録", null).setNegativeButton("キャンセル", null).create()
        dialog.setOnShowListener { val okBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE); okBtn.setOnClickListener { val (normalized, err) = validateAndNormalizeChildName(editText.text?.toString().orEmpty()); if (err != null) { inputLayout.error = err; return@setOnClickListener }; inputLayout.error = null; registerAsParent(childUid, normalized!!); dialog.dismiss() }; editText.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (inputLayout.error != null) inputLayout.error = null }; override fun afterTextChanged(s: android.text.Editable?) {} }) }
        dialog.show()
    }

    private fun validateAndNormalizeChildName(input: String): Pair<String?, String?> {
        val raw = input.trim().replace(Regex("""[ 1-9]+"""), " ")
        if (raw.isEmpty()) return Pair(null, "名前を入力してください。")
        if (raw.length !in 1..15) return Pair(null, "名前は1〜15文字で入力してください。")
        if (!Regex("""^[0-9A-Za-zぁ-んァ-ン一-龥ー・ 1-9]+$""").matches(raw)) return Pair(null, "使える文字は「英数字/ひらがな/カタカナ/漢字/スペース/・/ー」のみです。")
        return Pair(raw, null)
    }
}
