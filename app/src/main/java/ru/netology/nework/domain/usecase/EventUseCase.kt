package ru.netology.nework.domain.usecase

import javax.inject.Inject
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.repository.NeWorkRepository

class EventUseCase @Inject constructor(private val repository: NeWorkRepository) {
    suspend fun save(event: Event): Event = repository.saveEvent(event)
}

