package org.firefly.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.firefly.player.databinding.ActivityPlayerBinding
import org.firefly.player.model.Video
import androidx.media3.ui.PlayerView

class PlayerActivity_older : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO = "extra_video"
        private const val SEEK_TIME_MS = 10000L // 10 seconds
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var video: Video? = null
    private var playWhenReady = true
    private var playbackPosition = 0L
    private lateinit var gestureDetector: GestureDetector
    private val handler = Handler(Looper.getMainLooper())
    private var seekOverlay: TextView? = null
    private var minimizeButton: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        video = if (intent.hasExtra(EXTRA_VIDEO)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_VIDEO, Video::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_VIDEO)
            }
        } else {
            // Handle external video opening
            intent.data?.let { uri ->
                Video(
                    id = 0,
                    title = uri.lastPathSegment ?: "Video",
                    uri = uri,
                    path = uri.path ?: "",
                    duration = 0,
                    size = 0,
                    dateAdded = 0,
                    dateModified = 0,
                    mimeType = intent.type ?: "video/*"
                )
            }
        }

        if (video == null) {
            finish()
            return
        }

        setupFullscreen()
        setupGestureDetector()
        setupPlayer()
        setupMinimizeButton()
    }

    private fun setupFullscreen() {
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Hide system bars and make them appear on swipe
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Make the player view use full screen
        @Suppress("DEPRECATION")
        binding.playerView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LOW_PROFILE
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )

        // Allow user to control orientation (sensor-based)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    private fun setupMinimizeButton() {
        // Create minimize button with better styling
        minimizeButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_minimize)
            setBackgroundResource(R.drawable.minimize_button_background)
            imageTintList = ContextCompat.getColorStateList(context, R.color.white)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            alpha = 0.9f
            elevation = 4f
            
            // Size in pixels
            val size = resources.getDimensionPixelSize(R.dimen.minimize_button_size)
            layoutParams = FrameLayout.LayoutParams(size, size)
            
            setOnClickListener {
                finish()
            }
        }

        // Add to the player view overlay
        val contentFrame = binding.playerView
        val size = resources.getDimensionPixelSize(R.dimen.minimize_button_size)
        val margin = resources.getDimensionPixelSize(R.dimen.minimize_button_margin)
        
        val params = FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, margin, margin, 0)
        }

        (contentFrame as FrameLayout).addView(minimizeButton, params)
        
        // Make it visible when controller is visible
        binding.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            minimizeButton?.visibility = visibility
        })
        
        // Initially hide it
        minimizeButton?.visibility = View.GONE
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val viewWidth = binding.playerView.width
                val touchX = e.x

                player?.let { exoPlayer ->
                    if (touchX < viewWidth / 2) {
                        // Left side - rewind
                        val newPosition = (exoPlayer.currentPosition - SEEK_TIME_MS).coerceAtLeast(0)
                        exoPlayer.seekTo(newPosition)
                        showSeekAnimation(true)
                    } else {
                        // Right side - fast forward
                        val newPosition = (exoPlayer.currentPosition + SEEK_TIME_MS)
                            .coerceAtMost(exoPlayer.duration)
                        exoPlayer.seekTo(newPosition)
                        showSeekAnimation(false)
                    }
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Toggle playback controls visibility
                if (binding.playerView.isControllerFullyVisible) {
                    binding.playerView.hideController()
                } else {
                    binding.playerView.showController()
                }
                return true
            }
        })

        // Set touch listener on player view
        binding.playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showSeekAnimation(isRewind: Boolean) {
        // Remove existing overlay if any
        seekOverlay?.let { 
            (it.parent as? FrameLayout)?.removeView(it)
        }

        // Create elegant overlay with Firefly colors
        seekOverlay = TextView(this).apply {
            text = if (isRewind) "« 10s" else "10s »"
            textSize = 28f
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setBackgroundResource(R.drawable.seek_overlay_background)
            setPadding(48, 24, 48, 24)
            alpha = 0f
            elevation = 8f
        }

        // Add to the content frame
        val contentFrame = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (isRewind) {
                Gravity.CENTER_VERTICAL or Gravity.START
            } else {
                Gravity.CENTER_VERTICAL or Gravity.END
            }
            setMargins(60, 0, 60, 0)
        }

        contentFrame.addView(seekOverlay, params)

        // Animate with bounce
        seekOverlay?.animate()
            ?.alpha(1f)
            ?.scaleX(1.1f)
            ?.scaleY(1.1f)
            ?.setDuration(150)
            ?.withEndAction {
                seekOverlay?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(100)
                    ?.withEndAction {
                        handler.postDelayed({
                            seekOverlay?.animate()
                                ?.alpha(0f)
                                ?.setDuration(200)
                                ?.withEndAction {
                                    seekOverlay?.let { overlay ->
                                        (overlay.parent as? FrameLayout)?.removeView(overlay)
                                    }
                                    seekOverlay = null
                                }
                                ?.start()
                        }, 400)
                    }
                    ?.start()
            }
            ?.start()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer

            video?.let { video ->
                val mediaItem = MediaItem.fromUri(video.uri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(playbackPosition)
                exoPlayer.prepare()
            }

            // Add listener for playback state
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            finish()
                        }
                        Player.STATE_READY -> {
                            // Ready to play
                        }
                        Player.STATE_BUFFERING -> {
                            // Buffering
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Update UI based on playing state
                }
            })
        }

        // Controller auto-hide timeout
        binding.playerView.controllerShowTimeoutMs = 3000
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT <= 23 || player == null) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT <= 23) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT > 23) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        handler.removeCallbacksAndMessages(null)
        
        // Clean up overlay
        seekOverlay?.let { 
            (it.parent as? FrameLayout)?.removeView(it)
        }
        seekOverlay = null
        
        // Clean up minimize button
        minimizeButton?.let { 
            (it.parent as? FrameLayout)?.removeView(it)
        }
        minimizeButton = null
    }

    private fun initializePlayer() {
        if (player == null) {
            setupPlayer()
        }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.stop()
            exoPlayer.release()
        }
        player = null
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onBackPressed() {
        releasePlayer()
        super.onBackPressed()
    }
}
