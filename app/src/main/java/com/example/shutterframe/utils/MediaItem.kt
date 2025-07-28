package com.example.shutterframe.utils

import android.net.Uri
import com.example.shutterframe.data.MediaType

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val type: MediaType,
    val duration: Long = 0,
    val relativePath:String?=null,
    val dateAdded: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaItem

        if (id != other.id) return false
        if (uri != other.uri) return false
        if (type != other.type) return false
        if (duration != other.duration) return false
        if (relativePath != other.relativePath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (relativePath?.hashCode() ?: 0)
        return result
    }
}