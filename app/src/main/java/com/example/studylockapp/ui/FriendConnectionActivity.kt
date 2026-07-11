package com.example.studylockapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.StudyHistoryRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FriendConnectionActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var editFriendId: EditText
    private lateinit var textMyId: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_connection)

        textMyId = findViewById(R.id.text_my_id)
        editFriendId = findViewById(R.id.edit_friend_id)
        recycler = findViewById(R.id.recycler_friends)

        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: "---"
        textMyId.text = myUid

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_add_friend).setOnClickListener { addFriend() }

        recycler.layoutManager = LinearLayoutManager(this)
        loadFriends()
    }

    private fun addFriend() {
        val friendId = editFriendId.text.toString().trim()
        if (friendId.isBlank()) return

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

    inner class FriendAdapter(private val items: List<Pair<String, String>>) : RecyclerView.Adapter<FriendAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name = v.findViewById<TextView>(android.R.id.text1)
            val id = v.findViewById<TextView>(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (uid, name) = items[position]
            holder.name.text = name
            holder.id.text = "ID: $uid"
        }

        override fun getItemCount() = items.size
    }
}
