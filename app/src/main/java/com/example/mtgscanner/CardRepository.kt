package com.example.mtgscanner

import android.content.Context
import org.json.JSONArray

class CardRepository(context: Context) {
    private val preferences = context.getSharedPreferences("mtg_collection", Context.MODE_PRIVATE)

    fun getAll(): List<CardRecord> {
        val stored = preferences.getString(KEY_CARDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    add(CardRecord.fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addOrIncrement(card: CardRecord) {
        val cards = getAll().toMutableList()
        val index = cards.indexOfFirst { it.scryfallId == card.scryfallId && it.foil == card.foil }
        if (index >= 0) {
            cards[index] = cards[index].copy(quantity = cards[index].quantity + 1)
        } else {
            cards.add(card)
        }
        save(cards)
    }

    fun updateQuantity(card: CardRecord, quantity: Int) {
        val cards = getAll().toMutableList()
        val index = cards.indexOfFirst { it.scryfallId == card.scryfallId && it.foil == card.foil }
        if (index < 0) return
        if (quantity <= 0) cards.removeAt(index) else cards[index] = cards[index].copy(quantity = quantity)
        save(cards)
    }

    fun delete(card: CardRecord) {
        save(getAll().filterNot { it.scryfallId == card.scryfallId && it.foil == card.foil })
    }

    private fun save(cards: List<CardRecord>) {
        val array = JSONArray()
        cards.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_CARDS, array.toString()).apply()
    }

    companion object {
        private const val KEY_CARDS = "cards"
    }
}
