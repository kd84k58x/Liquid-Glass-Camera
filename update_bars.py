import re

with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    content = f.read()

# Replace variables
content = content.replace('var currentAspect by remember { mutableStateOf("FULL") }', 'var currentAspect by remember { mutableStateOf("9:16") }')
content = content.replace('var showSwipeDropdown by remember { mutableStateOf(false) }', 'var activeQuickSetting by remember { mutableStateOf<String?>(null) }')
content = content.replace('var activeDropdownSection by remember { mutableStateOf("Ratio") }', '')

import sys

# Replace the dropdown arrow Box with the Ratio Text Box
dropdown_start = content.find("                            Box(\\n                                modifier = Modifier\\n                                    .size(36.dp)")
dropdown_end_str = "                                    modifier = Modifier.size(20.dp)\\n                                )\\n                            }"
dropdown_end = content.find(dropdown_end_str, dropdown_start)
if dropdown_start != -1 and dropdown_end != -1:
    dropdown_end += len(dropdown_end_str)
    ratio_btn = """                            Box(
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
    content = content[:dropdown_start] + ratio_btn + content[dropdown_end:]
else:
    print("Could not find dropdown arrow section")


timer_dropdown_start = content.find("                    // Timer Dropdown")
swipe_dropdown_end = content.find("                // Bottom Floating Controls")

if timer_dropdown_start != -1 and swipe_dropdown_end != -1:
    new_controls = """                    // Timer Dropdown
                    AnimatedVisibility(
                        visible = showTimerDropdown,
                        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .glassEffect(RoundedCornerShape(24.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(0, 3, 5, 10).forEach { t ->
                                val isSelected = currentTimer == t
                                val text = if (t == 0) "Off" else "${t}s"
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSelected) uiColor else Color.White.copy(alpha = 0.16f))
                                        .border(1.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f), CircleShape)
                                        .bouncyClickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            currentTimer = t
                                            showTimerDropdown = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = text,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Separate floating bar for Watermark and Filter
                    AnimatedVisibility(
                        visible = !showResOptions && !showFpsOptions && !showTimerDropdown,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .glassEffect(CircleShape)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mark",
                                color = if (activeQuickSetting == "Mark") uiColor else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (activeQuickSetting == "Mark") FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.bouncyClickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    activeQuickSetting = if (activeQuickSetting == "Mark") null else "Mark"
                                }
                            )
                            Text(
                                text = "Filter",
                                color = if (activeQuickSetting == "Filter") uiColor else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (activeQuickSetting == "Filter") FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.bouncyClickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    activeQuickSetting = if (activeQuickSetting == "Filter") null else "Filter"
                                }
                            )
                        }
                    }

                    // Sliding Segmented Controls for Ratio, Mark, Filter
                    AnimatedVisibility(
                        visible = activeQuickSetting != null,
                        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        Box(modifier = Modifier.padding(top = 12.dp)) {
                            if (activeQuickSetting == "Ratio") {
                                SlidingSegmentedControl(
                                    options = aspectRatios,
                                    selectedOption = currentAspect,
                                    onOptionSelected = { 
                                        currentAspect = it
                                        // Auto-close after selection is nice but let's just leave it up
                                    },
                                    labelModifier = { it },
                                    uiColor = uiColor,
                                    itemWidth = 72.dp
                                )
                            } else if (activeQuickSetting == "Mark") {
                                SlidingSegmentedControl(
                                    options = WatermarkProfile.values().toList(),
                                    selectedOption = selectedWatermark,
                                    onOptionSelected = { 
                                        selectedWatermark = it
                                        isWatermarkEnabled = (it != WatermarkProfile.OFF)
                                    },
                                    labelModifier = { it.label },
                                    uiColor = uiColor,
                                    itemWidth = 86.dp
                                )
                            } else if (activeQuickSetting == "Filter") {
                                SlidingSegmentedControl(
                                    options = ColorFilterProfile.values().toList(),
                                    selectedOption = selectedFilter,
                                    onOptionSelected = { selectedFilter = it },
                                    labelModifier = { it.title },
                                    uiColor = uiColor,
                                    itemWidth = 80.dp
                                )
                            }
                        }
                    }
"""
    content = content[:timer_dropdown_start] + new_controls + "\n" + content[swipe_dropdown_end:]
else:
    print("Could not find timer dropdown boundaries")

with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(content)
print("Updated successfully")
