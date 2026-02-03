package org.firefly.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import org.firefly.player.model.Video
import org.firefly.player.repository.VideoRepository
import kotlinx.coroutines.launch

class VideoViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = VideoRepository(application.contentResolver)
    
    private val _videos = MutableLiveData<List<Video>>()
    val videos: LiveData<List<Video>> = _videos
    
    private val _groupedVideos = MutableLiveData<Map<String, List<Video>>>()
    val groupedVideos: LiveData<Map<String, List<Video>>> = _groupedVideos
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private var allVideos: List<Video> = emptyList()
    private var currentGroupType: GroupType = GroupType.NONE
    private var groupOrderAscending: Boolean = false
    
    enum class GroupType {
        NAME, DATE, SIZE, NONE
    }
    
    fun loadVideos() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                allVideos = repository.getAllVideos()
                applyGrouping()
                
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Error loading videos: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun groupBy(groupType: GroupType, ascending: Boolean = false) {
        currentGroupType = groupType
        groupOrderAscending = ascending
        applyGrouping()
    }
    
    fun setGroupOrder(ascending: Boolean) {
        groupOrderAscending = ascending
        applyGrouping()
    }
    
    fun getCurrentGroupOrder(): Boolean = groupOrderAscending
    fun getCurrentGroupType(): GroupType = currentGroupType
    
    private fun applyGrouping() {
        when (currentGroupType) {
            GroupType.NAME -> {
                _groupedVideos.value =
                    LinkedHashMap(repository.groupByName(allVideos, groupOrderAscending))
                _videos.value = emptyList()
            }
            GroupType.DATE -> {
                _groupedVideos.value =
                    LinkedHashMap(repository.groupByDate(allVideos, groupOrderAscending))
                _videos.value = emptyList()
            }
            GroupType.SIZE -> {
                _groupedVideos.value =
                    LinkedHashMap(repository.groupBySize(allVideos, groupOrderAscending))
                _videos.value = emptyList()
            }
            GroupType.NONE -> {
                _videos.value = repository.sortByDate(allVideos, false)
                _groupedVideos.value = emptyMap()
            }
        }
    }
    
    fun searchVideos(query: String) {
        if (query.isEmpty()) {
            applyGrouping()
            return
        }
        
        val filteredVideos = allVideos.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.path.contains(query, ignoreCase = true)
        }
        
        _videos.value = repository.sortByDate(filteredVideos, false)
        _groupedVideos.value = emptyMap()
    }
}
