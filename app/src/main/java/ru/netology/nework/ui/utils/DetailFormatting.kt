package ru.netology.nework.ui.utils

import android.content.Context
import ru.netology.nework.R
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.model.UserPreview
import ru.netology.nework.ui.viewmodel.FeedState

fun peopleNames(context: Context, ids: List<Long>, previews: Map<Long, UserPreview>, users: List<User>): String {
    if (ids.isEmpty()) return context.getString(R.string.detail_no_people)
    return ids.joinToString("\n") { id ->
        val user = users.firstOrNull { it.id == id }
        previews[id]?.name?.takeIf(String::isNotBlank)
            ?: user?.name?.takeIf(String::isNotBlank)
            ?: user?.login?.takeIf(String::isNotBlank)?.let { "@$it" }
            ?: context.getString(R.string.detail_user_fallback, id)
    }
}

fun authorJobText(context: Context, state: FeedState<Job>, fallback: String?): String {
    val latest = state.items.maxByOrNull { it.start }
    return when {
        latest != null -> "${latest.name} — ${latest.position}"
        !fallback.isNullOrBlank() && (state.loading || state.error != null) -> fallback
        state.loading -> context.getString(R.string.detail_job_loading)
        state.error != null -> context.getString(R.string.detail_job_load_error)
        else -> context.getString(R.string.detail_no_job)
    }
}
