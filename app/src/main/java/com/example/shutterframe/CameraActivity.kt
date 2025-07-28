package com.example.shutterframe

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.example.shutterframe.data.MediaType
import com.example.shutterframe.databinding.ActivityCameraBinding
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private lateinit var cameraExecutor: ExecutorService

    private var currentCaptureMode: MediaType = MediaType.IMAGE

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Camera và/hoặc Microphone không được cấp quyền.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            } else {
                startCamera()
                updateLatestMediaThumb()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Yêu cầu quyền camera và microphone khi ứng dụng khởi chạy
        if (allPermissionsGranted()) {
            startCamera()
            updateLatestMediaThumb()
        } else {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }
        updateLatestMediaThumb()

        binding.gotoAlbumButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Thiết lập sự kiện click
        binding.captureButton.setOnClickListener {
            when (currentCaptureMode) {
                MediaType.IMAGE -> takePhoto()
                MediaType.VIDEO -> captureVideo()
            }
        }

        // Thiết lập sự kiện click riêng cho nút dừng quay (nút vuông đỏ)
        binding.recordingStopButton.setOnClickListener {
            if (currentCaptureMode == MediaType.VIDEO) {
                captureVideo()
            }
        }

        // đổi cam trước cam sau
        binding.cameraSwitchButton.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        updateCaptureButtonUI(MediaType.IMAGE, false)

        binding.modeSelectorTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Tab "Chụp ảnh" được chọn
                        currentCaptureMode = MediaType.IMAGE
                        updateCaptureButtonUI(MediaType.IMAGE, false)
                    }

                    1 -> { // Tab "Quay video" được chọn
                        currentCaptureMode = MediaType.VIDEO
                        updateCaptureButtonUI(MediaType.VIDEO, false)
                    }
                }
            }

            override fun onTabUnselected(p0: TabLayout.Tab?) {
            }

            override fun onTabReselected(p0: TabLayout.Tab?) {
            }

        })

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun updateCaptureButtonUI(mode: MediaType, isRecording: Boolean) {
        when (mode) {
            MediaType.IMAGE -> {
                binding.captureButton.background =
                    ContextCompat.getDrawable(this, R.drawable.shutter_button_background_image)
                binding.captureButton.visibility = View.VISIBLE // Đảm bảo nút tròn hiện thị
                binding.recordingStopButton.visibility = View.GONE // Ẩn nút vuông
                binding.captureButton.isEnabled = true // Luôn kích hoạt nút chụp ảnh
                binding.recordingStopButton.isEnabled = false // Vô hiệu hóa nút dừng quay
            }

            MediaType.VIDEO -> {
                if (isRecording) {
                    binding.captureButton.visibility = View.GONE // Ẩn nút tròn
                    binding.recordingStopButton.visibility = View.VISIBLE // Hiển thị nút vuông đỏ
                    binding.recordingStopButton.background =
                        ContextCompat.getDrawable(this, R.drawable.recording_indicator_square)
                    binding.recordingStopButton.isEnabled = true // Kích hoạt nút dừng quay
                } else {
                    binding.captureButton.visibility = View.VISIBLE // Hiển thị nút tròn đỏ
                    binding.captureButton.background =
                        ContextCompat.getDrawable(this, R.drawable.shutter_button_background_video)
                    binding.recordingStopButton.visibility = View.GONE // Ẩn nút vuông
                    binding.captureButton.isEnabled = true // Kích hoạt nút bắt đầu quay
                    binding.recordingStopButton.isEnabled = false // Vô hiệu hóa nút dừng quay
                }
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        binding.captureButton.isEnabled = false

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                @OptIn(UnstableApi::class)
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg = "Ảnh đã được lưu tại: ${outputFileResults.savedUri}"
//                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                    binding.captureButton.isEnabled = true
                    updateLatestMediaThumb()
                }

                @OptIn(UnstableApi::class)
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
//                    Toast.makeText(baseContext, "Lỗi chụp ảnh: ${exc.message}", Toast.LENGTH_SHORT)
//                        .show()
                    binding.captureButton.isEnabled = true
                }
            }
        )
    }


    @OptIn(UnstableApi::class)
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        binding.captureButton.isEnabled = false

        val curRecording = recording

        if (curRecording != null) {
            curRecording.stop()
            recording = null
            updateCaptureButtonUI(MediaType.VIDEO, false)
            binding.captureButton.isEnabled = true
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }


        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                        this@CameraActivity,
                        Manifest.permission.RECORD_AUDIO
                    )
                ) {
                    withAudioEnabled()
                }
            }.start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        updateCaptureButtonUI(MediaType.VIDEO, true)
                        binding.captureButton.isEnabled = true
                        Log.d(TAG, "Video capture started")
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            Log.d(TAG, "Video đã được lưu: ${recordEvent.outputResults.outputUri}")
                            updateLatestMediaThumb()
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Lỗi ghi video: ${recordEvent.error}")
                        }
                        updateCaptureButtonUI(MediaType.VIDEO, false) // Dừng quay
                        binding.captureButton.isEnabled = true
                    }
                }
            }
    }


    @OptIn(UnstableApi::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST, FallbackStrategy.higherQualityOrLowerThan(
                            Quality.SD
                        )
                    )
                )
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )
            } catch (exc: IllegalStateException) {
                Log.e(TAG, "Liên kết use cases thất bại", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateLatestMediaThumb() {
        if (!allPermissionsGranted()) {
            binding.gotoAlbumButton.setImageResource(R.drawable.logo)
            return
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
//            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        var latestMediaUri: Uri? = null
        var latestMediaDate: Long = 0L

        // Lấy ảnh mới nhất
        val queryUriImages = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(
            queryUriImages,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumns = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateAddedColumns =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                val mediaId = cursor.getLong(idColumns)
                val date = cursor.getLong(dateAddedColumns)

                if (date > latestMediaDate) {
                    latestMediaUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    latestMediaDate = date
                }
            }
        }

        // Lấy video mới nhất (và so sánh với ảnh để chọn cái mới nhất)
        val queryUriVideos = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(
            queryUriVideos,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateAddedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                val mediaId = cursor.getLong(idColumn)
                val videoDate = cursor.getLong(dateAddedColumn)

                if (videoDate > latestMediaDate) {
                    latestMediaUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    latestMediaDate = videoDate
                }
            }
        }

        if (latestMediaUri != null) {
            Glide.with(this)
                .load(latestMediaUri)
                .thumbnail(0.1f) // Tải thumbnail nhỏ trước để tối ưu
                .centerCrop() // Cắt ảnh để vừa với ImageView
                .placeholder(R.drawable.logo)
                .error(R.drawable.logo)
                .into(binding.gotoAlbumButton)
        } else {
            // Không có ảnh/video nào, hiển thị ảnh mặc định
            binding.gotoAlbumButton.setImageResource(R.drawable.logo)
        }
    }

    // Kiểm tra tất cả các quyền cần thiết đã được cấp chưa
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // Quyền lưu trữ cho API <= 28
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                // READ_EXTERNAL_STORAGE không cần thiết nếu chỉ ghi file vào MediaStore
                // và không đọc file từ các ứng dụng khác.
            }.toTypedArray()
    }
}