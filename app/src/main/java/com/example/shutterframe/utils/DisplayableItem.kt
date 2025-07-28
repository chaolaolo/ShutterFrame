package com.example.shutterframe.utils

import com.example.shutterframe.utils.MediaItem

sealed class DisplayableItem {
    data class Header(val title:String): DisplayableItem()
    data class Media(val mediaItem: MediaItem): DisplayableItem()
}