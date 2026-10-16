package com.novelai.assistant.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.io.File
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore AES-GCM；敏感文件不参与备份。硬件保护取决于设备，不作强制保证。
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("novel_ai_secure", Context.MODE_PRIVATE)
    private val secrets = File(context.noBackupFilesDir, "secrets-v2").apply { check(isDirectory || mkdirs()) }

    private val masterKey: SecretKey by lazy { getOrCreateKey() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun file(key: String) = AtomicFile(File(secrets, MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ".bin"))
    private fun exists(target: AtomicFile) = target.baseFile.exists() || File(target.baseFile.path + ".bak").exists()
    @Synchronized fun containsEncrypted(key: String) = exists(file(key)) || prefs.contains(key)

    @Synchronized fun putEncrypted(key: String, plainText: String) {
        val target = file(key)
        if (plainText.isEmpty()) {
            target.delete()
            check(!target.baseFile.exists()) { "无法清除本机敏感数据" }
            check(prefs.edit().remove(key).commit()) { "无法清除旧版敏感数据" }
            return
        }
        val packed = SecretEnvelope.seal(key, plainText, masterKey)
        val out = target.startWrite()
        try { out.write(packed); target.finishWrite(out) }
        catch (e: Exception) { target.failWrite(out); throw e }
        check(prefs.edit().remove(key).commit()) { "敏感文件已保存，但旧版副本清理失败" }
    }

    @Synchronized fun getDecrypted(key: String): String? {
        val target = file(key)
        val legacy = prefs.getString(key, null)
        if (!exists(target) && legacy == null) return null
        val result = try {
            if (exists(target)) SecretEnvelope.open(key, target.readFully(), masterKey)
            else SecretEnvelope.openLegacy(Base64.decode(legacy, Base64.NO_WRAP), masterKey)
        } catch (_: Exception) {
            // Preserve ciphertext for recovery; never log plaintext, packed data or exception bodies.
            Log.w("SecureStorage", "Sensitive data could not be opened; authentication may be required")
            return null
        }
        // Delete the old copy only after a successful atomic, durable write.
        if (!exists(target)) putEncrypted(key, result)
        else if (legacy != null) check(prefs.edit().remove(key).commit())
        return result
    }

    fun putPlain(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun getPlain(key: String): String? = prefs.getString(key, null)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "novel_ai_master_key"
    }
}
