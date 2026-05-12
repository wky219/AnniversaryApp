package com.anniversary.app.data.database

import androidx.room.TypeConverter
import com.anniversary.app.data.entity.AnniversaryType

class Converters {
    @TypeConverter
    fun fromAnniversaryType(type: AnniversaryType): Int {
        return type.ordinal
    }

    @TypeConverter
    fun toAnniversaryType(ordinal: Int): AnniversaryType {
        return AnniversaryType.fromOrdinal(ordinal)
    }
}
