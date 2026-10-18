package ru.netology.nework.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.NetworkException
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.usecase.MediaUseCase
import ru.netology.nework.domain.usecase.PostUseCase

sealed interface CreatePostState {
    data object Idle : CreatePostState
    data object Loading : CreatePostState
    data object Success : CreatePostState
    data class Error(val message: String) : CreatePostState
}

@HiltViewModel
class PostViewModel @Inject constructor(
    private val postUseCase: PostUseCase,
    private val mediaUseCase: MediaUseCase,
) : ViewModel() {
    private val _createPost = MutableStateFlow<CreatePostState>(CreatePostState.Idle)
    val createPost: StateFlow<CreatePostState> = _createPost.asStateFlow()
    private val _editingPost = MutableStateFlow<Post?>(null)
    val editingPost: StateFlow<Post?> = _editingPost.asStateFlow()

    fun startEditing(post: Post) {
        _editingPost.value = post
        _createPost.value = CreatePostState.Idle
    }

    fun clearEditing() {
        _editingPost.value = null
    }

    fun savePost(
        content: String,
        link: String? = null,
        mentionIds: List<Long> = emptyList(),
        coords: Coordinates? = null,
        mediaFile: MediaFile? = null,
        keepExistingAttachment: Boolean = true,
    ) = viewModelScope.launch {
        if (_createPost.value == CreatePostState.Loading) return@launch
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) { _createPost.value = CreatePostState.Error("Введите текст поста"); return@launch }
        _createPost.value = CreatePostState.Loading
        runCatching {
            val attachment: Attachment? = mediaFile?.let { mediaUseCase.upload(it) }
                ?: _editingPost.value?.attachment?.takeIf { keepExistingAttachment }
            val existing = _editingPost.value
            val post = existing?.copy(
                content = cleanContent,
                link = link?.trim()?.takeIf(String::isNotBlank),
                mentionIds = mentionIds,
                coords = coords,
                attachment = attachment,
            ) ?: Post(
                id = 0,
                content = cleanContent,
                link = link?.trim()?.takeIf(String::isNotBlank),
                mentionIds = mentionIds,
                coords = coords,
                attachment = attachment,
            )
            postUseCase.save(post)
        }
            .onSuccess { _createPost.value = CreatePostState.Success }
            .onFailure { error ->
                val message = if (error is NetworkException) error.message else error.message ?: "Не удалось опубликовать пост"
                _createPost.value = CreatePostState.Error(message)
            }
    }

    fun clearCreatePostState() { _createPost.value = CreatePostState.Idle }
}
