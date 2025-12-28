package com.example.studylockapp.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object AdminAuthManager {

    // region Pref keys
    private const val KEY_ADMIN_LOCK_ENABLED = "adminLockEnabled"
    private const val KEY_ADMIN_BUTTON_HIDDEN = "adminButtonHidden"
    private const val KEY_HAS_COMPLETED_INITIAL_SETUP = "hasCompletedInitialSetup"
    private const val KEY_APP_LOCK_REQUIRED = "appLockRequired"

    private const val KEY_ADMIN_PIN_SALT_B64 = "adminPinSaltB64"
    private const val KEY_ADMIN_PIN_HASH_B64 = "adminPinHashB64"

    private const val KEY_ADMIN_RECOVERY_SALT_B64 = "adminRecoverySaltB64"
    private const val KEY_ADMIN_RECOVERY_HASH_B64 = "adminRecoveryHashB64"
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
        // UI側で数字のみ制限してもOKだが、ここでも最低限チェック
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

    // region Recovery code (英数字)
    fun isRecoveryCodeSet(context: Context): Boolean {
        val sp = prefs(context)
        return sp.getString(KEY_ADMIN_RECOVERY_SALT_B64, null) != null &&
                sp.getString(KEY_ADMIN_RECOVERY_HASH_B64, null) != null
    }

    /**
     * 親が控える用の復旧コードを生成（表示はUI側で「初回のみ」運用）
     * 紛らわしい文字を除外: 0,O,1,I,l など
     */
    fun generateRecoveryCode(length: Int = 16): String {
        require(length >= 12) { "Recovery code length should be >= 12" }

        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(chars[rnd.nextInt(chars.length)])
        }
        return sb.toString()
    }

    fun setRecoveryCode(context: Context, code: String) {
        require(code.length >= 12) { "Recovery code must be at least 12 chars" }

        val salt = randomBytes(16)
        val hash = sha256(salt, code.toByteArray(Charsets.UTF_8))

        prefs(context).edit()
            .putString(KEY_ADMIN_RECOVERY_SALT_B64, b64(salt))
            .putString(KEY_ADMIN_RECOVERY_HASH_B64, b64(hash))
            .apply()
    }

    fun verifyRecoveryCode(context: Context, code: String): Boolean {
        val sp = prefs(context)
        val saltB64 = sp.getString(KEY_ADMIN_RECOVERY_SALT_B64, null) ?: return false
        val hashB64 = sp.getString(KEY_ADMIN_RECOVERY_HASH_B64, null) ?: return false

        val salt = fromB64(saltB64)
        val expected = fromB64(hashB64)
        val actual = sha256(salt, code.toByteArray(Charsets.UTF_8))

        return MessageDigest.isEqual(expected, actual)
    }
    // endregion

    fun clearPinAndRecovery(context: Context) {
        prefs(context).edit()
            .remove(KEY_ADMIN_PIN_SALT_B64)
            .remove(KEY_ADMIN_PIN_HASH_B64)
            .remove(KEY_ADMIN_RECOVERY_SALT_B64)
            .remove(KEY_ADMIN_RECOVERY_HASH_B64)
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

