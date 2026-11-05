import re

with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    text = f.read()

# Make it a local val everywhere
text = text.replace("uiColor: Color = Color(0xFFFFC107),", "val uiColor = Color(0xFFFFC107)")

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(text)
