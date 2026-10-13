package ru.netology.nework.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.AuthenticationResult
import ru.netology.nework.domain.model.AvatarFile
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.usecase.AuthUseCase

enum class AuthOperation { LOGIN, REGISTER }

sealed interface AuthState {
    data object Idle : AuthState
    data class Loading(val operation: AuthOperation) : AuthState
    data class Success(val operation: AuthOperation) : AuthState
    data class HttpError(val operation: AuthOperation, val code: Int) : AuthState
    data class NetworkError(val operation: AuthOperation, val detail: String) : AuthState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
) : ViewModel() {
    private val _auth = MutableStateFlow<AuthState>(AuthState.Idle)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    fun isAuthenticated(): Boolean = authUseCase.isAuthenticated()
    fun currentUser(): User? = authUseCase.currentUser()
    fun logout() = authUseCase.logout()

    fun login(login: String, password: String) = viewModelScope.launch {
        authenticate(AuthOperation.LOGIN) { authUseCase.login(login, password) }
    }

    fun register(login: String, name: String, password: String, avatar: AvatarFile?) = viewModelScope.launch {
        authenticate(AuthOperation.REGISTER) { authUseCase.register(login, name, password, avatar) }
    }

    private suspend fun authenticate(
        operation: AuthOperation,
        request: suspend () -> AuthenticationResult,
    ) {
        _auth.value = AuthState.Loading(operation)
        runCatching { request() }
            .onSuccess { result ->
                _auth.value = when (result) {
                    is AuthenticationResult.Success -> AuthState.Success(operation)
                    is AuthenticationResult.HttpError -> AuthState.HttpError(operation, result.code)
                }
            }
            .onFailure { error ->
                Log.e("NeWorkAuth", "Authentication request failed", error)
                _auth.value = AuthState.NetworkError(
                    operation,
                    error.localizedMessage ?: error.javaClass.simpleName,
                )
            }
    }

    fun clearAuthState() {
        _auth.value = AuthState.Idle
    }
}
