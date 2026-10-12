package ru.netology.nework.data

import ru.netology.nework.data.api.NeWorkApi
import ru.netology.nework.data.model.Event
import ru.netology.nework.data.model.Credentials
import ru.netology.nework.data.model.Post
import ru.netology.nework.data.model.Registration
import ru.netology.nework.data.model.User
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeWorkRepository @Inject constructor(
    private val api: NeWorkApi,
    private val authStore: AuthStore,
) {
    suspend fun posts(): List<Post> = api.getPosts()
    suspend fun events(): List<Event> = api.getEvents()
    suspend fun users(): List<User> = api.getUsers()
    suspend fun createPost(content: String): Post = api.createPost(Post(id = 0, content = content))

    suspend fun authenticate(credentials: Credentials): Response<User> =
        api.authenticate(credentials).also(::saveAuthenticatedUser)

    suspend fun register(registration: Registration): Response<User> =
        api.register(registration).also(::saveAuthenticatedUser)

    fun logout() = authStore.clear()

    fun currentUser(): User? = authStore.currentUser()

    private fun saveAuthenticatedUser(response: Response<User>) {
        if (!response.isSuccessful) return
        val user = response.body() ?: return
        val tokenFromHeader = response.headers()["Authorization"]
        authStore.save(if (user.token == null && tokenFromHeader != null) user.copy(token = tokenFromHeader) else user)
    }
}
