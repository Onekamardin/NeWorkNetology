package ru.netology.nework.ui.utils

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import ru.netology.nework.BuildConfig

fun ImageView.loadServerImage(path: String?) {
    if (path.isNullOrBlank()) {
        Glide.with(this).clear(this)
        setImageResource(android.R.drawable.sym_def_app_icon)
        return
    }

    val url = if (path.startsWith("http://") || path.startsWith("https://")) {
        path
    } else {
        BuildConfig.BASE_URL.removeSuffix("api/") + path.removePrefix("/")
    }
    val image = GlideUrl(
        url,
        LazyHeaders.Builder().addHeader("Api-Key", BuildConfig.API_KEY).build(),
    )
    Glide.with(this)
        .load(image)
        .placeholder(android.R.drawable.sym_def_app_icon)
        .error(android.R.drawable.sym_def_app_icon)
        .centerCrop()
        .into(this)
}
