package com.example.shutterframe.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.shutterframe.utils.DisplayableItem
import com.example.shutterframe.utils.MediaItem
import com.example.shutterframe.R
import com.example.shutterframe.data.MediaType
import com.example.shutterframe.databinding.ItemHeaderBinding
import com.example.shutterframe.databinding.ItemMediaBinding
import java.util.Locale
import java.util.concurrent.TimeUnit

class MediaAdapter : ListAdapter<DisplayableItem, RecyclerView.ViewHolder>(MediaDiffCallback()) {

    val VIEW_TYPE_HEADER = 0
    val VIEW_TYPE_MEDIA = 1
    var onItemClick: ((MediaItem) -> Unit)? = null
    var onSelectionChange: ((MediaItem, Boolean) -> Unit)? = null

    var inSelectionMode: Boolean = false
        set(value) {
            field = value
            if (!value) {
                selectedItems.clear()
            }
            notifyDataSetChanged()
        }

    private val selectedItems = mutableMapOf<String, Boolean>()

    fun getSelectedMediaItems(): List<MediaItem> {
        return currentList.filterIsInstance<DisplayableItem.Media>()
            .map { it.mediaItem }
            .filter { selectedItems[it.uri.toString()] == true }
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()

        onSelectionChange?.invoke(MediaItem(0, Uri.EMPTY, "", MediaType.IMAGE, 0, ""), false)
    }

    fun selectAll() {
        currentList.filterIsInstance<DisplayableItem.Media>()
            .forEach { selectedItems[it.mediaItem.uri.toString()] = true }
        notifyDataSetChanged()
        onSelectionChange?.invoke(MediaItem(0, Uri.EMPTY, "", MediaType.IMAGE, 0, ""), true)
    }

    fun isItemSelected(mediaItem: MediaItem): Boolean {
        return selectedItems[mediaItem.uri.toString()] == true
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DisplayableItem.Header -> VIEW_TYPE_HEADER
            is DisplayableItem.Media -> VIEW_TYPE_MEDIA
            else -> throw IllegalArgumentException("Invalid item type")
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
//        val binding = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
//        return MediaViewHolder(binding)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding =
                    ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }

            VIEW_TYPE_MEDIA -> {
                val binding =
                    ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MediaViewHolder(binding)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (holder) {
            is HeaderViewHolder -> {
                val header = getItem(position) as DisplayableItem.Header
                holder.bind(header)
            }

            is MediaViewHolder -> {
                val mediaItem = getItem(position) as DisplayableItem.Media
                holder.bind(mediaItem.mediaItem, inSelectionMode, onItemClick, onSelectionChange)
            }
        }
    }

    inner class HeaderViewHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: DisplayableItem.Header) {
            binding.headerTitle.text = header.title
        }
    }

    inner class MediaViewHolder(private val binding: ItemMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            mediaItem: MediaItem,
            inSelectionMode: Boolean,
            onItemClick: ((MediaItem) -> Unit)?,
            onSelectionChange: ((MediaItem, Boolean) -> Unit)?
        ) {
            Glide.with(binding.mediaThumbnail.context)
                .load(mediaItem.uri)
                .centerCrop()
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(binding.mediaThumbnail)

            if (mediaItem.type == MediaType.VIDEO) {
                binding.videoIcon.visibility = View.VISIBLE
                binding.videoDuration.visibility = View.VISIBLE
                binding.videoDuration.text = formatDuration(mediaItem.duration)
            } else {
                binding.videoIcon.visibility = View.GONE
                binding.videoDuration.visibility = View.GONE
            }

            binding.checkboxSelection.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
            binding.checkboxSelection.isSelected = isItemSelected(mediaItem)
            binding.root.setOnClickListener {
                if (inSelectionMode) {
                    val isCurrentlySelected = selectedItems[mediaItem.uri.toString()] ?: false
                    selectedItems[mediaItem.uri.toString()] = !isCurrentlySelected
                    binding.checkboxSelection.isChecked = !isCurrentlySelected
                    onSelectionChange?.invoke(mediaItem, !isCurrentlySelected)
                } else {
                    onItemClick?.invoke(mediaItem)
                }
            }

            binding.root.setOnLongClickListener {
                if (!inSelectionMode) {
                    // Kích hoạt chế độ chọn
                    this@MediaAdapter.inSelectionMode =
                        true // Dùng this@MediaAdapter để truy cập biến của adapter
                    // Tự động chọn item vừa long-click
                    selectedItems[mediaItem.uri.toString()] = true
                    binding.checkboxSelection.isChecked = true
                    onSelectionChange?.invoke(mediaItem, true) // Thông báo cho Fragment
                    true // Tiêu thụ sự kiện long click
                } else {
                    false // Để long click hoạt động bình thường nếu đã ở chế độ chọn (ví dụ: kéo thả)
                }
            }

        }
    }


    private fun formatDuration(milis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milis) % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

}

class MediaDiffCallback : DiffUtil.ItemCallback<DisplayableItem>() {
    override fun areItemsTheSame(oldItem: DisplayableItem, newItem: DisplayableItem): Boolean {
        return when {
            oldItem is DisplayableItem.Header && newItem is DisplayableItem.Header -> oldItem.title == newItem.title
            oldItem is DisplayableItem.Media && newItem is DisplayableItem.Media -> oldItem.mediaItem.id == newItem.mediaItem.id
            else -> false
        }
    }

    override fun areContentsTheSame(
        oldItem: DisplayableItem,
        newItem: DisplayableItem
    ): Boolean {
        return when {
            oldItem is DisplayableItem.Header && newItem is DisplayableItem.Header -> oldItem == newItem
            oldItem is DisplayableItem.Media && newItem is DisplayableItem.Media -> oldItem.mediaItem == newItem.mediaItem
            else -> false
        }
    }
}