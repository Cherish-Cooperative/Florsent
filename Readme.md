# Florsent

幫助使用者減少社群媒體使用時間的 Android App。Florsent 不封鎖 App：在選定的社群 App 用超過額度後，畫面會慢慢變淡，再從畫面下緣浮出「停下來深呼吸一下？」，由使用者自己點進呼吸練習，最後簡短引導回到當下。

## 建置

需要 JDK 17 以上（Gradle 9.9 可以直接用 Android Studio 內建的 JDK 25）和 Android SDK（compileSdk 37.1）。

```sh
./gradlew testDebugUnitTest   # domain 狀態機單元測試
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # R8 + 資源縮減
```

| 項目 | 版本 |
|---|---|
| AGP / Gradle | 9.5.2 / 9.9.2（AGP 內建 Kotlin） |
| Kotlin | 2.4.21（Compose compiler plugin） |
| Compose BOM | 2026.11.01 |
| SDK | minSdk 26、targetSdk 36、compileSdk 37.1 |

## 架構

```
app.florsent
├── domain/    純 Kotlin，不依賴 Android
│   ├── GuidanceEngine     額度狀態機：reduce(state, event, config)、effect()、status()
│   ├── GuidanceState      狀態、事件、輸出效果
│   ├── GuidanceConfig     額度、淡化時間、提示時機、冷卻等參數
│   ├── ForegroundPolicy   把無障礙事件轉成「目前在前景的 App」
│   └── Breathing          四種呼吸法與時間軸（展開程度、階段、倒數）
├── data/      DataStore（使用者設定、引擎狀態）、已安裝 App 清單
├── service/   無障礙服務、GuidanceCoordinator（全域狀態與每秒 tick）、浮出提示
│   ├── effect/  VisualEffectController 擴充點與中性灰 overlay
│   └── audio/   呼吸練習的環境音與提示音
└── ui/        Compose 畫面（home、breathe、grounding、apps、settings）與主題
```

資料流是單向的：無障礙服務和 UI 把事件送進 `GuidanceCoordinator.dispatch()`，由 `GuidanceEngine` 算出新狀態，再由服務把 `effect` 畫到畫面上。

### 漸進引導的規則

- 只計算使用者勾選的 App 在前景、而且螢幕開著的時間。
- 單次額度或每日額度任一個超過後開始淡化，用 smoothstep 曲線在 3 分鐘內從無到最強。淡化到一半時浮出提示。
- 點提示進入呼吸練習，完成後進入冷卻時間；按「×」則延後 2 分鐘再提示。
- 離開監測的 App 超過 10 分鐘，單次額度重新計算；每日額度在當地午夜歸零。

### 呼吸動畫

`BreathingFlower` 用 Compose Canvas 畫兩層花瓣、光暈和進度環。路徑、漸層和筆觸在 `drawWithCache` 裡建好；動畫時間用 `withFrameNanos` 對齊單調時鐘，只在 draw lambda 裡讀取，所以每一幀只重畫、不會 recomposition。階段文字透過 `derivedStateOf` 每秒最多更新一次。換階段時有輕微震動，可選提示音。系統關閉動畫時花會保持靜止，文字和進度環照常更新。練習中螢幕保持常亮，App 離開前景時計時會暫停。

內建呼吸法：4-4-4 等長呼吸、4-4-4-4 盒式呼吸、4-7-8 放鬆呼吸（4 輪）、5.5-5.5 共振呼吸，每種約 1 分 15 秒。

### 視覺效果擴充點

`VisualEffectController` 目前只有 `DimOverlayController`（無障礙 overlay，中性灰，透明度最高 0.6）。之後預計加上：

- Android 15 以上：用 `ZenDeviceEffects.setShouldDisplayGrayscale` 在淡化到底時切成真正的灰階。
- P4：透過 Shizuku 呼叫 `cmd color_display set-layer-saturation`，逐 App 去飽和。

## 上架注意事項

無障礙服務只監聽 `typeWindowStateChanged`，不讀取視窗內容（`canRetrieveWindowContent=false`），`isAccessibilityTool=false`。開啟前 App 內會先顯示揭露說明，上架時還要在 Play Console 填寫無障礙 API 聲明並附上影片。
