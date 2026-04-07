package app.marlboroadvance.mpvex.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.browser.PathComponent
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.utils.storage.CoreMediaScanner
import app.marlboroadvance.mpvex.utils.storage.VideoScanUtils
import app.marlboroadvance.mpvex.utils.storage.StorageVolumeUtils
import app.marlboroadvance.mpvex.utils.storage.FileTypeUtils
import app.marlboroadvance.mpvex.utils.media.MediaFormatter
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * Unified repository for ALL media file operations
 * Consolidates FileSystemRepository, VideoRepository functionality
 */
object MediaFileRepository {
  private const val TAG = "MediaFileRepository"

  /**
   * Clears all caches
   */
  fun clearCache() {
    Log.d(TAG, "Clearing all caches (CoreMediaScanner)")
    CoreMediaScanner.clearCache()
  }

  // =============================================================================
  // FOLDER OPERATIONS (Album View)
  // =============================================================================

  /**
   * Scans all storage volumes to find all folders containing videos
   */
  suspend fun getAllVideoFolders(
    context: Context
  ): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      try {
        val koin = org.koin.core.context.GlobalContext.get()
        val browserPreferences = koin.get<app.marlboroadvance.mpvex.preferences.BrowserPreferences>()
        val appearancePreferences = koin.get<AppearancePreferences>()
        val playbackStateRepository = koin.get<PlaybackStateRepository>()
        
        val isAudioEnabled = browserPreferences.showAudioFiles.get()
        val playbackStates = playbackStateRepository.getAllPlaybackStates()
        val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
        
        val folders = CoreMediaScanner.getFlatMediaFolders(context, playbackStates, thresholdDays)
        folders
          .filter { folder -> isAudioEnabled || folder.videoCount > 0 }
          .map { folder ->
            VideoFolder(
              bucketId = folder.id,
              name = folder.name,
              path = folder.path,
              videoCount = folder.videoCount,
              audioCount = folder.audioCount,
              totalSize = folder.totalSize,
              totalDuration = folder.totalDuration,
              lastModified = folder.lastModified,
              newCount = folder.newCount
            )
          }
      } catch (e: Exception) {
        Log.e(TAG, "Error scanning for video folders", e)
        emptyList()
      }
    }

  /**
   * Fast scan using MediaStore - same as getAllVideoFolders
   * Kept for backward compatibility
   */
  suspend fun getAllVideoFoldersFast(
    context: Context,
    onProgress: ((Int) -> Unit)? = null,
  ): List<VideoFolder> = getAllVideoFolders(context)

  // =============================================================================
  // VIDEO FILE OPERATIONS
  // =============================================================================

  /**
   * Gets all videos in a specific folder
   */
  suspend fun getVideosInFolder(
    context: Context,
    bucketId: String
  ): List<Video> =
    withContext(Dispatchers.IO) {
      try {
        VideoScanUtils.getVideosInFolder(context, bucketId)
      } catch (e: Exception) {
        Log.e(TAG, "Error getting videos for bucket $bucketId", e)
        emptyList()
      }
    }

  /**
   * Gets videos from multiple folders
   */
  suspend fun getVideosForBuckets(
    context: Context,
    bucketIds: Set<String>
  ): List<Video> =
    withContext(Dispatchers.IO) {
      val result = mutableListOf<Video>()
      for (id in bucketIds) {
        runCatching { result += getVideosInFolder(context, id) }
      }
      result
    }

  // =============================================================================
  // FILE SYSTEM BROWSING (Tree View)
  // =============================================================================

  /**
   * Parses a path into breadcrumb components
   */
  fun getPathComponents(path: String): List<PathComponent> {
    if (path.isBlank()) return emptyList()
    val components = mutableListOf<PathComponent>()
    val normalizedPath = path.trimEnd('/')
    val parts = normalizedPath.split("/").filter { it.isNotEmpty() }
    components.add(PathComponent("Root", "/"))
    var currentPath = ""
    for (part in parts) {
      currentPath += "/$part"
      components.add(PathComponent(part, currentPath))
    }
    return components
  }

  /**
   * Scans a directory and returns its contents (folders and video files)
   */
  suspend fun scanDirectory(
    context: Context,
    path: String,
    showAllFileTypes: Boolean = false,
    useFastCount: Boolean = false,
  ): Result<List<FileSystemItem>> =
    withContext(Dispatchers.IO) {
      try {
        val directory = File(path)
        if (!directory.exists() || !directory.canRead() || !directory.isDirectory) {
          return@withContext Result.failure(Exception("Invalid directory: $path"))
        }

        val items = mutableListOf<FileSystemItem>()
        val koin = org.koin.core.context.GlobalContext.get()
        val browserPreferences = koin.get<app.marlboroadvance.mpvex.preferences.BrowserPreferences>()
        val appearancePreferences = koin.get<AppearancePreferences>()
        val playbackStateRepository = koin.get<PlaybackStateRepository>()
        
        val isAudioEnabled = browserPreferences.showAudioFiles.get()
        val playbackStates = playbackStateRepository.getAllPlaybackStates()
        val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()

        // Get folders using CoreMediaScanner (unified)
        val folders = CoreMediaScanner.getFoldersInDirectory(context, path, playbackStates, thresholdDays)
        folders
          .filter { data -> isAudioEnabled || data.videoCount > 0 }
          .forEach { folderData ->
            items.add(
              FileSystemItem.Folder(
                name = folderData.name,
                path = folderData.path,
                lastModified = folderData.lastModified,
                videoCount = folderData.videoCount,
                audioCount = folderData.audioCount,
                totalSize = folderData.totalSize,
                totalDuration = folderData.totalDuration,
                hasSubfolders = folderData.hasSubfolders,
                newCount = folderData.newCount
              ),
            )
          }

        // Get videos in current directory
        val videos = VideoScanUtils.getVideosInFolder(context, path)
        videos
          .filter { video -> isAudioEnabled || !video.isAudio }
          .forEach { video ->
            items.add(
              FileSystemItem.VideoFile(
                name = video.displayName,
                path = video.path,
                lastModified = File(video.path).lastModified(),
                video = video,
              ),
            )
          }

        Result.success(items)
      } catch (e: Exception) {
        Log.e(TAG, "Error scanning directory: $path", e)
        Result.failure(e)
      }
    }

  /**
   * Gets all storage volume roots with recursive video counts
   */
  suspend fun getStorageRoots(context: Context): List<FileSystemItem.Folder> =
    withContext(Dispatchers.IO) {
      val roots = mutableListOf<FileSystemItem.Folder>()
      try {
        val koin = org.koin.core.context.GlobalContext.get()
        val appearancePreferences = koin.get<AppearancePreferences>()
        val playbackStateRepository = koin.get<PlaybackStateRepository>()
        
        val playbackStates = playbackStateRepository.getAllPlaybackStates()
        val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()

        // Primary storage
        val primaryStorage = Environment.getExternalStorageDirectory()
        if (primaryStorage.exists() && primaryStorage.canRead()) {
          val primaryPath = primaryStorage.absolutePath
          val folderData = CoreMediaScanner.getFolderRecursiveData(context, primaryPath, playbackStates, thresholdDays)
          roots.add(
            FileSystemItem.Folder(
              name = "Internal Storage",
              path = primaryPath,
              lastModified = primaryStorage.lastModified(),
              videoCount = folderData?.videoCount ?: 0,
              audioCount = folderData?.audioCount ?: 0,
              totalSize = folderData?.totalSize ?: 0L,
              totalDuration = folderData?.totalDuration ?: 0L,
              hasSubfolders = true,
              newCount = folderData?.newCount ?: 0
            ),
          )
        }

        // External volumes
        val externalVolumes = StorageVolumeUtils.getExternalStorageVolumes(context)
        for (volume in externalVolumes) {
          val volumePath = StorageVolumeUtils.getVolumePath(volume) ?: continue
          val volumeDir = File(volumePath)
          if (volumeDir.exists() && volumeDir.canRead()) {
            val folderData = CoreMediaScanner.getFolderRecursiveData(context, volumePath, playbackStates, thresholdDays)
            roots.add(
              FileSystemItem.Folder(
                name = volume.getDescription(context),
                path = volumeDir.absolutePath,
                lastModified = volumeDir.lastModified(),
                videoCount = folderData?.videoCount ?: 0,
                audioCount = folderData?.audioCount ?: 0,
                totalSize = folderData?.totalSize ?: 0L,
                totalDuration = folderData?.totalDuration ?: 0L,
                hasSubfolders = true,
                newCount = folderData?.newCount ?: 0
              ),
            )
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error getting storage roots", e)
      }
      roots
    }
}
