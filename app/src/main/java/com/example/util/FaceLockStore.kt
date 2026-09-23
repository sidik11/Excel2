package com.example.util

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object FaceLockStore {
    private const val KEY_ALIAS = "ExcelImageVaultFaceLockKey"
    private const val TEMPLATE_FILE = "face_template.dat"
    private const val FORMAT = "FACE_TEMPLATE_V1"

    data class MatchResult(val matched: Boolean, val distance: Double)

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(256)
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? {
        return try {
        val parts = value.split(".")
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.DEFAULT))
        )
        String(
            cipher.doFinal(Base64.decode(parts[1], Base64.DEFAULT)),
            StandardCharsets.UTF_8
        )
        } catch (_: Throwable) { null }
    }

    fun saveTemplate(context: Context, vector: List<Float>): Pair<Boolean, String> {
        if (vector.size < 8) return false to "Face template is incomplete."
        return try {
            val now = System.currentTimeMillis()
            val array = JSONArray()
            vector.forEach { array.put(it.toDouble()) }
            val plain = JSONObject().apply {
                put("format", FORMAT)
                put("createdAt", now)
                put("updatedAt", now)
                put("vector", array)
            }.toString()
            val encrypted = encrypt(plain)
            val hash = sha256(plain)

            val dir = AppStorageHelper.getFaceLockDir(context)
            File(dir, TEMPLATE_FILE).writeText(
                JSONObject().apply {
                    put("format", FORMAT)
                    put("createdAt", now)
                    put("updatedAt", now)
                    put("templateHash", hash)
                    put("encryptedTemplate", encrypted)
                }.toString(),
                StandardCharsets.UTF_8
            )

            // Face Lock is independent from fingerprint registration. Keep
            // the complete face credential metadata in fingerprint.dat too.
            val fpFile = File(
                AppStorageHelper.getFingerprintDir(context),
                AppSecurityManager.FINGERPRINT_DAT_FILE_NAME
            )
            val old = if (fpFile.exists()) fpFile.readText(StandardCharsets.UTF_8) else ""
            val lines = old.lines().filter {
                !it.startsWith("FACE_TEMPLATE_FORMAT=") &&
                !it.startsWith("FACE_TEMPLATE_HASH=") &&
                !it.startsWith("FACE_TEMPLATE_ENCRYPTED=") &&
                !it.startsWith("FACE_LOCK_ENABLED=") &&
                !it.startsWith("IS_FACE_ENROLLED=") &&
                !it.startsWith("FACE_ENROLLED_AT=")
            }.toMutableList()
            while (lines.lastOrNull()?.isBlank() == true) lines.removeAt(lines.lastIndex)
            lines += "FACE_TEMPLATE_FORMAT=$FORMAT"
            lines += "FACE_TEMPLATE_HASH=$hash"
            lines += "FACE_TEMPLATE_ENCRYPTED=$encrypted"
            lines += "FACE_LOCK_ENABLED=true"
            lines += "IS_FACE_ENROLLED=true"
            lines += "FACE_ENROLLED_AT=$now"
            fpFile.writeText(lines.joinToString("\n") + "\n", StandardCharsets.UTF_8)

            true to "Face template saved to Android/media/${context.packageName}/facelock and fingerprint.dat."
        } catch (e: Throwable) {
            false to "Failed to save face template: ${e.message}"
        }
    }

    fun loadTemplate(context: Context): List<Float>? {
        return try {
        val file = File(AppStorageHelper.getFaceLockDir(context), TEMPLATE_FILE)
        if (!file.exists()) return null
        val wrapper = JSONObject(file.readText(StandardCharsets.UTF_8))
        val plain = decrypt(wrapper.optString("encryptedTemplate", "")) ?: return null
        val array = JSONObject(plain).optJSONArray("vector") ?: return null
        List(array.length()) { index -> array.getDouble(index).toFloat() }
        } catch (_: Throwable) { null }
    }

    fun compare(stored: List<Float>, live: List<Float>): MatchResult {
        if (stored.isEmpty() || live.size != stored.size) return MatchResult(false, Double.MAX_VALUE)
        var sum = 0.0
        for (i in stored.indices) {
            val d = (stored[i] - live[i]).toDouble()
            sum += d * d
        }
        val distance = kotlin.math.sqrt(sum / stored.size)
        return MatchResult(distance <= 0.20, distance)
    }

    fun isEnrolled(context: Context): Boolean =
        File(AppStorageHelper.getFaceLockDir(context), TEMPLATE_FILE).exists() &&
            loadTemplate(context) != null

    fun clear(context: Context) {
        try { File(AppStorageHelper.getFaceLockDir(context), TEMPLATE_FILE).delete() } catch (_: Throwable) {}
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
