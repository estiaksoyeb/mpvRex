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

    fun loadShorts() {
        viewModelScope.launch {
            _isLoading.value = true
            val discoveredShorts = ShortsDiscoveryOps.discoverShorts(
                getApplication(),
                shortsMediaDao,
                metadataCache,
                browserPreferences
            )
            
            // Apply persistent shuffle if enabled
            val finalShorts = if (browserPreferences.persistentShuffle.get()) {
                discoveredShorts.shuffled()
            } else {
                discoveredShorts
            }
            
            _shorts.value = finalShorts
            _isLoading.value = false
        }
    }

    suspend fun getThumbnail(video: Video): Bitmap? {
        return thumbnailRepository.getThumbnail(video, 1080, 1920)
    }

    fun toggleShuffle(currentIndex: Int) {
        val newState = !browserPreferences.persistentShuffle.get()
        browserPreferences.persistentShuffle.set(newState)
        
        if (newState) {
            shuffleShorts(currentIndex)
        } else {
            // Optional: Reload list to original order if disabled? 
            // For now, just toggling state is fine as shuffling is destructive to the list order.
        }
    }

    fun shuffleShorts(currentIndex: Int) {
        val currentList = _shorts.value
        if (currentList.isEmpty()) return
        
        val currentVideo = currentList.getOrNull(currentIndex) ?: return
        
        // Shuffle everything EXCEPT the current video, then re-insert it at the same index
        // to prevent the "title jump" while playing.
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

    fun blockVideo(video: Video) {
        viewModelScope.launch {
            val current = shortsMediaDao.getShortsMediaByPath(video.path)
            val newEntity = current?.copy(isBlocked = true)
                ?: ShortsMediaEntity(path = video.path, isBlocked = true, addedDate = System.currentTimeMillis())
            shortsMediaDao.upsert(newEntity)
            
            // NOTE: We no longer remove from _shorts immediately. 
            // This prevents index shifting that causes other videos to "pop" into the current view.
            // The video will be excluded next time loadShorts() is called.
        }
    }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ShortsViewModel(application) as T
        }
    }
}
