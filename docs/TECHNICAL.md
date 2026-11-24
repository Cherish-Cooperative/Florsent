# Florsent 技術文件

<p>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android">
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose, Material 3">
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-56705A" alt="Min SDK 26">
  <img src="https://img.shields.io/badge/APK-3.1%20MB-56705A" alt="Release APK 3.1 MB">
  <a href="../LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-56705A" alt="License: Apache 2.0"></a>
</p>

這份文件是 Florsent 架構、設計取捨與開發流程的權威參考，寫給人類開發者，也寫給協助開發的 AI agent。產品本身的介紹請見 [README](../README.md)，隱私權政策請見 [PRIVACY.md](../PRIVACY.md)。

修改程式碼時，如果改變了這裡描述的行為，請一併更新這份文件。

## 目錄

- [專案速覽](#專案速覽)
- [架構](#架構)
- [漸進引導狀態機](#漸進引導狀態機)
- [使用紀錄與作息學習](#使用紀錄與作息學習)
- [睡前引導](#睡前引導)
- [使用洞察與設定架構](#使用洞察與設定架構)
- [視覺效果三層](#視覺效果三層)
- [呼吸練習](#呼吸練習)
- [效能與省電](#效能與省電)
- [設計系統與無障礙](#設計系統與無障礙)
- [隱私與權限](#隱私與權限)
- [編譯與測試](#編譯與測試)
- [模擬器測試與踩坑](#模擬器測試與踩坑)
- [實機驗證紀錄](#實機驗證紀錄)
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
| Release APK | 約 3.1 MB（R8 與資源縮減） |

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
| `bedtime` | `null` | 睡前引導（`BedtimeConfig`），使用者接受邀請或自己選時間後才有值 |

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
- **螢幕「使用中」的判斷**：只有解鎖後才計時。`SCREEN_ON` 會在鎖定畫面時就送來，不能當作解鎖；`ACTION_USER_PRESENT` 在 Android 17 帶有「延後到 App 活躍才送」的策略，而無障礙服務的程序可能停在快取狀態（實機 `curProcState=16`），結果解鎖廣播永遠送不到，第一次關螢幕之後就再也不計時（v0.10.1 修正的 P0）。現在每個視窗事件都會檢查：引擎仍認為螢幕鎖著、但裝置已亮且已解鎖時（`ScreenPolicy.unlockNotYetSeen`），立即回報螢幕使用中。解鎖後一定有桌面或 App 的視窗事件，所以不依賴任何廣播。`USER_PRESENT` 仍保留，在舊版系統上更早觸發。
- **留意哪些 App（`SocialAppMatcher`）**：首次啟動只從「可啟動」的 App 裡挑，依官方套件或名稱中的品牌字（YouTube、Instagram、Threads…）判斷，排除 YouTube Music／Studio／Kids 等非動態 App。已停用的官方 App 仍算「已安裝」，以前會被勾選，而實際在用的修改版用戶端（例如 `app.morphe.android.youtube`「YouTube Morphe」）卻沒被留意。既有使用者在 v0.10.1 執行一次修復（`repairWatchedOnce`，旗標 `watched_variants_repaired_v1`）：若某品牌留意的 App 都無法啟動，補上同品牌可啟動的 App；只做一次，之後手動取消不會被加回。
- **點提醒後的延遲事件**：點提醒開啟呼吸畫面後 2 秒內，剛離開的 App 送來的視窗事件一律忽略（`ForegroundPolicy.isHandoffEcho`）。否則像 YouTube 這類 App 收尾時的事件會被當成「回到 App」，取消呼吸，讓淡化和提醒蓋在呼吸畫面上。
- **完成呼吸**：從真正的引導進入才會進入安靜時間；從預覽進入不會；在首頁主動練習、而且已經超過額度時也會。

### tick 排程（`nextTickDelayMs`）

| 情況 | 下一次 tick |
|---|---|
| 螢幕關閉、前景不是留意的 App | 不排程（零輪詢） |
| 預覽中、淡化進行中 | 1 秒 |
| 額度內 | 到額度用完的時間，最多 15 秒 |
| 延後提醒中 | 到延後結束，最多 15 秒 |
| 安靜時間 | 到安靜時間結束，最多 15 秒 |
| 睡前引導已設定 | 再加一個期限：時段內是剩下的睡前額度，時段外是到下一個時段開始 |

`GuidanceCoordinator` 每次狀態改變都重新排程（刻意不用 `distinctUntilChanged`，否則連續兩次相同間隔會漏排）。

### 持久化

- 只保存需要的欄位（前景 App、螢幕狀態、上次 tick、預覽都不存）。
- 例行寫入最多每 30 秒一次，內容沒變就不寫。
- 切換模式、跨日、安靜時間變動、停止計時（關螢幕、離開 App）時立即寫入。
- 載入完成前收到的事件會排隊，載入失敗也不會卡住。

## 使用紀錄與作息學習

使用者同意後（`UsageConsent.Granted`）才記錄。新使用者在 Onboarding 的 disclosure 一併同意；從 0.7 升級的使用者在首頁看到一次「讓 Florsent 了解你的作息」卡片。

### 檔案格式（`files/usage/`）

每個 UTC 日一個 TSV 檔（`2026-11-24.tsv`），保存 30 天（`UsageLogStore.KEEP_DAYS`），啟動時刪除過期的檔案。每一行：

```
<種類>\t<開始秒>\t<長度秒>\t<UTC 偏移秒>[\t<套件名稱>]
```

| 種類 | 意義 |
|---|---|
| `A` | 留意的 App 的一段使用（只有這一種有套件名稱） |
| `S` | 螢幕亮著且已解鎖的一段時間 |
| `B`／`E` | 開始／停止記錄（服務啟動、關閉同意） |
| `U` | 不知道發生什麼事的時間（程序被殺） |

- 每一行帶自己的 UTC 偏移，換時區後過去的夜晚仍維持當地時間。
- 解析失敗的行直接略過（`UsageCodec.decode` 回傳 `null`），偏移限制在 ±18 小時。
- 所有檔案操作都在同一條 IO 執行緒（`florsent-usage-log`），不會互相競爭。

### 當機與程序被殺

`open.state`（tmp 檔再 rename）保存目前開著的螢幕／App 區段與最後事件時間。新程序第一次記錄時，若 `open.state` 仍是「記錄中」，`UsageRecorder.start` 會先寫一筆 `U`（從開著的區段或最後事件到現在），再寫 `B`。學習演算法把 `U` 與 `B…E` 以外的時間都視為看不見，所以死掉的程序絕不會被當成「螢幕關著，正在睡覺」。

程序剛啟動時，無障礙服務要過一下才會連上，這段時間的輸入是「沒有在記錄」。如果直接照做，每次重啟都會多一對同一秒的 `E`／`B`。所以 `open.state` 留著「記錄中」時，`UsageRecording` 會先等 10 秒（`STARTUP_GRACE_MS`）：服務在這段時間連上，就只寫 `U` 與 `B`；一直沒有連上（服務被關閉、撤回同意），才用 `UsageRecorder.abandon` 寫 `U` 與 `E`。

### 作息學習（`BedtimeLearner`）

1. 夜晚以中午切分（中午到隔天中午），隔天中午之後才算定案。
2. 合併螢幕區段之間的空檔。夜裡 10 分鐘以內的短暫亮螢幕，若前後空檔都至少 60 分鐘，視為看鬧鐘或回訊息，併入空檔（可連鎖）。
3. 留下至少 3 小時、開始於 20:00～05:00、而且沒有碰到看不見時段的空檔；每晚取最長的一段，開始時間就是這晚的 onset。
4. 不確定的夜晚整晚跳過：20:00～10:00 之間有看不見的時段，或 00:00～05:00 有超過 2 小時的螢幕區段（開著影片睡著）。
5. 最近 28 晚做加權中位數，半衰期 7 天。至少 3 晚且 MAD ≤ 60 分鐘為 `Confident`；記錄滿 7 天仍不穩定為 `Irregular`；其他為 `Learning`。

`UsageAnalysis` 在背景讀 31 天紀錄、跑學習與洞察規則，結果快取 60 秒。

### 學到的時間不會移動已接受的時段

學到的時間只用在首頁邀請（一次）與設定頁的「改用最近的作息」。使用者接受後，`bedtimeAnchorMinute` 就固定下來。如果時段跟著學到的時間走，提醒有效時 onset 會提早，時段也跟著提早，形成自我收緊的迴圈。`BedtimeLearnerTest` 有一個測試專門守住這點。

## 睡前引導

`BedtimeConfig`：

| 參數 | 預設 | 說明 |
|---|---|---|
| `anchorMinuteOfDay` | 使用者決定 | 想放下手機的時間；中午以前的時間屬於前一晚 |
| `leadMinutes` | 30 | 時段從 anchor 前 30 分鐘開始 |
| `thresholdMs` | 15 分鐘 | 時段內累計使用多久開始淡化（可選 10／15／20） |
| `secondThresholdMs` | 7 分鐘 | 第一次之後，再用多久會第二次淡化 |
| `cooldownMs` | 30 分鐘 | 睡前的安靜時間 |
| `maxNudgesPerNight` | 2 | 超過後只淡化，不顯示提醒 |

- 時段：`[anchor − 30 分, max(anchor + 2 小時, 隔天 05:00))`。計數在換時段時歸零，不在午夜歸零。
- 時段內的累計與 session／每日額度並行，三者取最先超過的那個。
- 淡化開始時凍結情境（`GuidanceContext`）：白天開始的淡化進入時段後仍是白天的文字，反之亦然。
- 睡前情境下：提醒文字「該準備休息了嗎？」，點下去固定用 4-7-8 呼吸，結束時是睡前版的 grounding 與「去休息吧」按鈕（直接回桌面）。
- 首頁在時段開始前 3 小時內顯示「今晚 23:10 起會溫和提醒你休息」。

## 使用洞察與設定架構

### 洞察（`UsageInsights`）

- 今天、近 7 天：App 區段依各自的偏移切到當地日期，跨午夜的區段拆成兩天。沒有記錄的日子是 `null`（長條圖畫虛線框），不是 0。
- 洞察句最多兩句：先講作息（`Confident` 時，取到 5 分鐘），再講兩週趨勢。
- 趨勢只比較「已經過完」的 14 天（今天不算，否則每天早上都會顯示「比上週少」），兩週每一天都要有紀錄。差距 15% 以內為「差不多」。用詞不評分、不責備。
- 洞察頁的「今天」取引擎的每日用量與紀錄的較大值，和首頁卡片一致（引擎從午夜開始算，紀錄只從同意後開始）。

### 設定架構（漸進揭露）

首頁只有一張「今天」卡片（點進洞察頁）、呼吸入口與一列「設定」。設定第一層五項：

| 項目 | 內容 |
|---|---|
| 留意的 App | 勾選要計時的 App |
| 使用額度 | 單次、每日、安靜時間 |
| 睡前引導 | 時段、可以滑多久、關閉 |
| 呼吸與聲音 | 呼吸練習環境音、變淡時播放柔和雨聲、提示音、完成後回桌面 |
| 進階 | 外觀、無障礙服務、黑白收尾、Shizuku、使用紀錄與清除 |

沒有「智慧自動引導」總開關，也沒有自動調整的每日額度（會形成自我收緊的迴圈）。預設值合理，學到東西時只邀請一次。

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

- `BreathingTimeline` 是純函式：`expansionAt()`（逐幀、零配置，用 index 迴圈）、`displayAt()`（每秒變一次）、`movingMsAt()`、`sessionProgress()`、`idleExpansion()`。展開程度用 ease-in-out sine，在階段交界連續。
- **閉氣時整個畫面停住**：花瓣的旋轉與微晃、進度環都用 `movingMsAt()`（扣掉所有閉氣與停頓時間的經過時間）驅動，展開程度在閉氣時本來就固定，所以閉氣期間花朵與進度環的像素完全不變，只有倒數數字在走。使用者跟著畫面一起停。
- `BreathingFlower`：兩層各 6 片花瓣、光暈、中心微光、進度環。路徑、漸層、筆觸都在 `drawWithCache` 建好；動畫數值只在 draw lambda 裡讀取，所以每一幀只重畫、不重組。
- `rememberFrameClock`：以 `withFrameNanos` 累積時間；`rememberSaveable`，旋轉螢幕後接續；超過 250 ms 的間隔（App 在背景）只算一個標準幀，所以會暫停而不是跳過；閒置的花限制在約 30 fps。
- 換階段時輕微震動（API 34+ `SEGMENT_TICK`，否則 `CLOCK_TICK`）；提示音只在設定開啟時播放，並在選擇頁預先載入。
- 環境音在 App 進入背景時暫停並釋放音訊焦點；音效引擎延遲到真的需要時才建立。

### 聲音

| 音檔 | 用途 | 內容 |
|---|---|---|
| `res/raw/breathing_ambient.ogg` | 呼吸練習環境音 | 暖調頌缽共鳴與柔霧粉紅噪聲，48 秒，無敲擊節奏 |
| `res/raw/fade_ambient.ogg` | 變淡時的柔和雨聲 | 遠雨與晚風，42 秒 |
| `res/raw/cue_soft.wav` | 提示音 | 1.6 秒，22.05 kHz 單聲道 16-bit PCM（70 KB），依階段改變播放速率 |

- 兩首環境音由音景團隊以程式合成，沒有版權風險。原檔是 AAC（.m4a），但 AAC 編碼器會在開頭補約 46 ms 靜音，`MediaPlayer` 循環時不會去掉，每一圈都會斷一下。所以從去掉補位的 PCM 轉成 Opus（112 kbps VBR，Ogg 容器），轉檔後長度精準（48.0000 秒、42.0000 秒）。v0.10.2 依實機回饋整段加上固定增益再轉一次 Opus（128 kbps VBR），只調音量、不壓縮動態，循環接縫不變：頌缽 -13.0 LUFS、true peak -2.6 dBFS（+5.6 dB）；雨聲 -13.6 LUFS、true peak -1.3 dBFS（+5.0 dB）；提示音 `cue_soft.wav` 峰值 -15.5 dBFS（+6.5 dB）。播放倍率：頌缽 0.75（峰值約 -5 dBFS，不會破音）、提示音 0.42、雨聲 0.6。頌缽依實測回饋又加大約 3.5 dB，提示音跟著加約 1.6 dB，在較響的頌缽上仍清楚、不刺耳。
- **無縫循環（`GaplessLoop`）**：檔案本身沒有縫還不夠。`MediaPlayer.isLooping` 每一圈結束都會跳回開頭，解碼器要重新預熱（Opus pre-roll 80 ms），可能聽到短暫斷音。所以頌缽與雨聲都用 `setNextMediaPlayer` 把兩個播放器接起來：下一圈事先準備好，由系統無縫接手（logcat 會看到 `info (2, 0)` = `MEDIA_INFO_STARTED_AS_NEXT`），播完的那圈釋放、再排下一圈。
- **提示音（`CuePlayer`）**：預先解碼的 WAV 放進靜態 `AudioTrack`，每次只是重頭播放、以 `playbackRate` 改音高；指定 `PERFORMANCE_MODE_POWER_SAVING`，和頌缽走同一條大緩衝輸出，不會像 `SoundPool`（預設低延遲）那樣每響一次就喚醒另一條輸出、讓頌缽卡一下。所有呼叫在 `florsent-cue` 執行緒，不佔主執行緒。
- **提示音只響一次（`PhaseCueGate`）**：去重在 ViewModel，以（session、輪次、階段）為鍵，只接受嚴格遞增的階段。畫面重建、同一階段被回報兩次、時鐘倒退，都不會讓提示音或震動重複。
- 音量淡入淡出（`VolumeRamp`）依實際經過時間計算，不受系統「移除動畫」影響。
- **變淡時播放**（`FadeSoundController` + `FadeSoundscape`）是視覺效果組合裡的一層，和淡化層收到同樣的淡化程度：
  - 淡化程度大於 0、開關打開、沒有在通話（`MODE_IN_CALL`／`IN_COMMUNICATION`／`RINGTONE`）才播放。
  - 音訊焦點用 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，系統自動把前景 App 壓低音量，不會暫停；講話內容（Podcast 等）由系統自行暫停。
  - 15 秒淡入（音量曲線 0.6 × p²，v0.10.2 前是 0.4），離開時 1.5 秒淡出。淡入淡出用實際經過時間計算，不受「移除動畫」設定影響。
  - 點提醒時呼叫 `handOffToBreathing()`，改用 3.5 秒淡出，和呼吸畫面約 1 秒的啟動、2.5 秒的環境音淡入交疊。呼吸環境音要焦點時，正在淡出的雨聲不會被截短。
  - 來電或響鈴（`MODE_IN_CALL`、`MODE_IN_COMMUNICATION`、`MODE_RINGTONE`）一律不播；播放中遇到就 1.5 秒淡出，這一輪淡化不再出聲。第一次要求焦點就被拒絕時，同樣這一輪不再嘗試。
  - 其他 App 搶走焦點時（`FocusReclaimPolicy`）：滑 Reels、Shorts 時，每支新影片都會要一次焦點。v0.10.1 以前一被搶就整輪放棄，雨聲幾秒內就消失。現在等 1.2 秒冷卻後再用 `GAIN_TRANSIENT_MAY_DUCK` 要回來，讓影片被壓低、雨聲繼續。冷卻期間不管收到幾次焦點變化，都只排一次重新要求。拿到焦點後不到 1 秒又被搶走、或重新要求被拒絕，就算「對方在搶」：冷卻加倍（1.2、2.4、4.8、9.6 秒，上限 9.6 秒），連續 5 次以後這一輪放棄，避免和某個 App 無限互搶。一般滑動之間隔超過 1 秒，所以每次都只等 1.2 秒。焦點自己回來（`AUDIOFOCUS_GAIN`）、離開 App、點提醒交接時，排定的重新要求都會取消。只有留意的 App 正在變淡時才有聲音，所以只會從使用者正在滑的動態那裡把焦點拿回來。
- Android 17 的背景音訊強化會把背景 App 的播放靜音、焦點要求回傳失敗。綁定中的無障礙服務不受影響：模擬器開啟完整強制（`cmd audio set-enable-hardening 1`）時，雨聲仍是 started、`muted=none`，焦點為 `GAIN_TRANSIENT_MAY_DUCK`。不需要前景服務或新權限。
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
- **語言**：英文是預設資源（`res/values/`），繁體中文在 `res/values-zh-rTW/`。Android 7 以後會依文字系統比對，所以 zh-HK 等繁體中文地區也會用到繁中資源。
  - 系統層級：`res/xml/locales_config.xml` + `android:localeConfig`，Android 13 以上的「App 語言」設定會列出 English 與繁體中文。
  - App 內：「設定 > 進階 > 外觀 > 語言 / Language」（跟隨系統／繁體中文／English）。`AppLocales` 在 Android 13 以上呼叫 `LocaleManager.setApplicationLocales`，整個程序（包括無障礙服務的提醒文字）一起切換；Android 8～12 存在 `app_language` SharedPreferences，由 `MainActivity` 與無障礙服務的 `attachBaseContext` 套用，切換後重建 Activity（服務在下次啟動時才換語言）。
  - `androidResources.localeFilters = en, zh-rTW`，函式庫的其他語言不會打包進來；`bundle.language.enableSplit = false`，否則 Play 只會下載系統語言的字串，App 內切換到另一個語言時會沒有資源可用。
  - 語言名稱「繁體中文」「English」一律用該語言書寫（`translatable="false"`）；「語言 / Language」刻意雙語，用哪種語言都找得到。
  - 洞察頁的星期標籤取 `LocalConfiguration` 的語系，而不是 `Locale.getDefault()`。
  - 英文有數量的句子用 `plurals`（「已觀察 N 晚」、TalkBack 的倒數秒數）；其餘改寫成不需要單複數的句子（`%d min`、`%d selected`）。
- **無障礙慣例**：標題加 `heading()`；裝飾圖形不進語意樹；相關文字合併成一個節點；觸控目標至少 48dp；不要用 `outline` 當文字顏色；大字級與橫向不能被裁切。
- **橫排的選項不能被擠壓**：一般的 `Row` 會把剩下的寬度全塞給最後一顆，大字級時「20 分鐘」會被擠成一個字一行的直條（v0.10.2 實機回報，Pixel 10 Pro 字型 1.5 倍、密度 500）。單選的幾個選項用 `EqualChipGrid`＋`PillOption`：標籤只排一行，放得下就等寬排一排，放不下改成每排一個。各自獨立的開關用 `ChipRow`＋`PillChip`（`FlowRow`，維持原本寬度、放不下換行）。實測：英文字型 1.3 倍仍是一排三個，中文 1.3 倍以上與英文 2 倍改成上下排列。

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
- 使用紀錄（`files/usage/`）需要使用者同意、保存 30 天，可在「設定 > 進階」關閉或清除；沒有權限變動，也不離開手機。
- 任何權限或資料處理的變動，都要同步更新 [PRIVACY.md](../PRIVACY.md)。

## 編譯與測試

```sh
./gradlew testDebugUnitTest      # 208 個 JVM 測試
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
| `BreathingTimelineTest`（12） | 四種呼吸法、展開連續性、倒數、結束、閉氣時停住 |
| `ForegroundPolicyTest`（5） | 系統介面、輸入法、自己的 overlay、點提醒後的延遲事件、沒有 USER_PRESENT 也能辨認解鎖 |
| `ScreenUnlockRegressionTest`（1） | 重現 Android 17 實機：鎖過一次後不再計時，以及視窗事件回報解鎖後恢復 |
| `SocialAppMatcherTest`（5） | 停用的官方 App 不勾選、依名稱找到修改版、既有清單修復只補使用者選過的品牌、排除非動態 App |
| `SaturationPlannerTest`（6）、`SaturationSessionTest`（10） | 量化、還原優先、當機補救、失敗降級 |
| `GuidanceCoordinatorTest`（7） | 虛擬時間下的零輪詢、每秒淡化、寫入節流、載入前事件排隊 |
| `ZenGrayscaleControllerTest`（7） | 第一次同步、遲滯、存取權、失敗不重試 |
| `ShizukuSaturationTest`（8） | 五種狀態、按需綁定、連線後還原、Shizuku 中途停止、降級與重試 |
| `BedtimeLearnerTest`（17） | 規律夜晚、冷啟動、鬧鐘與回訊息、真正醒來、影片睡著、夜貓與輪班、程序被殺、停止記錄、中午定案、跨時區、熬夜、加權、時段不追著學習值跑 |
| `BedtimeGuidanceTest`（11） | 時段邊界、累計門檻、門檻選項、每晚兩次上限、點提醒後效果全部清除、情境凍結、午夜不歸零、早上結束、未設定、tick 期限 |
| `UsageLogTest`（8）、`UsageInsightsTest`（7） | 編碼與壞行、區段記錄、程序被殺變成 `U`、放棄殘留區段；跨午夜拆分、沒有資料不是 0、只比較完整的兩週、前三名 App |
| `UsageRecordingTest`（7） | 寫入順序、新程序補 `U`、重啟不寫多餘的停止與開始、服務沒來時收尾、沒有同意不寫、清除後重新開始、壞行與保存期限 |
| `SettingsRepositoryTest`（7）、`GuidanceStateStoreTest`（3） | 真實 DataStore 檔案上的預設值、往返與持久化 |
| `BreatheViewModelTest`（10） | 聲音延遲建立、背景暫停、提示音開關、同一階段只響一次、新練習重新計算、釋放 |
| `StringResourcesParityTest`（8） | 中英文鍵值、陣列句數、佔位符、複數、無漏翻、無句尾句號、`locales_config` 與資源一致、語言代碼對應 |
| `PhaseCueGateTest`（5）、`GaplessLoopTest`（5）、`WavReaderTest`（5） | 每階段只響一次、只往前、整段練習重複回報仍只響一次、新練習重來；循環接力、音量延續、暫停與釋放；WAV 解析、拒絕不支援格式、內建提示音規格、峰值介於 -18 到 -1 dBFS 與音高 |
| `FadeSoundControllerTest`（17） | 淡化開始播放一次、關閉不播、中途關閉淡出、通話不播、交接延長淡出、首次拒絕不重試、釋放、音量曲線；短影音搶焦點後 1.2 秒重新要求、冷卻內多次只要求一次、對方回搶時冷卻加倍並在 5 次後放棄、重新要求被拒延後再試、來電不重試、冷卻中來電取消、離開或交接取消、焦點自己回來取消 |
| `EqualChipGridMathTest`（6） | 選項等寬格的欄數：放得下就一排，放不下改成每排一個（3 個選項不會變成 2＋1，4 個可排 2×2），每格都不窄於最寬的標籤 |
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
- **淡化中途閃一下（變回正常再變灰）**，幾乎都是無障礙服務被解除又重新綁定：`uiautomator`、重新安裝 App、系統重啟服務都會讓 overlay 立刻移除。debug 版會輸出診斷 log（`DebugLog`，release 版不輸出），可以直接看出原因：

  ```sh
  adb logcat --pid=$(adb shell pidof app.florsent) -s FlorsentDbg
  # effect level=… / window event … / dim attach|detach / service connected|shutdown
  ```

## 實機驗證紀錄

0.8.1～0.8.2 在 Pixel 10 Pro（Android 17，API 37，`CP4A.261105.040.A1`）上驗證：

| 項目 | 結果 |
|---|---|
| 安裝與圖示 | debug 版安裝成功；應用程式資訊頁顯示展葉之 F 圖示；系統顯示「未使用任何數據」 |
| Onboarding 與無障礙服務 | 揭露頁、同意、使用者自己在系統設定開啟服務，綁定正常 |
| 30 秒預覽 | 0 秒開始淡化，約 12 秒浮出提醒，30 秒兩個 overlay 都移除 |
| 呼吸蓮花 | 約 28 秒 1,682 影格，jank 0.12%，p50 6 ms、p90 10 ms、p99 16 ms |
| 頌缽＋提示音（v0.10.0） | 等長呼吸完整 72 秒：提示音 18 次、重複 0 次、間隔 4.0 秒；第 48 秒 `MEDIA_INFO_STARTED_AS_NEXT` 無縫接手；兩條播放軌同在大緩衝輸出（HAL frame 1920），欠載全程 0 |
| 背景雨聲（Android 17） | 預覽全程 `started`、`muted=none`、焦點 `GAIN_TRANSIENT_MAY_DUCK`，無 AudioHardening 紀錄；系統記錄 ducking 了正在播的影片 App；淡出 1.5 秒；點提醒後與呼吸環境音重疊約 3.2 秒 |
| 計時（v0.10.1 前） | 鎖過一次螢幕後完全不計時：`SCREEN_ON`／`SCREEN_OFF` 有送達，44 次 `USER_PRESENT` 一次都沒送到 Florsent；YouTube 實際用的是 `app.morphe.android.youtube`，官方 YouTube 已停用 |
| 閉氣時停住 | 盒式呼吸：吸氣後閉氣 5 張、吐氣後停頓 6 張連續截圖的花朵與進度環像素完全相同 |
| 環境音 | 練習中 `MediaPlayer` started；按 Home 變 paused、交還音訊焦點；回來恢復；離開練習後釋放 |
| Zen 灰階收尾 | 需要使用者授予零打擾存取權後再驗證 |
| Shizuku 第三層 | 實機未安裝 Shizuku，未驗證 |

實機上的注意事項：

- `uiautomator dump` 在實機上同樣會暫時解除 Florsent 的無障礙服務，首頁會短暫切成「服務未開啟」的版面，按鈕位置往下移約 170 px。用 dump 的座標去點，會點到下一顆按鈕。實機請用截圖量座標。
- 驗證時只截 Florsent 自己在前景的畫面；預覽會回到使用者的桌面，用 `dumpsys window` 觀察 overlay 就好，不要截圖。

## Google Play 上架

| 項目 | 檔案 |
|---|---|
| 高解析度圖示 512 × 512 | [`docs/assets/play-icon-512.png`](assets/play-icon-512.png) |
| Feature Graphic 1024 × 500 | [`docs/assets/play-feature-graphic.png`](assets/play-feature-graphic.png) |
| 三步驟展示圖 1600 × 900 | [`docs/assets/promo-showcase.png`](assets/promo-showcase.png) |
| GitHub 橫幅 1280 × 640 | [`docs/assets/github-hero.png`](assets/github-hero.png) |
| App 圖示（向量） | `app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml` |
| 隱私權政策 | [`PRIVACY.md`](../PRIVACY.md)（需發布成公開網址） |

商店介紹、資料安全表單、無障礙服務聲明、示範影片分鏡與素材清單都在 [PLAY_STORE.md](PLAY_STORE.md)。Shizuku 功能是使用者主動開啟的選用功能，提交審核時一併說明用途。

### 舊分支歸檔

遠端的 `liball`（2025-02～03，liball／@liballouo 的滾輪時間選擇與第一版圖示）與 `ncc`（2025-03，滑雪呼吸動畫實驗）沒有合併進 main。兩者都以 annotated tag `archive/liball`、`archive/ncc` 保存在本地，原分支保留不動。

## 不可違反的規則

給所有參與開發的人與 AI agent：

1. **不加網路權限、不加分析或廣告 SDK。** 新依賴必須檢查合併後的 manifest。
2. **不擴大無障礙服務的範圍。** 只用 `TYPE_WINDOW_STATE_CHANGED`，不讀取視窗內容，不執行手勢。
3. **不封鎖 App。** 所有效果都要能逐步出現、逐步消失，提醒永遠可以延後。
4. **不讓使用者卡在黑白。** 第二、三層的任何改動，都要維持「先記錄再變灰、任何離開都還原、重啟就補救、失敗就降級」，並補上測試。
5. **時間規則寫在 `domain/`，而且要有單元測試。** 不要在 Android 元件裡寫計時邏輯。
6. **使用紀錄只在同意後寫入、只存在手機、保存 30 天。** 不記錄留意的 App 以外的套件名稱；學到的時間不能自動改動使用者接受的時段。
7. **螢幕關閉或不在留意的 App 時零輪詢。**
8. **使用者看得到的文字放在 `strings.xml`，中英文同時加**，用詞遵守 [CONTRIBUTING.md](../CONTRIBUTING.md) 的約定，並維持 WCAG AA。
9. **改了行為就更新文件**：這份文件、README、PRIVACY.md。
