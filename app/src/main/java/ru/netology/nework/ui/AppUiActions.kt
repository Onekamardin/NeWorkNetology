package ru.netology.nework.ui

import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.Event

interface AppUiActions {
    fun showMessage(message: String)
    fun showAuthRequired(message: String? = null)
    fun confirmDeleteJob(job: Job)
    fun confirmDeletePost(post: Post)
    fun confirmDeleteEvent(event: Event)
    fun setScreenTitle(title: String, subtitle: String? = null, showBottomNavigation: Boolean = false, showCreateButton: Boolean = false)
    fun updateFeed(tabId: Int, title: String, showCreateButton: Boolean)
}

interface SaveableScreen {
    fun save()
}
