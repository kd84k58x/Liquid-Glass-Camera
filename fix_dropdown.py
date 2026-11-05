import re

with open("app/src/main/java/com/example/ui/CameraUI.kt", "r") as f:
    content = f.read()

# Find the Swipe Dropdown section
start_marker = "// Swipe Dropdown"
end_marker = "// Bottom Floating Controls"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx == -1 or end_idx == -1:
    print("Could not find markers")
    exit(1)

# We want to replace the inside of the AnimatedVisibility block that handles showSwipeDropdown
# It's easier to just replace the Column content
column_start = content.find("Column(", start_idx)
column_end = content.find("                // Bottom Floating Controls", column_start)

if column_start == -1 or column_end == -1:
    print("Could not find column bounds")
    exit(1)

replacement = """                    // Swipe Dropdown
                    AnimatedVisibility(
                        visible = showSwipeDropdown,
                        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .glassEffect(RoundedCornerShape(24.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .widthIn(max = 360.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // CATEGORIES ROW
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Ratio", "Mark", "Filter").forEach { cat ->
                                    val isSelected = activeDropdownSection == cat
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) uiColor else Color.White.copy(alpha = 0.12f))
                                            .border(1.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f), CircleShape)
                                            .bouncyClickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                activeDropdownSection = cat
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            
                            // OPTIONS ROW
                            AnimatedContent(
                                targetState = activeDropdownSection,
                                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                                label = "DropdownOptions"
                            ) { section ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (section == "Ratio") {
                                        aspectRatios.forEach { aspect ->
                                            val isSelected = currentAspect == aspect
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) uiColor else Color.White.copy(alpha = 0.16f))
                                                    .border(1.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f), CircleShape)
                                                    .bouncyClickable {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        currentAspect = aspect
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = aspect,
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    } else if (section == "Mark") {
                                        WatermarkProfile.values().forEach { profile ->
                                            val isSelected = selectedWatermark == profile
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) uiColor else Color.White.copy(alpha = 0.16f))
                                                    .border(1.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f), CircleShape)
                                                    .bouncyClickable {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        selectedWatermark = profile
                                                        isWatermarkEnabled = (profile != WatermarkProfile.OFF)
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = profile.label,
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    } else if (section == "Filter") {
                                        ColorFilterProfile.values().forEach { filter ->
                                            val isSelected = selectedFilter == filter
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) uiColor else Color.White.copy(alpha = 0.16f))
                                                    .border(1.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f), CircleShape)
                                                    .bouncyClickable {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        selectedFilter = filter
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = filter.title,
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
"""

new_content = content[:start_idx] + replacement + content[column_end:]
with open("app/src/main/java/com/example/ui/CameraUI.kt", "w") as f:
    f.write(new_content)
print("Updated successfully")
