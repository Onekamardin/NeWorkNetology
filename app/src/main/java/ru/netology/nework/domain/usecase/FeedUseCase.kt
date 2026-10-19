package ru.netology.nework.domain.usecase

import javax.inject.Inject
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.repository.NeWorkRepository

class FeedUseCase @Inject constructor(private val repository: NeWorkRepository) {
    suspend fun posts(): List<Post> = repository.posts()
    suspend fun events(): List<Event> = repository.events()
    suspend fun post(id: Long): Post = repository.post(id)
    suspend fun event(id: Long): Event = repository.event(id)
    suspend fun users(): List<User> = repository.users()
    suspend fun likePost(id: Long): Post = repository.likePost(id)
    suspend fun unlikePost(id: Long): Post = repository.unlikePost(id)
    suspend fun likeEvent(id: Long): Event = repository.likeEvent(id)
    suspend fun unlikeEvent(id: Long): Event = repository.unlikeEvent(id)
    suspend fun deletePost(id: Long) = repository.deletePost(id)
    suspend fun deleteEvent(id: Long) = repository.deleteEvent(id)
    suspend fun participateEvent(id: Long) = repository.participateEvent(id)
    suspend fun leaveEvent(id: Long) = repository.leaveEvent(id)
}
