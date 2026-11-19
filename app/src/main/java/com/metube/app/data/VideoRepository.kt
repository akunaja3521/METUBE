package com.metube.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(private val context: Context) {

    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "ts", "mpeg", "mpg", "m4v", "flv", "wmv"
    )

    private val seasonEpisodeRegex = Regex(
        """(?i)(?:s(\d{1,2})[\s._-]*e(\d{1,3})|season[\s._-]*(\d{1,2})[\s._-]*episode[\s._-]*(\d{1,3})|\b(\d{1,2})x(\d{1,3})\b|(?:ep|episode|eps)[\s._-]*(\d{1,3}))"""
    )

    suspend fun loadAllVideos(manualFolderUris: Set<String>): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, VideoItem>()

        // 1. Scan MediaStore otomatis
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: continue
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val parentFolder = path?.let { File(it).parentFile?.name }
                        ?: (if (bucketCol >= 0) cursor.getString(bucketCol) else null)
                        ?: "Internal"
                    val parentPath = path?.let { File(it).parent } ?: parentFolder

                    val parsed = parseSeriesMetadata(displayName, parentFolder, parentPath)
                    val item = VideoItem(
                        id = "ms_$id",
                        title = displayName.substringBeforeLast('.'),
                        displayName = displayName,
                        uri = contentUri,
                        filePath = path,
                        folderName = parentFolder,
                        folderPath = parentPath,
                        durationMs = cursor.getLong(durCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAddedSec = cursor.getLong(dateCol),
                        isSeries = parsed.isSeries,
                        seriesName = parsed.seriesName,
                        seasonNumber = parsed.season,
                        episodeNumber = parsed.episode
                    )
                    val key = path ?: contentUri.toString()
                    result[key] = item
                }
            }
        }

        // 2. Scan folder manual via SAF (DocumentFile)
        manualFolderUris.forEach { uriStr ->
            runCatching {
                val treeUri = Uri.parse(uriStr)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.canRead()) {
                    scanDocumentTree(rootDoc, rootDoc.name ?: "Folder Manual", result)
                }
            }
        }

        result.values.sortedWith(
            compareBy<VideoItem> { it.folderName.lowercase() }
                .thenBy { it.seasonNumber }
                .thenBy { it.episodeNumber }
                .thenBy { it.title.lowercase() }
        )
    }

    private fun scanDocumentTree(
        dir: DocumentFile,
        folderLabel: String,
        out: MutableMap<String, VideoItem>
    ) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                scanDocumentTree(child, child.name ?: folderLabel, out)
            } else if (child.isFile) {
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in videoExtensions) {
                    val uri = child.uri
                    val key = uri.toString()
                    if (!out.containsKey(key)) {
                        val parsed = parseSeriesMetadata(name, folderLabel, dir.uri.toString())
                        out[key] = VideoItem(
                            id = "saf_${uri.hashCode()}",
                            title = name.substringBeforeLast('.'),
                            displayName = name,
                            uri = uri,
                            filePath = null,
                            folderName = folderLabel,
                            folderPath = dir.uri.toString(),
                            durationMs = 0L,
                            sizeBytes = child.length(),
                            dateAddedSec = child.lastModified() / 1000L,
                            isSeries = parsed.isSeries,
                            seriesName = parsed.seriesName,
                            seasonNumber = parsed.season,
                            episodeNumber = parsed.episode
                        )
                    }
                }
            }
        }
    }

    private data class ParsedSeries(
        val isSeries: Boolean,
        val seriesName: String,
        val season: Int,
        val episode: Int
    )

    private fun parseSeriesMetadata(
        fileName: String,
        folderName: String,
        folderPath: String
    ): ParsedSeries {
        val baseName = fileName.substringBeforeLast('.')
        val match = seasonEpisodeRegex.find(baseName)
        val folderSeasonMatch = Regex("""(?i)(?:season|s)[\s._-]*(\d{1,2})""").find(folderName)

        if (match != null) {
            val s1 = match.groups[1]?.value?.toIntOrNull()
            val e1 = match.groups[2]?.value?.toIntOrNull()
            val s2 = match.groups[3]?.value?.toIntOrNull()
            val e2 = match.groups[4]?.value?.toIntOrNull()
            val s3 = match.groups[5]?.value?.toIntOrNull()
            val e3 = match.groups[6]?.value?.toIntOrNull()
            val e4 = match.groups[7]?.value?.toIntOrNull()

            val season = s1 ?: s2 ?: s3 ?: folderSeasonMatch?.groups?.get(1)?.value?.toIntOrNull() ?: 1
            val episode = e1 ?: e2 ?: e3 ?: e4 ?: 1

            val cleanSeries = if (folderSeasonMatch != null) {
                File(folderPath).parentFile?.name ?: folderName
            } else {
                val prefix = baseName.substring(0, match.range.first)
                    .replace(Regex("""[._-]+$"""), "")
                    .trim()
                if (prefix.length >= 2) prefix else folderName
            }

            return ParsedSeries(
                isSeries = true,
                seriesName = cleanSeries,
                season = season,
                episode = episode
            )
        }

        return ParsedSeries(
            isSeries = false,
            seriesName = "",
            season = 1,
            episode = 1
        )
    }
}
