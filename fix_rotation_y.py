with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    text = f.read()

text = text.replace("rotationZ = switchRotationZ", "rotationY = switchRotationY")

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(text)
