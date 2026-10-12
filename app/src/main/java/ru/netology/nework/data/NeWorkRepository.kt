package ru.netology.nework.data

import ru.netology.nework.data.api.NeWorkApi
import ru.netology.nework.data.model.Event
import ru.netology.nework.data.model.Post
import ru.netology.nework.data.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeWorkRepository @Inject constructor(private val api: NeWorkApi) {
    suspend fun posts(): List<Post> = api.getPosts()
    suspend fun events(): List<Event> = api.getEvents()
    suspend fun users(): List<User> = api.getUsers()
    suspend fun createPost(content: String): Post = api.createPost(Post(id = 0, content = content))
}
