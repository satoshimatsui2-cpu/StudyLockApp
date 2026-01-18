package com.example.studylockapp.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

class QrCodeActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var qrImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code)

        qrImageView = findViewById(R.id.qr_image)
        statusTextView = findViewById(R.id.text_qr_status)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user != null) {
            // すでにログイン済みなら即表示
            showQrCode(user.uid)
        } else {
            // 未ログインなら、ここで匿名ログインを実行！
            statusTextView.text = "初回認証を行っています..."
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid != null) {
                        showQrCode(uid)
                        Toast.makeText(this, "認証成功", Toast.LENGTH_SHORT).show()
                    } else {
                        showError("UID取得エラー")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("QrCodeActivity", "Login failed", e)
                    showError("認証失敗: ${e.message}\nコンソールで匿名ログインを有効にしましたか？")
                }
        }
    }

    private fun showQrCode(uid: String) {
        statusTextView.text = "UID: $uid\n\n保護者アプリで読み取ってください"
        try {
            val bitmap = createQrCode(uid, 500, 500)
            qrImageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            showError("QR生成失敗: ${e.message}")
        }
    }

    private fun showError(msg: String) {
        statusTextView.text = msg
        statusTextView.setTextColor(Color.RED)
    }

    private fun createQrCode(content: String, width: Int, height: Int): Bitmap {
        val writer = MultiFormatWriter()
        val bitMatrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}