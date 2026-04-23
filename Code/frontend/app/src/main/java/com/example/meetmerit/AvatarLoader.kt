package com.example.meetmerit

import android.graphics.BitmapFactory
import android.view.View
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

fun ShapeableImageView.loadAvatar(
    avatarUrl: String?,
    fallbackView: TextView? = null,
) {
    if (avatarUrl.isNullOrBlank()) {
        setImageResource(android.R.drawable.ic_menu_myplaces)
        fallbackView?.visibility = View.VISIBLE
        visibility = if (fallbackView == null) View.VISIBLE else View.GONE
        tag = null
        return
    }

    fallbackView?.visibility = View.GONE
    visibility = View.VISIBLE
    setImageResource(android.R.drawable.ic_menu_myplaces)
    tag = avatarUrl

    CoroutineScope(Dispatchers.IO).launch {
        val bitmap = runCatching {
            val connection = URL(avatarUrl).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        post {
            if (tag == avatarUrl && bitmap != null) {
                setImageBitmap(bitmap)
            }
        }
    }
}
