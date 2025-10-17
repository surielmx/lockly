package com.developermx.lockly.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.developermx.lockly.VaultManager

data class PasswordStrength(
    val isValid: Boolean,
    val score: Int, // 0-3: 0=muy débil, 1=débil, 2=buena, 3=fuerte
    val message: String,
    val color: Color
)

fun validatePasswordStrength(password: String): PasswordStrength {
    if (password.length < 8) {
        return PasswordStrength(false, 0, "Mínimo 8 caracteres", Color(0xFFFFB4AB))
    }

    var score = 0
    val hasUpperCase = password.any { it.isUpperCase() }
    val hasLowerCase = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSpecial = password.any { !it.isLetterOrDigit() }

    if (hasUpperCase) score++
    if (hasLowerCase) score++
    if (hasDigit) score++
    if (hasSpecial) score++

    return when {
        score >= 4 -> PasswordStrength(true, 3, "Contraseña fuerte", Color(0xFFACD28E))
        score >= 3 -> PasswordStrength(true, 2, "Contraseña buena", Color(0xFFA0CFCE))
        score >= 2 -> PasswordStrength(true, 1, "Contraseña débil", Color(0xFFFBBF24))
        else -> PasswordStrength(false, 0, "Contraseña muy débil", Color(0xFFFFB4AB))
    }
}

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onPasswordChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val hasPassword = VaultManager.hasPassword(context)
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var biometricError by remember { mutableStateOf("") }
    var passwordJustCreated by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val activity = context as? FragmentActivity
    val biometricManager = BiometricManager.from(context)

    // Verificar capacidad biométrica del dispositivo
    val canUseBiometric = remember {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val showBiometricButton = hasPassword && canUseBiometric

    fun savePasswordToPrefs(password: String) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            "vault_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.edit {
            putString("vault_password", password)
        }
    }

    fun getPasswordFromPrefs(): String {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            "vault_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return prefs.getString("vault_password", "") ?: ""
    }

    fun showBiometricPrompt() {
        if (activity == null) return
        biometricError = "" // Limpiar errores previos
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    biometricError = ""
                    // Load password from encrypted preferences before authenticating
                    val pwd = getPasswordFromPrefs()
                    if (pwd.isNotEmpty()) {
                        onPasswordChanged(pwd)
                    }
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    biometricError = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED -> "Autenticación cancelada"
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "Autenticación cancelada"
                        BiometricPrompt.ERROR_LOCKOUT -> "Demasiados intentos. Usa tu contraseña."
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Sensor bloqueado. Usa tu contraseña."
                        BiometricPrompt.ERROR_NO_BIOMETRICS -> "No hay biometría registrada"
                        BiometricPrompt.ERROR_HW_NOT_PRESENT -> "Sensor biométrico no disponible"
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> "Sensor biométrico no disponible"
                        BiometricPrompt.ERROR_TIMEOUT -> "Tiempo de espera agotado. Intenta de nuevo."
                        else -> "Error de autenticación: $errString"
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    biometricError = "Huella no reconocida. Intenta de nuevo."
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación biométrica")
            .setSubtitle("Usa tu huella o biometría para acceder")
            .setNegativeButtonText("Cancelar")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    // Show biometric prompt automatically if a password exists
    LaunchedEffect(hasPassword) {
        if (hasPassword && !passwordJustCreated) {
            showBiometricPrompt()
        }
    }

    // Show biometric prompt automatically after creating password
    LaunchedEffect(passwordJustCreated) {
        if (passwordJustCreated && canUseBiometric) {
            showBiometricPrompt()
            passwordJustCreated = false
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (!hasPassword) "Bienvenido a Lockly" else "Acceso seguro",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (!hasPassword) "Crea tu contraseña maestra o usa tu huella para empezar" else "Ingresa tu contraseña maestra o usa biometría",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onPasswordChanged(it)
                    },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (hasPassword) ImeAction.Done else ImeAction.Next
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground
                    )
                )

                // Indicador de fortaleza de contraseña (solo al crear)
                if (!hasPassword && password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val strength = validatePasswordStrength(password)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strength.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = strength.color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Campo de confirmación de contraseña (solo al crear)
                if (!hasPassword) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirmar contraseña") },
                        singleLine = true,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (confirmPasswordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Button(
                    onClick = {
                        successMessage = ""
                        error = ""

                        // Validaciones para crear nueva contraseña
                        if (!hasPassword) {
                            val strength = validatePasswordStrength(password)

                            when {
                                !strength.isValid -> {
                                    error = "La contraseña debe tener al menos 8 caracteres."
                                }
                                password != confirmPassword -> {
                                    error = "Las contraseñas no coinciden."
                                }
                                else -> {
                                    VaultManager.savePasswordAndUserId(context, password)
                                    savePasswordToPrefs(password)
                                    if (canUseBiometric) {
                                        successMessage = "Contraseña creada. Configurando autenticación biométrica..."
                                        passwordJustCreated = true
                                    } else {
                                        successMessage = "Contraseña creada exitosamente."
                                    }
                                    password = ""
                                    confirmPassword = ""
                                }
                            }
                        } else {
                            // Validación para login con contraseña existente
                            if (password.isEmpty()) {
                                error = "Ingresa tu contraseña."
                            } else if (VaultManager.validatePassword(context, password)) {
                                // --- FIX: Ensure User ID exists to prevent crash on upload ---
                                VaultManager.ensureUserIdExists(context, password)
                                // --- End of FIX ---
                                savePasswordToPrefs(password)
                                onAuthenticated()
                            } else {
                                error = "Contraseña incorrecta."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (!hasPassword) "Crear contraseña" else "Acceder",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (error.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }
                if (successMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = successMessage, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (showBiometricButton) {
                    Button(
                        onClick = {
                            val pwd = getPasswordFromPrefs()
                            if (pwd.isNotEmpty()) {
                                onPasswordChanged(pwd)
                            }
                            showBiometricPrompt()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Text("Usar biometría", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    // Mostrar error biométrico si existe
                    if (biometricError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = biometricError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (hasPassword) {
                    // Mostrar mensaje informativo si no hay biometría disponible
                    val biometricStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    val infoMessage = when (biometricStatus) {
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                            "Este dispositivo no tiene sensor biométrico"
                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                            "Sensor biométrico no disponible actualmente"
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                            "No hay huellas registradas en el dispositivo. Configura biometría en Ajustes del sistema."
                        else -> "Autenticación biométrica no disponible"
                    }
                    Text(
                        text = infoMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}
