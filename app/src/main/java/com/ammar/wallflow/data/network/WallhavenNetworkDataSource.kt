package com.ammar.wallflow.data.network

import com.ammar.wallflow.data.network.model.wallhaven.NetworkWallhavenWallpaperResponse
import com.ammar.wallflow.data.network.model.wallhaven.NetworkWallhavenWallpapersResponse
import com.ammar.wallflow.model.search.WallhavenFilters
import org.jsoup.nodes.Document

interface WallhavenNetworkDataSource {
    suspend fun search(
        searchQuery: WallhavenFilters,
        page: Int? = null,
    ): NetworkWallhavenWallpapersResponse

    suspend fun wallpaper(wallpaperWallhavenId: String): NetworkWallhavenWallpaperResponse

    suspend fun popularTags(): Document?
}
