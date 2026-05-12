package com.anniversary.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "anniversaries")
data class Anniversary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val date: Long, // timestamp in millis (阳历时间戳)
    val type: AnniversaryType = AnniversaryType.CUSTOM,
    val note: String = "",
    val isRepeatYearly: Boolean = false,
    val reminderDays: Int = -1, // -1 means no reminder, otherwise days before
    val isLunar: Boolean = false, // 是否为农历
    val lunarMonth: Int = 0, // 农历月 (1-12)
    val lunarDay: Int = 0, // 农历日 (1-30)
    val lunarIsLeapMonth: Boolean = false, // 农历月是否为闰月
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

enum class AnniversaryType(val displayName: String) {
    BIRTHDAY("生日"),
    ANNIVERSARY("纪念日"),
    FESTIVAL("节日"),
    CUSTOM("自定义");

    companion object {
        fun fromOrdinal(ordinal: Int): AnniversaryType {
            return entries.getOrElse(ordinal) { CUSTOM }
        }
    }
}
