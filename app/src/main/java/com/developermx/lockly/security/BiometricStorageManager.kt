package com.developermx.lockly.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets

object BiometricStorageManager {

    private const val TAG = "BiometricStorageManager"
    private const val PREFERENCES_FILE_NAME = "biometric_secret_prefs"
    private const val ENCRYPTED_PASSWORD_KEY = "encrypted_master_password"

    private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
        // Use the modern MasterKey.Builder API
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(true)
            .build()

        // Use the modern EncryptedSharedPreferences.create method that takes a MasterKey object
        return EncryptedSharedPreferences.create(
            context,
            PREFERENCES_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun encryptAndStorePassword(context: Context, password: String) {
        try {
            val sharedPreferences = createEncryptedSharedPreferences(context)
            val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
            val passwordBase64 = Base64.encodeToString(passwordBytes, Base64.NO_WRAP)
            sharedPreferences.edit().putString(ENCRYPTED_PASSWORD_KEY, passwordBase64).apply()
            Log.d(TAG, "Master password encrypted and stored successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and store password.", e)
        }
    }

    fun decryptPasswordWithBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: (password: String) -> Unit,
        onError: (errorCode: Int, errString: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación biométrica")
            .setSubtitle("Usa tu huella para acceder a Lockly")
            .setNegativeButtonText("Usar contraseña")
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d(TAG, "Biometric authentication error: $errString ($errorCode)")
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Biometric authentication succeeded.")
                try {
                    // Now that the user has authenticated, we can access the key to decrypt the password.
                    val sharedPreferences = createEncryptedSharedPreferences(activity)
                    val encryptedPasswordBase64 = sharedPreferences.getString(ENCRYPTED_PASSWORD_KEY, null)

                    if (encryptedPasswordBase64 != null) {
                        val passwordBytes = Base64.decode(encryptedPasswordBase64, Base64.NO_WRAP)
                        val password = String(passwordBytes, StandardCharsets.UTF_8)
                        onSuccess(password)
                    } else {
                        onError(-1, "No password found to decrypt.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt password after biometric auth.", e)
                    onError(-1, "Decryption failed.")
                }
            }
        })

        biometricPrompt.authenticate(promptInfo)
    }
}