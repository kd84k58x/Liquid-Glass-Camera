import re

with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    content = f.read()

pattern = r"if \(!showResOptions && !showFpsOptions\) \{\s*Box\(\s*modifier = Modifier\s*\.size\(36\.dp\).*?imageVector = if \(showSwipeDropdown\).*?Modifier\.size\(20\.dp\)\s*\)\s*\}"

replacement = """if (!showResOptions && !showFpsOptions) {
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(CircleShape)
                                    .background(if (activeQuickSetting == "Ratio") Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
                                    .bouncyClickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        activeQuickSetting = if (activeQuickSetting == "Ratio") null else "Ratio"
                                        showTimerDropdown = false
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentAspect,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }"""

new_content, count = re.subn(pattern, replacement, content, flags=re.DOTALL)
if count > 0:
    print(f"Replaced {count} instances.")
    with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
        f.write(new_content)
else:
    print("Pattern not found!")
