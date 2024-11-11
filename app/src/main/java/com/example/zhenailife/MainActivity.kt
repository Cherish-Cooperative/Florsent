package com.example.zhenailife

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.zhenailife.ui.theme.ZHENAILifeTheme

class MainActivity : ComponentActivity() {

    private var filterEnabled = false
    private lateinit var mediaPlayer: MediaPlayer

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 創建通知頻道
        createNotificationChannel()

        // 檢查並請求通知權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // 初始化 MediaPlayer
        mediaPlayer = MediaPlayer.create(this, R.raw.relaxing_soft_handpan)

        // 註冊廣播接收器
        registerReceiver(notificationReceiver, IntentFilter("com.example.zhenailife.ACTION_NOTIFICATION"),
            RECEIVER_NOT_EXPORTED
        )

        setContent {
            ZHENAILifeTheme {
                MainScreen(
                    onRequestAccessibilityPermission = {
                        requestAccessibilityPermission()
                    },
                    onCheckServiceStatus = {
                        val isEnabled = isAccessibilityEnabled(this, MyAccessibilityService::class.java)
                        if (isEnabled) {
                            showToast("Accessibility Service is enabled")
                        } else {
                            showToast("Accessibility Service is not enabled")
                        }
                    },
                    onToggleFilter = { isChecked, updateSwitch ->
                        if (isAccessibilityEnabled(this, MyAccessibilityService::class.java)) {
                            filterEnabled = isChecked
                            toggleFilter(isChecked)
                            updateSwitch(isChecked) // 同步更新 Switch 状态
                        } else {
                            showToast("Please grant accessibility permission")
                            updateSwitch(false) // 恢复 Switch 状态为 "off"
                            requestAccessibilityPermission() // 跳转到设置页面授予权限
                        }
                    },
                    onStartCountdown = { timeInMillis ->
                        startCountdown(timeInMillis)
                    },
                    showToast = { message -> showToast(message) }
                )
            }
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleFilter(enable: Boolean) {
        val workManager = androidx.work.WorkManager.getInstance(this)
        val data = androidx.work.Data.Builder()
            .putBoolean("FILTER_ENABLED", enable)
            .build()

        val filterWorkRequest = androidx.work.OneTimeWorkRequestBuilder<FilterWorker>()
            .setInputData(data)
            .build()

        workManager.enqueue(filterWorkRequest)
    }

    private fun isAccessibilityEnabled(context: Context, service: Class<out android.accessibilityservice.AccessibilityService>): Boolean {
        var accessibilityEnabled = 0
        val accessibility_service_name = service.canonicalName
        // "com.example.zhenailife/com.example.zhenailife.MyAccessibilityService"

        try {
            // 检查无障碍功能是否启用
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            Log.d("AccessibilityCheck", "ACCESSIBILITY: $accessibilityEnabled")
        } catch (e: Settings.SettingNotFoundException) {
            Log.d("AccessibilityCheck", "Error finding setting: ${e.message}")
        }

        // 创建一个 ':' 分隔符的字符串拆分器
        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')

        // 检查无障碍服务是否开启
        if (accessibilityEnabled == 1) {
            Log.d("AccessibilityCheck", "***ACCESSIBILITY IS ENABLED***")

            // 获取当前启用的无障碍服务列表
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            Log.d("AccessibilityCheck", "Setting: $settingValue")

            // 如果有启用的服务，检查是否包含我们的服务
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next().split("/").toTypedArray()[1]
                    Log.d("AccessibilityCheck", "Service: $accessibilityService")

                    // 比较服务名是否匹配
                    if (accessibilityService.equals(accessibility_service_name, ignoreCase = true)) {
                        Log.d("AccessibilityCheck", "We've found the correct service!")
                        return true
                    }
                }
            }

            Log.d("AccessibilityCheck", "***END***")
        } else {
            Log.d("AccessibilityCheck", "***ACCESSIBILITY IS DISABLED***")
        }

        return false
    }

    fun startCountdown(timeInMillis: Long) {
        var notificationShown = false // 用於追蹤通知是否已顯示

        object : CountDownTimer(timeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (millisUntilFinished <= 5 * 60 * 1000 && !notificationShown) {
                    showNotification("Time is running out!", "5 minutes left", true)
                    notificationShown = true // 設置為 true，確保通知只顯示一次
                    toggleFilter(true)
                    mediaPlayer.start()
                }
            }

            override fun onFinish() {
                showNotification("Time's up!", "Countdown finished", false)
            }
        }.start()
    }

    private fun showNotification(title: String, content: String, isFiveMinutesLeft: Boolean) {
        // 檢查是否已獲得通知權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, "countdown_channel")
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (!isFiveMinutesLeft) {
                val snoozeIntent = Intent(this, NotificationReceiver::class.java).apply {
                    action = "com.example.zhenailife.ACTION_SNOOZE"
                }
                val snoozePendingIntent: PendingIntent = PendingIntent.getBroadcast(
                    this, 
                    0, 
                    snoozeIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.mipmap.ic_snooze, "再五分鐘", snoozePendingIntent)
            }

            with(NotificationManagerCompat.from(this)) {
                notify(1, builder.build())
            }
        } else {
            // 處理未獲得權限的情況
            showToast("Notification permission is not granted")
        }
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.zhenailife.ACTION_SNOOZE" -> {
                    startCountdown(5 * 60 * 1000)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
        mediaPlayer.release()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // 權限已授予
                showToast("Notification permission granted")
            } else {
                // 權限被拒絕
                showToast("Notification permission denied")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Countdown Channel"
            val descriptionText = "Channel for countdown notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("countdown_channel", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@Composable
fun MainScreen(
    onRequestAccessibilityPermission: () -> Unit,
    onCheckServiceStatus: () -> Unit,
    onToggleFilter: (Boolean, (Boolean) -> Unit) -> Unit,
    onStartCountdown: (Long) -> Unit,
    showToast: (String) -> Unit
) {
    var filterChecked by remember { mutableStateOf(false) }
    var countdownTime by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 检查服务是否启用的按钮
        Button(onClick = { onCheckServiceStatus() }) {
            Text(text = "Check Accessibility Service Status")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 引导启用服务的按钮
        Button(onClick = { onRequestAccessibilityPermission() }) {
            Text(text = "Enable Accessibility Service")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 切换滤镜的开关
        Switch(
            checked = filterChecked,
            onCheckedChange = { isChecked ->
                // 立即更新 Switch 状态
                filterChecked = isChecked
                // 调用 onToggleFilter，并将恢复 Switch 状态的逻辑传递给它
                onToggleFilter(isChecked) { updatedChecked ->
                    filterChecked = updatedChecked
                }
            }
        )
        Text(text = if (filterChecked) "Filter Enabled" else "Filter Disabled")

        // 倒數計時輸入框和按鈕
        OutlinedTextField(
            value = countdownTime,
            onValueChange = { countdownTime = it },
            label = { Text("Enter countdown time (minutes)") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            val timeInMillis = countdownTime.toLongOrNull()?.times(60 * 1000) ?: 0L
            if (timeInMillis > 0) {
                onStartCountdown(timeInMillis)
            } else {
                showToast("Please enter a valid time")
            }
        }) {
            Text("Start Countdown")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ZHENAILifeTheme {
        MainScreen({}, {}, { _, _ -> }, { _ -> }, { _ -> })
    }
}