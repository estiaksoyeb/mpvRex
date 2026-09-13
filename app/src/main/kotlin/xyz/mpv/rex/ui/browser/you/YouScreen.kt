package xyz.mpv.rex.ui.browser.you

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.browser.cards.NetworkConnectionCard
import xyz.mpv.rex.ui.browser.cards.PlaylistCard
import xyz.mpv.rex.ui.browser.cards.VideoCard
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.networkstreaming.NetworkBrowserScreen
import xyz.mpv.rex.ui.browser.networkstreaming.NetworkStreamingScreen
import xyz.mpv.rex.ui.browser.networkstreaming.NetworkStreamingViewModel
import xyz.mpv.rex.ui.browser.playlist.PlaylistDetailScreen
import xyz.mpv.rex.ui.browser.playlist.PlaylistScreen
import xyz.mpv.rex.ui.browser.playlist.PlaylistViewModel
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedItem
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedViewModel
import xyz.mpv.rex.ui.preferences.PreferencesScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.MediaUtils

private sealed class NetworkPreviewItem {
  data class Connection(val connection: NetworkConnection) : NetworkPreviewItem()
  data class Stream(val url: String) : NetworkPreviewItem()
}

@Serializable
object YouScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val browserPreferences = koinInject<BrowserPreferences>()

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
    val playlistUiSettings by playlistViewModel.uiSettings.collectAsState()

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
      connections.take(3).forEach { list.add(NetworkPreviewItem.Connection(it)) }
      val remaining = 3 - list.size
      if (remaining > 0) {
        playedLinks.take(remaining).forEach { list.add(NetworkPreviewItem.Stream(it)) }
      }
      list
    }

    // Accordion expanded / collapsed states
    var isRecentsExpanded by rememberSaveable { mutableStateOf(true) }
    var isPlaylistsExpanded by rememberSaveable { mutableStateOf(true) }
    var isNetworkExpanded by rememberSaveable { mutableStateOf(true) }

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
          onSettingsClick = {
            backStack.add(PreferencesScreen)
          },
        )
      },
    ) { paddingValues ->
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .padding(top = paddingValues.calculateTopPadding()),
        contentPadding = PaddingValues(
          start = 8.dp,
          end = 8.dp,
          bottom = navigationBarHeight + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {

        // ==========================================
        // 1. RECENTLY PLAYED SECTION
        // ==========================================
        item(key = "header_recents") {
          SectionHeader(
            title = stringResource(R.string.recently_played),
            isExpanded = isRecentsExpanded,
            onHeaderClick = {
              backStack.add(RecentlyPlayedScreen)
            },
            onToggleExpand = {
              isRecentsExpanded = !isRecentsExpanded
            },
          )
        }

        item(key = "content_recents") {
          AnimatedVisibility(
            visible = isRecentsExpanded,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
          ) {
            val previewRecents = recentItems.take(3)
            if (previewRecents.isEmpty()) {
              Text(
                text = stringResource(R.string.no_recently_played_videos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
              )
            } else {
              Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                previewRecents.forEach { item ->
                  when (item) {
                    is RecentlyPlayedItem.VideoItem -> {
                      VideoCard(
                        video = item.video,
                        uiSettings = recentsUiSettings,
                        onClick = {
                          MediaUtils.playFile(item.video, context, "you_tab")
                        },
                        isGridMode = false,
                        gridColumns = 1,
                        progressPercentage = item.progress,
                        isWatched = item.isWatched,
                        isRecentlyPlayed = true,
                      )
                    }
                    is RecentlyPlayedItem.PlaylistItem -> {
                      PlaylistCard(
                        playlist = item.playlist,
                        itemCount = item.videoCount,
                        uiSettings = recentsUiSettings,
                        onClick = {
                          backStack.add(PlaylistDetailScreen(item.playlist.id))
                        },
                        onLongClick = {},
                        isGridMode = false,
                        gridColumns = 1,
                      )
                    }
                  }
                }
              }
            }
          }
        }

        item(key = "divider_recents_playlists") {
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 1.dp,
          )
        }

        // ==========================================
        // 2. PLAYLISTS SECTION
        // ==========================================
        item(key = "header_playlists") {
          SectionHeader(
            title = stringResource(R.string.playlists),
            isExpanded = isPlaylistsExpanded,
            onHeaderClick = {
              backStack.add(PlaylistScreen)
            },
            onToggleExpand = {
              isPlaylistsExpanded = !isPlaylistsExpanded
            },
          )
        }

        item(key = "content_playlists") {
          AnimatedVisibility(
            visible = isPlaylistsExpanded,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
          ) {
            val previewPlaylists = playlistsWithCount.take(3)
            if (previewPlaylists.isEmpty()) {
              Text(
                text = stringResource(R.string.no_playlists_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
              )
            } else {
              Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                previewPlaylists.forEach { playlistWithCount ->
                  PlaylistCard(
                    playlist = playlistWithCount.playlist,
                    itemCount = playlistWithCount.itemCount,
                    uiSettings = playlistUiSettings,
                    onClick = {
                      backStack.add(PlaylistDetailScreen(playlistWithCount.playlist.id))
                    },
                    onLongClick = {},
                    isGridMode = false,
                    gridColumns = 1,
                  )
                }
              }
            }
          }
        }

        item(key = "divider_playlists_network") {
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 1.dp,
          )
        }

        // ==========================================
        // 3. NETWORK SECTION
        // ==========================================
        item(key = "header_network") {
          SectionHeader(
            title = stringResource(R.string.network),
            isExpanded = isNetworkExpanded,
            onHeaderClick = {
              backStack.add(NetworkStreamingScreen)
            },
            onToggleExpand = {
              isNetworkExpanded = !isNetworkExpanded
            },
          )
        }

        item(key = "content_network") {
          AnimatedVisibility(
            visible = isNetworkExpanded,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
          ) {
            if (networkPreviewItems.isEmpty()) {
              Text(
                text = stringResource(R.string.network_empty_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
              )
            } else {
              Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                networkPreviewItems.forEach { item ->
                  when (item) {
                    is NetworkPreviewItem.Connection -> {
                      val status = connectionStatuses[item.connection.id]
                      NetworkConnectionCard(
                        connection = item.connection,
                        onConnect = { conn -> networkViewModel.connect(conn) },
                        onDisconnect = { conn -> networkViewModel.disconnect(conn) },
                        onEdit = {
                          backStack.add(NetworkStreamingScreen)
                        },
                        onDelete = { conn -> networkViewModel.deleteConnection(conn) },
                        onBrowse = { conn ->
                          if (status?.isConnected == true) {
                            backStack.add(
                              NetworkBrowserScreen(
                                connectionId = conn.id,
                                connectionName = conn.name,
                                currentPath = "/",
                              ),
                            )
                          } else {
                            networkViewModel.connect(conn)
                          }
                        },
                        onAutoConnectChange = { conn, autoConnect ->
                          networkViewModel.updateConnection(conn.copy(autoConnect = autoConnect))
                        },
                        isConnected = status?.isConnected == true,
                        isConnecting = status?.isConnecting == true,
                        error = status?.error,
                      )
                    }
                    is NetworkPreviewItem.Stream -> {
                      Card(
                        modifier = Modifier
                          .fillMaxWidth()
                          .clickable {
                            val currentList = playedLinks.toMutableList()
                            currentList.remove(item.url)
                            currentList.add(0, item.url)
                            browserPreferences.playedNetworkLinks.set(currentList.joinToString("\n"))
                            MediaUtils.playFile(item.url, context, "network_stream")
                          },
                        colors = CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        shape = RoundedCornerShape(12.dp),
                      ) {
                        Row(
                          modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                          Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                          )
                          Text(
                            text = item.url,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
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
      }
    }
  }

  /**
   * Entire header div is clickable to open the full screen.
   * Div text is bold with an arrow chevron.
   * Far right toggle button expands/collapses the preview.
   */
  @Composable
  private fun SectionHeader(
    title: String,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Surface(
      onClick = onHeaderClick,
      modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 2.dp),
      shape = RoundedCornerShape(12.dp),
      color = Color.Transparent,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }

        IconButton(
          onClick = onToggleExpand,
          modifier = Modifier.size(36.dp),
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
          )
        }
      }
    }
  }
}
