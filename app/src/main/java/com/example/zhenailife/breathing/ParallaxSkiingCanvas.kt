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
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
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
    
    // 載入WebP格式的資源圖片，使用高縮放係數
    val backgroundBitmap = remember {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.bg3, options).asImageBitmap()
    }
    
    val snowAndroidBitmap = remember {
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        BitmapFactory.decodeResource(context.resources, R.drawable.snow, options)
    }
    val snowBitmap = remember { snowAndroidBitmap.asImageBitmap() }
    val snowEdgeRatio = remember { calculateSnowEdgeRatio(snowAndroidBitmap) }
    
    // 預處理雪邊界高度
    val snowEdgeHeights = remember { preprocessSnowEdgeHeights(snowAndroidBitmap) }
    
    val groundBitmap = remember {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.ground, options).asImageBitmap()
    }
    
    val skierBitmap = remember {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.ski, options).asImageBitmap()
    }
    
    val plantBitmap = remember {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
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
        mutableListOf<Plant>()
    }
    
    // 植物生成時間控制
    val plantGenerationTimer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "plantGeneration"
    )
    
    // 控制植物生成
    LaunchedEffect(plantGenerationTimer) {
        // 每2秒有30%機率生成一個新植物
        if (plantGenerationTimer > 0.95f && Random.nextFloat() < 0.3f) {
            plants.add(
                Plant(
                    xPosition = 1.2f, // 從畫面右側外生成
                    size = Random.nextFloat() * 0.005f + 0.005f, // 隨機大小
                    speed = Random.nextFloat() * 0.0005f + 0.0005f // 隨機移動速度
                )
            )
            
            // 限制植物數量，超過20個時移除最早的植物
            if (plants.size > 20) {
                plants.removeAt(0)
            }
        }
        
        // 更新所有植物的位置
        plants.forEach { plant ->
            plant.xPosition -= plant.speed
        }
        
        // 移除已經移出畫面的植物
        plants.removeAll { it.xPosition < -0.2f }
    }
    
    // 實現呼吸階段的環境效果
    LaunchedEffect(Unit) {
        breathingController.startBreathingCycle()
    }
    
    Canvas(modifier = modifier) {
        // 獲取畫布尺寸
        val width = size.width
        val height = size.height
        
        // 移除呼吸狀態對應的亮度調整，改為固定亮度
        val brightness = 0.3f // 固定亮度值
        
        // 計算雪相關位置
        val snowScale = height / snowAndroidBitmap.height.toFloat()  // 使用畫布高度作為縮放依據
        val snowScaledHeight = snowAndroidBitmap.height * snowScale
        val snowAdjustedY = (height - snowScaledHeight) / 2
        val snowEdgeLine = snowAdjustedY + (snowScaledHeight * snowEdgeRatio)
        
        // 計算雪層水平滾動偏移
        val snowScaledWidth = snowAndroidBitmap.width * snowScale
        val snowScrollDistance = - snowOffset * snowScaledWidth
        val normalizedSnowOffset = ((snowScrollDistance % snowScaledWidth) + snowScaledWidth) % snowScaledWidth
        val snowTranslationX = -snowScaledWidth + normalizedSnowOffset
        
        // 計算畫面中滑雪者的水平位置（假設固定在畫面中間）
        val skierX = width * 0.5f
        
        // 將 canvas 上的 skierX 映射到雪圖片坐標，考慮雪層水平偏移
        val effectiveX = (skierX - snowTranslationX) % snowScaledWidth
        val effectiveXMod = if (effectiveX < 0) effectiveX + snowScaledWidth else effectiveX
        val skierXRaw = (effectiveXMod / snowScale).toInt().coerceIn(0, snowAndroidBitmap.width - 1)
        
        // 從預處理結果取得對應 X 座標處的雪邊緣 Y 值，再轉換為 canvas 坐標
        val rawSnowY = snowEdgeHeights[skierXRaw]
        val dynamicSnowEdgeY = snowAdjustedY + (rawSnowY * snowScale)
        
        // 使用此動態計算的雪高度作為滑雪者的垂直位置
        val verticalPosition = dynamicSnowEdgeY
        
        // 繪製全畫面的藍天背景色 (確保沒有空白區域)
        drawRect(
            color = androidx.compose.ui.graphics.Color(0xFF8DD0D0),
            size = Size(width, height)
        )
        
        // 繪製背景層 (最遠，移動最慢) - 確保填滿整個畫布
        drawParallaxLayer(
            bitmap = backgroundBitmap,
            canvasWidth = width,
            canvasHeight = height,
            offset = backgroundOffset,
            brightness = brightness,
            fillMode = FillMode.FILL_BOTH // 使用FILL_BOTH確保完全覆蓋
        )
        
        // 繪製雪地層 (中間距離，移動中速)
        drawParallaxLayer(
            bitmap = snowBitmap,
            canvasWidth = width,
            canvasHeight = height,
            offset = snowOffset,
            brightness = 0f,
            fillMode = FillMode.FILL_BOTH // 使用FILL_BOTH確保完全覆蓋
        )
        
        // 在雪地上繪製植物
        plants.forEach { plant ->
            val plantX = width * plant.xPosition
            val plantSize = width * plant.size * 3  // 放大3倍
            drawPlant(
                bitmap = plantBitmap,
                x = plantX,
                y = snowEdgeLine + (height * 0.05f), // 使用雪邊緣作為基準
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
            brightness = 0f,
            fillMode = FillMode.FILL_BOTH // 使用FILL_BOTH確保完全覆蓋
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

// 植物數據類
data class Plant(
    var xPosition: Float, // 0-1範圍的x座標比例
    val size: Float,      // 大小比例
    val speed: Float      // 移動速度
)

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
    brightness: Float,
    fillMode: FillMode = FillMode.FILL_WIDTH
) {
    try {
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        
        // 計算縮放比例
        val scaleX = canvasWidth / bitmapWidth
        val scaleY = canvasHeight / bitmapHeight
        val scale = when (fillMode) {
            FillMode.FILL_WIDTH -> scaleX
            FillMode.FILL_HEIGHT -> scaleY
            FillMode.FILL_BOTH -> scaleY
        }
        
        // 計算縮放後的圖片尺寸
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale
        
        // 將圖片置中顯示，確保完整填滿畫面
        val adjustedYPosition = (canvasHeight - scaledHeight) / 2
        
        // 應用亮度調整
        val colorMatrix = if (brightness != 0f) {
            ColorMatrix().apply {
                setToScale(1f + brightness, 1f + brightness, 1f + brightness, 1f)
            }
        } else {
            null
        }
        
        // 改進滾動偏移計算，確保真正的無縫循環
        val scrollDistance = - offset * scaledWidth
        val normalizedOffset = ((scrollDistance % scaledWidth) + scaledWidth) % scaledWidth
        
        // 從左側開始繪製，確保覆蓋整個畫布
        var xPosition = -scaledWidth + normalizedOffset
        while (xPosition < canvasWidth) {
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
            xPosition += scaledWidth
        }
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
            color = Color.argb(
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

/**
 * 透過檢查圖片中多個取樣點，計算透明與不透明分界線的位置
 * 使用改進後的取樣和分析方法
 */
private fun calculateSnowEdgeRatio(bitmap: Bitmap): Float {
    // 輸出圖片基本資訊
    Log.d("SnowEdge", "開始分析圖片：寬度=${bitmap.width}, 高度=${bitmap.height}")
    
    val numSamples = 7  // 增加取樣點數量以提高準確性
    val samplePoints = List(numSamples) { index ->
        bitmap.width * (index + 1) / (numSamples + 1)  // 均勻分布的取樣點
    }
    
    // 輸出取樣點位置
    Log.d("SnowEdge", "取樣點位置: $samplePoints")
    
    var totalY = 0f
    var validSamples = 0
    
    // 針對每個取樣點 x 坐標
    for (x in samplePoints) {
        Log.d("SnowEdge", "分析取樣點 x=$x")
        var foundEdge = false
        
        // 從圖片頂部向下掃描
        scanLoop@ for (y in 0 until bitmap.height) {
            // 每隔50個像素輸出一次當前掃描位置
            if (y % 50 == 0) {
                Log.d("SnowEdge", "  掃描位置 y=$y")
            }
            
            // 判斷當前像素是否不透明
            val pixel = bitmap.getPixel(x, y)
            val alpha = Color.alpha(pixel)
            
            // 輸出特定位置的像素透明度
            if (y % 50 == 0) {
                Log.d("SnowEdge", "  位置(x=$x, y=$y)的像素透明度: alpha=$alpha")
            }
            
            if (alpha > 0) {  // 使用 Color.alpha 檢查透明度
                Log.d("SnowEdge", "  發現不透明像素：位置(x=$x, y=$y), 透明度=$alpha")
                
                // 為避免誤差，檢查下方連續幾個像素是否也為不透明
                var isValidEdge = true
                val checkRange = 5
                Log.d("SnowEdge", "  檢查下方 $checkRange 個像素")
                
                for (checkY in y + 1 until minOf(y + checkRange, bitmap.height)) {
                    val checkPixel = bitmap.getPixel(x, checkY)
                    val checkAlpha = Color.alpha(checkPixel)
                    Log.d("SnowEdge", "    檢查位置(x=$x, y=$checkY)的像素透明度: alpha=$checkAlpha")
                    
                    if (checkAlpha == 0) {
                        Log.d("SnowEdge", "    發現透明像素，不是有效邊界")
                        isValidEdge = false
                        break
                    }
                }
                
                if (isValidEdge) {
                    totalY += y.toFloat()
                    validSamples++
                    foundEdge = true
                    
                    // 輸出調試信息
                    Log.d("SnowEdge", "  找到有效邊界點：x=$x, y=$y")
                    break@scanLoop
                } else {
                    Log.d("SnowEdge", "  此位置不是有效邊界，繼續向下掃描")
                }
            }
        }
        
        if (!foundEdge) {
            Log.d("SnowEdge", "  未在取樣點 x=$x 找到有效邊界")
        }
    }
    
    // 如果沒有找到有效的樣本，返回預設值
    if (validSamples == 0) {
        Log.d("SnowEdge", "未找到任何有效邊界點，使用預設值0.5f")
        return 0.5f
    }
    
    // 返回平均高度比例
    val ratio = (totalY / validSamples) / bitmap.height.toFloat()
    Log.d("SnowEdge", "計算出的雪地邊界比例：$ratio (總高度=${totalY}, 有效樣本數=${validSamples})")
    return ratio
}

/**
 * 預處理雪邊界高度，計算每個X座標對應的雪邊緣Y座標
 */
private fun preprocessSnowEdgeHeights(bitmap: Bitmap): IntArray {
    val width = bitmap.width
    val height = bitmap.height
    
    Log.d("SnowEdge", "開始預處理雪邊界高度：寬度=${width}, 高度=${height}")
    
    // 預設為底部高度
    val edgeHeights = IntArray(width) { height / 2 }
    
    // 對每個X座標
    for (x in 0 until width) {
        // 每100個像素輸出一次進度
        if (x % 100 == 0) {
            Log.d("SnowEdge", "預處理進度: ${x}/${width}")
        }
        
        // 從上到下掃描
        var foundEdge = false
        for (y in 0 until height) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = Color.alpha(pixel)
            
            if (alpha > 0) { // 找到第一個不透明像素
                // 驗證這不是噪點 - 檢查附近像素
                var isValidEdge = true
                val checkRange = 5
                
                for (checkY in y + 1 until minOf(y + checkRange, height)) {
                    val checkPixel = bitmap.getPixel(x, checkY)
                    if (Color.alpha(checkPixel) == 0) {
                        isValidEdge = false
                        break
                    }
                }
                
                if (isValidEdge) {
                    edgeHeights[x] = y
                    foundEdge = true
                    
                    // 每100個像素輸出一個發現的邊緣
                    if (x % 100 == 0) {
                        Log.d("SnowEdge", "在X=${x}找到邊緣Y=${y}")
                    }
                    break
                }
            }
        }
        
        if (!foundEdge && x % 100 == 0) {
            Log.d("SnowEdge", "在X=${x}未找到有效邊緣，使用預設值")
        }
    }
    
    // 輸出一些統計訊息
    val minHeight = edgeHeights.minOrNull() ?: height / 2
    val maxHeight = edgeHeights.maxOrNull() ?: height / 2
    val avgHeight = edgeHeights.average()
    
    Log.d("SnowEdge", "預處理完成: 最小高度=${minHeight}, 最大高度=${maxHeight}, 平均高度=${avgHeight}")
    
    return edgeHeights
} 