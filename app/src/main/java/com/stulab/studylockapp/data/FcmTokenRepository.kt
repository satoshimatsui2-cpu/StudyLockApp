package com.stulab.studylockapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * 一般ユーザーのFCMトークンを管理するリポジトリ
 */
object FcmTokenRepository {

    /**
     * FCMトークンを自身のユーザープロファイルのサブコレクションに保存する
     */
    suspend fun updateToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val hash = sha256(token)

        val data = hashMapOf(
            "fcmToken" to token,
            "lastUpdated" to FieldValue.serverTimestamp(),
            "platform" to "android"
        )

        try {
            db.collection("users").document(user.uid)
                .collection("fcmTokens").document(hash)
                .set(data)
                .await()
        } catch (e: Exception) {
            // エラーログのみ記録（FCMトークン自体は含めない）
            android.util.Log.e("FcmTokenRepo", "Failed to update FCM token document")
        }
    }

    /**
     * 指定されたトークンを自身のサブコレクションから削除する
     */
    suspend fun deleteToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val hash = sha256(token)

        try {
            db.collection("users").document(user.uid)
                .collection("fcmTokens").document(hash)
                .delete()
                .await()
        } catch (e: Exception) {
            android.util.Log.e("FcmTokenRepo", "Failed to delete FCM token document")
        }
    }

    /**
     * トークンのSHA-256ハッシュを計算（小文字16進数）
     */
    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
