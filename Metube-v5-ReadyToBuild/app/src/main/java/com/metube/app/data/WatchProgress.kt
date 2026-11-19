package com.metube.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_progress")
data class WatchProgress(
    @PrimaryKey val videoId: String,
    val title: String,
    val uriString: String,
    val folderName: String,
    val seriesName: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long
)
