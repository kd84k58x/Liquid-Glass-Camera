with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    text = f.read()

text = text.replace(".background(Color(0xFF2C2C30))", ".background(uiColor)")
text = text.replace("color = if (isSelected) uiColor else Color.White.copy(alpha = 0.7f),", "color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),")
text = text.replace("color = if (isSelected) uiColor else Color.White.copy(alpha = 0.7f)", "color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)")

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(text)
