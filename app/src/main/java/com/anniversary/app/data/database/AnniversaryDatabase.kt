package com.anniversary.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anniversary.app.data.dao.AnniversaryDao
import com.anniversary.app.data.entity.Anniversary

@Database(entities = [Anniversary::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AnniversaryDatabase : RoomDatabase() {

    abstract fun anniversaryDao(): AnniversaryDao

    companion object {
        @Volatile
        private var INSTANCE: AnniversaryDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE anniversaries ADD COLUMN isLunar INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE anniversaries ADD COLUMN lunarMonth INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE anniversaries ADD COLUMN lunarDay INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE anniversaries ADD COLUMN lunarIsLeapMonth INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE anniversaries ADD COLUMN username TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AnniversaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnniversaryDatabase::class.java,
                    "anniversary_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
