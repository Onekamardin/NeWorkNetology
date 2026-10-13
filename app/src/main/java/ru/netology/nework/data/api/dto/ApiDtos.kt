package ru.netology.nework.data.api.dto

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
    val published: String = "",
    val likeOwnerIds: List<Long> = emptyList(),
    val likedByMe: Boolean = false,
    val attachment: AttachmentDto? = null,
    val link: String? = null,
    val coords: CoordinatesDto? = null,
    val mentionIds: List<Long> = emptyList(),
)

data class EventDto(
    val id: Long,
    val authorId: Long = 0,
    val author: String? = null,
    val authorAvatar: String? = null,
    val content: String = "",
    val published: String = "",
    val datetime: String = "",
    val type: String = "ONLINE",
    val likeOwnerIds: List<Long> = emptyList(),
    val likedByMe: Boolean = false,
    val attachment: AttachmentDto? = null,
    val link: String? = null,
    val coords: CoordinatesDto? = null,
    val speakerIds: List<Long> = emptyList(),
    val participantsIds: List<Long> = emptyList(),
)

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
data class CoordinatesDto(val latitude: Double, val longitude: Double)
data class CredentialsDto(val login: String, val password: String)

data class JobCreateRequestDto(
    val name: String,
    val position: String,
    val start: String,
    val finish: String? = null,
    val link: String? = null,
)

data class EventCreateRequestDto(
    val content: String,
    val datetime: String,
    val type: String,
    val speakerIds: List<Long> = emptyList(),
    val attachment: AttachmentDto? = null,
    val link: String? = null,
    val coords: CoordinatesDto? = null,
)
