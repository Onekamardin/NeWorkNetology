package ru.netology.nework.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.EventCreateRequest
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.NetworkException
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.domain.usecase.MediaUseCase
import ru.netology.nework.domain.usecase.EventUseCase

sealed interface CreateEventState {
    data object Idle : CreateEventState
    data object Loading : CreateEventState
    data object Success : CreateEventState
    data class Error(val message: String) : CreateEventState
}

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventUseCase: EventUseCase,
    private val mediaUseCase: MediaUseCase,
) : ViewModel() {
    private val _createEvent = MutableStateFlow<CreateEventState>(CreateEventState.Idle)
    val createEvent: StateFlow<CreateEventState> = _createEvent.asStateFlow()
    private val _eventDateMillis = MutableStateFlow(System.currentTimeMillis() + 60 * 60 * 1000)
    val eventDateMillis: StateFlow<Long> = _eventDateMillis.asStateFlow()
    private val _selectedSpeakerIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSpeakerIds: StateFlow<Set<Long>> = _selectedSpeakerIds.asStateFlow()
    private val _editingEvent = MutableStateFlow<Event?>(null)
    val editingEvent: StateFlow<Event?> = _editingEvent.asStateFlow()

    fun setEventDate(millis: Long) { _eventDateMillis.value = millis }
    fun setSpeakerSelected(userId: Long, selected: Boolean) {
        val ids = _selectedSpeakerIds.value.toMutableSet()
        if (selected) ids.add(userId) else ids.remove(userId)
        _selectedSpeakerIds.value = ids
    }

    fun startEditing(event: Event) {
        _editingEvent.value = event
        _createEvent.value = CreateEventState.Idle
        _eventDateMillis.value = parseEventDate(event.datetime)
        _selectedSpeakerIds.value = event.speakerIds.toSet()
    }

    fun resetEventDraft() {
        _editingEvent.value = null
        _createEvent.value = CreateEventState.Idle
        _eventDateMillis.value = System.currentTimeMillis() + 60 * 60 * 1000
        _selectedSpeakerIds.value = emptySet()
    }

    fun createEvent(
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
        if (content.isBlank()) { _createEvent.value = CreateEventState.Error("Введите описание события"); return }
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        createEvent(EventCreateRequest(
            content = content.trim(), datetime = formatter.format(Date(dateMillis)),
            type = if (offline) "OFFLINE" else "ONLINE", speakerIds = speakerIds,
            attachment = _editingEvent.value?.attachment?.takeIf { keepExistingAttachment },
            link = link?.trim()?.takeIf(String::isNotBlank), coords = coords,
            id = _editingEvent.value?.id ?: 0,
        ), mediaFile)
    }

    private fun createEvent(request: EventCreateRequest, mediaFile: MediaFile?) = viewModelScope.launch {
        _createEvent.value = CreateEventState.Loading
        runCatching {
            val attachment: Attachment? = mediaFile?.let { mediaUseCase.upload(it) } ?: request.attachment
            eventUseCase.create(request.copy(attachment = attachment))
        }
            .onSuccess { _createEvent.value = CreateEventState.Success }
            .onFailure { error ->
                val message = if (error is NetworkException) error.message else error.message ?: "Не удалось создать событие"
                _createEvent.value = CreateEventState.Error(message)
            }
    }

    fun clearCreateEventState() { _createEvent.value = CreateEventState.Idle }

    private fun parseEventDate(value: String): Long {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time
            }.getOrNull()
        } ?: System.currentTimeMillis()
    }
}
