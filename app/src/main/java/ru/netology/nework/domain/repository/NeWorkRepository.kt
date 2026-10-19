package ru.netology.nework.domain.repository

import ru.netology.nework.domain.model.AuthenticationResult
import ru.netology.nework.domain.model.AvatarFile
import ru.netology.nework.domain.model.Credentials
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.JobCreateRequest
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User

interface NeWorkRepository {
    suspend fun posts(): List<Post>
    suspend fun events(): List<Event>
    suspend fun post(id: Long): Post
    suspend fun event(id: Long): Event
    suspend fun users(): List<User>
    suspend fun user(id: Long): User
    suspend fun wall(id: Long): List<Post>
    suspend fun jobs(id: Long): List<Job>
    suspend fun createJob(request: JobCreateRequest): Job
    suspend fun deleteJob(id: Long)
    suspend fun likePost(id: Long): Post
    suspend fun unlikePost(id: Long): Post
    suspend fun likeEvent(id: Long): Event
    suspend fun unlikeEvent(id: Long): Event
    suspend fun deletePost(id: Long)
    suspend fun participateEvent(id: Long): Event
    suspend fun leaveEvent(id: Long): Event
    suspend fun deleteEvent(id: Long)
    suspend fun uploadMedia(file: MediaFile): Attachment
    suspend fun savePost(post: Post): Post
    suspend fun saveEvent(event: Event): Event
    suspend fun authenticate(credentials: Credentials): AuthenticationResult
    suspend fun register(login: String, name: String, password: String, avatar: AvatarFile?): AuthenticationResult
    fun logout()
    fun currentUser(): User?
}
