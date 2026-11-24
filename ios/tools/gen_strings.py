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
    "home.guidance.title": own("Gentle guidance on iPhone", "iPhone 上的漸進引導"),
    "home.guidance.body": own(
        "Screen Time support comes in the next version. Breathing and grounding already work",
        "下一版會接上「螢幕使用時間」，呼吸練習和回到當下現在就能用",
    ),
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
    "settings.screenTime": own("Screen Time", "螢幕使用時間"),
    "settings.screenTimeValue": own("Next version", "下一版開放"),
    "settings.shortcut": own("Shortcuts automation", "捷徑自動化"),
    "settings.shortcutHint": own(
        "Add “Over budget right now?” to an automation in Shortcuts and turn on grayscale when it answers yes",
        "在「捷徑」的自動化加入「現在超過額度了嗎？」，回答「是」時就開啟灰階",
    ),
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
    "shield.title": android("nudge_message"),
    "shield.subtitle": own(
        "You've been scrolling for a while. A minute of breathing can help",
        "已經滑了一陣子，花一分鐘呼吸一下吧",
    ),
    "shield.primary": android("nudge_accept_description"),
    "shield.secondary": android("nudge_dismiss_description"),
}

SHIELD_ACTION = {
    "notification.title": android("nudge_message"),
    "notification.body": own("Tap to start breathing", "輕觸開始呼吸練習"),
}

REPORT = {
    "report.today": android("today_label"),
    "report.minutes": android("minutes_format"),
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
write("FlorsentShieldAction/Localizable.xcstrings", catalog(SHIELD_ACTION))
write("FlorsentReport/Localizable.xcstrings", catalog(REPORT))
