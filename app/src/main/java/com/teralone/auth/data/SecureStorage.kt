package com.teralone.auth.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAccount(name: String, secret: String) {
        sharedPreferences.edit().putString(name, secret).apply()
    }

    fun getAccount(name: String): String? {
        return sharedPreferences.getString(name, null)
    }

    fun getAllAccounts(): Map<String, *> {
        return sharedPreferences.all
    }
}
