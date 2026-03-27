package app.marlboroadvance.mpvex.utils.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Tree View Scanner - Optimized for tree/browser view
 * 
 * Includes parent folders and recursive counts
 * Used for directory navigation with hierarchical structure
 */
object TreeViewScanner {
    private const val TAG = "TreeViewScanner"
    
    // Smart cache with short TTL (30 seconds)
    private var cachedTreeViewData: Map<String, FolderData>? = null
    private var cacheTimestamp: Long = 0
    private const val CACHE_TTL_MS = 10_000L // 10 seconds for faster refresh
    
    /**
     * Clear cache (call when media library changes)
     */
    fun clearCache() {
        cachedTreeViewData = null
        cacheTimestamp = 0
    }
    
    /**
     * Folder metadata with recursive counts
     */
    data class FolderData(
        val path: String,
        val name: String,
        val videoCount: Int,
        val totalSize: Long,
        val totalDuration: Long,
        val lastModified: Long,
        val hasSubfolders: Boolean = false
    )
    
    /**
     * Helper data class for video info during scanning
     */
    private data class VideoInfo(
        val size: Long,
        val duration: Long,
        val dateModified: Long
    )
    
    /**
     * Get direct child folders of a parent directory for tree view
     * Uses recursive counts with parent hierarchy
     */
    suspend fun getFoldersInDirectory(
        context: Context,
        parentPath: String
    ): List<FolderData> = withContext(Dispatchers.IO) {
        val allFolders = getOrBuildTreeViewData(context)
        
        // Filter for direct children only
        allFolders.values.filter { folder ->
            val parent = File(folder.path).parent
            parent == parentPath
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }
    
    /**
     * Get folder data with recursive counts for a specific path
     * Used for storage roots to show total video counts
     */
    suspend fun getFolderDataRecursive(
        context: Context,
        folderPath: String
    ): FolderData? = withContext(Dispatchers.IO) {
        val allFolders = getOrBuildTreeViewData(context)
        
        // First try exact match
        allFolders[folderPath]?.let { return@withContext it }
        
        // If no exact match, aggregate from ALL descendants (not just immediate children)
        // This is needed for storage roots that don't have direct videos
        var totalCount = 0
        var totalSize = 0L
        var totalDuration = 0L
        var lastModified = 0L
        var hasSubfolders = false
        
        // Find all descendants (any folder that starts with this path)
        for ((path, data) in allFolders) {
            if (path.startsWith("$folderPath${File.separator}")) {
                // Check if this is a direct child (immediate subdirectory)
                val relativePath = path.substring(folderPath.length + 1)
                val isDirectChild = !relativePath.contains(File.separator)
                
                if (isDirectChild) {
                    // Direct child - its counts are already recursive, so just add them
                    totalCount += data.videoCount
                    totalSize += data.totalSize
                    totalDuration += data.totalDuration
                    if (data.lastModified > lastModified) {
                        lastModified = data.lastModified
                    }
                    hasSubfolders = true
                }
            }
        }
        
        if (totalCount > 0) {
            FolderData(
                path = folderPath,
                name = File(folderPath).name,
                videoCount = totalCount,
                totalSize = totalSize,
                totalDuration = totalDuration,
                lastModified = lastModified,
                hasSubfolders = hasSubfolders
            )
        } else {
            null
        }
    }
    
    /**
     * Get or build tree view data with smart caching
     */
    private suspend fun getOrBuildTreeViewData(
        context: Context
    ): Map<String, FolderData> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        
        // Return cached data if still valid
        cachedTreeViewData?.let { cached ->
            if (now - cacheTimestamp < CACHE_TTL_MS) {
                return@withContext cached
            }
        }
        
        // Build fresh data
        val data = buildTreeViewData(context)
        
        // Update cache
        cachedTreeViewData = data
        cacheTimestamp = now
        
        data
    }
    
    /**
     * Build tree view data (no caching)
     */
    private suspend fun buildTreeViewData(
        context: Context
    ): Map<String, FolderData> = withContext(Dispatchers.IO) {
        val allFolders = mutableMapOf<String, FolderData>()
        
        // Step 1: Scan MediaStore with recursive counts
        scanMediaStoreRecursive(context, allFolders)
        
        // Step 2: Scan external volumes via filesystem (USB OTG, SD cards)
        scanExternalVolumes(context, allFolders)
        
        // Step 3: Build parent folder hierarchy
        buildParentHierarchy(allFolders)
        
        allFolders
    }
    
    /**
     * Scan MediaStore for all videos and audio, and build folder map (recursive counts)
     */
    private fun scanMediaStoreRecursive(
        context: Context,
        folders: MutableMap<String, FolderData>
    ) {
        // Collect media by folder
        val mediaByFolder = mutableMapOf<String, MutableList<VideoInfo>>()

        // Step 1: Scan Videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn) ?: continue
                    val file = File(path)
                    if (!file.exists()) continue
                    
                    val folderPath = file.parent ?: continue
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateColumn)
                    
                    mediaByFolder.getOrPut(folderPath) { mutableListOf() }.add(
                        VideoInfo(size, duration, dateModified)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore video scan error", e)
        }

        // Step 2: Scan Audio
        val audioProjection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn) ?: continue
                    val file = File(path)
                    if (!file.exists()) continue
                    
                    val folderPath = file.parent ?: continue
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateColumn)
                    
                    mediaByFolder.getOrPut(folderPath) { mutableListOf() }.add(
                        VideoInfo(size, duration, dateModified)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore audio scan error", e)
        }
        
        // Build folder data with recursive counts
        for ((folderPath, _) in mediaByFolder) {
            var totalCount = 0
            var totalSize = 0L
            var totalDuration = 0L
            var lastModified = 0L
            var hasSubfolders = false
            
            // Count all media in this folder and subdirectories
            for ((otherPath, items) in mediaByFolder) {
                if (otherPath == folderPath || otherPath.startsWith("$folderPath${File.separator}")) {
                    totalCount += items.size
                    for (item in items) {
                        totalSize += item.size
                        totalDuration += item.duration
                        if (item.dateModified > lastModified) {
                            lastModified = item.dateModified
                        }
                    }
                    if (otherPath != folderPath) {
                        hasSubfolders = true
                    }
                }
            }
            
            if (totalCount > 0) {
                folders[folderPath] = FolderData(
                    path = folderPath,
                    name = File(folderPath).name,
                    videoCount = totalCount,
                    totalSize = totalSize,
                    totalDuration = totalDuration,
                    lastModified = lastModified,
                    hasSubfolders = hasSubfolders
                )
            }
        }
    }
    
    /**
     * Scan external volumes (USB OTG, SD cards) via filesystem
     */
    private fun scanExternalVolumes(
        context: Context,
        folders: MutableMap<String, FolderData>
    ) {
        try {
            val externalVolumes = StorageVolumeUtils.getExternalStorageVolumes(context)
            
            if (externalVolumes.isEmpty()) {
                return
            }
            
            for (volume in externalVolumes) {
                val volumePath = StorageVolumeUtils.getVolumePath(volume)
                if (volumePath == null) {
                    continue
                }
                
                val volumeDir = File(volumePath)
                if (!volumeDir.exists() || !volumeDir.canRead()) {
                    continue
                }
                
                scanDirectoryRecursive(volumeDir, folders, maxDepth = 20)
            }
        } catch (e: Exception) {
            Log.e(TAG, "External volume scan error", e)
        }
    }
    
    /**
     * Recursively scan directory for videos
     */
    private fun scanDirectoryRecursive(
        directory: File,
        folders: MutableMap<String, FolderData>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth >= maxDepth) return
        if (!directory.exists() || !directory.canRead() || !directory.isDirectory) return
        
        try {
            val files = directory.listFiles() ?: return
            
            val mediaFiles = mutableListOf<File>()
            val subdirectories = mutableListOf<File>()
            
            for (file in files) {
                try {
                    when {
                        file.isDirectory -> {
                            if (!FileFilterUtils.shouldSkipFolder(file)) {
                                subdirectories.add(file)
                            }
                        }
                        file.isFile -> {
                            if (FileTypeUtils.isMediaFile(file)) {
                                mediaFiles.add(file)
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    continue
                }
            }
            
            // Add folder if it has media files
            if (mediaFiles.isNotEmpty()) {
                val folderPath = directory.absolutePath
                
                // Skip if already from MediaStore
                if (!folders.containsKey(folderPath)) {
                    var totalSize = 0L
                    var lastModified = 0L
                    
                    for (media in mediaFiles) {
                        totalSize += media.length()
                        val modified = media.lastModified()
                        if (modified > lastModified) {
                            lastModified = modified
                        }
                    }
                    
                    folders[folderPath] = FolderData(
                        path = folderPath,
                        name = directory.name,
                        videoCount = mediaFiles.size,
                        totalSize = totalSize,
                        totalDuration = 0L, // Duration not available from filesystem
                        lastModified = lastModified / 1000,
                        hasSubfolders = subdirectories.isNotEmpty()
                    )
                }
            }
            
            // Recurse into subdirectories
            for (subdir in subdirectories) {
                scanDirectoryRecursive(subdir, folders, maxDepth, currentDepth + 1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning: ${directory.absolutePath}", e)
        }
    }
    
    /**
     * Build parent folder hierarchy
     * Ensures intermediate folders (without direct videos) are included with correct recursive counts
     */
    private fun buildParentHierarchy(folders: MutableMap<String, FolderData>) {
        val allPaths = folders.keys.toSet()
        val parentsToAdd = mutableSetOf<String>()
        
        // Find all parent folders that need to be added
        for (folderPath in allPaths) {
            var currentPath = File(folderPath).parent
            while (currentPath != null) {
                if (currentPath == "/" || currentPath.length <= 1) break
                
                // Add parent even if it already exists - we'll recalculate its counts
                parentsToAdd.add(currentPath)
                currentPath = File(currentPath).parent
            }
        }
        
        // Process parents from deepest to shallowest to ensure correct aggregation
        val sortedParents = parentsToAdd.sortedByDescending { it.count { c -> c == File.separatorChar } }
        
        // Add/update parent folders with aggregated data
        for (parentPath in sortedParents) {
            var totalCount = 0
            var totalSize = 0L
            var totalDuration = 0L
            var lastModified = 0L
            var hasSubfolders = false
            
            // Aggregate from direct children only (their counts are already recursive)
            for ((folderPath, folderData) in folders) {
                val parent = File(folderPath).parent
                if (parent == parentPath) {
                    totalCount += folderData.videoCount
                    totalSize += folderData.totalSize
                    totalDuration += folderData.totalDuration
                    if (folderData.lastModified > lastModified) {
                        lastModified = folderData.lastModified
                    }
                    hasSubfolders = true
                }
            }
            
            if (totalCount > 0) {
                folders[parentPath] = FolderData(
                    path = parentPath,
                    name = File(parentPath).name,
                    videoCount = totalCount,
                    totalSize = totalSize,
                    totalDuration = totalDuration,
                    lastModified = lastModified,
                    hasSubfolders = hasSubfolders
                )
            }
        }
    }
}
