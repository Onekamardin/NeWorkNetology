package ru.netology.nework.domain.usecase

import javax.inject.Inject
import ru.netology.nework.domain.model.AuthenticationResult
import ru.netology.nework.domain.model.AvatarFile
import ru.netology.nework.domain.model.Credentials
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.repository.NeWorkRepository

class AuthUseCase @Inject constructor(private val repository: NeWorkRepository) {
    suspend fun login(login: String, password: String): AuthenticationResult =
        repository.authenticate(Credentials(login, password))

    suspend fun register(login: String, name: String, password: String, avatar: AvatarFile?): AuthenticationResult =
        repository.register(login, name, password, avatar)

    fun currentUser(): User? = repository.currentUser()
    fun isAuthenticated(): Boolean = currentUser() != null
    fun logout() = repository.logout()
}
