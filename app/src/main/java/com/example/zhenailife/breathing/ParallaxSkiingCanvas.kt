package com.example.zhenailife.breathing

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.example.zhenailife.R
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
/**
 * 視差滾動滑雪畫布
 * 實現背景、雪地、地面和角色的視差效果及呼吸引導動畫
 */
@Composable
fun ParallaxSkiingCanvas(
    modifier: Modifier = Modifier,
    breathingController: BreathingController
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    // 載入WebP格式的資源圖片，使用高縮放係數
    val backgroundBitmap = remember {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.bg3, options).asImageBitmap()
    }
    
    val snowBitmap = remember {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.snow, options).asImageBitmap()
    }
    
    val groundBitmap = remember {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.ground, options).asImageBitmap()
    }
    
    val skierBitmap = remember {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 2
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.ski, options).asImageBitmap()
    }
    
    val plantBitmap = remember {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 2
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.plant2, options).asImageBitmap()
    }
    
    // 創建無限動畫
    val infiniteTransition = rememberInfiniteTransition(label = "parallaxTransition")
    
    // 背景滾動動畫 (最慢)
    val backgroundOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "backgroundScroll"
    )
    
    // 雪地滾動動畫 (中速)
    val snowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snowScroll"
    )
    
    // 地面滾動動畫 (較快)
    val groundOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "groundScroll"
    )
    
    // 呼吸動畫進度
    val breathingProgress by breathingController.breathingProgress
    
    // 角色垂直位置動畫
    val skierVerticalOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(breathingController.totalCycleDuration.toInt()),
            repeatMode = RepeatMode.Restart
        ),
        label = "skierPosition"
    )
    
    // 產生隨機植物位置（每個植物尺寸更加一致，總體縮小）
    val plants = remember {
        List(8) {
            Pair(
                Random.nextFloat() * 0.8f + 0.1f,  // x位置 (0.1-0.9)
                Random.nextFloat() * 0.005f + 0.005f   // 植物大小調整為更適中的值
            )
        }
    }
    
    // 實現呼吸階段的環境效果
    LaunchedEffect(Unit) {
        breathingController.startBreathingCycle()
    }
    
    Canvas(modifier = modifier) {
        // 獲取畫布尺寸
        val width = size.width
        val height = size.height
        
        // 計算呼吸狀態對應的亮度調整和高度變化
        val brightness = when (breathingController.currentPhase.value) {
            BreathingPhase.INHALE -> 0.2f + (0.3f * breathingProgress)
            BreathingPhase.HOLD -> 0.5f
            BreathingPhase.EXHALE -> 0.5f - (0.2f * breathingProgress)
            BreathingPhase.RELAX -> 0.3f
        }
        
        // 調整滑雪者應該在的位置，移到畫布的更中間位置
        val snowLinePosition = height * 0.55f
        
        // 計算角色的當前垂直位置
        val verticalPosition = calculateSkierVerticalPosition(
            phase = breathingController.currentPhase.value,
            progress = breathingProgress,
            basePosition = snowLinePosition // 修改基準位置
        )
        
        // 繪製全畫面的藍天背景色 (確保沒有空白區域)
        drawRect(
            color = Color(0xFF8DD0D0),
            size = Size(width, height)
        )
        
        // 繪製背景層 (最遠，移動最慢) - 確保填滿整個畫布
        drawParallaxLayer(
            bitmap = backgroundBitmap,
            canvasWidth = width,
            canvasHeight = height,
            offset = backgroundOffset,
            scrollSpeed = 0.2f,  // 減慢背景滾動速度
            brightness = brightness,
            fillMode = FillMode.FILL_BOTH // 確保寬度和高度都被填滿
        )
        
        // 繪製雪地層 (中間距離，移動中速)
        drawParallaxLayer(
            bitmap = snowBitmap,
            canvasWidth = width,
            canvasHeight = height,
            offset = snowOffset,
            scrollSpeed = 1.0f,
            brightness = 0f,
            fillMode = FillMode.FILL_BOTH // 確保寬度和高度都被填滿
        )
        
        // 在雪地上繪製植物
        plants.forEach { (xRatio, sizeRatio) ->
            val plantX = width * xRatio
            val plantSize = width * sizeRatio
            drawPlant(
                bitmap = plantBitmap,
                x = plantX,
                y = snowLinePosition + (height * 0.05f), // 調整植物位置，更接近雪地線
                width = plantSize,
                height = plantSize * 1.5f
            )
        }
        
        // 繪製地面層 (最近，移動最快)
        drawParallaxLayer(
            bitmap = groundBitmap,
            canvasWidth = width,
            canvasHeight = height,
            offset = groundOffset,
            scrollSpeed = 2.0f,
            brightness = 0f,
            fillMode = FillMode.FILL_BOTH // 確保寬度和高度都被填滿
        )
        
        // 繪製滑雪角色 (中央，放大5倍)
        drawSkier(
            bitmap = skierBitmap,
            x = width * 0.5f,  // 正中央
            y = verticalPosition,
            width = 300f,  // 放大5倍
            height = 300f   // 放大5倍
        )
        
        // 根據呼吸階段繪製特效（如吐氣階段的霧氣）
        if (breathingController.currentPhase.value == BreathingPhase.EXHALE) {
            drawBreathingEffect(width, height, breathingProgress)
        }
    }
}

// 填充模式枚舉
enum class FillMode {
    FILL_WIDTH,    // 填滿寬度
    FILL_HEIGHT,   // 填滿高度
    FILL_BOTH      // 填滿兩者
}

/**
 * 根據呼吸階段計算滑雪者的垂直位置
 */
private fun calculateSkierVerticalPosition(
    phase: BreathingPhase,
    progress: Float,
    basePosition: Float
): Float {
    return when (phase) {
        BreathingPhase.INHALE -> {
            // 吸氣階段，角色上升（上坡）
            basePosition - (progress * 120f)
        }
        BreathingPhase.HOLD -> {
            // 閉氣階段，角色在最高點（跳躍）
            basePosition - 120f
        }
        BreathingPhase.EXHALE -> {
            // 吐氣階段，角色下降（下坡）
            basePosition - 120f + (progress * 120f)
        }
        BreathingPhase.RELAX -> {
            // 放鬆階段，角色平穩滑行
            basePosition
        }
    }
}
    
/**
 * 繪製視差層
 * 使用自定義縮放比例繪製背景和地面，實現無縫循環
 */
private fun DrawScope.drawParallaxLayer(
    bitmap: ImageBitmap,
    canvasWidth: Float,
    canvasHeight: Float,
    offset: Float,
    scrollSpeed: Float,
    brightness: Float,
    fillMode: FillMode = FillMode.FILL_WIDTH
) {
    try {
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        
        // 計算縮放比例
        val scaleX = canvasWidth / bitmapWidth
        val scaleY = canvasHeight / bitmapHeight
        
        // 根據填充模式選擇合適的縮放比例，並增加覆蓋保證因子
        val scale = when (fillMode) {
            FillMode.FILL_WIDTH -> scaleX
            FillMode.FILL_HEIGHT -> scaleY
            FillMode.FILL_BOTH -> maxOf(scaleX, scaleY) * 1.1f // 增加到10%的額外覆蓋
        }
        
        // 計算縮放後的圖片尺寸
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale
        
        // 重新計算垂直位置 - 確保圖片在垂直方向上真正居中
        // 調整位置向下移動約30%，解決圖片顯示偏上問題
        val yPosition = when (fillMode) {
            FillMode.FILL_WIDTH -> (canvasHeight - scaledHeight) / 2
            FillMode.FILL_HEIGHT -> canvasHeight * 0.3f // 從頂部下移30%
            FillMode.FILL_BOTH -> (canvasHeight - scaledHeight) / 2 // 保持基本居中計算
        }
        
        // 對所有填充模式都應用額外下移
        val adjustedYPosition = when (fillMode) {
            FillMode.FILL_BOTH -> yPosition + (canvasHeight * 0.3f) // 向下偏移30%
            FillMode.FILL_HEIGHT -> yPosition // 已經有30%偏移
            FillMode.FILL_WIDTH -> yPosition + (canvasHeight * 0.3f) // 也向下偏移30%
        }
        
        // 應用亮度調整
        val colorMatrix = if (brightness != 0f) {
            ColorMatrix().apply {
                setToScale(1f + brightness, 1f + brightness, 1f + brightness, 1f)
            }
        } else {
            null
        }
        
        // 改進滾動偏移計算，確保平滑循環
        // 為不同速度的層計算單獨的偏移量
        val tileWidth = scaledWidth
        
        // 關鍵改進：確保每一幀偏移量增量很小，避免跳變
        // 對不同速度的層使用連續偏移而不是百分比位置
        val totalScrollDistance = offset * scrollSpeed * canvasWidth
        val normalizedOffset = totalScrollDistance % tileWidth
        
        // 預繪第一張圖片前的圖片（確保左邊無縫連接）
        val xPositionBefore = -normalizedOffset - tileWidth
        if (xPositionBefore + tileWidth > 0) {
            withTransform({
                translate(left = xPositionBefore, top = adjustedYPosition)
                scale(scale, scale)
            }) {
                drawImage(
                    image = bitmap,
                    topLeft = Offset.Zero,
                    alpha = 1.0f,
                    colorFilter = colorMatrix?.let { ColorFilter.colorMatrix(it) }
                )
            }
        }
        
        // 繪製主要可見圖片
        withTransform({
            translate(left = -normalizedOffset, top = adjustedYPosition)
            scale(scale, scale)
        }) {
            drawImage(
                image = bitmap,
                topLeft = Offset.Zero,
                alpha = 1.0f,
                colorFilter = colorMatrix?.let { ColorFilter.colorMatrix(it) }
            )
        }
        
        // 計算後續需要的圖片數量，確保完全覆蓋畫布
        // 始終保持至少一張額外的圖片，確保右側無縫連接
        val numAdditionalImages = Math.ceil((canvasWidth / tileWidth).toDouble()).toInt() + 1
        
        // 循環繪製額外的圖片，確保無縫連接
        for (i in 1 until numAdditionalImages) {
            val xPosition = -normalizedOffset + (i * tileWidth)
            
            // 確保只繪製可能可見的圖片（性能優化）
            if (xPosition < canvasWidth) {
                withTransform({
                    translate(left = xPosition, top = adjustedYPosition)
                    scale(scale, scale)
                }) {
                    drawImage(
                        image = bitmap,
                        topLeft = Offset.Zero,
                        alpha = 1.0f,
                        colorFilter = colorMatrix?.let { ColorFilter.colorMatrix(it) }
                    )
                }
            }
        }
        
        // 用於調試的輔助標記（註釋掉，不在正式顯示中顯示）
        // drawLine(
        //     color = Color.Red,
        //     start = Offset(0f, canvasHeight / 2),
        //     end = Offset(canvasWidth, canvasHeight / 2),
        //     strokeWidth = 5f
        // )
    } catch (e: Exception) {
        Log.e("ParallaxCanvas", "Error drawing layer: ${e.message}")
    }
}

/**
 * 繪製滑雪角色
 */
private fun DrawScope.drawSkier(
    bitmap: ImageBitmap,
    x: Float,
    y: Float,
    width: Float,
    height: Float
) {
    try {
        // 計算繪製位置，使角色居中
        val left = x - width / 2
        val top = y - height
        
        // 計算縮放比例
        val scaleX = width / bitmap.width
        val scaleY = height / bitmap.height
        
        // 使用縮放矩陣繪製角色
        scale(scaleX, scaleY, Offset(left, top)) {
            drawImage(
                image = bitmap,
                topLeft = Offset(0f, 0f),
                alpha = 1.0f
            )
        }
    } catch (e: Exception) {
        Log.e("ParallaxCanvas", "Error drawing skier: ${e.message}")
    }
}

/**
 * 繪製植物
 */
private fun DrawScope.drawPlant(
    bitmap: ImageBitmap,
    x: Float,
    y: Float,
    width: Float,
    height: Float
) {
    try {
        // 計算繪製位置，使植物居中
        val left = x - width / 2
        val top = y - height
        
        // 計算縮放比例
        val scaleX = width / bitmap.width
        val scaleY = height / bitmap.height
        
        // 使用縮放矩陣繪製植物
        scale(scaleX, scaleY, Offset(left, top)) {
            drawImage(
                image = bitmap,
                topLeft = Offset(0f, 0f),
                alpha = 1.0f
            )
        }
    } catch (e: Exception) {
        Log.e("ParallaxCanvas", "Error drawing plant: ${e.message}")
    }
}

/**
 * 繪製呼吸特效
 */
private fun DrawScope.drawBreathingEffect(
    canvasWidth: Float,
    canvasHeight: Float,
    progress: Float
) {
    // 吐氣階段的霧氣效果
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = android.graphics.Color.argb(
                (100 * progress).toInt(),
                255, 255, 255
            )
            style = Paint.Style.FILL
        }
        
        // 霧氣從角色嘴部擴散
        val centerX = canvasWidth * 0.5f  // 調整為畫面中央
        val centerY = canvasHeight * 0.45f // 調整為與角色嘴部高度相近的位置
        val radius = 20.dp.toPx() * progress
        
        canvas.nativeCanvas.drawCircle(centerX, centerY, radius, paint)
    }
} 