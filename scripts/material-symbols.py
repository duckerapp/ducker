#!/usr/bin/env python3
"""Writes the app's icons, ui/components/Symbols.kt, from Google's Material Symbols.

    python3 scripts/material-symbols.py

Style Rounded, weight WEIGHT, grade 0, optical size 24; outlined unless a row asks for the filled
form. To add an icon, add a row to ICONS and run the script again; the SVGs come from the npm
package @material-symbols/svg-<WEIGHT> at the pinned version, through jsDelivr. Licence: Apache 2.0,
app/src/main/assets/licenses/Material-Symbols-LICENSE.txt.
"""
import re
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

VERSION = "0.47.6"
# The owner chose 500 on 19.10.2026 after trying 400 on the phone (D-323).
WEIGHT = 500
URL = "https://cdn.jsdelivr.net/npm/@material-symbols/svg-{}@{}/rounded/{}.svg"
OUT = Path(__file__).resolve().parent.parent / "app/src/main/java/com/github/kr328/clash/ui/components/Symbols.kt"

# Name in the code, symbol, filled, mirrored in right-to-left layouts.
# Filled only where the fill is a state: a favourite, a done or chosen mark.
ICONS = [
    ("Add", "add", False, False),
    ("AddCircleOutline", "add_circle", False, False),
    ("AltRoute", "alt_route", False, False),
    ("Apps", "apps", False, False),
    ("Article", "article", False, False),
    ("ArrowBack", "arrow_back", False, True),
    ("BatteryChargingFull", "battery_charging_full", False, False),
    ("BugReport", "bug_report", False, False),
    ("Calculate", "calculate", False, False),
    ("Campaign", "campaign", False, False),
    ("Cancel", "cancel", True, False),
    ("Category", "category", False, False),
    ("Check", "check", False, False),
    ("CheckCircle", "check_circle", True, False),
    ("Close", "close", False, False),
    ("Code", "code", False, False),
    ("ContentCopy", "content_copy", False, False),
    ("ContentPaste", "content_paste", False, False),
    ("Contrast", "contrast", False, False),
    ("CreditCard", "credit_card", False, False),
    ("DarkMode", "dark_mode", False, False),
    ("DeleteOutline", "delete", False, False),
    ("Edit", "edit", False, False),
    ("Email", "mail", False, False),
    ("ErrorOutline", "error", False, False),
    ("Favorite", "favorite", False, False),
    ("FileDownload", "download", False, False),
    ("FileOpen", "file_open", False, False),
    ("Fingerprint", "fingerprint", False, False),
    ("Flag", "flag", False, False),
    ("FlashOff", "flash_off", False, False),
    ("FlashOn", "flash_on", False, False),
    ("Forum", "forum", False, False),
    ("Fullscreen", "fullscreen", False, False),
    ("Home", "home", False, False),
    ("Info", "info", False, False),
    ("KeyboardArrowDown", "keyboard_arrow_down", False, False),
    ("KeyboardArrowRight", "keyboard_arrow_right", False, True),
    ("KeyboardArrowUp", "keyboard_arrow_up", False, False),
    ("Lan", "lan", False, False),
    ("Language", "language", False, False),
    ("LightMode", "light_mode", False, False),
    ("Link", "link", False, False),
    ("List", "list", False, True),
    ("Lock", "lock", False, False),
    ("Monitoring", "monitoring", False, False),
    ("MoreHoriz", "more_horiz", False, False),
    ("Notifications", "notifications", False, False),
    ("OpenInNew", "open_in_new", False, True),
    ("Palette", "palette", False, False),
    ("PhotoLibrary", "photo_library", False, False),
    ("PowerSettingsNew", "power_settings_new", False, False),
    ("PriorityHigh", "priority_high", False, False),
    ("QrCode2", "qr_code_2", False, False),
    ("QrCodeScanner", "qr_code_scanner", False, False),
    ("RadioButtonUnchecked", "radio_button_unchecked", False, False),
    ("Redo", "redo", False, True),
    ("Refresh", "refresh", False, False),
    ("RestartAlt", "restart_alt", False, False),
    ("Science", "science", False, False),
    ("Search", "search", False, False),
    ("Send", "send", False, True),
    ("Settings", "settings", False, False),
    ("Shield", "shield", False, False),
    ("ShieldLock", "shield_lock", False, False),
    ("SmartToy", "smart_toy", False, False),
    ("Speed", "speed", False, False),
    ("Star", "star", True, False),
    ("StarOutline", "star", False, False),
    ("SupportAgent", "support_agent", False, False),
    ("SystemUpdate", "mobile_arrow_down", False, False),
    ("TaskAlt", "task_alt", False, False),
    ("TextDecrease", "text_decrease", False, False),
    ("TextFields", "text_fields", False, False),
    ("TextIncrease", "text_increase", False, False),
    ("ToggleOn", "toggle_on", False, False),
    ("Translate", "translate", False, False),
    ("Tune", "tune", False, False),
    ("Undo", "undo", False, True),
    ("Update", "update", False, False),
    ("VisibilityOff", "visibility_off", False, False),
    ("VitalSigns", "vital_signs", False, False),
    ("VpnLock", "vpn_lock", False, False),
    ("WarningAmber", "warning", False, False),
]

PATH_DATA = re.compile(r"[MmLlHhVvCcSsQqTtAaZz0-9.,\- ]+")


def path_of(symbol: str, filled: bool) -> str:
    url = URL.format(WEIGHT, VERSION, symbol + ("-fill" if filled else ""))
    root = ET.fromstring(urllib.request.urlopen(url, timeout=30).read())
    assert root.get("viewBox") == "0 -960 960 960", (symbol, root.get("viewBox"))
    parts = [e.get("d") for e in root.iter() if e.tag.endswith("path")]
    assert parts and all(PATH_DATA.fullmatch(d) for d in parts), symbol
    return " ".join(parts)


def main():
    rows = []
    for name, symbol, filled, mirrored in ICONS:
        args = '"%s", "%s"' % (symbol + ("_fill" if filled else ""), path_of(symbol, filled))
        if mirrored:
            args += ", mirrored = true"
        rows.append("    val %s: ImageVector by lazy { symbol(%s) }\n" % (name, args))
        print(name)

    OUT.write_text(
        "// Written by scripts/material-symbols.py from Material Symbols %s, weight %d; edit the script, not this file.\n" % (VERSION, WEIGHT)
        + """package com.github.kr328.clash.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's icons: Google's Material Symbols, Rounded, weight %d, as vectors (D-323). Apache License 2.0,
 * see `assets/licenses/Material-Symbols-LICENSE.txt`. [androidx.compose.material3.Icon] tints them, so the
 * color here does not matter.
 */
object Symbols {
""" % WEIGHT + "".join(rows) + """
    /** The symbols are drawn on a 960 grid whose top is at -960, as in their SVG `viewBox`. */
    private fun symbol(name: String, path: String, mirrored: Boolean = false): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = mirrored,
    ).apply {
        addGroup(translationY = 960f)
        addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        clearGroup()
    }.build()
}
"""
    )
    print("written", OUT, len(ICONS))


if __name__ == "__main__":
    main()
