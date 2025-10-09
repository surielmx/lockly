package com.developermx.lockly

import android.app.Application
import com.google.crypto.tink.aead.AeadConfig
import java.security.GeneralSecurityException

class LocklyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            AeadConfig.register()
        } catch (e: GeneralSecurityException) {
            // Tink ya está inicializado.
        }
    }
}
