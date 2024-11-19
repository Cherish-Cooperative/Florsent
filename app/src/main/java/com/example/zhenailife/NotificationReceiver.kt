package com.example.zhenailife

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "com.example.zhenailife.ACTION_SNOOZE") {
            // 啟動 MainActivity 並傳遞延長的時間
            val extendIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.zhenailife.ACTION_EXTEND_COUNTDOWN"
                putExtra("EXTRA_EXTEND_TIME", 5 * 60 * 1000L) // 延長五分鐘
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 確保 Activity 可以從非 Activity 環境啟動
            }
            context?.startActivity(extendIntent)
        }
    }
} 