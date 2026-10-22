package com.mailmate.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private local storage. SMTP app password is encrypted with an Android Keystore AES key. */
class LocalStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("mailmate", Context.MODE_PRIVATE)
    private val filesDir = File(context.filesDir, "resumes").apply { mkdirs() }

    fun loadConfig(): SmtpConfig = SmtpConfig(
        host = prefs.getString("smtp_host", "smtp.gmail.com") ?: "smtp.gmail.com",
        port = prefs.getString("smtp_port", "587") ?: "587",
        email = prefs.getString("smtp_email", "") ?: "",
        appPassword = decrypt(prefs.getString("smtp_password", "") ?: "")
    )

    fun saveConfig(config: SmtpConfig) {
        prefs.edit()
            .putString("smtp_host", config.host.trim())
            .putString("smtp_port", config.port.trim())
            .putString("smtp_email", config.email.trim())
            .putString("smtp_password", encrypt(config.appPassword))
            .apply()
    }

    fun resumeDirectory(): File = filesDir

    fun loadTemplates(): List<MailTemplate> {
        val raw = prefs.getString("templates", null) ?: return listOf(defaultTemplate())
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MailTemplate(o.getString("id"), o.getString("name"), o.getString("subject"), o.getString("body"))
            }
        } catch (_: Exception) { listOf(defaultTemplate()) }
    }

    fun saveTemplates(items: List<MailTemplate>) {
        val arr = JSONArray()
        items.forEach { t -> arr.put(JSONObject().put("id", t.id).put("name", t.name).put("subject", t.subject).put("body", t.body)) }
        prefs.edit().putString("templates", arr.toString()).apply()
    }

    fun loadResumes(): List<ResumeFile> {
        val raw = prefs.getString("resumes", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ResumeFile(o.getString("id"), o.getString("name"), o.getString("fileName"))
            }.filter { File(filesDir, it.fileName).exists() }
        } catch (_: Exception) { emptyList() }
    }

    fun saveResumes(items: List<ResumeFile>) {
        val arr = JSONArray()
        items.forEach { r -> arr.put(JSONObject().put("id", r.id).put("name", r.name).put("fileName", r.fileName)) }
        prefs.edit().putString("resumes", arr.toString()).apply()
    }

    fun loadHistory(): List<SendRecord> {
        val raw = prefs.getString("history", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SendRecord(o.getLong("timestamp"), o.getString("recipient"), o.getString("subject"), o.getString("status"), o.optString("details", ""))
            }.sortedByDescending { it.timestamp }
        } catch (_: Exception) { emptyList() }
    }

    fun addHistory(record: SendRecord) {
        val arr = JSONArray()
        (listOf(record) + loadHistory()).take(250).forEach { r ->
            arr.put(JSONObject().put("timestamp", r.timestamp).put("recipient", r.recipient).put("subject", r.subject).put("status", r.status).put("details", r.details))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun defaultTemplate() = MailTemplate(
        name = "Job application",
        subject = "Application for {{role}}",
        body = "Hello,\n\nI am writing to express my interest in the {{role}} opportunity. Please find my resume attached for your consideration.\n\nThank you for your time.\n\nRegards,\n{{your_name}}"
    )

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        return try {
            val packed = Base64.decode(value, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    companion object { private const val KEY_ALIAS = "mailmate_smtp_key_v1" }
}
