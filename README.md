<p align="center">
  <img src="docs/assets/github-hero.png" alt="Florsent：不封鎖任何 App，只在你用太久時，溫柔地提醒。" width="100%">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android">
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose, Material 3">
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-56705A" alt="Min SDK 26">
  <img src="https://img.shields.io/badge/APK-4.6%20MB-56705A" alt="Release APK 4.6 MB">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-56705A" alt="License: Apache 2.0"></a>
</p>

<p align="center">
  一個幫你慢慢放下手機的 Android App。<br>
  不封鎖、不說教，只在你用太久時，讓畫面溫柔地變淡。
</p>

---

## 理念

封鎖會讓人想繞過去，跳出來的警告也容易被反射性地關掉。Florsent 換了一個方向：當你在社群 App 上用超過自己設定的額度，畫面會一點一點變淡，顏色和對比慢慢退去，滑動的吸引力自然下降。

要不要停下來，始終由你決定。提醒可以按「✕」延後，App 也從來不會被擋住。

## 三個時刻

<p align="center">
  <img src="docs/assets/promo-showcase.png" alt="Florsent 的三個時刻：溫柔漸進提醒、沉浸呼吸練習、回到生活當下" width="100%">
</p>

1. **溫柔漸進提醒**：超過額度後，畫面在 3 分鐘內慢慢變淡；淡到一半時，畫面下方浮出「停下來深呼吸一下？」。
2. **沉浸呼吸練習**：點提醒直接開始。畫面上的蓮花跟著節奏開合，有四種呼吸法可以選，每種大約一分多鐘。
3. **回到生活當下**：練習結束後出現一句簡短的感官提示，例如「感受雙腳踩在地上的重量」。按「回到生活」後畫面恢復，接下來的安靜時間內不再提醒。

## 特色

- **只看你選的 App**：只計算你勾選的社群 App 在前景、而且螢幕開著的時間。首次使用會自動勾選手機上常見的社群 App。
- **兩種額度**：單次額度（預設 15 分鐘）和每日額度（預設 60 分鐘）。每日額度用完後，每次打開還有 2 分鐘緩衝，不會一打開就被擋住。
- **四種呼吸法**：4-4-4 等長呼吸、4-4-4-4 盒式呼吸、4-7-8 放鬆呼吸（上限 4 輪）、5.5-5.5 共振呼吸。
- **安靜的細節**：換階段時輕微震動；環境音（手碟）和提示音（柔和鈴聲）可以分別開關，預設都關閉。
- **30 秒體驗**：首頁可以直接預覽整個漸進引導，不必真的等到超過額度。
- **無障礙**：完整的 TalkBack 語意、48dp 觸控目標、深淺色模式下的文字都符合 WCAG AA，關閉系統動畫時會自動降級，橫向和大字級也放得下。
- **小**：Release APK 4.6 MB，沒有網路權限，沒有第三方分析或廣告 SDK。

## 漸進式灰階：三層設計

Android 沒有開放一般 App 調整整個螢幕的飽和度，所以 Florsent 把視覺效果拆成三層，透過同一個 `VisualEffectController` 介面串接：

| 層 | 做法 | 需要 | 狀態 |
|---|---|---|---|
| **基本層** | 無障礙服務的 overlay 視窗，以中性灰（#858585）半透明疊加，透明度最高 0.6。混入中間灰會同時降低對比和飽和度，畫面是變淡而不是變色 | 開啟無障礙服務 | 已完成 |
| **灰階收尾** | 淡到底時啟用 Florsent 自己的系統「模式」（`AutomaticZenRule`），唯一的效果是灰階（`ZenDeviceEffects`）。中斷過濾設為 ALL，不影響通知和鈴聲 | Android 15 以上，並授予「零打擾」存取權 | 已完成，選用 |
| **逐 App 去飽和** | 透過 Shizuku 呼叫 `cmd color_display set-layer-saturation`，只讓正在用的 App 從彩色慢慢變灰 | 自行安裝 Shizuku | 規劃中 |

設計重點：

- 無障礙 overlay 是受信任的視窗，透明度再高也不會擋住觸控。一般懸浮窗超過 0.8 就會被系統擋下。
- 灰階收尾的每個系統呼叫都有保護。沒有權限、權限被收回或發生任何錯誤時，都會退回基本層。服務啟動時會先同步一次，就算 App 曾經當掉，畫面也不會停在黑白。
- 關閉灰階開關時，會把模式從系統的「模式」清單刪除。

## 效能與省電

- **零輪詢**：螢幕關閉或前景不是留意的 App 時，沒有任何計時器在跑。在留意的 App 裡，只有畫面正在變淡時才每秒更新，其他時候直接睡到下一個時間點，最多 15 秒醒一次。
- **流暢的淡化**：overlay 每秒接一段一秒長的線性動畫，以螢幕刷新率連續變化。只改 view 的透明度，並關閉 overlapping rendering，每一幀只需要合成。
- **呼吸動畫零 recomposition**：蓮花用 Compose Canvas 繪製。路徑、徑向漸層和筆觸在 `drawWithCache` 裡建好，時間來自 `withFrameNanos` 的單調時鐘，只在 draw 階段讀取，所以每一幀只重畫、不重組，也不配置新物件。
- **少寫入**：引擎狀態只在需要保存的欄位改變時才寫入，最多 30 秒一次；切換模式、跨日、關螢幕時立刻寫入。
- **時間正確**：時鐘被調回去不會拉長安靜時間；日期只往前跑；時區每次重新讀取。旋轉螢幕時，呼吸練習接著進行。

## 隱私承諾

- 無障礙服務**只**接收 `TYPE_WINDOW_STATE_CHANGED`，用來知道前景是哪個 App。`canRetrieveWindowContent=false`，所以讀不到畫面上的任何文字或內容。
- 沒有網路權限，所有資料只存在手機上，也不會被雲端備份或轉移到其他裝置。
- 首次使用時，會先清楚說明 Florsent 用無障礙服務做什麼、不做什麼，使用者同意後才會帶到系統設定。

完整內容請見 [隱私權政策 PRIVACY.md](PRIVACY.md)。

## 架構

```
app.florsent
├── domain/    純 Kotlin 狀態機與呼吸時間軸，全部有單元測試
├── data/      DataStore（設定、引擎狀態）、已安裝 App 清單
├── service/   無障礙服務、GuidanceCoordinator、浮出提醒
│   ├── effect/  VisualEffectController、中性灰 overlay、灰階收尾
│   └── audio/   環境音與提示音
└── ui/        Compose 畫面：onboarding、home、breathe、grounding、apps、settings
```

資料流是單向的：無障礙服務和 UI 把事件送進 `GuidanceCoordinator.dispatch()`，由純函式的 `GuidanceEngine.reduce()` 算出新狀態，再由服務把效果畫到畫面上。所有時間相關的規則都在 domain 層，可以不靠模擬器直接測試。

## 本機編譯與測試

需要 JDK 17 以上（Android Studio 內建的 JDK 即可）和 Android SDK（compileSdk 37.1）。

```sh
git clone https://github.com/Cherish-Cooperative/Florsent.git
cd Florsent
./gradlew testDebugUnitTest   # 41 個 domain 單元測試
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # R8 與資源縮減
```

| 項目 | 版本 |
|---|---|
| AGP／Gradle | 9.5.2／9.9.2 |
| Kotlin | 2.4.21（Compose compiler plugin） |
| Compose BOM | 2026.11.01 |
| SDK | minSdk 26、targetSdk 36、compileSdk 37.1 |

### 在模擬器上試用

```sh
# 開啟無障礙服務（等同使用者在設定裡打開）
adb shell settings put secure enabled_accessibility_services app.florsent/app.florsent.service.FlorsentAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 授予「零打擾」存取權，測試灰階收尾（Android 15 以上）
adb shell cmd notification allow_dnd app.florsent
```

- 首頁的「30 秒體驗漸進引導」會在 20 秒內跑完整個淡化，第 12 秒浮出提醒。
- 模擬器無法顯示色彩轉換，灰階收尾的畫面效果要用實機確認。可以用 `adb shell dumpsys notification` 查看「Florsent 漸進灰階」模式的狀態。
- `uiautomator dump` 執行時會暫停其他無障礙服務。之後立刻檢查畫面，會誤以為服務沒開，請等幾秒，或改用截圖確認。
- `am force-stop` 和 `pm clear` 會讓系統移除已啟用的無障礙服務，之後需要重新開啟。

## Google Play 上架素材

| 用途 | 檔案 | 尺寸 |
|---|---|---|
| Play 商店主視覺（Feature Graphic） | [`docs/assets/play-feature-graphic.png`](docs/assets/play-feature-graphic.png) | 1024 × 500 |
| 三步驟體驗展示圖 | [`docs/assets/promo-showcase.png`](docs/assets/promo-showcase.png) | 1600 × 900 |
| GitHub 首頁橫幅 | [`docs/assets/github-hero.png`](docs/assets/github-hero.png) | 1280 × 640 |
| App 圖示 | `app/src/main/res/drawable/ic_launcher_*.xml`（向量 adaptive icon，含 monochrome 圖層） | 向量 |
| 隱私權政策 | [`PRIVACY.md`](PRIVACY.md) | |

上架前還要準備：

- Play Console 的**無障礙 API 聲明**，附上示範影片。Florsent 不是輔助工具，所以 `isAccessibilityTool=false`。
- 512 × 512 的高解析度圖示：可在 Android Studio 的 Image Asset 工具中，以現有的向量圖示匯出。
- 手機截圖。
- 將 `PRIVACY.md` 發布成公開網址，填入 Play Console。

applicationId 是 `app.florsent`，正式上架後不能再改。

## 參與貢獻

歡迎回報問題或提出想法。開始之前，請先讀 [CONTRIBUTING.md](CONTRIBUTING.md)，裡面說明了開發流程、程式風格，以及 Florsent 在產品上的幾個堅持。

## 授權

Florsent 以 [Apache License 2.0](LICENSE) 授權釋出。
