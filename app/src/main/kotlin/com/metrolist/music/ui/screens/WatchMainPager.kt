/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import com.metrolist.music.ui.component.WatchTabIndicator
import com.metrolist.music.ui.screens.library.LibraryScreen
import com.metrolist.music.ui.screens.radio.RadioScreen
import com.metrolist.music.ui.screens.tv.TvStreamingScreen
import kotlinx.coroutines.launch

@Composable
fun WatchMainPager(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> HomeScreen(snackbarHostState = snackbarHostState)
                1 -> TvStreamingScreen()
                2 -> RadioScreen()
                3 -> LibraryScreen()
            }
        }

        WatchTabIndicator(
            currentPage = pagerState.currentPage,
            onTabSelected = { pageIndex ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pageIndex)
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
                .statusBarsPadding()
        )
    }
}
