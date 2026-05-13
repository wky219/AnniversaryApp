package com.anniversary.app.util

import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility for exporting/importing anniversary data as JSON.
 */
object DataBackupUtils {

    private const val KEY_VERSION = "version"
    private const val KEY_EXPORT_TIME = "exportTime"
    private const val KEY_ANNIVERSARIES = "anniversaries"
    private const val CURRENT_VERSION = 1

    // Anniversary field keys
    private const val F_NAME = "name"
    private const val F_DATE = "date"
    private const val F_TYPE = "type"
    private const val F_NOTE = "note"
    private const val F_REPEAT_YEARLY = "isRepeatYearly"
    private const val F_REMINDER_DAYS = "reminderDays"
    private const val F_IS_LUNAR = "isLunar"
    private const val F_LUNAR_MONTH = "lunarMonth"
    private const val F_LUNAR_DAY = "lunarDay"
    private const val F_LUNAR_IS_LEAP = "lunarIsLeapMonth"
    private const val F_CREATED_AT = "createdAt"
    private const val F_UPDATED_AT = "updatedAt"

    /**
     * Serialize a list of anniversaries to JSON string.
     */
    fun toJson(anniversaries: List<Anniversary>): String {
        val root = JSONObject().apply {
            put(KEY_VERSION, CURRENT_VERSION)
            put(KEY_EXPORT_TIME, System.currentTimeMillis())
        }

        val array = JSONArray()
        for (ann in anniversaries) {
            val obj = JSONObject().apply {
                put(F_NAME, ann.name)
                put(F_DATE, ann.date)
                put(F_TYPE, ann.type.name)
                put(F_NOTE, ann.note)
                put(F_REPEAT_YEARLY, ann.isRepeatYearly)
                put(F_REMINDER_DAYS, ann.reminderDays)
                put(F_IS_LUNAR, ann.isLunar)
                put(F_LUNAR_MONTH, ann.lunarMonth)
                put(F_LUNAR_DAY, ann.lunarDay)
                put(F_LUNAR_IS_LEAP, ann.lunarIsLeapMonth)
                put(F_CREATED_AT, ann.createdAt)
                put(F_UPDATED_AT, ann.updatedAt)
            }
            array.put(obj)
        }
        root.put(KEY_ANNIVERSARIES, array)
        return root.toString(2)
    }

    /**
     * Deserialize JSON string to a list of anniversaries.
     * Returns null if the format is invalid.
     */
    fun fromJson(json: String): List<Anniversary>? {
        return try {
            val root = JSONObject(json)
            val version = root.optInt(KEY_VERSION, -1)
            if (version == -1) return null

            val array = root.optJSONArray(KEY_ANNIVERSARIES) ?: return null
            val result = mutableListOf<Anniversary>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = try {
                    AnniversaryType.valueOf(obj.optString(F_TYPE, "CUSTOM"))
                } catch (e: Exception) {
                    AnniversaryType.CUSTOM
                }

                val anniversary = Anniversary(
                    name = obj.optString(F_NAME, ""),
                    date = obj.optLong(F_DATE, 0L),
                    type = type,
                    note = obj.optString(F_NOTE, ""),
                    isRepeatYearly = obj.optBoolean(F_REPEAT_YEARLY, false),
                    reminderDays = obj.optInt(F_REMINDER_DAYS, -1),
                    isLunar = obj.optBoolean(F_IS_LUNAR, false),
                    lunarMonth = obj.optInt(F_LUNAR_MONTH, 0),
                    lunarDay = obj.optInt(F_LUNAR_DAY, 0),
                    lunarIsLeapMonth = obj.optBoolean(F_LUNAR_IS_LEAP, false),
                    createdAt = obj.optLong(F_CREATED_AT, System.currentTimeMillis()),
                    updatedAt = obj.optLong(F_UPDATED_AT, System.currentTimeMillis())
                )
                if (anniversary.name.isNotBlank()) {
                    result.add(anniversary)
                }
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}
