package com.stulab.studylockapp.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class WordConverters {

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return JSONArray(value).toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        val list = mutableListOf<String>()
        val jsonArray = JSONArray(value)
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    @TypeConverter
    fun fromRelatedWordList(value: List<RelatedWord>): String {
        val jsonArray = JSONArray()
        value.forEach {
            val obj = JSONObject()
            obj.put("word", it.word)
            obj.put("note", it.note)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toRelatedWordList(value: String): List<RelatedWord> {
        if (value.isEmpty()) return emptyList()
        val list = mutableListOf<RelatedWord>()
        val jsonArray = JSONArray(value)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(RelatedWord(obj.getString("word"), obj.getString("note")))
        }
        return list
    }

    @TypeConverter
    fun fromSpellingStatus(status: SpellingStatus): String {
        return status.name
    }

    @TypeConverter
    fun toSpellingStatus(value: String): SpellingStatus {
        return try {
            SpellingStatus.valueOf(value)
        } catch (e: Exception) {
            SpellingStatus.NOT_STARTED
        }
    }
}
