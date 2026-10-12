package ru.netology.nework.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import ru.netology.nework.domain.model.User

@Singleton
class AuthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("nework_auth", Context.MODE_PRIVATE)

    fun currentUser(): User? {
        if (!preferences.contains(KEY_LOGIN)) return null
        return User(
            id = preferences.getLong(KEY_ID, 0L),
            login = preferences.getString(KEY_LOGIN, "").orEmpty(),
            name = preferences.getString(KEY_NAME, "").orEmpty(),
            token = preferences.getString(KEY_TOKEN, null),
        )
    }

    fun save(user: User) {
        preferences.edit()
            .putLong(KEY_ID, user.id)
            .putString(KEY_LOGIN, user.login)
            .putString(KEY_NAME, user.name)
            .putString(KEY_TOKEN, user.token)
            .apply()
    }

    fun token(): String? = preferences.getString(KEY_TOKEN, null)

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_LOGIN = "login"
        const val KEY_NAME = "name"
        const val KEY_TOKEN = "token"
    }
}
