package xyz.mpv.rex.ui.browser.you

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.res.pluralStringResource
import xyz.mpv.rex.presentation.components.ConfirmDialog
import xyz.mpv.rex.presentation.components.pullrefresh.PullRefreshBox
import xyz.mpv.rex.utils.permission.PermissionUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.database.entities.PlaylistEntity
import xyz.mpv.rex.database.repository.PlaylistRepository
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.dialogs.AddToPlaylistDialog
import xyz.mpv.rex.ui.browser.networkstreaming.NetworkBrowserScreen
import xyz.mpv.rex.ui.browser.networkstreaming.NetworkStreamingScreen
import xyz.mpv.rex.ui.browser.networkstreaming.NetworkStreamingViewModel
import xyz.mpv.rex.ui.browser.playlist.PlaylistDetailScreen
import xyz.mpv.rex.ui.browser.playlist.PlaylistScreen
import xyz.mpv.rex.ui.browser.playlist.PlaylistViewModel
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedItem
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedViewModel
import xyz.mpv.rex.ui.browser.search.SearchScreen
import xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet
import xyz.mpv.rex.ui.preferences.PreferencesScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.MediaUtils

private sealed class NetworkPreviewItem {
  data class Connection(val connection: NetworkConnection) : NetworkPreviewItem()
  data class Stream(val url: String) : NetworkPreviewItem()
}

@Serializable
object YouScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val browserPreferences = koinInject<BrowserPreferences>()
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val playlistRepository = koinInject<PlaylistRepository>()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
    val isRefreshing = remember { mutableStateOf(false) }

    val recentsViewModel: RecentlyPlayedViewModel = viewModel(
      factory = RecentlyPlayedViewModel.factory(context.applicationContext as Application),
    )
    val playlistViewModel: PlaylistViewModel = viewModel(
      factory = PlaylistViewModel.factory(context.applicationContext as Application),
    )
    val networkViewModel: NetworkStreamingViewModel = viewModel(
      factory = NetworkStreamingViewModel.factory(context.applicationContext as Application),
    )

    val recentItems by recentsViewModel.recentItems.collectAsState()
    val recentsUiSettings by recentsViewModel.uiSettings.collectAsState()

    val playlistsWithCount by playlistViewModel.playlistsWithCount.collectAsState()

    val connections by networkViewModel.connections.collectAsState()
    val connectionStatuses by networkViewModel.connectionStatuses.collectAsState()
    val playedLinksSerialized by browserPreferences.playedNetworkLinks.collectAsState()

    val playedLinks by remember(playedLinksSerialized) {
      derivedStateOf {
        if (playedLinksSerialized.isBlank()) emptyList()
        else playedLinksSerialized.split("\n").filter { it.isNotBlank() }
      }
    }

    val networkPreviewItems = remember(connections, playedLinks) {
      val list = mutableListOf<NetworkPreviewItem>()
      connections.forEach { list.add(NetworkPreviewItem.Connection(it)) }
      playedLinks.forEach { list.add(NetworkPreviewItem.Stream(it)) }
      list
    }

    // Interactive Action States
    var activeVideoItem by remember { mutableStateOf<RecentlyPlayedItem.VideoItem?>(null) }
    var activePlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var activeConnection by remember { mutableStateOf<NetworkConnection?>(null) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }

    // Dialog States
    var videoForPlaylist by remember { mutableStateOf<Video?>(null) }
    var videoForInfo by remember { mutableStateOf<Video?>(null) }
    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var connectionToDelete by remember { mutableStateOf<NetworkConnection?>(null) }
    var videoToDeleteFromRecents by remember { mutableStateOf<RecentlyPlayedItem.VideoItem?>(null) }
    val deleteFilesCheckbox = rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
      MainScreen.scrollToTopRequest.collect { tabId ->
        if (tabId == "you") {
          scope.launch {
            listState.animateScrollToItem(0)
          }
        }
      }
    }

    val navigationBarHeight = LocalNavigationBarHeight.current

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = stringResource(R.string.you),
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onBackClick = null,
          onCancelSelection = {},
          onSearchClick = {
            backStack.add(SearchScreen())
          },
          onSettingsClick = {
            backStack.add(PreferencesScreen)
          },
        )
      },
    ) { paddingValues ->
      PullRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
          isRefreshing.value = true
          scope.launch {
            recentsViewModel.refresh()
            playlistViewModel.refresh()
            kotlinx.coroutines.delay(400)
            isRefreshing.value = false
          }
        },
        modifier = Modifier
          .fillMaxSize()
          .padding(top = paddingValues.calculateTopPadding()),
      ) {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(
            bottom = navigationBarHeight + 16.dp,
          ),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

          // ==========================================
          // PROFILE & QUICK ACTIONS HEADER
          // ==========================================
          item(key = "profile_header") {
            YouProfileHeader(
              recentCount = recentItems.size,
              playlistCount = playlistsWithCount.size,
              connectionCount = connections.size + playedLinks.size,
              onHistoryClick = { backStack.add(RecentlyPlayedScreen) },
              onPlaylistsClick = { backStack.add(PlaylistScreen) },
              onNetworkClick = { backStack.add(NetworkStreamingScreen) },
            )
          }

          // ==========================================
          // 1. RECENTLY PLAYED SECTION (HORIZONTAL SHELF)
          // ==========================================
          item(key = "header_recents") {
            SectionHeader(
              title = stringResource(R.string.recently_played),
              onViewAllClick = {
                backStack.add(RecentlyPlayedScreen)
              },
            )
          }

          item(key = "content_recents") {
            if (!enableRecentlyPlayed) {
              ShelfEmptyCard(
                icon = Icons.Filled.History,
                title = stringResource(R.string.recently_played_disabled_title),
                message = stringResource(R.string.recently_played_disabled_message),
              )
            } else {
              val previewRecents = recentItems.take(15)
              if (previewRecents.isEmpty()) {
                ShelfEmptyCard(
                  icon = Icons.Filled.History,
                  title = stringResource(R.string.no_recently_played_videos),
                  message = stringResource(R.string.no_recently_played_videos_message),
                )
              } else {
                LazyRow(
                  contentPadding = PaddingValues(horizontal = 16.dp),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  items(previewRecents, key = { item ->
                    when (item) {
                      is RecentlyPlayedItem.VideoItem -> "video_${item.video.id}_${item.timestamp}"
                      is RecentlyPlayedItem.PlaylistItem -> "playlist_${item.playlist.id}_${item.timestamp}"
                    }
                  }) { item ->
                    when (item) {
                      is RecentlyPlayedItem.VideoItem -> {
                        RecentlyPlayedVideoShelfCard(
                          video = item.video,
                          progressPercentage = item.progress,
                          isWatched = item.isWatched,
                          isRecentlyPlayed = true,
                          showThumbnails = recentsUiSettings.showVideoThumbnails,
                          onClick = {
                            MediaUtils.playFile(item.video, context, "you_tab")
                          },
                          onLongClick = {
                            activeVideoItem = item
                          },
                          modifier = Modifier.width(160.dp),
                        )
                      }
                      is RecentlyPlayedItem.PlaylistItem -> {
                        PlaylistShelfCard(
                          playlist = item.playlist,
                          itemCount = item.videoCount,
                          onClick = {
                            backStack.add(PlaylistDetailScreen(item.playlist.id))
                          },
                          onLongClick = {
                            activePlaylist = item.playlist
                          },
                          modifier = Modifier.width(160.dp),
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // ==========================================
          // 2. PLAYLISTS SECTION (HORIZONTAL SHELF)
          // ==========================================
          item(key = "header_playlists") {
            SectionHeader(
              title = stringResource(R.string.playlists),
              onViewAllClick = {
                backStack.add(PlaylistScreen)
              },
            )
          }

          item(key = "content_playlists") {
            val previewPlaylists = playlistsWithCount.take(15)
            if (previewPlaylists.isEmpty()) {
              ShelfEmptyCard(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                title = stringResource(R.string.no_playlists_yet),
                message = "Custom playlists you create will appear here",
              )
            } else {
              LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                items(previewPlaylists, key = { it.playlist.id }) { playlistWithCount ->
                  PlaylistShelfCard(
                    playlist = playlistWithCount.playlist,
                    itemCount = playlistWithCount.itemCount,
                    onClick = {
                      backStack.add(PlaylistDetailScreen(playlistWithCount.playlist.id))
                    },
                    onLongClick = {
                      activePlaylist = playlistWithCount.playlist
                    },
                    modifier = Modifier.width(150.dp),
                  )
                }
              }
            }
          }

          // ==========================================
          // 3. NETWORK SECTION (HORIZONTAL SHELF)
          // ==========================================
          item(key = "header_network") {
            SectionHeader(
              title = stringResource(R.string.network),
              onViewAllClick = {
                backStack.add(NetworkStreamingScreen)
              },
            )
          }

          item(key = "content_network") {
            if (networkPreviewItems.isEmpty()) {
              ShelfEmptyCard(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.network_empty_title),
                message = "Saved network servers and streams will appear here",
              )
            } else {
              LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                items(networkPreviewItems, key = { item ->
                  when (item) {
                    is NetworkPreviewItem.Connection -> "conn_${item.connection.id}"
                    is NetworkPreviewItem.Stream -> "stream_${item.url}"
                  }
                }) { item ->
                  when (item) {
                    is NetworkPreviewItem.Connection -> {
                      val status = connectionStatuses[item.connection.id]
                      NetworkConnectionShelfCard(
                        connection = item.connection,
                        isConnected = status?.isConnected == true,
                        isConnecting = status?.isConnecting == true,
                        onClick = {
                          if (status?.isConnected == true) {
                            backStack.add(
                              NetworkBrowserScreen(
                                connectionId = item.connection.id,
                                connectionName = item.connection.name,
                                currentPath = "/",
                              ),
                            )
                          } else {
                            networkViewModel.connect(item.connection)
                          }
                        },
                        onLongClick = {
                          activeConnection = item.connection
                        },
                        modifier = Modifier.width(180.dp),
                      )
                    }
                    is NetworkPreviewItem.Stream -> {
                      NetworkStreamShelfCard(
                        url = item.url,
                        onClick = {
                          val currentList = playedLinks.toMutableList()
                          currentList.remove(item.url)
                          currentList.add(0, item.url)
                          browserPreferences.playedNetworkLinks.set(currentList.joinToString("\n"))
                          MediaUtils.playFile(item.url, context, "network_stream")
                        },
                        onLongClick = {
                          activeStreamUrl = item.url
                        },
                        modifier = Modifier.width(180.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // ==========================================
    // ITEM ACTIONS BOTTOM SHEETS
    // ==========================================

    // 1. Video Options Sheet
    if (activeVideoItem != null) {
      val video = activeVideoItem!!.video
      ModalBottomSheet(
        onDismissRequest = { activeVideoItem = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
          Box(
            modifier = Modifier
              .padding(top = 16.dp, bottom = 10.dp)
              .size(width = 36.dp, height = 4.dp)
              .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                shape = CircleShape,
              ),
          )
        },
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.PlayArrow,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(24.dp),
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = video.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${video.durationFormatted} • ${video.sizeFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
              )
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          ActionSheetItem(
            icon = Icons.Filled.PlayArrow,
            label = stringResource(R.string.play),
            onClick = {
              val v = video
              activeVideoItem = null
              MediaUtils.playFile(v, context, "you_tab")
            },
          )
          ActionSheetItem(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            label = stringResource(R.string.add_to_playlist),
            onClick = {
              val v = video
              activeVideoItem = null
              videoForPlaylist = v
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Info,
            label = stringResource(R.string.info),
            onClick = {
              val v = video
              activeVideoItem = null
              videoForInfo = v
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Share,
            label = stringResource(R.string.generic_share),
            onClick = {
              val v = video
              activeVideoItem = null
              MediaUtils.shareVideos(context, listOf(v))
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.remove),
            tint = MaterialTheme.colorScheme.error,
            onClick = {
              val itemToDelete = activeVideoItem
              activeVideoItem = null
              if (itemToDelete != null) {
                deleteFilesCheckbox.value = false
                videoToDeleteFromRecents = itemToDelete
              }
            },
          )
        }
      }
    }

    // 2. Playlist Options Sheet
    if (activePlaylist != null) {
      val playlist = activePlaylist!!
      ModalBottomSheet(
        onDismissRequest = { activePlaylist = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
          Box(
            modifier = Modifier
              .padding(top = 16.dp, bottom = 10.dp)
              .size(width = 36.dp, height = 4.dp)
              .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                shape = CircleShape,
              ),
          )
        },
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(24.dp),
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = if (playlist.isM3uPlaylist) "Network Playlist" else "Local Playlist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
              )
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          ActionSheetItem(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            label = stringResource(R.string.playlists),
            onClick = {
              val p = playlist
              activePlaylist = null
              backStack.add(PlaylistDetailScreen(p.id))
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Edit,
            label = stringResource(R.string.rename),
            onClick = {
              val p = playlist
              activePlaylist = null
              renameText = p.name
              playlistToRename = p
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.delete),
            tint = MaterialTheme.colorScheme.error,
            onClick = {
              val p = playlist
              activePlaylist = null
              playlistToDelete = p
            },
          )
        }
      }
    }

    // 3. Network Connection Options Sheet
    if (activeConnection != null) {
      val conn = activeConnection!!
      val isConn = connectionStatuses[conn.id]?.isConnected == true
      ModalBottomSheet(
        onDismissRequest = { activeConnection = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
          Box(
            modifier = Modifier
              .padding(top = 16.dp, bottom = 10.dp)
              .size(width = 36.dp, height = 4.dp)
              .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                shape = CircleShape,
              ),
          )
        },
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.FolderOpen,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(24.dp),
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = conn.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${conn.protocol.displayName} • ${conn.host}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
              )
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          ActionSheetItem(
            icon = Icons.Filled.FolderOpen,
            label = if (isConn) "Browse" else "Connect & Browse",
            onClick = {
              val c = conn
              activeConnection = null
              if (isConn) {
                backStack.add(
                  NetworkBrowserScreen(
                    connectionId = c.id,
                    connectionName = c.name,
                    currentPath = "/",
                  ),
                )
              } else {
                networkViewModel.connect(c)
              }
            },
          )
          if (isConn) {
            ActionSheetItem(
              icon = Icons.Filled.LinkOff,
              label = "Disconnect",
              onClick = {
                val c = conn
                activeConnection = null
                networkViewModel.disconnect(c)
              },
            )
          }
          ActionSheetItem(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.delete),
            tint = MaterialTheme.colorScheme.error,
            onClick = {
              val c = conn
              activeConnection = null
              connectionToDelete = c
            },
          )
        }
      }
    }

    // 4. Network Stream Options Sheet
    if (activeStreamUrl != null) {
      val streamUrl = activeStreamUrl!!
      ModalBottomSheet(
        onDismissRequest = { activeStreamUrl = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
          Box(
            modifier = Modifier
              .padding(top = 16.dp, bottom = 10.dp)
              .size(width = 36.dp, height = 4.dp)
              .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                shape = CircleShape,
              ),
          )
        },
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.tertiaryContainer,
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.Link,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.size(24.dp),
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Stream",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = streamUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          ActionSheetItem(
            icon = Icons.Filled.PlayArrow,
            label = stringResource(R.string.play),
            onClick = {
              val url = streamUrl
              activeStreamUrl = null
              val currentList = playedLinks.toMutableList()
              currentList.remove(url)
              currentList.add(0, url)
              browserPreferences.playedNetworkLinks.set(currentList.joinToString("\n"))
              MediaUtils.playFile(url, context, "network_stream")
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.ContentCopy,
            label = "Copy Link",
            onClick = {
              val url = streamUrl
              activeStreamUrl = null
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              clipboard?.setPrimaryClip(ClipData.newPlainText("Stream URL", url))
              Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            },
          )
          ActionSheetItem(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.network_remove_link_action),
            tint = MaterialTheme.colorScheme.error,
            onClick = {
              val url = streamUrl
              activeStreamUrl = null
              val currentList = playedLinks.toMutableList()
              currentList.remove(url)
              browserPreferences.playedNetworkLinks.set(currentList.joinToString("\n"))
            },
          )
        }
      }
    }

    // ==========================================
    // ACTION DIALOGS & OVERLAYS
    // ==========================================

    // Add To Playlist Dialog
    if (videoForPlaylist != null) {
      AddToPlaylistDialog(
        isOpen = true,
        videos = listOf(videoForPlaylist!!),
        onDismiss = { videoForPlaylist = null },
        onSuccess = {
          videoForPlaylist = null
          playlistViewModel.loadData()
        },
      )
    }

    // Media Info Sheet
    if (videoForInfo != null) {
      MediaInfoSheet(
        uri = videoForInfo!!.uri,
        onDismiss = { videoForInfo = null },
      )
    }

    // Playlist Rename Dialog
    if (playlistToRename != null) {
      AlertDialog(
        onDismissRequest = { playlistToRename = null },
        title = { Text(stringResource(R.string.rename)) },
        text = {
          OutlinedTextField(
            value = renameText,
            onValueChange = { renameText = it },
            label = { Text(stringResource(R.string.name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        },
        confirmButton = {
          TextButton(
            enabled = renameText.isNotBlank(),
            onClick = {
              val target = playlistToRename!!
              val newName = renameText.trim()
              playlistToRename = null
              scope.launch {
                playlistRepository.updatePlaylist(target.copy(name = newName))
                playlistViewModel.loadData()
              }
            },
          ) {
            Text(stringResource(R.string.rename))
          }
        },
        dismissButton = {
          TextButton(onClick = { playlistToRename = null }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }

    // Playlist Delete Dialog
    if (playlistToDelete != null) {
      AlertDialog(
        onDismissRequest = { playlistToDelete = null },
        title = { Text(stringResource(R.string.delete)) },
        text = { Text("Delete playlist \"${playlistToDelete!!.name}\"?") },
        confirmButton = {
          TextButton(
            onClick = {
              val target = playlistToDelete!!
              playlistToDelete = null
              scope.launch {
                playlistRepository.deletePlaylist(target)
                playlistViewModel.loadData()
              }
            },
          ) {
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { playlistToDelete = null }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }

    // Network Connection Delete Dialog
    if (connectionToDelete != null) {
      AlertDialog(
        onDismissRequest = { connectionToDelete = null },
        title = { Text(stringResource(R.string.delete)) },
        text = { Text("Delete connection \"${connectionToDelete!!.name}\"?") },
        confirmButton = {
          TextButton(
            onClick = {
              val target = connectionToDelete!!
              connectionToDelete = null
              scope.launch {
                networkViewModel.deleteConnection(target)
              }
            },
          ) {
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { connectionToDelete = null }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }

    // Recently Played Video Delete / Remove Confirmation Dialog
    if (videoToDeleteFromRecents != null) {
      val target = videoToDeleteFromRecents!!
      val itemText = pluralStringResource(R.plurals.item_type_item_plural, 1)
      val deleteFiles = deleteFilesCheckbox.value

      val title = if (deleteFiles) {
        stringResource(R.string.delete_files_title, 1, itemText)
      } else {
        stringResource(R.string.remove_from_history_title, 1, itemText)
      }

      val subtitle = if (deleteFiles) {
        stringResource(R.string.delete_files_msg)
      } else {
        stringResource(R.string.remove_from_history_msg, itemText)
      }

      ConfirmDialog(
        title = title,
        subtitle = subtitle,
        customContent = {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = deleteFilesCheckbox.value,
              onCheckedChange = {
                deleteFilesCheckbox.value = it
              },
            )
            Text(
              text = stringResource(R.string.also_delete_files),
              modifier = Modifier.padding(start = 8.dp),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        },
        onConfirm = {
          val toDelete = target
          val shouldDeleteFiles = deleteFilesCheckbox.value
          videoToDeleteFromRecents = null
          deleteFilesCheckbox.value = false
          scope.launch {
            if (shouldDeleteFiles) {
              PermissionUtils.StorageOps.deleteVideos(context, listOf(toDelete.video))
            }
            recentsViewModel.deleteRecentItems(listOf(toDelete))
          }
        },
        onCancel = {
          videoToDeleteFromRecents = null
          deleteFilesCheckbox.value = false
        },
      )
    }
  }

  /**
   * Action Sheet Item Row
   */
  @Composable
  private fun ActionSheetItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
  ) {
    Surface(
      onClick = onClick,
      shape = RoundedCornerShape(12.dp),
      color = Color.Transparent,
      modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(24.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Normal,
          color = tint,
        )
      }
    }
  }

  /**
   * Top Profile & Quick Actions header for 'You' tab
   */
  @Composable
  private fun YouProfileHeader(
    recentCount: Int,
    playlistCount: Int,
    connectionCount: Int,
    onHistoryClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onNetworkClick: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(52.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.AccountCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(36.dp),
            )
          }
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          val stats = buildList {
            if (recentCount > 0) add("$recentCount recent")
            if (playlistCount > 0) add("$playlistCount ${if (playlistCount == 1) "playlist" else "playlists"}")
            if (connectionCount > 0) add("$connectionCount ${if (connectionCount == 1) "server" else "servers"}")
          }.joinToString(" • ")

          if (stats.isNotBlank()) {
            Text(
              text = stats,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
            )
          }
        }
      }

      // Quick action shortcut buttons
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        QuickActionChip(
          icon = Icons.Filled.History,
          label = stringResource(R.string.recently_played),
          onClick = onHistoryClick,
        )
        QuickActionChip(
          icon = Icons.AutoMirrored.Filled.PlaylistPlay,
          label = stringResource(R.string.playlists),
          onClick = onPlaylistsClick,
        )
        QuickActionChip(
          icon = Icons.Filled.Language,
          label = stringResource(R.string.network),
          onClick = onNetworkClick,
        )
      }
    }
  }

  @Composable
  private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Surface(
      onClick = onClick,
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      contentColor = MaterialTheme.colorScheme.onSurface,
      modifier = modifier,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
      }
    }
  }

  /**
   * Section Header with Title and "View all" navigation action
   */
  @Composable
  private fun SectionHeader(
    title: String,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )

      TextButton(
        onClick = onViewAllClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
      ) {
        Text(
          text = stringResource(R.string.view_all),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }

  /**
   * Compact card for recently played video in horizontal shelf
   * Guarantees fixed dimensions to prevent layout shifts during horizontal scrolling
   */
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun RecentlyPlayedVideoShelfCard(
    video: Video,
    progressPercentage: Float?,
    isWatched: Boolean,
    isRecentlyPlayed: Boolean,
    showThumbnails: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    val thumbnailRepository = koinInject<ThumbnailRepository>()
    val density = LocalDensity.current
    val thumbWidthDp = 160.dp
    val aspect = 16f / 9f
    val thumbWidthPx = with(density) { thumbWidthDp.roundToPx() }
    val thumbHeightPx = (thumbWidthPx / aspect).roundToInt()

    val thumbnailKey = remember(video.id, video.dateModified, video.size, thumbWidthPx, thumbHeightPx) {
      thumbnailRepository.thumbnailKey(video, thumbWidthPx, thumbHeightPx)
    }

    var thumbnail by remember(thumbnailKey) {
      mutableStateOf(thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx))
    }

    LaunchedEffect(thumbnailKey) {
      thumbnailRepository.thumbnailReadyKeys.filter { it == thumbnailKey }.collect {
        thumbnail = thumbnailRepository.getThumbnailFromMemory(video, thumbWidthPx, thumbHeightPx)
      }
    }

    LaunchedEffect(thumbnailKey, showThumbnails) {
      if (thumbnail == null && showThumbnails) {
        thumbnail = withContext(Dispatchers.IO) {
          thumbnailRepository.getThumbnail(video, thumbWidthPx, thumbHeightPx)
        }
      }
    }

    Card(
      modifier = modifier
        .height(148.dp)
        .clip(RoundedCornerShape(10.dp))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
      shape = RoundedCornerShape(10.dp),
      colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
      ) {
        // 16:9 Thumbnail Box
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
          contentAlignment = Alignment.Center,
        ) {
          if (showThumbnails && thumbnail != null) {
            Image(
              bitmap = thumbnail!!.asImageBitmap(),
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          } else {
            Icon(
              imageVector = if (video.isAudio) Icons.Filled.MusicNote else Icons.Filled.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(36.dp),
              tint = MaterialTheme.colorScheme.secondary,
            )
          }

          // Progress Bar
          if (progressPercentage != null && !isWatched) {
            LinearProgressIndicator(
              progress = { progressPercentage },
              modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
              color = MaterialTheme.colorScheme.primary,
              trackColor = Color.Black.copy(alpha = 0.35f),
            )
          }

          // Duration overlay
          if (video.durationFormatted.isNotBlank() && video.durationFormatted != "--:--" && video.durationFormatted != "00:00") {
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = Color.Black.copy(alpha = 0.72f),
              contentColor = Color.White,
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
            ) {
              Text(
                text = video.durationFormatted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Fixed height text container guarantees NO jumping effect on horizontal scroll
        Column(
          modifier = Modifier
            .height(44.dp)
            .padding(horizontal = 2.dp),
          verticalArrangement = Arrangement.Center,
        ) {
          val shouldHighlight = isRecentlyPlayed && !isWatched
          Text(
            text = video.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (shouldHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = when {
              shouldHighlight -> MaterialTheme.colorScheme.primary
              isWatched -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
              else -> MaterialTheme.colorScheme.onSurface
            },
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }

  /**
   * Compact card for playlist in horizontal shelf
   */
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun PlaylistShelfCard(
    playlist: PlaylistEntity,
    itemCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Card(
      modifier = modifier
        .height(148.dp)
        .clip(RoundedCornerShape(12.dp))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
          )

          Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.72f),
            contentColor = Color.White,
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(4.dp),
          ) {
            Text(
              text = "$itemCount",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(
          modifier = Modifier.height(44.dp),
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Text(
            text = if (playlist.isM3uPlaylist) "Network" else "Local",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      }
    }
  }

  /**
   * Compact card for saved network connection in horizontal shelf
   */
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun NetworkConnectionShelfCard(
    connection: NetworkConnection,
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Card(
      modifier = modifier
        .clip(RoundedCornerShape(12.dp))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isConnected) {
              MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
              MaterialTheme.colorScheme.surfaceContainerHighest
            },
          ) {
            Text(
              text = when {
                isConnecting -> "Connecting..."
                isConnected -> "Connected"
                else -> "Offline"
              },
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }

        Column {
          Text(
            text = connection.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "${connection.protocol.displayName} • ${connection.host}",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      }
    }
  }

  /**
   * Compact card for recent network stream in horizontal shelf
   */
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun NetworkStreamShelfCard(
    url: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Card(
      modifier = modifier
        .clip(RoundedCornerShape(12.dp))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.tertiaryContainer,
          modifier = Modifier.size(36.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.Link,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        Column {
          Text(
            text = "Stream",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = url,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      }
    }
  }

  /**
   * Styled inline empty card for shelves
   */
  @Composable
  private fun ShelfEmptyCard(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      shape = RoundedCornerShape(12.dp),
      modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.size(42.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.outline,
              modifier = Modifier.size(24.dp),
            )
          }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      }
    }
  }
}
