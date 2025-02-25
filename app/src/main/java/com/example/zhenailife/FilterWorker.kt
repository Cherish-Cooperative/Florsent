package com.example.zhenailife

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

class FilterWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        // 在這裡執行您的後台任務
        val enable = inputData.getBoolean("FILTER_ENABLED", false)
        val intent = Intent(applicationContext, MyAccessibilityService::class.java)
        intent.putExtra("FILTER_ENABLED", enable)
        applicationContext.startService(intent)
        return Result.success()
    }
} 