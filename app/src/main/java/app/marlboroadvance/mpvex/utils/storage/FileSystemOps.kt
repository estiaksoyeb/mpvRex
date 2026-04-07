package app.marlboroadvance.mpvex.utils.storage

import android.content.Context
import android.os.Environment
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.browser.PathComponent
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Operations for filesystem-level tasks like path parsing, directory scanning,
 * and storage volume management.
 */
object FileSystemOps {

    /**
     * Parses a path into breadcrumb components.
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
     * Gets all storage volume roots with recursive counts.
     */
    suspend fun getStorageRoots(context: Context): List<FileSystemItem.Folder> =
        withContext(Dispatchers.IO) {
            val roots = mutableListOf<FileSystemItem.Folder>()
            try {
                val koin = GlobalContext.get()
                val appearancePreferences = koin.get<AppearancePreferences>()
                val playbackStateRepository = koin.get<PlaybackStateRepository>()
                
                val playbackStates = playbackStateRepository.getAllPlaybackStates()
                val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()

                // Internal Storage
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
                        )
                    )
                }

                // External Volumes (SD Cards, USB)
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
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Log and return what we have
            }
            roots
        }
}
