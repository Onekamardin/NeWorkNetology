package ru.netology.nework.domain.model

sealed interface AuthenticationResult {
    data class Success(val user: User) : AuthenticationResult
    data class HttpError(val code: Int) : AuthenticationResult
}

data class AvatarFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

class NetworkException(
    val code: Int? = null,
    override val message: String,
) : Exception(message)
