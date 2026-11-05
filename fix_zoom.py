import re
with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    text = f.read()

# 1. Remove Zoom Backdrop variables
backdrop_regex = r"    var isZoomUiActive by remember \{ mutableStateOf\(false\) \}.*?label = \"zoomBackdropAlpha\"\n    \)"
text = re.sub(backdrop_regex, "", text, flags=re.DOTALL)

# 2. Revert Bottom Floating Controls layout
bottom_controls_regex = r"                    // Bottom Floating Controls.*?ExactZoomRow\(currentZoom, uiColor, maxZoom\) \{ zoom ->"
replacement = """                    // Bottom Floating Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Zoom Presets Floating Capsule
                        ExactZoomRow(currentZoom, uiColor, maxZoom) { zoom ->"""
text = re.sub(bottom_controls_regex, replacement, text, flags=re.DOTALL)

# 3. Remove the extra closing brace we added for the Box
mode_row_regex = r"                        // Mode Selection Floating Pill.*?ExactModeRow\(.*?onModeChange = \{ mode ->.*?currentMode = mode\n                                \}\n                            \}\n                        \)\n                    \}\n                    \}"
mode_replacement = """                        // Mode Selection Floating Pill
                        ExactModeRow(
                            selectedMode = currentMode,
                            uiColor = uiColor,
                            onModeChange = { mode ->
                                if (!isRecording) {
                                    triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                    currentMode = mode
                                }
                            }
                        )
                    }"""
text = re.sub(mode_row_regex, mode_replacement, text, flags=re.DOTALL)

with open("/tmp/CameraUI_nozoom.kt", "w") as f:
    f.write(text)
