package com.novelai.assistant.data.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A versioned, name-bound envelope. Legacy values are read only during migration. */
object SecretEnvelope {
    private val magic = byteArrayOf(78, 65, 83, 2)
    fun seal(name: String, text: String, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD("NovelAI:$name:v2".toByteArray(Charsets.UTF_8))
        return magic + cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }
    fun open(name: String, packed: ByteArray, key: SecretKey): String {
        require(packed.size >= 32 && packed.copyOfRange(0, 4).contentEquals(magic))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, packed.copyOfRange(4, 16)))
        cipher.updateAAD("NovelAI:$name:v2".toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(packed.copyOfRange(16, packed.size)), Charsets.UTF_8)
    }
    fun openLegacy(packed: ByteArray, key: SecretKey): String {
        require(packed.size >= 29 && packed[0].toInt() == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, packed.copyOfRange(1, 13)))
        return String(cipher.doFinal(packed.copyOfRange(13, packed.size)), Charsets.UTF_8)
    }
}
