package com.example.gscan.feature.security.presentation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.security.domain.model.AppLockSettings
import com.example.gscan.feature.security.domain.model.PinVerificationResult
import com.example.gscan.feature.security.domain.repository.AppLockRepository
import com.example.gscan.feature.security.domain.usecase.VerifyAppLockPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppLockUiState(
    val settings: AppLockSettings = AppLockSettings(),
    val locked: Boolean = false,
    val verifying: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val repository: AppLockRepository,
    private val verifyPin: VerifyAppLockPinUseCase,
) : ViewModel() {
    private val initialSettings = repository.settings.value
    private val _uiState = MutableStateFlow(
        AppLockUiState(settings = initialSettings, locked = initialSettings.enabled),
    )
    private var backgroundedAtElapsedMillis: Long? = null

    val uiState: StateFlow<AppLockUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        settings = settings,
                        locked = if (settings.enabled) state.locked else false,
                        errorMessage = if (settings.enabled) state.errorMessage else null,
                    )
                }
            }
        }
    }

    fun onBackgrounded() {
        if (_uiState.value.settings.enabled) {
            backgroundedAtElapsedMillis = SystemClock.elapsedRealtime()
        }
    }

    fun onForegrounded() {
        val backgroundedAt = backgroundedAtElapsedMillis ?: return
        backgroundedAtElapsedMillis = null
        val state = _uiState.value
        if (state.settings.enabled &&
            SystemClock.elapsedRealtime() - backgroundedAt >= state.settings.lockTimeout.durationMillis
        ) {
            _uiState.update { it.copy(locked = true, errorMessage = null) }
        }
    }

    fun unlockWithPin(pin: String) {
        if (_uiState.value.verifying) return
        _uiState.update { it.copy(verifying = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                when (val result = verifyPin(pin)) {
                    PinVerificationResult.Success -> _uiState.update {
                        it.copy(locked = false, verifying = false, errorMessage = null)
                    }
                    is PinVerificationResult.Invalid -> _uiState.update {
                        it.copy(
                            verifying = false,
                            errorMessage = "PIN không đúng. Còn ${result.attemptsBeforeDelay} lần trước khi tạm khóa.",
                        )
                    }
                    is PinVerificationResult.TemporarilyLocked -> _uiState.update {
                        it.copy(
                            verifying = false,
                            errorMessage = "Bạn đã thử quá nhiều lần. Hãy thử lại sau ${result.remainingSeconds} giây.",
                        )
                    }
                    PinVerificationResult.Unavailable -> _uiState.update {
                        it.copy(
                            verifying = false,
                            errorMessage = "Không thể đọc dữ liệu khóa an toàn. Hãy khởi động lại ứng dụng.",
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(verifying = false, errorMessage = "Không thể kiểm tra PIN. Hãy thử lại.")
                }
            }
        }
    }

    fun unlockWithBiometric() {
        if (!_uiState.value.settings.biometricEnabled) return
        _uiState.update { it.copy(locked = false, errorMessage = null) }
    }

    fun onBiometricError(message: String?) {
        if (!message.isNullOrBlank()) _uiState.update { it.copy(errorMessage = message) }
    }

    fun onBiometricUnavailable(message: String) {
        viewModelScope.launch {
            runCatching { repository.setBiometricEnabled(false) }
            _uiState.update { it.copy(errorMessage = message) }
        }
    }
}
