package com.api.hazrat.util

import com.api.hazrat.util.AppSecret.ALGORITHM
import com.api.hazrat.util.AppSecret.IV_SIZE
import com.api.hazrat.util.AppSecret.KEY_BYTES
import com.api.hazrat.util.AppSecret.TAG_LENGTH
import com.api.hazrat.util.AppSecret.TRANSFORMATION
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtil {

    private val secretKey: SecretKey = generateKey()

    private fun generateKey() : SecretKey {
        val keyBytes = KEY_BYTES.toByteArray(Charsets.UTF_8)

        // Ensure key size is 16, 24, or 32 bytes (AES requirement)
        val sha = MessageDigest.getInstance("SHA-256")
        val hashedKey = sha.digest(keyBytes) // Hash it to get 256-bit (32 bytes)
        val finalKey = hashedKey.copyOf(32)  // AES-256 expects exactly 32 bytes
        return SecretKeySpec(finalKey, ALGORITHM)
    }

    fun encrypt(data: String) : String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        val combined = iv + encryptedBytes
        return  Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(data: String) : String {
        val decodeByteArray = Base64.getDecoder().decode(data)

        val iv = decodeByteArray.copyOfRange(0, IV_SIZE)
        val encryptedByteArray = decodeByteArray.copyOfRange(IV_SIZE, decodeByteArray.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))

        val decryptBytes = cipher.doFinal(encryptedByteArray)
        return String(decryptBytes, Charsets.UTF_8)

    }
}