package com.example.tvlimit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Profile::class, UsageLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tv_limit_database"
                )
                .addCallback(DatabaseCallback(CoroutineScope(Dispatchers.IO)))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    val dao = database.profileDao()
                    // Default Child Profile
                    dao.insertProfile(Profile(
                        name = "Child",
                        isRestricted = true,
                        pin = "0000",
                        dailyLimitMinutes = 120, // 2 Hours
                        sessionLimitMinutes = 45,
                        restDurationMinutes = 15
                    ))
                    // Default Parent Profile
                    dao.insertProfile(Profile(
                        name = "Parent",
                        isRestricted = false,
                        pin = "1234",
                        dailyLimitMinutes = -1,
                        sessionLimitMinutes = -1,
                        restDurationMinutes = 0
                    ))
                }
            }
        }
    }
}
