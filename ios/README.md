# Florsent iOS

Native Swift / SwiftUI, iPhone and iPad. Phase 1: breathing, back to the present, settings.
Phase 2: gentle guidance with Screen Time.

## Gentle guidance (Phase 2)

1. Settings › Turn on gentle guidance asks for Family Controls `.individual` authorization, then
   `FamilyActivityPicker` picks the watched apps (stored as opaque tokens in `group.app.florsent`).
2. `FlorsentMonitor` watches a daily budget (00:00–23:59) and an optional bedtime window
   (bedtime → 05:00). Stage one at the budget: a notification with the 12 s distant-rain sound that
   opens breathing. Stage two five minutes later: the soft sage shield (bedtime wording at night).
3. Shield buttons (`FlorsentShieldAction`): "Go breathe" closes the app and posts a notification
   into breathing; "5 more minutes" lifts the shield and re-arms a 5-minute threshold.
4. Finishing a breath starts quiet time (default 20 min): the shield lifts, thresholds are held,
   and a follow-up watches again once quiet time ends if still over budget.
5. `FlorsentReport` draws calm insights (today, last 7 days, week over week, top apps). The
   "Over budget right now?" App Intent reads the live state for a grayscale Shortcuts automation.

All decisions live in `FlorsentCore/Guidance.swift` (plain values, tested on macOS); the Screen
Time calls are thin adapters in `ScreenTimeShared/`, compiled into the app, the monitor and the
shield action.

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
| `ScreenTimeShared/` | Adapters: watched apps, shield store, DeviceActivity scheduling, notifications (`ScreenTime` strings) |
| `FlorsentMonitor/` | `DeviceActivityMonitor`: one state transition per callback, no SwiftUI (~6 MB limit) |
| `FlorsentShieldConfig/` | Static sage shield ("Time for a break" / "Time to wind down?") |
| `FlorsentShieldAction/` | "Go breathe" and "5 more minutes" |
| `FlorsentReport/` | `DeviceActivityReport` extension: calm insights |
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

`-FlorsentScreen home|breathe|session|grounding|settings|bedtime|insights`, `-FlorsentFreezeMs <ms>` (session clock
stands still), `-FlorsentGroundingIndex <n>`, `-FlorsentNight YES|NO`.

On a device, pass launch arguments after `--`:

```sh
xcrun devicectl device process launch --device <udid> --terminate-existing app.florsent -- -FlorsentScreen settings
```
