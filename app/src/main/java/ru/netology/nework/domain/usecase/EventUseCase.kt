package ru.netology.nework.domain.usecase

import javax.inject.Inject
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.EventCreateRequest
import ru.netology.nework.domain.repository.NeWorkRepository

class EventUseCase @Inject constructor(private val repository: NeWorkRepository) {
    suspend fun create(request: EventCreateRequest): Event = repository.createEvent(request)
}
