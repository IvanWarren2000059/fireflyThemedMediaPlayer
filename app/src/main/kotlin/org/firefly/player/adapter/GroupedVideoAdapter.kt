package org.firefly.player.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.firefly.player.R
import org.firefly.player.model.Video

class GroupedVideoAdapter(
    private val onVideoClick: (Video) -> Unit
) : RecyclerView.Adapter<GroupedVideoAdapter.GroupViewHolder>() {

    private var groups: List<Pair<String, List<Video>>> = emptyList()

    fun submitGroups(groupedVideos: Map<String, List<Video>>) {
        // Preserve the order from the Map - it's already sorted correctly in VideoRepository
        groups = groupedVideos.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_group, parent, false)
        return GroupViewHolder(view, onVideoClick)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount(): Int = groups.size

    class GroupViewHolder(
        itemView: View,
        private val onVideoClick: (Video) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val headerView: TextView = itemView.findViewById(R.id.groupHeader)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.groupRecyclerView)
        private val adapter = VideoAdapter(onVideoClick)
        
        init {
            recyclerView.layoutManager = LinearLayoutManager(itemView.context)
            recyclerView.adapter = adapter
        }
        
        fun bind(group: Pair<String, List<Video>>) {
            headerView.text = "${group.first} (${group.second.size})"
            adapter.submitList(group.second)
        }
    }
}