package com.ammar.wallflow.data.db.entity.wallhaven

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ammar.wallflow.model.search.WallhavenFilters
import com.ammar.wallflow.model.search.WallhavenSavedSearch
import com.ammar.wallflow.model.search.WallhavenSearch
import kotlinx.serialization.Serializable

@Entity(
    tableName = "wallhaven_saved_searches",
    indices = [
        Index(
            value = ["name"],
            unique = true,
        ),
    ],
)
@Serializable
data class WallhavenSavedSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val query: String,
    val filters: String,
)

fun WallhavenSavedSearchEntity.toWallhavenSavedSearch() = WallhavenSavedSearch(
    id = id,
    name = name,
    search = WallhavenSearch(
        query = query,
        filters = WallhavenFilters.fromQueryString(filters),
    ),
)
