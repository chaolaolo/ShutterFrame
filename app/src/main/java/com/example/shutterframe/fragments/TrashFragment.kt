package com.example.shutterframe.fragments

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.shutterframe.MediaDetailActivity
import com.example.shutterframe.utils.DisplayableItem
import com.example.shutterframe.utils.MediaItem
import com.example.shutterframe.R
import com.example.shutterframe.TrashItemDetailActivity
import com.example.shutterframe.adapter.MediaAdapter
import com.example.shutterframe.data.MediaType
import com.example.shutterframe.databinding.BottomSheetPermanentlyDeleteBinding
import com.example.shutterframe.databinding.FragmentTrashBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TrashFragment : Fragment() {

    private var _binding: FragmentTrashBinding? = null
    private val binding get() = _binding!!

    private lateinit var mediaAdapter: MediaAdapter

    private val GRID_COLUMNS = 4
    private var selectedCount = 0

    private val mediaStoreLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(requireContext(), "Thao tác thành công.", Toast.LENGTH_SHORT).show()
            exitSelectionMode() // Thoát chế độ chọn sau khi thành công
            loadTrashedMedia() // Tải lại danh sách media
        } else {
            Toast.makeText(requireContext(), "Thao tác bị huỷ hoặc lỗi.", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            loadTrashedMedia()
        } else {
            Toast.makeText(
                requireContext(),
                "Cần quyền truy cập bộ nhớ để hiển thị ảnh và video trong thùng rác.",
                Toast.LENGTH_LONG
            ).show()
            binding.progressBar.visibility = View.GONE
            binding.noMediaText.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentTrashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mediaAdapter = MediaAdapter()
        binding.mediaRecyclerView.apply {
            // Không cần SpanSizeLookup vì không có header
//            layoutManager = GridLayoutManager(context, GRID_COLUMNS)
            layoutManager = GridLayoutManager(context, GRID_COLUMNS).apply {
                // Nếu bạn có header, bạn cần SpanSizeLookup để header chiếm toàn bộ chiều rộng
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (mediaAdapter.getItemViewType(position) == mediaAdapter.VIEW_TYPE_HEADER) {
                            GRID_COLUMNS // Header chiếm toàn bộ chiều rộng
                        } else {
                            1 // Item media chiếm 1 cột
                        }
                    }
                }
            }
            adapter = mediaAdapter
        }

        mediaAdapter.onItemClick = { mediaItem ->
//            val intent = Intent(requireContext(), TrashItemDetailActivity::class.java)
//                .apply {
//                    data = mediaItem.uri
//                    putExtra("mediaType", mediaItem.type.name)
//                    val deletionTimeMilis = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
//                    putExtra("deletionTimestamp", deletionTimeMilis)
//                }
//            startActivity(intent)
            if (!mediaAdapter.inSelectionMode) { // Chỉ mở chi tiết nếu không ở chế độ chọn
                val intent = Intent(requireContext(), TrashItemDetailActivity::class.java)
                    .apply {
                        data = mediaItem.uri
                        putExtra("mediaType", mediaItem.type.name)
                        // Giả định thời gian xoá là 30 ngày kể từ hiện tại nếu bạn muốn hiển thị thông báo
                        // Bạn có thể lấy timestamp thực tế từ MediaStore nếu lưu nó
                        val deletionTimeMilis =
                            System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                        putExtra("deletionTimestamp", deletionTimeMilis)
                    }
                startActivity(intent)
            }
        }
        mediaAdapter.onSelectionChange = { _, _ ->
            updateSelectedCount()
        }
        setupClickListeners()
        checkAndRequestPermissions()
    }


    private fun setupClickListeners() {
        binding.txtChoice.setOnClickListener {
            enterSelectionMode()
        }

        binding.btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }

        binding.btnSelectAll.setOnClickListener {
            mediaAdapter.selectAll()
            updateSelectedCount()
        }

        // Nút xoá các mục đã chọn
        binding.btnDeleteSelected.setOnClickListener {
            val selectedMedia = mediaAdapter.getSelectedMediaItems()
            if (selectedMedia.isNotEmpty()) {
                showDeleteConfirmationBottomSheet(selectedMedia)
            } else {
                Toast.makeText(requireContext(), "Chưa chọn mục nào để xoá.", Toast.LENGTH_SHORT).show()
            }
        }

        // Nút khôi phục các mục đã chọn
        binding.btnRestoreSelected.setOnClickListener {
            val selectedMedia = mediaAdapter.getSelectedMediaItems()
            if (selectedMedia.isNotEmpty()) {
                showRestoreConfirmationBottomSheet(selectedMedia)
            } else {
                Toast.makeText(requireContext(), "Chưa chọn mục nào để khôi phục.", Toast.LENGTH_SHORT).show()
            }
        }

        // Nút ba chấm (tùy chọn khác)
        binding.btnClearAllTrashItem.setOnClickListener {
            // TODO: Triển khai Menu tùy chọn khác nếu cần (ví dụ: "Xoá tất cả")
            Toast.makeText(requireContext(), "Tùy chọn khác (chưa triển khai)", Toast.LENGTH_SHORT).show()
        }
    }


    private fun enterSelectionMode() {
        mediaAdapter.inSelectionMode = true
        binding.initialHeader.visibility = View.GONE
        binding.selectionHeader.visibility = View.VISIBLE
        binding.footerActionsLayout.visibility = View.VISIBLE
        updateSelectedCount() // Cập nhật số lượng ban đầu (thường là 0)
    }

    private fun exitSelectionMode() {
        mediaAdapter.inSelectionMode = false // Đặt lại adapter về chế độ bình thường
        mediaAdapter.clearSelection() // Xóa tất cả lựa chọn trong adapter
        selectedCount = 0
        binding.initialHeader.visibility = View.VISIBLE
        binding.selectionHeader.visibility = View.GONE
        binding.footerActionsLayout.visibility = View.GONE
        binding.trashTitle.text = getString(R.string.thung_rac) // Đặt lại tiêu đề ban đầu
    }

    private fun updateSelectedCount() {
        selectedCount = mediaAdapter.getSelectedMediaItems().size
        binding.txtSelectedCount.text = selectedCount.toString()

        // Cập nhật text và trạng thái cho nút xoá/khôi phục
        binding.btnDeleteSelected.text = "Xoá (${selectedCount})"
        binding.btnRestoreSelected.text = "Khôi phục (${selectedCount})"

        val enableButtons = selectedCount > 0
        binding.btnDeleteSelected.alpha = if (enableButtons) 1.0f else 0.5f
        binding.btnRestoreSelected.alpha = if (enableButtons) 1.0f else 0.5f
        binding.btnDeleteSelected.isEnabled = enableButtons
        binding.btnRestoreSelected.isEnabled = enableButtons
    }

    override fun onResume() {
        super.onResume()
        loadTrashedMedia()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allPermissionsGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            loadTrashedMedia()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun loadTrashedMedia() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(
                requireContext(),
                "Chức năng thùng rác chỉ khả dụng trên Android 11 (API 30) trở lên.",
                Toast.LENGTH_LONG
            ).show()
            binding.progressBar.visibility = View.GONE
            // Xóa hết danh sách nếu không hỗ trợ
            mediaAdapter.submitList(emptyList())
            binding.noMediaText.visibility = View.VISIBLE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.noMediaText.visibility = View.GONE

        lifecycleScope.launch {
            val trashedMediaList = withContext(Dispatchers.IO) {
                val images =
                    queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE)
                val videos =
                    queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO)
//                (images + videos)
//                    .sortedByDescending { it.id } // Sắp xếp theo ngày thêm (mới nhất trước)
//                    .map { DisplayableItem.Media(it) } // Chuyển đổi mỗi MediaItem thành DisplayableItem.Media
                val groupedMedia = (images + videos)
                    .sortedByDescending { it.dateAdded } // Sắp xếp theo ngày thêm (mới nhất trước)
                    .groupBy {
                        // Nhóm theo ngày (ví dụ: "Hôm nay", "Hôm qua", "25 Tháng 7, 2025")
                        val mediaDate = java.util.Date(it.dateAdded * 1000L) // dateAdded là giây, chuyển sang millis
                        formatDateForGrouping(mediaDate)
                    }

                val displayableItems = mutableListOf<DisplayableItem>()
                groupedMedia.forEach { (dateHeader, mediaItems) ->
                    displayableItems.add(DisplayableItem.Header(dateHeader))
                    displayableItems.addAll(mediaItems.map { DisplayableItem.Media(it) })
                }
                displayableItems

            }

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                mediaAdapter.submitList(trashedMediaList)
                if (trashedMediaList.isEmpty()) {
                    binding.noMediaText.visibility = View.VISIBLE
                } else {
                    binding.noMediaText.visibility = View.GONE
                }
            }
        }
    }

    private fun queryMedia(collection: Uri, mediaType: MediaType): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_TRASHED
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Dùng Bundle thay vì URI với query string
            val queryArgs = Bundle().apply {
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.MediaColumns.DATE_ADDED)
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }

            requireContext().contentResolver.query(
                collection,
                projection,
                queryArgs,
                null
            )?.use { cursor ->
                readMediaCursor(cursor, collection, mediaType, mediaList)
            }

        } else {
            // Fallback cho API thấp hơn nếu cần (nhưng Android R trở lên mới hỗ trợ thùng rác)
            requireContext().contentResolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.IS_TRASHED} = ?",
                arrayOf("1"),
                sortOrder
            )?.use { cursor ->
                readMediaCursor(cursor, collection, mediaType, mediaList)
            }
        }

        return mediaList
    }

    private fun readMediaCursor(
        cursor: android.database.Cursor,
        collection: Uri,
        mediaType: MediaType,
        mediaList: MutableList<MediaItem>
    ) {
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val contentUri: Uri = ContentUris.withAppendedId(collection, id)
            val duration = if (mediaType == MediaType.VIDEO) cursor.getLong(durationColumn) else 0
            val relativePath = cursor.getString(relativePathColumn)

            mediaList.add(
                MediaItem(
                    id,
                    contentUri,
                    name,
                    mediaType,
                    duration,
                    relativePath
                )
            )
        }
    }

    private fun showDeleteConfirmationBottomSheet(mediaItems: List<MediaItem>) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val bottomSheetBinding = BottomSheetPermanentlyDeleteBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        val count = mediaItems.size
        bottomSheetBinding.txtDeletePermanentlyTitle.text = "Xoá ${count} mục? Các mục này sẽ bị xoá vĩnh viễn khỏi thiết bị của bạn."

        bottomSheetBinding.txtDeletePermanentButtonText.text = "Xoá vĩnh viễn (${count})"
        bottomSheetBinding.btnDeletePermanent.setOnClickListener {
            performPermanentDeleteMultiple(mediaItems.map { it.uri })
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun showRestoreConfirmationBottomSheet(mediaItems: List<MediaItem>) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        // Tái sử dụng layout BottomSheetDeleteConfirmationBinding, nhưng đổi nội dung
        val bottomSheetBinding = BottomSheetPermanentlyDeleteBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        val count = mediaItems.size
        bottomSheetBinding.txtDeletePermanentlyTitle.text = "Khôi phục ${count} mục? Các mục này sẽ được khôi phục về vị trí ban đầu."
        bottomSheetBinding.txtDeletePermanentlyTitle.setTextColor(Color.parseColor("#000000"))

        bottomSheetBinding.txtDeletePermanentButtonText.text = "Khôi phục (${count})"
        bottomSheetBinding.txtDeletePermanentButtonText.setTextColor(Color.parseColor("#000000"))
        bottomSheetBinding.iconDelete.setImageResource(R.drawable.outline_settings_backup_restore)
        bottomSheetBinding.btnDeletePermanent.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue)
        bottomSheetBinding.txtDeletePermanentButtonText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))


        bottomSheetBinding.btnDeletePermanent.setOnClickListener {
            restoreMediaMultiple(mediaItems.map { it.uri })
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun performPermanentDeleteMultiple(mediaUris: List<Uri>) {
        if (mediaUris.isEmpty()) return

        val urisToDelete = ArrayList(mediaUris)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
                val pendingIntent = MediaStore.createDeleteRequest(requireContext().contentResolver, urisToDelete)
                mediaStoreLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10
                val pendingIntent = MediaStore.createDeleteRequest(requireContext().contentResolver, urisToDelete)
                mediaStoreLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else { // Android 9 trở xuống
                var deletedCount = 0
                for (uri in urisToDelete) {
                    try {
                        val rowsAffected = requireContext().contentResolver.delete(uri, null, null)
                        if (rowsAffected > 0) {
                            deletedCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Xử lý lỗi cho từng file
                    }
                }
                if (deletedCount == urisToDelete.size) {
                    Toast.makeText(requireContext(), "Đã xoá vĩnh viễn ${deletedCount} mục.", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                    loadTrashedMedia()
                } else {
                    Toast.makeText(requireContext(), "Đã xoá ${deletedCount}/${urisToDelete.size} mục.", Toast.LENGTH_LONG).show()
                    exitSelectionMode()
                    loadTrashedMedia()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Cần quyền để xoá media: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Lỗi khi xoá media: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun restoreMediaMultiple(mediaUris: List<Uri>) {
        if (mediaUris.isEmpty()) return

        val urisToRestore = ArrayList(mediaUris)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
                val pendingIntent = MediaStore.createTrashRequest(requireContext().contentResolver, urisToRestore, false) // false để bỏ rác
                mediaStoreLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10
                val pendingIntent = MediaStore.createTrashRequest(requireContext().contentResolver, urisToRestore, false)
                mediaStoreLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else { // Android 9 trở xuống (không hỗ trợ thùng rác MediaStore)
                var restoredCount = 0
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0) // Cập nhật IS_PENDING = 0 nếu có
                    // Trên API < Q, không có IS_TRASHED, chỉ có thể "khôi phục" bằng cách
                    // thay đổi trạng thái pending hoặc di chuyển file nếu bạn có cơ chế riêng.
                    // Ở đây, chúng ta chỉ cố gắng bỏ trạng thái IS_PENDING.
                }
                for (uri in urisToRestore) {
                    try {
                        val rowsAffected = requireContext().contentResolver.update(uri, contentValues, null, null)
                        if (rowsAffected > 0) {
                            restoredCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Xử lý lỗi cho từng file
                    }
                }
                if (restoredCount == urisToRestore.size) {
                    Toast.makeText(requireContext(), "Đã khôi phục ${restoredCount} mục.", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                    loadTrashedMedia()
                } else {
                    Toast.makeText(requireContext(), "Đã khôi phục ${restoredCount}/${urisToRestore.size} mục.", Toast.LENGTH_LONG).show()
                    exitSelectionMode()
                    loadTrashedMedia()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Cần quyền để khôi phục media: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Lỗi khi khôi phục media: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    fun formatDuration(milis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milis) % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    // Hàm tiện ích để format ngày cho grouping
    fun formatDateForGrouping(date: Date): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.time = date

        val today = java.util.Calendar.getInstance()
        val yesterday = java.util.Calendar.getInstance()
        yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1)

        return when {
            calendar.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                    calendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) -> "Hôm nay"
            calendar.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
                    calendar.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR) -> "Hôm qua"
            else -> SimpleDateFormat("dd 'Tháng' MM, yyyy", Locale.getDefault()).format(date)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}