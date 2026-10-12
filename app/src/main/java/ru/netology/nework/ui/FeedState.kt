package ru.netology.nework.ui

data class FeedState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)
