package ru.netology.nework.data.repository

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response
import ru.netology.nework.data.local.AuthStore
import ru.netology.nework.data.api.NeWorkApi
import ru.netology.nework.data.api.dto.toDomain
import ru.netology.nework.data.api.dto.toDto
import ru.netology.nework.domain.model.AuthenticationResult
import ru.netology.nework.domain.model.AvatarFile
import ru.netology.nework.domain.model.Credentials
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.EventCreateRequest
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.JobCreateRequest
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.model.NetworkException
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.repository.NeWorkRepository

@Singleton
class NeWorkRepositoryImpl @Inject constructor(
    private val api: NeWorkApi,
    private val authStore: AuthStore,
) : NeWorkRepository {
    override suspend fun posts(): List<Post> = safeRequest { api.getPosts() }.map { it.toDomain() }
    override suspend fun events(): List<Event> = safeRequest { api.getEvents() }.map { it.toDomain() }
    override suspend fun users(): List<User> = safeRequest { api.getUsers() }.map { it.toDomain() }
    override suspend fun user(id: Long): User = safeRequest { api.getUser(id) }.toDomain()
    override suspend fun wall(id: Long): List<Post> = safeRequest { api.getWall(id) }.map { it.toDomain() }
    override suspend fun jobs(id: Long): List<Job> = safeRequest { api.getJobs(id) }.map { it.toDomain() }
    override suspend fun createJob(request: JobCreateRequest): Job = safeRequest { api.createJob(request.toDto()) }.toDomain()

    override suspend fun deleteJob(id: Long) {
        val response = safeRequest { api.deleteJob(id) }
        checkResponse(response)
    }

    override suspend fun likePost(id: Long): Post = safeRequest { api.likePost(id) }.toDomain()
    override suspend fun unlikePost(id: Long): Post = safeRequest { api.unlikePost(id) }.toDomain()
    override suspend fun likeEvent(id: Long): Event = safeRequest { api.likeEvent(id) }.toDomain()
    override suspend fun unlikeEvent(id: Long): Event = safeRequest { api.unlikeEvent(id) }.toDomain()
    override suspend fun deletePost(id: Long) {
        checkResponse(safeRequest { api.deletePost(id) })
    }
    override suspend fun deleteEvent(id: Long) {
        checkResponse(safeRequest { api.deleteEvent(id) })
    }
    override suspend fun uploadMedia(file: MediaFile): Attachment {
        val body = file.bytes.toRequestBody(file.mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("file", file.fileName, body)
        val response = safeRequest { api.uploadMedia(part) }
        val type = when {
            file.mimeType.startsWith("image/", ignoreCase = true) -> "IMAGE"
            file.mimeType.startsWith("video/", ignoreCase = true) -> "VIDEO"
            else -> "AUDIO"
        }
        return Attachment(response.url, type)
    }

    override suspend fun createPost(
        content: String,
        link: String?,
        mentionIds: List<Long>,
        coords: Coordinates?,
        attachment: Attachment?,
    ): Post = safeRequest {
        api.createPost(
            Post(
                id = 0,
                content = content,
                link = link,
                mentionIds = mentionIds,
                coords = coords,
                attachment = attachment,
            ).toDto(),
        )
    }.toDomain()
    override suspend fun createEvent(request: EventCreateRequest): Event = safeRequest { api.createEvent(request.toDto()) }.toDomain()

    override suspend fun authenticate(credentials: Credentials): AuthenticationResult {
        val response = safeRequest { api.authenticate(credentials.toDto()) }
        if (!response.isSuccessful) return AuthenticationResult.HttpError(response.code())
        val user = response.body()?.toDomain() ?: return AuthenticationResult.HttpError(response.code())
        saveUser(user, credentials.login, responseToken = response.headers()["Authorization"])
        return AuthenticationResult.Success(authStore.currentUser() ?: user)
    }

    override suspend fun register(
        login: String,
        name: String,
        password: String,
        avatar: AvatarFile?,
    ): AuthenticationResult {
        val textType = "text/plain".toMediaType()
        val avatarPart = avatar?.let {
            val body = it.bytes.toRequestBody(it.mimeType.toMediaType())
            MultipartBody.Part.createFormData("file", it.fileName, body)
        }
        val response = safeRequest {
            api.register(
                login = login.toRequestBody(textType),
                password = password.toRequestBody(textType),
                name = name.toRequestBody(textType),
                file = avatarPart,
            )
        }
        if (!response.isSuccessful) return AuthenticationResult.HttpError(response.code())
        val user = response.body()?.toDomain() ?: return AuthenticationResult.HttpError(response.code())
        saveUser(user, login, name, response.headers()["Authorization"])
        return AuthenticationResult.Success(authStore.currentUser() ?: user)
    }

    override fun logout() = authStore.clear()
    override fun currentUser(): User? = authStore.currentUser()

    private fun saveUser(user: User, login: String, name: String = "", responseToken: String? = null) {
        val token = user.token ?: responseToken
        authStore.save(
            user.copy(
                login = user.login?.takeIf(String::isNotBlank) ?: login,
                name = user.name?.takeIf(String::isNotBlank) ?: name,
                token = token,
            ),
        )
    }

    private fun checkResponse(response: Response<Unit>) {
        if (!response.isSuccessful) {
            throw NetworkException(response.code(), "Ошибка сервера (${response.code()})")
        }
    }

    private suspend fun <T> safeRequest(request: suspend () -> T): T {
        return try {
            request()
        } catch (error: HttpException) {
            throw NetworkException(error.code(), "Ошибка сервера (${error.code()})")
        } catch (error: IOException) {
            throw NetworkException(null, "Не удалось связаться с сервером")
        }
    }
}
