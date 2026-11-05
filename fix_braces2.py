with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    text = f.read()

# Add the missing brace before Bottom Floating Controls
text = text.replace("                // Bottom Floating Controls", "                }\n                // Bottom Floating Controls", 1)

# Remove the extra brace at the end of the file (which I added in fix_end_brace.py)
text = text.rstrip()
if text.endswith("}"):
    text = text[:-1]

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(text)
