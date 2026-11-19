package com.metube.app.data

import android.net.Uri

data class VideoItem(
    val id: String,
    val title: String,
    val displayName: String,
    val uri: Uri,
    val filePath: String?,
    val folderName: String,
    val folderPath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val isSeries: Boolean = false,
    val seriesName: String = "",
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1
)
