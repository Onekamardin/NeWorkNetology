package ru.netology.nework.ui

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())

/** Преобразует дату сервера в формат из ТЗ. Если строка некорректна, возвращает её как есть. */
fun formatServerDate(value: String): String {
    if (value.isBlank()) return ""
    val zone = ZoneId.systemDefault()

    return runCatching { Instant.parse(value).atZone(zone).format(displayDateFormatter) }
        .recoverCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone).format(displayDateFormatter) }
        .recoverCatching { LocalDateTime.parse(value).format(displayDateFormatter) }
        .getOrDefault(value)
}
