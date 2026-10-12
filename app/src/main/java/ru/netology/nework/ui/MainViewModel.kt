package ru.netology.nework.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.data.NeWorkRepository
import ru.netology.nework.data.model.Event
import ru.netology.nework.data.model.Credentials
import ru.netology.nework.data.model.Post
import ru.netology.nework.data.model.Registration
import ru.netology.nework.data.model.User

data class FeedState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

enum class AuthOperation { LOGIN, REGISTER }

sealed interface AuthState {
    data object Idle : AuthState
    data class Loading(val operation: AuthOperation) : AuthState
    data class Success(val operation: AuthOperation) : AuthState
    data class HttpError(val operation: AuthOperation, val code: Int) : AuthState
    data class NetworkError(val operation: AuthOperation) : AuthState
}

@HiltViewModel
class MainViewModel @Inject constructor(private val repository: NeWorkRepository) : ViewModel() {
    private val _auth = MutableStateFlow<AuthState>(AuthState.Idle)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private val _posts = MutableStateFlow(FeedState<Post>())
    val posts: StateFlow<FeedState<Post>> = _posts.asStateFlow()
    private val _events = MutableStateFlow(FeedState<Event>())
    val events: StateFlow<FeedState<Event>> = _events.asStateFlow()
    private val _users = MutableStateFlow(FeedState<User>())
    val users: StateFlow<FeedState<User>> = _users.asStateFlow()

    fun isAuthenticated(): Boolean = repository.currentUser() != null

    fun logout() = repository.logout()

    fun login(login: String, password: String) = viewModelScope.launch {
        _auth.value = AuthState.Loading(AuthOperation.LOGIN)
        runCatching { repository.authenticate(Credentials(login, password)) }
            .onSuccess { response ->
                _auth.value = if (response.isSuccessful && response.body() != null) {
                    AuthState.Success(AuthOperation.LOGIN)
                } else {
                    AuthState.HttpError(AuthOperation.LOGIN, response.code())
                }
            }
            .onFailure { _auth.value = AuthState.NetworkError(AuthOperation.LOGIN) }
    }

    fun register(login: String, name: String, password: String, avatar: String? = null) = viewModelScope.launch {
        _auth.value = AuthState.Loading(AuthOperation.REGISTER)
        val request = Registration(login = login, password = password, name = name, avatar = avatar)
        runCatching { repository.register(request) }
            .onSuccess { response ->
                _auth.value = if (response.isSuccessful && response.body() != null) {
                    AuthState.Success(AuthOperation.REGISTER)
                } else {
                    AuthState.HttpError(AuthOperation.REGISTER, response.code())
                }
            }
            .onFailure { _auth.value = AuthState.NetworkError(AuthOperation.REGISTER) }
    }

    fun clearAuthState() { _auth.value = AuthState.Idle }

    fun loadPosts() = viewModelScope.launch {
        _posts.value = _posts.value.copy(loading = true, error = null)
        runCatching { repository.posts() }
            .onSuccess { _posts.value = FeedState(items = it) }
            .onFailure { _posts.value = FeedState(error = it.message ?: "Не удалось загрузить посты") }
    }

    fun loadEvents() = viewModelScope.launch {
        _events.value = _events.value.copy(loading = true, error = null)
        runCatching { repository.events() }
            .onSuccess { _events.value = FeedState(items = it) }
            .onFailure { _events.value = FeedState(error = it.message ?: "Не удалось загрузить события") }
    }

    fun loadUsers() = viewModelScope.launch {
        _users.value = _users.value.copy(loading = true, error = null)
        runCatching { repository.users() }
            .onSuccess { _users.value = FeedState(items = it) }
            .onFailure { _users.value = FeedState(error = it.message ?: "Не удалось загрузить пользователей") }
    }
}
