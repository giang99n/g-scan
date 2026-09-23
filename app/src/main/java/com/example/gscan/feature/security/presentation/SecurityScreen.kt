package com.example.gscan.feature.security.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gscan.core.designsystem.component.GScanTopAppBar
import com.example.gscan.feature.security.data.AppLockBiometricCrypto
import com.example.gscan.feature.security.data.BiometricCryptoPreparation
import com.example.gscan.feature.security.domain.model.AppLockTimeout

@Composable
fun SecurityScreen(
    onBackClick: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = rememberBiometricAvailability()
    val biometricPrompt = rememberBiometricPrompt(
        activity = context.findFragmentActivity(),
        onSuccess = { result ->
            if (AppLockBiometricCrypto.finishAuthentication(result.cryptoObject)) {
                viewModel.setBiometricEnabled(true)
            } else {
                AppLockBiometricCrypto.deleteKey()
                viewModel.showError("Không thể xác nhận sinh trắc học. Hãy thử lại.")
            }
        },
        onError = viewModel::showError,
    )
    SecurityContent(
        state = state,
        biometricAvailable = biometricAvailable,
        onBackClick = onBackClick,
        onClearError = viewModel::clearError,
        onBiometricChange = { enabled ->
            if (!enabled) {
                viewModel.setBiometricEnabled(false)
            } else if (biometricPrompt != null) {
                when (val preparation = AppLockBiometricCrypto.prepareAuthentication(true)) {
                    is BiometricCryptoPreparation.Ready -> biometricPrompt.authenticate(
                        createBiometricPromptInfo(
                            title = "Bật mở khóa sinh trắc học",
                            subtitle = "Xác thực để liên kết sinh trắc học với AloScan",
                            negativeButtonText = "Hủy",
                        ),
                        preparation.cryptoObject,
                    )
                    is BiometricCryptoPreparation.Unavailable -> viewModel.showError(preparation.message)
                }
            }
        },
        onTimeoutSelected = viewModel::setTimeout,
        onEnable = viewModel::enable,
        onChangePin = viewModel::changePin,
        onDisable = viewModel::disable,
    )
}

@Composable
private fun SecurityContent(
    state: SecurityUiState,
    biometricAvailable: Boolean,
    onBackClick: () -> Unit,
    onClearError: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onTimeoutSelected: (AppLockTimeout) -> Unit,
    onEnable: (String, String) -> Unit,
    onChangePin: (String, String, String) -> Unit,
    onDisable: (String) -> Unit,
) {
    var dialogMode by remember { mutableStateOf<PinDialogMode?>(null) }

    LaunchedEffect(state.completedOperationVersion) {
        if (state.completedOperationVersion > 0) dialogMode = null
    }

    Scaffold(
        topBar = { GScanTopAppBar("Bảo mật tài liệu", onBackClick, !state.processing) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SecurityStatusCard(state.settings.enabled)
            if (!state.settings.enabled) {
                Text(
                    "Khóa AloScan khi mở ứng dụng để người khác không thể xem tài liệu nếu đang cầm thiết bị của bạn.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onClearError(); dialogMode = PinDialogMode.ENABLE },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Bật khóa ứng dụng") }
            } else {
                BiometricOption(
                    enabled = state.settings.biometricEnabled,
                    available = biometricAvailable,
                    processing = state.processing,
                    onChange = onBiometricChange,
                )
                TimeoutOptions(
                    selected = state.settings.lockTimeout,
                    enabled = !state.processing,
                    onSelect = onTimeoutSelected,
                )
                OutlinedButton(
                    onClick = { onClearError(); dialogMode = PinDialogMode.CHANGE },
                    enabled = !state.processing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Đổi PIN") }
                FilledTonalButton(
                    onClick = { onClearError(); dialogMode = PinDialogMode.DISABLE },
                    enabled = !state.processing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Tắt khóa ứng dụng") }
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "App Lock ngăn truy cập qua giao diện và ẩn nội dung khỏi ảnh chụp màn hình/Recent Apps. File tài liệu chưa được mã hóa bởi tính năng này.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    dialogMode?.let { mode ->
        PinActionDialog(
            mode = mode,
            processing = state.processing,
            errorMessage = state.errorMessage,
            onDismiss = { if (!state.processing) { dialogMode = null; onClearError() } },
            onEnable = onEnable,
            onChange = onChangePin,
            onDisable = onDisable,
        )
    }
}

@Composable
private fun SecurityStatusCard(enabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (enabled) Icons.Rounded.Security else Icons.Rounded.Lock, contentDescription = null)
            Column(Modifier.padding(start = 14.dp)) {
                Text(if (enabled) "Khóa ứng dụng đang bật" else "Khóa ứng dụng đang tắt", fontWeight = FontWeight.Bold)
                Text(
                    if (enabled) "AloScan sẽ yêu cầu xác thực sau thời gian đã chọn."
                    else "Tài liệu có thể được mở ngay khi vào ứng dụng.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BiometricOption(
    enabled: Boolean,
    available: Boolean,
    processing: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Fingerprint, contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Mở bằng sinh trắc học", fontWeight = FontWeight.SemiBold)
                Text(
                    if (available) "PIN vẫn luôn có thể dùng để mở khóa."
                    else "Thiết bị chưa có sinh trắc học phù hợp hoặc chưa đăng ký.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled && available,
                onCheckedChange = onChange,
                enabled = available && !processing,
            )
        }
    }
}

@Composable
private fun TimeoutOptions(
    selected: AppLockTimeout,
    enabled: Boolean,
    onSelect: (AppLockTimeout) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tự khóa sau khi rời ứng dụng", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        AppLockTimeout.entries.forEach { timeout ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = timeout == selected, onClick = { onSelect(timeout) }, enabled = enabled)
                Text(timeout.label, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun PinActionDialog(
    mode: PinDialogMode,
    processing: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onEnable: (String, String) -> Unit,
    onChange: (String, String, String) -> Unit,
    onDisable: (String) -> Unit,
) {
    var currentPin by remember(mode) { mutableStateOf("") }
    var newPin by remember(mode) { mutableStateOf("") }
    var confirmation by remember(mode) { mutableStateOf("") }
    val valid = when (mode) {
        PinDialogMode.ENABLE -> newPin.length == PIN_LENGTH && confirmation.length == PIN_LENGTH
        PinDialogMode.CHANGE -> currentPin.length == PIN_LENGTH && newPin.length == PIN_LENGTH && confirmation.length == PIN_LENGTH
        PinDialogMode.DISABLE -> currentPin.length == PIN_LENGTH
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mode.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode != PinDialogMode.ENABLE) PinField("PIN hiện tại", currentPin, !processing) { currentPin = it }
                if (mode != PinDialogMode.DISABLE) {
                    PinField("PIN mới gồm 6 số", newPin, !processing) { newPin = it }
                    PinField("Nhập lại PIN mới", confirmation, !processing) { confirmation = it }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (mode) {
                        PinDialogMode.ENABLE -> onEnable(newPin, confirmation)
                        PinDialogMode.CHANGE -> onChange(currentPin, newPin, confirmation)
                        PinDialogMode.DISABLE -> onDisable(currentPin)
                    }
                },
                enabled = valid && !processing,
            ) {
                if (processing) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                else Text(mode.action)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !processing) { Text("Hủy") } },
    )
}

@Composable
private fun PinField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { character -> character in '0'..'9' }.take(PIN_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

private enum class PinDialogMode(val title: String, val action: String) {
    ENABLE("Tạo PIN khóa ứng dụng", "Bật khóa"),
    CHANGE("Đổi PIN", "Lưu PIN"),
    DISABLE("Tắt khóa ứng dụng?", "Tắt khóa"),
}

private val AppLockTimeout.label: String
    get() = when (this) {
        AppLockTimeout.IMMEDIATELY -> "Ngay lập tức"
        AppLockTimeout.ONE_MINUTE -> "Sau 1 phút"
        AppLockTimeout.FIVE_MINUTES -> "Sau 5 phút"
        AppLockTimeout.FIFTEEN_MINUTES -> "Sau 15 phút"
    }

private const val PIN_LENGTH = 6

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SecurityScreenPreview() {
    com.example.gscan.core.designsystem.theme.GScanTheme(darkTheme = false) {
        SecurityContent(
            state = SecurityUiState(),
            biometricAvailable = true,
            onBackClick = {}, onClearError = {}, onBiometricChange = {},
            onTimeoutSelected = {}, onEnable = { _, _ -> },
            onChangePin = { _, _, _ -> }, onDisable = {},
        )
    }
}
