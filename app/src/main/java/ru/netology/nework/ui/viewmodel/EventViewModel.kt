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

    fun setEventDate(millis: Long) { _eventDateMillis.value = millis }
    fun setSpeakerSelected(userId: Long, selected: Boolean) {
        val ids = _selectedSpeakerIds.value.toMutableSet()
        if (selected) ids.add(userId) else ids.remove(userId)
        _selectedSpeakerIds.value = ids
    }
    fun resetEventDraft() {
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
    ) {
        if (_createEvent.value == CreateEventState.Loading) return
        if (content.isBlank()) { _createEvent.value = CreateEventState.Error("Введите описание события"); return }
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        createEvent(EventCreateRequest(
            content = content.trim(), datetime = formatter.format(Date(dateMillis)),
            type = if (offline) "OFFLINE" else "ONLINE", speakerIds = speakerIds,
            link = link?.trim()?.takeIf(String::isNotBlank), coords = coords,
        ), mediaFile)
    }

    private fun createEvent(request: EventCreateRequest, mediaFile: MediaFile?) = viewModelScope.launch {
        _createEvent.value = CreateEventState.Loading
        runCatching {
            val attachment = mediaFile?.let { mediaUseCase.upload(it) }
            eventUseCase.create(request.copy(attachment = attachment))
        }
            .onSuccess { _createEvent.value = CreateEventState.Success }
            .onFailure { error ->
                val message = if (error is NetworkException) error.message else error.message ?: "Не удалось создать событие"
                _createEvent.value = CreateEventState.Error(message)
            }
    }

    fun clearCreateEventState() { _createEvent.value = CreateEventState.Idle }
}
