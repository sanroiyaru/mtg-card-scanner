package com.example.mtgscanner

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class ScryfallClient {
    private val executor = Executors.newSingleThreadExecutor()

    fun lookup(
        setCode: String,
        collectorNumber: String,
        language: String,
        cardName: String,
        callback: (Result<CardRecord>) -> Unit
    ) {
        executor.execute {
            callback(runCatching {
                val endpoint = if (setCode.isNotBlank() && collectorNumber.isNotBlank()) {
                    val set = encode(setCode.lowercase())
                    val number = encode(collectorNumber)
                    val lang = encode(language.lowercase())
                    "https://api.scryfall.com/cards/$set/$number/$lang"
                } else if (cardName.isNotBlank()) {
                    "https://api.scryfall.com/cards/named?fuzzy=${encode(cardName)}"
                } else {
                    throw IllegalArgumentException("セットコードとコレクター番号、またはカード名を入力してください")
                }
                CardRecord.fromScryfall(request(endpoint))
            })
        }
    }

    private fun request(endpoint: String): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json;q=0.9,*/*;q=0.8")
            setRequestProperty("User-Agent", "MTGCardScanner/1.0 Android")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val details = runCatching { JSONObject(body).optString("details") }.getOrNull()
                throw IOException(details?.takeIf { it.isNotBlank() } ?: "Scryfall APIエラー ($code)")
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
