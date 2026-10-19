package ru.netology.nework.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.JobCreateRequest
import ru.netology.nework.domain.model.NetworkException
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.usecase.FeedUseCase
import ru.netology.nework.domain.usecase.AuthUseCase
import ru.netology.nework.domain.usecase.ProfileUseCase

data class ProfileState(
    val user: User? = null,
    val wall: FeedState<Post> = FeedState(),
    val jobs: FeedState<Job> = FeedState(),
)

data class JobDraft(val name: String = "", val position: String = "", val link: String = "")

enum class JobOperation { CREATED, UPDATED, DELETED }

sealed interface JobOperationState {
    data object Idle : JobOperationState
    data object Loading : JobOperationState
    data class Success(val operation: JobOperation) : JobOperationState
    data class Error(val message: String) : JobOperationState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileUseCase: ProfileUseCase,
    private val feedUseCase: FeedUseCase,
    private val authUseCase: AuthUseCase,
) : ViewModel() {
    private val _profile = MutableStateFlow(ProfileState())
    val profile = _profile.asStateFlow()
    private val _jobOperation = MutableStateFlow<JobOperationState>(JobOperationState.Idle)
    val jobOperation = _jobOperation.asStateFlow()
    private val _editingJob = MutableStateFlow<Job?>(null)
    val editingJob = _editingJob.asStateFlow()
    private val _jobDraft = MutableStateFlow(JobDraft())
    val jobDraft = _jobDraft.asStateFlow()
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private var jobStart: Long? = null
    private var jobFinish: Long? = null
    private var userLoad: CoroutineJob? = null
    private var wallLoad: CoroutineJob? = null
    private var jobsLoad: CoroutineJob? = null
    private var jobMutation: CoroutineJob? = null

    fun jobStartMillis(): Long? = jobStart
    fun jobFinishMillis(): Long? = jobFinish

    fun setJobPeriod(startMillis: Long?, finishMillis: Long?) {
        jobStart = startMillis
        jobFinish = finishMillis
    }

    fun updateJobDraft(name: String, position: String, link: String) {
        _jobDraft.value = JobDraft(name, position, link)
    }

    fun resetJobDraft() {
        jobMutation?.cancel()
        jobMutation = null
        _jobOperation.value = JobOperationState.Idle
        _editingJob.value = null
        _jobDraft.value = JobDraft()
        jobStart = null
        jobFinish = null
    }

    fun startEditingJob(job: Job) {
        resetJobDraft()
        _editingJob.value = job
        _jobDraft.value = JobDraft(job.name, job.position, job.link.orEmpty())
        jobStart = parseJobDate(job.start)
        jobFinish = job.finish?.takeIf(String::isNotBlank)?.let(::parseJobDate)
    }

    fun saveJob(name: String, position: String, startMillis: Long?, finishMillis: Long?, link: String?) {
        if (_jobOperation.value == JobOperationState.Loading) return
        val userId = authUseCase.currentUser()?.id
        if (userId == null || userId != _profile.value.user?.id) {
            _jobOperation.value = JobOperationState.Error("Войдите в свой профиль, чтобы сохранить место работы")
            return
        }
        if (name.isBlank() || position.isBlank() || startMillis == null) {
            _jobOperation.value = JobOperationState.Error("Заполните название, должность и период работы")
            return
        }
        if (finishMillis != null && finishMillis < startMillis) {
            _jobOperation.value = JobOperationState.Error("Дата окончания не может быть раньше начала")
            return
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val request = JobCreateRequest(
            id = _editingJob.value?.id ?: 0,
            name = name.trim(),
            position = position.trim(),
            start = formatter.format(Date(startMillis)) + "T00:00:00.000Z",
            finish = finishMillis?.let { formatter.format(Date(it)) + "T00:00:00.000Z" },
            link = link?.trim()?.takeIf(String::isNotBlank),
        )
        _jobOperation.value = JobOperationState.Loading
        jobMutation = viewModelScope.launch {
            try {
                check(authUseCase.currentUser()?.id == userId) { "Войдите в аккаунт ещё раз" }
                val saved = profileUseCase.createJob(request)
                currentCoroutineContext().ensureActive()
                if (authUseCase.currentUser()?.id != userId) return@launch
                if (request.id != 0L && saved.id != request.id) {
                    throw IllegalStateException("Сервер вернул другую запись. Обновите список работ")
                }
                jobsLoad?.cancel()
                if (_profile.value.user?.id == userId) {
                    val jobs = _profile.value.jobs.items
                    _profile.value = _profile.value.copy(
                        jobs = FeedState(items = (jobs.filterNot { it.id == saved.id } + saved).sortedByDescending { it.start }),
                    )
                }
                _jobOperation.value = JobOperationState.Success(
                    if (request.id == 0L) JobOperation.CREATED else JobOperation.UPDATED,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _jobOperation.value = JobOperationState.Error(error.message ?: "Не удалось сохранить место работы")
            }
        }
    }

    fun refreshJobs() {
        val user = _profile.value.user ?: return
        jobsLoad?.cancel()
        _profile.value = _profile.value.copy(jobs = _profile.value.jobs.copy(loading = true, error = null))
        jobsLoad = viewModelScope.launch {
            try {
                val jobs = profileUseCase.jobs(user.id)
                if (_profile.value.user?.id == user.id) {
                    _profile.value = _profile.value.copy(jobs = FeedState(items = jobs.sortedByDescending { it.start }))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_profile.value.user?.id == user.id) {
                    val state = if (error is NetworkException && error.code == 404) FeedState<Job>() else {
                        _profile.value.jobs.copy(loading = false, error = "Не удалось загрузить места работы")
                    }
                    _profile.value = _profile.value.copy(jobs = state)
                }
            }
        }
    }

    fun deleteJob(id: Long) {
        if (_jobOperation.value == JobOperationState.Loading) return
        val userId = authUseCase.currentUser()?.id
        if (userId == null || userId != _profile.value.user?.id) {
            _jobOperation.value = JobOperationState.Error("Можно удалить только свои места работы")
            return
        }
        _jobOperation.value = JobOperationState.Loading
        jobMutation = viewModelScope.launch {
            try {
                check(authUseCase.currentUser()?.id == userId) { "Войдите в аккаунт ещё раз" }
                profileUseCase.deleteJob(id)
                currentCoroutineContext().ensureActive()
                if (authUseCase.currentUser()?.id != userId) return@launch
                jobsLoad?.cancel()
                _profile.value = _profile.value.copy(
                    jobs = FeedState(items = _profile.value.jobs.items.filterNot { it.id == id }),
                )
                _jobOperation.value = JobOperationState.Success(JobOperation.DELETED)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _jobOperation.value = JobOperationState.Error(error.message ?: "Не удалось удалить работу")
            }
        }
    }

    fun clearJobOperationState() {
        _jobOperation.value = JobOperationState.Idle
    }

    fun togglePostLike(post: Post) = viewModelScope.launch {
        try {
            val updated = if (post.likedByMe) feedUseCase.unlikePost(post.id) else feedUseCase.likePost(post.id)
            _profile.value = _profile.value.copy(
                wall = _profile.value.wall.copy(items = _profile.value.wall.items.map { if (it.id == updated.id) updated else it }),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _messages.emit(error.message ?: "Не удалось изменить отметку «Нравится»")
        }
    }

    fun loadProfile(user: User) {
        userLoad?.cancel()
        wallLoad?.cancel()
        jobsLoad?.cancel()
        val old = _profile.value.takeIf { it.user?.id == user.id }
        _profile.value = ProfileState(
            user = user,
            wall = (old?.wall ?: FeedState()).copy(loading = true, error = null),
            jobs = (old?.jobs ?: FeedState()).copy(loading = true, error = null),
        )
        userLoad = viewModelScope.launch {
            try {
                val loaded = profileUseCase.user(user.id)
                if (_profile.value.user?.id == user.id) _profile.value = _profile.value.copy(user = loaded)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Unit
            }
        }
        wallLoad = viewModelScope.launch {
            try {
                val posts = profileUseCase.wall(user.id)
                if (_profile.value.user?.id == user.id) _profile.value = _profile.value.copy(wall = FeedState(items = posts))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_profile.value.user?.id == user.id) {
                    val state = if (error is NetworkException && error.code == 404) FeedState<Post>() else {
                        _profile.value.wall.copy(loading = false, error = "Не удалось загрузить стену")
                    }
                    _profile.value = _profile.value.copy(wall = state)
                }
            }
        }
        refreshJobs()
    }

    fun clearProfile() {
        userLoad?.cancel()
        wallLoad?.cancel()
        jobsLoad?.cancel()
        _profile.value = ProfileState()
        resetJobDraft()
    }

    private fun parseJobDate(value: String): Long? = runCatching {
        LocalDate.parse(value.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

