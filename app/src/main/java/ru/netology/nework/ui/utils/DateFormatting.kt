package ru.netology.nework.ui.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
private val jobDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

fun formatServerDate(value: String): String {
    if (value.isBlank()) return ""
    val zone = ZoneId.systemDefault()

    return runCatching { Instant.parse(value).atZone(zone).format(displayDateFormatter) }
        .recoverCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone).format(displayDateFormatter) }
        .recoverCatching { LocalDateTime.parse(value).format(displayDateFormatter) }
        .getOrDefault(value)
}

fun formatJobDate(value: String): String {
    if (value.isBlank()) return ""
    return runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate().format(jobDateFormatter) }
        .recoverCatching { OffsetDateTime.parse(value).toLocalDate().format(jobDateFormatter) }
        .recoverCatching { LocalDateTime.parse(value).toLocalDate().format(jobDateFormatter) }
        .recoverCatching { LocalDate.parse(value).format(jobDateFormatter) }
        .getOrDefault(value)
}
