package com.example.tvlimit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Int): Profile?

    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun getProfileByName(name: String): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)

    @Update
    suspend fun updateProfile(profile: Profile)

    // Usage Logs
    @Query("SELECT * FROM usage_logs WHERE profileId = :profileId AND date = :date LIMIT 1")
    suspend fun getUsageLog(profileId: Int, date: String): UsageLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageLog(usageLog: UsageLog)

    @Query("UPDATE usage_logs SET totalUsageMinutes = :minutes WHERE id = :id")
    suspend fun updateUsageMinutes(id: Int, minutes: Int)
}
