package com.ammar.wallflow.data.repository

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.ammar.wallflow.IoDispatcher
import com.ammar.wallflow.data.db.dao.FavoriteDao
import com.ammar.wallflow.data.db.dao.WallpapersDao
import com.ammar.wallflow.data.db.entity.FavoriteEntity
import com.ammar.wallflow.data.db.entity.toWallpaper
import com.ammar.wallflow.data.repository.local.LocalWallpapersRepository
import com.ammar.wallflow.data.repository.utils.successOr
import com.ammar.wallflow.model.Source
import com.ammar.wallflow.model.Wallpaper
import com.ammar.wallflow.model.wallhaven.wallhavenWallpaper1
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

@Singleton
class FavoritesRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val wallpapersDao: WallpapersDao,
    private val localWallpapersRepository: LocalWallpapersRepository,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
) {
    fun observeAll() = favoriteDao.observeAll()

    @OptIn(ExperimentalPagingApi::class)
    fun favoriteWallpapersPager(
        context: Context,
        pageSize: Int = 24,
        prefetchDistance: Int = pageSize,
        initialLoadSize: Int = pageSize * 3,
    ): Flow<PagingData<Wallpaper>> = Pager(
        config = PagingConfig(
            pageSize = pageSize,
            prefetchDistance = prefetchDistance,
            initialLoadSize = initialLoadSize,
        ),
        remoteMediator = null,
        pagingSourceFactory = { favoriteDao.pagingSource() },
    ).flow.map {
        it.map { entity ->
            when (entity.source) {
                Source.WALLHAVEN -> {
                    val wallpaperEntity = wallpapersDao.getByWallhavenId(entity.sourceId)
                    wallpaperEntity?.toWallpaper() ?: wallhavenWallpaper1
                }
                Source.LOCAL -> localWallpapersRepository.wallpaper(
                    context = context,
                    wallpaperUriStr = entity.sourceId,
                ).firstOrNull()?.successOr(null) ?: wallhavenWallpaper1
            }
        }.filter { wallpaper -> wallpaper != wallhavenWallpaper1 }
    }.flowOn(ioDispatcher)

    suspend fun toggleFavorite(
        sourceId: String,
        source: Source,
    ) = withContext(ioDispatcher) {
        val exists = favoriteDao.exists(
            sourceId = sourceId,
            source = source,
        )
        if (exists) {
            // delete it
            favoriteDao.deleteBySourceIdAndType(
                sourceId = sourceId,
                source = source,
            )
            return@withContext
        }
        favoriteDao.upsert(
            FavoriteEntity(
                id = 0,
                sourceId = sourceId,
                source = source,
                favoritedOn = Clock.System.now(),
            ),
        )
    }

    suspend fun addFavorite(
        sourceId: String,
        source: Source,
    ) = withContext(ioDispatcher) {
        val exists = favoriteDao.exists(
            sourceId = sourceId,
            source = source,
        )
        if (exists) {
            return@withContext
        }
        favoriteDao.upsert(
            FavoriteEntity(
                id = 0,
                sourceId = sourceId,
                source = source,
                favoritedOn = Clock.System.now(),
            ),
        )
    }

    suspend fun getRandom(
        context: Context,
    ) = withContext(ioDispatcher) {
        val entity = favoriteDao.getRandom() ?: return@withContext null
        when (entity.source) {
            Source.WALLHAVEN -> {
                val wallpaperEntity = wallpapersDao.getByWallhavenId(entity.sourceId)
                wallpaperEntity?.toWallpaper()
            }
            Source.LOCAL -> localWallpapersRepository.wallpaper(
                context = context,
                wallpaperUriStr = entity.sourceId,
            ).firstOrNull()?.successOr(null)
        }
    }

    suspend fun insertEntities(entities: Collection<FavoriteEntity>) = withContext(ioDispatcher) {
        val existing = favoriteDao.getAll()
        val existingMap = existing.associateBy { (it.source to it.sourceId) }
        val insertFavorites = entities.filter {
            // only take non-existing favorites
            existingMap[(it.source to it.sourceId)] == null
        }.map {
            // reset id
            it.copy(id = 0)
        }
        favoriteDao.insertAll(insertFavorites)
    }
}
