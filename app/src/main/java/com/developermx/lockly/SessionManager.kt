package com.developermx.lockly

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages the user's session and stores the master password securely.
 * The password is encrypted and persists during the app session.
 */
object SessionManager {
    private const val PREFS_NAME = "session_prefs"
    private const val KEY_SESSION_PASSWORD = "session_password"

    private var masterPassword: String? = null
    private var encryptedPrefs: SharedPreferences? = null

    /**
     * Initialize the session manager with context.
     * This should be called when the app starts.
     */
    fun init(context: Context) {
        if (encryptedPrefs == null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // Load password from encrypted prefs if exists
            masterPassword = encryptedPrefs?.getString(KEY_SESSION_PASSWORD, null)
        }
    }

    /**
     * Sets the master password for the current session.
     */
    fun setPassword(password: String) {
        masterPassword = password
        encryptedPrefs?.edit()?.putString(KEY_SESSION_PASSWORD, password)?.apply()
    }

    /**
     * Gets the master password for the current session.
     * Returns null if no session is active.
     */
    fun getPassword(): String? {
        // If password is in memory, return it
        if (masterPassword != null) {
            android.util.Log.d("SessionManager", "Password retrieved from memory: ${masterPassword?.length} chars")
            return masterPassword
        }

        // Otherwise, try to load from encrypted prefs
        masterPassword = encryptedPrefs?.getString(KEY_SESSION_PASSWORD, null)
        android.util.Log.d("SessionManager", "Password retrieved from prefs: ${if (masterPassword != null) "${masterPassword?.length} chars" else "NULL"}")
        return masterPassword
    }

    /**
     * Checks if there's an active session with a password set.
     */
    fun hasActiveSession(): Boolean {
        return getPassword() != null
    }

    /**
     * Clears the session and removes the password from memory and storage.
     */
    fun clearSession() {
        masterPassword = null
        encryptedPrefs?.edit()?.remove(KEY_SESSION_PASSWORD)?.apply()
    }
}
