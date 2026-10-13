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
import ru.netology.nework.data.api.dto.CredentialsDto
import ru.netology.nework.data.api.dto.EventCreateRequestDto
import ru.netology.nework.data.api.dto.EventDto
import ru.netology.nework.data.api.dto.JobCreateRequestDto
import ru.netology.nework.data.api.dto.JobDto
import ru.netology.nework.data.api.dto.MediaUploadDto
import ru.netology.nework.data.api.dto.PostDto
import ru.netology.nework.data.api.dto.UserDto

interface NeWorkApi {
    @GET("posts") suspend fun getPosts(): List<PostDto>
    @GET("events") suspend fun getEvents(): List<EventDto>
    @GET("users") suspend fun getUsers(): List<UserDto>
    @GET("users/{id}") suspend fun getUser(@Path("id") id: Long): UserDto
    @GET("users/{id}/wall") suspend fun getWall(@Path("id") id: Long): List<PostDto>
    @GET("users/{id}/jobs") suspend fun getJobs(@Path("id") id: Long): List<JobDto>
    @POST("my/jobs") suspend fun createJob(@Body body: JobCreateRequestDto): JobDto
    @DELETE("my/jobs/{id}") suspend fun deleteJob(@Path("id") id: Long): Response<Unit>

    @POST("posts/{id}/likes") suspend fun likePost(@Path("id") id: Long): PostDto
    @DELETE("posts/{id}/likes") suspend fun unlikePost(@Path("id") id: Long): PostDto
    @POST("events/{id}/likes") suspend fun likeEvent(@Path("id") id: Long): EventDto
    @DELETE("events/{id}/likes") suspend fun unlikeEvent(@Path("id") id: Long): EventDto

    @POST("users/authentication") suspend fun authenticate(@Body body: CredentialsDto): Response<UserDto>
    @Multipart
    @POST("users/registration")
    suspend fun register(
        @Part("login") login: RequestBody,
        @Part("pass") password: RequestBody,
        @Part("name") name: RequestBody,
        @Part file: MultipartBody.Part?,
    ): Response<UserDto>
    @Multipart
    @POST("media")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): MediaUploadDto

    @POST("posts") suspend fun createPost(@Body body: PostDto): PostDto
    @DELETE("posts/{id}") suspend fun deletePost(@Path("id") id: Long): Response<Unit>
    @POST("events") suspend fun createEvent(@Body body: EventCreateRequestDto): EventDto
    @DELETE("events/{id}") suspend fun deleteEvent(@Path("id") id: Long): Response<Unit>
    @POST("events/{id}/participants") suspend fun participateEvent(@Path("id") id: Long): Response<Unit>
    @DELETE("events/{id}/participants") suspend fun leaveEvent(@Path("id") id: Long): Response<Unit>
}
