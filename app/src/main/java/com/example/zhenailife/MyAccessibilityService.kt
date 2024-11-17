package com.example.zhenailife

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.FrameLayout

class MyAccessibilityService : AccessibilityService() {

    private var filterView: FrameLayout? = null
    private var filterEnabled = false
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private val animationDuration = 60_000L // 一分鐘
    private val updateInterval = 1000L // 每秒更新一次

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 初始化滤镜或其他逻辑
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // 处理无障碍事件（不需要修改）
    }

    override fun onInterrupt() {
        // 当服务中断时调用
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理从 MainActivity 传递过来的意图
        filterEnabled = intent?.getBooleanExtra("FILTER_ENABLED", false) ?: false
        if (filterEnabled) {
            enableFilter()
        } else {
            disableFilter()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun enableFilter() {
        if (filterView == null) {
            filterView = FrameLayout(this)
            // 初始顏色設置為完全透明
            filterView!!.setBackgroundColor(Color.argb(0, 125, 102, 8))
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                5000, // 原本是 WindowManager.LayoutParams.MATCH_PARENT，這邊直接寫死超長覆蓋
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // 這兩個 Flag 很重要
                PixelFormat.TRANSLUCENT
            )
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.addView(filterView, layoutParams)
        }

        // 開始漸變效果
        startColorAnimation()
    }

    private fun disableFilter() {
        // 停止漸變動畫
        stopColorAnimation()

        if (filterView != null) {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.removeView(filterView)
            filterView = null
        }
    }

    private fun startColorAnimation() {
        var elapsed = 0L
        val totalSteps = (animationDuration / updateInterval).toInt()
        val increment = 100 / totalSteps // 每步增加的透明度

        updateRunnable = object : Runnable {
            override fun run() {
                if (elapsed >= animationDuration) {
                    // 完成動畫，設定為最終顏色
                    filterView?.setBackgroundColor(Color.argb(100, 125, 102, 8)) // 土黃色，透明度100
                    handler.removeCallbacks(this)
                    return
                }

                // 計算當前透明度
                val currentAlpha = ((elapsed / animationDuration.toFloat()) * 100).toInt()
                val alpha = if (currentAlpha > 100) 100 else currentAlpha
                filterView?.setBackgroundColor(Color.argb(alpha, 125, 102, 8))

                // 增加已經經過的時間
                elapsed += updateInterval
                handler.postDelayed(this, updateInterval)
            }
        }

        handler.post(updateRunnable!!)
    }

    private fun stopColorAnimation() {
        updateRunnable?.let {
            handler.removeCallbacks(it)
        }
        updateRunnable = null
    }
}
