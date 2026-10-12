package ru.netology.nework.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.NetworkException
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.usecase.FeedUseCase
import ru.netology.nework.domain.usecase.ProfileUseCase

sealed interface FeedActionResult {
    data object PostDeleted : FeedActionResult
    data object EventDeleted : FeedActionResult
    data class Error(val message: String) : FeedActionResult
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedUseCase: FeedUseCase,
    private val profileUseCase: ProfileUseCase,
) : ViewModel() {
    private val _posts = MutableStateFlow(FeedState<Post>())
    val posts: StateFlow<FeedState<Post>> = _posts.asStateFlow()
    private val _events = MutableStateFlow(FeedState<Event>())
    val events: StateFlow<FeedState<Event>> = _events.asStateFlow()
    private val _users = MutableStateFlow(FeedState<User>())
    val users: StateFlow<FeedState<User>> = _users.asStateFlow()
    private val _detailJobs = MutableStateFlow(FeedState<Job>())
    val detailJobs: StateFlow<FeedState<Job>> = _detailJobs.asStateFlow()
    private var detailAuthorId: Long? = null
    private val _selectedPost = MutableStateFlow<Post?>(null)
    val selectedPost: StateFlow<Post?> = _selectedPost.asStateFlow()
    private val _selectedEvent = MutableStateFlow<Event?>(null)
    val selectedEvent: StateFlow<Event?> = _selectedEvent.asStateFlow()
    private val _likeErrors = MutableSharedFlow<String>()
    val likeErrors: SharedFlow<String> = _likeErrors.asSharedFlow()
    private val _messages = MutableSharedFlow<FeedActionResult>()
    val messages: SharedFlow<FeedActionResult> = _messages.asSharedFlow()

    fun selectPost(post: Post) { _selectedPost.value = post; _selectedEvent.value = null }
    fun selectEvent(event: Event) { _selectedEvent.value = event; _selectedPost.value = null }

    fun loadPosts() = viewModelScope.launch {
        _posts.value = _posts.value.copy(loading = true, error = null)
        runCatching { feedUseCase.posts() }
            .onSuccess { _posts.value = FeedState(items = it) }
            .onFailure { _posts.value = FeedState(error = it.message ?: "Не удалось загрузить посты") }
    }

    fun loadEvents() = viewModelScope.launch {
        _events.value = _events.value.copy(loading = true, error = null)
        runCatching { feedUseCase.events() }
            .onSuccess { _events.value = FeedState(items = it) }
            .onFailure { _events.value = FeedState(error = it.message ?: "Не удалось загрузить события") }
    }

    fun loadUsers() = viewModelScope.launch {
        _users.value = _users.value.copy(loading = true, error = null)
        runCatching { feedUseCase.users() }
            .onSuccess { _users.value = FeedState(items = it) }
            .onFailure { _users.value = FeedState(error = it.message ?: "Не удалось загрузить пользователей") }
    }

    fun togglePostLike(post: Post) = viewModelScope.launch {
        runCatching { if (post.likedByMe) feedUseCase.unlikePost(post.id) else feedUseCase.likePost(post.id) }
            .onSuccess { updated ->
                _posts.value = _posts.value.copy(items = _posts.value.items.map { if (it.id == updated.id) updated else it })
            }
            .onFailure { _likeErrors.emit(it.message ?: "Не удалось изменить отметку «Нравится»") }
    }

    fun toggleEventLike(event: Event) = viewModelScope.launch {
        runCatching { if (event.likedByMe) feedUseCase.unlikeEvent(event.id) else feedUseCase.likeEvent(event.id) }
            .onSuccess { updated ->
                _events.value = _events.value.copy(items = _events.value.items.map { if (it.id == updated.id) updated else it })
            }
            .onFailure { _likeErrors.emit(it.message ?: "Не удалось изменить отметку «Нравится»") }
    }

    fun deletePost(post: Post) = viewModelScope.launch {
        runCatching { feedUseCase.deletePost(post.id) }
            .onSuccess {
                _posts.value = _posts.value.copy(items = _posts.value.items.filterNot { it.id == post.id })
                _messages.emit(FeedActionResult.PostDeleted)
            }
            .onFailure { _messages.emit(FeedActionResult.Error(it.message ?: "Не удалось удалить пост")) }
    }

    fun deleteEvent(event: Event) = viewModelScope.launch {
        runCatching { feedUseCase.deleteEvent(event.id) }
            .onSuccess {
                _events.value = _events.value.copy(items = _events.value.items.filterNot { it.id == event.id })
                _messages.emit(FeedActionResult.EventDeleted)
            }
            .onFailure { _messages.emit(FeedActionResult.Error(it.message ?: "Не удалось удалить событие")) }
    }

    fun loadDetailJobs(userId: Long) = viewModelScope.launch {
        detailAuthorId = userId
        if (userId <= 0) { _detailJobs.value = FeedState(); return@launch }
        _detailJobs.value = FeedState(loading = true)
        runCatching { profileUseCase.jobs(userId) }
            .onSuccess { if (detailAuthorId == userId) _detailJobs.value = FeedState(items = it) }
            .onFailure { error ->
                if (detailAuthorId == userId) {
                    val missing = error is NetworkException && error.code == 404
                    _detailJobs.value = if (missing) FeedState() else FeedState(error = "Не удалось загрузить места работы")
                }
            }
    }
}
