package ru.netology.nework.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.JobCreateRequest
import ru.netology.nework.domain.model.NetworkException
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.usecase.FeedUseCase
import ru.netology.nework.domain.usecase.ProfileUseCase

data class ProfileState(
    val user: User? = null,
    val wall: FeedState<Post> = FeedState(loading = true),
    val jobs: FeedState<Job> = FeedState(loading = true),
)

sealed interface JobOperationState {
    data object Idle : JobOperationState
    data object Loading : JobOperationState
    data object Success : JobOperationState
    data class Error(val message: String) : JobOperationState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileUseCase: ProfileUseCase,
    private val feedUseCase: FeedUseCase,
) : ViewModel() {
    private val _profile = MutableStateFlow(ProfileState())
    val profile: StateFlow<ProfileState> = _profile.asStateFlow()

    private val _jobOperation = MutableStateFlow<JobOperationState>(JobOperationState.Idle)
    val jobOperation: StateFlow<JobOperationState> = _jobOperation.asStateFlow()
    private var jobStart: Long? = null
    private var jobFinish: Long? = null

    fun jobStartMillis(): Long? = jobStart
    fun jobFinishMillis(): Long? = jobFinish

    fun setJobPeriod(startMillis: Long?, finishMillis: Long?) {
        jobStart = startMillis
        jobFinish = finishMillis
    }

    fun resetJobDraft() {
        _jobOperation.value = JobOperationState.Idle
        jobStart = null
        jobFinish = null
    }

    fun createJob(name: String, position: String, startMillis: Long?, finishMillis: Long?, link: String?) {
        val cleanName = name.trim()
        val cleanPosition = position.trim()
        if (cleanName.isBlank() || cleanPosition.isBlank() || startMillis == null) {
            _jobOperation.value = JobOperationState.Error("Заполните название, должность и период работы")
            return
        }
        if (finishMillis != null && finishMillis < startMillis) {
            _jobOperation.value = JobOperationState.Error("Дата окончания не может быть раньше начала")
            return
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val request = JobCreateRequest(
            name = cleanName,
            position = cleanPosition,
            start = dateFormat.format(Date(startMillis)) + "T00:00:00.000Z",
            finish = finishMillis?.let { dateFormat.format(Date(it)) + "T00:00:00.000Z" },
            link = link?.trim()?.takeIf(String::isNotBlank),
        )
        createJob(request)
    }

    private fun createJob(request: JobCreateRequest) = viewModelScope.launch {
        _jobOperation.value = JobOperationState.Loading
        runCatching { profileUseCase.createJob(request) }
            .onSuccess { createdJob ->
                val jobToShow = createdJob.copy(
                    name = createdJob.name.ifBlank { request.name },
                    position = createdJob.position.ifBlank { request.position },
                    start = createdJob.start.ifBlank { request.start },
                    finish = createdJob.finish ?: request.finish,
                    link = createdJob.link ?: request.link,
                )
                val currentJobs = _profile.value.jobs
                _profile.value = _profile.value.copy(
                    jobs = currentJobs.copy(items = currentJobs.items + jobToShow, error = null),
                )
                _jobOperation.value = JobOperationState.Success
            }
            .onFailure { _jobOperation.value = JobOperationState.Error(it.message ?: "Не удалось добавить работу") }
    }

    fun deleteJob(id: Long) = viewModelScope.launch {
        _jobOperation.value = JobOperationState.Loading
        runCatching { profileUseCase.deleteJob(id) }
            .onSuccess {
                _profile.value = _profile.value.copy(
                    jobs = _profile.value.jobs.copy(items = _profile.value.jobs.items.filterNot { it.id == id }),
                )
                _jobOperation.value = JobOperationState.Success
            }
            .onFailure { _jobOperation.value = JobOperationState.Error(it.message ?: "Не удалось удалить работу") }
    }

    fun clearJobOperationState() {
        _jobOperation.value = JobOperationState.Idle
    }

    fun togglePostLike(post: Post) = viewModelScope.launch {
        runCatching {
            if (post.likedByMe) feedUseCase.unlikePost(post.id) else feedUseCase.likePost(post.id)
        }.onSuccess { updatedPost ->
            _profile.value = _profile.value.copy(
                wall = _profile.value.wall.copy(
                    items = _profile.value.wall.items.map { if (it.id == updatedPost.id) updatedPost else it },
                ),
            )
        }
    }

    fun loadProfile(user: User) = viewModelScope.launch {
        _profile.value = ProfileState(user = user)
        launch {
            runCatching { profileUseCase.user(user.id) }
                .onSuccess { loaded -> _profile.value = _profile.value.copy(user = loaded) }
        }
        launch {
            _profile.value = _profile.value.copy(wall = FeedState(loading = true))
            runCatching { profileUseCase.wall(user.id) }
                .onSuccess { _profile.value = _profile.value.copy(wall = FeedState(items = it)) }
                .onFailure { error ->
                    val missing = error is NetworkException && error.code == 404
                    _profile.value = _profile.value.copy(
                        wall = if (missing) FeedState() else FeedState(error = "Не удалось загрузить стену. Попробуйте позже."),
                    )
                }
        }
        launch {
            _profile.value = _profile.value.copy(jobs = FeedState(loading = true))
            runCatching { profileUseCase.jobs(user.id) }
                .onSuccess { _profile.value = _profile.value.copy(jobs = FeedState(items = it)) }
                .onFailure { error ->
                    val missing = error is NetworkException && error.code == 404
                    _profile.value = _profile.value.copy(
                        jobs = if (missing) FeedState() else FeedState(error = "Не удалось загрузить места работы. Попробуйте позже."),
                    )
                }
        }
    }

    fun clearProfile() {
        _profile.value = ProfileState()
    }
}
