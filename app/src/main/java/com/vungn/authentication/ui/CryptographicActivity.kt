package com.vungn.authentication.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.vungn.authentication.ui.theme.AuthenticationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CryptographicActivity : AppCompatActivity() {
    private val isAuthenticated = MutableStateFlow(false)
    private val encryptedInfo = MutableStateFlow<ByteArray?>(null)
    private val authenticateActivityForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                lifecycleScope.launch {
                    isAuthenticated.value = true
                }
                generateSecretKey(
                    KeyGenParameterSpec.Builder(
                        KEY_NAME, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    ).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setUserAuthenticationRequired(true).also {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                it.setUserAuthenticationParameters(
                                    VALIDITY_DURATION_SECONDS,
                                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                                )
                            }
                        }.build()
                )
            } else {
                doAuth()
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            isAuthenticated.collect {
                if (!it) {
                    doAuth()
                }
            }
        }
        setContent {
            val isAuthenticated by isAuthenticated.collectAsState()
            val encryptedInfo by encryptedInfo.collectAsState()
            var text by remember { mutableStateOf("") }
            if (isAuthenticated) {
                AuthenticationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "Hello World!")
                            TextField(value = text, onValueChange = { text = it })
                            Button(onClick = { encryptSecretInformation(text) }) {
                                Text(text = "Encrypt")
                            }
                            if (encryptedInfo != null) {
                                Text(text = "Encrypted information: ${encryptedInfo.contentToString()}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun doAuth() {
        val intent = Intent(this, AuthenticationActivity::class.java)
        authenticateActivityForResult.launch(intent)
    }

    private fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")

        // Before the keystore can be accessed, it must be loaded.
        keyStore.load(null)
        return keyStore.getKey(KEY_NAME, null) as SecretKey
    }

    private fun getCipher(): Cipher {
        return Cipher.getInstance(
            KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7
        )
    }

    private fun encryptSecretInformation(text: String) {
        // Exceptions are unhandled for getCipher() and getSecretKey().
        val cipher = getCipher()
        val secretKey = getSecretKey()
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            lifecycleScope.launch {
                encryptedInfo.emit(
                    cipher.doFinal(
                        text.toByteArray(Charset.defaultCharset())
                    )
                )
            }
            Log.d(
                TAG, "Encrypted information: " + encryptedInfo.value.contentToString()
            )
        } catch (e: InvalidKeyException) {
            Log.e(TAG, "Key is invalid.", e)
            lifecycleScope.launch {
                isAuthenticated.emit(false)
            }
        } catch (e: UserNotAuthenticatedException) {
            Log.d(TAG, "The key's validity timed out.")
            lifecycleScope.launch {
                isAuthenticated.emit(false)
            }
        }
    }

    companion object {
        private const val TAG = "CryptographicActivity"
        private const val KEY_NAME = "com.vungn.authentication.ui.CryptographicActivity"
        private const val VALIDITY_DURATION_SECONDS = 5
    }
}