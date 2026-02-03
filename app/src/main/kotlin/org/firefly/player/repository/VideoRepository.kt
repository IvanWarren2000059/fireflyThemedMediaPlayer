package org.firefly.player.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.firefly.player.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(private val contentResolver: ContentResolver) {

    suspend fun getAllVideos(): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        
        contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: cursor.getString(displayNameColumn)
                val path = cursor.getString(pathColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                videos.add(
                    Video(
                        id = id,
                        title = title,
                        uri = uri,
                        path = path,
                        duration = duration,
                        size = size,
                        dateAdded = dateAdded,
                        dateModified = dateModified,
                        mimeType = mimeType,
                        width = width,
                        height = height
                    )
                )
            }
        }
        
        videos
    }
    
    fun sortByName(videos: List<Video>, ascending: Boolean = true): List<Video> {
        return if (ascending) {
            videos.sortedBy { it.title.lowercase() }
        } else {
            videos.sortedByDescending { it.title.lowercase() }
        }
    }
    
    fun sortByDate(videos: List<Video>, ascending: Boolean = true): List<Video> {
        return if (ascending) {
            videos.sortedBy { it.dateAdded }
        } else {
            videos.sortedByDescending { it.dateAdded }
        }
    }
    
    fun sortBySize(videos: List<Video>, ascending: Boolean = true): List<Video> {
        return if (ascending) {
            videos.sortedBy { it.size }
        } else {
            videos.sortedByDescending { it.size }
        }
    }
    
    fun groupByName(videos: List<Video>, ascending: Boolean = true): Map<String, List<Video>> {
        // Group videos by first letter
        val grouped = videos.groupBy { video ->
            video.title.firstOrNull()?.uppercase() ?: "#"
        }
        
        // Sort videos within each group based on ascending parameter
        val sortedGroups = grouped.mapValues { (_, groupVideos) ->
            if (ascending) {
                // A-Z within the group (Aba, Deda, Deka)
                groupVideos.sortedBy { it.title.lowercase() }
            } else {
                // Z-A within the group (Deka, Deda, Aba)
                groupVideos.sortedByDescending { it.title.lowercase() }
            }
        }
        
        // Groups themselves are ALWAYS A-Z
        return sortedGroups.toSortedMap()
    }
    
    fun groupByDate(videos: List<Video>, ascending: Boolean = true): Map<String, List<Video>> {
        // Group videos by month and year
        val grouped = videos.groupBy { video ->
            val date = java.util.Date(video.dateAdded * 1000)
            val format = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            format.format(date)
        }
        
        // Sort videos within each group based on ascending parameter
        val sortedGroups = grouped.mapValues { (_, groupVideos) ->
            if (ascending) {
                // Oldest first within the month
                groupVideos.sortedBy { it.dateAdded }
            } else {
                // Newest first within the month
                groupVideos.sortedByDescending { it.dateAdded }
            }
        }
        
        // Custom comparator for date strings - ALWAYS newest groups first
        val dateComparator = Comparator<String> { date1, date2 ->
            try {
                val format = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                val d1 = format.parse(date1)
                val d2 = format.parse(date2)
                when {
                    d1 == null && d2 == null -> 0
                    d1 == null -> 1
                    d2 == null -> -1
                    // Always newest groups first (2026, 2025, 2024...)
                    else -> d2.compareTo(d1)
                }
            } catch (e: Exception) {
                date2.compareTo(date1) // Reverse string comparison as fallback
            }
        }
        
        // Groups themselves are ALWAYS newest → oldest (2026, 2025, 2024...)
        return sortedGroups.toSortedMap(dateComparator)
    }
    
    fun groupBySize(videos: List<Video>, ascending: Boolean = true): Map<String, List<Video>> {
        // Group videos by size range
        val grouped = videos.groupBy { video ->
            val mb = video.size / (1024.0 * 1024.0)
            when {
                mb < 10 -> "< 10 MB"
                mb < 50 -> "10-50 MB"
                mb < 100 -> "50-100 MB"
                mb < 500 -> "100-500 MB"
                mb < 1024 -> "500 MB - 1 GB"
                else -> "> 1 GB"
            }
        }
        
        // Sort videos within each group based on ascending parameter
        val sortedGroups = grouped.mapValues { (_, groupVideos) ->
            if (ascending) {
                // Smallest first within the size range
                groupVideos.sortedBy { it.size }
            } else {
                // Largest first within the size range
                groupVideos.sortedByDescending { it.size }
            }
        }
        
        // Define size order - ALWAYS smallest to largest groups
        val sizeOrder = listOf(
            "< 10 MB",
            "10-50 MB",
            "50-100 MB",
            "100-500 MB",
            "500 MB - 1 GB",
            "> 1 GB"
        )
        
        val sizeComparator = Comparator<String> { size1, size2 ->
            val index1 = sizeOrder.indexOf(size1)
            val index2 = sizeOrder.indexOf(size2)
            index1.compareTo(index2)
        }
        
        // Groups themselves are ALWAYS smallest → largest
        return sortedGroups.toSortedMap(sizeComparator)
    }
}