package ru.netology.nework.domain.usecase

import javax.inject.Inject
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.repository.NeWorkRepository

class PostUseCase @Inject constructor(private val repository: NeWorkRepository) {
    suspend fun save(post: Post): Post = repository.savePost(post)
}

