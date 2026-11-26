# Florsent 隱私權政策／Privacy Policy

生效日期／Effective date：2026-11-27

[繁體中文](#繁體中文) ・ [English](#english)

---

## 繁體中文

Florsent（以下稱「本 App」）是一個幫助使用者減少社群媒體使用時間的 App，有 Android 版與 iOS／iPadOS 版。本政策說明本 App 會處理哪些資料、為什麼需要、以及資料如何保存。下方各節說明 Android 版；iOS／iPadOS 版的做法另見〈[iOS／iPadOS 版](#iosipados-版)〉一節。

### 一句話摘要

本 App 所需的資料全部只在你的手機上處理與保存。我們不蒐集、不上傳、不出售，也不與任何人分享。本 App 沒有網路權限，也不包含任何廣告或分析 SDK。

### 本 App 在手機上處理的資料

| 資料 | 用途 | 保存位置 |
|---|---|---|
| 目前在前景的 App 的套件名稱（例如 `com.instagram.android`） | 判斷你是否正在使用你選擇留意的 App，以便計時 | 在記憶體中使用；只有留意的 App 的使用時段，會在你同意後寫入使用紀錄（見下方） |
| 使用紀錄（需要你同意） | 學習你通常幾點放下手機、提供睡前提醒與使用洞察 | 手機本機，30 天後自動刪除 |
| 手機上可啟動的 App 清單（名稱與圖示） | 讓你選擇要留意哪些 App | 不另外保存，每次開啟時讀取 |
| 使用時間統計（今日累計、本次累計、超過額度後的時間） | 計算額度、決定何時淡化畫面與顯示提醒 | 手機本機（App 私有儲存空間） |
| 你的設定（留意的 App、額度、安靜時間、聲音與呼吸偏好） | 讓 App 依你的偏好運作 | 手機本機（App 私有儲存空間） |

本 App **不會**存取或處理：畫面上的文字與內容、你輸入的任何文字、通知內容、聯絡人、相片、位置、帳號資訊或任何可識別你個人身分的資料。

### 使用紀錄（需要你同意）

為了學習你的作息並提供睡前提醒與使用洞察，本 App 會在你同意後，於手機上保存以下紀錄：

- 留意的 App 的名稱，以及每段使用的開始時間與長度。
- 解鎖後螢幕亮著的時段，只有時間，不含 App 名稱。
- 紀錄開始與停止的時間，以及當時的時區偏移（換時區後，過去的紀錄仍維持原本的當地時間）。

這些紀錄只用來：

- 在手機上計算你通常幾點放下手機。演算法在手機上執行，不使用任何雲端服務或 AI 模型。
- 顯示今天、近 7 天與常用 App 的使用時間。

保存與控制：

- 紀錄保存在本 App 的私有儲存空間，30 天後自動刪除。
- 你可以隨時在「設定 > 進階」關閉「記錄使用時段」，或按「清除使用紀錄」立即刪除全部紀錄與學到的作息。
- 新使用者在開啟無障礙服務前的說明中會看到這項說明；從舊版升級的使用者，會在首頁被詢問一次是否開始記錄。選擇「不記錄」時，不會保存任何使用紀錄，睡前引導仍可用自己指定的時間。

### 無障礙服務（AccessibilityService API）的使用說明

本 App 需要你在系統設定中開啟「Florsent 漸進引導」無障礙服務，才能提供核心功能。本 App 並非協助身心障礙者的輔助工具。

**用途：**

- 偵測目前在前景的是哪一個 App，只用來計算你選擇留意的 App 的使用時間。
- 在畫面上顯示漸進的淡化效果，以及「停下來深呼吸一下？」的提醒。
- 呼吸練習結束、你按下「回到生活」時關掉螢幕（等同按一下電源鍵）。這項可以在「設定」中關閉。

**範圍限制：**

- 只接收「視窗狀態改變」（`TYPE_WINDOW_STATE_CHANGED`）事件，從中只讀取 App 的套件名稱；螢幕開關與解鎖的時間來自系統廣播。
- 已設定為不讀取視窗內容（`canRetrieveWindowContent=false`），因此無法看到畫面上的文字、圖片或你輸入的內容。
- 不執行任何手勢。唯一的系統動作是上面說的關掉螢幕，而且只在你按下「回到生活」時發生。
- 取得的資訊不會離開你的手機。

**同意與關閉：** 開啟前，本 App 會先顯示上述說明，你同意後才會帶你到系統設定。你可以隨時在「設定 > 無障礙」中關閉這項服務，關閉後計時與畫面效果會立即停止。

### 選用權限：「零打擾」存取權（Android 15 以上）

如果你在設定中開啟「淡到底時改成黑白」，本 App 會請你授予「零打擾」存取權（通知政策存取權）。這個權限只用來建立並開關一個名為「Florsent 漸進灰階」的系統模式，它唯一的效果是讓畫面變成黑白，不會變更你的通知、鈴聲或其他模式。你可以隨時關閉這項功能或收回權限。關閉功能時，這個模式也會一併刪除。

### 選用進階功能：Shizuku 逐 App 去飽和

如果你自行安裝並啟動 Shizuku，並在本 App 設定中開啟「讓 App 自己慢慢褪色」，本 App 會透過 Shizuku 調整你正在使用的 App 的顏色飽和度，讓它慢慢變成黑白。

- 只會把你選擇留意的 App 的套件名稱與飽和度數值（0 到 100）交給系統，沒有其他資料。
- 為了在 App 異常結束後仍能恢復顏色，本 App 會在手機上暫存「目前被調整過的 App」清單，恢復後立即清除。
- 這項功能必須由你主動安裝 Shizuku、在 Shizuku 中允許本 App，並在設定中開啟；任何一步沒有完成，功能都不會運作。

### 權限一覽

| 權限 | 用途 |
|---|---|
| 無障礙服務（`BIND_ACCESSIBILITY_SERVICE`） | 如上所述，偵測前景 App、顯示淡化效果與提醒，並在你按下「回到生活」時關掉螢幕 |
| 通知政策存取權（`ACCESS_NOTIFICATION_POLICY`，選用） | Android 15 以上的灰階收尾功能 |
| 查詢可啟動的 App（`<queries>`，非 `QUERY_ALL_PACKAGES`） | 列出 App 供你選擇 |
| Shizuku API（`moe.shizuku.manager.permission.API_V23`，選用） | 與你自行安裝的 Shizuku 溝通，用於進階的逐 App 去飽和 |
| 查詢 Shizuku 是否安裝（`<queries>` 指定單一套件） | 在設定頁顯示 Shizuku 的狀態 |

本 App 不要求網路、位置、相機、麥克風、聯絡人、儲存空間或通知權限。

### 資料保存與刪除

- 所有資料都保存在本 App 的私有儲存空間中，其他 App 無法讀取。
- 本 App 已關閉 Android 的自動備份與裝置間轉移，資料不會被備份到雲端，也不會被轉移到新手機。
- 你可以隨時刪除全部資料：在系統設定中清除本 App 的儲存空間，或直接解除安裝本 App。

### iOS／iPadOS 版

iPhone 與 iPad 上的 Florsent 透過 Apple 的「螢幕使用時間」API（Family Controls、Managed Settings、Device Activity）運作，不使用上面描述的 Android 無障礙服務、Shizuku 或零打擾權限。

- **只拿到 Apple 的不透明代碼。** 你在 Apple 的系統選擇器裡挑要留意的 App、類別或網站，本 App 收到的是 Apple 加密過的代碼（token），看不到 App 名稱、網址或瀏覽紀錄。授權與否由你在系統的「螢幕使用時間」提示中決定，可以隨時在「設定 > 螢幕使用時間」收回。
- **偏好只存在這台裝置。** 你的選擇（上述代碼）、額度、睡前時間、聲音與呼吸偏好，只保存在本 App 與其延伸功能共用的 App Group（`group.app.florsent`）裡，不會同步到 iCloud，也不會傳到任何伺服器。解除安裝本 App 時會一併刪除。
- **洞察在 Apple 的沙盒裡產生。** 使用時間的圖表由 Apple 的 DeviceActivityReport 延伸功能繪製。這個延伸功能被系統隔離，不能寫出資料、不能連網，畫出來的內容只顯示在你的螢幕上，本 App 本身讀不到這些使用資料。
- **沒有網路連線、分析工具或帳號。** iOS 版不建立任何網路連線，不含廣告或分析 SDK，也不需要註冊或登入。
- 本 App 附有 Apple 要求的隱私清單（`PrivacyInfo.xcprivacy`），聲明不追蹤、不蒐集資料，只為了在裝置上保存偏好而使用 `UserDefaults`。

### 資料分享

本 App 不與任何第三方分享資料。由於本 App 沒有網路權限，資料在技術上也無法從本 App 傳送出去。

### 兒童

本 App 不針對 13 歲以下兒童設計，也不會蒐集任何人的個人資料。

### 政策變更

若本政策有所變更，我們會更新本頁面與上方的生效日期。如果變更涉及資料處理方式，會在 App 更新說明中註明。

### 聯絡我們

對本政策有任何問題，請在本專案的 GitHub Issues 提出：<https://github.com/Cherish-Cooperative/Florsent/issues>

---

## English

Florsent ("the App") helps people spend less time on social media, on Android and on iOS / iPadOS. This policy explains what data the App handles, why, and how it is stored. The sections below describe the Android app; see [iOS / iPadOS](#ios--ipados) for how the iPhone and iPad app works.

### In short

All data the App needs is processed and stored only on your phone. We do not collect, upload, sell or share it with anyone. The App has no internet permission and contains no advertising or analytics SDKs.

### Data the App handles on your phone

| Data | Purpose | Where it is kept |
|---|---|---|
| The package name of the app currently in the foreground (for example `com.instagram.android`) | To tell whether you are using one of the apps you chose to watch, so its time can be counted | Used in memory; only usage sessions of watched apps are written to the usage history, with your consent (see below) |
| Usage history (with your consent) | To learn when you usually put the phone down, and to show bedtime reminders and usage insights | On the phone; deleted automatically after 30 days |
| The list of launchable apps on the phone (names and icons) | To let you choose which apps to watch | Not stored; read when you open the list |
| Usage totals (today, this session, time over budget) | To track your budgets and decide when to fade the screen and show the reminder | On the phone (the App's private storage) |
| Your settings (watched apps, budgets, quiet time, sound and breathing preferences) | To make the App work the way you set it up | On the phone (the App's private storage) |

The App does **not** access or process on-screen text or content, anything you type, notification contents, contacts, photos, location, account information, or any data that personally identifies you.

### Usage history (with your consent)

To learn your daily rhythm and offer bedtime reminders and usage insights, the App keeps the following on your phone once you agree:

- The names of the apps you chose to watch, with the start time and length of each session.
- When the screen was unlocked and in use (times only, no app names).
- When recording started and stopped, and the UTC offset at the time (so past nights keep their local time after you travel).

It is used only to:

- Work out, on the phone, when you usually put the phone down. The algorithm runs locally and uses no cloud service or AI model.
- Show today's, the last 7 days' and your most used apps' time.

Storage and control:

- The history is kept in the App's private storage and deleted automatically after 30 days.
- You can switch off "record usage sessions" or tap "clear usage history" in Settings > Advanced at any time; clearing deletes all history and what was learned from it.
- New users see this in the explanation shown before turning on the accessibility service; users upgrading from an earlier version are asked once on the home screen. If you choose not to record, nothing is kept and bedtime guidance can still use a time you choose.

### Use of the AccessibilityService API

The App's core features require you to turn on the "Florsent 漸進引導" (Florsent Gentle Guidance) accessibility service in system settings. The App is not an assistive tool for people with disabilities.

**Purpose:**

- To detect which app is in the foreground, used only to count the time spent in the apps you chose to watch.
- To show the gradual fading effect and the "Take a breath?" reminder on screen.
- To turn the screen off (the same as pressing the power button) when you tap "Back to life" after a breathing exercise. You can turn this off in Settings.

**Limits:**

- It receives only window-state-change events (`TYPE_WINDOW_STATE_CHANGED`) and reads only the app's package name from them; screen on/off and unlock times come from system broadcasts.
- It is configured not to retrieve window content (`canRetrieveWindowContent=false`), so it cannot see on-screen text, images or anything you type.
- It performs no gestures. Its only system action is turning the screen off as described above, and only when you tap "Back to life".
- Nothing it learns ever leaves your phone.

**Consent and turning it off:** Before the service is turned on, the App shows the explanation above and only takes you to system settings after you agree. You can turn the service off at any time under Settings > Accessibility; counting and screen effects stop immediately.

### Optional permission: Do Not Disturb access (Android 15 and later)

If you turn on "switch to black and white when fully faded" in settings, the App asks for Do Not Disturb (notification policy) access. It is used only to create and switch a system mode called "Florsent 漸進灰階" (Florsent Gradual Grayscale), whose only effect is to turn the screen grayscale. It does not change your notifications, sounds or other modes. You can turn the feature off or revoke the access at any time; turning the feature off also deletes the mode.

### Optional advanced feature: per-app desaturation with Shizuku

If you install and start Shizuku yourself and turn on "let apps fade to gray" in the App's settings, the App uses Shizuku to lower the colour saturation of the app you are using, so it slowly turns grayscale.

- Only the package name of the app you chose to watch and a saturation value (0 to 100) are passed to the system, nothing else.
- So that colours can be restored even after an unexpected exit, the App keeps a short on-device list of apps whose colour it changed, and clears it as soon as they are restored.
- The feature only works after you install Shizuku, allow the App in Shizuku, and turn the feature on in settings.

### Permissions

| Permission | Purpose |
|---|---|
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`) | As described above: detect the foreground app, show the fade and reminder, and turn the screen off when you tap "Back to life" |
| Notification policy access (`ACCESS_NOTIFICATION_POLICY`, optional) | The grayscale finish on Android 15 and later |
| Querying launchable apps (`<queries>`, not `QUERY_ALL_PACKAGES`) | Listing apps for you to choose from |
| Shizuku API (`moe.shizuku.manager.permission.API_V23`, optional) | Talking to the Shizuku app you installed, for the advanced per-app desaturation |
| Checking whether Shizuku is installed (`<queries>` for that one package) | Showing Shizuku's status in settings |

The App does not request internet, location, camera, microphone, contacts, storage or notification permissions.

### Storage and deletion

- All data is kept in the App's private storage, which other apps cannot read.
- Android Auto Backup and device-to-device transfer are turned off, so the data is never backed up to the cloud or moved to a new phone.
- You can delete everything at any time by clearing the App's storage in system settings or by uninstalling the App.

### iOS / iPadOS

On iPhone and iPad, Florsent works through Apple's Screen Time APIs (Family Controls, Managed Settings and Device Activity). It does not use the Android accessibility service, Shizuku or Do Not Disturb access described above.

- **Opaque Apple tokens only.** You choose the apps, categories or websites to watch in Apple's own system picker, and the App receives encrypted tokens from Apple. It never sees app names, web addresses or your browsing history. You grant access in the system Screen Time prompt and can revoke it at any time under Settings > Screen Time.
- **Preferences stay on this device.** Your selection (those tokens), budgets, bedtime, sound and breathing preferences are stored only in the App Group shared by the App and its extensions (`group.app.florsent`). They are not synced to iCloud or sent to any server, and they are deleted when you uninstall the App.
- **Insights are drawn inside Apple's sandbox.** Usage charts are rendered by Apple's DeviceActivityReport extension, which the system isolates so it cannot write data out or reach the network. What it draws is shown only on your screen; the App itself cannot read that usage data.
- **No network connections, analytics or accounts.** The iOS app makes no network connections, contains no advertising or analytics SDKs, and needs no sign-up or sign-in.
- The App ships the privacy manifest Apple requires (`PrivacyInfo.xcprivacy`), declaring no tracking, no data collection, and `UserDefaults` used only to keep preferences on the device.

### Sharing

The App shares no data with any third party. Because it has no internet permission, the App is technically unable to send data off the device.

### Children

The App is not directed at children under 13 and does not collect personal data from anyone.

### Changes

If this policy changes, we will update this page and the effective date above. Changes to how data is handled will also be noted in the App's release notes.

### Contact

For any question about this policy, please open an issue on the project's GitHub: <https://github.com/Cherish-Cooperative/Florsent/issues>
