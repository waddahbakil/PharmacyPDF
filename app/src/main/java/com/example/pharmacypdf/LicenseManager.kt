package com.example.pharmacypdf

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LicenseManager(private val context: Context) {

    // 1. غير هذي لقيمة من عندك. مثل: "MyApp2026@Yemen!"
    private val MY_SECRET_KEY = intArrayOf(77, 121, 65, 112, 50, 48, 50, 54, 64, 89, 101, 109, 101, 110, 33)

    // 2. غير هذي لبصمة توقيعك. الشرح تحت كيف تجيبها
    private val MY_SIGNATURE_HASH = "PUT_YOUR_SHA256_HASH_HERE"

    private val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_license_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val KEY_EXPIRY_DATE = "expiry_date_enc"
        private const val KEY_USED_CODES = "used_codes_enc"
    }

    private fun getSecret(): String {
        return MY_SECRET_KEY.map { it.toChar() }.joinToString("")
    }

    fun startTrialIfFirstRun() {
        if (!prefs.contains(KEY_EXPIRY_DATE)) {
            val now = System.currentTimeMillis()
            val expiry = now + TimeUnit.DAYS.toMillis(2)
            prefs.edit().putLong(KEY_EXPIRY_DATE, expiry).apply()
        }
    }

    fun isActivated(): Boolean {
        if (!checkAppSignature()) return false // لو التطبيق معدل يقفل
        val expiry = prefs.getLong(KEY_EXPIRY_DATE, 0)
        return System.currentTimeMillis() < expiry
    }

    fun getRemainingDays(): Int {
        val expiry = prefs.getLong(KEY_EXPIRY_DATE, 0)
        val diff = expiry - System.currentTimeMillis()
        return if (diff > 0) TimeUnit.MILLISECONDS.toDays(diff).toInt() else 0
    }

    fun activateWithCode(code: String): String {
        if (!checkAppSignature()) return "تم اكتشاف تعديل على التطبيق"

        val usedCodes = prefs.getStringSet(KEY_USED_CODES, mutableSetOf()) ?: mutableSetOf()
        if (usedCodes.contains(code)) {
            return "هذا الكود مستخدم من قبل"
        }

        val duration = validateCode(code)
        if (duration == 0) {
            return "كود التفعيل غير صحيح"
        }

        val now = System.currentTimeMillis()
        val expiry = now + TimeUnit.DAYS.toMillis(duration.toLong())

        prefs.edit()
           .putLong(KEY_EXPIRY_DATE, expiry)
           .putStringSet(KEY_USED_CODES, usedCodes + code)
           .apply()

        return "تم التفعيل بنجاح لمدة $duration يوم"
    }

    private fun validateCode(code: String): Int {
        if (!code.startsWith("WD-") || code.length < 10) return 0
        val parts = code.split("-")
        if (parts.size != 3) return 0

        val type = parts[1].first()
        val hash = parts[1].substring(1)
        val check = parts[2]

        val days = when (type) {
            'W' -> 7
            'M' -> 30
            'Y' -> 365
            else -> return 0
        }

        val validHash = sha256(deviceId + getSecret() + type).substring(0, 4).uppercase()
        val validCheck = sha256(validHash + getSecret()).substring(0, 1).uppercase()

        return if (hash == validHash && check == validCheck) days else 0
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // دالة لتوليد الأكواد - تستخدمها أنت فقط
    fun generateCode(type: Char): String {
        val hash = sha256(deviceId + getSecret() + type).substring(0, 4).uppercase()
        val check = sha256(hash + getSecret()).substring(0, 1).uppercase()
        return "WD-$type$hash-$check"
    }
    
    // تحقق أن التطبيق موقع بتوقيعك أنت وليس معدل
    private fun checkAppSignature(): Boolean {
        if (MY_SIGNATURE_HASH == "PUT_YOUR_SHA256_HASH_HERE") return true // تجاهل التحقق لو ما عدلتها
        try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            for (signature in signatures) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(signature.toByteArray())
                val currentSignature = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                if (currentSignature == MY_SIGNATURE_HASH) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
