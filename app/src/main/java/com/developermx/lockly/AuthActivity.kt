package com.developermx.lockly

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.developermx.lockly.ui.AuthScreen
import com.developermx.lockly.ui.theme.LocklyTheme

class AuthActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            LocklyTheme {
                val passwordState = remember { mutableStateOf("") }
                AuthScreen(
                    onAuthenticated = {
                        val intent = Intent(this@AuthActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        intent.putExtra("_vlt_pwd.bin", passwordState.value)
                        startActivity(intent)
                        finish()
                    },
                    onPasswordChanged = { passwordState.value = it }
                )
            }
        }
    }
}
