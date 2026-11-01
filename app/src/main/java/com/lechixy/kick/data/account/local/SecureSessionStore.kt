package com.lechixy.kick.data.account.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.AtomicFile

/** Stored outside Auto Backup/device transfer and settings exports. */
class SecureSessionStore(context: Context) : SessionStore {
    private val directory = File(context.noBackupFilesDir, "kick_account").apply { mkdirs() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized
    override fun read(name: String): String? {
        val file = AtomicFile(File(directory, name))
        if (!file.baseFile.exists()) return null
        return try {
            val parts = file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }.split(':', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (_: Exception) { file.delete(); null }
    }

    @Synchronized
    override fun write(name: String, value: String?) {
        val file = AtomicFile(File(directory, name))
        if (value == null) { file.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        val output = file.startWrite()
        try { output.write(encrypted.toByteArray()); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }

    companion object { private const val ALIAS = "kick_account_v1" }
}
