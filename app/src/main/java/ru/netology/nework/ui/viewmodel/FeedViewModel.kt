package ru.netology.nework.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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
    private val _participationLoading = MutableStateFlow<Long?>(null)
    val participationLoading: StateFlow<Long?> = _participationLoading.asStateFlow()
    private val _participationMessages = MutableSharedFlow<String>()
    val participationMessages: SharedFlow<String> = _participationMessages.asSharedFlow()
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading = _detailLoading.asStateFlow()
    private val _detailError = MutableStateFlow<String?>(null)
    val detailError = _detailError.asStateFlow()
    private var detailLoad: CoroutineJob? = null
    private var postsLoad: CoroutineJob? = null
    private var eventsLoad: CoroutineJob? = null
    private var usersLoad: CoroutineJob? = null

    fun updatePost(post: Post) {
        val items = _posts.value.items
        _posts.value = _posts.value.copy(items = if (items.any { it.id == post.id }) items.map { if (it.id == post.id) post else it } else listOf(post) + items)
        if (_selectedPost.value?.id == post.id) _selectedPost.value = post
    }

    fun updateEvent(event: Event) {
        val items = _events.value.items
        _events.value = _events.value.copy(items = if (items.any { it.id == event.id }) items.map { if (it.id == event.id) event else it } else listOf(event) + items)
        if (_selectedEvent.value?.id == event.id) _selectedEvent.value = event
    }

    fun loadPost(id: Long) {
        detailLoad?.cancel()
        _detailError.value = null
        _detailLoading.value = true
        detailLoad = viewModelScope.launch {
            try {
                val post = feedUseCase.post(id)
                _selectedPost.value = post
                _selectedEvent.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _detailError.value = error.message ?: "Не удалось загрузить пост"
            } finally {
                if (isActive) _detailLoading.value = false
            }
        }
    }

    fun loadEvent(id: Long) {
        detailLoad?.cancel()
        _detailError.value = null
        _detailLoading.value = true
        detailLoad = viewModelScope.launch {
            try {
                val event = feedUseCase.event(id)
                _selectedEvent.value = event
                _selectedPost.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _detailError.value = error.message ?: "Не удалось загрузить событие"
            } finally {
                if (isActive) _detailLoading.value = false
            }
        }
    }

    fun selectPost(post: Post) {
        detailLoad?.cancel()
        _detailLoading.value = false
        _detailError.value = null
        _selectedPost.value = post
        _selectedEvent.value = null
    }

    fun selectEvent(event: Event) {
        detailLoad?.cancel()
        _detailLoading.value = false
        _detailError.value = null
        _selectedEvent.value = event
        _selectedPost.value = null
    }

    fun loadPosts() {
        postsLoad?.cancel()
        postsLoad = viewModelScope.launch {
            _posts.value = _posts.value.copy(loading = true, error = null)
            runCatching { feedUseCase.posts() }
                .onSuccess { _posts.value = FeedState(items = it) }
                .onFailure {
                    if (it is CancellationException) throw it
                    _posts.value = _posts.value.copy(loading = false, error = it.message ?: "Не удалось загрузить посты")
                }
        }
    }

    fun loadEvents() {
        eventsLoad?.cancel()
        eventsLoad = viewModelScope.launch {
            _events.value = _events.value.copy(loading = true, error = null)
            runCatching { feedUseCase.events() }
                .onSuccess { _events.value = FeedState(items = it) }
                .onFailure {
                    if (it is CancellationException) throw it
                    _events.value = _events.value.copy(loading = false, error = it.message ?: "Не удалось загрузить события")
                }
        }
    }

    fun loadUsers() {
        usersLoad?.cancel()
        usersLoad = viewModelScope.launch {
            _users.value = _users.value.copy(loading = true, error = null)
            runCatching { feedUseCase.users() }
                .onSuccess { _users.value = FeedState(items = it) }
                .onFailure {
                    if (it is CancellationException) throw it
                    _users.value = _users.value.copy(loading = false, error = it.message ?: "Не удалось загрузить пользователей")
                }
        }
    }

    fun togglePostLike(post: Post) = viewModelScope.launch {
        runCatching { if (post.likedByMe) feedUseCase.unlikePost(post.id) else feedUseCase.likePost(post.id) }
            .onSuccess { updated ->
                updatePost(updated)
            }
            .onFailure {
                if (it is CancellationException) throw it
                _likeErrors.emit(it.message ?: "Не удалось изменить отметку «Нравится»")
            }
    }

    fun toggleEventLike(event: Event) = viewModelScope.launch {
        runCatching { if (event.likedByMe) feedUseCase.unlikeEvent(event.id) else feedUseCase.likeEvent(event.id) }
            .onSuccess { updated ->
                updateEvent(updated)
            }
            .onFailure {
                if (it is CancellationException) throw it
                _likeErrors.emit(it.message ?: "Не удалось изменить отметку «Нравится»")
            }
    }

    fun toggleEventParticipation(event: Event, userId: Long) = viewModelScope.launch {
        if (_participationLoading.value == event.id) return@launch
        if (userId <= 0) {
            _participationMessages.emit("Не удалось определить пользователя. Войдите в аккаунт ещё раз.")
            return@launch
        }
        val wasParticipant = userId in event.participantsIds || event.participatedByMe
        _participationLoading.value = event.id
        runCatching {
            if (wasParticipant) feedUseCase.leaveEvent(event.id) else feedUseCase.participateEvent(event.id)
        }.onSuccess { updated ->
            updateEvent(updated)
            _participationMessages.emit(
                if (wasParticipant) "Вы больше не участвуете в событии" else "Вы записались на событие",
            )
        }.onFailure {
            if (it is CancellationException) throw it
            _participationMessages.emit(it.message ?: "Не удалось изменить участие в событии")
        }
        _participationLoading.value = null
    }

    fun deletePost(post: Post) = viewModelScope.launch {
        runCatching { feedUseCase.deletePost(post.id) }
            .onSuccess {
                _posts.value = _posts.value.copy(items = _posts.value.items.filterNot { it.id == post.id })
                _messages.emit(FeedActionResult.PostDeleted)
            }
            .onFailure {
                if (it is CancellationException) throw it
                _messages.emit(FeedActionResult.Error(it.message ?: "Не удалось удалить пост"))
            }
    }

    fun deleteEvent(event: Event) = viewModelScope.launch {
        runCatching { feedUseCase.deleteEvent(event.id) }
            .onSuccess {
                _events.value = _events.value.copy(items = _events.value.items.filterNot { it.id == event.id })
                _messages.emit(FeedActionResult.EventDeleted)
            }
            .onFailure {
                if (it is CancellationException) throw it
                _messages.emit(FeedActionResult.Error(it.message ?: "Не удалось удалить событие"))
            }
    }

    fun loadDetailJobs(userId: Long) = viewModelScope.launch {
        detailAuthorId = userId
        if (userId <= 0) { _detailJobs.value = FeedState(); return@launch }
        _detailJobs.value = FeedState(loading = true)
        runCatching { profileUseCase.jobs(userId) }
            .onSuccess { if (detailAuthorId == userId) _detailJobs.value = FeedState(items = it) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                if (detailAuthorId == userId) {
                    val missing = error is NetworkException && error.code == 404
                    _detailJobs.value = if (missing) FeedState() else FeedState(error = "Не удалось загрузить места работы")
                }
            }
    }
}
