package org.firefly.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import org.firefly.player.adapter.GroupedVideoAdapter
import org.firefly.player.adapter.VideoAdapter
import org.firefly.player.databinding.ActivityMainBinding
import org.firefly.player.model.Video
import org.firefly.player.viewmodel.VideoViewModel
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.LayerDrawable

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: VideoViewModel
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var groupedAdapter: GroupedVideoAdapter
    private var isGrouped = false
    private var searchView: SearchView? = null

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
        startFireflyAnimation()

     
    }
   private fun startFireflyAnimation() {
    val rootLayout = findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.rootLayout)
    val background = rootLayout.background
    
    // The background is a LayerDrawable, we need to get the second layer (index 1)
    if (background is LayerDrawable && background.numberOfLayers > 1) {
        val fireflyLayer = background.getDrawable(1) // Index 1 is the firefly animation layer
        if (fireflyLayer is AnimationDrawable) {
            fireflyLayer.start()
        }
    }
}
    private fun setupActionBar() {
        setSupportActionBar(binding.toolbar)
        // NO title - just the toolbar with icons
        supportActionBar?.setDisplayShowTitleEnabled(false)
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
        searchView = searchItem.actionView as SearchView

        // Listen for when SearchView is closed (back button clicked)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true // Allow expansion
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                // When search is collapsed, restore the original state
                val currentGroupType = viewModel.getCurrentGroupType()
                
                if (currentGroupType != VideoViewModel.GroupType.NONE && !isGrouped) {
                    // Restore grouped view
                    isGrouped = true
                    binding.recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                    binding.recyclerView.adapter = groupedAdapter
                } else if (currentGroupType == VideoViewModel.GroupType.NONE && isGrouped) {
                    // Restore ungrouped view
                    isGrouped = false
                    binding.recyclerView.layoutManager = GridLayoutManager(this@MainActivity, 2)
                    binding.recyclerView.adapter = videoAdapter
                }
                
                // Trigger reload to show all videos
                viewModel.searchVideos("")
                return true // Allow collapse
            }
        })

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    // When search text is cleared but SearchView is still open
                    viewModel.searchVideos("")
                } else {
                    // When searching, force ungrouped view
                    if (isGrouped) {
                        isGrouped = false
                        binding.recyclerView.layoutManager = GridLayoutManager(this@MainActivity, 2)
                        binding.recyclerView.adapter = videoAdapter
                    }
                    viewModel.searchVideos(newText)
                }
                
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_group -> {
                searchView?.setQuery("", false)
                searchView?.isIconified = true
                showSortGroupDialog()
                true
            }
            R.id.action_order -> {
                searchView?.setQuery("", false)
                searchView?.isIconified = true
                showSortGroupDialog()
                true
            }
            R.id.action_refresh -> {
                searchView?.setQuery("", false)
                searchView?.isIconified = true
                loadVideos()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortGroupDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sort_group, null)
        
        val groupByRadioGroup = dialogView.findViewById<RadioGroup>(R.id.groupByRadioGroup)
        val sortByRadioGroup = dialogView.findViewById<RadioGroup>(R.id.sortByRadioGroup)
        val sortAscending = dialogView.findViewById<RadioButton>(R.id.sortByName)
        val sortDescending = dialogView.findViewById<RadioButton>(R.id.sortByDate)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnApply = dialogView.findViewById<MaterialButton>(R.id.btnApply)
        
        // Set current selections
        val currentGroupType = viewModel.getCurrentGroupType()
        val currentOrder = viewModel.getCurrentGroupOrder()
        
        // Set group by selection
        when (currentGroupType) {
            VideoViewModel.GroupType.NONE -> dialogView.findViewById<RadioButton>(R.id.groupByNone).isChecked = true
            VideoViewModel.GroupType.NAME -> dialogView.findViewById<RadioButton>(R.id.groupByFolder).isChecked = true
            VideoViewModel.GroupType.DATE -> dialogView.findViewById<RadioButton>(R.id.groupByDate).isChecked = true
            VideoViewModel.GroupType.SIZE -> dialogView.findViewById<RadioButton>(R.id.groupBySize).isChecked = true
        }
        
        // Set sort by selection
        if (currentOrder) {
            sortAscending.isChecked = true
        } else {
            sortDescending.isChecked = true
        }
        
        // Function to update sort radio button text based on selected group
        val updateSortText: (VideoViewModel.GroupType) -> Unit = { groupType ->
            when (groupType) {
                VideoViewModel.GroupType.NONE -> {
                    sortAscending.text = "Ascending (A→Z, Old→New, Small→Large)"
                    sortDescending.text = "Descending (Z→A, New→Old, Large→Small)"
                }
                VideoViewModel.GroupType.NAME -> {
                    sortAscending.text = "A-Z within each folder"
                    sortDescending.text = "Z-A within each folder"
                }
                VideoViewModel.GroupType.DATE -> {
                    sortAscending.text = "Oldest first within each month"
                    sortDescending.text = "Newest first within each month"
                }
                VideoViewModel.GroupType.SIZE -> {
                    sortAscending.text = "Smallest first within each range"
                    sortDescending.text = "Largest first within each range"
                }
            }
        }
        
        // Set initial sort text
        updateSortText(currentGroupType)
        
        // Update sort text when group selection changes
        groupByRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val groupType = when (checkedId) {
                R.id.groupByNone -> VideoViewModel.GroupType.NONE
                R.id.groupByFolder -> VideoViewModel.GroupType.NAME
                R.id.groupByDate -> VideoViewModel.GroupType.DATE
                R.id.groupBySize -> VideoViewModel.GroupType.SIZE
                else -> VideoViewModel.GroupType.NONE
            }
            updateSortText(groupType)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnApply.setOnClickListener {
            val selectedGroupId = groupByRadioGroup.checkedRadioButtonId
            val selectedSortId = sortByRadioGroup.checkedRadioButtonId
            
            // Determine group type
            val groupType = when (selectedGroupId) {
                R.id.groupByNone -> VideoViewModel.GroupType.NONE
                R.id.groupByFolder -> VideoViewModel.GroupType.NAME
                R.id.groupByDate -> VideoViewModel.GroupType.DATE
                R.id.groupBySize -> VideoViewModel.GroupType.SIZE
                else -> VideoViewModel.GroupType.NONE
            }
            
            // Determine sort order (ascending = true, descending = false)
            val ascending = selectedSortId == R.id.sortByName
            
            val currentGroupType = viewModel.getCurrentGroupType()
            val isChangingGroupType = currentGroupType != groupType
            
            // Only fade when changing between grouped/ungrouped or changing group type
            if (isChangingGroupType) {
                // Smooth fade animation
                binding.recyclerView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        // Apply grouping
                        if (groupType == VideoViewModel.GroupType.NONE) {
                            isGrouped = false
                            binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
                            binding.recyclerView.adapter = videoAdapter
                            viewModel.groupBy(VideoViewModel.GroupType.NONE)
                        } else {
                            isGrouped = true
                            binding.recyclerView.layoutManager = LinearLayoutManager(this)
                            binding.recyclerView.adapter = groupedAdapter
                            viewModel.groupBy(groupType, ascending)
                        }
                        
                        // Fade back in
                        binding.recyclerView.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            } else {
                // Just changing sort order - smooth rearrangement (no fade)
                viewModel.groupBy(groupType, ascending)
            }
            
            dialog.dismiss()
        }
        
        dialog.show()
    }
}