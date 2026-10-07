package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BattleRoyaleViewModel
import com.example.engine.MatchUiState
import com.example.model.HudControlConfig
import com.example.model.HudControlId
import kotlin.math.roundToInt

@Composable
fun HudEditorScreen(
    state: MatchUiState,
    viewModel: BattleRoyaleViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.closeHudEditor()
    }

    var selectedControlId by remember { mutableStateOf(HudControlId.ABILITY_BUTTON) }
    val activePreset = state.hudPresets.getOrElse(state.activeHudSlot) { state.hudPresets.first() }
    val selectedConfig = (activePreset.controls[selectedControlId] ?: HudControlConfig(selectedControlId)).clamped()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("hud_editor_screen")
    ) {
        val screenWPx = with(density) { maxWidth.toPx() }.coerceAtLeast(320f)
        val screenHPx = with(density) { maxHeight.toPx() }.coerceAtLeast(320f)
        val densityVal = density.density

        val resolvedBounds = remember(activePreset, screenWPx.roundToInt(), screenHPx.roundToInt(), densityVal) {
            resolveHudLayoutBounds(activePreset, screenWPx, screenHPx, densityVal)
        }

        // Render every gameplay control as a draggable, resizable node clamped inside screen bounds
        HudControlId.entries.forEach { controlId ->
            val box = resolvedBounds[controlId] ?: return@forEach
            val isSelected = controlId == selectedControlId
            val isRectShape = controlId in setOf(
                HudControlId.WEAPON_BAR,
                HudControlId.HP_ARMOR_BAR,
                HudControlId.COMPASS,
                HudControlId.MINIMAP
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(box.leftPx.roundToInt(), box.topPx.roundToInt()) }
                    .size(with(density) { box.widthPx.toDp() }, with(density) { box.heightPx.toDp() })
                    .alpha(box.alpha)
                    .clip(if (isRectShape) RoundedCornerShape(10.dp) else CircleShape)
                    .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.28f) else Color(0xFF1E293B).copy(alpha = 0.85f))
                    .border(
                        width = if (isSelected) 2.5.dp else 1.5.dp,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF64748B),
                        shape = if (isRectShape) RoundedCornerShape(10.dp) else CircleShape
                    )
                    .clickable { selectedControlId = controlId }
                    .pointerInput(controlId, screenWPx, screenHPx) {
                        detectDragGestures(
                            onDragStart = { selectedControlId = controlId },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val cur = _uiStateControl(viewModel, controlId)
                                val minX = if (controlId.isRightSideCombatControl) 0.52f else 0.06f
                                val maxX = if (controlId == HudControlId.JOYSTICK) 0.45f else 0.94f
                                val nextNormX = (cur.normX + dragAmount.x / screenWPx).coerceIn(minX, maxX)
                                val nextNormY = (cur.normY + dragAmount.y / screenHPx).coerceIn(0.05f, 0.95f)
                                viewModel.updateHudControl(
                                    controlId = controlId,
                                    normX = nextNormX,
                                    normY = nextNormY
                                )
                            }
                        )
                    }
                    .testTag("hud_editor_node_${controlId.name.lowercase()}")
            ) {
                Text(
                    text = controlId.displayName,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(3.dp)
                )
            }
        }

        // Control Inspector & 3-Layout Switcher Panel at Top-Center
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .width(340.dp)
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "HUD EDITOR (MAX 3 LAYOUTS)",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    // 3 Layout Preset Tabs (Slot 0, 1, 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (slot in 0..2) {
                            val active = state.activeHudSlot == slot
                            Surface(
                                color = if (active) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clickable { viewModel.selectHudLayoutSlot(slot) }
                                    .testTag("hud_slot_tab_$slot")
                            ) {
                                Text(
                                    text = "L${slot + 1}",
                                    color = if (active) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Editing: ${selectedControlId.displayName} (Drag control to move)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                // Size Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Size ${(selectedConfig.sizeScale * 100).roundToInt()}%",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        modifier = Modifier.width(68.dp)
                    )
                    Slider(
                        value = selectedConfig.sizeScale,
                        onValueChange = { viewModel.updateHudControl(selectedControlId, sizeScale = it) },
                        valueRange = 0.75f..1.35f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF)),
                        modifier = Modifier.weight(1f).height(26.dp)
                    )
                }

                // Transparency / Alpha Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Alpha ${(selectedConfig.alpha * 100).roundToInt()}%",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        modifier = Modifier.width(68.dp)
                    )
                    Slider(
                        value = selectedConfig.alpha,
                        onValueChange = { viewModel.updateHudControl(selectedControlId, alpha = it) },
                        valueRange = 0.30f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E676), activeTrackColor = Color(0xFF00E676)),
                        modifier = Modifier.weight(1f).height(26.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetActiveHudLayout() },
                        modifier = Modifier.weight(1f).testTag("hud_reset_button")
                    ) {
                        Text("RESET", color = Color(0xFFFF8A80), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.saveActiveHudLayout() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.weight(1f).testTag("hud_save_button")
                    ) {
                        Text("SAVE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    Button(
                        onClick = { viewModel.closeHudEditor() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier.weight(1f).testTag("hud_exit_button")
                    ) {
                        Text("DONE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

private fun _uiStateControl(viewModel: BattleRoyaleViewModel, id: HudControlId): HudControlConfig {
    val s = viewModel.uiState.value
    val preset = s.hudPresets.getOrElse(s.activeHudSlot) { s.hudPresets.first() }
    return (preset.controls[id] ?: HudControlConfig(id)).clamped()
}
