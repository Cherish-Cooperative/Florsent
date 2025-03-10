package com.example.zhenailife.breathing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * 呼吸引導滑雪動畫的主Composable
 * 整合了視差背景、滑雪角色和呼吸狀態控制
 */
@Composable
fun BreathingAnimationScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 創建呼吸狀態控制器
    val breathingController = remember { BreathingController() }
    
    // 主畫布容器
    ParallaxSkiingCanvas(
        modifier = modifier.fillMaxSize(),
        breathingController = breathingController
    )
} 