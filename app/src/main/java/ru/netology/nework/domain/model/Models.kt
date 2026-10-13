package ru.netology.nework.domain.model

data class User(
    val id: Long,
    val login: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val online: Boolean = false,
    val token: String? = null,
)

data class Post(
    val id: Long,
    val authorId: Long = 0,
    val author: String? = null,
    val authorAvatar: String? = null,
    val content: String = "",
    val published: String = "",
    val likeOwnerIds: List<Long> = emptyList(),
    val likedByMe: Boolean = false,
    val attachment: Attachment? = null,
    val link: String? = null,
    val coords: Coordinates? = null,
    val mentionIds: List<Long> = emptyList(),
)

data class Event(
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
    val attachment: Attachment? = null,
    val link: String? = null,
    val coords: Coordinates? = null,
    val speakerIds: List<Long> = emptyList(),
    val participantsIds: List<Long> = emptyList(),
)

data class EventCreateRequest(
    val content: String,
    val datetime: String,
    val type: String,
    val speakerIds: List<Long> = emptyList(),
    val attachment: Attachment? = null,
    val link: String? = null,
    val coords: Coordinates? = null,
)

data class Attachment(val url: String, val type: String)
data class Coordinates(val latitude: Double, val longitude: Double)
data class Job(
    val id: Long,
    val name: String = "",
    val position: String = "",
    val start: String = "",
    val finish: String? = null,
    val link: String? = null,
)

data class JobCreateRequest(
    val name: String,
    val position: String,
    val start: String,
    val finish: String? = null,
    val link: String? = null,
)

data class Credentials(val login: String, val password: String)



data class MediaFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
