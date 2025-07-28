package com.example.shutterframe

import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.registerForActivityResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.example.shutterframe.data.MediaType
import com.example.shutterframe.databinding.ActivityMediaDetailBinding
import com.example.shutterframe.databinding.BottomSheetMediaInfoBinding
import com.example.shutterframe.databinding.DialogDeleteConfirmationBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI

class MediaDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaDetailBinding
    private var player: ExoPlayer? = null
    private lateinit var deleteLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var currentMediaUri: Uri? = null
    private var isMuted: Boolean = false

    private var currentMediaType: MediaType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMediaDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val mediaUri = intent.data
        val mediaTypeString: String? = intent.getStringExtra("mediaType")
        currentMediaUri = mediaUri
        currentMediaType = mediaTypeString?.let { MediaType.valueOf(it) }

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

        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
        binding.moreButton.setOnClickListener {
            currentMediaUri?.let { uri ->
                showMediaInfoBottomSheet(uri, currentMediaType)
            } ?: run {
                Toast.makeText(this, "Không có thông tin media để hiển thị.", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.infoButton.setOnClickListener {
            currentMediaUri?.let { uri ->
                showMediaInfoBottomSheet(uri, currentMediaType)
            } ?: run {
                Toast.makeText(this, "Không có thông tin media để hiển thị.", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.mediaDetailPlayerView.findViewById<ImageButton>(R.id.mute_button)
            ?.setOnClickListener {
                toggleMute()
            }

        deleteLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    Toast.makeText(this, "Đã chuyển vào thùng rác.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Không thể chuyển vào thùng rác.", Toast.LENGTH_SHORT)
                        .show()
                }
            }

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

    private fun showDeleteConfirmationDialog() {
        val dialogBinding = DialogDeleteConfirmationBinding.inflate(LayoutInflater.from(this))
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogBinding.root)
//        dialog.behavior.peekHeight = BottomSheetDialog.BEHAVIOR_DRAG
        dialog.setCanceledOnTouchOutside(true)
        dialogBinding.btnMoveToTrash.setOnClickListener {
            deleteImageFunc()
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun deleteImageFunc() {
        currentMediaUri?.let { uri ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent =
                    MediaStore.createTrashRequest(contentResolver, listOf(uri), true)
                try {
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    deleteLauncher.launch(intentSenderRequest)
                } catch (e: IntentSender.SendIntentException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Lỗi khi yêu cầu chuyển vào thùng rác", Toast.LENGTH_SHORT)
                        .show()

                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                try {
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    deleteLauncher.launch(intentSenderRequest)
                } catch (e: IntentSender.SendIntentException) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(
                        this,
                        "Lỗi khi yêu cầu xóa",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                try {
                    val rowsDeleted = contentResolver.delete(uri, null, null)
                    if (rowsDeleted > 0) {
                        android.widget.Toast.makeText(
                            this,
                            "Media đã được xóa",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        android.widget.Toast.makeText(
                            this,
                            "Không thể xóa media",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(
                        this,
                        "Không có quyền xóa media. Hãy kiểm tra quyền WRITE_EXTERNAL_STORAGE.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        } ?: run {
            Toast.makeText(this, "Không tìm thấy đường dẫn media", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMediaInfoBottomSheet(mediaUri: Uri, mediaType: MediaType?) {
        val bottomSheetBinding = BottomSheetMediaInfoBinding.inflate(LayoutInflater.from(this))
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        val commonProjection = mutableListOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DURATION,
        )

        if (mediaType == MediaType.IMAGE) {
            commonProjection.add(MediaStore.Images.Media.DATE_TAKEN)
            commonProjection.add(MediaStore.Images.Media.F_NUMBER)
            commonProjection.add(MediaStore.Images.Media.EXPOSURE_TIME)
            commonProjection.add(MediaStore.Images.Media.ISO)
        }


        contentResolver.query(mediaUri, commonProjection.toTypedArray(), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val dateAddedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                    val relativePathColumn =
                        cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)

                    val displayName = if (nameColumn != -1) cursor.getString(nameColumn) else "N/A"
                    val dateAdded =
                        if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0L
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                    val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                    val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                    val relativePath =
                        if (relativePathColumn != -1) cursor.getString(relativePathColumn) else "N/A"
                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L

                    val dateFormat =
                        SimpleDateFormat("EE, dd 'thg' MM, yyyy • HH:mm", Locale("vi", "VN"))
                    val formattedDate = dateFormat.format(Date(dateAdded * 1000L))

                    bottomSheetBinding.txtDateTime.text = formattedDate

                    val sizeKB = if (size < 1024) {
                        "$size B"
                    } else if (size < 1024 * 1024) {
                        String.format("%.2f KB", size / 1024.0)
                    } else {
                        String.format("%.2f MB", size / (1024.0 * 1024.0))
                    }
                    val resolutionMP = String.format("%.1fMP", (width * height) / 1_000_000.0)

                    // Thông tin file (tên, kích thước, độ phân giải)
                    bottomSheetBinding.txtFileInfo.text =
                        "$displayName\n$resolutionMP • ${width} x ${height}"

                    val fullPath = if (relativePath != "N/A" && !relativePath.isNullOrEmpty()) {
                        "/storage/emulated/0/$relativePath${File.separator}$displayName"
                    } else {
                        val file = File(mediaUri.path ?: "")
                        file.absolutePath
                    }

                    bottomSheetBinding.txtDevicePath.text = "Trên thiết bị ($sizeKB)\n$fullPath"

                    // Xử lý thông tin Camera/Thiết bị dựa trên loại media
                    if (mediaType == MediaType.IMAGE) {
//                        val modelColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MODEL)
                        val fNumberColumn = cursor.getColumnIndex(MediaStore.Images.Media.F_NUMBER)
                        val exposureTimeColumn =
                            cursor.getColumnIndex(MediaStore.Images.Media.EXPOSURE_TIME)
                        val isoColumn = cursor.getColumnIndex(MediaStore.Images.Media.ISO)

//                        val model = if (modelColumn != -1) cursor.getString(modelColumn) else "Unknown Device"
                        val fNumber =
                            if (fNumberColumn != -1) cursor.getFloat(fNumberColumn) else 0.0f
                        val exposureTime =
                            if (exposureTimeColumn != -1) cursor.getFloat(exposureTimeColumn) else 0.0f
                        val iso = if (isoColumn != -1) cursor.getInt(isoColumn) else 0

                        val cameraInfoText = buildString {
                            if (fNumber > 0) append(
                                "\nf/${
                                    String.format(
                                        Locale.US,
                                        "%.1f",
                                        fNumber
                                    )
                                }"
                            )
                            if (exposureTime > 0) append(" • 1/${(1 / exposureTime).toInt()}") // Chuyển đổi thành 1/X
                            if (iso > 0) append(" • ISO${iso}")
                        }
                        bottomSheetBinding.txtCameraInfo.text = "$cameraInfoText"
                    } else if (mediaType == MediaType.VIDEO) {
                        bottomSheetBinding.txtCameraInfo.text = "Video"
                    } else {
                        bottomSheetBinding.txtCameraInfo.text = "Không có thông tin thiết bị"
                    }
                }
            }

        bottomSheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        bottomSheetDialog.show()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onResume() {
        super.onResume()
        if (player == null && currentMediaType == MediaType.VIDEO) {
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