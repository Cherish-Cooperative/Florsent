# Florsent

Florsent 是一個幫助人減少社群媒體使用時間的 Android App。

它不封鎖任何 App。在你選定的社群 App 用超過額度時，畫面會一點一點變淡，畫面下方再浮出一句「停下來深呼吸一下？」。要不要停下來由你決定；點了就進入一分鐘左右的呼吸練習，最後用一句簡短的感官提示，帶你回到眼前的生活。

## 為什麼是「漸進」而不是封鎖

封鎖會讓人想繞過去，跳出來的警告則容易被反射性地關掉。Florsent 選擇讓社群 App 慢慢變得沒那麼好看：顏色變淡、對比變低，滑動的吸引力自然下降。提醒只在畫面已經淡了一半時出現，而且永遠可以按「×」延後。

## 核心流程

1. **計時**：只計算你勾選的 App 在前景、而且螢幕開著的時間。
2. **漸進淡化**：超過額度後，中性灰的半透明層用 smoothstep 曲線在 3 分鐘內慢慢加深。前幾秒幾乎察覺不到，最後畫面明顯灰白。
3. **浮出提醒**：淡化到一半時，畫面下緣浮出「停下來深呼吸一下？」。按「×」會延後 2 分鐘。
4. **呼吸練習**：點提醒直接開始呼吸，畫面上的花跟著節奏開合。
5. **回到當下**：練習結束後顯示一句感官覺察提示，按「回到生活」後解除淡化，進入休息時間，也可以設定直接回到手機桌面。

### 額度規則

| 規則 | 預設 |
|---|---|
| 單次額度：連續使用多久後開始引導 | 15 分鐘 |
| 每日額度：一天累計的上限 | 60 分鐘 |
| 每日額度用完後，每次使用的緩衝期 | 2 分鐘 |
| 休息時間：完成呼吸後不再打擾 | 20 分鐘 |
| 離開多久算新的一次使用 | 10 分鐘 |

每日額度用完之後，打開社群 App 不會立刻開始淡化，而是先有 2 分鐘緩衝，比單次額度早，但不會讓人一打開就覺得被擋住。如果是在一段長時間使用的途中用完每日額度，淡化會直接開始。每日額度在當地午夜歸零。

## 漸進式灰階的設計

Android 沒有開放一般 App 調整整個螢幕飽和度的 API，所以 Florsent 把視覺效果分成三層，全部透過 `VisualEffectController` 介面串接：

| 層 | 做法 | 條件 | 狀態 |
|---|---|---|---|
| 基本層 | 無障礙服務的 overlay 視窗，用中性灰（#858585）半透明疊加，透明度最高 0.6。混入中間灰會同時降低對比和飽和度，所以畫面是變淡而不是變色 | 開啟無障礙服務 | 已完成 |
| 灰階收尾 | 淡化到底時，啟用 Florsent 自己的系統「模式」（`AutomaticZenRule`），唯一的裝置效果是灰階（`ZenDeviceEffects.setShouldDisplayGrayscale`）。中斷過濾設為 ALL，不會影響通知和鈴聲 | Android 15 以上，使用者在設定中開啟並授予「零打擾」存取權 | 已完成，預設關閉 |
| 逐 App 去飽和 | 透過 Shizuku 呼叫 `cmd color_display set-layer-saturation`，只讓正在用的社群 App 從彩色慢慢變灰 | 使用者自行安裝 Shizuku | 規劃中 |

設計上的幾個重點：

- **觸控不受影響**：無障礙 overlay（`TYPE_ACCESSIBILITY_OVERLAY`）是受信任的視窗，透明度再高也不會擋住觸控。一般懸浮窗超過 0.8 就會被系統擋下。
- **平滑**：淡化進行中，引擎每秒更新一次強度，overlay 用長度一秒的線性動畫銜接，等於以螢幕刷新率連續變化。overlay 只改 view 的透明度，並宣告 `hasOverlappingRendering() = false`，每一幀只需要合成，不經過全螢幕離屏圖層。
- **安全降級**：灰階收尾的每個系統呼叫都有保護。沒有權限、權限被收回、系統版本不支援或發生任何錯誤時，就只剩基本層的淡化，不會當掉。關閉開關時會把模式從使用者的「模式」清單中刪除，不留痕跡。
- **開關有緩衝**：灰階在強度達 0.98 時開啟、低於 0.9 時關閉，避免在邊界來回閃爍。

## 呼吸練習

內建四種呼吸法，每種約 1 分 15 秒：

| 呼吸法 | 節奏（秒） | 輪數 |
|---|---|---|
| 等長呼吸 | 吸 4・屏息 4・吐 4 | 6 |
| 盒式呼吸 | 吸 4・屏息 4・吐 4・停頓 4 | 5 |
| 4-7-8 放鬆呼吸 | 吸 4・屏息 7・吐 8 | 4（上限） |
| 共振呼吸 | 吸 5.5・吐 5.5 | 7 |

呼吸動畫 `BreathingFlower` 用 Compose Canvas 繪製兩層花瓣、光暈和進度環：

- 花瓣路徑、徑向漸層和筆觸在 `drawWithCache` 裡建好，畫每一幀時只做旋轉和縮放，不配置新物件，也不使用 blur。
- 時間來自 `withFrameNanos` 的單調時鐘，只在 draw lambda 裡讀取，所以動畫每一幀只會重畫，不會觸發 recomposition。階段文字和倒數透過 `derivedStateOf` 每秒最多更新一次。
- App 離開前景時計時暫停，回來後接著練習。練習中螢幕保持常亮。
- 換階段時有輕微震動。環境音（手碟）和提示音（合成的柔和鈴聲）可以分別開關，預設都關閉，避免在公共場合突然出聲。
- 系統關閉動畫時，花不再連續移動，改成吸氣、屏息時張開，吐氣、停頓時收合，文字和進度環照常更新。
- 旋轉螢幕或切換深淺色時，練習接著進行，不會從頭開始。花的大小依可用高度調整，橫向和大字級也放得下。
- TalkBack 會在換階段時唸出「吸氣」「吐氣」，倒數和輪數合成一句，不會每秒打擾。

## 隱私與權限

| 權限 | 用途 |
|---|---|
| 無障礙服務 | 只監聽 `TYPE_WINDOW_STATE_CHANGED`，知道前景是哪個 App；`canRetrieveWindowContent=false`，不讀取畫面內容。也用來顯示 overlay 和提醒 |
| `ACCESS_NOTIFICATION_POLICY`（選用） | Android 15 以上的灰階收尾，需要使用者另外授予「零打擾」存取權 |
| `<queries>` MAIN/LAUNCHER | 列出可以選擇的 App，不需要 `QUERY_ALL_PACKAGES` |

所有資料只存在手機的 DataStore 裡，Florsent 沒有網路權限。

首次使用時，Onboarding 的最後一頁會完整說明 Florsent 用無障礙服務做什麼、不做什麼，使用者同意後才帶到系統設定。這頁就是 Google Play 要求的 prominent disclosure。

## 架構

```
app.florsent
├── domain/    純 Kotlin，不依賴 Android，全部有單元測試
│   ├── GuidanceEngine     額度狀態機：reduce(state, event, config)、effect()、status()
│   ├── GuidanceState      狀態、事件、輸出效果
│   ├── GuidanceConfig     額度、緩衝、淡化、提醒、休息等參數
│   ├── ForegroundPolicy   把無障礙事件轉成「目前在前景的 App」
│   └── Breathing          四種呼吸法與時間軸（展開程度、階段、倒數）
├── data/      DataStore（使用者設定、引擎狀態）、已安裝 App 清單
├── service/   無障礙服務、GuidanceCoordinator（全域狀態與每秒 tick）、浮出提醒
│   ├── effect/  VisualEffectController、中性灰 overlay、灰階收尾
│   └── audio/   呼吸練習的環境音與提示音
└── ui/        Compose 畫面與主題
    ├── onboarding/  首次使用三頁
    ├── home/        首頁
    ├── breathe/     呼吸法選擇、練習、BreathingFlower
    ├── grounding/   回到當下
    ├── apps/        監測的 App
    └── settings/    額度與設定
```

資料流是單向的：無障礙服務和 UI 把事件送進 `GuidanceCoordinator.dispatch()`，由 `GuidanceEngine` 算出新狀態，無障礙服務再把 `effect` 交給各個 `VisualEffectController` 畫到畫面上。

### 省電與時間

- **不輪詢**：螢幕關閉或前景不是留意的 App 時，完全沒有計時器在跑。在留意的 App 裡，只有畫面正在變淡時才每秒更新，其他時候直接睡到下一個時間點（額度用完、延後提醒結束、安靜時間結束），最多 15 秒醒一次。每次醒來都加上實際經過的時間，所以計時一樣準。
- **少寫入**：只有需要保存的欄位改變時才存檔，平常最多 30 秒寫一次；切換模式、跨日、離開 App 或關螢幕這些時刻會立刻存。
- **時鐘保護**：使用者把時鐘調回去，也不會拉長安靜時間或延後提醒。日期只往前跑，往西飛越換日線不會多拿一份每日額度。時區每次重新讀取，出國後午夜邊界立刻正確。
- **灰階不會卡住**：灰階模式的狀態比 App 的程序活得久，所以服務一啟動就會先同步一次，就算 App 曾經當掉或被系統回收，也不會讓畫面一直停在黑白。

視覺上使用鼠尾草綠、晨霧灰、暖石色三組低飽和色，支援深淺色模式和 edge-to-edge，不使用 Material You 動態配色。所有內文文字在深淺色模式下都符合 WCAG AA（4.5:1）。App 圖示是向量的 adaptive icon，鼠尾草綠底上兩層半透明的米白花瓣，和呼吸花同一套造型，並附 Android 13 以上主題圖示用的 monochrome 圖層。

## 編譯與測試

需要 JDK 17 以上和 Android SDK（compileSdk 37.1）。Gradle 9.9 可以直接使用 Android Studio 內建的 JDK。

```sh
./gradlew testDebugUnitTest   # domain 單元測試
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # 開啟 R8 與資源縮減
```

| 項目 | 版本 |
|---|---|
| AGP / Gradle | 9.5.2 / 9.9.2（AGP 內建 Kotlin 支援） |
| Kotlin | 2.4.21（Compose compiler plugin） |
| Compose BOM | 2026.11.01 |
| SDK | minSdk 26、targetSdk 36、compileSdk 37.1 |

compileSdk 37.1 是新版 AndroidX 的要求，只決定編譯時能用哪些 API；執行時的行為由 targetSdk 36 決定。

### 在模擬器上手動測試

```sh
# 開啟無障礙服務（等同使用者在設定裡打開）
adb shell settings put secure enabled_accessibility_services app.florsent/app.florsent.service.FlorsentAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 授予「零打擾」存取權，測試灰階收尾
adb shell cmd notification allow_dnd app.florsent
```

注意事項：

- 模擬器不支援顯示色彩轉換，灰階收尾的畫面效果要用實機確認。可以用 `adb shell dumpsys notification` 查看「Florsent 漸進灰階」模式的 `STATE_TRUE` / `STATE_FALSE`。
- `adb shell am force-stop app.florsent` 和 `pm clear` 會讓系統把無障礙服務從已啟用清單中移除，之後需要重新開啟。
- `uiautomator dump` 執行時會暫停其他無障礙服務，所以在它之後立刻檢查畫面，會誤以為服務沒開。請等幾秒，或改用截圖確認。
- 首頁的「30 秒體驗漸進引導」會在 20 秒內跑完整個淡化過程，第 12 秒浮出提醒，適合快速確認效果。

## 上架注意事項

- Florsent 不是無障礙輔助工具，所以 `isAccessibilityTool=false`。上架時需要在 Play Console 填寫無障礙 API 聲明，並附上示範影片。
- applicationId 是 `app.florsent`，正式上架後不能再改。
