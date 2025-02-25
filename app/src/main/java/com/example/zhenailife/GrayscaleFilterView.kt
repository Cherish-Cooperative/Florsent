package com.example.zhenailife

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View

class GrayscaleFilterView(context: Context) : View(context) {

    private val paint = Paint()

    init {
        // 创建灰度 ColorMatrix
        val grayscaleMatrix = ColorMatrix().apply {
            setSaturation(0f)  // 将饱和度设为 0，转为灰度
        }

        // 创建土黄色的 ColorMatrix
        val sepiaMatrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    1.2f, 0.2f, 0f, 0f, 50f,   // 红色通道（增强红色并加一些偏移量）
                    0.2f, 1.0f, 0f, 0f, 30f,   // 绿色通道（减小绿色并加一些偏移量）
                    0f, 0.2f, 1.0f, 0f, -20f,  // 蓝色通道（减少蓝色，给负偏移）
                    0f, 0f, 0f, 1f, 0f         // Alpha 通道
                )
            )
        }

        // 将灰度矩阵和土黄色矩阵组合在一起
        grayscaleMatrix.postConcat(sepiaMatrix)

        // 将组合后的矩阵应用到 Paint 对象
        paint.colorFilter = ColorMatrixColorFilter(grayscaleMatrix)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        // 在 Canvas 上绘制一个全屏矩形，并使用 Paint 应用颜色效果
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
