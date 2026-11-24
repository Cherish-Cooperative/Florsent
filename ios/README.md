# Florsent iOS

Native Swift / SwiftUI. Phase 1 (breathing, back to the present, settings) works fully; the Screen
Time extensions are skeletons for Phase 2.

## Setup

```sh
brew install xcodegen          # once
cd ios && xcodegen generate    # creates Florsent.xcodeproj (not committed)
open Florsent.xcodeproj
```

Team, bundle IDs and the App Group live only in `Config.xcconfig`. Entitlements and every Info.plist
derive from it.

## Layout

| Path | What |
|---|---|
| `FlorsentKit/` | Swift package. `FlorsentCore` (Foundation only: breathing timeline, cue gate, sound levels, App Group state) and `FlorsentKit` (palette, WCAG audit, `BreathingFlower` Canvas) |
| `Florsent/` | The app: home, breathing picker and session, back to the present, settings, `AVAudioEngine` soundscape, Core Haptics, App Intents |
| `FlorsentMonitor/` | `DeviceActivityMonitor` (links only FlorsentCore to stay under the ~6 MB limit) |
| `FlorsentShieldConfig/` | Sage, soft shield ("Pause for a deep breath?") |
| `FlorsentShieldAction/` | Shield buttons: remembers a breathing request, posts a notification with the rain sound |
| `FlorsentReport/` | `DeviceActivityReport` extension (today's minutes) |
| `FlorsentTests/` | App tests: localization parity with Android, audio frame counts, haptics, session clock |
| `tools/` | `convert_audio.sh`, `render_icons.swift`, `gen_strings.py` (regenerate assets from the Android sources) |

## Shared with Android

`shared/test-fixtures/breathing_golden.json` is generated from the Android code by
`shared/tools/export_golden.sh` (it compiles `Breathing.kt` read-only and reads `strings.xml`).
`FlorsentCoreTests` checks all 1,372 timeline samples, the easing curve, the sound levels and the
copy against it. If Android's breathing or copy changes, rerun the exporter, then
`python3 ios/tools/gen_strings.py`, then the tests.

## Build and test

```sh
cd ios
xcodebuild -project Florsent.xcodeproj -scheme Florsent \
  -destination 'platform=iOS Simulator,name=Florsent iPhone 17 Pro' \
  -derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO test
(cd FlorsentKit && swift test)   # the package alone, on macOS
```

## Screenshots (debug builds)

Launch arguments open any screen in a fixed state:

```sh
xcrun simctl launch <udid> app.florsent -AppleLanguages '(zh-Hant)' \
  -FlorsentScreen session -FlorsentPattern relax_478 -FlorsentFreezeMs 9200
```

`-FlorsentScreen home|breathe|session|grounding|settings`, `-FlorsentFreezeMs <ms>` (session clock
stands still), `-FlorsentGroundingIndex <n>`, `-FlorsentNight YES|NO`.
