package app.marlboroadvance.mpvex.ui.browser.shorts

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.dao.ShortsMediaDao
import app.marlboroadvance.mpvex.database.entities.ShortsMediaEntity
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.utils.media.ShortsDiscoveryOps
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ShortsViewModel(
    application: Application
) : AndroidViewModel(application), KoinComponent {

    private val shortsMediaDao: ShortsMediaDao by inject()
    private val browserPreferences: BrowserPreferences by inject()
    private val metadataCache: VideoMetadataCacheRepository by inject()
    private val thumbnailRepository: ThumbnailRepository by inject()

    private val _shorts = MutableStateFlow<List<Video>>(emptyList())
    val shorts: StateFlow<List<Video>> = _shorts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val lovedPaths: StateFlow<Set<String>> = shortsMediaDao.observeAllShortsMedia()
        .map { list -> list.filter { it.isLoved }.map { it.path }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val blockedPaths: StateFlow<Set<String>> = shortsMediaDao.observeAllShortsMedia()
        .map { list -> list.filter { it.isBlocked }.map { it.path }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isShuffleEnabled: StateFlow<Boolean> = browserPreferences.persistentShuffle.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), browserPreferences.persistentShuffle.get())

    private val _currentSpeed = MutableStateFlow(1.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow()
    
    // --- Phase B: Session History ---
    private val seenPaths = mutableSetOf<String>()

    fun loadShorts(initialVideoPath: String? = null, blockedOnly: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            
            val finalShorts = if (blockedOnly) {
                // Phase 4: Blocked Only Mode (Skip weighted logic)
                val blockedInDb = shortsMediaDao.getAllShortsMedia().filter { it.isBlocked }
                val flatFolders = app.marlboroadvance.mpvex.utils.storage.CoreMediaScanner.getFlatMediaFolders(getApplication())
                val allVideos = flatFolders.flatMap { folder ->
                    app.marlboroadvance.mpvex.utils.storage.VideoScanUtils.getVideosInFolder(getApplication(), folder.path)
                }.filter { !it.isAudio }
                
                val blockedPathsSet = blockedInDb.map { it.path }.toSet()
                allVideos.filter { it.path in blockedPathsSet }
            } else {
                val discoveredShorts = ShortsDiscoveryOps.discoverShorts(
                    getApplication(),
                    shortsMediaDao,
                    metadataCache,
                    browserPreferences
                )
                
                // --- Phase B: Smart Weighted Interleaving ---
                val loved = discoveredShorts.filter { lovedPaths.value.contains(it.path) }.shuffled().toMutableList()
                val others = discoveredShorts.filter { !lovedPaths.value.contains(it.path) }.shuffled().toMutableList()
                
                val interleaved = mutableListOf<Video>()
                
                // Interleave in a 2:1 ratio (2 normal, 1 loved)
                while (loved.isNotEmpty() || others.isNotEmpty()) {
                    // Add up to 2 "others"
                    repeat(2) {
                        if (others.isNotEmpty()) interleaved.add(others.removeAt(0))
                    }
                    // Add 1 "loved"
                    if (loved.isNotEmpty()) interleaved.add(loved.removeAt(0))
                }
                
                interleaved
            }
            
            // If an initial video is specified, move it to the front
            val orderedShorts = if (initialVideoPath != null) {
                val initial = finalShorts.find { it.path == initialVideoPath }
                if (initial != null) {
                    listOf(initial) + finalShorts.filter { it.path != initialVideoPath }
                } else {
                    finalShorts
                }
            } else {
                finalShorts
            }
            
            _shorts.value = orderedShorts
            _isLoading.value = false
        }
    }
    
    /**
     * Mark a video as seen in the current session.
     */
    fun markAsSeen(video: Video) {
        seenPaths.add(video.path)
    }

    suspend fun getThumbnail(video: Video): Bitmap? {
        return thumbnailRepository.getThumbnail(video, 1080, 1920)
    }

    fun updatePlaybackSpeed() {
        _currentSpeed.value = MPVLib.getPropertyDouble("speed") ?: 1.0
    }

    fun toggleShuffle(currentIndex: Int) {
        val newState = !browserPreferences.persistentShuffle.get()
        browserPreferences.persistentShuffle.set(newState)
        
        if (newState) {
            shuffleShorts(currentIndex)
        }
    }

    fun shuffleShorts(currentIndex: Int) {
        val currentList = _shorts.value
        if (currentList.isEmpty()) return
        
        val currentVideo = currentList.getOrNull(currentIndex) ?: return
        
        val mutableList = currentList.toMutableList()
        mutableList.removeAt(currentIndex)
        mutableList.shuffle()
        mutableList.add(currentIndex, currentVideo)
        
        _shorts.value = mutableList
    }

    fun toggleLove(video: Video) {
        viewModelScope.launch {
            val current = shortsMediaDao.getShortsMediaByPath(video.path)
            val isLoved = current?.isLoved ?: false
            val newEntity = current?.copy(isLoved = !isLoved) 
                ?: ShortsMediaEntity(path = video.path, isLoved = true)
            shortsMediaDao.upsert(newEntity)
        }
    }

    fun toggleBlock(video: Video) {
        viewModelScope.launch {
            val current = shortsMediaDao.getShortsMediaByPath(video.path)
            val isBlocked = current?.isBlocked ?: false
            val newEntity = current?.copy(isBlocked = !isBlocked)
                ?: ShortsMediaEntity(path = video.path, isBlocked = true, addedDate = System.currentTimeMillis())
            shortsMediaDao.upsert(newEntity)
        }
    }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ShortsViewModel(application) as T
        }
    }
}
