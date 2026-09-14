package com.example.gscan.feature.security.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gscan.feature.security.domain.model.AppLockSettings
import com.example.gscan.feature.security.domain.model.AppLockTimeout
import com.example.gscan.feature.security.domain.model.PinVerificationResult
import com.example.gscan.feature.security.domain.repository.AppLockRepository
import com.example.gscan.feature.security.domain.usecase.ChangeAppLockPinUseCase
import com.example.gscan.feature.security.domain.usecase.DisableAppLockUseCase
import com.example.gscan.feature.security.domain.usecase.EnableAppLockUseCase
import com.example.gscan.feature.security.domain.usecase.UpdateAppLockOptionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SecurityUiState(
    val settings: AppLockSettings = AppLockSettings(),
    val processing: Boolean = false,
    val errorMessage: String? = null,
    val completedOperationVersion: Int = 0,
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    repository: AppLockRepository,
    private val enableAppLock: EnableAppLockUseCase,
    private val changePin: ChangeAppLockPinUseCase,
    private val disableAppLock: DisableAppLockUseCase,
    private val updateOptions: UpdateAppLockOptionsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SecurityUiState(settings = repository.settings.value))
    val uiState: StateFlow<SecurityUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.settings.collect { settings -> _uiState.update { it.copy(settings = settings) } }
        }
    }

    fun enable(pin: String, confirmation: String) {
        if (pin != confirmation) {
            _uiState.update { it.copy(errorMessage = "PIN xác nhận không khớp.") }
            return
        }
        runOperation {
            enableAppLock(pin)
            completeOperation()
        }
    }

    fun changePin(currentPin: String, newPin: String, confirmation: String) {
        if (newPin != confirmation) {
            _uiState.update { it.copy(errorMessage = "PIN xác nhận không khớp.") }
            return
        }
        runOperation {
            when (val result = changePin(currentPin, newPin)) {
                PinVerificationResult.Success -> completeOperation()
                else -> showVerificationError(result)
            }
        }
    }

    fun disable(pin: String) = runOperation {
        when (val result = disableAppLock(pin)) {
            PinVerificationResult.Success -> completeOperation()
            else -> showVerificationError(result)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        runOperation { updateOptions.setBiometricEnabled(enabled) }
    }

    fun setTimeout(timeout: AppLockTimeout) {
        runOperation { updateOptions.setTimeout(timeout) }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun showError(message: String?) {
        if (!message.isNullOrBlank()) _uiState.update { it.copy(errorMessage = message) }
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (_uiState.value.processing) return
        _uiState.update { it.copy(processing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = error.message ?: "PIN không hợp lệ.") }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Không thể cập nhật khóa ứng dụng. Hãy thử lại.") }
            } finally {
                _uiState.update { it.copy(processing = false) }
            }
        }
    }

    private fun completeOperation() {
        _uiState.update {
            it.copy(errorMessage = null, completedOperationVersion = it.completedOperationVersion + 1)
        }
    }

    private fun showVerificationError(result: PinVerificationResult) {
        val message = when (result) {
            PinVerificationResult.Success -> return
            is PinVerificationResult.Invalid ->
                "PIN hiện tại không đúng. Còn ${result.attemptsBeforeDelay} lần trước khi tạm khóa."
            is PinVerificationResult.TemporarilyLocked ->
                "Bạn đã thử quá nhiều lần. Hãy thử lại sau ${result.remainingSeconds} giây."
            PinVerificationResult.Unavailable ->
                "Không thể đọc dữ liệu khóa an toàn. Hãy khởi động lại ứng dụng."
        }
        _uiState.update { it.copy(errorMessage = message) }
    }
}
