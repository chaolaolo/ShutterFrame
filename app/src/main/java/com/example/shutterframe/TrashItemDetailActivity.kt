package com.example.shutterframe

import android.app.ComponentCaller
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.example.shutterframe.data.MediaType
import com.example.shutterframe.databinding.ActivityTrashItemDetailBinding
import com.example.shutterframe.databinding.BottomSheetPermanentlyDeleteBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.concurrent.TimeUnit

class TrashItemDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashItemDetailBinding
    private var player: ExoPlayer? = null
    private var isMuted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTrashItemDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val mediaUri = intent.data
        val mediaTypeString: String? = intent.getStringExtra("mediaType")
        val deletionTimestamp: Long = intent.getLongExtra("deletionTimestamp", -1L)


        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.mediaDetailPlayerView.findViewById<ImageButton>(R.id.mute_button)
            ?.setOnClickListener {
                toggleMute()
            }

        if (mediaUri != null && mediaTypeString != null) {
            when (MediaType.valueOf(mediaTypeString)) {
                MediaType.IMAGE -> {
                    binding.mediaDetailImage.visibility = View.VISIBLE
                    binding.mediaDetailPlayerView.visibility = View.GONE
                    Glide.with(this)
                        .load(mediaUri)
                        .into(binding.mediaDetailImage)
                }

                MediaType.VIDEO -> {
                    binding.mediaDetailImage.visibility = View.GONE
                    binding.mediaDetailPlayerView.visibility = View.VISIBLE
                    initializePlayer(mediaUri)
                }
            }
        }

        if (deletionTimestamp != -1L) {
            val remainingDays = calculateRemainingDays(deletionTimestamp)
            if (remainingDays >= 0) {
                binding.txtNoticeDelete.text =
                    "Ảnh của bạn sẽ bị xoá vĩnh viễn sau $remainingDays ngày"
            } else {
                binding.txtNoticeDelete.text = "Ảnh của bạn sẽ sớm bị xoá vĩnh viễn."
            }
        } else {
            binding.txtNoticeDelete.text = "Ảnh của bạn sẽ bị xoá vĩnh viễn."
        }

        binding.btnDelete.setOnClickListener {
            showPernamentDeleteDialog(mediaUri)
        }

        binding.btnRestore.setOnClickListener {
            restoreMedia(mediaUri)
        }

    }

    private fun calculateRemainingDays(deletionTimestamp: Long): Long {
        val currentTimeMillis = System.currentTimeMillis()
        if (deletionTimestamp <= currentTimeMillis) {
            return 0
        }
        val diffMillis = deletionTimestamp - currentTimeMillis
        return TimeUnit.MILLISECONDS.toDays(diffMillis)

    }

    private fun initializePlayer(videoUri: Uri) {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.mediaDetailPlayerView.player = exoPlayer
            val mediaItem = MediaItem.fromUri(videoUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true // tự động phát
            isMuted = exoPlayer.volume == 0f
            updateMuteButtonIcon()
        }
    }

    private fun toggleMute() {
        player?.let { exoPlayer ->
            if (isMuted) {
                exoPlayer.volume = 1f
                isMuted = false
            } else {
                exoPlayer.volume = 0f
                isMuted = true
            }
            updateMuteButtonIcon()
        }
    }

    private fun updateMuteButtonIcon() {
        val muteButton = binding.mediaDetailPlayerView.findViewById<ImageButton>(R.id.mute_button)
        muteButton?.setImageResource(
            if (isMuted) {
                R.drawable.outline_volume_off
            } else {
                R.drawable.outline_volume_up
            }
        )
    }


    private fun showPernamentDeleteDialog(mediaUri: Uri?) {
        if (mediaUri == null) {
            Toast.makeText(this, "Không có media để xoá.", Toast.LENGTH_SHORT).show()
            return
        }
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetPermanentlyDeleteBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        bottomSheetBinding.btnDeletePermanent.setOnClickListener {
            if (mediaUri != null) {
                performPermanentDelete(mediaUri)
            }
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.show()
    }

    private fun performPermanentDelete(mediaUri: Uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                contentResolver.delete(mediaUri, null, null)
                Toast.makeText(this, "Đã xoá vĩnh viễn: $mediaUri", Toast.LENGTH_SHORT).show()
                finish()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rowDeleted = contentResolver.delete(mediaUri, null, null)
                if (rowDeleted > 0) {
                    Toast.makeText(this, "Đã xoá vĩnh viễn: $mediaUri", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Không tìm thấy media để xoá.", Toast.LENGTH_SHORT).show()
                }
            } else {
                val rowsDeleted = contentResolver.delete(mediaUri, null, null)
                if (rowsDeleted > 0) {
                    Toast.makeText(this, "Đã xoá vĩnh viễn: $mediaUri", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Không thể xoá media.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent =
                    MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri))
                startIntentSenderForResult(pendingIntent.intentSender, 123, null, 0, 0, 0, null)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rowDeleted = contentResolver.delete(mediaUri, null, null)
                if (rowDeleted > 0) {
                    Toast.makeText(this, "Đã xoá vĩnh viễn: $mediaUri", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                Toast.makeText(this, "Cần cấp quyền để xoá media: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
            e.printStackTrace()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khi xoá media: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun restoreMedia(mediaUri: Uri?) {
        if (mediaUri == null) {
            Toast.makeText(this, "Không tìm thấy media để khôi phục.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentValues = ContentValues().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    put(MediaStore.MediaColumns.IS_TRASHED, 0)
                }
            }

            val rowsUpdated = contentResolver.update(mediaUri, contentValues, null, null)

            if (rowsUpdated > 0) {
                Toast.makeText(this, "Đã khôi phục media: $mediaUri", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Không tìm thấy media để khôi phục.", Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent =
                    MediaStore.createFavoriteRequest(contentResolver, listOf(mediaUri), true)
                startIntentSenderForResult(pendingIntent.intentSender, 124, null, 0, 0, 0, null)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pendingIntent = MediaStore.createWriteRequest(contentResolver, listOf(mediaUri))
                startIntentSenderForResult(pendingIntent.intentSender, 124, null, 0, 0, 0, null)
            } else {
                Toast.makeText(
                    this,
                    "Cần cấp quyền để khôi phục media: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            e.printStackTrace()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khi khôi phục media: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            123 -> {
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "Đã xoá vĩnh viễn", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Xoá media bị huỷ hoặc lỗi.", Toast.LENGTH_SHORT).show()
                }
            }

            124 -> {
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "Đã khôi phục", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Khôi phục media bị huỷ hoặc lỗi.", Toast.LENGTH_SHORT)
                        .show()
                }
            }

        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }


    override fun onResume() {
        super.onResume()
        if (player == null && intent.getStringExtra("mediaType") == MediaType.VIDEO.name) {
            val mediaUri: Uri? = intent.data
            if (mediaUri != null) {
                initializePlayer(mediaUri)
            }
        }
    }


    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

}