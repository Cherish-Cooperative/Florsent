"""Generates the iOS String Catalogs (.xcstrings) for the app and its extensions.

Copy that exists on Android is taken verbatim from shared/test-fixtures/breathing_golden.json (itself
exported from strings.xml), with Android format specifiers converted (%1$d → %1$lld, %s → %@). Those
entries carry the comment "Android: <key>", and LocalizationTests checks them against the golden file.
iOS-only copy is written below in both languages.

Usage: python3 ios/tools/gen_strings.py   (from the repo root)
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
golden = json.loads((ROOT / "shared/test-fixtures/breathing_golden.json").read_text())
LANGS = ["en", "zh-Hant"]


def ios_format(s: str) -> str:
    s = re.sub(r"%(\d+\$)?d", lambda m: f"%{m.group(1) or ''}lld", s)
    return re.sub(r"%(\d+\$)?s", lambda m: f"%{m.group(1) or ''}@", s)


def android(key: str) -> dict:
    return {"android": key, **{lang: ios_format(golden["copy"][lang]["strings"][key]) for lang in LANGS}}


def own(en: str, zh: str, comment: str = "") -> dict:
    return {"en": en, "zh-Hant": zh, "comment": comment}


APP = {
    # Home
    "home.breatheNow": android("home_breathe_now"),
    "home.subtitle": android("onboarding_step_breathe"),
    "home.grounding": android("grounding_label"),
    "home.guidance.title": own("Gentle guidance", "溫柔引導"),
    "home.guidance.body": own(
        "Pick the apps you'd like to use less. Past your budget, Florsent reminds you with distant rain, then gently asks for a break",
        "選好想少滑一點的 App，超過額度時 Florsent 會先用遠方的雨聲提醒，再溫柔地請你休息",
    ),
    "home.guidance.setupAction": own("Set up gentle guidance", "設定溫柔引導"),
    "home.guidance.daily": own("Daily budget %@", "每日額度 %@"),
    "home.guidance.bedtime": own("bedtime %@", "睡前 %@"),
    "home.guidance.over": own("Over today's budget. A breath might help", "今天已經超過額度，呼吸一下吧"),
    "home.insights": own("Calm insights", "平靜洞察"),
    "greeting.morning": android("greeting_morning"),
    "greeting.afternoon": android("greeting_afternoon"),
    "greeting.evening": android("greeting_evening"),
    "greeting.night": android("greeting_night"),
    # Breathe
    "breathe.title": android("breathe_title"),
    "breathe.start": android("breathe_start"),
    "breathe.ambient": android("breathe_ambient_sound"),
    "breathe.chime": android("breathe_cue_sound"),
    "breathe.cycle": android("breathe_cycle"),
    "breathe.endEarly": android("breathe_end_early"),
    "breathe.rhythm.a11y": own("Rhythm %@ seconds", "節奏 %@ 秒", "VoiceOver: the pattern's rhythm, e.g. 4, 7, 8"),
    "phase.inhale": android("phase_inhale"),
    "phase.holdIn": android("phase_hold_in"),
    "phase.exhale": android("phase_exhale"),
    "phase.holdOut": android("phase_hold_out"),
    "pattern.duration": android("pattern_duration"),
    "pattern.durationEven": android("pattern_duration_even"),
    "pattern.equal_444.name": android("pattern_equal"),
    "pattern.equal_444.desc": android("pattern_equal_desc"),
    "pattern.box_4444.name": android("pattern_box"),
    "pattern.box_4444.desc": android("pattern_box_desc"),
    "pattern.relax_478.name": android("pattern_478"),
    "pattern.relax_478.desc": android("pattern_478_desc"),
    "pattern.resonance_55.name": android("pattern_resonance"),
    "pattern.resonance_55.desc": android("pattern_resonance_desc"),
    # Grounding
    "grounding.label": android("grounding_label"),
    "grounding.another": android("grounding_another"),
    "grounding.return": android("grounding_return"),
    "grounding.returnNight": android("grounding_return_bedtime"),
    # Settings
    "settings.title": android("settings_title"),
    "settings.section.breathing": android("settings_row_breathing"),
    "settings.ambient": android("settings_ambient"),
    "settings.ambientHint": android("settings_ambient_hint"),
    "settings.chime": android("breathe_cue_sound"),
    "settings.chimeHint": android("settings_cue_hint"),
    "settings.haptics": own("Breath haptics", "換氣震動"),
    "settings.hapticsHint": own("A soft pulse as you switch breath", "換氣時輕輕震一下"),
    "settings.section.appearance": android("settings_section_appearance"),
    "settings.appearanceHint": own("Follows your iPhone's light and dark mode", "跟著 iPhone 的淺色或深色模式"),
    "settings.language": android("settings_language"),
    "settings.languageHint": own("Change it in iPhone Settings", "到 iPhone 的「設定」更改"),
    "settings.section.guidance": android("settings_section_guidance"),
    "settings.guidance.enable": own("Turn on gentle guidance", "開啟溫柔引導"),
    "settings.guidance.enableHint": own(
        "Uses Screen Time. Which apps you use stays on this device",
        "會用到「螢幕使用時間」，你用了哪些 App 只留在這台裝置",
    ),
    "settings.guidance.denied": own(
        "Screen Time access wasn't granted. Tap above to try again",
        "沒有取得「螢幕使用時間」權限，可以點上面再試一次",
    ),
    "settings.guidance.apps": own("Watched apps", "留意的 App"),
    "settings.guidance.appsHint": own("Apps, categories and sites to use less", "想少用一點的 App、類別和網站"),
    "settings.guidance.appsNone": own("None yet", "還沒選"),
    "settings.guidance.toggle": own("Gentle guidance", "溫柔引導"),
    "settings.guidance.toggleHint": own(
        "Rain first, then a soft pause screen",
        "先用雨聲提醒，再出現柔和的休息畫面",
    ),
    "settings.guidance.footer": own(
        "At your budget, a notification with distant rain asks if you'd like a break. Five minutes later a soft screen offers breathing or 5 more minutes. No passcode, no lecture",
        "到了額度，會先收到附遠方雨聲的通知，問你要不要休息。再過 5 分鐘會出現柔和的畫面，可以去呼吸，也可以再滑 5 分鐘，不用密碼，也不說教",
    ),
    "budget.daily": own("Daily budget", "每日額度"),
    "budget.dailyHint": own("Your total for the day", "一天累計的上限"),
    "budget.quiet": own("Quiet time", "安靜時間"),
    "budget.quietHint": own("No reminders for this long after breathing", "練習後多久不再提醒"),
    "duration.minutes": own("%lld min", "%lld 分鐘"),
    "duration.hours": own("%lld h", "%lld 小時"),
    "duration.hoursMinutes": own("%1$lld h %2$lld min", "%1$lld 小時 %2$lld 分鐘"),
    # Bedtime (copy mirrors Android's bedtime_* strings)
    "bedtime.title": own("Bedtime", "睡前引導"),
    "bedtime.offShort": own("Off", "未開啟"),
    "bedtime.off": own("Bedtime guidance is off", "睡前引導未開啟"),
    "bedtime.current": own(
        "From %1$@ each night, a gentle reminder after %2$lld min of scrolling",
        "每晚 %1$@ 起，累計滑 %2$lld 分鐘會提醒你休息",
    ),
    "bedtime.time": own("Bedtime", "想睡的時間"),
    "bedtime.threshold": own("Scrolling before a reminder", "提醒前可以滑多久"),
    "bedtime.note": own("At most two reminders a night, until 5 AM", "每晚最多提醒兩次，到清晨 5 點為止"),
    "bedtime.needsGuidance": own("Turn on gentle guidance in Settings first", "請先在設定開啟溫柔引導"),
    "bedtime.suggest.title": own("Before sleep, try 4-7-8", "睡前試試 4-7-8 呼吸"),
    "bedtime.suggest.body": own(
        "In for 4 seconds, hold for 7, out for 8. The long exhale helps your body slow down",
        "吸氣 4 秒、停 7 秒、吐氣 8 秒，長長的吐氣能讓身體慢下來",
    ),
    "bedtime.suggest.use": own("Use 4-7-8", "改用 4-7-8"),
    "bedtime.suggest.try": own("Try 4-7-8 now", "現在試試 4-7-8"),
    # Shortcuts grayscale guide
    "shortcut.title": own("Grayscale with Shortcuts", "用捷徑自動開啟灰階"),
    "shortcut.step1": own(
        "In the Shortcuts app, open Automation and add an App automation: choose your watched apps, “Is Opened”, “Run Immediately”",
        "打開「捷徑」App，在「自動化」新增「App」自動化，選好留意的 App，設成「已打開」並「立即執行」",
    ),
    "shortcut.step2": own(
        "Add the action “Over budget right now?” from Florsent, then an “If” for when the result is true",
        "加入 Florsent 的「現在超過額度了嗎？」動作，再加一個「如果」，條件設成結果為「是」",
    ),
    "shortcut.step3": own(
        "Inside the If, add “Set Color Filters” and turn it on. Choose Grayscale first in Settings › Accessibility › Display & Text Size › Color Filters",
        "在「如果」裡加入「設定色彩濾鏡」並開啟。灰階要先在「設定 › 輔助使用 › 顯示與文字大小 › 色彩濾鏡」選好",
    ),
    "shortcut.open": own("Open Shortcuts", "打開捷徑"),
    "shortcut.statusYes": own("Yes", "是"),
    "shortcut.statusNo": own("No", "否"),
    "shortcut.footer": own(
        "iOS doesn't let apps turn on grayscale, so your own automation does it. Florsent only answers whether you're over budget",
        "iOS 不讓 App 直接開啟灰階，所以交給你自己的自動化。Florsent 只負責回答現在是否超過額度",
    ),
    # Insights
    "insights.title": own("Calm insights", "平靜洞察"),
    "insights.calm": own("Breathing", "呼吸練習"),
    "insights.breathsToday": own("Today: %lld", "今天 %lld 次"),
    "insights.setup": own(
        "Turn on gentle guidance to see time on your watched apps here",
        "開啟溫柔引導後，這裡會顯示留意的 App 用了多久",
    ),
    "grounding.quiet": own("No reminders for the next %lld min", "接下來 %lld 分鐘不再提醒", "Mirrors Android grounding_cooldown"),
    "settings.footer": android("settings_footer"),
    # App Intents
    "intent.overBudget.title": own("Over budget right now?", "現在超過額度了嗎？"),
    "intent.overBudget.description": own(
        "Tells whether your watched apps are over today's budget. Use it in an automation to turn on grayscale",
        "回報留意的 App 今天是否超過額度，可以在自動化裡用來開啟灰階",
    ),
    "intent.overBudget.yes": own("Yes, over budget. Time for a break?", "是，已經超過額度了，休息一下吧？"),
    "intent.overBudget.no": own("Not yet, still within budget", "還沒有，仍在額度內"),
    "intent.breathe.title": android("nudge_accept_description"),
    "shortcut.overBudget": own("Over budget?", "超過額度了嗎？", "App Shortcut tile title"),
    "shortcut.breathe": android("breathe_title"),
    "intent.breathe.description": own("Opens Florsent's breathing practice", "打開 Florsent 的呼吸練習"),
}

# Plural strings: {key: {lang: {quantity: value}}}
APP_PLURALS = {
    "breathe.secondsLeft": {
        "en": {"one": "%lld second left", "other": "%lld seconds left"},
        "zh-Hant": {"other": "還有 %lld 秒"},
    },
    "settings.guidance.appsCount": {
        "en": {"one": "%lld selected", "other": "%lld selected"},
        "zh-Hant": {"other": "已選 %lld 項"},
    },
    "insights.breathsWeek": {
        "en": {"one": "%lld breathing session this week", "other": "%lld breathing sessions this week"},
        "zh-Hant": {"other": "這週完成 %lld 次呼吸練習"},
    },
}

APP_GROUNDING = {}
for lang in LANGS:
    arrays = golden["copy"][lang]["arrays"]
    for night, name in [(False, "grounding_prompts"), (True, "grounding_prompts_bedtime")]:
        for i, text in enumerate(arrays[name]):
            key = f"grounding.{'night' if night else 'day'}.{i + 1}"
            APP_GROUNDING.setdefault(key, {"android": f"{name}[{i}]"})[lang] = text
APP.update(APP_GROUNDING)

SHIELD_CONFIG = {
    "shield.daily.title": own("Time for a break", "休息一下吧"),
    "shield.daily.subtitle": own(
        "You've been scrolling for a while. A minute of breathing can help",
        "已經滑了一陣子，花一分鐘呼吸一下吧",
    ),
    "shield.bedtime.title": own("Time to wind down?", "該準備休息了嗎？", "Mirrors Android nudge_message_bedtime"),
    "shield.bedtime.subtitle": own(
        "It's getting late. A minute of slow breathing helps you settle for sleep",
        "夜深了，花一分鐘慢慢呼吸，讓身體準備入睡",
    ),
    "shield.primary": own("Go breathe", "去呼吸"),
    "shield.secondary": own("5 more minutes", "再滑 5 分鐘"),
}

# Notifications posted by FlorsentMonitor and FlorsentShieldAction (table "ScreenTime", compiled
# into the app and both extensions from ios/ScreenTimeShared).
SCREEN_TIME = {
    "notice.daily.title": own("How about a short break?", "要不要休息一下？"),
    "notice.bedtime.title": own("Time to wind down?", "該準備休息了嗎？", "Mirrors Android nudge_message_bedtime"),
    "notice.body": own("Tap to start breathing", "輕觸開始呼吸練習"),
    "invite.title": own("Ready when you are", "準備好就開始"),
    "invite.body": own("Tap to open breathing. One minute is enough", "輕觸打開呼吸練習，一分鐘就好"),
}

REPORT = {
    "report.today": own("Today on watched apps", "今天在留意的 App 上"),
    "report.minutes": android("minutes_format"),
    "report.hours": own("%lld h", "%lld 小時"),
    "report.hoursMinutes": own("%1$lld h %2$lld min", "%1$lld 小時 %2$lld 分鐘"),
    "report.lastSeven": own("Last 7 days", "近 7 天", "Mirrors Android insights_last_seven"),
    "report.topApps": own("Most used watched apps", "常用的留意 App", "Mirrors Android insights_top_apps"),
    "report.less": own("%@ less scrolling than last week", "這週比上週少滑了 %@", "Mirrors Android insight_less"),
    "report.more": own("A little more time on these apps this week", "這週在這些 App 上多花了一些時間"),
    "report.similar": own("About the same as last week", "這兩週的使用時間差不多", "Mirrors Android insight_similar"),
    "report.notEnough": own("Check back in a few days", "再記錄幾天，這裡就會有內容", "Mirrors Android insight_not_enough"),
    "report.noRecord": own("No record", "沒有紀錄"),
    "report.bar.a11y": own("%1$@, %2$@", "%1$@，%2$@"),
}

# App Shortcuts phrases: keys are the English phrases in code; must contain ${applicationName}.
SHORTCUTS = {
    "Is ${applicationName} over budget?": own("Is ${applicationName} over budget?", "${applicationName}：現在超過額度了嗎？"),
    "Am I over budget in ${applicationName}?": own("Am I over budget in ${applicationName}?", "${applicationName} 超過額度了嗎？"),
    "Breathe with ${applicationName}": own("Breathe with ${applicationName}", "用 ${applicationName} 呼吸練習"),
    "Start breathing in ${applicationName}": own("Start breathing in ${applicationName}", "在 ${applicationName} 開始呼吸練習"),
}


def unit(value: str) -> dict:
    return {"stringUnit": {"state": "translated", "value": value}}


def catalog(entries: dict, plurals: dict | None = None) -> dict:
    strings = {}
    for key, e in entries.items():
        item = {"extractionState": "manual", "localizations": {lang: unit(e[lang]) for lang in LANGS}}
        comment = f"Android: {e['android']}" if "android" in e else e.get("comment", "")
        if comment:
            item["comment"] = comment
        strings[key] = item
    for key, langs in (plurals or {}).items():
        strings[key] = {
            "extractionState": "manual",
            "localizations": {
                lang: {"variations": {"plural": {q: unit(v) for q, v in forms.items()}}} for lang, forms in langs.items()
            },
        }
    return {"sourceLanguage": "en", "strings": dict(sorted(strings.items())), "version": "1.0"}


def write(path: str, data: dict) -> None:
    target = ROOT / "ios" / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=False) + "\n")
    print(f"{path}: {len(data['strings'])} strings")


write("Florsent/Resources/Localizable.xcstrings", catalog(APP, APP_PLURALS))
write("Florsent/Resources/AppShortcuts.xcstrings", catalog(SHORTCUTS))
write("FlorsentShieldConfig/Localizable.xcstrings", catalog(SHIELD_CONFIG))
write("ScreenTimeShared/ScreenTime.xcstrings", catalog(SCREEN_TIME))
write("FlorsentReport/Localizable.xcstrings", catalog(REPORT))
