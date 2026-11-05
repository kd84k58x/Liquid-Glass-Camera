with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    content = f.read()

target = "                // Bottom Floating Controls"
replacement = """                }
                // Bottom Floating Controls"""

new_content = content.replace(target, replacement, 1)

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(new_content)
print("Added missing brace")
