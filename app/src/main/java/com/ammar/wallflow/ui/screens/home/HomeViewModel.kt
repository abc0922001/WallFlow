package com.ammar.wallflow.ui.screens.home

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.ammar.wallflow.data.db.entity.toFavorite
import com.ammar.wallflow.data.db.entity.toSavedSearch
import com.ammar.wallflow.data.preferences.LayoutPreferences
import com.ammar.wallflow.data.repository.AppPreferencesRepository
import com.ammar.wallflow.data.repository.FavoritesRepository
import com.ammar.wallflow.data.repository.SavedSearchRepository
import com.ammar.wallflow.data.repository.WallhavenRepository
import com.ammar.wallflow.data.repository.utils.Resource
import com.ammar.wallflow.data.repository.utils.successOr
import com.ammar.wallflow.model.Favorite
import com.ammar.wallflow.model.Purity
import com.ammar.wallflow.model.SavedSearch
import com.ammar.wallflow.model.Search
import com.ammar.wallflow.model.SearchQuery
import com.ammar.wallflow.model.Sorting
import com.ammar.wallflow.model.TopRange
import com.ammar.wallflow.model.Wallpaper
import com.ammar.wallflow.model.toSearchQuery
import com.ammar.wallflow.model.wallhaven.WallhavenTag
import com.ammar.wallflow.ui.screens.navArgs
import com.ammar.wallflow.utils.combine
import com.github.materiiapps.partial.Partialize
import com.github.materiiapps.partial.partial
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@OptIn(
    ExperimentalCoroutinesApi::class,
    FlowPreview::class,
)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val wallHavenRepository: WallhavenRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val savedSearchRepository: SavedSearchRepository,
    private val favoritesRepository: FavoritesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mainSearch = savedStateHandle.navArgs<HomeScreenNavArgs>().search
    private val popularTags = wallHavenRepository.popularTags()
    private val localUiState = MutableStateFlow(HomeUiStatePartial())

    private val homeSearchFlow = appPreferencesRepository.appPreferencesFlow.mapLatest {
        it.homeSearch
    }.distinctUntilChanged()
    private val wallpapersLoadingFlow = MutableStateFlow(false)
    private val debouncedWallpapersLoadingFlow = wallpapersLoadingFlow
        .debounce { if (it) 1000 else 0 }
        .distinctUntilChanged()

    val wallpapers = if (mainSearch != null) {
        wallHavenRepository.wallpapersPager(mainSearch.toSearchQuery())
    } else {
        homeSearchFlow.flatMapLatest {
            wallHavenRepository.wallpapersPager(it.toSearchQuery())
        }
    }.cachedIn(viewModelScope)

    val uiState = combine(
        popularTags,
        appPreferencesRepository.appPreferencesFlow,
        localUiState,
        debouncedWallpapersLoadingFlow,
        savedSearchRepository.observeAll(),
        favoritesRepository.observeAll(),
    ) {
            tags,
            appPreferences,
            local,
            wallpapersLoading,
            savedSearchEntities,
            favorites,
        ->
        local.merge(
            HomeUiState(
                wallhavenTags = (
                    if (tags is Resource.Loading) {
                        tempWallhavenTags
                    } else {
                        tags.successOr(
                            emptyList(),
                        )
                    }
                    ).toImmutableList(),
                areTagsLoading = tags is Resource.Loading,
                mainSearch = mainSearch,
                homeSearch = appPreferences.homeSearch,
                wallpapersLoading = wallpapersLoading,
                blurSketchy = appPreferences.blurSketchy,
                blurNsfw = appPreferences.blurNsfw,
                showNSFW = appPreferences.wallhavenApiKey.isNotBlank(),
                savedSearches = savedSearchEntities.map { entity -> entity.toSavedSearch() },
                layoutPreferences = appPreferences.lookAndFeelPreferences.layoutPreferences,
                favorites = favorites.map { it.toFavorite() }.toImmutableList(),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(),
    )

    fun refresh() {
        viewModelScope.launch {
            wallHavenRepository.refreshPopularTags()
        }
    }

    fun updateHomeSearch(search: Search) {
        viewModelScope.launch {
            appPreferencesRepository.updateHomeSearch(search)
        }
    }

    fun showFilters(show: Boolean) = localUiState.update {
        it.copy(showFilters = partial(show))
    }

    fun setWallpapersLoading(refreshing: Boolean) = wallpapersLoadingFlow.update { refreshing }

    fun setSelectedWallpaper(wallpaper: Wallpaper) = localUiState.update {
        it.copy(selectedWallpaper = partial(wallpaper))
    }

    fun showSaveSearchAsDialog(search: Search? = null) = localUiState.update {
        it.copy(saveSearchAsSearch = partial(search))
    }

    fun saveSearchAs(name: String, search: Search) = viewModelScope.launch {
        savedSearchRepository.upsert(
            SavedSearch(
                name = name,
                search = search,
            ),
        )
    }

    fun showSavedSearches(show: Boolean = true) = localUiState.update {
        it.copy(showSavedSearchesDialog = partial(show))
    }

    fun toggleFavorite(wallpaper: Wallpaper) = viewModelScope.launch {
        favoritesRepository.toggleFavorite(
            sourceId = wallpaper.id,
            source = wallpaper.source,
        )
    }
}

private val tempWallhavenTags = List(3) {
    WallhavenTag(
        id = it + 1L,
        name = "Loading...", // no need for string resource as "Loading..." won't be visible
        alias = emptyList(),
        categoryId = 0,
        category = "",
        purity = Purity.SFW,
        createdAt = Clock.System.now(),
    )
}

@Stable
@Partialize
data class HomeUiState(
    val wallhavenTags: ImmutableList<WallhavenTag> = persistentListOf(),
    val areTagsLoading: Boolean = true,
    val mainSearch: Search? = null,
    val homeSearch: Search = Search(
        filters = SearchQuery(
            sorting = Sorting.TOPLIST,
            topRange = TopRange.ONE_DAY,
        ),
    ),
    val showFilters: Boolean = false,
    val wallpapersLoading: Boolean = false,
    val blurSketchy: Boolean = false,
    val blurNsfw: Boolean = false,
    val showNSFW: Boolean = false,
    val selectedWallpaper: Wallpaper? = null,
    val saveSearchAsSearch: Search? = null,
    val showSavedSearchesDialog: Boolean = false,
    val savedSearches: List<SavedSearch> = emptyList(),
    val layoutPreferences: LayoutPreferences = LayoutPreferences(),
    val favorites: ImmutableList<Favorite> = persistentListOf(),
) {
    val isHome = mainSearch == null
}
