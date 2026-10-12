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
import ru.netology.nework.data.model.Post
import ru.netology.nework.data.model.User

data class FeedState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(private val repository: NeWorkRepository) : ViewModel() {
    private val _posts = MutableStateFlow(FeedState<Post>())
    val posts: StateFlow<FeedState<Post>> = _posts.asStateFlow()
    private val _events = MutableStateFlow(FeedState<Event>())
    val events: StateFlow<FeedState<Event>> = _events.asStateFlow()
    private val _users = MutableStateFlow(FeedState<User>())
    val users: StateFlow<FeedState<User>> = _users.asStateFlow()

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
