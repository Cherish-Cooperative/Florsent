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
    "settings.language": android("settings_language"),
    "settings.guidance.apps": own("Watched apps", "留意的 App"),
    "settings.guidance.appsNone": own("None yet", "還沒選"),
    "settings.guidance.toggle": own("Gentle guidance", "溫柔引導"),
    "settings.row.budget": own("Budgets", "使用額度", "Mirrors Android settings_row_budget"),
    "budget.daily": own("Daily budget", "每日額度"),
    "budget.quiet": own("Quiet time", "安靜時間"),
    "budget.quietHint": own("Quiet time: no reminders for a while after breathing", "安靜時間：呼吸練習後，這段時間不再提醒"),
    "duration.minutes": own("%lld min", "%lld 分鐘"),
    "duration.hours": own("%lld h", "%lld 小時"),
    "duration.hoursMinutes": own("%1$lld h %2$lld min", "%1$lld 小時 %2$lld 分鐘"),
    # Bedtime (copy mirrors Android's bedtime_* strings)
    "bedtime.title": own("Bedtime", "睡前引導"),
    "bedtime.offShort": own("Off", "未開啟"),
    "bedtime.time": own("Bedtime", "想睡的時間"),
    "bedtime.threshold": own("Scrolling before a reminder", "提醒前可以滑多久"),
    "bedtime.note": own("At most two reminders a night, until 5 AM", "每晚最多提醒兩次，到清晨 5 點"),
    "bedtime.needsGuidance": own("Turn on gentle guidance first", "請先開啟溫柔引導"),
    "bedtime.suggest.title": own("Before sleep, try 4-7-8", "睡前試試 4-7-8 呼吸"),
    "bedtime.suggest.use": own("Use 4-7-8", "改用 4-7-8"),
    "bedtime.suggest.try": own("Try 4-7-8 now", "現在試試 4-7-8"),
    # Shortcuts grayscale guide. Labels are the exact iOS 26 ones (zh_TW read from the iOS 26.5
    # runtime): 自動化, App, 已開啟, 已關閉, 立即執行, 下一步, 如果, 設定顏色濾鏡, 顏色濾鏡, 灰階.
    "shortcut.title": own("Grayscale", "自動灰階"),
    "shortcut.optional": own("Optional", "選用"),
    "shortcut.step1": own(
        "In Shortcuts, tap Automation › + › App. Choose your watched apps, tick “Is Opened” and “Run Immediately”, then Next",
        "打開「捷徑」，點「自動化」›「＋」›「App」。選好社群 App，勾「已開啟」與「立即執行」，點「下一步」",
    ),
    "shortcut.step2": own(
        "Search “Florsent” and add “Over budget right now?”. Then search and add “If”",
        "搜尋「Florsent」，加入「現在超過額度了嗎？」，再搜尋並加入「如果」",
    ),
    "shortcut.step3": own(
        "Search “Color Filters” and drag “Set Color Filters” inside the If",
        "搜尋「顏色濾鏡」，把「設定顏色濾鏡」拖進「如果」裡",
    ),
    "shortcut.step4": own(
        "Make one more App automation with “Is Closed”: add “Set Color Filters” and switch it to “Off”",
        "再新增一個「已關閉」的 App 自動化：加入「設定顏色濾鏡」，把「開啟」改成「關閉」",
    ),
    "shortcut.open": own("Open Shortcuts", "打開捷徑"),
    "shortcut.done": own("Done", "完成"),
    "shortcut.statusYes": own("Yes", "是"),
    "shortcut.statusNo": own("No", "否"),
    "shortcut.footer": own(
        "Color Filters are grayscale by default. Within budget, nothing changes",
        "顏色濾鏡預設就是灰階。還在額度內時不會變灰",
    ),
    # Why Screen Time access wasn't granted (FamilyControlsError), one line each
    "auth.hint.passcode": own("Turn on a device passcode in Settings first", "請先在系統「設定」開啟裝置密碼"),
    "auth.hint.restricted": own("Screen Time is restricted on this device", "這台裝置的「螢幕使用時間」受到限制"),
    "auth.hint.network": own("Needs a connection. Try again in a moment", "需要網路連線，請稍後再試"),
    "auth.hint.conflict": own("Screen Time is already managed by another app or family", "「螢幕使用時間」已由其他 App 或家人管理"),
    "auth.hint.account": own("Sign in to your Apple Account in Settings first", "請先在「設定」登入 Apple 帳號"),
    "auth.hint.generic": own("Screen Time access wasn't granted. Tap to try again", "沒有取得「螢幕使用時間」權限，點一下再試"),
    # Insights
    "insights.title": own("Calm insights", "平靜洞察"),
    "insights.calm": own("Breathing", "呼吸練習"),
    "insights.breathsToday": own("Today: %lld", "今天 %lld 次"),
    "grounding.quiet": own("No reminders for the next %lld min", "接下來 %lld 分鐘不再提醒", "Mirrors Android grounding_cooldown"),
    # Android says "this phone"; iOS also runs on iPad, so the device-neutral words.
    "settings.footer": own("Florsent %@ · Your data stays on this device", "Florsent %@・資料只存在這台裝置上"),
    # App Intents
    "intent.overBudget.title": own("Over budget right now?", "現在超過額度了嗎？"),
    "intent.overBudget.description": own(
        "Whether your watched apps are over budget now",
        "留意的 App 現在是否超過額度",
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
