package com.example.zhenailife.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelTimePicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = (value - range.first).coerceAtLeast(0)
        )
        val scope = rememberCoroutineScope()
        val itemHeightDp = 50.dp
        val visibleItems = 5

        val centerItemIndex = remember {
            derivedStateOf {
                val firstVisible = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                if (itemHeight > 0) {
                    firstVisible + (offset + itemHeight / 2) / itemHeight
                } else {
                    firstVisible
                }
            }
        }

        LaunchedEffect(listState.isScrollInProgress) {
            if (!listState.isScrollInProgress) {
                val newValue = (centerItemIndex.value + range.first).coerceIn(range)
                if (newValue != value) {
                    onValueChange(newValue)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .height(itemHeightDp * visibleItems)
                    .fillMaxWidth(0.3f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .height(itemHeightDp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) { }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .height(itemHeightDp * visibleItems)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
                ) {
                    items(2) {
                        Spacer(modifier = Modifier.height(itemHeightDp))
                    }
                    
                    items(range.last - range.first + 1) { index ->
                        val itemValue = index + range.first
                        Box(
                            modifier = Modifier
                                .height(itemHeightDp)
                                .fillMaxWidth()
                                .clickable { 
                                    scope.launch {
                                        listState.animateScrollToItem(index)
                                        onValueChange(itemValue)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$itemValue",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (itemValue == value) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurface,
                                fontSize = if (itemValue == value) 18.sp else 16.sp,
                                fontWeight = if (itemValue == value) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    
                    items(2) {
                        Spacer(modifier = Modifier.height(itemHeightDp))
                    }
                }
            }
            
            Text(
                text = "分鐘",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    selectedMinutes: Int,
    onSelectedMinutesChange: (Int) -> Unit,
    onStartCountdown: (Long) -> Unit,
    showToast: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        WheelTimePicker(
            value = selectedMinutes,
            onValueChange = { onSelectedMinutesChange(it) },
            range = 1..120
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (selectedMinutes > 0) {
                onStartCountdown(selectedMinutes * 60L * 1000L)
            } else {
                showToast("請選擇有效時間")
            }
        }) {
            Text("Start!")
        }
    }
} 