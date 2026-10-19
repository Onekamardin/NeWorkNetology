package ru.netology.nework.data.api.dto

import com.google.gson.annotations.SerializedName

data class UserDto(
    val id: Long,
    val login: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val online: Boolean = false,
    val token: String? = null,
)

data class PostDto(
    val id: Long,
    val authorId: Long = 0,
    val author: String? = null,
    val authorAvatar: String? = null,
    val content: String = "",
    val published: String? = null,
    val likeOwnerIds: List<Long> = emptyList(),
    val likedByMe: Boolean = false,
    val attachment: AttachmentDto? = null,
    val link: String? = null,
    val coords: CoordinatesDto? = null,
    val mentionIds: List<Long> = emptyList(),
    val authorJob: String? = null,
    val users: Map<Long, UserPreviewDto> = emptyMap(),
    val mentionedMe: Boolean = false,
)

data class EventDto(
    val id: Long,
    val authorId: Long = 0,
    val author: String? = null,
    val authorAvatar: String? = null,
    val content: String = "",
    val published: String? = null,
    val datetime: String = "",
    val type: String = "ONLINE",
    val likeOwnerIds: List<Long> = emptyList(),
    val likedByMe: Boolean = false,
    val attachment: AttachmentDto? = null,
    val link: String? = null,
    val coords: CoordinatesDto? = null,
    val speakerIds: List<Long> = emptyList(),
    val participantsIds: List<Long> = emptyList(),
    val authorJob: String? = null,
    val users: Map<Long, UserPreviewDto> = emptyMap(),
    val participatedByMe: Boolean = false,
)

data class UserPreviewDto(val name: String, val avatar: String? = null)

data class JobDto(
    val id: Long,
    val name: String = "",
    val position: String = "",
    val start: String = "",
    val finish: String? = null,
    val link: String? = null,
)

data class AttachmentDto(val url: String, val type: String)
data class MediaUploadDto(val url: String)
data class CoordinatesDto(
    @SerializedName("lat") val latitude: Double,
    @SerializedName("long") val longitude: Double,
)

data class JobCreateRequestDto(
    val name: String,
    val position: String,
    val start: String,
    val finish: String? = null,
    val link: String? = null,
    val id: Long = 0,
)

