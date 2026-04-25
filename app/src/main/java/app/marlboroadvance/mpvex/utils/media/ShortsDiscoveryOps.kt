package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.util.Log
import app.marlboroadvance.mpvex.database.dao.ShortsMediaDao
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.utils.storage.CoreMediaScanner
import app.marlboroadvance.mpvex.utils.storage.VideoScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Operations for discovering and filtering vertical videos (Shorts).
 */
object ShortsDiscoveryOps {
    private const val TAG = "ShortsDiscoveryOps"

    /**
     * Discovers all vertical videos across all scanned media folders.
     */
    suspend fun discoverShorts(
        context: Context,
        shortsMediaDao: ShortsMediaDao,
        metadataCache: VideoMetadataCacheRepository,
        browserPreferences: BrowserPreferences
    ): List<Video> = withContext(Dispatchers.IO) {
        try {
            // 1. Get all folders that contain media
            val flatFolders = CoreMediaScanner.getFlatMediaFolders(context)
            
            // 2. Extract all videos from these folders
            val allVideos = flatFolders.flatMap { folder ->
                VideoScanUtils.getVideosInFolder(context, folder.path)
            }.filter { !it.isAudio }

            // 3. Enrich videos with metadata (we need width/height)
            // Even if chips are disabled, we NEED metadata for vertical detection.
            // We use MetadataRetrieval but we must ensure we get the dimensions.
            val enrichedVideos = MetadataRetrieval.enrichVideosIfNeeded(
                context, allVideos, browserPreferences, metadataCache
            )

            // 4. Get shorts metadata from DB
            val shortsMetadata = shortsMediaDao.getAllShortsMedia().associateBy { it.path }

            // 5. Filter for vertical videos or manually added shorts, excluding blocked
            enrichedVideos.filter { video ->
                val metadata = shortsMetadata[video.path]
                val isBlocked = metadata?.isBlocked ?: false
                if (isBlocked) return@filter false

                val isManuallyAdded = metadata?.isManuallyAdded ?: false
                
                // If width/height are still 0 (because enrichment was skipped by prefs),
                // we might need to extract them anyway for the vertical check.
                var width = video.width
                var height = video.height
                
                if (width == 0 || height == 0) {
                   val file = java.io.File(video.path)
                   if (file.exists()) {
                       val meta = metadataCache.getOrExtractMetadata(file, video.uri, video.displayName)
                       if (meta != null) {
                           width = meta.width
                           height = meta.height
                       }
                   }
                }

                val isVertical = height > width && height > 0

                isVertical || isManuallyAdded
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering shorts", e)
            emptyList()
        }
    }
}
