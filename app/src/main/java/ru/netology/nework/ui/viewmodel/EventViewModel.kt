package ru.netology.nework.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.domain.usecase.AuthUseCase
import ru.netology.nework.domain.usecase.EventUseCase
import ru.netology.nework.domain.usecase.MediaUseCase

data class EventDraft(
    val content: String = "",
    val link: String = "",
    val offline: Boolean = false,
    val coords: Coordinates? = null,
    val media: MediaFile? = null,
    val keepAttachment: Boolean = true,
)

sealed interface CreateEventState {
    data object Idle : CreateEventState
    data object Loading : CreateEventState
    data class Success(val event: Event) : CreateEventState
    data class Error(val message: String) : CreateEventState
}

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventUseCase: EventUseCase,
    private val mediaUseCase: MediaUseCase,
    private val authUseCase: AuthUseCase,
) : ViewModel() {
    private val _createEvent = MutableStateFlow<CreateEventState>(CreateEventState.Idle)
    val createEvent = _createEvent.asStateFlow()
    private val _eventDateMillis = MutableStateFlow(nextHour())
    val eventDateMillis = _eventDateMillis.asStateFlow()
    private val _selectedSpeakerIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSpeakerIds = _selectedSpeakerIds.asStateFlow()
    private val _editingEvent = MutableStateFlow<Event?>(null)
    val editingEvent = _editingEvent.asStateFlow()
    var draft = EventDraft()
        private set
    private var saveTask: CoroutineJob? = null

    fun setEventDate(millis: Long) {
        _eventDateMillis.value = millis
    }

    fun setSpeakerSelected(userId: Long, selected: Boolean) {
        _selectedSpeakerIds.value = if (selected) _selectedSpeakerIds.value + userId else _selectedSpeakerIds.value - userId
    }

    fun updateDraft(value: EventDraft) {
        draft = value
    }

    fun startEditing(event: Event) {
        resetEventDraft()
        _editingEvent.value = event
        _createEvent.value = CreateEventState.Idle
        _eventDateMillis.value = parseEventDate(event.datetime)
        _selectedSpeakerIds.value = event.speakerIds.toSet()
        draft = EventDraft(event.content, event.link.orEmpty(), event.type.equals("OFFLINE", true), event.coords)
    }

    fun resetEventDraft() {
        saveTask?.cancel()
        saveTask = null
        _editingEvent.value = null
        _createEvent.value = CreateEventState.Idle
        _eventDateMillis.value = nextHour()
        _selectedSpeakerIds.value = emptySet()
        draft = EventDraft()
    }

    fun saveEvent(
        content: String,
        dateMillis: Long,
        offline: Boolean,
        speakerIds: List<Long>,
        link: String? = null,
        coords: Coordinates? = null,
        mediaFile: MediaFile? = null,
        keepExistingAttachment: Boolean = true,
    ) {
        if (_createEvent.value == CreateEventState.Loading) return
        val existing = _editingEvent.value
        val user = authUseCase.currentUser()
        if (!authUseCase.isAuthenticated() || user == null) {
            _createEvent.value = CreateEventState.Error("Войдите в аккаунт")
            return
        }
        if (existing != null && existing.authorId != user.id) {
            _createEvent.value = CreateEventState.Error("Можно изменять только свои события")
            return
        }
        if (content.isBlank()) {
            _createEvent.value = CreateEventState.Error("Введите описание события")
            return
        }
        _createEvent.value = CreateEventState.Loading
        saveTask = viewModelScope.launch {
            try {
                val attachment = mediaFile?.let { mediaUseCase.upload(it) }
                    ?: existing?.attachment?.takeIf { keepExistingAttachment }
                currentCoroutineContext().ensureActive()
                check(authUseCase.currentUser()?.id == user.id) { "Войдите в аккаунт ещё раз" }
                val initial = existing ?: Event(
                    id = 0, authorId = user.id, author = user.name?.takeIf(String::isNotBlank) ?: user.login.orEmpty(),
                    authorAvatar = user.avatar, published = Instant.now().toString(),
                )
                val event = initial.copy(
                    content = content.trim(), datetime = Instant.ofEpochMilli(dateMillis).toString(),
                    type = if (offline) "OFFLINE" else "ONLINE", speakerIds = speakerIds,
                    link = link?.trim()?.takeIf(String::isNotBlank), coords = coords, attachment = attachment,
                )
                val saved = eventUseCase.save(event)
                currentCoroutineContext().ensureActive()
                check(authUseCase.currentUser()?.id == user.id) { "Войдите в аккаунт ещё раз" }
                check(existing == null || saved.id == existing.id) { "Сервер вернул другой ID вместо обновления события" }
                _createEvent.value = CreateEventState.Success(saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _createEvent.value = CreateEventState.Error(error.message ?: "Не удалось сохранить событие")
            }
        }
    }

    fun clearCreateEventState() {
        _createEvent.value = CreateEventState.Idle
    }

    private fun parseEventDate(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        .getOrDefault(nextHour())

    private fun nextHour() = System.currentTimeMillis() + 60 * 60 * 1000
}

