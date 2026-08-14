package com.example.mtgscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object ImageLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(url: String?, imageView: ImageView) {
        imageView.tag = url
        imageView.setImageDrawable(null)
        if (url.isNullOrBlank()) return
        cache.get(url)?.let {
            imageView.setImageBitmap(it)
            return
        }
        executor.execute {
            val bitmap = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "MTGCardScanner/1.0 Android")
                try {
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull() ?: return@execute
            cache.put(url, bitmap)
            imageView.post {
                if (imageView.tag == url) imageView.setImageBitmap(bitmap)
            }
        }
    }
}
