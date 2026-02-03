package org.firefly.player.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.firefly.player.R
import org.firefly.player.model.Video

class VideoAdapter(
    private val onVideoClick: (Video) -> Unit
) : ListAdapter<Video, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view, onVideoClick)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class VideoViewHolder(
        itemView: View,
        private val onVideoClick: (Video) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val thumbnailView: ImageView = itemView.findViewById(R.id.videoThumbnail)
        private val titleView: TextView = itemView.findViewById(R.id.videoTitle)
        private val durationView: TextView = itemView.findViewById(R.id.videoDuration)
        private val sizeView: TextView = itemView.findViewById(R.id.videoSize)
        
        fun bind(video: Video) {
            titleView.text = video.title
            durationView.text = video.getFormattedDuration()
            sizeView.text = video.getFormattedSize()
            
            // Load thumbnail using Glide
            Glide.with(itemView.context)
                .load(video.uri)
                .centerCrop()
                .placeholder(R.drawable.ic_video_placeholder)
                .error(R.drawable.ic_video_placeholder)
                .into(thumbnailView)
            
            itemView.setOnClickListener {
                onVideoClick(video)
            }
        }
    }
    
    class VideoDiffCallback : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean {
            return oldItem == newItem
        }
    }
}
