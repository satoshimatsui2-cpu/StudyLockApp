package com.example.studylockapp.data

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AdminAuthManager {

    // region Pref keys
    private const val KEY_ADMIN_LOCK_ENABLED = "adminLockEnabled"
    private const val KEY_ADMIN_BUTTON_HIDDEN = "adminButtonHidden"
    private const val KEY_HAS_COMPLETED_INITIAL_SETUP = "hasCompletedInitialSetup"
    private const val KEY_APP_LOCK_REQUIRED = "appLockRequired"

    private const val KEY_ADMIN_PIN_SALT_B64 = "adminPinSaltB64"
    private const val KEY_ADMIN_PIN_HASH_B64 = "adminPinHashB64"

    // TOTP Secret (Base32 encoded string is standard for Authenticator apps, but we store raw bytes or Base64 here)
    // Authenticator用のURIにする際はBase32エンコードが必要。保存はBase64で統一して管理。
    private const val KEY_TOTP_SECRET_B64 = "adminTotpSecretB64"
    // endregion

    // region Flags
    fun isAdminLockEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ADMIN_LOCK_ENABLED, false)
    }

    fun setAdminLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADMIN_LOCK_ENABLED, enabled).apply()
    }

    fun isAdminButtonHidden(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ADMIN_BUTTON_HIDDEN, false)
    }

    fun setAdminButtonHidden(context: Context, hidden: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADMIN_BUTTON_HIDDEN, hidden).apply()
    }

    fun hasCompletedInitialSetup(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HAS_COMPLETED_INITIAL_SETUP, false)
    }

    fun setCompletedInitialSetup(context: Context, completed: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAS_COMPLETED_INITIAL_SETUP, completed).apply()
    }

    fun isAppLockRequired(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_APP_LOCK_REQUIRED, false)
    }

    fun setAppLockRequired(context: Context, required: Boolean) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK_REQUIRED, required).apply()
    }
    // endregion

    // region PIN
    fun isPinSet(context: Context): Boolean {
        val sp = prefs(context)
        return sp.getString(KEY_ADMIN_PIN_SALT_B64, null) != null &&
                sp.getString(KEY_ADMIN_PIN_HASH_B64, null) != null
    }

    fun setPin(context: Context, pin: String) {
        require(pin.length >= 4) { "PIN must be at least 4 chars" }

        val salt = randomBytes(16)
        val hash = sha256(salt, pin.toByteArray(Charsets.UTF_8))

        prefs(context).edit()
            .putString(KEY_ADMIN_PIN_SALT_B64, b64(salt))
            .putString(KEY_ADMIN_PIN_HASH_B64, b64(hash))
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val sp = prefs(context)
        val saltB64 = sp.getString(KEY_ADMIN_PIN_SALT_B64, null) ?: return false
        val hashB64 = sp.getString(KEY_ADMIN_PIN_HASH_B64, null) ?: return false

        val salt = fromB64(saltB64)
        val expected = fromB64(hashB64)
        val actual = sha256(salt, pin.toByteArray(Charsets.UTF_8))

        return MessageDigest.isEqual(expected, actual)
    }
    // endregion

    // region TOTP (Google Authenticator)
    fun isTotpSet(context: Context): Boolean {
        return prefs(context).getString(KEY_TOTP_SECRET_B64, null) != null
    }

    /**
     * 新しいシークレットキー（20バイト推奨）を生成してBase32文字列（URI用）とBase64（保存用）を返す
     * 保存はしない。ユーザーが確認後に setTotpSecret を呼ぶ想定。
     */
    fun generateTotpSecretKey(): ByteArray {
        // 160 bits = 20 bytes is recommended for SHA1
        return randomBytes(20)
    }

    fun setTotpSecret(context: Context, secretBytes: ByteArray) {
        prefs(context).edit()
            .putString(KEY_TOTP_SECRET_B64, b64(secretBytes))
            .apply()
    }

    /**
     * TOTPコード検証
     * Google Authenticator仕様: HMAC-SHA1, 30秒ステップ, 6桁
     * 前後1ステップ（計90秒）の猶予を持たせる
     */
    fun verifyTotp(context: Context, codeString: String): Boolean {
        val secretB64 = prefs(context).getString(KEY_TOTP_SECRET_B64, null) ?: return false
        val secret = fromB64(secretB64)
        val code = codeString.toLongOrNull() ?: return false

        // Check current, prev, next intervals
        val currentInterval = System.currentTimeMillis() / 1000 / 30
        for (i in -1..1) {
            if (generateTOTP(secret, currentInterval + i) == code) {
                return true
            }
        }
        return false
    }

    // TOTP algorithm implementation
    private fun generateTOTP(secret: ByteArray, interval: Long): Long {
        val data = ByteBuffer.allocate(8).putLong(interval).array()
        val algo = "HmacSHA1"
        try {
            val mac = Mac.getInstance(algo)
            mac.init(SecretKeySpec(secret, algo))
            val hash = mac.doFinal(data)

            // Dynamic truncation
            val offset = hash[hash.size - 1].toInt() and 0xF
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            return (binary % 1_000_000).toLong()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }

    /**
     * Base32 Encode (RFC 4648) for Google Authenticator URI
     * Simplified implementation
     */
    fun toBase32(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var i = 0
        var index = 0
        var digit = 0
        val sb = StringBuilder((bytes.size * 8 + 4) / 5)

        while (i < bytes.size) {
            val currByte = if (bytes[i] >= 0) bytes[i].toInt() else bytes[i].toInt() + 256

            if (index > 3) {
                val nextByte = if ((i + 1) < bytes.size)
                    (if (bytes[i + 1] >= 0) bytes[i + 1].toInt() else bytes[i + 1].toInt() + 256)
                else 0

                digit = currByte and (0xFF shr index)
                index = (index + 5) % 8
                digit = digit shl index
                digit = digit or (nextByte shr (8 - index))
                i++
            } else {
                digit = (currByte shr (8 - (index + 5))) and 0x1F
                index = (index + 5) % 8
                if (index == 0) i++
            }
            sb.append(alphabet[digit])
        }
        return sb.toString()
    }
    // endregion

    fun clearPinAndRecovery(context: Context) {
        prefs(context).edit()
            .remove(KEY_ADMIN_PIN_SALT_B64)
            .remove(KEY_ADMIN_PIN_HASH_B64)
            // .remove(KEY_TOTP_SECRET_B64) // TOTPは残すべきかもしれないが、初期化なら消す
            .remove(KEY_TOTP_SECRET_B64)
            .apply()
    }

    // region internals
    private fun prefs(context: Context) = AppSettings.getPrefs(context)

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also {
        SecureRandom().nextBytes(it)
    }

    private fun sha256(salt: ByteArray, input: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(input)
        return md.digest()
    }

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun fromB64(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)
    // endregion
}