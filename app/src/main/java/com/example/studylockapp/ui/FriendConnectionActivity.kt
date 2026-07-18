package com.example.studylockapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.CaptureActivityPortrait
import com.example.studylockapp.R
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.StudyHistoryRepository
import com.google.firebase.auth.FirebaseAuth
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class FriendConnectionActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var editFriendId: EditText
    private lateinit var textMyId: TextView
    private lateinit var textMyName: TextView
    private lateinit var appSettings: AppSettings

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        val payload = result.contents
        if (payload != null) {
            editFriendId.setText(payload)
            addFriend()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_connection)

        textMyId = findViewById(R.id.text_my_id)
        textMyName = findViewById(R.id.text_my_name_display)
        editFriendId = findViewById(R.id.edit_friend_id)
        recycler = findViewById(R.id.recycler_friends)
        appSettings = AppSettings(this)

        checkAuthAndInitialize()
        updateMyNameDisplay()

        val btnAddFriend = findViewById<View>(R.id.btn_add_friend)
        btnAddFriend.setOnClickListener { addFriend() }

        editFriendId.addTextChangedListener {
            btnAddFriend.isEnabled = it?.trim()?.isNotEmpty() == true
        }

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_edit_my_name).setOnClickListener { showEditMyNameDialog() }

        findViewById<View>(R.id.btn_show_my_qr).setOnClickListener {
            val intent = Intent(this, QrCodeActivity::class.java).apply {
                putExtra("prompt", "友達のアプリで読み取ってください")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_scan_qr).setOnClickListener {
            val options = ScanOptions()
            options.setPrompt("友達のQRコードを枠内に写してください")
            options.setBeepEnabled(false)
            options.setOrientationLocked(true)
            options.setCaptureActivity(CaptureActivityPortrait::class.java)
            barcodeLauncher.launch(options)
        }

        recycler.layoutManager = LinearLayoutManager(this)
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

    private fun addFriend() {
        val friendId = editFriendId.text.toString().trim()
        if (friendId.isBlank()) return
        
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        if (friendId == myUid) {
            Toast.makeText(this, "自分のIDは追加できません", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val success = StudyHistoryRepository.addFriend(friendId)
            if (success) {
                Toast.makeText(this@FriendConnectionActivity, "フレンドを追加しました", Toast.LENGTH_SHORT).show()
                editFriendId.text.clear()
                loadFriends()
            } else {
                Toast.makeText(this@FriendConnectionActivity, "ユーザーが見つからないか、エラーが発生しました", Toast.LENGTH_SHORT).show()
            }
        }
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
        val inputLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            setPadding(padding, (8 * resources.displayMetrics.density).toInt(), padding, 0)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = "あなたの名前 (15文字以内)"
        }
        val editText = EditText(this).apply {
            setText(appSettings.userName ?: "")
            filters = arrayOf(android.text.InputFilter.LengthFilter(15))
            maxLines = 1
            isSingleLine = true
            setSelection(text.length)
            // テキスト色を明示的に指定（テーマの影響を回避）
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_main))
        }
        inputLayout.addView(editText)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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
        val editText = EditText(this)
        editText.setText(currentName)
        editText.setSelection(currentName.length)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("表示名の変更")
            .setMessage("フレンドの表示名を変更します。")
            .setView(editText)
            .setPositiveButton("変更") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        val success = StudyHistoryRepository.updateFriendDisplayName(friendUid, newName)
                        if (success) {
                            Toast.makeText(this@FriendConnectionActivity, "変更しました", Toast.LENGTH_SHORT).show()
                            loadFriends()
                        }
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .create()

        dialog.show()
        
        // 余白の調整
        val padding = (24 * resources.displayMetrics.density).toInt()
        (editText.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.setMargins(padding, 0, padding, 0)
    }

    private fun showRemoveFriendDialog(friendUid: String, friendName: String) {
        com.example.studylockapp.ui.alert.AppDialogHelper.showConfirm(
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
