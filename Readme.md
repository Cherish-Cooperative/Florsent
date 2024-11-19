應用在「請勿打擾」模式下顯示通知的關鍵程式碼：

**1. 設置通知類別和優先級**

在 `showNotification` 方法中，設置通知的類別為 `CATEGORY_ALARM` 並將優先級設為 `PRIORITY_MAX`：

```kotlin
val builder = NotificationCompat.Builder(this, "countdown_channel")
    .setSmallIcon(R.mipmap.ic_notification)
    .setContentTitle(title)
    .setContentText(content)
    .setPriority(NotificationCompat.PRIORITY_MAX) // 設置優先級為最高
    .setCategory(NotificationCompat.CATEGORY_ALARM) // 設置類別為警報
    .setContentIntent(pendingIntent)
    .setAutoCancel(true)
```

**2. 創建通知頻道**

在 `createNotificationChannel` 方法中，設置通知頻道的重要性為 `IMPORTANCE_HIGH`，並分類為警報：

```kotlin
private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "倒數計時頻道"
        val descriptionText = "用於倒數計時的通知頻道，能繞過請勿打擾模式"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("countdown_channel", name, importance).apply {
            description = descriptionText
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, null) // 設定通知聲音為預設警報聲音
            enableVibration(true)
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
```

**3. 檢查並提示用戶繞過 DND**

在 `showNotification` 方法中，檢查通知頻道是否能繞過 DND，若無法，提示用戶前往設定頁面：

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel("countdown_channel")
    if (channel != null && !channel.canBypassDnd()) {
        showToast("請在系統設置中允許應用繞過請勿打擾模式")
        // 引導用戶前往設定頁面
        val intentSettings = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, "countdown_channel")
        }
        startActivity(intentSettings)
    }
}
```

**4. 更新廣播接收器**

確保 `NotificationReceiver` 能夠處理通知的延長行為：

```kotlin
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
```

**5. 完整的 `showNotification` 方法**

綜合上述修改，完整的 `showNotification` 方法如下：

```kotlin
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

        // 檢查頻道是否可以繞過 DND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel("countdown_channel")
            if (channel != null && !channel.canBypassDnd()) {
                showToast("請在系統設置中允許應用繞過請勿打擾模式")
                // 引導用戶前往設定頁面
                val intentSettings = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, "countdown_channel")
                }
                startActivity(intentSettings)
            }
        }
    } else {
        showToast("通知權限未授予")
    }
}
```
