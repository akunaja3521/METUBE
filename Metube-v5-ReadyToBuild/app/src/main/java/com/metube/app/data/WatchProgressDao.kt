package com.metube.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY lastWatchedAt DESC")
    fun observeAllHistory(): Flow<List<WatchProgress>>

    @Query("SELECT * FROM watch_progress WHERE videoId = :videoId LIMIT 1")
    suspend fun getProgress(videoId: String): WatchProgress?

    @Query("SELECT * FROM watch_progress WHERE seriesName = :seriesName AND seriesName != '' ORDER BY lastWatchedAt DESC LIMIT 1")
    suspend fun getLastSeriesEpisode(seriesName: String): WatchProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgress)

    @Query("DELETE FROM watch_progress")
    suspend fun clearAll()
}
