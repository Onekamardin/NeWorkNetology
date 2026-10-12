package ru.netology.nework.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.NetworkException
import ru.netology.nework.domain.usecase.PostUseCase

sealed interface CreatePostState {
    data object Idle : CreatePostState
    data object Loading : CreatePostState
    data object Success : CreatePostState
    data class Error(val message: String) : CreatePostState
}

@HiltViewModel
class PostViewModel @Inject constructor(private val postUseCase: PostUseCase) : ViewModel() {
    private val _createPost = MutableStateFlow<CreatePostState>(CreatePostState.Idle)
    val createPost: StateFlow<CreatePostState> = _createPost.asStateFlow()

    fun createPost(content: String, link: String? = null) = viewModelScope.launch {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) { _createPost.value = CreatePostState.Error("Введите текст поста"); return@launch }
        _createPost.value = CreatePostState.Loading
        runCatching { postUseCase.create(cleanContent, link?.trim()?.takeIf(String::isNotBlank)) }
            .onSuccess { _createPost.value = CreatePostState.Success }
            .onFailure { error ->
                val message = if (error is NetworkException) error.message else error.message ?: "Не удалось опубликовать пост"
                _createPost.value = CreatePostState.Error(message)
            }
    }

    fun clearCreatePostState() { _createPost.value = CreatePostState.Idle }
}
