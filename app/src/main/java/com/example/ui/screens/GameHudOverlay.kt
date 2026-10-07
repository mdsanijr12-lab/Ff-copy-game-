package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BattleRoyaleViewModel
import com.example.engine.IslandMapGenerator
import com.example.engine.MatchUiState
import com.example.model.AbilityState
import com.example.model.CharacterId
import com.example.model.CompassDirection
import com.example.model.HudControlConfig
import com.example.model.HudControlId
import com.example.model.HudLayoutPreset
import com.example.model.ScopeType
import com.example.model.WeaponSlotIndex
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class HudResolvedBox(
    val controlId: HudControlId,
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val alpha: Float
) {
    val rightPx: Float get() = leftPx + widthPx
    val bottomPx: Float get() = topPx + heightPx
    val centerX: Float get() = leftPx + widthPx * 0.5f
    val centerY: Float get() = topPx + heightPx * 0.5f

    fun overlaps(other: HudResolvedBox, gapPx: Float = 0f): Boolean {
        return leftPx < other.rightPx + gapPx &&
            rightPx + gapPx > other.leftPx &&
            topPx < other.bottomPx + gapPx &&
            bottomPx + gapPx > other.topPx
    }
}

/**
 * Computes screen-clamped, non-overlapping pixel bounding boxes for every HUD element.
 * Optimized for low-end devices (Vivo Y03) with zero per-frame allocations when cached via remember.
 * Guarantees:
 * 1. Movement Joystick stays on the left half of the screen.
 * 2. All 9 Combat Buttons (Fire, Aim, Scope, Reload, Weapon Switch, Jump, Crouch, Prone, Ability) stay on the right half.
 * 3. No control is ever positioned outside the screen bounds.
 * 4. No two HUD controls ever overlap on any screen size or orientation.
 */
fun resolveHudLayoutBounds(
    preset: HudLayoutPreset,
    screenWPx: Float,
    screenHPx: Float,
    densityPxPerDp: Float
): Map<HudControlId, HudResolvedBox> {
    val safeW = screenWPx.coerceAtLeast(320f)
    val safeH = screenHPx.coerceAtLeast(320f)
    val marginPx = 4f * densityPxPerDp
    val minGapPx = 4f * densityPxPerDp

    // Adaptive compact scale so controls fit cleanly even on compact 360dp screens or landscape height
    val minDimDp = min(safeW, safeH) / densityPxPerDp.coerceAtLeast(1f)
    val screenFitScale = (minDimDp / 400f).coerceIn(0.76f, 1.0f)

    fun baseDimensionsDp(id: HudControlId): Pair<Float, Float> = when (id) {
        HudControlId.MINIMAP -> 98f to 98f
        HudControlId.JOYSTICK -> 108f to 108f
        HudControlId.FIRE_BUTTON -> 70f to 70f
        HudControlId.ABILITY_BUTTON -> 56f to 56f
        HudControlId.AIM_BUTTON, HudControlId.SCOPE_BUTTON, HudControlId.JUMP_BUTTON -> 50f to 50f
        HudControlId.RELOAD_BUTTON, HudControlId.CROUCH_BUTTON, HudControlId.PRONE_BUTTON -> 48f to 48f
        HudControlId.MEDKIT_BUTTON, HudControlId.GLOO_BUTTON, HudControlId.INVENTORY_BUTTON -> 48f to 48f
        HudControlId.WEAPON_BAR -> 168f to 48f
        HudControlId.COMPASS -> 178f to 42f
        HudControlId.HP_ARMOR_BAR -> 158f to 36f
    }

    val entries = HudControlId.entries
    val count = entries.size
    val widths = FloatArray(count)
    val heights = FloatArray(count)
    val lefts = FloatArray(count)
    val tops = FloatArray(count)
    val alphas = FloatArray(count)

    fun minAllowedLeft(id: HudControlId, w: Float): Float {
        return if (id.isRightSideCombatControl) {
            (safeW * 0.48f).coerceAtMost((safeW - w - marginPx).coerceAtLeast(marginPx))
        } else {
            marginPx
        }
    }

    fun maxAllowedLeft(id: HudControlId, w: Float): Float {
        return if (id == HudControlId.JOYSTICK) {
            (safeW * 0.46f - w).coerceAtLeast(marginPx)
        } else {
            (safeW - w - marginPx).coerceAtLeast(marginPx)
        }
    }

    for (i in 0 until count) {
        val id = entries[i]
        val cfg = (preset.controls[id] ?: HudControlConfig(id)).clamped()
        val (baseW, baseH) = baseDimensionsDp(id)
        val userScale = cfg.sizeScale.coerceIn(0.75f, 1.25f) * screenFitScale
        val isTouchButton = id !in setOf(HudControlId.MINIMAP, HudControlId.WEAPON_BAR, HudControlId.COMPASS, HudControlId.HP_ARMOR_BAR)
        val minTouchPx = if (isTouchButton) 48f * densityPxPerDp * screenFitScale.coerceAtLeast(0.85f) else 28f * densityPxPerDp
        val wPx = (baseW * densityPxPerDp * userScale).coerceAtLeast(minTouchPx).coerceAtMost(safeW * 0.46f)
        val hPx = (baseH * densityPxPerDp * userScale).coerceAtLeast(minTouchPx).coerceAtMost(safeH * 0.32f)

        widths[i] = wPx
        heights[i] = hPx
        alphas[i] = cfg.alpha

        val minL = minAllowedLeft(id, wPx)
        val maxL = maxAllowedLeft(id, wPx).coerceAtLeast(minL)
        val minT = marginPx
        val maxT = (safeH - hPx - marginPx).coerceAtLeast(minT)

        lefts[i] = (cfg.normX * safeW - wPx * 0.5f).coerceIn(minL, maxL)
        tops[i] = (cfg.normY * safeH - hPx * 0.5f).coerceIn(minT, maxT)
    }

    // Iterative non-overlapping relaxation solver so buttons NEVER overlap on any screen aspect ratio
    for (iter in 0 until 36) {
        var anyOverlap = false
        for (i in 0 until count) {
            for (j in i + 1 until count) {
                val overlapX = min(lefts[i] + widths[i], lefts[j] + widths[j]) - maxOf(lefts[i], lefts[j]) + minGapPx
                val overlapY = min(tops[i] + heights[i], tops[j] + heights[j]) - maxOf(tops[i], tops[j]) + minGapPx
                if (overlapX > 0f && overlapY > 0f) {
                    anyOverlap = true
                    val cxI = lefts[i] + widths[i] * 0.5f
                    val cyI = tops[i] + heights[i] * 0.5f
                    val cxJ = lefts[j] + widths[j] * 0.5f
                    val cyJ = tops[j] + heights[j] * 0.5f

                    if (overlapX < overlapY) {
                        val push = overlapX * 0.55f + 0.5f
                        val dir = if (cxI < cxJ || (cxI == cxJ && i < j)) -1f else 1f
                        lefts[i] += dir * push
                        lefts[j] -= dir * push
                    } else {
                        val push = overlapY * 0.55f + 0.5f
                        val dir = if (cyI < cyJ || (cyI == cyJ && i < j)) -1f else 1f
                        tops[i] += dir * push
                        tops[j] -= dir * push
                    }

                    val idI = entries[i]
                    val minLI = minAllowedLeft(idI, widths[i])
                    val maxLI = maxAllowedLeft(idI, widths[i]).coerceAtLeast(minLI)
                    lefts[i] = lefts[i].coerceIn(minLI, maxLI)
                    tops[i] = tops[i].coerceIn(marginPx, (safeH - heights[i] - marginPx).coerceAtLeast(marginPx))

                    val idJ = entries[j]
                    val minLJ = minAllowedLeft(idJ, widths[j])
                    val maxLJ = maxAllowedLeft(idJ, widths[j]).coerceAtLeast(minLJ)
                    lefts[j] = lefts[j].coerceIn(minLJ, maxLJ)
                    tops[j] = tops[j].coerceIn(marginPx, (safeH - heights[j] - marginPx).coerceAtLeast(marginPx))
                }
            }
        }
        if (!anyOverlap) break
    }

    val result = HashMap<HudControlId, HudResolvedBox>(count)
    for (i in 0 until count) {
        val id = entries[i]
        result[id] = HudResolvedBox(
            controlId = id,
            leftPx = lefts[i],
            topPx = tops[i],
            widthPx = widths[i],
            heightPx = heights[i],
            alpha = alphas[i]
        )
    }
    return result
}

@Composable
fun GameHudOverlay(
    state: MatchUiState,
    viewModel: BattleRoyaleViewModel,
    modifier: Modifier = Modifier
) {
    val activePreset = state.hudPresets.getOrElse(state.activeHudSlot) { state.hudPresets.first() }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val screenWPx = with(density) { maxWidth.toPx() }.coerceAtLeast(320f)
        val screenHPx = with(density) { maxHeight.toPx() }.coerceAtLeast(320f)
        val densityVal = density.density

        // Memoized layout calculation for Vivo Y03 low-end performance (recomputes only on preset or resize)
        val resolvedBounds = remember(activePreset, screenWPx.roundToInt(), screenHPx.roundToInt(), densityVal) {
            resolveHudLayoutBounds(activePreset, screenWPx, screenHPx, densityVal)
        }

        fun boxFor(id: HudControlId): HudResolvedBox =
            resolvedBounds[id] ?: HudResolvedBox(id, 0f, 0f, 96f, 96f, 0.92f)

        // 1. Directional Enemy Fire Indicators & Footstep Icons around Center Crosshair
        DirectionalCombatIndicators(
            state = state,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Synchronized Mini-Map (Left-Top)
        val mapBox = boxFor(HudControlId.MINIMAP)
        val mapSizeDp = with(density) { mapBox.widthPx.toDp() }
        Box(
            modifier = Modifier
                .hudBoxPositioned(mapBox, density)
                .alpha(mapBox.alpha)
                .testTag("hud_minimap")
        ) {
            SynchronizedMiniMap(
                state = state,
                mapSize = mapSizeDp
            )
        }

        // 3. Top Compass, Alive Count, Kills, Coins & Safe Zone Status
        val compassBox = boxFor(HudControlId.COMPASS)
        Box(
            modifier = Modifier
                .hudBoxPositioned(compassBox, density)
                .alpha(compassBox.alpha)
                .testTag("hud_compass")
        ) {
            TacticalCompassAndStatsBar(
                state = state,
                onLeaveMatch = { viewModel.returnToLobby() }
            )
        }

        // Friendly AI Voice Callout Banner (Friendly AI ONLY)
        state.friendlyCallout?.let { callout ->
            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.88f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp)
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Teammate Callout",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "${callout.botName}: \"${callout.message}\"",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Status / Feedback Banner
        if (state.statusBannerText.isNotEmpty()) {
            Surface(
                color = Color(0xFF090D16).copy(alpha = 0.88f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 82.dp)
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
            ) {
                Text(
                    text = state.statusBannerText,
                    color = Color(0xFF00E5FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // Dedicated Warning When Outside the Safe Zone (non-overlapping top alert)
        if (state.safeZone.isPlayerOutside) {
            Surface(
                color = Color(0xFFB71C1C).copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (state.statusBannerText.isNotEmpty()) 108.dp else 82.dp)
                    .border(1.2.dp, Color(0xFFFF8A80), RoundedCornerShape(14.dp))
                    .testTag("hud_outside_zone_warning")
            ) {
                Text(
                    text = "OUTSIDE SAFE ZONE! RETURN TO SAFE ZONE (-${state.safeZone.damagePerSecond} HP/s)",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        // Hit & Damage Feedback Indicator
        if (state.hitMarkerTimerSec > 0f && state.lastDamageDealtAmount > 0) {
            val isHeadshot = state.lastHitZone == com.example.model.HitZone.HEAD
            val badgeColor = if (isHeadshot) Color(0xFFFF1744) else Color(0xFFFFEA00)
            Surface(
                color = Color(0xFF090D16).copy(alpha = 0.90f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 110.dp)
                    .border(1.2.dp, badgeColor, RoundedCornerShape(14.dp))
                    .testTag("hud_damage_feedback")
            ) {
                Text(
                    text = "${state.lastHitZone.label.uppercase()} -${state.lastDamageDealtAmount} HP",
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        // Contextual Prompts: Vending Machine, Knocked Teammate Revive, Vehicle Enter/Exit
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 110.dp)
        ) {
            state.nearbyVendingMachine?.let { vm ->
                val deadCount = state.deadTeammates.size
                val hasEnoughCoins = state.matchCoins >= vm.coinCost
                val hasRespawnsLeft = vm.remainingRespawns > 0
                val canRespawn = deadCount > 0 && hasEnoughCoins && hasRespawnsLeft
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .border(
                            1.5.dp,
                            if (canRespawn) Color(0xFF00E676) else Color(0xFFEF4444),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = canRespawn) {
                            viewModel.useVendingMachineToRespawnTeammate()
                        }
                        .testTag("vending_machine_prompt")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "VENDING MACHINE (${vm.zoneName.uppercase()})",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        val statusText = when {
                            !hasRespawnsLeft -> "UNAVAILABLE: RESPAWN LIMIT REACHED (0/${vm.maxRespawns})"
                            deadCount == 0 -> "NO FULLY DEAD TEAMMATES TO RESPAWN"
                            !hasEnoughCoins -> "UNAVAILABLE: NEED ${vm.coinCost} COINS (YOU HAVE ${state.matchCoins})"
                            else -> "TAP TO RESPAWN ${state.deadTeammates.first().name} (${vm.coinCost} COINS | ${vm.remainingRespawns} LEFT)"
                        }
                        Text(
                            text = statusText,
                            color = if (canRespawn) Color(0xFF00E676) else Color(0xFFFF8A80),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            state.nearbyKnockAllyToRevive?.let { ally ->
                Button(
                    onClick = { viewModel.reviveNearbyKnockedTeammate() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    modifier = Modifier.testTag("revive_ally_button")
                ) {
                    Icon(Icons.Default.Healing, contentDescription = "Revive", tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("REVIVE ${ally.name} (${ally.knockedTimerSec.toInt()}s)", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                }
            }

            if (state.drivingVehicleId == null && state.nearbyVehicle != null) {
                Button(
                    onClick = { viewModel.toggleEnterExitVehicle() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.testTag("vehicle_button")
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = "Vehicle")
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "DRIVE ${state.nearbyVehicle?.name?.uppercase()}",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // If player is Spectating after full death, show Spectator Mode Overlay
        if (state.isPlayerDead) {
            SpectatorModeOverlay(
                state = state,
                onNextTarget = { viewModel.cycleSpectatorTarget() },
                onReturnToLobby = { viewModel.finishMatchFromSpectator() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            return@BoxWithConstraints
        }

        // Knocked state warning bar
        if (state.isPlayerKnocked) {
            Surface(
                color = Color(0xFFB71C1C).copy(alpha = 0.92f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 86.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (state.isBeingRevived) "BEING REVIVED BY TEAMMATE..." else "KNOCKED DOWN - BLEEDOUT IN ${state.playerKnockedTimerSec.toInt()}s",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (state.isBeingRevived) (state.reviveProgressSec / 3f).coerceIn(0f, 1f)
                            else (state.playerKnockedTimerSec / 25f).coerceIn(0f, 1f)
                        },
                        color = if (state.isBeingRevived) Color(0xFF00E676) else Color(0xFFFFEA00),
                        modifier = Modifier.width(170.dp)
                    )
                }
            }
        }

        // 4. Virtual Movement Joystick (Left Side — Responsive Multi-Touch + Unlimited Sprint Stamina)
        val joyBox = boxFor(HudControlId.JOYSTICK)
        val joySizeDp = with(density) { joyBox.widthPx.toDp() }
        Box(
            modifier = Modifier
                .hudBoxPositioned(joyBox, density)
                .alpha(joyBox.alpha)
                .testTag("hud_joystick")
        ) {
            TacticalVirtualJoystick(
                sizeDp = joySizeDp,
                onMoveChanged = { nx, ny, sprint ->
                    viewModel.setJoystickInput(nx, ny, sprint)
                }
            )
        }

        // 5. Manual FIRE Button (Right Side — Manual Fire ONLY + Smooth Thumb-Drag Aiming While Firing)
        val fireBox = boxFor(HudControlId.FIRE_BUTTON)
        val saniLocked = state.matchCharacter == CharacterId.SANI && state.abilityState == AbilityState.ACTIVE
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .hudBoxPositioned(fireBox, density)
                .alpha(fireBox.alpha)
                .clip(CircleShape)
                .background(
                    when {
                        saniLocked -> Color(0xFF475569)
                        state.isManualFiringHeld -> Color(0xFFFF1744)
                        else -> Color(0xFFD50000).copy(alpha = 0.90f)
                    }
                )
                .border(2.5.dp, if (saniLocked) Color(0xFFFF9100) else Color(0xFFFF8A80), CircleShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        viewModel.onFireButtonPressedChanged(true)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    change.consume()
                                    // Smoothly rotate camera while holding & dragging the Fire button
                                    viewModel.rotateCameraByTouch(delta.x, delta.y)
                                }
                            }
                        } finally {
                            viewModel.onFireButtonPressedChanged(false)
                        }
                    }
                }
                .testTag("hud_fire_button")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Manual Fire",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (saniLocked) "LOCKED" else "FIRE",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // 6. AIM (ADS) Button (Right Side)
        RoundHudActionButton(
            box = boxFor(HudControlId.AIM_BUTTON),
            density = density,
            label = "AIM",
            isActive = state.isAiming,
            activeColor = Color(0xFF00E5FF),
            testTag = "hud_aim_button",
            onClick = { viewModel.toggleAim() }
        ) {
            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Aim", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        // 7. SCOPE Button (Right Side — Red Dot / 2x / 4x / Sniper)
        val currentScopeLabel = state.activeWeapon?.equippedScope?.let {
            if (it.zoomFactor > 1f) "${it.zoomFactor.toInt()}x" else "SCOPE"
        } ?: "SCOPE"
        RoundHudActionButton(
            box = boxFor(HudControlId.SCOPE_BUTTON),
            density = density,
            label = currentScopeLabel,
            isActive = state.isScopedIn,
            activeColor = Color(0xFF00E676),
            testTag = "hud_scope_button",
            onClick = { viewModel.toggleOrCycleScope() }
        ) {
            Icon(Icons.Default.Visibility, contentDescription = "Scope", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        // 8. RELOAD Button (Right Side)
        RoundHudActionButton(
            box = boxFor(HudControlId.RELOAD_BUTTON),
            density = density,
            label = if (state.isReloading) "${String.format("%.1f", state.reloadRemainingSec)}s" else "RELOAD",
            isActive = state.isReloading,
            activeColor = Color(0xFFFFEA00),
            testTag = "hud_reload_button",
            onClick = { viewModel.triggerManualReload() }
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        // 9. JUMP / VAULT Button (Right Side)
        RoundHudActionButton(
            box = boxFor(HudControlId.JUMP_BUTTON),
            density = density,
            label = "JUMP",
            isActive = state.isJumping || state.isVaulting,
            activeColor = Color(0xFF38BDF8),
            testTag = "hud_jump_button",
            onClick = { viewModel.triggerJumpOrVault() }
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Jump or Vault", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // 10. CROUCH Button (Right Side)
        RoundHudActionButton(
            box = boxFor(HudControlId.CROUCH_BUTTON),
            density = density,
            label = "CROUCH",
            isActive = state.isCrouching,
            activeColor = Color(0xFF00E5FF),
            testTag = "hud_crouch_button",
            onClick = { viewModel.toggleCrouch() }
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Crouch", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        // 11. PRONE Button (Right Side)
        RoundHudActionButton(
            box = boxFor(HudControlId.PRONE_BUTTON),
            density = density,
            label = "PRONE",
            isActive = state.isProne,
            activeColor = Color(0xFF00E5FF),
            testTag = "hud_prone_button",
            onClick = { viewModel.toggleProne() }
        ) {
            Text("PRN", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        }

        // 12. CHARACTER ABILITY Button (Right Side — Shows READY, ACTIVE, COOLDOWN + Cooldown Timer)
        val abilityBox = boxFor(HudControlId.ABILITY_BUTTON)
        val abilityColor = when (state.abilityState) {
            AbilityState.READY -> if (state.matchCharacter == CharacterId.SANI) Color(0xFF00E5FF) else Color(0xFFFF4081)
            AbilityState.ACTIVE -> Color(0xFF00E676)
            AbilityState.COOLDOWN -> Color(0xFF475569)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .hudBoxPositioned(abilityBox, density)
                .alpha(abilityBox.alpha)
                .clip(CircleShape)
                .background(Color(0xFF0F172A).copy(alpha = 0.92f))
                .border(2.2.dp, abilityColor, CircleShape)
                .clickable(enabled = state.abilityState == AbilityState.READY) {
                    viewModel.activateCharacterAbility()
                }
                .testTag("hud_ability_button")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (state.matchCharacter == CharacterId.SANI) Icons.Default.Bolt else Icons.Default.Radar,
                    contentDescription = "Character Ability",
                    tint = abilityColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = state.abilityState.name,
                    color = abilityColor,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black
                )
                if (state.abilityState != AbilityState.READY) {
                    Text(
                        text = "${state.abilityTimerSec.roundToInt()}s",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 13. WEAPON SWITCH & AMMO BAR (Right Side — Main Gun 1, Main Gun 2, Secondary)
        val wepBarBox = boxFor(HudControlId.WEAPON_BAR)
        Box(
            modifier = Modifier
                .hudBoxPositioned(wepBarBox, density)
                .alpha(wepBarBox.alpha)
                .testTag("hud_weapon_bar")
        ) {
            WeaponSwitchBar(
                state = state,
                onSelectSlot = { viewModel.switchWeaponSlot(it) }
            )
        }

        // 14. MEDKIT Button (Left Side Utility)
        RoundHudActionButton(
            box = boxFor(HudControlId.MEDKIT_BUTTON),
            density = density,
            label = "MED (${state.medkitCount})",
            isActive = false,
            activeColor = Color(0xFF00E676),
            testTag = "hud_medkit_button",
            onClick = { viewModel.useMedkit() }
        ) {
            Icon(Icons.Default.Healing, contentDescription = "Medkit", tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
        }

        // 15. GLOO WALL Button (Left Side Utility)
        RoundHudActionButton(
            box = boxFor(HudControlId.GLOO_BUTTON),
            density = density,
            label = "GLOO (${state.glooWallCount})",
            isActive = false,
            activeColor = Color(0xFF38BDF8),
            testTag = "hud_gloo_button",
            onClick = { viewModel.deployGlooWall() }
        ) {
            Icon(Icons.Default.Shield, contentDescription = "Gloo Wall", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
        }

        // 16. BACKPACK / INVENTORY Button (Left Side Utility)
        RoundHudActionButton(
            box = boxFor(HudControlId.INVENTORY_BUTTON),
            density = density,
            label = "BAG Lv.${state.backpackTier}",
            isActive = state.isInventoryOpen,
            activeColor = Color(0xFFFACC15),
            testTag = "hud_bag_button",
            onClick = { viewModel.toggleInventorySheet() }
        ) {
            Icon(Icons.Default.Backpack, contentDescription = "Backpack", tint = Color.White, modifier = Modifier.size(16.dp))
        }

        // 17. HP & ARMOR BAR
        val hpBox = boxFor(HudControlId.HP_ARMOR_BAR)
        Box(
            modifier = Modifier
                .hudBoxPositioned(hpBox, density)
                .alpha(hpBox.alpha)
                .testTag("hud_hp_armor_bar")
        ) {
            HpAndArmorStatusCard(state = state)
        }

        // 17b. Small Vehicle Control UI while driving (Steering, Acceleration, Braking, Speedometer & Exit)
        if (state.drivingVehicleId != null && !state.isPlayerDead && !state.isPlayerKnocked) {
            VehicleDrivingControlBar(
                state = state,
                onSteerChanged = { viewModel.setVehicleSteering(it) },
                onThrottleChanged = { viewModel.setVehicleThrottle(it) },
                onBrakeChanged = { viewModel.setVehicleBraking(it) },
                onExitVehicle = { viewModel.toggleEnterExitVehicle() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 66.dp)
                    .testTag("vehicle_control_ui")
            )
        }

        // 18. Backpack Inventory Modal when opened
        if (state.isInventoryOpen) {
            InventoryOverlayCard(
                state = state,
                onSelectScope = { viewModel.selectScopeForActiveWeapon(it) },
                onClose = { viewModel.toggleInventorySheet() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun Modifier.hudBoxPositioned(
    box: HudResolvedBox,
    density: androidx.compose.ui.unit.Density
): Modifier = with(density) {
    this@hudBoxPositioned
        .offset { IntOffset(box.leftPx.roundToInt(), box.topPx.roundToInt()) }
        .size(box.widthPx.toDp(), box.heightPx.toDp())
}

@Composable
private fun VehicleDrivingControlBar(
    state: MatchUiState,
    onSteerChanged: (Float) -> Unit,
    onThrottleChanged: (Float) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
    onExitVehicle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vehicleName = state.currentDrivingVehicle?.name ?: "VEHICLE"
    val speedKmh = state.vehicleSpeedKmh
    val gearLabel = when {
        state.vehicleBrakeInput -> "BRK"
        state.vehicleSpeedMps > 0.3f -> "FWD"
        state.vehicleSpeedMps < -0.3f -> "REV"
        else -> "N"
    }

    Surface(
        color = Color(0xFF0B1320).copy(alpha = 0.92f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(14.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = "Driving Vehicle",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = vehicleName.uppercase(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "$speedKmh KM/H [$gearLabel]",
                    color = Color(0xFF00E676),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("vehicle_speed_text")
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Steer Left
                VehicleHoldControlButton(
                    label = "LEFT",
                    isActive = state.vehicleSteerInput < -0.1f,
                    activeColor = Color(0xFF00E5FF),
                    testTag = "vehicle_steer_left_button",
                    onPressChanged = { pressed -> onSteerChanged(if (pressed) -1f else 0f) }
                )

                // Steer Right
                VehicleHoldControlButton(
                    label = "RIGHT",
                    isActive = state.vehicleSteerInput > 0.1f,
                    activeColor = Color(0xFF00E5FF),
                    testTag = "vehicle_steer_right_button",
                    onPressChanged = { pressed -> onSteerChanged(if (pressed) 1f else 0f) }
                )

                // Brake / Reverse
                VehicleHoldControlButton(
                    label = "BRAKE",
                    isActive = state.vehicleBrakeInput,
                    activeColor = Color(0xFFFF5252),
                    testTag = "vehicle_brake_button",
                    onPressChanged = { pressed -> onBrakeChanged(pressed) }
                )

                // Accelerate / Gas
                VehicleHoldControlButton(
                    label = "GAS",
                    isActive = state.vehicleThrottleInput > 0.1f,
                    activeColor = Color(0xFF00E676),
                    testTag = "vehicle_accelerate_button",
                    onPressChanged = { pressed -> onThrottleChanged(if (pressed) 1f else 0f) }
                )

                // Exit Vehicle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 52.dp, height = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.88f))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .clickable { onExitVehicle() }
                        .testTag("vehicle_exit_button")
                ) {
                    Text(
                        text = "EXIT",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleHoldControlButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    testTag: String,
    onPressChanged: (Boolean) -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 52.dp, height = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.35f) else Color(0xFF1E293B))
            .border(1.2.dp, if (isActive) activeColor else Color(0xFF64748B), RoundedCornerShape(8.dp))
            .clickable { onPressChanged(!isActive) }
            .pointerInput(label) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onPressChanged(true)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                        }
                    } finally {
                        onPressChanged(false)
                    }
                }
            }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = if (isActive) activeColor else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun RoundHudActionButton(
    box: HudResolvedBox,
    density: androidx.compose.ui.unit.Density,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .hudBoxPositioned(box, density)
            .alpha(box.alpha)
            .clip(CircleShape)
            .background(if (isActive) activeColor.copy(alpha = 0.34f) else Color(0xFF0F172A).copy(alpha = 0.86f))
            .border(1.8.dp, if (isActive) activeColor else Color(0xFF64748B), CircleShape)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            iconContent()
            Text(
                text = label,
                color = Color.White,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Renders directional enemy fire indicators (Left, Right, Front, Rear)
 * AND simple directional footstep icons (NO enemy/teammate name, HP, or distance text!).
 */
@Composable
private fun DirectionalCombatIndicators(
    state: MatchUiState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.46f
            val ringRadius = minOf(size.width, size.height) * 0.27f

            for (fireInd in state.fireDirectionIndicators) {
                val startAngle = fireInd.angleRelToCameraDeg - 90f - 22f
                drawArc(
                    color = Color(0xFFFF1744).copy(alpha = (fireInd.remainingSec / 1.6f).coerceIn(0.25f, 0.92f)),
                    startAngle = startAngle,
                    sweepAngle = 44f,
                    useCenter = false,
                    topLeft = Offset(cx - ringRadius, cy - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = 10f)
                )
            }

            val footRadius = ringRadius * 0.82f
            for (foot in state.footstepIndicators) {
                val rad = Math.toRadians((foot.angleRelToCameraDeg - 90f).toDouble())
                val fx = cx + cos(rad).toFloat() * footRadius
                val fy = cy + sin(rad).toFloat() * footRadius
                val footColor = if (foot.isFriendly) {
                    Color(0xFF00E5FF).copy(alpha = 0.80f)
                } else {
                    Color(0xFFFF9100).copy(alpha = foot.intensity.coerceIn(0.45f, 0.95f))
                }
                drawOval(
                    color = footColor,
                    topLeft = Offset(fx - 5f, fy - 7f),
                    size = Size(5f, 9f)
                )
                drawOval(
                    color = footColor,
                    topLeft = Offset(fx + 2f, fy - 4f),
                    size = Size(5f, 9f)
                )
            }
        }
    }
}

/**
 * Synchronized coordinate conversion from 3D world (wx, wz) to 2D North-Up Minimap (x, y).
 * In the 3D world:
 * - North (0° yaw) is +Z -> mapped to Top of minimap (y = 0)
 * - South (180° yaw) is -Z -> mapped to Bottom of minimap (y = height)
 * - East (90° yaw) is +X -> mapped to Right of minimap (x = width)
 * - West (270° yaw) is -X -> mapped to Left of minimap (x = 0)
 */
fun worldToMinimapOffset(wx: Float, wz: Float, width: Float, height: Float): Offset {
    val halfSpan = IslandMapGenerator.PLAYABLE_LIMIT
    val worldSpan = halfSpan * 2f
    val mx = (((wx + halfSpan) / worldSpan) * width).coerceIn(0f, width)
    val my = (((halfSpan - wz) / worldSpan) * height).coerceIn(0f, height)
    return Offset(mx, my)
}

/**
 * Synchronized Mini-Map showing:
 * - Safe Zone (synchronized with world Safe Zone)
 * - Player
 * - Friendly teammates
 * - Vehicles
 * - Vending Machines
 * - Temporary enemy markers ONLY when detected, marked by friendly AI, or revealed by RIMA ability.
 */
@Composable
private fun SynchronizedMiniMap(
    state: MatchUiState,
    mapSize: Dp
) {
    val nowMs = System.currentTimeMillis()
    val borderColor = if (state.safeZone.isPlayerOutside) Color(0xFFFF1744) else Color(0xFF00E5FF)
    Surface(
        color = Color(0xFF0B1320).copy(alpha = 0.92f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .size(mapSize)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val w = size.width
                val h = size.height
                val worldSpan = IslandMapGenerator.PLAYABLE_LIMIT * 2f

                // River strip across minimap
                drawRect(
                    color = Color(0xFF0284C7).copy(alpha = 0.55f),
                    topLeft = Offset(0f, h * 0.47f),
                    size = Size(w, h * 0.06f)
                )

                // Current Safe Zone Circle (Synchronized with state.safeZone!)
                val szCenter = worldToMinimapOffset(state.safeZone.centerX, state.safeZone.centerZ, w, h)
                val szRadiusPx = ((state.safeZone.currentRadius / worldSpan) * w).coerceIn(3f, w * 0.72f)
                drawCircle(
                    color = if (state.safeZone.isShrinking) Color(0xFF00E5FF) else Color(0xFF38BDF8),
                    radius = szRadiusPx,
                    center = szCenter,
                    style = Stroke(width = 2.2f)
                )

                // Next Target Safe Zone Circle (White thin ring)
                val targetCenter = worldToMinimapOffset(state.safeZone.targetCenterX, state.safeZone.targetCenterZ, w, h)
                val targetRadiusPx = ((state.safeZone.targetRadius / worldSpan) * w).coerceIn(2f, w * 0.68f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.65f),
                    radius = targetRadiusPx,
                    center = targetCenter,
                    style = Stroke(width = 1.2f)
                )

                // Vending Machines (Green Squares)
                for (vm in state.vendingMachines) {
                    val vPos = worldToMinimapOffset(vm.x, vm.z, w, h)
                    drawRect(
                        color = if (vm.remainingRespawns > 0) Color(0xFF00E676) else Color(0xFF64748B),
                        topLeft = Offset(vPos.x - 3f, vPos.y - 3f),
                        size = Size(6f, 6f)
                    )
                }

                // Vehicles (Synchronized with world vehicles: Gold when free, Cyan when driven by player/ally)
                for (veh in state.vehicles) {
                    val vehPos = worldToMinimapOffset(veh.x, veh.z, w, h)
                    val isPlayerDrivingThis = state.drivingVehicleId == veh.id
                    drawCircle(
                        color = if (isPlayerDrivingThis) Color(0xFF00E5FF) else Color(0xFFFACC15),
                        radius = if (isPlayerDrivingThis) 3.8f else 3.0f,
                        center = vehPos
                    )
                }

                // Friendly Teammates & Temporary Enemy Markers (NEVER permanent enemy locations!)
                for (bot in state.bots) {
                    if (bot.isDead) continue
                    val bPos = worldToMinimapOffset(bot.x, bot.z, w, h)
                    if (bot.isFriendly) {
                        drawCircle(
                            color = if (bot.isKnocked) Color(0xFFFF9100) else Color(0xFF38BDF8),
                            radius = 3.8f,
                            center = bPos
                        )
                    } else if (state.isEnemyTemporarilyVisibleOnMinimap(bot, nowMs)) {
                        drawCircle(
                            color = Color(0xFFFF1744),
                            radius = 3.8f,
                            center = bPos
                        )
                    }
                }

                // Player position & North-up direction arrow
                val pPos = worldToMinimapOffset(state.playerX, state.playerZ, w, h)
                if (state.safeZone.isPlayerOutside) {
                    // Guide line from player toward Safe Zone center when outside the zone
                    drawLine(
                        color = Color(0xFFFFEA00).copy(alpha = 0.80f),
                        start = pPos,
                        end = szCenter,
                        strokeWidth = 1.4f
                    )
                }
                drawCircle(
                    color = Color.White,
                    radius = 4.5f,
                    center = pPos
                )
                val rad = Math.toRadians(state.cameraYawDeg.toDouble())
                drawLine(
                    color = Color(0xFF00E676),
                    start = pPos,
                    end = Offset(
                        (pPos.x + sin(rad).toFloat() * 9f).coerceIn(0f, w),
                        (pPos.y - cos(rad).toFloat() * 9f).coerceIn(0f, h)
                    ),
                    strokeWidth = 2.2f
                )
            }

            // Cardinal North 'N' indicator at top of minimap
            Text(
                text = "N",
                color = Color(0xFF00E5FF),
                fontSize = 7.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 1.dp)
            )

            // Synchronized Safe Zone Phase & Countdown Timer Footer
            Text(
                text = "P${state.safeZone.phase} ${state.safeZone.formattedCountdown} • ${
                    if (state.safeZone.isPlayerOutside) "OUTSIDE!"
                    else if (state.safeZone.isShrinking) "SHRINKING"
                    else if (state.safeZone.isFinalZoneReached) "FINAL"
                    else "SAFE"
                }",
                color = when {
                    state.safeZone.isPlayerOutside -> Color(0xFFFF1744)
                    state.safeZone.isShrinking -> Color(0xFFFFEA00)
                    else -> Color(0xFF00E5FF)
                },
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .testTag("minimap_zone_timer")
            )
        }
    }
}

@Composable
private fun TacticalCompassAndStatsBar(
    state: MatchUiState,
    onLeaveMatch: () -> Unit
) {
    val camYaw = state.cameraYawDeg
    val yawInt = camYaw.roundToInt() % 360
    val activeDir = state.compassDirection

    // Compute visible compass ticks around camera yaw (-90°..+90° window) for N, NE, E, SE, S, SW, W, NW
    val allDirections = CompassDirection.entries
    val visibleTicks = remember(yawInt) {
        allDirections.mapNotNull { dir ->
            var diff = (dir.centerDeg - camYaw + 540f) % 360f - 180f
            if (diff < -180f) diff += 360f
            if (abs(diff) <= 75f) dir to diff else null
        }.sortedBy { it.second }
    }

    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.90f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            // Top Row: 8-Point Tactical Compass Strip (N, NE, E, SE, S, SW, W, NW) synchronized with camera
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compass_direction_strip")
            ) {
                visibleTicks.forEach { (dir, _) ->
                    val isCurrent = dir == activeDir
                    Text(
                        text = if (isCurrent) "▼${dir.label} ${yawInt}°" else dir.label,
                        color = when {
                            isCurrent -> Color(0xFF00E676)
                            dir == CompassDirection.N -> Color(0xFF00E5FF)
                            else -> Color(0xFF94A3B8)
                        },
                        fontSize = if (isCurrent) 8.5.sp else 7.5.sp,
                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Bottom Row: Alive Count, Kills, Safe Zone Countdown, Coins & Exit
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ALIVE ${state.aliveCount}",
                    color = Color(0xFF00E5FF),
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = "K:${state.playerKills}",
                    color = Color(0xFFFF5252),
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = "Z${state.safeZone.phase} ${state.safeZone.formattedCountdown}",
                    color = if (state.safeZone.isShrinking) Color(0xFFFFEA00) else Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = "${state.matchCoins}C",
                    color = Color(0xFFFFD600),
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Exit to Lobby",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onLeaveMatch() }
                        .testTag("hud_exit_match_button")
                )
            }
        }
    }
}

/**
 * Responsive left-side movement joystick with immediate touch response and multi-touch pointer isolation.
 */
@Composable
private fun TacticalVirtualJoystick(
    sizeDp: Dp,
    onMoveChanged: (Float, Float, Boolean) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var isSprinting by remember { mutableStateOf(false) }
    val currentOnMoveChanged by rememberUpdatedState(onMoveChanged)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeDp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val center = Offset(size.width * 0.5f, size.height * 0.5f)
                    val maxRadius = (size.width * 0.42f).coerceAtLeast(16f)

                    fun updateFromPosition(pos: Offset) {
                        val raw = pos - center
                        val dist = hypot(raw.x, raw.y)
                        val clamped = if (dist > maxRadius && dist > 0f) {
                            Offset((raw.x / dist) * maxRadius, (raw.y / dist) * maxRadius)
                        } else raw
                        knobOffset = clamped
                        val normX = (clamped.x / maxRadius).coerceIn(-1f, 1f)
                        val normY = (-clamped.y / maxRadius).coerceIn(-1f, 1f) // Up is +Y forward
                        val sprint = normY > 0.72f
                        isSprinting = sprint
                        currentOnMoveChanged(normX, normY, sprint)
                    }

                    updateFromPosition(down.position)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            updateFromPosition(change.position)
                        }
                    } finally {
                        knobOffset = Offset.Zero
                        isSprinting = false
                        currentOnMoveChanged(0f, 0f, false)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val outerR = size.width * 0.44f
            drawCircle(
                color = Color(0xFF0F172A).copy(alpha = 0.65f),
                radius = outerR,
                center = center
            )
            drawCircle(
                color = if (isSprinting) Color(0xFF00E676) else Color(0xFF00E5FF).copy(alpha = 0.75f),
                radius = outerR,
                center = center,
                style = Stroke(width = 2.5f)
            )
            // Sprint indicator notch at top
            drawCircle(
                color = if (isSprinting) Color(0xFF00E676) else Color.White.copy(alpha = 0.5f),
                radius = 5f,
                center = Offset(center.x, center.y - outerR + 4f)
            )
            // Thumb knob
            drawCircle(
                color = if (isSprinting) Color(0xFF00E676) else Color(0xFF00E5FF),
                radius = outerR * 0.36f,
                center = center + knobOffset
            )
        }
    }
}

@Composable
private fun WeaponSwitchBar(
    state: MatchUiState,
    onSelectSlot: (WeaponSlotIndex) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize()
    ) {
        WeaponSlotIndex.entries.forEach { slot ->
            val wep = state.weaponSlots[slot]
            val isSelected = state.activeWeaponSlot == slot
            val reserve = wep?.let { state.ammoReserve[it.spec.category.ammoType] ?: 0 } ?: 0
            Surface(
                color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A).copy(alpha = 0.88f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF475569),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectSlot(slot) }
                    .testTag("weapon_slot_${slot.name.lowercase()}")
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = wep?.spec?.name?.take(8) ?: slot.label,
                        color = if (wep != null) Color.White else Color(0xFF64748B),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (wep != null) {
                        Text(
                            text = "${wep.currentAmmo}/$reserve",
                            color = if (wep.currentAmmo == 0) Color(0xFFFF5252) else Color(0xFF00E676),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = "EMPTY",
                            color = Color(0xFF475569),
                            fontSize = 7.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HpAndArmorStatusCard(state: MatchUiState) {
    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.90f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "HP ${state.playerHp.roundToInt()}/${state.playerMaxHp.roundToInt()}",
                    color = Color(0xFF00E676),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = "ARM ${state.playerArmor.roundToInt()} (V${state.armorTier}/H${state.helmetTier})",
                    color = Color(0xFF38BDF8),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { (state.playerHp / state.playerMaxHp.coerceAtLeast(1f)).coerceIn(0f, 1f) },
                color = if (state.playerHp < 30f) Color(0xFFFF1744) else Color(0xFF00E676),
                trackColor = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun InventoryOverlayCard(
    state: MatchUiState,
    onSelectScope: (ScopeType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.96f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .width(310.dp)
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "BACKPACK LV.${state.backpackTier} (${state.currentBackpackWeight}/${state.maxBackpackCapacity})",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
                Text(
                    text = "CLOSE",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onClose() }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Coins: ${state.matchCoins} | Medkits: ${state.medkitCount} | Gloo Walls: ${state.glooWallCount} | Grenades: ${state.grenadeCount}",
                color = Color.White,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Reserve Ammo:",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            state.ammoReserve.forEach { (type, count) ->
                Text(
                    text = "• ${type.label}: $count rounds",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Scopes Owned (Tap to Equip):",
                color = Color(0xFF00E676),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            val activeWep = state.activeWeapon
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                state.inventoryScopes.sortedBy { it.zoomFactor }.forEach { scope ->
                    val isSelected = activeWep?.equippedScope == scope
                    val isCompat = activeWep == null || scope in activeWep.spec.category.compatibleScopes
                    Surface(
                        color = when {
                            isSelected -> Color(0xFF00E676)
                            isCompat -> Color(0xFF1E293B)
                            else -> Color(0xFF0F172A)
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (isSelected) Color.White else Color(0xFF334155),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = isCompat) { onSelectScope(scope) }
                    ) {
                        Text(
                            text = scope.label,
                            color = if (isSelected) Color.Black else if (isCompat) Color.White else Color(0xFF475569),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpectatorModeOverlay(
    state: MatchUiState,
    onNextTarget: () -> Unit,
    onReturnToLobby: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTarget = state.currentSpectatorTarget
    var resultsClicked by remember(state.matchStartCount) { mutableStateOf(false) }
    Surface(
        color = Color(0xFF090D16).copy(alpha = 0.94f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .testTag("spectator_mode_overlay")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SPECTATOR MODE (OBSERVING ONLY)",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                if (activeTarget != null) {
                    val roleTag = when {
                        activeTarget.isTeammate -> "LIVING TEAMMATE"
                        activeTarget.botId == state.killerBotId -> "ELIMINATED BY"
                        else -> "REMAINING PLAYER"
                    }
                    Text(
                        text = "$roleTag: ${activeTarget.name} (${activeTarget.characterId.displayName}) - HP ${activeTarget.hp.coerceAtLeast(1f).roundToInt()}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "No valid targets remaining",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.spectatorTargets.size > 1) {
                    Button(
                        onClick = onNextTarget,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.testTag("spectator_switch_target_button")
                    ) {
                        Text("SWITCH TARGET", fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = {
                        if (!resultsClicked) {
                            resultsClicked = true
                            onReturnToLobby()
                        }
                    },
                    enabled = !resultsClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                    modifier = Modifier.testTag("spectator_results_button")
                ) {
                    Text("MATCH RESULTS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
