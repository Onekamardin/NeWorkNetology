package ru.netology.nework.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
import ru.netology.nework.data.model.Credentials
import ru.netology.nework.data.model.Event
import ru.netology.nework.data.model.Job
import ru.netology.nework.data.model.Post
import ru.netology.nework.data.model.User

/**
 * Пути сверить с OpenAPI сервера до реализации запросов.
 * Базовый URL и обязательный Api-Key задаются в app/build.gradle.kts и NetworkModule.
 */
interface NeWorkApi {
    @GET("posts") suspend fun getPosts(): List<Post>
    @GET("events") suspend fun getEvents(): List<Event>
    @GET("users") suspend fun getUsers(): List<User>
    @GET("users/{id}") suspend fun getUser(@Path("id") id: Long): User
    @GET("users/{id}/wall") suspend fun getWall(@Path("id") id: Long): List<Post>
    @GET("users/{id}/jobs") suspend fun getJobs(@Path("id") id: Long): List<Job>

    @POST("users/authentication") suspend fun authenticate(@Body body: Credentials): Response<User>
    @Multipart
    @POST("users/registration")
    suspend fun register(
        @Part("login") login: RequestBody,
        @Part("pass") password: RequestBody,
        @Part("name") name: RequestBody,
        @Part file: MultipartBody.Part?,
    ): Response<User>
    @POST("posts") suspend fun createPost(@Body body: Post): Post
    @DELETE("posts/{id}") suspend fun deletePost(@Path("id") id: Long): Response<Unit>
    @POST("events") suspend fun createEvent(@Body body: Event): Event
    @DELETE("events/{id}") suspend fun deleteEvent(@Path("id") id: Long): Response<Unit>
}
