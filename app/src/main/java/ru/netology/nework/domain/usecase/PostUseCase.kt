package ru.netology.nework.domain.usecase

import javax.inject.Inject
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.repository.NeWorkRepository

class PostUseCase @Inject constructor(private val repository: NeWorkRepository) {
    suspend fun save(post: Post): Post = repository.savePost(post)

    suspend fun create(
        content: String,
        link: String? = null,
        mentionIds: List<Long> = emptyList(),
        coords: Coordinates? = null,
        attachment: Attachment? = null,
    ): Post = repository.createPost(content, link, mentionIds, coords, attachment)
}
