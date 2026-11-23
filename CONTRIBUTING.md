# 參與貢獻 Florsent

謝謝你願意花時間讓 Florsent 變得更好。不論是回報問題、提出想法、修正錯字，還是送出程式碼，我們都很歡迎。

## 動手之前：Florsent 的幾個堅持

這幾點決定了什麼樣的改動會被接受，送 PR 前請先讀過。

1. **漸進，不封鎖。** Florsent 不擋住任何 App，也不強迫使用者停下來。提醒永遠可以延後，所有效果都要能逐步出現、逐步消失。
2. **隱私優先。** App 沒有網路權限，資料不離開手機，也不會被備份到雲端。任何需要新權限、網路或第三方 SDK 的改動，請先開 issue 討論。
3. **無障礙服務只做最少的事。** 只接收 `TYPE_WINDOW_STATE_CHANGED`，不讀取視窗內容。擴大服務範圍會影響 Google Play 審核，也會影響使用者的信任。
4. **小而安靜。** 每一個新功能、新文字、新按鈕都要有存在的理由。文案以台灣繁體中文書寫，精簡、溫和，不說教。
5. **省電。** 螢幕關閉或不在留意的 App 時，不能有任何輪詢或喚醒。

## 開發環境

- Android Studio（內建 JDK 即可，需要 JDK 17 以上）
- Android SDK：compileSdk 37.1、targetSdk 36、minSdk 26

```sh
./gradlew testDebugUnitTest   # 單元測試
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # 確認 R8 與資源縮減沒有問題
```

在模擬器上開啟無障礙服務、授予權限的指令，請見[技術文件的「模擬器測試與踩坑」](docs/TECHNICAL.md#模擬器測試與踩坑)。架構、測試架構與不可違反的規則，也都整理在 [docs/TECHNICAL.md](docs/TECHNICAL.md)。

## 專案結構與分層

- `domain/`：純 Kotlin，不依賴 Android。額度、淡化、提醒、安靜時間等所有時間規則都在 `GuidanceEngine` 裡，以 `reduce(state, event, config)` 的純函式實作。
- `data/`：DataStore 與已安裝 App 清單。
- `service/`：無障礙服務、`GuidanceCoordinator`、overlay、聲音。
- `ui/`：Jetpack Compose 畫面。

新的時間規則請寫在 `domain/`，並補上單元測試。測試用 `GuidanceEngineTest` 裡的 `Harness` 模擬時間流逝，不需要模擬器。

## 程式風格

- Kotlin 遵循官方風格（`kotlin.code.style=official`）。
- 使用者看得到的文字一律放在 `res/values/strings.xml`。用詞請保持一致：
  - 提醒：浮出的那句「停下來深呼吸一下？」
  - 變淡當動詞用，淡化當名詞用
  - 安靜時間：完成呼吸後不再提醒的期間
  - 留意的 App：使用者選擇計時的 App
  - 閉氣：吸氣後憋住的階段；吐氣後的憋氣叫「停頓」
- 文案能短就短。標題已經說清楚的，不要再加副標題；說明、狀態與提示文字句尾不加句號，問句保留問號。
- Compose 動畫的數值請在 draw 階段讀取（`drawWithCache`、`graphicsLayer` 的 lambda），避免每一幀觸發 recomposition。
- 新的畫面元素請確認：
  - TalkBack 語意：標題加上 `heading()`，裝飾性圖形從語意中隱藏，相關文字合併成一個節點。
  - 觸控目標至少 48dp。
  - 文字顏色在深淺色模式下都至少 4.5:1。不要把 `outline` 當成文字顏色。
  - 關閉系統動畫時能正常使用。
  - 大字級（1.6 倍以上）和橫向不會被裁切。

## Commit 與 Pull Request

1. 從 `main` 開一個新分支，例如 `fix/nudge-position`、`feat/weekly-summary`。
2. Commit 訊息用中文簡述做了什麼，開頭可以加一個 emoji（沿用專案慣例）。
3. 送 PR 前請確認：
   - `./gradlew testDebugUnitTest assembleDebug assembleRelease` 全部通過。
   - 改到畫面的話，附上前後截圖，最好包含深色模式。
   - 改到權限、資料處理或無障礙服務的話，同步更新 [PRIVACY.md](PRIVACY.md)。
4. PR 描述請說明「為什麼要改」，而不只是「改了什麼」。

## 回報問題與提出想法

請使用 [Issues](https://github.com/Cherish-Cooperative/Florsent/issues) 頁面的範本：

- **錯誤回報**：請附上手機型號、Android 版本、Florsent 版本與重現步驟。
- **功能建議**：請描述你遇到的情境，以及這個想法怎麼符合「漸進、不封鎖」的方向。

回報問題時，請不要貼上任何個人資料或其他 App 的畫面內容。

## 授權

送出貢獻即表示你同意你的貢獻以本專案的 [Apache License 2.0](LICENSE) 授權釋出。
