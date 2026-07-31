package com.stulab.studylockapp.service

import android.content.Context
import android.util.Log
import com.stulab.studylockapp.data.notification.CharacterLines
import com.stulab.studylockapp.data.notification.NotificationContext
import com.stulab.studylockapp.data.notification.StudyCharacter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * フレンドの目標達成をリアルタイムで監視し、通知を出すクラス
 */
object FriendNotificationManager {
    private const val TAG = "FriendNotifMgr"
    private var listenerRegistration: ListenerRegistration? = null

    // フレンドUIDと、あなたが設定した表示名（ニックネーム）のマップ
    private val friendNicknames = mutableMapOf<String, String>()

    /**
     * 監視を開始する
     */
    fun startListening(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (listenerRegistration != null) return

        val db = FirebaseFirestore.getInstance()
        val myUid = user.uid

        // 1. まず自分のフレンドリスト（ニックネームを含む）の変化を監視する
        listenerRegistration = db.collection("users").document(myUid).collection("friends")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Friend list listen failed.", e)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { dc ->
                    val friendUid = dc.document.id
                    val nickname = dc.document.getString("displayName") ?: "友達"

                    when (dc.type) {
                        DocumentChange.Type.ADDED -> {
                            friendNicknames[friendUid] = nickname
                            observeFriendStatus(context, friendUid)
                        }
                        DocumentChange.Type.MODIFIED -> {
                            // 名前が編集されたらメモリ上の値を更新
                            friendNicknames[friendUid] = nickname
                        }
                        DocumentChange.Type.REMOVED -> {
                            friendNicknames.remove(friendUid)
                            friendListeners[friendUid]?.remove()
                            friendListeners.remove(friendUid)
                        }
                    }
                }
            }
    }

    private val friendListeners = mutableMapOf<String, ListenerRegistration>()
    private val lastNotifiedTimestamps = mutableMapOf<String, Long>()

    private fun observeFriendStatus(context: Context, friendUid: String) {
        if (friendListeners.containsKey(friendUid)) return

        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("users").document(friendUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val broadcastAt = snapshot.getTimestamp("goalMetBroadcastAt") ?: return@addSnapshotListener
                val broadcastMillis = broadcastAt.toDate().time
                
                // 重複通知を防止（同じタイムスタンプなら通知済み）
                if (lastNotifiedTimestamps[friendUid] == broadcastMillis) return@addSnapshotListener
                
                // 5分以内のブロードキャストのみ通知する
                val diffMillis = System.currentTimeMillis() - broadcastMillis
                if (diffMillis > -60000 && diffMillis < 5 * 60 * 1000) {
                    val lastGoalStreak = snapshot.getLong("lastGoalStreak")?.toInt() ?: 0
                    val friendName = friendNicknames[friendUid] ?: snapshot.getString("displayName") ?: "友達"

                    val settings = com.stulab.studylockapp.data.AppSettings(context)
                    val character = StudyCharacter.fromId(settings.selectedCharacterId)
                    
                    // 自分の目標達成状況を確認
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val todayStr = sdf.format(java.util.Date())
                    val isMyGoalMet = settings.lastGoalMetDate == todayStr

                    val contextForNotif = if (isMyGoalMet) {
                        NotificationContext.FRIEND_GOAL_MET_MY_GOAL_DONE
                    } else {
                        NotificationContext.FRIEND_GOAL_MET_MY_GOAL_PENDING
                    }

                    // タイトルを完全に空にすると表示されない端末があるため、キャラ名を入れる
                    val title = character.displayName
                    val message = CharacterLines.getLine(
                        character = character, 
                        context = contextForNotif, 
                        streak = lastGoalStreak, 
                        name = settings.userName ?: "きみ",
                        friendName = friendName
                    )

                    // 選択中のキャラの icon_... 画像を取得 (CharacterDisplayUtils の共通ロジックを使用)
                    val emotion = CharacterLines.getEmotionForContext(character, contextForNotif)
                    val imageResId = com.stulab.studylockapp.ui.CharacterDisplayUtils.getNotificationIconDrawable(
                        context, character.id, emotion.id
                    )

                    lastNotifiedTimestamps[friendUid] = broadcastMillis
                    NotificationHelper.showNotification(context, title, message, imageResId)
                }
            }
        
        friendListeners[friendUid] = listener
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
        friendListeners.values.forEach { it.remove() }
        friendListeners.clear()
    }
}
