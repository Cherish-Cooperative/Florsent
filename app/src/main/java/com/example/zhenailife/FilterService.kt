package com.example.zhenailife

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.util.Log  // 导入 Log 类

class FilterService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var grayscaleFilterView: GrayscaleFilterView

    override fun onCreate() {
        Log.d("GrayscaleFilterView", "Screen dimensions: width = , height = ")
        super.onCreate()

        // 获取 WindowManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 创建自定义的 GrayscaleFilterView
        grayscaleFilterView = GrayscaleFilterView(this)

        // 设置 WindowManager 参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // Android 8.0 以上使用此类型
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        // 确保滤镜覆盖状态栏和导航栏
        params.gravity = Gravity.TOP or Gravity.START

        // 将自定义的 GrayscaleFilterView 添加到 WindowManager
        windowManager.addView(grayscaleFilterView, params)
    }

    override fun onDestroy() {
        super.onDestroy()

        // 移除自定义滤镜视图
        if (::grayscaleFilterView.isInitialized) {
            windowManager.removeView(grayscaleFilterView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null  // 这是一个未绑定的服务
    }
}
