package com.stulab.studylockapp.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stulab.studylockapp.data.FcmTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCMトークンの更新や通知の受信をハンドリングするサービス
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 新しいFCMトークンが生成されたときに呼ばれる
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // サーバー（Firestore）にトークンを保存
        serviceScope.launch {
            FcmTokenRepository.updateToken(token)
        }
    }

    /**
     * メッセージを受信したときに呼ばれる
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        if (data["type"] == "friend_goal_met") {
            handleFriendGoalMet(message)
        }
    }

    private fun handleFriendGoalMet(message: RemoteMessage) {
        val data = message.data

        // Cloud Functionから送られてくるパーソナライズされた文面を使用
        val title = data["title"] ?: "フレンドの目標達成！"
        val body = data["body"] ?: run {
            val actorName = data["actorName"] ?: "フレンド"
            "${actorName}さんが今日の目標を達成しました！"
        }

        // 通知IDの生成 (actorUid + goalMetBroadcastAt)
        val actorUid = data["actorUid"]
        val timestamp = data["goalMetBroadcastAt"]
        val notificationId = if (!actorUid.isNullOrEmpty() && !timestamp.isNullOrEmpty()) {
            val rawHash = (actorUid + timestamp).hashCode()
            val posHash = if (rawHash == Int.MIN_VALUE) 2000 else kotlin.math.abs(rawHash)
            if (posHash <= 1001) posHash + 1002 else posHash
        } else {
            2000
        }

        // 受信者のパートナーIDとセリフに紐付いた感情を取得
        val charId = data["receiverCharacterId"] ?: "george"
        val emotion = data["emotion"] ?: "joy"

        // 適切な画像リソースIDを取得 (CharacterDisplayUtilsを使用)
        val imageResId = com.stulab.studylockapp.ui.CharacterDisplayUtils.getNotificationIconDrawable(
            applicationContext, charId, emotion
        )

        try {
            NotificationHelper.showNotification(applicationContext, title, body, imageResId, notificationId)
        } catch (e: Exception) {
            // 通知表示エラー
        }
    }
}
