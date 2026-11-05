with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    content = f.read()

content = content.replace("showSwipeDropdown || showTimerDropdown", "activeQuickSetting != null || showTimerDropdown")
content = content.replace("showSwipeDropdown = false", "activeQuickSetting = null")
content = content.replace("!showSwipeDropdown", "activeQuickSetting == null")

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(content)
print("Fixed remaining showSwipeDropdown usages")
