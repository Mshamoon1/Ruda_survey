package com.ruda.survey.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromMapToString(value: Map<String, Any?>): String = gson.toJson(value)

    @TypeConverter
    fun fromStringToMap(value: String): Map<String, Any?> {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromListToString(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun fromStringToList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}
