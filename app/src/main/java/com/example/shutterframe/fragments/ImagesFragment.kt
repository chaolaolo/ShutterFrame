package com.example.shutterframe.fragments

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.shutterframe.CameraActivity
import com.example.shutterframe.MainActivity
import com.example.shutterframe.MediaDetailActivity
import com.example.shutterframe.utils.DisplayableItem
import com.example.shutterframe.utils.MediaItem
import com.example.shutterframe.adapter.MediaAdapter
import com.example.shutterframe.data.MediaType
import com.example.shutterframe.databinding.FragmentImagesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImagesFragment : Fragment() {

    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!
    private lateinit var mediaAdapter: MediaAdapter
    private val GRID_COLUMNS = 4
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            loadMedia()
        } else {
            Toast.makeText(
                requireContext(),
                "Cần quyền truy cập bộ nhớ để hiển thị ảnh và video.",
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mediaAdapter = MediaAdapter()
        binding.mediaRecyclerView.apply {
            val layoutManager = GridLayoutManager(context, GRID_COLUMNS)
            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (mediaAdapter.getItemViewType(position)) {
                        mediaAdapter.VIEW_TYPE_HEADER -> GRID_COLUMNS
                        mediaAdapter.VIEW_TYPE_MEDIA -> 1
                        else -> 1
                    }
                }
            }
            this.layoutManager = layoutManager
            adapter = mediaAdapter
        }

        mediaAdapter.onItemClick = { mediaItem ->
            val intent = Intent(requireContext(), MediaDetailActivity::class.java)
                .apply {
                    data = mediaItem.uri
                    putExtra("mediaType", mediaItem.type.name)
                }
            startActivity(intent)

        }

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        loadMedia()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
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
            loadMedia()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }


    private fun loadMedia() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val displayableList = withContext(Dispatchers.IO) {
                val images =
                    queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE)
                val videos =
                    queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO)
                val allMedia = (images + videos).sortedByDescending { it.id }
                val groupedMedia = allMedia.groupBy {
                    it.relativePath?.trimEnd('/') ?: "Unknown Folder" // Xóa '/' cuối cùng nếu có
                }
                val resultList = mutableListOf<DisplayableItem>()

                val sortedGroupKeys = groupedMedia.keys.sorted()

                sortedGroupKeys.forEach { folderName ->
                    resultList.add(DisplayableItem.Header(folderName))
                    groupedMedia[folderName]?.let { mediaInFolder ->
                        resultList.addAll(mediaInFolder.map { DisplayableItem.Media(it) })
                    }
                }

                resultList
            }

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                mediaAdapter.submitList(displayableList)
                if (displayableList.isEmpty()) {
                    Toast.makeText(requireContext(), "Không có hình ảnh nào.", Toast.LENGTH_SHORT)
                        .show()
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
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        requireContext().contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val relativePathColumn =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri: Uri = ContentUris.withAppendedId(collection, id)
                val duration =
                    if (mediaType == MediaType.VIDEO) cursor.getLong(durationColumn) else 0
                val relativePath = cursor.getString(relativePathColumn)
                mediaList.add(MediaItem(id, contentUri, name, mediaType, duration, relativePath))
            }
        }
        return mediaList
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}