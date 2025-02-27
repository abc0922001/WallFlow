package com.ammar.wallflow.ui.screens.local

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.ammar.wallflow.model.Wallpaper
import com.ammar.wallflow.ui.common.LocalSystemController
import com.ammar.wallflow.ui.common.bottomWindowInsets
import com.ammar.wallflow.ui.common.bottombar.LocalBottomBarController
import com.ammar.wallflow.ui.common.mainsearch.LocalMainSearchBarController
import com.ammar.wallflow.ui.common.topWindowInsets
import com.ammar.wallflow.ui.screens.destinations.WallpaperScreenDestination
import com.ammar.wallflow.ui.wallpaperviewer.WallpaperViewerViewModel
import com.ammar.wallflow.utils.applyWallpaper
import com.ammar.wallflow.utils.getStartBottomPadding
import com.ammar.wallflow.utils.shareWallpaper
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.navigate

@OptIn(ExperimentalMaterial3Api::class)
@Destination
@Composable
fun LocalScreen(
    navController: NavController,
    viewModel: LocalScreenViewModel = hiltViewModel(),
    viewerViewModel: WallpaperViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val viewerUiState by viewerViewModel.uiState.collectAsStateWithLifecycle()
    val wallpapers = viewModel.wallpapers.collectAsLazyPagingItems()
    val systemController = LocalSystemController.current
    val bottomBarController = LocalBottomBarController.current
    val searchBarController = LocalMainSearchBarController.current
    val bottomWindowInsets = bottomWindowInsets
    val gridState = rememberLazyStaggeredGridState()
    val navigationBarsInsets = WindowInsets.navigationBars
    val density = LocalDensity.current
    val bottomPadding = remember(
        bottomBarController.state.value,
        density,
        bottomWindowInsets.getBottom(density),
        navigationBarsInsets.getBottom(density),
    ) {
        getStartBottomPadding(
            density,
            bottomBarController,
            bottomWindowInsets,
            navigationBarsInsets,
        )
    }
    val systemState by systemController.state
    val context = LocalContext.current
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) {
        if (it == null) {
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.takePersistableUriPermission(
            it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        viewModel.refreshFolders()
    }

    LaunchedEffect(Unit) {
        systemController.resetBarsState()
        bottomBarController.update { it.copy(visible = true) }
        searchBarController.update { it.copy(visible = false) }
    }

    val onWallpaperClick: (wallpaper: Wallpaper) -> Unit = remember(systemState.isExpanded) {
        {
            if (systemState.isExpanded) {
                viewModel.setSelectedWallpaper(it)
                viewerViewModel.setWallpaper(
                    source = it.source,
                    wallpaperId = it.id,
                    thumbData = it.thumbData,
                )
            } else {
                // navigate to wallpaper screen
                navController.navigate(
                    WallpaperScreenDestination(
                        source = it.source,
                        wallpaperId = it.id,
                        thumbData = it.thumbData,
                    ),
                )
            }
        }
    }

    val onAddFolderClick: () -> Unit = remember(openDocumentTreeLauncher) {
        {
            openDocumentTreeLauncher.launch(null)
        }
    }

    LocalScreenContent(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(topWindowInsets),
        wallpapers = wallpapers,
        folders = uiState.folders,
        isExpanded = systemState.isExpanded,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = bottomPadding + 8.dp,
        ),
        gridState = gridState,
        favorites = uiState.favorites,
        selectedWallpaper = uiState.selectedWallpaper,
        layoutPreferences = uiState.layoutPreferences,
        fullWallpaper = viewerUiState.wallpaper,
        fullWallpaperActionsVisible = viewerUiState.actionsVisible,
        fullWallpaperLoading = viewerUiState.loading,
        showFullWallpaperInfo = viewerUiState.showInfo,
        onWallpaperClick = onWallpaperClick,
        onWallpaperFavoriteClick = viewModel::toggleFavorite,
        onFullWallpaperTransform = viewerViewModel::onWallpaperTransform,
        onFullWallpaperTap = viewerViewModel::onWallpaperTap,
        onFullWallpaperInfoClick = viewerViewModel::showInfo,
        onFullWallpaperInfoDismiss = { viewerViewModel.showInfo(false) },
        onFullWallpaperShareImageClick = {
            val wallpaper = viewerUiState.wallpaper ?: return@LocalScreenContent
            shareWallpaper(context, viewerViewModel, wallpaper)
        },
        onFullWallpaperApplyWallpaperClick = {
            val wallpaper = viewerUiState.wallpaper ?: return@LocalScreenContent
            applyWallpaper(context, viewerViewModel, wallpaper)
        },
        onFullWallpaperFullScreenClick = {
            viewerUiState.wallpaper?.run {
                navController.navigate(
                    WallpaperScreenDestination(
                        source = source,
                        thumbData = thumbData,
                        wallpaperId = id,
                    ),
                )
            }
        },
        onFABClick = { viewModel.showManageFoldersSheet(true) },
        onAddFolderClick = onAddFolderClick,
    )

    if (uiState.showManageFoldersSheet) {
        ManageFoldersBottomSheet(
            folders = uiState.folders,
            sort = uiState.sort,
            onDismissRequest = { viewModel.showManageFoldersSheet(false) },
            onAddFolderClick = onAddFolderClick,
            onRemoveClick = {
                context.contentResolver.releasePersistableUriPermission(
                    it.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                viewModel.refreshFolders()
            },
            onSortChange = viewModel::updateSort,
        )
    }
}
