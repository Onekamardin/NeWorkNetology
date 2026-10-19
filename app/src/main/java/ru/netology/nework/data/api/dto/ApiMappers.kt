package ru.netology.nework.data.api.dto

import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.JobCreateRequest
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.model.UserPreview

fun UserDto.toDomain() = User(id, login, name, avatar, online, token)

fun PostDto.toDomain() = Post(
    id = id, authorId = authorId, author = author, authorAvatar = authorAvatar,
    content = content, published = published.orEmpty(), likeOwnerIds = likeOwnerIds,
    likedByMe = likedByMe, attachment = attachment?.toDomain(), link = link,
    coords = coords?.toDomain(), mentionIds = mentionIds, authorJob = authorJob,
    users = users.mapValues { (_, user) -> UserPreview(user.name, user.avatar) },
    mentionedMe = mentionedMe,
)

fun EventDto.toDomain() = Event(
    id = id, authorId = authorId, author = author, authorAvatar = authorAvatar,
    content = content, published = published.orEmpty(), datetime = datetime, type = type,
    likeOwnerIds = likeOwnerIds, likedByMe = likedByMe, attachment = attachment?.toDomain(),
    link = link, coords = coords?.toDomain(), speakerIds = speakerIds,
    participantsIds = participantsIds, authorJob = authorJob,
    users = users.mapValues { (_, user) -> UserPreview(user.name, user.avatar) },
    participatedByMe = participatedByMe,
)

fun JobDto.toDomain() = Job(id, name, position, start, finish, link)
fun AttachmentDto.toDomain() = Attachment(url, type)
fun CoordinatesDto.toDomain() = Coordinates(latitude, longitude)
fun JobCreateRequest.toDto() = JobCreateRequestDto(name, position, start, finish, link, id)

fun Post.toDto() = PostDto(
    id = id, authorId = authorId, author = author, authorAvatar = authorAvatar,
    content = content, published = published.takeIf(String::isNotBlank),
    likeOwnerIds = likeOwnerIds, likedByMe = likedByMe,
    attachment = attachment?.let { AttachmentDto(it.url, it.type) }, link = link,
    coords = coords?.let { CoordinatesDto(it.latitude, it.longitude) }, mentionIds = mentionIds,
    authorJob = authorJob, users = users.mapValues { (_, user) -> UserPreviewDto(user.name, user.avatar) },
    mentionedMe = mentionedMe,
)

fun Event.toDto() = EventDto(
    id = id, authorId = authorId, author = author, authorAvatar = authorAvatar,
    content = content, published = published.takeIf(String::isNotBlank), datetime = datetime,
    type = type, likeOwnerIds = likeOwnerIds, likedByMe = likedByMe,
    attachment = attachment?.let { AttachmentDto(it.url, it.type) }, link = link,
    coords = coords?.let { CoordinatesDto(it.latitude, it.longitude) },
    speakerIds = speakerIds, participantsIds = participantsIds, authorJob = authorJob,
    users = users.mapValues { (_, user) -> UserPreviewDto(user.name, user.avatar) },
    participatedByMe = participatedByMe,
)

