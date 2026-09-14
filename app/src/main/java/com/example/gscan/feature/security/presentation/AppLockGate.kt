package com.example.gscan.feature.security.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gscan.feature.security.data.AppLockBiometricCrypto
import com.example.gscan.feature.security.data.BiometricCryptoPreparation

@Composable
fun AppLockGate(
    viewModel: AppLockViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForegrounded()
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) {
                    viewModel.onBackgrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity, state.settings.enabled) {
        if (state.settings.enabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    if (state.locked) {
        AppUnlockScreen(
            state = state,
            onUnlockWithPin = viewModel::unlockWithPin,
            onBiometricSuccess = viewModel::unlockWithBiometric,
            onBiometricError = viewModel::onBiometricError,
            onBiometricUnavailable = viewModel::onBiometricUnavailable,
        )
    } else {
        content()
    }
}

@Composable
private fun AppUnlockScreen(
    state: AppLockUiState,
    onUnlockWithPin: (String) -> Unit,
    onBiometricSuccess: () -> Unit,
    onBiometricError: (String?) -> Unit,
    onBiometricUnavailable: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val biometricAvailable = rememberBiometricAvailability()
    val biometricPrompt = rememberBiometricPrompt(
        activity = activity,
        onSuccess = { result ->
            if (AppLockBiometricCrypto.finishAuthentication(result.cryptoObject)) {
                onBiometricSuccess()
            } else {
                AppLockBiometricCrypto.deleteKey()
                onBiometricUnavailable("Không thể xác nhận sinh trắc học. Hãy dùng PIN và bật lại.")
            }
        },
        onError = onBiometricError,
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(20.dp))
                Text("AloScan đang được khóa", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Nhập PIN để tiếp tục xem tài liệu của bạn.",
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { character -> character in '0'..'9' }.take(PIN_LENGTH) },
                    label = { Text("PIN 6 số") },
                    singleLine = true,
                    enabled = !state.verifying,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.errorMessage?.let {
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { onUnlockWithPin(pin) },
                    enabled = pin.length == PIN_LENGTH && !state.verifying,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp),
                ) {
                    if (state.verifying) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Mở khóa")
                }
                if (state.settings.biometricEnabled && biometricAvailable && biometricPrompt != null) {
                    FilledTonalButton(
                        onClick = {
                            when (val preparation = AppLockBiometricCrypto.prepareAuthentication(false)) {
                                is BiometricCryptoPreparation.Ready -> biometricPrompt.authenticate(
                                    createBiometricPromptInfo(
                                        title = "Mở khóa AloScan",
                                        subtitle = "Xác thực để xem tài liệu",
                                    ),
                                    preparation.cryptoObject,
                                )
                                is BiometricCryptoPreparation.Unavailable -> {
                                    if (preparation.disableBiometric) {
                                        onBiometricUnavailable(preparation.message)
                                    } else {
                                        onBiometricError(preparation.message)
                                    }
                                }
                            }
                        },
                        enabled = !state.verifying,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Dùng sinh trắc học")
                    }
                }
            }
        }
    }
}

internal fun Context.canUseBiometric(): Boolean =
    BiometricManager.from(this).canAuthenticate(BIOMETRIC_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

@Composable
internal fun rememberBiometricAvailability(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var available by remember(context) { mutableStateOf(context.canUseBiometric()) }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) available = context.canUseBiometric()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return available
}

@Composable
internal fun rememberBiometricPrompt(
    activity: FragmentActivity?,
    onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    onError: (String?) -> Unit,
): BiometricPrompt? {
    val currentSuccess by rememberUpdatedState(onSuccess)
    val currentError by rememberUpdatedState(onError)
    return remember(activity) {
        activity ?: return@remember null
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    currentSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        currentError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    currentError("Không nhận diện được. Bạn có thể thử lại hoặc dùng PIN.")
                }
            },
        )
    }
}

internal fun createBiometricPromptInfo(
    title: String,
    subtitle: String,
    negativeButtonText: String = "Dùng PIN",
): BiometricPrompt.PromptInfo =
    BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
        .setNegativeButtonText(negativeButtonText)
        .build()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun Context.findFragmentActivity(): FragmentActivity? = findActivity() as? FragmentActivity

private const val PIN_LENGTH = 6
private const val BIOMETRIC_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
