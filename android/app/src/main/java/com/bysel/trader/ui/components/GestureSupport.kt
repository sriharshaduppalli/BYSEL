package com.bysel.trader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * Material3 Pull-to-Refresh indicator using custom implementation
 * Works with swipe down gestures on any composable
 */
@Composable
@OptIn(ExperimentalMaterialApi::class)
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enabled && !isRefreshing) {
                    Modifier.pullRefresh(pullRefreshState)
                } else {
                    Modifier
                }
            )
    ) {
        content()
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Helper to detect vertical drag gestures
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectVerticalDragGestures(
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onVerticalDrag: (change: PointerInputChange, dragAmount: Float) -> Unit
) {
    detectDragGestures(
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        onDrag = { change, dragAmount ->
            onVerticalDrag(change, dragAmount.y)
        }
    )
}

/**
 * Swipeable Tab Navigation using HorizontalPager
 * Allows users to swipe left/right between tabs
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableTabLayout(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<TabItem>,
    modifier: Modifier = Modifier,
    content: @Composable (tabIndex: Int) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { tabs.size }
    )

    // Sync selectedTabIndex with pagerState
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedTabIndex) {
            onTabSelected(pagerState.currentPage)
        }
    }

    // Sync pagerState with external selectedTabIndex changes (from bottom nav)
    LaunchedEffect(selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1, // Pre-load adjacent pages
            userScrollEnabled = true // Enable swipe gestures
        ) { page ->
            content(page)
        }
    }
}

/**
 * Data class for tab items
 */
data class TabItem(
    val title: String,
    val icon: @Composable () -> Unit
)

/**
 * Swipe-to-Dismiss wrapper for dismissible list items
 * Implements Material3 swipe patterns
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SwipeToDismissItem(
    item: T,
    onDismiss: (T) -> Unit,
    modifier: Modifier = Modifier,
    dismissDirection: SwipeToDismissBoxValue = SwipeToDismissBoxValue.EndToStart,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val allowStartToEnd = dismissDirection == SwipeToDismissBoxValue.StartToEnd ||
        dismissDirection == SwipeToDismissBoxValue.Settled
    val allowEndToStart = dismissDirection == SwipeToDismissBoxValue.EndToStart ||
        dismissDirection == SwipeToDismissBoxValue.Settled

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            val shouldDismiss =
                (dismissValue == SwipeToDismissBoxValue.StartToEnd && allowStartToEnd) ||
                    (dismissValue == SwipeToDismissBoxValue.EndToStart && allowEndToStart)

            if (shouldDismiss) {
                onDismiss(item)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = enabled && allowStartToEnd,
        enableDismissFromEndToStart = enabled && allowEndToStart,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.Center
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        content = { Row(content = content) }
    )
}

/**
 * Edge swipe detector for custom navigation
 * Useful for drawer-like interactions
 */
@Composable
fun EdgeSwipeDetector(
    onSwipeFromLeft: () -> Unit = {},
    onSwipeFromRight: () -> Unit = {},
    onSwipeFromTop: () -> Unit = {},
    onSwipeFromBottom: () -> Unit = {},
    edgeThreshold: Float = 50f,
    swipeThreshold: Float = 100f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(edgeThreshold, swipeThreshold) {
                var dragStartX = 0f
                var totalDragX = 0f
                var swipeHandled = false

                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragStartX = offset.x
                        totalDragX = 0f
                        swipeHandled = false
                    },
                    onDragEnd = {
                        totalDragX = 0f
                        swipeHandled = false
                    },
                    onDragCancel = {
                        totalDragX = 0f
                        swipeHandled = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (swipeHandled) {
                            return@detectHorizontalDragGestures
                        }

                        totalDragX += dragAmount
                        val startedFromLeft = dragStartX <= edgeThreshold
                        val startedFromRight = dragStartX >= size.width - edgeThreshold

                        if (startedFromLeft && totalDragX >= swipeThreshold) {
                            swipeHandled = true
                            change.consume()
                            onSwipeFromLeft()
                        } else if (startedFromRight && totalDragX <= -swipeThreshold) {
                            swipeHandled = true
                            change.consume()
                            onSwipeFromRight()
                        }
                    }
                )
            }
            .pointerInput(edgeThreshold, swipeThreshold) {
                var dragStartY = 0f
                var totalDragY = 0f
                var swipeHandled = false

                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragStartY = offset.y
                        totalDragY = 0f
                        swipeHandled = false
                    },
                    onDragEnd = {
                        totalDragY = 0f
                        swipeHandled = false
                    },
                    onDragCancel = {
                        totalDragY = 0f
                        swipeHandled = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (swipeHandled) {
                            return@detectVerticalDragGestures
                        }

                        totalDragY += dragAmount
                        val startedFromTop = dragStartY <= edgeThreshold
                        val startedFromBottom = dragStartY >= size.height - edgeThreshold

                        if (startedFromTop && totalDragY >= swipeThreshold) {
                            swipeHandled = true
                            change.consume()
                            onSwipeFromTop()
                        } else if (startedFromBottom && totalDragY <= -swipeThreshold) {
                            swipeHandled = true
                            change.consume()
                            onSwipeFromBottom()
                        }
                    }
                )
            }
    ) {
        content()
    }
}
