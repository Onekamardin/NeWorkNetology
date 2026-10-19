package ru.netology.nework

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.usecase.AuthUseCase
import ru.netology.nework.domain.usecase.EventUseCase
import ru.netology.nework.domain.usecase.FeedUseCase
import ru.netology.nework.domain.usecase.MediaUseCase
import ru.netology.nework.domain.usecase.PostUseCase
import ru.netology.nework.domain.usecase.ProfileUseCase
import ru.netology.nework.ui.viewmodel.CreatePostState
import ru.netology.nework.ui.viewmodel.CreateEventState
import ru.netology.nework.ui.viewmodel.EventViewModel
import ru.netology.nework.ui.viewmodel.JobOperation
import ru.netology.nework.ui.viewmodel.JobOperationState
import ru.netology.nework.ui.viewmodel.PostViewModel
import ru.netology.nework.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class EditingTest {
    private lateinit var repository: FakeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = FakeRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun postViewModel() = PostViewModel(
        PostUseCase(repository), MediaUseCase(repository), AuthUseCase(repository),
    )

    @Test
    fun editingPostKeepsIdentityAndExistingAttachment() = runTest {
        val attachment = Attachment("/media/old.jpg", "IMAGE")
        val original = Post(11, authorId = 7, content = "Old", published = "2026-10-01T09:00:00Z",
            attachment = attachment, likeOwnerIds = listOf(8), mentionIds = listOf(9))
        val model = postViewModel()
        model.startEditing(original)
        model.savePost("New", mentionIds = listOf(9), coords = Coordinates(55.7, 37.6))
        advanceUntilIdle()

        val saved = requireNotNull(repository.savedPost)
        assertEquals(11L, saved.id)
        assertEquals("New", saved.content)
        assertEquals(original.published, saved.published)
        assertEquals(attachment, saved.attachment)
        assertEquals(listOf(8L), saved.likeOwnerIds)
        assertEquals(listOf(9L), saved.mentionIds)
        assertEquals(0, repository.uploadCount)
    }

    @Test
    fun attachmentCanBeRemovedAndReplaced() = runTest {
        val original = Post(11, authorId = 7, content = "Old", attachment = Attachment("/old.jpg", "IMAGE"))
        val model = postViewModel()
        model.startEditing(original)
        model.savePost("Without attachment", keepExistingAttachment = false)
        advanceUntilIdle()
        assertNull(repository.savedPost?.attachment)

        model.startEditing(original)
        model.savePost("New attachment", mediaFile = MediaFile("new.jpg", "image/jpeg", byteArrayOf(1)))
        advanceUntilIdle()
        assertEquals("/media/new.jpg", repository.savedPost?.attachment?.url)
        assertEquals(1, repository.uploadCount)
    }

    @Test
    fun editingEventKeepsParticipantsAndLikes() = runTest {
        val original = Event(21, authorId = 7, content = "Old", datetime = "2026-10-20T12:00:00Z",
            published = "2026-10-01T09:00:00Z", participantsIds = listOf(7, 9),
            likeOwnerIds = listOf(9), participatedByMe = true, attachment = Attachment("/old.mp4", "VIDEO"))
        val model = EventViewModel(EventUseCase(repository), MediaUseCase(repository), AuthUseCase(repository))
        model.startEditing(original)
        model.saveEvent("New", model.eventDateMillis.value, true, listOf(10))
        advanceUntilIdle()

        val saved = requireNotNull(repository.savedEvent)
        assertEquals(21L, saved.id)
        assertEquals(original.participantsIds, saved.participantsIds)
        assertEquals(original.likeOwnerIds, saved.likeOwnerIds)
        assertEquals(original.attachment, saved.attachment)
        assertEquals(original.datetime, saved.datetime)
        assertEquals("OFFLINE", saved.type)
        assertEquals(listOf(10L), saved.speakerIds)
    }

    @Test
    fun failureKeepsEditorOpenForRetry() = runTest {
        val original = Post(11, authorId = 7, content = "Old")
        val model = postViewModel()
        model.startEditing(original)
        repository.failure = IllegalStateException("Offline")
        model.savePost("New")
        advanceUntilIdle()
        assertTrue(model.createPost.value is CreatePostState.Error)
        assertEquals(original, model.editingPost.value)

        repository.failure = null
        model.savePost("New")
        advanceUntilIdle()
        assertTrue(model.createPost.value is CreatePostState.Success)
        assertEquals(11L, repository.savedPost?.id)
    }

    @Test
    fun doubleTapSendsOnlyOneSaveRequest() = runTest {
        val model = postViewModel()
        model.savePost("New")
        model.savePost("New")
        advanceUntilIdle()
        assertEquals(1, repository.postSaveCount)
        assertFalse(repository.savedPost?.published.isNullOrBlank())
        assertEquals(7L, repository.savedPost?.authorId)
    }

    @Test
    fun unauthenticatedUserCannotSave() = runTest {
        repository.logout()
        val model = postViewModel()
        model.savePost("New")
        advanceUntilIdle()
        assertTrue(model.createPost.value is CreatePostState.Error)
        assertEquals(0, repository.postSaveCount)
    }

    @Test
    fun editingJobUpdatesSameRecordAndRefreshKeepsIt() = runTest {
        val original = Job(31, "Old company", "Developer", "2024-01-01T00:00:00Z")
        repository.storedJobs.add(original)
        val model = ProfileViewModel(ProfileUseCase(repository), FeedUseCase(repository), AuthUseCase(repository))
        model.loadProfile(requireNotNull(repository.signedInUser))
        advanceUntilIdle()
        model.startEditingJob(original)
        model.saveJob("New company", "Android developer", model.jobStartMillis(), null, "https://example.com")
        advanceUntilIdle()

        assertEquals(31L, repository.savedJob?.id)
        assertEquals(JobOperationState.Success(JobOperation.UPDATED), model.jobOperation.value)
        model.refreshJobs()
        advanceUntilIdle()
        assertEquals(1, model.profile.value.jobs.items.size)
        assertEquals("New company", model.profile.value.jobs.items.single().name)
        assertNull(model.profile.value.jobs.items.single().finish)
    }

    @Test
    fun openingAnotherPostCancelsPreviousSave() = runTest {
        val response = CompletableDeferred<Unit>()
        repository.beforePostSave = { response.await() }
        val model = postViewModel()
        model.startEditing(Post(11, authorId = 7, content = "First"))
        model.savePost("Changed first post")
        runCurrent()
        assertEquals(CreatePostState.Loading, model.createPost.value)

        val secondPost = Post(12, authorId = 7, content = "Second")
        model.startEditing(secondPost)
        response.complete(Unit)
        advanceUntilIdle()

        assertEquals(CreatePostState.Idle, model.createPost.value)
        assertEquals(secondPost, model.editingPost.value)
        assertEquals("Second", model.draft.content)
        assertNull(repository.savedPost)
    }

    @Test
    fun openingAnotherEventCancelsPreviousSave() = runTest {
        val response = CompletableDeferred<Unit>()
        repository.beforeEventSave = { response.await() }
        val model = EventViewModel(EventUseCase(repository), MediaUseCase(repository), AuthUseCase(repository))
        model.startEditing(Event(21, authorId = 7, content = "First"))
        model.saveEvent("Changed first event", model.eventDateMillis.value, false, emptyList())
        runCurrent()
        assertEquals(CreateEventState.Loading, model.createEvent.value)

        val secondEvent = Event(22, authorId = 7, content = "Second")
        model.startEditing(secondEvent)
        response.complete(Unit)
        advanceUntilIdle()

        assertEquals(CreateEventState.Idle, model.createEvent.value)
        assertEquals(secondEvent, model.editingEvent.value)
        assertEquals("Second", model.draft.content)
        assertNull(repository.savedEvent)
    }

    @Test
    fun openingAnotherJobCancelsPreviousSave() = runTest {
        val response = CompletableDeferred<Unit>()
        repository.beforeJobSave = { response.await() }
        val model = ProfileViewModel(ProfileUseCase(repository), FeedUseCase(repository), AuthUseCase(repository))
        model.loadProfile(requireNotNull(repository.signedInUser))
        advanceUntilIdle()
        model.startEditingJob(Job(31, "First", "Developer", "2024-01-01T00:00:00Z"))
        model.saveJob("Changed first company", "Developer", model.jobStartMillis(), null, null)
        runCurrent()
        assertEquals(JobOperationState.Loading, model.jobOperation.value)

        val secondJob = Job(32, "Second", "Designer", "2025-01-01T00:00:00Z")
        model.startEditingJob(secondJob)
        response.complete(Unit)
        advanceUntilIdle()

        assertEquals(JobOperationState.Idle, model.jobOperation.value)
        assertEquals(secondJob, model.editingJob.value)
        assertEquals("Second", model.jobDraft.value.name)
        assertTrue(model.profile.value.jobs.items.isEmpty())
        assertNull(repository.savedJob)
    }

    @Test
    fun logoutDuringUploadPreventsPostSave() = runTest {
        val upload = CompletableDeferred<Unit>()
        repository.beforeUpload = { upload.await() }
        val model = postViewModel()
        model.savePost("New post", mediaFile = MediaFile("new.jpg", "image/jpeg", byteArrayOf(1)))
        runCurrent()
        repository.logout()
        upload.complete(Unit)
        advanceUntilIdle()

        assertTrue(model.createPost.value is CreatePostState.Error)
        assertEquals(0, repository.postSaveCount)
        assertNull(repository.savedPost)
    }

    @Test
    fun logoutDuringUploadPreventsEventSave() = runTest {
        val upload = CompletableDeferred<Unit>()
        repository.beforeUpload = { upload.await() }
        val model = EventViewModel(EventUseCase(repository), MediaUseCase(repository), AuthUseCase(repository))
        model.saveEvent("New event", model.eventDateMillis.value, false, emptyList(),
            mediaFile = MediaFile("new.jpg", "image/jpeg", byteArrayOf(1)))
        runCurrent()
        repository.logout()
        upload.complete(Unit)
        advanceUntilIdle()

        assertTrue(model.createEvent.value is CreateEventState.Error)
        assertNull(repository.savedEvent)
    }
}

