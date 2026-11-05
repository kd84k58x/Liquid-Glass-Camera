import re

with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    text = f.read()

text = text.replace("fun SettingsScreen(\n    val uiColor = Color(0xFFFFC107)\n    onBack: () -> Unit,", "fun SettingsScreen(\n    onBack: () -> Unit,")

# Now inject it into the top of the body
text = text.replace(") {\n    val view = LocalView.current\n", ") {\n    val uiColor = Color(0xFFFFC107)\n    val view = LocalView.current\n")

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(text)
