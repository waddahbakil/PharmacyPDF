package com.example.pharmacypdf

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.TimeUnit

class LicenseManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)
    private val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    companion object {
        private const val KEY_ACTIVATION_DATE = "activation_date"
        private const val KEY_EXPIRY_DATE = "expiry_date"
        private const val KEY_USED_CODES = "used_codes"
    }

    // تشغيل الفترة التجريبية يومين أول مرة
    fun startTrialIfFirstRun() {
        if (!prefs.contains(KEY_ACTIVATION_DATE)) {
            val now = System.currentTimeMillis()
            val expiry = now + TimeUnit.DAYS.toMillis(2)
            prefs.edit()
               .putLong(KEY_ACTIVATION_DATE, now)
               .putLong(KEY_EXPIRY_DATE, expiry)
               .apply()
        }
    }

    // هل التطبيق مفعل؟
    fun isActivated(): Boolean {
        val expiry = prefs.getLong(KEY_EXPIRY_DATE, 0)
        return System.currentTimeMillis() < expiry
    }

    // كم باقي يوم
    fun getRemainingDays(): Int {
        val expiry = prefs.getLong(KEY_EXPIRY_DATE, 0)
        val diff = expiry - System.currentTimeMillis()
        return if (diff > 0) TimeUnit.MILLISECONDS.toDays(diff).toInt() else 0
    }

    // تفعيل بكود
    fun activateWithCode(code: String): String {
        val usedCodes = prefs.getStringSet(KEY_USED_CODES, mutableSetOf())?: mutableSetOf()
        
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
           .putLong(KEY_ACTIVATION_DATE, now)
           .putLong(KEY_EXPIRY_DATE, expiry)
           .putStringSet(KEY_USED_CODES, usedCodes + code)
           .apply()
        
        return "تم التفعيل بنجاح لمدة $duration يوم"
    }

    // التحقق من الكود: يرجع عدد الأيام أو 0 لو غلط
    private fun validateCode(code: String): Int {
        // صيغة الكود: WD-XXXX-YYYY-Z
        // X = نوع: W=اسبوع, M=شهر, Y=سنة
        // Y = هاش من deviceId + كلمة سر
        // Z = Checksum
        if (!code.startsWith("WD-") || code.length < 10) return 0

        val parts = code.split("-")
        if (parts.size!= 3) return 0

        val type = parts[1].first()
        val hash = parts[1].substring(1)
        val check = parts[2]

        val days = when (type) {
            'W' -> 7
            'M' -> 30
            'Y' -> 365
            else -> return 0
        }

        // كلمة السرية لتوليد الأكواد. غيرها بكلمة من عندك
        val secret = "WaddahBakil2026" 
        val validHash = sha256(deviceId + secret + type).substring(0, 4).uppercase()
        val validCheck = sha256(validHash + secret).substring(0, 1).uppercase()

        return if (hash == validHash && check == validCheck) days else 0
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // دالة لتوليد الأكواد - تستخدمها أنت بس على جهازك
    fun generateCode(type: Char): String {
        val secret = "WaddahBakil2026"
        val hash = sha256(deviceId + secret + type).substring(0, 4).uppercase()
        val check = sha256(hash + secret).substring(0, 1).uppercase()
        return "WD-$type$hash-$check"
    }
}
