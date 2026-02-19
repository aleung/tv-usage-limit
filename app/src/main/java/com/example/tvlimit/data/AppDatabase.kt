package com.example.tvlimit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Profile::class, UsageLog::class], version = 2, exportSchema = false)
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
                .fallbackToDestructiveMigration() // For development simplicity
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
            // onCreate is only called if the DB file doesn't exist.
            // We can delegate to populateDatabase here too.
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.profileDao())
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // onOpen is called every time the DB is opened.
            // We check if data exists, if not, we populate.
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.profileDao())
                }
            }
        }

        suspend fun populateDatabase(dao: ProfileDao) {
            if (dao.getProfileCount() == 0) {
                // Default Child Profile
                dao.insertProfile(Profile(
                    name = "Child",
                    isRestricted = true,
                    pin = "0000",
                    dailyLimitMinutes = 120, // 2 Hours
                    sessionLimitMinutes = 45,
                    restDurationMinutes = 15,
                    restrictedApps = "com.google.android.youtube.tv,com.google.android.youtube"
                ))
                // Default Parent Profile
                dao.insertProfile(Profile(
                    name = "Parent",
                    isRestricted = false,
                    pin = "1234",
                    dailyLimitMinutes = -1,
                    sessionLimitMinutes = -1,
                    restDurationMinutes = 0,
                    restrictedApps = null
                ))
            }
        }
    }
}
