package com.metube.app.playback

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

@Composable
fun rememberMetubeController(context: Context): MediaController? {
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MetubePlaybackService::class.java)
        )
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener(
            {
                runCatching {
                    controller = controllerFuture.get()
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            MediaController.releaseFuture(controllerFuture)
            controller = null
        }
    }

    return controller
}

fun Activity.enterMetubePipMode(videoWidth: Int, videoHeight: Int): Boolean {
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
        return false
    }
    val safeWidth = if (videoWidth > 0) videoWidth else 16
    val safeHeight = if (videoHeight > 0) videoHeight else 9
    val rawRatio = safeWidth.toFloat() / safeHeight.toFloat()
    val clampedRatio = rawRatio.coerceIn(0.42f, 2.38f)
    val aspectRatio = Rational((clampedRatio * 100).toInt(), 100)

    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(aspectRatio)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(true)
    }

    return enterPictureInPictureMode(builder.build())
}

fun Player.setSubtitleEnabled(enabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
        .build()
}

fun Player.selectTextTrack(groupIndex: Int, trackIndex: Int) {
    val textGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val targetGroup = textGroups.getOrNull(groupIndex) ?: return
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setOverrideForType(
            TrackSelectionOverride(targetGroup.mediaTrackGroup, listOf(trackIndex))
        )
        .build()
}
