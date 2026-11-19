package com.metube.app.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.io.File

object SubtitleHelper {

    private val supportedExtensions = setOf("srt", "ass", "ssa", "vtt", "ttml")

    fun resolveMimeType(fileNameOrUri: String): String {
        val ext = fileNameOrUri.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt" -> MimeTypes.TEXT_VTT
            "ttml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    fun buildSubtitleConfiguration(
        uri: Uri,
        label: String? = null,
        language: String = "id"
    ): MediaItem.SubtitleConfiguration {
        val fileName = label ?: uri.lastPathSegment ?: "Subtitle"
        return MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(resolveMimeType(fileName))
            .setLanguage(language)
            .setLabel(fileName)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    fun findMatchingLocalSubtitles(videoPath: String?): List<MediaItem.SubtitleConfiguration> {
        if (videoPath.isNullOrBlank()) return emptyList()
        val videoFile = File(videoPath)
        val parentDir = videoFile.parentFile ?: return emptyList()
        if (!parentDir.exists() || !parentDir.isDirectory) return emptyList()

        val baseName = videoFile.nameWithoutExtension
        val matches = parentDir.listFiles { file ->
            file.isFile &&
                file.extension.lowercase() in supportedExtensions &&
                file.nameWithoutExtension.startsWith(baseName, ignoreCase = true)
        } ?: return emptyList()

        return matches.sortedBy { it.name.length }.map { subFile ->
            buildSubtitleConfiguration(
                uri = Uri.fromFile(subFile),
                label = subFile.name
            )
        }
    }
}
