package com.example.pharmacypdf

import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.TimeUnit
import kotlin.experimental.xor

class LicenseManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val DEVICE_ID = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    private val SECRET_KEY = "PharmaKey2024" // غيرها بكلمة سر خاصة فيك

    fun startTrialIfFirstRun() {
        if (!prefs.contains("install_date")) {
            prefs.edit().putLong("install_date", System.currentTimeMillis()).apply()
        }
    }

    fun isActivated(): Boolean {
        val expiry = prefs.getLong("expiry_date", 0)
        if (expiry == 0L) return false
        return System.currentTimeMillis() < expiry
    }

    fun getRemainingDays(): Int {
        if (!isActivated()) return 0
        val expiry = prefs.getLong("expiry_date", 0)
        val diff = expiry - System.currentTimeMillis()
        return TimeUnit.MILLISECONDS.toDays(diff).toInt().coerceAtLeast(0)
    }

    fun activateWithCode(code: String): String {
        val decoded = decodeCode(code)
        if (decoded == null) return "كود غير صالح"

        val (deviceId, type) = decoded
        if (deviceId!= DEVICE_ID) return "هذا الكود ليس لهذا الجهاز"

        val duration = when (type) {
            'W' -> TimeUnit.DAYS.toMillis(7)
            'M' -> TimeUnit.DAYS.toMillis(30)
            'Y' -> TimeUnit.DAYS.toMillis(365)
            else -> return "نوع الكود غير معروف"
        }

        val expiryDate = System.currentTimeMillis() + duration
        prefs.edit().putLong("expiry_date", expiryDate).apply()
        return "تم التفعيل بنجاح"
    }

    fun generateCodeForDevice(deviceId: String, type: Char): String {
        val data = "$deviceId-$type"
        val keyBytes = SECRET_KEY.toByteArray()
        val dataBytes = data.toByteArray()
        val encodedBytes = dataBytes.mapIndexed { i, byte -> byte xor keyBytes[i % keyBytes.size] }.toByteArray()
        val encoded = android.util.Base64.encodeToString(encodedBytes, android.util.Base64.NO_WRAP)
        return "WD-$type${encoded.take(5).uppercase()}-${encoded.takeLast(5).uppercase()}"
    }

    private fun decodeCode(code: String): Pair<String, Char>? {
        try {
            val parts = code.split("-")
            if (parts.size!= 3 || parts[0]!= "WD") return null
            val type = parts[1][0]
            val encoded = parts[1].substring(1) + parts[2]
            val decodedBytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            val keyBytes = SECRET_KEY.toByteArray()
            val decoded = decodedBytes.mapIndexed { i, byte -> byte xor keyBytes[i % keyBytes.size] }.toByteArray().toString(Charsets.UTF_8)
            val dataParts = decoded.split("-")
            if (dataParts.size!= 2) return null
            return Pair(dataParts[0], dataParts[1][0])
        } catch (e: Exception) {
            return null
        }
    }
}
