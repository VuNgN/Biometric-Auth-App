package com.vungn.authentication.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vungn.authentication.ui.theme.AuthenticationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.concurrent.Executor

class AuthenticationActivity : AppCompatActivity() {
    private val biometricEnrollActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "Biometric enroll success")
            } else {
                Log.d(TAG, "Biometric enroll failed")
            }
            checkBiometric()
        }
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private val isBiometricEnrolled = MutableStateFlow(false)
    private val errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkBiometric()
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt =
            BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int, errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(
                        applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    val encryptedInfo: ByteArray? = result.cryptoObject?.cipher?.doFinal(
                        // plaintext-string text is whatever data the developer would like
                        // to encrypt. It happens to be plain-text in this example, but it
                        // can be anything
                        "NguyenNgocVu".toByteArray(Charset.defaultCharset())
                    )
                    Log.d(
                        TAG, "Encrypted information: " + encryptedInfo.contentToString()
                    )
                    Toast.makeText(
                        applicationContext, "Authentication succeeded!", Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        applicationContext, "Authentication failed", Toast.LENGTH_SHORT
                    ).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use account password").build()

        setContent {
            val hostState = remember {
                SnackbarHostState()
            }
            val message by errorMessage.collectAsState()
            val isBiometricEnrolled by isBiometricEnrolled.collectAsState()
            LaunchedEffect(key1 = message, block = {
                if (message != null) {
                    hostState.showSnackbar(message!!)
                }
            })
            AuthenticationTheme {
                // A surface container using the 'background' color from the theme
                Scaffold(modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = hostState) }) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues = paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                        ) {
                            if (isBiometricEnrolled) {
                                // Prompt appears when user clicks "Log in".
                                // Consider integrating with the keystore to unlock cryptographic operations,
                                // if needed by your app.
                                Button(onClick = { biometricPrompt.authenticate(promptInfo) }) {
                                    Text(text = "Biometric Login")
                                }
                            } else {
                                Button(onClick = {
                                    val enrollIntent =
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                                                putExtra(
                                                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                                    BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                                                )
                                            }
                                        } else {
                                            Intent(Settings.ACTION_SECURITY_SETTINGS)
                                        }
                                    biometricEnrollActivity.launch(enrollIntent)
                                }) {
                                    Text(text = "Setup Biometric")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkBiometric() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d(TAG, "App can authenticate using biometrics.")
                lifecycleScope.launch {
                    isBiometricEnrolled.emit(true)
                }
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.e(TAG, "No biometric features available on this device.")
                lifecycleScope.launch {
                    isBiometricEnrolled.emit(false)
                    errorMessage.emit("No biometric features available on this device.")
                }
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.e(TAG, "Biometric features are currently unavailable.")
                lifecycleScope.launch {
                    isBiometricEnrolled.emit(false)
                    errorMessage.emit("Biometric features are currently unavailable.")
                }
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.e(
                    TAG,
                    "The user hasn't associated " + "any biometric credentials with their account."
                )
                lifecycleScope.launch {
                    isBiometricEnrolled.emit(false)
                    errorMessage.emit("The user hasn't associated " + "any biometric credentials with their account.")
                }
            }

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Log.e(TAG, "BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED")
                lifecycleScope.launch {
                    isBiometricEnrolled.emit(false)
                    errorMessage.emit("BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED")
                }
            }

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Log.e(TAG, "BIOMETRIC_ERROR_UNSUPPORTED")
                lifecycleScope.launch {
                    isBiometricEnrolled.emit(false)
                    errorMessage.emit("BIOMETRIC_ERROR_UNSUPPORTED")
                }
            }

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                Log.e(TAG, "BIOMETRIC_STATUS_UNKNOWN")
                lifecycleScope.launch {
                    isBiometricEnrolled.emit(false)
                    errorMessage.emit("BIOMETRIC_STATUS_UNKNOWN")
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}