package org.firefly.player.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Video(
    val id: Long,
    val title: String,
    val uri: Uri,
    val path: String,
    val duration: Long,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0
) : Parcelable {
    
    fun getFormattedDuration(): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
            else -> String.format("%02d:%02d", minutes, seconds % 60)
        }
    }
    
    fun getFormattedSize(): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            else -> String.format("%.2f KB", kb)
        }
    }
}