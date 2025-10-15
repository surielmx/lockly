package com.developermx.lockly

import android.app.Application
import android.util.Log
import com.google.crypto.tink.aead.AeadConfig

class LocklyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            Log.d("LocklyApplication", "Initializing Tink...")
            AeadConfig.register()
            Log.i("LocklyApplication", "Tink initialized successfully.")
        } catch (t: Throwable) { // Catching Throwable to find any possible startup error
            // This will prevent the app from crashing and will show us the real error.
            Log.e("LocklyApplication", "FATAL: Failed to initialize application", t)
        }
    }
}
