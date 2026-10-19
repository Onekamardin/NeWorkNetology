package ru.netology.nework

import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.model.AuthenticationResult
import ru.netology.nework.domain.model.AvatarFile
import ru.netology.nework.domain.model.Credentials
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.JobCreateRequest
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.repository.NeWorkRepository

class FakeRepository : NeWorkRepository {
    var signedInUser: User? = User(7, "test", "Test", token = "test-token")
    var savedPost: Post? = null
    var savedEvent: Event? = null
    var savedJob: JobCreateRequest? = null
    var failure: Exception? = null
    var postSaveCount = 0
    var uploadCount = 0
    var beforePostSave: (suspend () -> Unit)? = null
    var beforeEventSave: (suspend () -> Unit)? = null
    var beforeJobSave: (suspend () -> Unit)? = null
    var beforeUpload: (suspend () -> Unit)? = null
    val storedJobs = mutableListOf<Job>()

    override suspend fun savePost(post: Post): Post {
        postSaveCount++
        beforePostSave?.invoke()
        failure?.let { throw it }
        return post.copy(id = post.id.takeIf { it != 0L } ?: 100).also { savedPost = it }
    }

    override suspend fun saveEvent(event: Event): Event {
        beforeEventSave?.invoke()
        failure?.let { throw it }
        return event.copy(id = event.id.takeIf { it != 0L } ?: 200).also { savedEvent = it }
    }

    override suspend fun createJob(request: JobCreateRequest): Job {
        beforeJobSave?.invoke()
        failure?.let { throw it }
        savedJob = request
        val job = Job(request.id.takeIf { it != 0L } ?: 300, request.name, request.position, request.start, request.finish, request.link)
        storedJobs.removeAll { it.id == job.id }
        storedJobs.add(job)
        return job
    }

    override suspend fun uploadMedia(file: MediaFile): Attachment {
        uploadCount++
        beforeUpload?.invoke()
        return Attachment("/media/new.jpg", "IMAGE")
    }

    override suspend fun jobs(id: Long) = storedJobs.toList()
    override suspend fun deleteJob(id: Long) { storedJobs.removeAll { it.id == id } }
    override suspend fun user(id: Long) = requireNotNull(signedInUser)
    override suspend fun wall(id: Long) = emptyList<Post>()
    override suspend fun posts() = listOfNotNull(savedPost)
    override suspend fun events() = listOfNotNull(savedEvent)
    override suspend fun post(id: Long) = requireNotNull(savedPost)
    override suspend fun event(id: Long) = requireNotNull(savedEvent)
    override suspend fun users() = listOfNotNull(signedInUser)
    override suspend fun likePost(id: Long): Post = error("Unused")
    override suspend fun unlikePost(id: Long): Post = error("Unused")
    override suspend fun likeEvent(id: Long): Event = error("Unused")
    override suspend fun unlikeEvent(id: Long): Event = error("Unused")
    override suspend fun deletePost(id: Long) { savedPost = null }
    override suspend fun deleteEvent(id: Long) { savedEvent = null }
    override suspend fun participateEvent(id: Long): Event = error("Unused")
    override suspend fun leaveEvent(id: Long): Event = error("Unused")
    override suspend fun authenticate(credentials: Credentials): AuthenticationResult = error("Unused")
    override suspend fun register(login: String, name: String, password: String, avatar: AvatarFile?): AuthenticationResult = error("Unused")
    override fun logout() { signedInUser = null }
    override fun currentUser() = signedInUser
}

