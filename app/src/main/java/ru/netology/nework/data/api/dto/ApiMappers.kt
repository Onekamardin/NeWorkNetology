package ru.netology.nework.data.api.dto

import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.Credentials
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.EventCreateRequest
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.JobCreateRequest
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User

fun UserDto.toDomain() = User(id, login, name, avatar, online, token)
fun PostDto.toDomain() = Post(
    id, authorId, author, authorAvatar, content, published, likeOwnerIds, likedByMe,
    attachment?.toDomain(), link, coords?.toDomain(), mentionIds,
)
fun EventDto.toDomain() = Event(
    id, authorId, author, authorAvatar, content, published, datetime, type,
    likeOwnerIds, likedByMe, attachment?.toDomain(), link, coords?.toDomain(), speakerIds, participantsIds,
)
fun JobDto.toDomain() = Job(id, name, position, start, finish, link)
fun AttachmentDto.toDomain() = Attachment(url, type)
fun CoordinatesDto.toDomain() = Coordinates(latitude, longitude)

fun Credentials.toDto() = CredentialsDto(login, password)
fun JobCreateRequest.toDto() = JobCreateRequestDto(name, position, start, finish, link)
fun EventCreateRequest.toDto() = EventCreateRequestDto(
    content, datetime, type, speakerIds, attachment?.let { AttachmentDto(it.url, it.type) }, link,
    coords?.let { CoordinatesDto(it.latitude, it.longitude) },
)
fun Post.toDto() = PostDto(
    id, authorId, author, authorAvatar, content, published, likeOwnerIds, likedByMe,
    attachment?.let { AttachmentDto(it.url, it.type) }, link,
    coords?.let { CoordinatesDto(it.latitude, it.longitude) }, mentionIds,
)
