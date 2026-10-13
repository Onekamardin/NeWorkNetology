package ru.netology.nework.domain.usecase

import javax.inject.Inject
import ru.netology.nework.domain.model.Attachment
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.domain.repository.NeWorkRepository

class MediaUseCase @Inject constructor(private val repository: NeWorkRepository) {
    suspend fun upload(file: MediaFile): Attachment = repository.uploadMedia(file)
}
