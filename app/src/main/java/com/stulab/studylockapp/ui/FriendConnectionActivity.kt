package com.stulab.studylockapp.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stulab.studylockapp.CaptureActivityPortrait
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.StudyHistoryRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class FriendConnectionActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var textMyId: TextView
    private lateinit var textMyName: TextView
    private lateinit var appSettings: AppSettings
    private var friendListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val processedUidsInSession = mutableSetOf<String>()

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        val payload = result.contents
        if (payload != null) {
            processFriendId(payload)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_connection)

        textMyId = findViewById(R.id.text_my_id)
        textMyName = findViewById(R.id.text_my_name_display)
        recycler = findViewById(R.id.recycler_friends)
        appSettings = AppSettings(this)

        checkAuthAndInitialize()
        updateMyNameDisplay()
        startFriendAutoDetection()

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_edit_my_name).setOnClickListener { showEditMyNameDialog() }

        findViewById<View>(R.id.btn_show_my_qr).setOnClickListener {
            val intent = Intent(this, QrCodeActivity::class.java).apply {
                putExtra("prompt", "友達のアプリで読み取ってください")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_add_friend_main).setOnClickListener {
            showAddFriendOptionDialog()
        }

        recycler.layoutManager = LinearLayoutManager(this)
    }

    private fun showAddFriendOptionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_friend_options, null)
        val btnScan = dialogView.findViewById<MaterialButton>(R.id.btn_scan_qr_option)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.layout_friend_id_input)
        val editId = dialogView.findViewById<TextInputEditText>(R.id.edit_friend_id_dialog)

        // 視認性のための明示的指定
        editId.setTextColor(Color.BLACK)
        editId.setHintTextColor(Color.GRAY)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("フレンドを追加")
            .setView(dialogView)
            .setPositiveButton("追加", null)
            .setNegativeButton("キャンセル", null)
            .create()

        btnScan.setOnClickListener {
            dialog.dismiss()
            startQrScan()
        }

        dialog.setOnShowListener {
            val btnAdd = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btnAdd.setOnClickListener {
                val friendId = editId.text.toString().trim()
                if (friendId.isEmpty()) {
                    inputLayout.error = "IDを入力してください"
                    return@setOnClickListener
                }
                if (friendId == FirebaseAuth.getInstance().currentUser?.uid) {
                    inputLayout.error = "自分のIDは追加できません"
                    return@setOnClickListener
                }
                
                dialog.dismiss()
                processFriendId(friendId)
            }
        }

        dialog.show()
    }

    private fun startQrScan() {
        val options = ScanOptions()
        options.setPrompt("友達のQRコードを枠内に写してください")
        options.setBeepEnabled(false)
        options.setOrientationLocked(true)
        options.setCaptureActivity(CaptureActivityPortrait::class.java)
        barcodeLauncher.launch(options)
    }

    private fun processFriendId(friendId: String) {
        processedUidsInSession.add(friendId)
        lifecycleScope.launch {
            val db = FirebaseFirestore.getInstance()
            try {
                val doc = db.collection("users").document(friendId).get().await()
                if (doc.exists()) {
                    val initialName = doc.getString("displayName") ?: "友達"
                    showEditFriendNameDialog(friendId, initialName, isNewFriend = true)
                } else {
                    Toast.makeText(this@FriendConnectionActivity, "ユーザーが見つかりません", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FriendConnectionActivity, "エラーが発生しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAuthAndInitialize() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        
        if (user != null) {
            textMyId.text = user.uid
            loadFriends()
            lifecycleScope.launch {
                StudyHistoryRepository.updateLastActiveStatus()
            }
        } else {
            textMyId.text = "認証中..."
            auth.signInAnonymously().addOnSuccessListener { result ->
                val newUid = result.user?.uid ?: "---"
                textMyId.text = newUid
                lifecycleScope.launch {
                    StudyHistoryRepository.updateLastActiveStatus()
                    loadFriends()
                }
            }.addOnFailureListener {
                textMyId.text = "認証失敗"
                Toast.makeText(this, "サーバーとの接続に失敗しました", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startFriendAutoDetection() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        friendListener?.remove()
        friendListener = db.collection("users").document(myUid).collection("friends")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                loadFriends()
            }
    }

    override fun onDestroy() {
        friendListener?.remove()
        super.onDestroy()
    }

    private fun showEditFriendNameDialog(friendUid: String, currentName: String, isNewFriend: Boolean = false) {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val inputLayout = TextInputLayout(this).apply {
            setPadding(padding, (8 * resources.displayMetrics.density).toInt(), padding, 0)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
            hint = "フレンドの表示名"
            boxBackgroundColor = Color.WHITE
            setHintTextColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_sub)))
            boxStrokeColor = ContextCompat.getColor(context, R.color.navy_primary)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            setText(currentName)
            filters = arrayOf(android.text.InputFilter.LengthFilter(15))
            maxLines = 1
            isSingleLine = true
            setSelection(text?.length ?: 0)
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isNewFriend) "フレンドの登録" else "表示名の変更")
            .setMessage(if (isNewFriend) "相手の呼び名を決めてください" else null)
            .setView(inputLayout)
            .setPositiveButton("保存") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        if (isNewFriend) {
                            val myName = appSettings.userName ?: "ユーザー"
                            val success = StudyHistoryRepository.addFriend(friendUid, myName)
                            if (success) {
                                StudyHistoryRepository.updateFriendDisplayName(friendUid, newName)
                                if (appSettings.userName != null) {
                                    StudyHistoryRepository.updateLastActiveStatus(appSettings.userName)
                                }
                                Toast.makeText(this@FriendConnectionActivity, "フレンドを登録しました", Toast.LENGTH_SHORT).show()
                                loadFriends()
                            }
                        } else {
                            StudyHistoryRepository.updateFriendDisplayName(friendUid, newName)
                            loadFriends()
                        }
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun loadFriends() {
        lifecycleScope.launch {
            val friends = StudyHistoryRepository.getFriends()
            recycler.adapter = FriendAdapter(friends)
        }
    }

    private fun updateMyNameDisplay() {
        textMyName.text = appSettings.userName ?: "未設定"
    }

    private fun showEditMyNameDialog() {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val inputLayout = TextInputLayout(this).apply {
            setPadding(padding, (8 * resources.displayMetrics.density).toInt(), padding, 0)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = "あなたの名前 (15文字以内)"
            boxBackgroundColor = ContextCompat.getColor(context, R.color.dialog_surface)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            setText(appSettings.userName ?: "")
            filters = arrayOf(android.text.InputFilter.LengthFilter(15))
            maxLines = 1
            isSingleLine = true
            setSelection(text?.length ?: 0)
            setTextColor(ContextCompat.getColor(context, R.color.text_main))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_sub))
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle("名前の変更")
            .setView(inputLayout)
            .setPositiveButton("保存") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    appSettings.userName = newName
                    updateMyNameDisplay()
                    lifecycleScope.launch {
                        StudyHistoryRepository.updateLastActiveStatus(newName)
                    }
                    Toast.makeText(this, "名前を更新しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    inner class FriendAdapter(private val items: List<Pair<String, String>>) : RecyclerView.Adapter<FriendAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.text_friend_name)
            val id: TextView = v.findViewById(R.id.text_friend_id)
            val btnEdit: ImageButton = v.findViewById(R.id.btn_edit_friend_name)
            val btnRemove: ImageButton = v.findViewById(R.id.btn_remove_friend)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (uid, name) = items[position]
            holder.name.text = name
            holder.id.text = "ID: $uid"
            
            holder.btnEdit.setOnClickListener {
                showEditNameDialog(uid, name)
            }
            holder.btnRemove.setOnClickListener {
                showRemoveFriendDialog(uid, name)
            }
        }

        override fun getItemCount() = items.size
    }

    private fun showEditNameDialog(friendUid: String, currentName: String) {
        showEditFriendNameDialog(friendUid, currentName, isNewFriend = false)
    }

    private fun showRemoveFriendDialog(friendUid: String, friendName: String) {
        com.stulab.studylockapp.ui.alert.AppDialogHelper.showConfirm(
            context = this,
            title = "フレンド削除",
            message = "${friendName}さんをフレンドリストから削除しますか？\n（相手のリストからも削除されます）",
            positiveText = "削除する",
            negativeText = "キャンセル",
            onPositive = {
                lifecycleScope.launch {
                    val success = StudyHistoryRepository.removeFriend(friendUid)
                    if (success) {
                        Toast.makeText(this@FriendConnectionActivity, "削除しました", Toast.LENGTH_SHORT).show()
                        loadFriends()
                    }
                }
            }
        )
    }
}
