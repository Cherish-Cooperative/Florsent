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
import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.zhenailife.ui.theme.ZHENAILifeTheme
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
// wheel picker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
// 自動對齊、選擇
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    private var filterEnabled = false
    private lateinit var mediaPlayer: MediaPlayer
    private var countDownTimer: CountDownTimer? = null // 新增：記錄倒數計時器
    private var notificationShown: Boolean = false // 新增：追蹤通知是否已顯示

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

        // 註冊本地廣播接收器
        registerReceiver(localReceiver, IntentFilter("com.example.zhenailife.ACTION_SNOOZE_LOCAL"),
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
                            showToast("Accessibility Service 已啟用")
                        } else {
                            showToast("Accessibility Service 未啟用")
                        }
                    },
                    onToggleFilter = { isChecked, updateSwitch ->
                        if (isAccessibilityEnabled(this, MyAccessibilityService::class.java)) {
                            filterEnabled = isChecked
                            toggleFilter(isChecked)
                            updateSwitch(isChecked) // 同步更新 Switch 狀態
                        } else {
                            showToast("請授予 Accessibility 權限")
                            updateSwitch(false)
                            requestAccessibilityPermission()
                        }
                    },
                    onStartCountdown = { timeInMillis ->
                        if (isAccessibilityEnabled(this, MyAccessibilityService::class.java)) {
                            startCountdown(timeInMillis)
                            showToast("倒數計時開始")

                            // 創建一個返回主頁的 Intent
                            val startMain = Intent(Intent.ACTION_MAIN)
                            startMain.addCategory(Intent.CATEGORY_HOME)
                            startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(startMain)
                        } else {
                            showToast("請授予 Accessibility 權限")
                            requestAccessibilityPermission()
                        }
                    },
                    showToast = { message -> showToast(message) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.example.zhenailife.ACTION_EXTEND_COUNTDOWN") {
            val extendTime = intent.getLongExtra("EXTRA_EXTEND_TIME", 0L)
            if (extendTime > 0L) {
                startCountdown(extendTime)
                showToast("倒數計時延長五分鐘")
            }
        }
    }

    // 新增：啟動倒數計時器
    private fun startCountdown(timeInMillis: Long) {
        stopCountdown() // 確保之前的計時器已停止
        notificationShown = false // 重置通知標記

        countDownTimer = object : CountDownTimer(timeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (millisUntilFinished <= 5 * 60 * 1000 && !notificationShown) {
                    showNotification("時間快到了！", "剩餘 5 分鐘", true)
                    notificationShown = true // 用於追蹤通知是否已顯示
                    toggleFilter(true)

                    // 啟動音樂服務
                    val musicIntent = Intent(this@MainActivity, MusicService::class.java)
                    startService(musicIntent)
                }

                // 更新 UI 或其他操作
            }

            override fun onFinish() {
                showNotification("時間到！", "倒數結束", false)
            }
        }.start()
    }

    // 新增：停止倒數計時器
    private fun stopCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    override fun onResume() {
        super.onResume()
        stopCountdown() // 在 onResume 時停止倒數計時器
        stopMusicService() // 停止音樂服務
        toggleFilter(false) // 停止濾鏡
        notificationShown = false // 重置通知標記
    }


    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleFilter(enable: Boolean) {
        val workManager = WorkManager.getInstance(this)
        val data = Data.Builder()
            .putBoolean("FILTER_ENABLED", enable)
            .build()

        val filterWorkRequest = OneTimeWorkRequestBuilder<FilterWorker>()
            .setInputData(data)
            .build()

        workManager.enqueue(filterWorkRequest)
    }

    private fun isAccessibilityEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        var accessibilityEnabled = 0
        val accessibilityServiceName = service.canonicalName

        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            Log.d("AccessibilityCheck", "ACCESSIBILITY: $accessibilityEnabled")
        } catch (e: Settings.SettingNotFoundException) {
            Log.d("AccessibilityCheck", "Error finding setting: ${e.message}")
        }

        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')

        if (accessibilityEnabled == 1) {
            Log.d("AccessibilityCheck", "***ACCESSIBILITY IS ENABLED***")

            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            Log.d("AccessibilityCheck", "Setting: $settingValue")

            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next().split("/".toRegex()).toTypedArray()[1]
                    Log.d("AccessibilityCheck", "Service: $accessibilityService")

                    if (accessibilityService.equals(accessibilityServiceName, ignoreCase = true)) {
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

    private fun showNotification(title: String, content: String, isFiveMinutesLeft: Boolean) {
        // 檢查是否已獲得通知權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("FROM_NOTIFICATION", true)
            }
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
                .setPriority(NotificationCompat.PRIORITY_MAX) // 設置優先級為最高
                .setCategory(NotificationCompat.CATEGORY_ALARM) // 設置類別為警報
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(null) // 移除聲音
                .setVibrate(longArrayOf()) // 移除振動

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

            // 檢查頻道是否可以繞過 DND 不要刪除此註解
            // val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // val channel = manager.getNotificationChannel("countdown_channel")
            // if (channel != null && !channel.canBypassDnd()) {
            //     showToast("請在系統設置中允許應用繞過請勿打擾模式")
            //     // 可選：引導用戶前往設置頁面
            //     val intentSettings = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            //         putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            //         putExtra(Settings.EXTRA_CHANNEL_ID, "countdown_channel")
            //     }
            //     startActivity(intentSettings)
            // }
        } else {
            showToast("通知權限未授予")
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

    private val localReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.zhenailife.ACTION_SNOOZE_LOCAL" -> {
                    startCountdown(5 * 60 * 1000)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
        unregisterReceiver(localReceiver)
        stopCountdown()
        mediaPlayer.release()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // 權限已授予
                showToast("通知權限已授予")
            } else {
                // 權限被拒絕
                showToast("通知權限被拒絕")
            }
        }
    }

    private fun createNotificationChannel() {
        val name = "倒數計時頻道"
        val descriptionText = "用於倒數計時的通知頻道，能繞過請勿打擾模式"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("countdown_channel", name, importance).apply {
            description = descriptionText
            setSound(null, null) // 不發出音效
            enableVibration(false) // 不震動
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun stopMusicService() {
        val musicIntent = Intent(this, MusicService::class.java)
        stopService(musicIntent)
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
    var selectedMinutes by remember { mutableStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 檢查服務是否啟用的按鈕
        Button(onClick = { onCheckServiceStatus() }) {
            Text(text = "檢查 Accessibility Service 狀態")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 引導啟用服務的按鈕
        Button(onClick = { onRequestAccessibilityPermission() }) {
            Text(text = "啟用 Accessibility Service")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 切換濾鏡的開關
        Switch(
            checked = filterChecked,
            onCheckedChange = { isChecked ->
                filterChecked = isChecked
                onToggleFilter(isChecked) { updatedChecked ->
                    filterChecked = updatedChecked
                }
            }
        )
        Text(text = if (filterChecked) "濾鏡已啟用" else "濾鏡已禁用")

        // 直接顯示滾輪選擇器
        @OptIn(ExperimentalFoundationApi::class)
        @Composable
        fun WheelTimePicker(
            value: Int,
            onValueChange: (Int) -> Unit,
            range: IntRange
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 添加當前選擇值的顯示
                Text(
                    text = "已選擇：$value 分鐘",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (value - range.first).coerceAtLeast(0)
                )
                val scope = rememberCoroutineScope()
                val itemHeightDp = 50.dp
                val visibleItems = 5

                // 使用 derivedStateOf 來追蹤中間項目
                val centerItemIndex = remember(listState.firstVisibleItemIndex) {
                    listState.firstVisibleItemIndex 
                }

                // 只在滾動停止時更新值
                LaunchedEffect(listState.isScrollInProgress) {
                    if (!listState.isScrollInProgress) {
                        val newValue = (centerItemIndex + range.first).coerceIn(range)
                        if (newValue != value) {
                            onValueChange(newValue)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .height(itemHeightDp * visibleItems)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // 背景裝飾
                    Card(
                        modifier = Modifier
                            .height(itemHeightDp)
                            .fillMaxWidth(0.5f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) { }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .height(itemHeightDp * visibleItems)
                            .fillMaxWidth(0.5f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
                    ) {
                        // 添加頂部填充項
                        items(2) {
                            Spacer(modifier = Modifier.height(itemHeightDp))
                        }
                        
                        // 實際的數字項目
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
                                    text = "$itemValue 分鐘",
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
                        
                        // 添加底部填充項
                        items(2) {
                            Spacer(modifier = Modifier.height(itemHeightDp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        WheelTimePicker(
            value = selectedMinutes,
            onValueChange = { selectedMinutes = it },
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
            Text("開始倒數計時")
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