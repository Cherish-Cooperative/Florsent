# Florsent 技術文件

<p>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android">
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose, Material 3">
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-56705A" alt="Min SDK 26">
  <img src="https://img.shields.io/badge/APK-4.6%20MB-56705A" alt="Release APK 4.6 MB">
  <a href="../LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-56705A" alt="License: Apache 2.0"></a>
</p>

這份文件是 Florsent 架構、設計取捨與開發流程的權威參考，寫給人類開發者，也寫給協助開發的 AI agent。產品本身的介紹請見 [README](../README.md)，隱私權政策請見 [PRIVACY.md](../PRIVACY.md)。

修改程式碼時，如果改變了這裡描述的行為，請一併更新這份文件。

## 目錄

- [專案速覽](#專案速覽)
- [架構](#架構)
- [漸進引導狀態機](#漸進引導狀態機)
- [視覺效果三層](#視覺效果三層)
- [呼吸練習](#呼吸練習)
- [效能與省電](#效能與省電)
- [設計系統與無障礙](#設計系統與無障礙)
- [隱私與權限](#隱私與權限)
- [編譯與測試](#編譯與測試)
- [模擬器測試與踩坑](#模擬器測試與踩坑)
- [Google Play 上架](#google-play-上架)
- [不可違反的規則](#不可違反的規則)

## 專案速覽

| 項目 | 內容 |
|---|---|
| applicationId／namespace | `app.florsent`（上架後不能再改） |
| 語言與 UI | Kotlin 2.4.21、Jetpack Compose（BOM 2026.11.01）、Material 3 |
| 建置 | AGP 9.5.2（內建 Kotlin 支援）、Gradle 9.9.2、Java 17 target |
| SDK | minSdk 26、targetSdk 36、compileSdk 37.1 |
| 依賴 | AndroidX core／activity／lifecycle／DataStore、Coroutines、Shizuku API 13.1.5（選用功能） |
| 不使用 | 網路、DI 框架、Navigation 函式庫、任何分析或廣告 SDK |
| Release APK | 約 4.6 MB（R8 與資源縮減） |

compileSdk 37.1 是新版 AndroidX 的要求，只決定編譯時能用哪些 API；執行時行為由 targetSdk 36 決定。

## 架構

```
app.florsent
├── FlorsentApp.kt          Application 與手寫的 AppContainer（依賴容器）
├── MainActivity.kt         單一 Activity；edge-to-edge；處理浮出提醒帶進來的 intent
├── domain/                 純 Kotlin，不依賴 Android
│   ├── GuidanceEngine      額度狀態機：reduce()、effect()、status()、nextTickDelayMs()
│   ├── GuidanceState       狀態、事件（GuidanceEvent）、輸出效果（GuidanceEffect）
│   ├── GuidanceConfig      所有可調參數與預設值
│   ├── ForegroundPolicy    把無障礙事件轉成「目前在前景的 App」
│   ├── Breathing           四種呼吸法、時間軸（展開程度、階段、倒數）
│   ├── SaturationPlanner   第三層：強度換算飽和度、產生還原優先的指令
│   └── Easing              smoothstep
├── data/
│   ├── SettingsRepository  使用者設定（DataStore）
│   ├── GuidanceStateStore  引擎狀態的持久化（GuidanceStatePersistence 介面）
│   └── InstalledAppsRepository  可啟動 App 清單與圖示快取
├── service/
│   ├── FlorsentAccessibilityService  只監聽 TYPE_WINDOW_STATE_CHANGED；承載 overlay 視窗
│   ├── GuidanceCoordinator  全域狀態、tick 排程、持久化節流
│   ├── NudgeOverlay        浮出提醒（plain View）
│   ├── effect/             VisualEffectController 與三層實作、SaturationSession
│   ├── shizuku/            ShizukuSaturation、PlatformShizukuClient、ShellService（AIDL）
│   └── audio/              BreathingSound／BreathingSoundscape
└── ui/                     Compose 畫面與 ViewModel
    ├── onboarding/ home/ breathe/ grounding/ apps/ settings/ components/
    └── theme/              配色、動態色、ContrastAudit
```

### 資料流

```
無障礙事件 / 螢幕開關 / UI 操作
        │  GuidanceEvent
        ▼
GuidanceCoordinator.dispatch()  ──►  GuidanceEngine.reduce(state, event, config)  （純函式）
        │                                    │
        │  StateFlow<GuidanceState>          │  GuidanceEngine.effect()
        ▼                                    ▼
   UI（ViewModel）                     StateFlow<GuidanceEffect>
                                             │
                                             ▼
                         FlorsentAccessibilityService
                         └─ CompositeVisualEffectController
                            ├─ DimOverlayController        （第一層）
                            ├─ ZenGrayscaleController      （第二層，API 35+）
                            └─ ShizukuSaturationController （第三層，選用）
                         └─ NudgeOverlay
```

所有時間相關的規則都在 `domain/`，是可以在 JVM 上直接測試的純函式。Android 端只是轉接器。

## 漸進引導狀態機

### 參數（`GuidanceConfig`）

| 參數 | 預設 | 說明 |
|---|---|---|
| `sessionBudgetMs` | 15 分鐘 | 連續使用多久後開始變淡（使用者可調 5～60） |
| `dailyBudgetMs` | 60 分鐘 | 一天累計上限（可調 15～240） |
| `dailyGraceMs` | 2 分鐘 | 每日額度用完後，每段使用仍有的緩衝 |
| `fadeDurationMs` | 3 分鐘 | 超過額度後，從無到最強的時間 |
| `nudgeAtProgress` | 0.5 | 淡化進度到一半時浮出提醒 |
| `nudgeSnoozeMs` | 2 分鐘 | 按「✕」延後多久 |
| `cooldownMs` | 20 分鐘 | 安靜時間（可調 5～60） |
| `sessionGapMs` | 10 分鐘 | 離開多久算新的一段使用 |
| `breathingTimeoutMs` | 15 分鐘 | 練習開著不動多久視為放棄 |
| `maxTickIntervalMs` | 15 秒 | 沒有變化時最久多久醒一次 |
| `maxTickGapMs` | 20 秒 | 單次計時上限，防止凍結程序或時鐘跳動被算成使用時間 |
| `preview` | 30 秒／20 秒淡化／12 秒提醒 | 首頁的體驗模式 |

### 模式

- `Tracking`：計時中；超過額度就累積 `overBudgetMs`，驅動淡化。
- `Breathing`：使用者點了提醒。回到留意的 App 會視為放棄並回到 `Tracking`（淡化程度保留）。
- `Cooldown`：完成呼吸後的安靜時間，只累計每日用量，不顯示效果。

### 規則重點

- **計時**：只有螢幕開著、而且前景是留意的 App 時才計時。每次事件先用「前一個前景」結算經過的時間，再套用事件。
- **超過額度**：`max(單次超過, min(每日超過, 本段已用 − 緩衝))`。兩項都隨使用 1:1 增加，所以夾在 `[0, delta]` 就是這一步中超過額度的部分。
- **跨日**：日期只往前跑；往回超過一天才視為真正的時鐘重設。時區每次重新讀取。
- **時鐘保護**：安靜時間、延後提醒的到期時間都有上限；未來的時間戳記（時鐘被調回去）會被丟掉或視為過期。
- **新的一段使用**：開始計時時，若距離上次使用超過 `sessionGapMs`，清除本段累計。
- **完成呼吸**：從真正的引導進入才會進入安靜時間；從預覽進入不會；在首頁主動練習、而且已經超過額度時也會。

### tick 排程（`nextTickDelayMs`）

| 情況 | 下一次 tick |
|---|---|
| 螢幕關閉、前景不是留意的 App | 不排程（零輪詢） |
| 預覽中、淡化進行中 | 1 秒 |
| 額度內 | 到額度用完的時間，最多 15 秒 |
| 延後提醒中 | 到延後結束，最多 15 秒 |
| 安靜時間 | 到安靜時間結束，最多 15 秒 |

`GuidanceCoordinator` 每次狀態改變都重新排程（刻意不用 `distinctUntilChanged`，否則連續兩次相同間隔會漏排）。

### 持久化

- 只保存需要的欄位（前景 App、螢幕狀態、上次 tick、預覽都不存）。
- 例行寫入最多每 30 秒一次，內容沒變就不寫。
- 切換模式、跨日、安靜時間變動、停止計時（關螢幕、離開 App）時立即寫入。
- 載入完成前收到的事件會排隊，載入失敗也不會卡住。

## 視覺效果三層

所有效果都實作 `VisualEffectController`：`render(VisualEffect(level, targetPackage))` 與 `release()`。`level` 是已經過 smoothstep 的 0～1。三層彼此獨立，任一層失敗都不影響其他層。

| 層 | 實作 | 條件 | 預設 |
|---|---|---|---|
| 第一層：中性灰暗化 | `DimOverlayController` | 無障礙服務開啟 | 開 |
| 第二層：灰階收尾 | `ZenGrayscaleController` ＋ `PlatformZenRuleGateway` | Android 15+、「零打擾」存取權 | 關 |
| 第三層：逐 App 去飽和 | `ShizukuSaturationController` ＋ `ShizukuSaturation` | Shizuku 已安裝、已啟動、已授權 | 關 |

### 第一層：中性灰 overlay

- `TYPE_ACCESSIBILITY_OVERLAY`、全螢幕、`FLAG_NOT_TOUCHABLE`，覆蓋狀態列與導覽列。這是受信任的視窗，透明度再高也不會擋住觸控（`TYPE_APPLICATION_OVERLAY` 超過 0.8 會被擋）。
- 顏色 `#858585`，最高透明度 0.6。混入中間灰會同時降低對比與飽和度。
- 每次 `render` 用一秒的線性 `ViewPropertyAnimator` 銜接，所以以螢幕刷新率連續變化；`hasOverlappingRendering() = false`，避免全螢幕離屏圖層。強度回到 0 時移除視窗。

### 第二層：Zen 灰階收尾（Android 15+）

- 建立 Florsent 專屬的 `AutomaticZenRule`：`INTERRUPTION_FILTER_ALL`，唯一的裝置效果是 `ZenDeviceEffects.setShouldDisplayGrayscale(true)`，`allowManualInvocation=false`。
- 強度到 0.98 開啟、低於 0.9 關閉（遲滯，避免閃爍）。
- **第一次 render 一定同步一次**：規則的狀態比 App 程序活得久，當機後不能假設它是關的。
- 關閉設定時刪除規則；沒有存取權時不呼叫任何平台 API；平台呼叫失敗不重試，等下次強度變化再評估。
- 驗證：`adb shell dumpsys notification` 中「Florsent 漸進灰階」的 `STATE_TRUE`／`STATE_FALSE`。

### 第三層：Shizuku 逐 App 去飽和

**執行方式**：Shizuku 13.1.5 的 `newProcess` 已改為 private，所以使用官方推薦的 UserService：

- `ShellService` 在 Shizuku 程序中以 shell（或 root）身分執行，只暴露 `setLayerSaturation(level, caller, target)`，自行驗證參數後組出固定指令 `cmd color_display set-layer-saturation <level> <caller> <target>`，不提供任意指令執行。
- `UserServiceArgs`：`daemon(false)`（隨 App 程序結束）、明確的 `tag`（R8 會改類別名稱）、`version = VERSION_CODE`。
- `proguard-rules.pro` 保留 `ShellService` 的建構子。

**AOSP 行為**（決定了還原策略）：

- 每個 App 的飽和度只存在 system_server 的記憶體中，**呼叫端程序結束後不會清除**，重新開機才會清除。
- 實際套用的是所有呼叫端中的最小值；同一呼叫端設回 100 會移除自己的紀錄。
- shell 需要 `CONTROL_DISPLAY_COLOR_TRANSFORMS`：Android 14 QPR2 起才有。更早的版本指令會失敗，這一層會自動降級。

**不會卡在黑白的保證**（`SaturationPlanner`、`SaturationSession`、`ShizukuSaturation`）：

1. 每個 App 在被調低之前，先同步寫入待還原清單（SharedPreferences `commit()`）；成功恢復到 100 才移除。
2. 指令一律先還原、再調低；目標改變時舊目標先恢復。
3. 離開 App、呼吸、安靜時間、關閉開關、服務關閉，都會讓期望狀態變成空的，於是全部恢復。
4. App 重新啟動或 Shizuku 重新連上時，`recover()` 先把清單裡的 App 全部恢復。
5. 任何一次失敗就進入降級：停止調低並嘗試還原，第一、二層繼續引導；使用者可在設定頁重新連線。
6. Shizuku 中途停止時無法還原，設定頁會照實提示，Shizuku 重新啟動後一秒內自動還原。
7. 飽和度量化成 5 一階，三分鐘淡化最多約 20 次指令；所有指令在單一背景執行緒依序執行，且只套用最新的請求。
8. 只在功能開啟或清單不為空時才綁定 UserService。

**設定頁狀態**：未安裝 → 未啟動 → 版本太舊 → 未授權 → 已就緒，另有「降級」與「待還原」提示，每個狀態一句說明、一個動作。

## 呼吸練習

| 呼吸法 | 節奏（秒） | 輪數 | 總長 |
|---|---|---|---|
| 等長呼吸 `equal_444` | 4・4・4 | 6 | 1:12 |
| 盒式呼吸 `box_4444` | 4・4・4・4 | 5 | 1:20 |
| 4-7-8 放鬆呼吸 `relax_478` | 4・7・8 | 4（上限） | 1:16 |
| 共振呼吸 `resonance_55` | 5.5・5.5 | 7 | 1:17 |

- `BreathingTimeline` 是純函式：`expansionAt()`（逐幀、零配置，用 index 迴圈）、`displayAt()`（每秒變一次）、`sessionProgress()`、`idleExpansion()`。展開程度用 ease-in-out sine，在階段交界連續。
- `BreathingFlower`：兩層各 6 片花瓣、光暈、中心微光、進度環。路徑、漸層、筆觸都在 `drawWithCache` 建好；動畫數值只在 draw lambda 裡讀取，所以每一幀只重畫、不重組。
- `rememberFrameClock`：以 `withFrameNanos` 累積時間；`rememberSaveable`，旋轉螢幕後接續；超過 250 ms 的間隔（App 在背景）只算一個標準幀，所以會暫停而不是跳過；閒置的花限制在約 30 fps。
- 換階段時輕微震動（API 34+ `SEGMENT_TICK`，否則 `CLOCK_TICK`）；提示音只在設定開啟時播放，並在選擇頁預先載入。
- 環境音在 App 進入背景時暫停並釋放音訊焦點；音效引擎延遲到真的需要時才建立。
- 關閉系統動畫時，花依階段張開或收合，不做連續動畫。
- TalkBack：階段標籤是唯一的 live region（polite），倒數與輪數合併成一句、不朗讀每一秒。

## 效能與省電

| 項目 | 做法 |
|---|---|
| 背景喚醒 | 螢幕關閉、非留意的 App 時沒有任何計時器；其他時候依下一個期限排程 |
| 無障礙事件 | 只訂閱 `typeWindowStateChanged`；系統介面、權限對話框、分享選單、輸入法不改變前景 |
| 儲存 | 例行最多 30 秒一次、內容相同不寫、重要時刻立即寫 |
| 繪圖 | overlay 只改透明度；花在 draw 階段讀取動畫值、零重組 |
| 記憶體 | 圖示 LRU 以 bytes 計量（4 MB）、以尺寸為 key；SoundPool 延遲建立 |
| 平台呼叫 | Zen 存取權只在接近頂端時才查詢；Shizuku 指令量化並合併 |

## 設計系統與無障礙

- **配色**：鼠尾草綠、晨霧灰、暖石色，深淺兩套，完整定義 M3 的 `surfaceContainer*` 層級。卡片使用 `surfaceContainerLow`。
- **動態色**：Android 12+ 可在設定中開啟「跟隨系統主題色」，改用 `dynamicLightColorScheme`／`dynamicDarkColorScheme`。所有元件（包括呼吸花）都從 `ColorScheme` 取色，所以任何桌布配色下都協調。預設關閉，維持品牌色。
- **對比驗證**：`ContrastAudit` 以 WCAG 2.x 公式計算 Florsent 實際使用的文字／背景組合（包括選中卡片的半透明疊色）。品牌深淺色由單元測試保證全部 ≥ 4.5:1；debug build 在動態色啟用時，會把實際配色的對比記錄到 logcat 的 `FlorsentContrast`。
- **M3 與系統整合**：edge-to-edge、`enableOnBackInvokedCallback="true"`（預測性返回）、adaptive icon 含 monochrome 圖層（主題圖示）。
- **App 圖示「展葉之 F」**：鼠尾草綠線性漸層加左上柔光，前景是主葉、內瓣與露珠組成的 F，皆為 108×108 viewport 的向量。
- **無障礙慣例**：標題加 `heading()`；裝飾圖形不進語意樹；相關文字合併成一個節點；觸控目標至少 48dp；不要用 `outline` 當文字顏色；大字級與橫向不能被裁切。

## 隱私與權限

合併後的 release manifest 只有這些權限：

| 權限 | 來源與用途 |
|---|---|
| `ACCESS_NOTIFICATION_POLICY` | 第二層灰階收尾（選用） |
| `moe.shizuku.manager.permission.API_V23` | Shizuku SDK 加入；與使用者自行安裝的 Shizuku 溝通（選用） |
| `app.florsent.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX 為未匯出的動態 receiver 自動加入 |

此外宣告了無障礙服務（`BIND_ACCESSIBILITY_SERVICE`）、Shizuku provider，以及 `<queries>`（可啟動的 App、Shizuku 套件）。

- **沒有網路權限**。新增依賴後務必檢查合併後的 manifest。
- `allowBackup="false"`，`backup_rules.xml` 與 `data_extraction_rules.xml` 排除所有資料網域：不雲端備份、不裝置間轉移。
- 無障礙服務：`canRetrieveWindowContent=false`、`isAccessibilityTool=false`。開啟前在 Onboarding 第三頁（或首頁對話框）顯示 Play 要求的 prominent disclosure。
- 任何權限或資料處理的變動，都要同步更新 [PRIVACY.md](../PRIVACY.md)。

## 編譯與測試

```sh
./gradlew testDebugUnitTest      # 99 個 JVM 測試
./gradlew assembleDebug
./gradlew assembleRelease        # 確認 R8 沒有移除 ShellService
```

### 測試架構

為了能在 JVM 上測試，平台呼叫都藏在介面後面：

| 介面 | 平台實作 | 用途 |
|---|---|---|
| `GuidanceStatePersistence` | `GuidanceStateStore` | 測試 coordinator 的寫入節流 |
| `ZenRuleGateway` | `PlatformZenRuleGateway` | 測試灰階收尾的開關邏輯 |
| `ShizukuClient`／`ShellHandle` | `PlatformShizukuClient` | 測試 Shizuku 狀態、綁定與還原 |
| `SaturationBackend`／`DirtyPackageStore` | `ShizukuSaturation` 內部、`SharedPrefsDirtyStore` | 測試第三層的還原保證 |
| `BreathingSound` | `BreathingSoundscape` | 測試 ViewModel 的聲音生命週期 |

| 測試 | 涵蓋 |
|---|---|
| `GuidanceEngineTest`（27） | 額度、淡化、提醒、呼吸、安靜時間、預覽、跨日、時鐘保護、tick 排程 |
| `BreathingTimelineTest`（11） | 四種呼吸法、展開連續性、倒數、結束 |
| `ForegroundPolicyTest`（3） | 系統介面、輸入法、自己的 overlay |
| `SaturationPlannerTest`（6）、`SaturationSessionTest`（10） | 量化、還原優先、當機補救、失敗降級 |
| `GuidanceCoordinatorTest`（7） | 虛擬時間下的零輪詢、每秒淡化、寫入節流、載入前事件排隊 |
| `ZenGrayscaleControllerTest`（7） | 第一次同步、遲滯、存取權、失敗不重試 |
| `ShizukuSaturationTest`（8） | 五種狀態、按需綁定、連線後還原、Shizuku 中途停止、降級與重試 |
| `SettingsRepositoryTest`（5）、`GuidanceStateStoreTest`（3） | 真實 DataStore 檔案上的預設值、往返與持久化 |
| `BreatheViewModelTest`（8） | 聲音延遲建立、背景暫停、提示音開關、釋放 |
| `ContrastAuditTest`（4） | WCAG 公式、品牌深淺色全部通過 AA |

單元測試開啟了 `unitTests.isReturnDefaultValues = true`，讓 `android.util.Log` 等 stub 回傳預設值。

## 模擬器測試與踩坑

### 常用指令

```sh
# 開啟無障礙服務（等同使用者在設定裡打開）
adb shell settings put secure enabled_accessibility_services app.florsent/app.florsent.service.FlorsentAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 第二層：授予「零打擾」存取權
adb shell cmd notification allow_dnd app.florsent

# 第三層：啟動已安裝的 Shizuku（官方 APK，簽章 CN=Rikka）
adb shell "$(adb shell pm path moe.shizuku.privileged.api | sed 's/package://; s/base.apk//')lib/arm64/libshizuku.so"

# 檢查第三層實際套用的飽和度
adb shell dumpsys color_display        # 看 App Saturation 區段
```

### 已知的坑

- **`uiautomator dump` 會暫停其他無障礙服務。** Florsent 的服務會被斷開再重新綁定，畫面暫時顯示「服務未開啟」，第三層也會因此還原。驗證服務相關狀態時，請改用截圖，或等幾秒再檢查。
- **`am force-stop` 與 `pm clear` 會讓系統移除已啟用的無障礙服務**，需要重新開啟。`kill -9` 不會，系統會自動重新綁定，適合用來模擬當機。
- **`adb shell monkey -p <pkg>` 在這個系統映像上常常啟動失敗。** 改用 `am start -n 'pkg/.Activity'`，類別名稱裡的 `$` 要加引號。
- **模擬器不支援顯示色彩轉換。** `cmd color_display set-saturation` 和 Zen 灰階的狀態會正確改變，但畫面不會變；要用 `dumpsys` 驗證，實際效果用實機確認。
- **權限對話框會短暫成為前景。** 例如 YouTube 第一次開啟時；Florsent 已把它視為暫時性介面，但測試時最好先授權。
- **`am start` 帶回已在前景的 Activity 不會觸發 onResume。** 設定頁的 Shizuku 狀態在 onResume 時更新，測試時先回桌面再開。
- **同一個 AVD 不能同時開兩個實例。** 另建一個 AVD，或加 `-read-only`。
- 首頁的「30 秒體驗漸進引導」在 20 秒內跑完淡化、第 12 秒浮出提醒；搭配 `dumpsys color_display` 可以完整驗證三層。

## Google Play 上架

| 項目 | 檔案 |
|---|---|
| 高解析度圖示 512 × 512 | [`docs/assets/play-icon-512.png`](assets/play-icon-512.png) |
| Feature Graphic 1024 × 500 | [`docs/assets/play-feature-graphic.png`](assets/play-feature-graphic.png) |
| 三步驟展示圖 1600 × 900 | [`docs/assets/promo-showcase.png`](assets/promo-showcase.png) |
| GitHub 橫幅 1280 × 640 | [`docs/assets/github-hero.png`](assets/github-hero.png) |
| App 圖示（向量） | `app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml` |
| 隱私權政策 | [`PRIVACY.md`](../PRIVACY.md)（需發布成公開網址） |

還需要：Play Console 的無障礙 API 聲明與示範影片、手機截圖。Shizuku 功能是使用者主動開啟的選用功能，提交審核時一併說明用途。

## 不可違反的規則

給所有參與開發的人與 AI agent：

1. **不加網路權限、不加分析或廣告 SDK。** 新依賴必須檢查合併後的 manifest。
2. **不擴大無障礙服務的範圍。** 只用 `TYPE_WINDOW_STATE_CHANGED`，不讀取視窗內容，不執行手勢。
3. **不封鎖 App。** 所有效果都要能逐步出現、逐步消失，提醒永遠可以延後。
4. **不讓使用者卡在黑白。** 第二、三層的任何改動，都要維持「先記錄再變灰、任何離開都還原、重啟就補救、失敗就降級」，並補上測試。
5. **時間規則寫在 `domain/`，而且要有單元測試。** 不要在 Android 元件裡寫計時邏輯。
6. **螢幕關閉或不在留意的 App 時零輪詢。**
7. **使用者看得到的文字放在 `strings.xml`**，用詞遵守 [CONTRIBUTING.md](../CONTRIBUTING.md) 的約定，並維持 WCAG AA。
8. **改了行為就更新文件**：這份文件、README、PRIVACY.md。
