package com.ruda.survey.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruda.survey.domain.model.AuthState
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: SurveyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AuthState>>(UiState.Empty)
    val uiState: StateFlow<UiState<AuthState>> = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = UiState.Error("VALIDATION_ERROR", "Username and password are required")
            return
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.login(username, password)
            _uiState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("LOGIN_FAILED", it.message ?: "Login failed") }
            )
        }
    }

    fun checkExistingSession() {
        if (repository.isLoggedIn()) {
            _uiState.value = UiState.Success(AuthState(isLoggedIn = true))
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = UiState.Empty
        }
    }
}
