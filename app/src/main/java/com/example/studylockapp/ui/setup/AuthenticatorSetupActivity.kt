package com.example.studylockapp.ui.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.R
import com.example.studylockapp.data.AdminAuthManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

class AuthenticatorSetupActivity : AppCompatActivity() {

    private lateinit var secretKey: ByteArray
    private lateinit var editCode: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authenticator_setup)

        // Generate new secret (not saved yet)
        secretKey = AdminAuthManager.generateTotpSecretKey()

        setupViews()
    }

    private fun setupViews() {
        val imageQr = findViewById<ImageView>(R.id.image_qr_code)
        val textSecret = findViewById<TextView>(R.id.text_secret_key)
        val btnCopy = findViewById<MaterialButton>(R.id.btn_copy_secret)
        editCode = findViewById(R.id.edit_totp_code)
        val btnVerify = findViewById<MaterialButton>(R.id.btn_verify_totp)
        val btnCancel = findViewById<MaterialButton>(R.id.btn_cancel)

        // Build otpauth URI
        // URI Format: otpauth://totp/Label:Account?secret=Secret&issuer=Issuer
        val appName = getString(R.string.app_name)
        val secretBase32 = AdminAuthManager.toBase32(secretKey)
        val uri = "otpauth://totp/$appName:Admin?secret=$secretBase32&issuer=$appName"

        // Display Secret for manual entry
        textSecret.text = secretBase32

        // Generate QR Code
        try {
            val bitmap = generateQrCode(uri, 600, 600)
            imageQr.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Authenticator Secret", secretBase32)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }

        btnVerify.setOnClickListener {
            val code = editCode.text?.toString() ?: ""
            if (code.length != 6) {
                editCode.error = getString(R.string.totp_incorrect)
                return@setOnClickListener
            }

            // Verify using the temporary secret
            // We need to temporarily set the secret to verify, or we need to expose verify method that takes secret
            // Since AdminAuthManager.verifyTotp reads from Prefs, we should verify manually here using internal method logic
            // But AdminAuthManager.verifyTotp is tied to prefs.
            // Let's use a trick: save it temporarily? No, unsafe.
            // Better: Add a verify method that takes secret in AdminAuthManager or just use the logic here?
            // To keep encapsulation, let's just save it. If verify fails, user won't leave screen anyway?
            // Actually better to verify BEFORE saving to avoid overwriting existing valid secret with invalid one.
            
            // Let's allow saving first? No.
            // Let's implement a verify helper in AdminAuthManager that accepts secret.
            // For now, I'll temporarily save it, verify, and if fail revert? No complex.
            // I'll update AdminAuthManager to have a verify method accepting secret bytes.
            
            // Wait, I can't easily modify AdminAuthManager again in this single step. 
            // I will save it. If the user doesn't complete verification, they have a set secret they don't know?
            // True. 
            // Let's replicate the verify logic locally here since it's simple.
            
            if (verifyTotpLocally(secretKey, code)) {
                // Success! Save permanently
                AdminAuthManager.setTotpSecret(this, secretKey)
                Toast.makeText(this, R.string.totp_setup_complete, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                editCode.error = getString(R.string.totp_incorrect)
            }
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    // Copy of verify logic from AdminAuthManager for local check before saving
    private fun verifyTotpLocally(secret: ByteArray, codeString: String): Boolean {
        val code = codeString.toLongOrNull() ?: return false
        val currentInterval = System.currentTimeMillis() / 1000 / 30
        for (i in -1..1) {
            if (generateTOTP(secret, currentInterval + i) == code) {
                return true
            }
        }
        return false
    }

    private fun generateTOTP(secret: ByteArray, interval: Long): Long {
        val data = java.nio.ByteBuffer.allocate(8).putLong(interval).array()
        val algo = "HmacSHA1"
        try {
            val mac = javax.crypto.Mac.getInstance(algo)
            mac.init(javax.crypto.spec.SecretKeySpec(secret, algo))
            val hash = mac.doFinal(data)
            val offset = hash[hash.size - 1].toInt() and 0xF
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)
            return (binary % 1_000_000).toLong()
        } catch (e: Exception) {
            return -1
        }
    }

    private fun generateQrCode(content: String, width: Int, height: Int): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height)
        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height
        val pixels = IntArray(matrixWidth * matrixHeight)
        for (y in 0 until matrixHeight) {
            val offset = y * matrixWidth
            for (x in 0 until matrixWidth) {
                pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.RGB_565)
        bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
        return bitmap
    }
}