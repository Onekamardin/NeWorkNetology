package ru.netology.nework.domain.usecase

import javax.inject.Inject
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.JobCreateRequest
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.repository.NeWorkRepository

class ProfileUseCase @Inject constructor(private val repository: NeWorkRepository) {
    suspend fun user(id: Long): User = repository.user(id)
    suspend fun wall(id: Long): List<Post> = repository.wall(id)
    suspend fun jobs(id: Long): List<Job> = repository.jobs(id)
    suspend fun createJob(request: JobCreateRequest): Job = repository.createJob(request)
    suspend fun deleteJob(id: Long) = repository.deleteJob(id)
}
