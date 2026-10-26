package com.mantra.remote

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * v16 (Marko, 26.10.2026: "for the phone it is a PIN. So if I enter the PIN there, it connects to my phone and unlocks
 * it with the PIN"): a phone's PIN kept on this phone only, encrypted with a key that never leaves the Android
 * Keystore (AES-GCM); the preferences hold only the ciphertext.
 */
object Secrets {
    private const val ALIAS = "mantra-remote-pins"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }

    fun putPin(prefs: SharedPreferences?, address: String, pin: String) {
        val p = prefs ?: return
        if (pin.isEmpty()) { p.edit().remove("pin:$address").apply(); return }
        runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val out = c.iv + c.doFinal(pin.toByteArray())
            p.edit().putString("pin:$address", Base64.encodeToString(out, Base64.NO_WRAP)).apply()
        }
    }

    fun pin(prefs: SharedPreferences?, address: String): String {
        val b64 = prefs?.getString("pin:$address", null) ?: return ""
        return runCatching {
            val all = Base64.decode(b64, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, 12)) }
            String(c.doFinal(all, 12, all.size - 12))
        }.getOrDefault("")
    }
}
