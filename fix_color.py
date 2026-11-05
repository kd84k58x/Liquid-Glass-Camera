import re
with open("/tmp/CameraUI_nozoom.kt", "r") as f:
    text = f.read()

# 1. Replace uiColor state with constant
ui_color_regex = r"    var uiColor by remember \{\s*mutableStateOf\(Color\(prefs\.getInt\(\"ui_color\", 0xFFFFC107\.toInt\(\)\)\)\)\s*\}"
text = re.sub(ui_color_regex, "    val uiColor = Color(0xFFFFC107)", text)

# 2. Remove uiColor from SettingsScreen call
settings_call_regex = r"                        uiColor = uiColor,\n                        onUiColorChange = \{\n                            uiColor = it\n                            prefs\.edit\(\)\.putInt\(\"ui_color\", it\.value\.toInt\(\)\)\.apply\(\)\n                        \},"
text = re.sub(settings_call_regex, "", text)

# 3. Remove uiColor from SettingsScreen definition
settings_def_regex = r"    uiColor: Color,\n    onUiColorChange: \(Color\) -> Unit,"
text = re.sub(settings_def_regex, "", text)

# 4. Remove Section 5 entirely
section_5_regex = r"            // Section 5: Accent Color Palette.*?// Section 6: Default Aspect Ratio"
replacement_section_6 = r"            // Section 6: Default Aspect Ratio"
text = re.sub(section_5_regex, replacement_section_6, text, flags=re.DOTALL)

with open("/tmp/CameraUI_nocolor.kt", "w") as f:
    f.write(text)
