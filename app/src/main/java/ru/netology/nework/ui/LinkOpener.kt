package ru.netology.nework.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import ru.netology.nework.R

fun openLink(context: Context, link: String) {
    val cleanLink = link.trim()
    if (cleanLink.isBlank()) return

    val linkWithProtocol = if (cleanLink.startsWith("http://", true) || cleanLink.startsWith("https://", true)) {
        cleanLink
    } else {
        "https://$cleanLink"
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkWithProtocol)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_open_error, Toast.LENGTH_SHORT).show()
    }
}
