with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    text = f.read()

text += "\n}\n"

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(text)
