package org.firefly.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import org.firefly.player.adapter.GroupedVideoAdapter
import org.firefly.player.adapter.VideoAdapter
import org.firefly.player.databinding.ActivityMainBinding
import org.firefly.player.model.Video
import org.firefly.player.viewmodel.VideoViewModel

class MainActivity_old : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: VideoViewModel
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var groupedAdapter: GroupedVideoAdapter
    private var isGrouped = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadVideos()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        setupViewModel()
        setupRecyclerView()
        checkPermissionsAndLoadVideos()
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Firefly Player"
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[VideoViewModel::class.java]

        viewModel.videos.observe(this) { videos ->
            if (!isGrouped) {
                videoAdapter.submitList(videos)
                binding.emptyView.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (videos.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        viewModel.groupedVideos.observe(this) { groupedVideos ->
            if (isGrouped && groupedVideos.isNotEmpty()) {
                groupedAdapter.submitGroups(groupedVideos)
                binding.emptyView.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter { video ->
            openVideo(video)
        }

        groupedAdapter = GroupedVideoAdapter { video ->
            openVideo(video)
        }

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = videoAdapter
        }
    }

    private fun checkPermissionsAndLoadVideos() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == 
                PackageManager.PERMISSION_GRANTED -> {
                loadVideos()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                showPermissionRationaleDialog(permission)
            }
            else -> {
                permissionLauncher.launch(permission)
            }
        }
    }

    private fun loadVideos() {
        viewModel.loadVideos()
    }

    private fun openVideo(video: Video) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_VIDEO, video)
        }
        startActivity(intent)
    }

    private fun showPermissionRationaleDialog(permission: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs access to your videos to display them.")
            .setPositiveButton("OK") { _, _ ->
                permissionLauncher.launch(permission)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Without storage permission, this app cannot access your videos. Please grant permission in Settings.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.searchVideos(newText ?: "")
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_group -> {
                showGroupDialog()
                true
            }
            R.id.action_order -> {
                showOrderDialog()
                true
            }
            R.id.action_refresh -> {
                loadVideos()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showGroupDialog() {
        val options = arrayOf(
            "No Grouping",
            "Group by Name",
            "Group by Date",
            "Group by Size"
        )

        AlertDialog.Builder(this)
            .setTitle("Group By")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // No grouping - show all videos sorted by date (newest first)
                        isGrouped = false
                        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
                        binding.recyclerView.adapter = videoAdapter
                        viewModel.groupBy(VideoViewModel.GroupType.NONE)
                    }
                    1 -> {
                        // Group by Name - then ask for order
                        showGroupOrderDialog(VideoViewModel.GroupType.NAME)
                    }
                    2 -> {
                        // Group by Date - then ask for order
                        showGroupOrderDialog(VideoViewModel.GroupType.DATE)
                    }
                    3 -> {
                        // Group by Size - then ask for order
                        showGroupOrderDialog(VideoViewModel.GroupType.SIZE)
                    }
                }
            }
            .show()
    }

    private fun showGroupOrderDialog(groupType: VideoViewModel.GroupType) {
        val orderOptions = when (groupType) {
            VideoViewModel.GroupType.NAME -> arrayOf(
                "A-Z within each letter",
                "Z-A within each letter"
            )
            VideoViewModel.GroupType.DATE -> arrayOf(
                "Oldest first within each month",
                "Newest first within each month"
            )
            VideoViewModel.GroupType.SIZE -> arrayOf(
                "Smallest first within each range",
                "Largest first within each range"
            )
            else -> arrayOf("Ascending", "Descending")
        }

        // Set default selection based on group type
        val defaultSelection = when (groupType) {
            VideoViewModel.GroupType.DATE -> 1  // Default to Newest first within month
            VideoViewModel.GroupType.SIZE -> 1  // Default to Largest first within range
            else -> 0  // Default to ascending for name
        }

        AlertDialog.Builder(this)
            .setTitle("Sort videos within groups")
            .setSingleChoiceItems(orderOptions, defaultSelection) { dialog, which ->
                val ascending = which == 0
                
                // Switch to grouped layout
                isGrouped = true
                binding.recyclerView.layoutManager = LinearLayoutManager(this)
                binding.recyclerView.adapter = groupedAdapter
                
                // Apply grouping with selected order
                viewModel.groupBy(groupType, ascending)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOrderDialog() {
        val currentGroupType = viewModel.getCurrentGroupType()
        
        if (currentGroupType == VideoViewModel.GroupType.NONE) {
            Toast.makeText(this, "Please select a grouping first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentOrder = viewModel.getCurrentGroupOrder()
        val currentSelection = if (currentOrder) 0 else 1
        
        val orderOptions = when (currentGroupType) {
            VideoViewModel.GroupType.NAME -> arrayOf(
                "A-Z within each letter",
                "Z-A within each letter"
            )
            VideoViewModel.GroupType.DATE -> arrayOf(
                "Oldest first within each month",
                "Newest first within each month"
            )
            VideoViewModel.GroupType.SIZE -> arrayOf(
                "Smallest first within each range",
                "Largest first within each range"
            )
            else -> arrayOf("Ascending", "Descending")
        }

        AlertDialog.Builder(this)
            .setTitle("Change video order within groups")
            .setSingleChoiceItems(orderOptions, currentSelection) { dialog, which ->
                val ascending = which == 0
                viewModel.setGroupOrder(ascending)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}