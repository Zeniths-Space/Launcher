package com.elara.launcher

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Asks for the device's screen lock (fingerprint, face, PIN, pattern or password) and calls
 * [onSuccess] once it is given. Android's own prompt, so this app never sees the credential.
 * Without a screen lock there is nothing to ask for, so it says so and does not call [onSuccess].
 */
@Composable
fun rememberDeviceAuth(title: String, onSuccess: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val success by rememberUpdatedState(onSuccess)
    // Android 9 and older: the system's confirm-credential screen.
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) success()
    }
    return {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        when {
            !keyguard.isDeviceSecure -> Toast.makeText(context, R.string.auth_no_lock, Toast.LENGTH_LONG).show()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> prompt(context, title) { success() }
            else -> {
                @Suppress("DEPRECATION")
                keyguard.createConfirmDeviceCredentialIntent(title, null)?.let { confirm.launch(it) } ?: success()
            }
        }
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun prompt(context: Context, title: String, onSuccess: () -> Unit) {
    val builder = BiometricPrompt.Builder(context).setTitle(title)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
    } else {
        @Suppress("DEPRECATION")
        builder.setDeviceCredentialAllowed(true)
    }
    builder.build().authenticate(
        CancellationSignal(),
        context.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
}
