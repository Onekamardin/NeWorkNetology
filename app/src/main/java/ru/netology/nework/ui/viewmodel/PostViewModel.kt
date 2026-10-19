package ru.netology.nework.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.usecase.AuthUseCase
import ru.netology.nework.domain.usecase.MediaUseCase
import ru.netology.nework.domain.usecase.PostUseCase

data class PostDraft(
    val content: String = "",
    val link: String = "",
    val mentionIds: Set<Long> = emptySet(),
    val coords: Coordinates? = null,
    val media: MediaFile? = null,
    val keepAttachment: Boolean = true,
)

sealed interface CreatePostState {
    data object Idle : CreatePostState
    data object Loading : CreatePostState
    data class Success(val post: Post) : CreatePostState
    data class Error(val message: String) : CreatePostState
}

@HiltViewModel
class PostViewModel @Inject constructor(
    private val postUseCase: PostUseCase,
    private val mediaUseCase: MediaUseCase,
    private val authUseCase: AuthUseCase,
) : ViewModel() {
    private val _createPost = MutableStateFlow<CreatePostState>(CreatePostState.Idle)
    val createPost = _createPost.asStateFlow()
    private val _editingPost = MutableStateFlow<Post?>(null)
    val editingPost = _editingPost.asStateFlow()
    var draft = PostDraft()
        private set
    private var saveTask: CoroutineJob? = null

    fun startEditing(post: Post) {
        clearEditing()
        _editingPost.value = post
        _createPost.value = CreatePostState.Idle
        draft = PostDraft(post.content, post.link.orEmpty(), post.mentionIds.toSet(), post.coords)
    }

    fun updateDraft(value: PostDraft) {
        draft = value
    }

    fun clearEditing() {
        saveTask?.cancel()
        saveTask = null
        _createPost.value = CreatePostState.Idle
        _editingPost.value = null
        draft = PostDraft()
    }

    fun savePost(
        content: String,
        link: String? = null,
        mentionIds: List<Long> = emptyList(),
        coords: Coordinates? = null,
        mediaFile: MediaFile? = null,
        keepExistingAttachment: Boolean = true,
    ) {
        if (_createPost.value == CreatePostState.Loading) return
        val user = authUseCase.currentUser()
        val existing = _editingPost.value
        if (!authUseCase.isAuthenticated() || user == null) {
            _createPost.value = CreatePostState.Error("Войдите в аккаунт")
            return
        }
        if (existing != null && existing.authorId != user.id) {
            _createPost.value = CreatePostState.Error("Можно изменять только свои посты")
            return
        }
        if (content.isBlank()) {
            _createPost.value = CreatePostState.Error("Введите текст поста")
            return
        }
        _createPost.value = CreatePostState.Loading
        saveTask = viewModelScope.launch {
            try {
                val attachment = mediaFile?.let { mediaUseCase.upload(it) }
                    ?: existing?.attachment?.takeIf { keepExistingAttachment }
                currentCoroutineContext().ensureActive()
                check(authUseCase.currentUser()?.id == user.id) { "Войдите в аккаунт ещё раз" }
                val initial = existing ?: Post(
                    id = 0, authorId = user.id, author = user.name?.takeIf(String::isNotBlank) ?: user.login.orEmpty(),
                    authorAvatar = user.avatar, published = Instant.now().toString(),
                )
                val post = initial.copy(
                    content = content.trim(),
                    link = link?.trim()?.takeIf(String::isNotBlank),
                    mentionIds = mentionIds, coords = coords, attachment = attachment,
                )
                val saved = postUseCase.save(post)
                currentCoroutineContext().ensureActive()
                check(authUseCase.currentUser()?.id == user.id) { "Войдите в аккаунт ещё раз" }
                check(existing == null || saved.id == existing.id) { "Сервер вернул другой ID вместо обновления поста" }
                _createPost.value = CreatePostState.Success(saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _createPost.value = CreatePostState.Error(error.message ?: "Не удалось сохранить пост")
            }
        }
    }

    fun clearCreatePostState() {
        _createPost.value = CreatePostState.Idle
    }
}

