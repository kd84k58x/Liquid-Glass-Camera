with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    text = f.read()

text = text.replace("import androidx.compose.ui.text.style.TextAlign", "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow")

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(text)
