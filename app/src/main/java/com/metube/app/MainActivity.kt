package com.metube.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.metube.app.data.*
import com.metube.app.playback.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MetubePage(val label: String) {
    HOME("Home"),
    ALL_VIDEOS("Semua Video"),
    SERIES("Serial"),
    MOVIES("Film"),
    FOLDERS("Folder"),
    HISTORY("Riwayat"),
    AUDIO("Audio"),
    URL_VIDEO("URL Video"),
    SETTINGS("Pengaturan")
}

class MainActivity : ComponentActivity() {

    private var activePlayingState by mutableStateOf(false)
    private var audioOnlyState by mutableStateOf(false)
    private var currentVideoWidth by mutableIntStateOf(16)
    private var currentVideoHeight by mutableIntStateOf(9)
    private var isInPipState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { AppPreferences(context) }
            val settings by prefs.settingsFlow.collectAsState(initial = UserSettings())
            val systemDark = isSystemInDarkTheme()

            val useDarkTheme = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            val colorScheme = if (useDarkTheme) {
                darkColorScheme(
                    primary = Color(0xFF9C4DFF),
                    onPrimary = Color.White,
                    secondary = Color(0xFFB388FF),
                    background = Color(0xFF0E0B16),
                    surface = Color(0xFF171224),
                    surfaceVariant = Color(0xFF231C35),
                    onBackground = Color(0xFFF3EFFA),
                    onSurface = Color(0xFFF3EFFA)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF6A1B9A),
                    onPrimary = Color.White,
                    secondary = Color(0xFF8E24AA),
                    background = Color(0xFFF8F5FC),
                    surface = Color.White,
                    surfaceVariant = Color(0xFFEDE7F6),
                    onBackground = Color(0xFF1A1423),
                    onSurface = Color(0xFF1A1423)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MetubeRootScreen(
                        prefs = prefs,
                        settings = settings,
                        isInPipMode = isInPipState,
                        onPlaybackMetaChanged = { playing, audioOnly, w, h ->
                            activePlayingState = playing
                            audioOnlyState = audioOnly
                            if (w > 0) currentVideoWidth = w
                            if (h > 0) currentVideoHeight = h
                        },
                        onRequestPip = {
                            enterMetubePipMode(currentVideoWidth, currentVideoHeight)
                        }
                    )
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (activePlayingState && !audioOnlyState) {
            enterMetubePipMode(currentVideoWidth, currentVideoHeight)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState = isInPictureInPictureMode
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MetubeRootScreen(
    prefs: AppPreferences,
    settings: UserSettings,
    isInPipMode: Boolean,
    onPlaybackMetaChanged: (Boolean, Boolean, Int, Int) -> Unit,
    onRequestPip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VideoRepository(context) }
    val db = remember { MetubeDatabase.getInstance(context) }
    val dao = remember { db.watchProgressDao() }
    val historyList by dao.observeAllHistory().collectAsState(initial = emptyList())

    val controller = rememberMetubeController(context)

    var hasPermission by remember {
        mutableStateOf(checkMediaPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.any { it } || checkMediaPermission(context)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms += Manifest.permission.READ_MEDIA_VIDEO
                perms += Manifest.permission.POST_NOTIFICATIONS
            } else {
                perms += Manifest.permission.READ_EXTERNAL_STORAGE
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    suspend fun refreshVideos() {
        isLoading = true
        videos = repository.loadAllVideos(settings.manualFolderUris)
        isLoading = false
    }

    LaunchedEffect(hasPermission, settings.manualFolderUris) {
        if (hasPermission) {
            refreshVideos()
        }
    }

    var currentPage by remember { mutableStateOf(MetubePage.HOME) }
    var activePlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var currentVideo by remember { mutableStateOf<VideoItem?>(null) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var extraSubtitleUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                prefs.addManualFolderUri(treeUri.toString())
            }
        }
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { subUri ->
        if (subUri != null && currentVideo != null) {
            extraSubtitleUris = extraSubtitleUris + subUri
            val vid = currentVideo!!
            val pos = controller?.currentPosition ?: 0L
            playVideoOnController(
                controller = controller,
                video = vid,
                startPositionMs = pos,
                extraSubs = extraSubtitleUris,
                subtitleEnabled = settings.subtitleEnabled
            )
            Toast.makeText(context, "Subtitle eksternal dimuat", Toast.LENGTH_SHORT).show()
        }
    }

    fun startPlaying(video: VideoItem, playlist: List<VideoItem>, audioOnly: Boolean = false) {
        scope.launch {
            val saved = dao.getProgress(video.id)
            val resumePos = if (saved != null && saved.durationMs > 0 && saved.positionMs < saved.durationMs - 5000L) {
                saved.positionMs
            } else 0L

            activePlaylist = playlist
            currentVideo = video
            isAudioOnly = audioOnly
            extraSubtitleUris = emptyList()

            playVideoOnController(
                controller = controller,
                video = video,
                startPositionMs = resumePos,
                extraSubs = emptyList(),
                subtitleEnabled = settings.subtitleEnabled
            )
        }
    }

    DisposableEffect(controller, currentVideo, activePlaylist, settings.autoplayNext) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackMetaChanged(isPlaying, isAudioOnly, 0, 0)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                onPlaybackMetaChanged(controller?.isPlaying == true, isAudioOnly, videoSize.width, videoSize.height)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && settings.autoplayNext) {
                    val cur = currentVideo ?: return
                    val idx = activePlaylist.indexOfFirst { it.id == cur.id }
                    if (idx >= 0 && idx + 1 < activePlaylist.size) {
                        val nextVideo = activePlaylist[idx + 1]
                        startPlaying(nextVideo, activePlaylist, isAudioOnly)
                    }
                }
            }
        }
        controller?.addListener(listener)
        onDispose {
            controller?.removeListener(listener)
        }
    }

    LaunchedEffect(controller, currentVideo) {
        while (currentVideo != null && controller != null) {
            delay(4000L)
            val vid = currentVideo ?: break
            val pos = controller.currentPosition
            val dur = controller.duration.coerceAtLeast(vid.durationMs)
            if (pos > 1000L) {
                dao.upsert(
                    WatchProgress(
                        videoId = vid.id,
                        title = vid.title,
                        uriString = vid.uri.toString(),
                        folderName = vid.folderName,
                        seriesName = vid.seriesName,
                        seasonNumber = vid.seasonNumber,
                        episodeNumber = vid.episodeNumber,
                        positionMs = pos,
                        durationMs = dur,
                        lastWatchedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    if (currentVideo != null && !isAudioOnly) {
        val vid = currentVideo!!
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (controller != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = true
                            keepScreenOn = true
                        }
                    },
                    update = { playerView ->
                        playerView.player = controller
                        playerView.subtitleView?.apply {
                            setApplyEmbeddedStyles(false)
                            setApplyEmbeddedFontSizes(false)
                            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, settings.subtitleSizeSp)
                            setBottomPaddingFraction(settings.subtitleBottomPadding)
                            setStyle(
                                CaptionStyleCompat(
                                    settings.subtitleColorArgb,
                                    android.graphics.Color.argb(140, 0, 0, 0),
                                    android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    Typeface.DEFAULT_BOLD
                                )
                            )
                        }
                    }
                )
            }

            if (!isInPipMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        currentVideo = null
                        onPlaybackMetaChanged(false, false, 16, 9)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                    Text(
                        text = vid.title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = {
                        val nextState = !settings.subtitleEnabled
                        scope.launch { prefs.updateSubtitleConfig(enabled = nextState) }
                        controller?.setSubtitleEnabled(nextState)
                    }) {
                        Icon(
                            if (settings.subtitleEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                            contentDescription = "Subtitle ON/OFF",
                            tint = if (settings.subtitleEnabled) Color(0xFFB388FF) else Color.White
                        )
                    }
                    IconButton(onClick = { showSubtitleDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Pengaturan Subtitle", tint = Color.White)
                    }
                    IconButton(onClick = {
                        val pos = controller?.currentPosition ?: 0L
                        val res = SnapshotHelper.captureFrameToGallery(context, vid.uri, pos, vid.title)
                        res.onSuccess {
                            Toast.makeText(context, "Snapshot disimpan: $it", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Gagal snapshot: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Take Snapshot", tint = Color.White)
                    }
                    IconButton(onClick = {
                        isAudioOnly = true
                        onPlaybackMetaChanged(controller?.isPlaying == true, true, 16, 9)
                    }) {
                        Icon(Icons.Default.Headphones, contentDescription = "Mode Audio Saja", tint = Color.White)
                    }
                    IconButton(onClick = onRequestPip) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                    }
                }
            }
        }

        if (showSubtitleDialog) {
            SubtitleSettingsDialog(
                settings = settings,
                controller = controller,
                onDismiss = { showSubtitleDialog = false },
                onPickExternalSubtitle = {
                    subtitlePickerLauncher.launch(arrayOf("*/*"))
                },
                onUpdateConfig = { size, color, padding, sync ->
                    scope.launch {
                        prefs.updateSubtitleConfig(
                            sizeSp = size,
                            colorArgb = color,
                            bottomPadding = padding,
                            syncOffsetMs = sync
                        )
                    }
                }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "METUBE",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val nextMode = if (settings.viewMode == ViewMode.POSTER) ViewMode.THUMBNAIL else ViewMode.POSTER
                        scope.launch { prefs.setViewMode(nextMode) }
                    }) {
                        Icon(
                            if (settings.viewMode == ViewMode.POSTER) Icons.Default.ViewModule else Icons.Default.ViewList,
                            contentDescription = "Ganti Poster/Thumbnail"
                        )
                    }
                    IconButton(onClick = { scope.launch { refreshVideos() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = MetubePage.entries.indexOf(currentPage),
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    MetubePage.entries.forEach { page ->
                        Tab(
                            selected = currentPage == page,
                            onClick = { currentPage = page },
                            text = { Text(page.label) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (currentVideo != null && isAudioOnly) {
                val vid = currentVideo!!
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode Audio Saja", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(vid.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = {
                            if (controller?.isPlaying == true) controller.pause() else controller?.play()
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause")
                        }
                        TextButton(onClick = {
                            isAudioOnly = false
                            onPlaybackMetaChanged(controller?.isPlaying == true, false, 16, 9)
                        }) {
                            Text("Video")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (currentPage) {
                    MetubePage.HOME -> HomeScreen(
                        videos = videos,
                        history = historyList,
                        viewMode = settings.viewMode,
                        onPlayVideo = { vid, list -> startPlaying(vid, list, false) }
                    )
                    MetubePage.ALL_VIDEOS -> VideoCollectionScreen(
                        title = "Semua Video (${videos.size})",
                        videos = videos,
                        viewMode = settings.viewMode,
                        onPlayVideo = { vid -> startPlaying(vid, videos, false) }
                    )
                    MetubePage.SERIES -> SeriesScreen(
                        videos = videos.filter { it.isSeries },
                        history = historyList,
                        onPlayEpisode = { ep, seasonEpisodes -> startPlaying(ep, seasonEpisodes, false) }
                    )
                    MetubePage.MOVIES -> VideoCollectionScreen(
                        title = "Film (${videos.count { !it.isSeries }})",
                        videos = videos.filter { !it.isSeries },
                        viewMode = settings.viewMode,
                        onPlayVideo = { vid -> startPlaying(vid, videos.filter { !it.isSeries }, false) }
                    )
                    MetubePage.FOLDERS -> FoldersScreen(
                        videos = videos,
                        manualFolderUris = settings.manualFolderUris,
                        onAddFolder = { folderPickerLauncher.launch(null) },
                        onRemoveFolder = { uri -> scope.launch { prefs.removeManualFolderUri(uri) } },
                        onPlayVideo = { vid, folderList -> startPlaying(vid, folderList, false) }
                    )
                    MetubePage.HISTORY -> HistoryScreen(
                        history = historyList,
                        videos = videos,
                        onResume = { vid -> startPlaying(vid, videos, false) },
                        onClear = { scope.launch { dao.clearAll() } }
                    )
                    MetubePage.AUDIO -> AudioModeScreen(
                        videos = videos,
                        onPlayAudio = { vid -> startPlaying(vid, videos, true) }
                    )
                    MetubePage.URL_VIDEO -> OfficialUrlPlayerScreen()
                    MetubePage.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onThemeChange = { scope.launch { prefs.setThemeMode(it) } },
                        onViewModeChange = { scope.launch { prefs.setViewMode(it) } },
                        onAutoplayChange = { scope.launch { prefs.setAutoplayNext(it) } },
                        onAddFolder = { folderPickerLauncher.launch(null) }
                    )
                }
            }
        }
    }
}

private fun checkMediaPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
}

private fun playVideoOnController(
    controller: MediaController?,
    video: VideoItem,
    startPositionMs: Long,
    extraSubs: List<Uri>,
    subtitleEnabled: Boolean
) {
    val player = controller ?: return
    val autoSubs = SubtitleHelper.findMatchingLocalSubtitles(video.filePath)
    val manualSubs = extraSubs.map { SubtitleHelper.buildSubtitleConfiguration(it) }
    val allSubs = autoSubs + manualSubs

    val mediaItem = MediaItem.Builder()
        .setMediaId(video.id)
        .setUri(video.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(video.title)
                .setAlbumTitle(video.folderName)
                .build()
        )
        .setSubtitleConfigurations(allSubs)
        .build()

    player.setMediaItem(mediaItem, startPositionMs)
    player.setSubtitleEnabled(subtitleEnabled)
    player.prepare()
    player.play()
}

@Composable
fun HomeScreen(
    videos: List<VideoItem>,
    history: List<WatchProgress>,
    viewMode: ViewMode,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (history.isNotEmpty()) {
            item {
                Text("Lanjutkan Menonton", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(history.take(5)) { prog ->
                val matched = videos.find { it.id == prog.videoId }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (matched != null) onPlayVideo(matched, videos)
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prog.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Folder: ${prog.folderName} • ${formatDuration(prog.positionMs)} / ${formatDuration(prog.durationMs)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Video Terbaru (${videos.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(videos.take(20)) { video ->
            VideoRowCard(video = video, viewMode = viewMode, onClick = { onPlayVideo(video, videos) })
        }
    }
}

@Composable
fun VideoCollectionScreen(
    title: String,
    videos: List<VideoItem>,
    viewMode: ViewMode,
    onPlayVideo: (VideoItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (viewMode == ViewMode.POSTER) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(videos) { video ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPlayVideo(video) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(40.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(video.folderName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(videos) { video ->
                    VideoRowCard(video = video, viewMode = viewMode, onClick = { onPlayVideo(video) })
                }
            }
        }
    }
}

@Composable
fun VideoRowCard(video: VideoItem, viewMode: ViewMode, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(if (viewMode == ViewMode.POSTER) 56.dp else 72.dp, 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    "${video.folderName} • ${formatDuration(video.durationMs)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SeriesScreen(
    videos: List<VideoItem>,
    history: List<WatchProgress>,
    onPlayEpisode: (VideoItem, List<VideoItem>) -> Unit
) {
    val groupedBySeries = remember(videos) {
        videos.groupBy { it.seriesName.ifBlank { it.folderName } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Serial Terdeteksi (${groupedBySeries.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        groupedBySeries.forEach { (seriesTitle, episodes) ->
            val sortedEps = episodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
            val lastProg = history.firstOrNull { it.seriesName == seriesTitle }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(seriesTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (lastProg != null) {
                            Text(
                                "Terakhir ditonton: S${lastProg.seasonNumber}E${lastProg.episodeNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        sortedEps.forEach { ep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayEpisode(ep, sortedEps) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "S${ep.seasonNumber}E${ep.episodeNumber} - ${ep.title}",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.PlayArrow, contentDescription = "Putar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersScreen(
    videos: List<VideoItem>,
    manualFolderUris: Set<String>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit
) {
    val byFolder = remember(videos) { videos.groupBy { it.folderName } }
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Folder Video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = onAddFolder) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Tambah Folder")
                }
            }
        }

        if (selectedFolder != null && byFolder.containsKey(selectedFolder)) {
            val list = byFolder[selectedFolder] ?: emptyList()
            item {
                TextButton(onClick = { selectedFolder = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Kembali ke Daftar Folder ($selectedFolder)")
                }
            }
            items(list) { vid ->
                VideoRowCard(video = vid, viewMode = ViewMode.THUMBNAIL, onClick = { onPlayVideo(vid, list) })
            }
        } else {
            byFolder.forEach { (folder, list) ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedFolder = folder },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(folder, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("${list.size} video", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (manualFolderUris.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Folder Manual Terdaftar", style = MaterialTheme.typography.titleSmall)
                }
                items(manualFolderUris.toList()) { uri ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Uri.parse(uri).lastPathSegment ?: uri, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { onRemoveFolder(uri) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<WatchProgress>,
    videos: List<VideoItem>,
    onResume: (VideoItem) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Riwayat Tontonan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Hapus Semua") }
                }
            }
        }
        items(history) { item ->
            val vid = videos.find { it.id == item.videoId }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { if (vid != null) onResume(vid) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Posisi: ${formatDuration(item.positionMs)} / ${formatDuration(item.durationMs)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun AudioModeScreen(
    videos: List<VideoItem>,
    onPlayAudio: (VideoItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Mode Audio Saja (Latar Belakang)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Putar trek audio dari video lokal tanpa layar video aktif.", style = MaterialTheme.typography.bodySmall)
        }
        items(videos) { video ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPlayAudio(video) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(video.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficialUrlPlayerScreen() {
    var inputUrl by remember { mutableStateOf("") }
    var embedUrl by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pemutar URL Resmi (YouTube & Bilibili)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Menggunakan pemutar embed resmi platform sesuai ketentuan layanan (tanpa bypass iklan/DRM).",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tempel URL YouTube atau Bilibili") },
            singleLine = true
        )
        Button(onClick = {
            val resolved = buildOfficialEmbedUrl(inputUrl.trim())
            if (resolved != null) {
                embedUrl = resolved
                errorMsg = null
            } else {
                errorMsg = "URL tidak dikenali. Gunakan tautan video YouTube atau Bilibili yang valid."
            }
        }) {
            Text("Muat Pemutar Resmi")
        }

        if (errorMsg != null) {
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
        }

        if (embedUrl != null) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                    }
                },
                update = { webView ->
                    webView.loadUrl(embedUrl!!)
                }
            )
        }
    }
}

private fun buildOfficialEmbedUrl(rawUrl: String): String? {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null

    if (host.contains("youtube.com") || host.contains("youtu.be")) {
        val videoId = if (host.contains("youtu.be")) {
            uri.lastPathSegment
        } else {
            uri.getQueryParameter("v") ?: uri.pathSegments.lastOrNull()
        }
        if (!videoId.isNullOrBlank()) {
            return "https://www.youtube.com/embed/$videoId?playsinline=1"
        }
    }

    if (host.contains("bilibili.com") || host.contains("bilibili.tv")) {
        val bvid = Regex("""(BV[0-9A-Za-z]+)""").find(rawUrl)?.groupValues?.get(1)
        if (!bvid.isNullOrBlank()) {
            return "https://player.bilibili.com/player.html?bvid=$bvid&high_quality=1"
        }
    }
    return null
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onThemeChange: (ThemeMode) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onAddFolder: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Pengaturan Aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Text("Tema Tampilan", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeChange(mode) },
                        label = { Text(mode.name) }
                    )
                }
            }
        }
        item {
            Text("Mode Tampilan Katalog", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.viewMode == mode,
                        onClick = { onViewModeChange(mode) },
                        label = { Text(mode.name) }
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Autoplay Episode Berikutnya", modifier = Modifier.weight(1f))
                Switch(checked = settings.autoplayNext, onCheckedChange = onAutoplayChange)
            }
        }
        item {
            OutlinedButton(onClick = onAddFolder) {
                Text("Pilih Folder Video Manual (${settings.manualFolderUris.size} terdaftar)")
            }
        }
    }
}

@Composable
fun SubtitleSettingsDialog(
    settings: UserSettings,
    controller: MediaController?,
    onDismiss: () -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onUpdateConfig: (Float, Int, Float, Long) -> Unit
) {
    var sizeSp by remember { mutableFloatStateOf(settings.subtitleSizeSp) }
    var colorArgb by remember { mutableIntStateOf(settings.subtitleColorArgb) }
    var bottomPad by remember { mutableFloatStateOf(settings.subtitleBottomPadding) }
    var syncMs by remember { mutableLongStateOf(settings.subtitleSyncOffsetMs) }

    val colorChoices = listOf(
        "Putih" to Color.White.toArgb(),
        "Kuning" to Color.Yellow.toArgb(),
        "Cyan" to Color.Cyan.toArgb(),
        "Hijau" to Color.Green.toArgb()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan & Trek Subtitle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPickExternalSubtitle, modifier = Modifier.fillMaxWidth()) {
                    Text("Pilih File Subtitle Manual (.srt/.ass/.vtt)")
                }

                val textGroups = controller?.currentTracks?.groups?.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }.orEmpty()
                if (textGroups.isNotEmpty()) {
                    Text("Trek Subtitle Tersedia:", fontWeight = FontWeight.SemiBold)
                    textGroups.forEachIndexed { gIdx, group ->
                        for (tIdx in 0 until group.length) {
                            val format = group.getTrackFormat(tIdx)
                            val label = format.label ?: format.language ?: "Trek ${gIdx + 1}.${tIdx + 1}"
                            TextButton(onClick = { controller?.selectTextTrack(gIdx, tIdx) }) {
                                Text(if (group.isTrackSelected(tIdx)) "✓ $label" else label)
                            }
                        }
                    }
                }

                Text("Ukuran Teks: ${sizeSp.toInt()} sp")
                Slider(value = sizeSp, onValueChange = { sizeSp = it }, valueRange = 12f..32f)

                Text("Posisi Vertikal (Bawah): ${(bottomPad * 100).toInt()}%")
                Slider(value = bottomPad, onValueChange = { bottomPad = it }, valueRange = 0.02f..0.30f)

                Text("Sinkronisasi Waktu: ${syncMs} ms")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { syncMs -= 250L }) { Text("-0.25s") }
                    OutlinedButton(onClick = { syncMs = 0L }) { Text("Reset") }
                    OutlinedButton(onClick = { syncMs += 250L }) { Text("+0.25s") }
                }

                Text("Warna Teks:")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colorChoices.forEach { (name, argb) ->
                        FilterChip(
                            selected = colorArgb == argb,
                            onClick = { colorArgb = argb },
                            label = { Text(name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onUpdateConfig(sizeSp, colorArgb, bottomPad, syncMs)
                onDismiss()
            }) {
                Text("Simpan")
            }
        }
    )
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
