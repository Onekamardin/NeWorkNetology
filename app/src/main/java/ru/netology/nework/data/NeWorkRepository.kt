package ru.netology.nework.data

import ru.netology.nework.data.api.NeWorkApi
import ru.netology.nework.data.model.Event
import ru.netology.nework.data.model.Credentials
import ru.netology.nework.data.model.Post
import ru.netology.nework.data.model.User
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
        api.authenticate(credentials).also { response ->
            saveAuthenticatedUser(response, login = credentials.login)
        }

    suspend fun register(
        login: String,
        name: String,
        password: String,
        avatar: MultipartBody.Part?,
    ): Response<User> {
        val textType = "text/plain".toMediaType()
        return api.register(
            login = login.toRequestBody(textType),
            password = password.toRequestBody(textType),
            name = name.toRequestBody(textType),
            file = avatar,
        ).also { response ->
            saveAuthenticatedUser(response, login = login, name = name)
        }
    }

    fun logout() = authStore.clear()

    fun currentUser(): User? = authStore.currentUser()

    private fun saveAuthenticatedUser(response: Response<User>, login: String = "", name: String = "") {
        if (!response.isSuccessful) return
        val user = response.body() ?: return
        val tokenFromHeader = response.headers()["Authorization"]
        authStore.save(
            user.copy(
                login = user.login?.takeIf(String::isNotBlank) ?: login,
                name = user.name?.takeIf(String::isNotBlank) ?: name,
                token = user.token ?: tokenFromHeader,
            ),
        )
    }
}
