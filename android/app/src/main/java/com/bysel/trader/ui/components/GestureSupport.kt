package com.bysel.trader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Material3 Pull-to-Refresh indicator using custom implementation
 * Works with swipe down gestures on any composable
 */
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        var offsetY by remember { mutableFloatStateOf(0f) }
        var isDragging by remember { mutableStateOf(false) }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enabled && !isRefreshing) {
                        Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                },
                                onDragEnd = {
                                    if (offsetY > 150f) {
                                        onRefresh()
                                    }
                                    offsetY = 0f
                                    isDragging = false
                                },
                                onDragCancel = {
                                    offsetY = 0f
                                    isDragging = false
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    // Only allow pull down at top of content
                                    if (dragAmount > 0f) {
                                        offsetY = (offsetY + dragAmount).coerceIn(0f, 200f)
                                    } else if (offsetY > 0f) {
                                        offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            content()
            
            // Show refresh indicator when pulling or refreshing
            if (isDragging && offsetY > 0f || isRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(top = (offsetY / 4).dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF7C4DFF)
                    )
                }
            }
        }
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
    val coroutineScope = rememberCoroutineScope()

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
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || 
                dismissValue == SwipeToDismissBoxValue.StartToEnd) {
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
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
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
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        // Check if drag started from edge
                        when {
                            offset.x < edgeThreshold -> {
                                // Started from left edge
                            }
                            offset.x > size.width - edgeThreshold -> {
                                // Started from right edge
                            }
                        }
                    },
                    onDragEnd = {},
                    onHorizontalDrag = { change, dragAmount ->
                        // Handle horizontal swipe
                        if (dragAmount.absoluteValue > swipeThreshold) {
                            if (change.position.x < edgeThreshold && dragAmount > 0) {
                                onSwipeFromLeft()
                            } else if (change.position.x > size.width - edgeThreshold && dragAmount < 0) {
                                onSwipeFromRight()
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { _ -> },
                    onDragEnd = {},
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount.absoluteValue > swipeThreshold) {
                            if (change.position.y < edgeThreshold && dragAmount > 0) {
                                onSwipeFromTop()
                            } else if (change.position.y > size.height - edgeThreshold && dragAmount < 0) {
                                onSwipeFromBottom()
                            }
                        }
                    }
                )
            }
    ) {
        content()
    }
}
