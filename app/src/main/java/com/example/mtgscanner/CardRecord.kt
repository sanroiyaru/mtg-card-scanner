package com.example.mtgscanner

import org.json.JSONObject

data class CardRecord(
    val scryfallId: String,
    val name: String,
    val printedName: String?,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val language: String,
    val imageUrl: String?,
    val usd: String?,
    val usdFoil: String?,
    val quantity: Int = 1,
    val foil: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = printedName?.takeIf { it.isNotBlank() } ?: name

    val displayPrice: String
        get() {
            val value = if (foil) usdFoil ?: usd else usd
            return value?.let { "$$it" } ?: "価格情報なし"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("scryfallId", scryfallId)
        put("name", name)
        put("printedName", printedName)
        put("setCode", setCode)
        put("setName", setName)
        put("collectorNumber", collectorNumber)
        put("language", language)
        put("imageUrl", imageUrl)
        put("usd", usd)
        put("usdFoil", usdFoil)
        put("quantity", quantity)
        put("foil", foil)
        put("addedAt", addedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): CardRecord = CardRecord(
            scryfallId = json.getString("scryfallId"),
            name = json.getString("name"),
            printedName = json.nullableString("printedName"),
            setCode = json.getString("setCode"),
            setName = json.getString("setName"),
            collectorNumber = json.getString("collectorNumber"),
            language = json.optString("language", "en"),
            imageUrl = json.nullableString("imageUrl"),
            usd = json.nullableString("usd"),
            usdFoil = json.nullableString("usdFoil"),
            quantity = json.optInt("quantity", 1),
            foil = json.optBoolean("foil", false),
            addedAt = json.optLong("addedAt", System.currentTimeMillis())
        )

        fun fromScryfall(json: JSONObject): CardRecord {
            val imageUris = when {
                json.has("image_uris") -> json.getJSONObject("image_uris")
                json.optJSONArray("card_faces")?.length()?.let { it > 0 } == true ->
                    json.getJSONArray("card_faces").getJSONObject(0).optJSONObject("image_uris")
                else -> null
            }
            val prices = json.optJSONObject("prices") ?: JSONObject()
            return CardRecord(
                scryfallId = json.getString("id"),
                name = json.getString("name"),
                printedName = json.nullableString("printed_name"),
                setCode = json.getString("set").uppercase(),
                setName = json.getString("set_name"),
                collectorNumber = json.getString("collector_number"),
                language = json.optString("lang", "en"),
                imageUrl = imageUris?.nullableString("normal")
                    ?: imageUris?.nullableString("large"),
                usd = prices.nullableString("usd"),
                usdFoil = prices.nullableString("usd_foil")
            )
        }

        private fun JSONObject.nullableString(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}
