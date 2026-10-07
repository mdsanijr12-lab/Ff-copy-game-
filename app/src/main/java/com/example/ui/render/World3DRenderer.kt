package com.example.ui.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.IslandMapGenerator
import com.example.engine.MatchUiState
import com.example.model.AbilityState
import com.example.model.BuildingType
import com.example.model.CharacterAnimState
import com.example.model.CharacterId
import com.example.model.HitZone
import com.example.model.LootCategory
import com.example.model.ScopeType
import com.example.model.WeaponCategory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private sealed interface RenderCommand {
    val depthZ: Float
}

private data class BuildingRenderCmd(
    override val depthZ: Float,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val widthMeters: Float,
    val heightMeters: Float,
    val colorHex: Long,
    val isVaultable: Boolean,
    val isBridge: Boolean
) : RenderCommand

private data class TreeRenderCmd(
    override val depthZ: Float,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val heightMeters: Float
) : RenderCommand

private data class RockRenderCmd(
    override val depthZ: Float,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val radiusMeters: Float,
    val heightMeters: Float
) : RenderCommand

private data class BillboardRenderCmd(
    override val depthZ: Float,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val widthMeters: Float,
    val heightMeters: Float,
    val isLargeBanner: Boolean
) : RenderCommand

private data class VendingRenderCmd(
    override val depthZ: Float,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val remainingRespawns: Int
) : RenderCommand

private data class VehicleRenderCmd(
    override val depthZ: Float,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val name: String
) : RenderCommand

private data class LootRenderCmd(
    override val depthZ: Float,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val category: LootCategory,
    val label: String
) : RenderCommand

private data class GlooWallRenderCmd(
    override val depthZ: Float,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val widthMeters: Float
) : RenderCommand

private data class BotRenderCmd(
    override val depthZ: Float,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val characterId: CharacterId,
    val isFriendly: Boolean,
    val isKnocked: Boolean,
    val animState: CharacterAnimState,
    val animPhase: Float,
    val revealedByRima: Boolean
) : RenderCommand

@Composable
fun World3DViewport(
    state: MatchUiState,
    onCameraDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val saniBannerBitmap = ImageBitmap.imageResource(id = R.drawable.img_sani_banner_1791221363697)
    val textMeasurer = rememberTextMeasurer()
    val currentOnCameraDrag by rememberUpdatedState(onCameraDrag)
    // Object-pooled command buffer reused across frames to minimize allocations on Vivo Y03
    val commands = remember { ArrayList<RenderCommand>(160) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("world_3d_viewport")
            .pointerInput(Unit) {
                awaitEachGesture {
                    var activeLookPointerId: PointerId? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        if (changes.none { it.pressed }) break

                        // Pick the latest pressed unconsumed pointer if our current look pointer was released
                        if (activeLookPointerId == null || changes.none { it.id == activeLookPointerId && it.pressed }) {
                            activeLookPointerId = changes.lastOrNull { it.pressed && !it.isConsumed }?.id
                                ?: changes.lastOrNull { it.pressed }?.id
                        }

                        val lookChange = changes.firstOrNull { it.id == activeLookPointerId && it.pressed }
                        if (lookChange != null && !lookChange.isConsumed) {
                            val delta = lookChange.positionChange()
                            if (delta != Offset.Zero) {
                                lookChange.consume()
                                currentOnCameraDrag(delta.x, delta.y)
                            }
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val fovScale = state.smoothFovScale.coerceIn(1f, 3.6f)
        val focalLength = (w * 0.62f) * fovScale
        val effectivePitch = (state.playerPitchDeg + if (state.isFreeLooking) state.freeLookPitchOffsetDeg else 0f).coerceIn(-38f, 38f)
        val horizonY = (h * 0.44f) + (effectivePitch * (h * 0.011f))

        // 1. Sky & Horizon Atmosphere
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0B192C),
                    Color(0xFF1E3E62),
                    Color(0xFF3A6D8C)
                ),
                startY = 0f,
                endY = horizonY.coerceIn(0f, h)
            ),
            size = Size(w, horizonY.coerceIn(0f, h))
        )

        // 2. Island Terrain Ground Plane
        val groundColorTop = if (state.isSwimming) Color(0xFF0284C7) else Color(0xFF2D4A3E)
        val groundColorBottom = if (state.isSwimming) Color(0xFF0369A1) else Color(0xFF1E352B)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(groundColorTop, groundColorBottom),
                startY = horizonY.coerceIn(0f, h),
                endY = h
            ),
            topLeft = Offset(0f, horizonY.coerceIn(0f, h)),
            size = Size(w, (h - horizonY).coerceAtLeast(0f))
        )

        // Camera position (3rd-person offset behind player with wall-clip protection & over-the-shoulder aim)
        val effectiveYaw = (state.playerYawDeg + if (state.isFreeLooking) state.freeLookYawOffsetDeg else 0f + 360f) % 360f
        val yawRad = Math.toRadians(effectiveYaw.toDouble())
        val sinYaw = sin(yawRad).toFloat()
        val cosYaw = cos(yawRad).toFloat()
        val desiredCamDist = state.smoothCamDistBehind.coerceIn(0.25f, 4.5f)
        // Prevent camera from clipping behind solid walls/rocks
        val obstacleBehindDist = IslandMapGenerator.computeRayObstacleDistance(
            startX = state.playerX,
            startZ = state.playerZ,
            dirX = -sinYaw,
            dirZ = -cosYaw,
            maxRange = desiredCamDist,
            glooWalls = state.glooWalls
        )
        val camDistBehind = (obstacleBehindDist - 0.25f).coerceIn(0.25f, desiredCamDist)
        val shoulderOffset = state.smoothShoulderOffsetX
        val camHeightOffset = when {
            state.isProne || state.isPlayerKnocked -> 0.85f
            state.isCrouching -> 1.35f
            else -> 2.15f
        }
        val camX = state.playerX - sinYaw * camDistBehind + cosYaw * shoulderOffset
        val camY = state.playerY + camHeightOffset
        val camZ = state.playerZ - cosYaw * camDistBehind - sinYaw * shoulderOffset

        // 3. Draw River & Roads Perspective Ground Strips
        drawRoadsAndWaterStrips(
            camX = camX,
            camY = camY,
            camZ = camZ,
            sinYaw = sinYaw,
            cosYaw = cosYaw,
            focalLength = focalLength,
            horizonY = horizonY,
            screenW = w,
            screenH = h
        )

        // 4. Build Depth-Sorted Render List (with LOD & Distance Culling for Vivo Y03)
        val maxDrawDist = state.effectiveGraphicsQuality.drawDistance
        commands.clear()

        fun projectWorldPoint(wx: Float, wy: Float, wz: Float): Triple<Float, Float, Float>? {
            val dx = wx - camX
            val dz = wz - camZ
            val localZ = dx * sinYaw + dz * cosYaw
            if (localZ < 1.2f || localZ > maxDrawDist) return null
            val localX = dx * cosYaw - dz * sinYaw
            val localY = wy - camY
            val scale = focalLength / localZ
            val sx = (w * 0.5f) + localX * scale
            val sy = horizonY - localY * scale
            if (sx < -w * 0.45f || sx > w * 1.45f) return null
            return Triple(sx, sy, localZ)
        }

        // Buildings & Bridges & Low Vaultable Walls
        for (b in IslandMapGenerator.buildings) {
            if (abs(b.centerX - camX) > maxDrawDist || abs(b.centerZ - camZ) > maxDrawDist) continue
            val proj = projectWorldPoint(b.centerX, b.floorY, b.centerZ) ?: continue
            val (sx, sy, lz) = proj
            commands.add(
                BuildingRenderCmd(
                    depthZ = lz,
                    screenX = sx,
                    screenY = sy,
                    scale = focalLength / lz,
                    widthMeters = b.width,
                    heightMeters = b.height,
                    colorHex = b.colorHex,
                    isVaultable = b.isVaultable,
                    isBridge = b.type == BuildingType.BRIDGE_DECK
                )
            )
        }

        // Large SANI Billboards & Banners (using uploaded SANI banner asset)
        for (bb in IslandMapGenerator.billboards) {
            if (abs(bb.x - camX) > maxDrawDist || abs(bb.z - camZ) > maxDrawDist) continue
            val by = IslandMapGenerator.getTerrainHeight(bb.x, bb.z)
            val proj = projectWorldPoint(bb.x, by, bb.z) ?: continue
            val (sx, sy, lz) = proj
            commands.add(
                BillboardRenderCmd(
                    depthZ = lz,
                    screenX = sx,
                    screenY = sy,
                    scale = focalLength / lz,
                    widthMeters = bb.width,
                    heightMeters = bb.height,
                    isLargeBanner = bb.isLargeBanner
                )
            )
        }

        // Environmental Rocks
        var rockCount = 0
        val maxRocks = state.effectiveGraphicsQuality.maxTreesRendered / 2
        for (rk in IslandMapGenerator.rocks) {
            if (rockCount >= maxRocks) break
            if (abs(rk.x - camX) > maxDrawDist || abs(rk.z - camZ) > maxDrawDist) continue
            val ry = IslandMapGenerator.getTerrainHeight(rk.x, rk.z)
            val proj = projectWorldPoint(rk.x, ry, rk.z) ?: continue
            val (sx, sy, lz) = proj
            rockCount++
            commands.add(
                RockRenderCmd(
                    depthZ = lz,
                    screenX = sx,
                    screenY = sy,
                    scale = focalLength / lz,
                    radiusMeters = rk.radius,
                    heightMeters = rk.height
                )
            )
        }

        // Trees (respecting GraphicsQuality maxTreesRendered for low-end devices)
        var treeCount = 0
        val maxTrees = state.effectiveGraphicsQuality.maxTreesRendered
        for (tr in IslandMapGenerator.trees) {
            if (treeCount >= maxTrees) break
            if (abs(tr.x - camX) > maxDrawDist || abs(tr.z - camZ) > maxDrawDist) continue
            val ty = IslandMapGenerator.getTerrainHeight(tr.x, tr.z)
            val proj = projectWorldPoint(tr.x, ty, tr.z) ?: continue
            val (sx, sy, lz) = proj
            treeCount++
            commands.add(
                TreeRenderCmd(
                    depthZ = lz,
                    screenX = sx,
                    screenY = sy,
                    scale = focalLength / lz,
                    heightMeters = tr.height
                )
            )
        }

        // Vending Machines
        for (vm in state.vendingMachines) {
            if (abs(vm.x - camX) > maxDrawDist || abs(vm.z - camZ) > maxDrawDist) continue
            val vy = IslandMapGenerator.getTerrainHeight(vm.x, vm.z)
            val proj = projectWorldPoint(vm.x, vy, vm.z) ?: continue
            val (sx, sy, lz) = proj
            commands.add(
                VendingRenderCmd(
                    depthZ = lz,
                    screenX = sx,
                    screenY = sy,
                    scale = focalLength / lz,
                    remainingRespawns = vm.remainingRespawns
                )
            )
        }

        // Vehicles
        for (veh in state.vehicles) {
            if (veh.id == state.drivingVehicleId) continue
            if (abs(veh.x - camX) > maxDrawDist || abs(veh.z - camZ) > maxDrawDist) continue
            val vy = IslandMapGenerator.getTerrainHeight(veh.x, veh.z)
            val proj = projectWorldPoint(veh.x, vy, veh.z) ?: continue
            val (sx, sy, lz) = proj
            commands.add(
                VehicleRenderCmd(
                    depthZ = lz,
                    screenX = sx,
                    screenY = sy,
                    scale = focalLength / lz,
                    name = veh.name
                )
            )
        }

        // Deployed Gloo Walls
        for (gw in state.glooWalls) {
            if (abs(gw.x - camX) > maxDrawDist || abs(gw.z - camZ) > maxDrawDist) continue
            val gy = IslandMapGenerator.getTerrainHeight(gw.x, gw.z)
            val proj = projectWorldPoint(gw.x, gy, gw.z) ?: continue
            val (sx, sy, lz) = proj
            commands.add(
                GlooWallRenderCmd(
                    depthZ = lz,
                    screenX = sx,
                    screenY = sy,
                    scale = focalLength / lz,
                    widthMeters = gw.width
                )
            )
        }

        // Loot items within 72m (capped per frame for Vivo Y03 performance)
        var renderedLootCount = 0
        val maxLootRendered = state.effectiveGraphicsQuality.maxTreesRendered
        for (item in state.lootItems) {
            if (renderedLootCount >= maxLootRendered) break
            if (item.collected) continue
            val dx = item.x - camX
            val dz = item.z - camZ
            if (abs(dx) > 72f || abs(dz) > 72f) continue
            val proj = projectWorldPoint(item.x, item.y, item.z) ?: continue
            val (sx, sy, lz) = proj
            renderedLootCount++
            commands.add(
                LootRenderCmd(
                    depthZ = lz,
                    screenX = sx,
                    screenY = sy,
                    scale = focalLength / lz,
                    category = item.category,
                    label = item.name
                )
            )
        }

        // Friendly & Enemy AI Bots
        for (bot in state.bots) {
            if (bot.isDead) continue
            val proj = projectWorldPoint(bot.x, bot.y, bot.z) ?: continue
            val (sx, sy, lz) = proj
            val distToPlayer = hypot(bot.x - state.playerX, bot.z - state.playerZ)
            val revealedByRima = !bot.isFriendly && state.rimaRevealActive && distToPlayer <= 165f
            val hasLos = IslandMapGenerator.hasLineOfSight(camX, camZ, bot.x, bot.z, state.glooWalls)
            if (!hasLos && !revealedByRima && !bot.isFriendly) continue

            commands.add(
                BotRenderCmd(
                    depthZ = lz,
                    screenX = sx,
                    screenY = sy,
                    scale = focalLength / lz,
                    characterId = bot.characterId,
                    isFriendly = bot.isFriendly,
                    isKnocked = bot.isKnocked,
                    animState = bot.animState,
                    animPhase = bot.animPhase,
                    revealedByRima = revealedByRima
                )
            )
        }

        commands.sortByDescending { it.depthZ }

        // 5. Execute Depth-Sorted Draw Commands
        for (cmd in commands) {
            when (cmd) {
                is BuildingRenderCmd -> draw3DBuilding(cmd)
                is TreeRenderCmd -> draw3DTree(cmd)
                is RockRenderCmd -> draw3DRock(cmd)
                is BillboardRenderCmd -> drawSaniBillboard(cmd, saniBannerBitmap, textMeasurer)
                is VendingRenderCmd -> drawVendingMachine(cmd, textMeasurer)
                is VehicleRenderCmd -> drawVehicle(cmd)
                is GlooWallRenderCmd -> drawGlooWall(cmd)
                is LootRenderCmd -> drawLootItem(cmd, textMeasurer)
                is BotRenderCmd -> drawCombatantBot(cmd)
            }
        }

        // 6. Draw World Safe Zone Boundary Wall (Synchronized with Minimap SafeZone!)
        drawWorldSafeZoneBoundary(
            safeZone = state.safeZone,
            camX = camX,
            camY = camY,
            camZ = camZ,
            sinYaw = sinYaw,
            cosYaw = cosYaw,
            focalLength = focalLength,
            horizonY = horizonY,
            screenW = w,
            screenH = h
        )

        // 7. Draw Bullet Traces & Hit Impact Effects (Sparks + Floating Damage Feedback)
        for (tr in state.bulletTraces) {
            val p1 = projectWorldPoint(tr.startX, tr.startY, tr.startZ)
            val p2 = projectWorldPoint(tr.endX, tr.endY, tr.endZ)
            if (p1 != null && p2 != null) {
                drawLine(
                    color = if (tr.isFriendly) Color(0xFFFFEA00) else Color(0xFFFF5252),
                    start = Offset(p1.first, p1.second),
                    end = Offset(p2.first, p2.second),
                    strokeWidth = 3.2f
                )
            }
        }

        for (ef in state.hitEffects) {
            val lifeRatio = (ef.remainingSec / 0.55f).coerceIn(0f, 1f)
            val floatY = ef.y + (1f - lifeRatio) * 0.85f
            val proj = projectWorldPoint(ef.x, floatY, ef.z) ?: continue
            val (sx, sy, _) = proj
            val isHead = ef.hitZone == HitZone.HEAD
            val sparkColor = if (isHead) Color(0xFFFF1744) else Color(0xFFFFEA00)
            val sparkRadius = (16f * (1.2f - lifeRatio * 0.5f)).coerceIn(6f, 22f)
            drawCircle(
                color = sparkColor.copy(alpha = lifeRatio * 0.55f),
                radius = sparkRadius,
                center = Offset(sx, sy)
            )
            // 4-ray impact spark
            val rayLen = sparkRadius * 1.15f
            drawLine(sparkColor, Offset(sx - rayLen, sy - rayLen), Offset(sx + rayLen, sy + rayLen), strokeWidth = 2.4f)
            drawLine(sparkColor, Offset(sx - rayLen, sy + rayLen), Offset(sx + rayLen, sy - rayLen), strokeWidth = 2.4f)

            val dmgLabel = textMeasurer.measure(
                text = if (isHead) "HEAD -${ef.damage}" else "-${ef.damage}",
                style = TextStyle(
                    color = if (isHead) Color(0xFFFF5252) else Color(0xFFFFEA00),
                    fontSize = if (isHead) 12.sp else 11.sp,
                    fontWeight = FontWeight.Black
                )
            )
            drawText(
                textLayoutResult = dmgLabel,
                topLeft = Offset(sx - dmgLabel.size.width * 0.5f, sy - sparkRadius - dmgLabel.size.height - 2f)
            )
        }

        // 8. Draw Third-Person Player Character or Driven Vehicle in Foreground (when not inside full scope)
        if (!state.isScopedIn && !state.isPlayerDead) {
            if (state.drivingVehicleId != null) {
                drawThirdPersonDrivenVehicle(
                    vehicleName = state.currentDrivingVehicle?.name ?: "SANI Tactical Rover",
                    centerX = w * 0.50f,
                    baseY = h * 0.85f,
                    vehScale = h * 0.22f,
                    steerInput = state.vehicleSteerInput,
                    isBraking = state.vehicleBrakeInput
                )
            } else {
                val charCenterX = if (state.isAiming) w * 0.40f else w * 0.46f
                val charBaseY = h * 0.84f
                drawThirdPersonCharacter(
                    characterId = state.matchCharacter,
                    centerX = charCenterX,
                    baseY = charBaseY,
                    charScale = h * 0.25f,
                    animState = state.playerAnimState,
                    animPhase = state.playerAnimPhase,
                    isAbilityActive = state.abilityState == AbilityState.ACTIVE,
                    weaponCategory = state.activeWeapon?.spec?.category ?: WeaponCategory.SECONDARY,
                    textMeasurer = textMeasurer
                )
            }
        }

        // 9. Draw Third-Person Crosshair OR Scope Optic Overlay
        drawCrosshairAndScopeOverlay(
            state = state,
            screenW = w,
            screenH = h,
            textMeasurer = textMeasurer
        )
    }
}

private fun DrawScope.drawRoadsAndWaterStrips(
    camX: Float,
    camY: Float,
    camZ: Float,
    sinYaw: Float,
    cosYaw: Float,
    focalLength: Float,
    horizonY: Float,
    screenW: Float,
    screenH: Float
) {
    for (xStep in -320..320 step 32) {
        val wx = xStep.toFloat()
        val wz = sin(wx * 0.012f) * 10f
        val dx = wx - camX
        val dz = wz - camZ
        val lz = dx * sinYaw + dz * cosYaw
        if (lz in 3f..220f) {
            val lx = dx * cosYaw - dz * sinYaw
            val scale = focalLength / lz
            val sx = screenW * 0.5f + lx * scale
            val sy = horizonY - (-0.8f - camY) * scale
            val rw = (IslandMapGenerator.RIVER_HALF_WIDTH * 2f * scale).coerceAtMost(screenW)
            val rh = (14f * scale).coerceIn(4f, 65f)
            if (sy in horizonY..screenH) {
                drawOval(
                    color = Color(0xFF0284C7).copy(alpha = 0.72f),
                    topLeft = Offset(sx - rw * 0.5f, sy - rh * 0.5f),
                    size = Size(rw, rh)
                )
            }
        }
    }

    for (zStep in -360..360 step 28) {
        val wx = 0f
        val wz = zStep.toFloat()
        val dx = wx - camX
        val dz = wz - camZ
        val lz = dx * sinYaw + dz * cosYaw
        if (lz in 3f..200f) {
            val lx = dx * cosYaw - dz * sinYaw
            val scale = focalLength / lz
            val sx = screenW * 0.5f + lx * scale
            val gy = IslandMapGenerator.getTerrainHeight(wx, wz)
            val sy = horizonY - (gy - camY) * scale
            val roadW = (14f * scale).coerceIn(6f, screenW * 0.8f)
            val roadH = (12f * scale).coerceIn(4f, 48f)
            if (sy in horizonY..screenH) {
                drawRect(
                    color = Color(0xFF334155),
                    topLeft = Offset(sx - roadW * 0.5f, sy - roadH * 0.5f),
                    size = Size(roadW, roadH)
                )
            }
        }
    }
}

private fun DrawScope.draw3DBuilding(cmd: BuildingRenderCmd) {
    val wPx = (cmd.widthMeters * cmd.scale).coerceIn(10f, size.width * 0.9f)
    val hPx = (cmd.heightMeters * cmd.scale).coerceIn(8f, size.height * 0.8f)
    val left = cmd.screenX - wPx * 0.5f
    val top = cmd.screenY - hPx

    if (cmd.isBridge) {
        drawRoundRect(
            color = Color(0xFF475569),
            topLeft = Offset(left, top),
            size = Size(wPx, hPx.coerceAtLeast(10f)),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawLine(
            color = Color(0xFFFACC15),
            start = Offset(left, top + 2f),
            end = Offset(left + wPx, top + 2f),
            strokeWidth = 2.5f
        )
        return
    }

    val baseColor = Color(cmd.colorHex)
    drawRect(
        color = baseColor,
        topLeft = Offset(left, top),
        size = Size(wPx, hPx)
    )
    drawRect(
        color = if (cmd.isVaultable) Color(0xFF38BDF8) else Color(0xFF0F172A),
        topLeft = Offset(left - 2f, top),
        size = Size(wPx + 4f, (hPx * 0.08f).coerceIn(3f, 10f))
    )
    if (!cmd.isVaultable && wPx > 28f && hPx > 28f) {
        val winW = wPx * 0.18f
        val winH = hPx * 0.18f
        drawRect(
            color = Color(0xFF38BDF8).copy(alpha = 0.45f),
            topLeft = Offset(left + wPx * 0.18f, top + hPx * 0.24f),
            size = Size(winW, winH)
        )
        drawRect(
            color = Color(0xFF38BDF8).copy(alpha = 0.45f),
            topLeft = Offset(left + wPx * 0.64f, top + hPx * 0.24f),
            size = Size(winW, winH)
        )
        drawRect(
            color = Color(0xFF090D16),
            topLeft = Offset(left + wPx * 0.40f, top + hPx * 0.62f),
            size = Size(wPx * 0.20f, hPx * 0.38f)
        )
    }
}

private fun DrawScope.draw3DTree(cmd: TreeRenderCmd) {
    val hPx = (cmd.heightMeters * cmd.scale).coerceIn(12f, size.height * 0.65f)
    val trunkW = (hPx * 0.14f).coerceAtLeast(3f)
    val trunkH = hPx * 0.38f
    drawRect(
        color = Color(0xFF5D4037),
        topLeft = Offset(cmd.screenX - trunkW * 0.5f, cmd.screenY - trunkH),
        size = Size(trunkW, trunkH)
    )
    val canopyRadius = (hPx * 0.36f).coerceAtLeast(6f)
    drawCircle(
        color = Color(0xFF15803D),
        radius = canopyRadius,
        center = Offset(cmd.screenX, cmd.screenY - trunkH - canopyRadius * 0.55f)
    )
    drawCircle(
        color = Color(0xFF22C55E).copy(alpha = 0.65f),
        radius = canopyRadius * 0.72f,
        center = Offset(cmd.screenX - canopyRadius * 0.15f, cmd.screenY - trunkH - canopyRadius * 0.7f)
    )
}

private fun DrawScope.draw3DRock(cmd: RockRenderCmd) {
    val wPx = (cmd.radiusMeters * 2.1f * cmd.scale).coerceIn(10f, 120f)
    val hPx = (cmd.heightMeters * cmd.scale).coerceIn(8f, 95f)
    drawRoundRect(
        color = Color(0xFF475569),
        topLeft = Offset(cmd.screenX - wPx * 0.5f, cmd.screenY - hPx),
        size = Size(wPx, hPx),
        cornerRadius = CornerRadius(wPx * 0.32f, hPx * 0.38f)
    )
    drawRoundRect(
        color = Color(0xFF64748B),
        topLeft = Offset(cmd.screenX - wPx * 0.36f, cmd.screenY - hPx * 0.88f),
        size = Size(wPx * 0.72f, hPx * 0.55f),
        cornerRadius = CornerRadius(wPx * 0.25f, hPx * 0.25f)
    )
}

private fun DrawScope.drawSaniBillboard(
    cmd: BillboardRenderCmd,
    saniBannerBitmap: ImageBitmap,
    textMeasurer: TextMeasurer
) {
    val boardW = (cmd.widthMeters * cmd.scale).coerceIn(28f, size.width * 0.75f)
    val boardH = (cmd.heightMeters * cmd.scale).coerceIn(18f, size.height * 0.50f)
    val pillarH = (3.5f * cmd.scale).coerceIn(8f, 90f)
    val top = cmd.screenY - pillarH - boardH
    val left = cmd.screenX - boardW * 0.5f

    val pillarW = (boardW * 0.06f).coerceAtLeast(3f)
    drawRect(
        color = Color(0xFF334155),
        topLeft = Offset(left + boardW * 0.18f, cmd.screenY - pillarH),
        size = Size(pillarW, pillarH)
    )
    drawRect(
        color = Color(0xFF334155),
        topLeft = Offset(left + boardW * 0.76f, cmd.screenY - pillarH),
        size = Size(pillarW, pillarH)
    )

    drawRoundRect(
        color = Color(0xFF00E5FF),
        topLeft = Offset(left - 3f, top - 3f),
        size = Size(boardW + 6f, boardH + 6f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    drawImage(
        image = saniBannerBitmap,
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize = IntSize(boardW.toInt().coerceAtLeast(1), boardH.toInt().coerceAtLeast(1))
    )

    if (cmd.isLargeBanner && boardW > 70f) {
        val label = textMeasurer.measure(
            text = "SANI ARENA",
            style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        )
        drawText(
            textLayoutResult = label,
            topLeft = Offset(cmd.screenX - label.size.width * 0.5f, top - label.size.height - 4f)
        )
    }
}

private fun DrawScope.drawVendingMachine(cmd: VendingRenderCmd, textMeasurer: TextMeasurer) {
    val wPx = (2.4f * cmd.scale).coerceIn(14f, 110f)
    val hPx = (3.4f * cmd.scale).coerceIn(20f, 150f)
    val left = cmd.screenX - wPx * 0.5f
    val top = cmd.screenY - hPx

    drawRoundRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(left, top),
        size = Size(wPx, hPx),
        cornerRadius = CornerRadius(5f, 5f)
    )
    val accent = if (cmd.remainingRespawns > 0) Color(0xFF00E676) else Color(0xFFEF4444)
    drawRoundRect(
        color = accent,
        topLeft = Offset(left, top),
        size = Size(wPx, hPx),
        cornerRadius = CornerRadius(5f, 5f),
        style = Stroke(width = 2.5f)
    )
    drawRect(
        color = accent.copy(alpha = 0.35f),
        topLeft = Offset(left + wPx * 0.15f, top + hPx * 0.15f),
        size = Size(wPx * 0.70f, hPx * 0.45f)
    )
    if (wPx > 26f) {
        val txt = textMeasurer.measure(
            text = "RESPAWN",
            style = TextStyle(color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
        drawText(txt, topLeft = Offset(cmd.screenX - txt.size.width * 0.5f, top - 14f))
    }
}

private fun DrawScope.drawVehicle(cmd: VehicleRenderCmd) {
    val wPx = (3.8f * cmd.scale).coerceIn(18f, 160f)
    val hPx = (2.1f * cmd.scale).coerceIn(10f, 90f)
    val left = cmd.screenX - wPx * 0.5f
    val top = cmd.screenY - hPx

    val bodyColor = when {
        cmd.name.contains("Buggy", ignoreCase = true) -> Color(0xFFD97706)
        cmd.name.contains("Armored", ignoreCase = true) -> Color(0xFF334155)
        else -> Color(0xFF0284C7)
    }

    // Upper cabin canopy
    drawRoundRect(
        color = Color(0xFF0F172A).copy(alpha = 0.88f),
        topLeft = Offset(left + wPx * 0.18f, top),
        size = Size(wPx * 0.64f, hPx * 0.42f),
        cornerRadius = CornerRadius(5f, 5f)
    )
    // Lower vehicle chassis
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(left, top + hPx * 0.30f),
        size = Size(wPx, hPx * 0.52f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    val wheelR = (hPx * 0.24f).coerceAtLeast(3f)
    drawCircle(Color(0xFF090D16), radius = wheelR, center = Offset(left + wPx * 0.22f, cmd.screenY - wheelR * 0.6f))
    drawCircle(Color(0xFF090D16), radius = wheelR, center = Offset(left + wPx * 0.78f, cmd.screenY - wheelR * 0.6f))
}

private fun DrawScope.drawThirdPersonDrivenVehicle(
    vehicleName: String,
    centerX: Float,
    baseY: Float,
    vehScale: Float,
    steerInput: Float,
    isBraking: Boolean
) {
    val wPx = (vehScale * 1.45f).coerceIn(90f, 260f)
    val hPx = (vehScale * 0.85f).coerceIn(55f, 150f)
    val steerOffsetPx = steerInput * 8f
    val left = centerX - wPx * 0.5f + steerOffsetPx
    val top = baseY - hPx

    val primaryColor = when {
        vehicleName.contains("Buggy", ignoreCase = true) -> Color(0xFFD97706)
        vehicleName.contains("Armored", ignoreCase = true) -> Color(0xFF334155)
        else -> Color(0xFF0284C7)
    }

    // Shadow under vehicle
    drawOval(
        color = Color.Black.copy(alpha = 0.42f),
        topLeft = Offset(left - wPx * 0.06f, baseY - hPx * 0.16f),
        size = Size(wPx * 1.12f, hPx * 0.24f)
    )

    // Rear tires
    val tireW = wPx * 0.16f
    val tireH = hPx * 0.40f
    drawRoundRect(
        color = Color(0xFF090D16),
        topLeft = Offset(left + wPx * 0.04f, baseY - tireH),
        size = Size(tireW, tireH),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = Color(0xFF090D16),
        topLeft = Offset(left + wPx * 0.80f, baseY - tireH),
        size = Size(tireW, tireH),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Cabin / Roll cage
    drawRoundRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(left + wPx * 0.18f, top),
        size = Size(wPx * 0.64f, hPx * 0.50f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    // Rear window
    drawRoundRect(
        color = Color(0xFF38BDF8).copy(alpha = 0.35f),
        topLeft = Offset(left + wPx * 0.24f, top + hPx * 0.08f),
        size = Size(wPx * 0.52f, hPx * 0.28f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Main rear chassis body
    drawRoundRect(
        color = primaryColor,
        topLeft = Offset(left + wPx * 0.08f, top + hPx * 0.38f),
        size = Size(wPx * 0.84f, hPx * 0.44f),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Taillights (glow brighter red when braking)
    val tailColor = if (isBraking) Color(0xFFFF1744) else Color(0xFF991B1B)
    drawRoundRect(
        color = tailColor,
        topLeft = Offset(left + wPx * 0.13f, top + hPx * 0.48f),
        size = Size(wPx * 0.14f, hPx * 0.14f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = tailColor,
        topLeft = Offset(left + wPx * 0.73f, top + hPx * 0.48f),
        size = Size(wPx * 0.14f, hPx * 0.14f),
        cornerRadius = CornerRadius(4f, 4f)
    )
}

private fun DrawScope.drawGlooWall(cmd: GlooWallRenderCmd) {
    val wPx = (cmd.widthMeters * cmd.scale).coerceIn(22f, 240f)
    val hPx = (2.8f * cmd.scale).coerceIn(14f, 140f)
    val left = cmd.screenX - wPx * 0.5f
    val top = cmd.screenY - hPx

    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF00E5FF).copy(alpha = 0.85f), Color(0xFF0284C7).copy(alpha = 0.92f)),
            startY = top,
            endY = cmd.screenY
        ),
        topLeft = Offset(left, top),
        size = Size(wPx, hPx),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.8f),
        topLeft = Offset(left, top),
        size = Size(wPx, hPx),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 2f)
    )
}

private fun DrawScope.drawLootItem(cmd: LootRenderCmd, textMeasurer: TextMeasurer) {
    val r = (0.7f * cmd.scale).coerceIn(5f, 22f)
    val color = when (cmd.category) {
        LootCategory.COIN -> Color(0xFFFFD600)
        LootCategory.GUN -> Color(0xFF00E5FF)
        LootCategory.MEDKIT, LootCategory.REPAIR_KIT -> Color(0xFF00E676)
        LootCategory.ARMOR, LootCategory.HELMET, LootCategory.BACKPACK -> Color(0xFFA855F7)
        LootCategory.GLOO_WALL -> Color(0xFF38BDF8)
        else -> Color(0xFFF97316)
    }

    drawLine(
        color = color.copy(alpha = 0.45f),
        start = Offset(cmd.screenX, cmd.screenY),
        end = Offset(cmd.screenX, cmd.screenY - r * 3.2f),
        strokeWidth = 2f
    )
    drawCircle(
        color = color,
        radius = r,
        center = Offset(cmd.screenX, cmd.screenY - r * 0.5f)
    )
    if (cmd.depthZ < 24f) {
        val txt = textMeasurer.measure(
            text = cmd.label,
            style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        )
        drawText(txt, topLeft = Offset(cmd.screenX - txt.size.width * 0.5f, cmd.screenY - r * 2.4f - txt.size.height))
    }
}

private fun DrawScope.drawCombatantBot(cmd: BotRenderCmd) {
    val postureFactor = when (cmd.animState) {
        CharacterAnimState.PRONE, CharacterAnimState.KNOCK -> 0.45f
        CharacterAnimState.CROUCH -> 0.72f
        else -> 1.0f
    }
    val charH = (1.85f * cmd.scale * postureFactor).coerceIn(12f, 210f)
    val charW = (charH * 0.42f).coerceAtLeast(6f)
    val cx = cmd.screenX
    val footY = cmd.screenY
    val headR = (charW * 0.28f).coerceAtLeast(3f)
    val torsoTop = footY - charH * 0.78f
    val torsoH = charH * 0.42f

    val jacketColor = if (cmd.characterId == CharacterId.SANI) Color(0xFF64748B) else Color(0xFFF8FAFC)
    val pantsColor = if (cmd.characterId == CharacterId.SANI) Color(0xFF3F4E3A) else Color(0xFF18181B)
    val accentColor = if (cmd.characterId == CharacterId.SANI) Color(0xFF22C55E) else Color(0xFFFF4081)

    val legSwing = sin(cmd.animPhase) * (charH * 0.08f)
    drawLine(
        color = pantsColor,
        start = Offset(cx - charW * 0.18f, torsoTop + torsoH),
        end = Offset(cx - charW * 0.20f, footY - legSwing),
        strokeWidth = (charW * 0.22f).coerceAtLeast(2f)
    )
    drawLine(
        color = pantsColor,
        start = Offset(cx + charW * 0.18f, torsoTop + torsoH),
        end = Offset(cx + charW * 0.20f, footY + legSwing),
        strokeWidth = (charW * 0.22f).coerceAtLeast(2f)
    )

    drawRoundRect(
        color = jacketColor,
        topLeft = Offset(cx - charW * 0.42f, torsoTop),
        size = Size(charW * 0.84f, torsoH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRect(
        color = accentColor,
        topLeft = Offset(cx - charW * 0.24f, torsoTop + torsoH * 0.25f),
        size = Size(charW * 0.48f, torsoH * 0.22f)
    )

    drawCircle(
        color = Color(0xFF4E342E),
        radius = headR,
        center = Offset(cx, torsoTop - headR * 0.9f)
    )

    if (cmd.isFriendly) {
        drawCircle(
            color = if (cmd.isKnocked) Color(0xFFFF9100) else Color(0xFF00E5FF),
            radius = 5f,
            center = Offset(cx, torsoTop - headR * 2.4f)
        )
    }

    if (cmd.revealedByRima) {
        val markerY = torsoTop - headR * 2.8f
        val dPath = Path().apply {
            moveTo(cx, markerY - 8f)
            lineTo(cx + 7f, markerY)
            lineTo(cx, markerY + 8f)
            lineTo(cx - 7f, markerY)
            close()
        }
        drawPath(dPath, color = Color(0xFFFF4081))
        drawCircle(
            color = Color(0xFFFF4081).copy(alpha = 0.4f),
            radius = charW * 0.75f,
            center = Offset(cx, footY - charH * 0.5f),
            style = Stroke(width = 2f)
        )
    }
}

private fun DrawScope.drawWorldSafeZoneBoundary(
    safeZone: com.example.model.SafeZoneState,
    camX: Float,
    camY: Float,
    camZ: Float,
    sinYaw: Float,
    cosYaw: Float,
    focalLength: Float,
    horizonY: Float,
    screenW: Float,
    screenH: Float
) {
    val segments = 64
    val radius = safeZone.currentRadius.coerceAtLeast(10f)
    val wallColor = if (safeZone.isShrinking) Color(0xFF00E5FF) else Color(0xFF38BDF8)
    var prevGroundPoint: Offset? = null
    var prevTopPoint: Offset? = null

    for (i in 0..segments) {
        val angle = (i.toDouble() / segments) * 2.0 * PI
        val wx = safeZone.centerX + cos(angle).toFloat() * radius
        val wz = safeZone.centerZ + sin(angle).toFloat() * radius
        val dx = wx - camX
        val dz = wz - camZ
        val lz = dx * sinYaw + dz * cosYaw
        if (lz > 1.2f && lz < 1200f) {
            val lx = dx * cosYaw - dz * sinYaw
            val scale = focalLength / lz
            val sx = screenW * 0.5f + lx * scale
            val groundY = IslandMapGenerator.getTerrainHeight(wx, wz).coerceAtLeast(0f)
            val sy = horizonY - (groundY - camY) * scale
            val wallTop = horizonY - (groundY + 34f - camY) * scale
            val curGround = Offset(sx, sy.coerceIn(-screenH, screenH * 2f))
            val curTop = Offset(sx, wallTop.coerceIn(-screenH, screenH * 2f))

            if (sx in -120f..(screenW + 120f)) {
                drawLine(
                    color = wallColor.copy(alpha = 0.34f),
                    start = Offset(sx, wallTop.coerceIn(0f, screenH)),
                    end = Offset(sx, sy.coerceIn(0f, screenH)),
                    strokeWidth = 3.2f
                )
            }
            if (prevGroundPoint != null && prevTopPoint != null) {
                if (kotlin.math.abs(curGround.x - prevGroundPoint.x) < screenW * 0.9f) {
                    drawLine(
                        color = wallColor.copy(alpha = 0.80f),
                        start = prevGroundPoint,
                        end = curGround,
                        strokeWidth = 3.0f
                    )
                    drawLine(
                        color = wallColor.copy(alpha = 0.45f),
                        start = prevTopPoint,
                        end = curTop,
                        strokeWidth = 2.0f
                    )
                }
            }
            prevGroundPoint = curGround
            prevTopPoint = curTop
        } else {
            prevGroundPoint = null
            prevTopPoint = null
        }
    }

    // Visual screen-edge warning frame when player is outside the Safe Zone
    if (safeZone.isPlayerOutside) {
        drawRect(
            color = Color(0xFFFF1744).copy(alpha = 0.28f),
            topLeft = Offset.Zero,
            size = Size(screenW, screenH),
            style = Stroke(width = 12f)
        )
    }
}

private fun DrawScope.drawThirdPersonCharacter(
    characterId: CharacterId,
    centerX: Float,
    baseY: Float,
    charScale: Float,
    animState: CharacterAnimState,
    animPhase: Float,
    isAbilityActive: Boolean,
    weaponCategory: WeaponCategory,
    textMeasurer: TextMeasurer
) {
    val postureScaleY = when (animState) {
        CharacterAnimState.PRONE, CharacterAnimState.KNOCK, CharacterAnimState.DEATH -> 0.42f
        CharacterAnimState.CROUCH -> 0.72f
        CharacterAnimState.SWIM -> 0.50f
        CharacterAnimState.VAULT, CharacterAnimState.JUMP -> 0.92f
        else -> 1.0f
    }
    val bobOffset = when (animState) {
        CharacterAnimState.SPRINT, CharacterAnimState.ABILITY -> sin(animPhase * 2f) * 6f
        CharacterAnimState.RUN, CharacterAnimState.WALK -> sin(animPhase) * 3.5f
        CharacterAnimState.JUMP, CharacterAnimState.VAULT -> -18f
        else -> 0f
    }

    val totalH = charScale * postureScaleY
    val shoulderW = charScale * 0.36f
    val footY = baseY + bobOffset
    val hipY = footY - totalH * 0.45f
    val shoulderY = footY - totalH * 0.82f
    val headRadius = charScale * 0.105f
    val headCenterY = shoulderY - headRadius * 0.95f

    if (isAbilityActive) {
        if (characterId == CharacterId.SANI) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(centerX, shoulderY + totalH * 0.2f),
                    radius = charScale * 0.65f
                ),
                radius = charScale * 0.65f,
                center = Offset(centerX, shoulderY + totalH * 0.2f)
            )
            for (i in -2..2) {
                val lx = centerX + i * (shoulderW * 0.32f)
                drawLine(
                    color = Color(0xFF18FFFF),
                    start = Offset(lx, footY),
                    end = Offset(lx, shoulderY - 12f),
                    strokeWidth = 2.5f
                )
            }
        } else {
            val pulseRadius = charScale * (0.45f + ((animPhase % PI.toFloat()) / PI.toFloat()) * 0.85f)
            drawCircle(
                color = Color(0xFFFF4081).copy(alpha = 0.65f),
                radius = pulseRadius,
                center = Offset(centerX, hipY),
                style = Stroke(width = 3.5f)
            )
        }
    }

    drawOval(
        color = Color.Black.copy(alpha = 0.38f),
        topLeft = Offset(centerX - shoulderW * 0.65f, baseY - 6f),
        size = Size(shoulderW * 1.3f, 14f)
    )

    val legSwing = if (animState in setOf(CharacterAnimState.WALK, CharacterAnimState.RUN, CharacterAnimState.SPRINT, CharacterAnimState.ABILITY)) {
        sin(animPhase) * (charScale * 0.08f)
    } else 0f

    if (characterId == CharacterId.SANI) {
        val pantsColor = Color(0xFF3E4A3B)
        val bootColor = Color(0xFF27272A)
        val legW = shoulderW * 0.28f

        drawRoundRect(
            color = pantsColor,
            topLeft = Offset(centerX - shoulderW * 0.34f, hipY),
            size = Size(legW, (footY - hipY - legSwing).coerceAtLeast(8f)),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = pantsColor,
            topLeft = Offset(centerX + shoulderW * 0.06f, hipY),
            size = Size(legW, (footY - hipY + legSwing).coerceAtLeast(8f)),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(centerX + shoulderW * 0.30f, hipY + (footY - hipY) * 0.18f),
            size = Size(legW * 0.35f, (footY - hipY) * 0.28f)
        )
        drawRoundRect(
            color = bootColor,
            topLeft = Offset(centerX - shoulderW * 0.35f, footY - legSwing - 10f),
            size = Size(legW * 1.06f, 11f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = bootColor,
            topLeft = Offset(centerX + shoulderW * 0.05f, footY + legSwing - 10f),
            size = Size(legW * 1.06f, 11f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        val torsoH = (hipY - shoulderY).coerceAtLeast(12f)
        val armW = shoulderW * 0.22f
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(centerX - shoulderW * 0.62f, shoulderY + 2f),
            size = Size(armW, torsoH * 0.88f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(centerX + shoulderW * 0.40f, shoulderY + 2f),
            size = Size(armW, torsoH * 0.88f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = Color(0xFF475569),
            topLeft = Offset(centerX - shoulderW * 0.44f, shoulderY),
            size = Size(shoulderW * 0.88f, torsoH),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(centerX - shoulderW * 0.44f, hipY - 5f),
            size = Size(shoulderW * 0.88f, 5f)
        )
        val backText = textMeasurer.measure(
            text = "SANI",
            style = TextStyle(
                color = Color(0xFF22C55E),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        )
        drawText(
            textLayoutResult = backText,
            topLeft = Offset(centerX - backText.size.width * 0.5f, shoulderY + torsoH * 0.22f)
        )

        drawCircle(
            color = Color(0xFF4E342E),
            radius = headRadius,
            center = Offset(centerX, headCenterY)
        )
    } else {
        val pantsColor = Color(0xFF18181B)
        val legW = shoulderW * 0.27f

        drawRoundRect(
            color = pantsColor,
            topLeft = Offset(centerX - shoulderW * 0.33f, hipY),
            size = Size(legW, (footY - hipY - legSwing).coerceAtLeast(8f)),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = pantsColor,
            topLeft = Offset(centerX + shoulderW * 0.06f, hipY),
            size = Size(legW, (footY - hipY + legSwing).coerceAtLeast(8f)),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawLine(
            color = Color(0xFFFF4081),
            start = Offset(centerX - shoulderW * 0.26f, hipY),
            end = Offset(centerX - shoulderW * 0.26f, hipY + (footY - hipY) * 0.35f),
            strokeWidth = 2.5f
        )
        drawLine(
            color = Color(0xFFFF4081),
            start = Offset(centerX + shoulderW * 0.22f, hipY),
            end = Offset(centerX + shoulderW * 0.22f, hipY + (footY - hipY) * 0.35f),
            strokeWidth = 2.5f
        )
        drawRoundRect(
            color = Color(0xFFFBCFE8),
            topLeft = Offset(centerX - shoulderW * 0.34f, footY - legSwing - 10f),
            size = Size(legW * 1.05f, 11f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = Color(0xFFFBCFE8),
            topLeft = Offset(centerX + shoulderW * 0.05f, footY + legSwing - 10f),
            size = Size(legW * 1.05f, 11f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        val torsoH = (hipY - shoulderY).coerceAtLeast(12f)
        val armW = shoulderW * 0.21f
        drawRoundRect(
            color = Color(0xFF059669),
            topLeft = Offset(centerX - shoulderW * 0.60f, shoulderY + 2f),
            size = Size(armW, torsoH * 0.85f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = Color(0xFF059669),
            topLeft = Offset(centerX + shoulderW * 0.39f, shoulderY + 2f),
            size = Size(armW, torsoH * 0.85f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = Color(0xFFF8FAFC),
            topLeft = Offset(centerX - shoulderW * 0.42f, shoulderY),
            size = Size(shoulderW * 0.84f, torsoH * 0.90f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = Color(0xFF18181B),
            topLeft = Offset(centerX - shoulderW * 0.32f, shoulderY + torsoH * 0.10f),
            size = Size(shoulderW * 0.64f, torsoH * 0.76f),
            cornerRadius = CornerRadius(5f, 5f)
        )
        val rimaText = textMeasurer.measure(
            text = "RIMA",
            style = TextStyle(
                color = Color(0xFFFF4081),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        )
        drawText(
            textLayoutResult = rimaText,
            topLeft = Offset(centerX - rimaText.size.width * 0.5f, shoulderY + torsoH * 0.22f)
        )

        drawCircle(
            color = Color(0xFF4E342E),
            radius = headRadius,
            center = Offset(centerX, headCenterY)
        )
        drawArc(
            color = Color.White,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(centerX - headRadius, headCenterY - headRadius),
            size = Size(headRadius * 2f, headRadius * 2f),
            style = Stroke(width = 3f)
        )
    }

    // Held weapon model silhouette scaled by active weapon category
    val gunY = shoulderY + (hipY - shoulderY) * 0.32f
    val barrelLenFactor = when (weaponCategory) {
        WeaponCategory.SNIPER, WeaponCategory.DMR -> 0.86f
        WeaponCategory.ASSAULT_RIFLE, WeaponCategory.SHOTGUN -> 0.72f
        WeaponCategory.SMG -> 0.58f
        WeaponCategory.SECONDARY -> 0.45f
    }
    drawLine(
        color = Color(0xFF0F172A),
        start = Offset(centerX + shoulderW * 0.35f, gunY),
        end = Offset(centerX + shoulderW * barrelLenFactor, gunY - 14f),
        strokeWidth = if (weaponCategory == WeaponCategory.SHOTGUN) 7.5f else 6f
    )
    if (animState == CharacterAnimState.SHOOT) {
        drawCircle(
            color = Color(0xFFFFEA00),
            radius = 9f,
            center = Offset(centerX + shoulderW * (barrelLenFactor + 0.04f), gunY - 16f)
        )
    }
}

private fun DrawScope.drawCrosshairAndScopeOverlay(
    state: MatchUiState,
    screenW: Float,
    screenH: Float,
    textMeasurer: TextMeasurer
) {
    val cx = screenW * 0.5f
    val cy = screenH * 0.44f
    val activeWep = state.activeWeapon
    val activeScope = activeWep?.equippedScope ?: ScopeType.NONE

    if (state.isScopedIn) {
        val scopeRadius = min(screenW, screenH) * 0.36f
        drawCircle(
            color = Color.Black.copy(alpha = 0.66f),
            radius = scopeRadius * 1.45f,
            center = Offset(cx, cy),
            style = Stroke(width = scopeRadius * 0.9f)
        )
        when (activeScope) {
            ScopeType.RED_DOT -> {
                // Original Red Dot Reflex Sight: Cyan reflex housing with glowing crimson dot & side brackets
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = scopeRadius * 0.85f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.2f)
                )
                val bGap = scopeRadius * 0.22f
                drawLine(Color(0xFFFF1744), Offset(cx - bGap - 14f, cy), Offset(cx - bGap, cy), strokeWidth = 2.2f)
                drawLine(Color(0xFFFF1744), Offset(cx + bGap, cy), Offset(cx + bGap + 14f, cy), strokeWidth = 2.2f)
                drawCircle(
                    color = Color(0xFFFF1744),
                    radius = 5.0f,
                    center = Offset(cx, cy)
                )
            }
            ScopeType.SCOPE_2X -> {
                // Original 2x Tactical Holo Sight: Dual concentric rings + emerald chevron reticle
                drawCircle(
                    color = Color(0xFF00E676),
                    radius = scopeRadius * 0.92f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = 0.45f),
                    radius = scopeRadius * 0.35f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.6f)
                )
                drawLine(Color(0xFF00E676), Offset(cx, cy), Offset(cx - 9f, cy + 11f), strokeWidth = 2.5f)
                drawLine(Color(0xFF00E676), Offset(cx, cy), Offset(cx + 9f, cy + 11f), strokeWidth = 2.5f)
                drawCircle(Color(0xFF00E676), radius = 3f, center = Offset(cx, cy))
            }
            ScopeType.SCOPE_4X -> {
                // Original 4x ACOG-Style Combat Optic: Crosshair with Bullet-Drop Compensator (BDC) hashmarks
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = scopeRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.2f)
                )
                drawLine(Color(0xFF00E5FF).copy(alpha = 0.75f), Offset(cx - scopeRadius, cy), Offset(cx + scopeRadius, cy), strokeWidth = 1.6f)
                drawLine(Color(0xFF00E5FF).copy(alpha = 0.75f), Offset(cx, cy - scopeRadius), Offset(cx, cy + scopeRadius), strokeWidth = 1.6f)
                for (step in 1..3) {
                    val dropY = cy + step * (scopeRadius * 0.16f)
                    val tickHalfW = (14f - step * 3f).coerceAtLeast(4f)
                    drawLine(Color(0xFF00E676), Offset(cx - tickHalfW, dropY), Offset(cx + tickHalfW, dropY), strokeWidth = 2f)
                }
                drawCircle(Color(0xFF00E676), radius = 4f, center = Offset(cx, cy))
            }
            ScopeType.SNIPER_8X, ScopeType.NONE -> {
                // Original Sniper Scope (6.5x/8x): Full Mil-Dot Precision Reticle with windage & elevation dots
                drawCircle(
                    color = Color(0xFF38BDF8),
                    radius = scopeRadius * 1.04f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.5f)
                )
                drawLine(Color(0xFF38BDF8).copy(alpha = 0.85f), Offset(cx - scopeRadius, cy), Offset(cx + scopeRadius, cy), strokeWidth = 1.5f)
                drawLine(Color(0xFF38BDF8).copy(alpha = 0.85f), Offset(cx, cy - scopeRadius), Offset(cx, cy + scopeRadius), strokeWidth = 1.5f)
                for (step in -3..3) {
                    if (step == 0) continue
                    val offsetPx = step * (scopeRadius * 0.20f)
                    drawCircle(Color(0xFF00E5FF), radius = 2.5f, center = Offset(cx + offsetPx, cy))
                    drawCircle(Color(0xFF00E5FF), radius = 2.5f, center = Offset(cx, cy + offsetPx))
                }
                drawCircle(Color(0xFFFF1744), radius = 3.8f, center = Offset(cx, cy))
            }
        }
        val scopeLabel = textMeasurer.measure(
            text = "${activeScope.label.uppercase()} (${activeScope.zoomFactor}x)",
            style = TextStyle(color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
        drawText(scopeLabel, topLeft = Offset(cx - scopeLabel.size.width * 0.5f, cy + scopeRadius + 8f))
    } else {
        val gap = if (state.isAiming) 7f else if (state.isSprinting) 16f else 11f
        val len = 10f
        val col = if (state.matchCharacter == CharacterId.SANI && state.abilityState == AbilityState.ACTIVE) {
            Color(0xFFFF9100)
        } else {
            Color.White.copy(alpha = 0.9f)
        }
        // Shotgun circular spread reticle vs standard 4-tick crosshair
        if (activeWep?.spec?.category == WeaponCategory.SHOTGUN) {
            drawCircle(
                color = col,
                radius = gap + 10f,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )
        }
        drawLine(col, Offset(cx - gap - len, cy), Offset(cx - gap, cy), strokeWidth = 2.2f)
        drawLine(col, Offset(cx + gap, cy), Offset(cx + gap + len, cy), strokeWidth = 2.2f)
        drawLine(col, Offset(cx, cy - gap - len), Offset(cx, cy - gap), strokeWidth = 2.2f)
        drawLine(col, Offset(cx, cy + gap), Offset(cx, cy + gap + len), strokeWidth = 2.2f)
        drawCircle(if (state.isAiming) Color(0xFF00E5FF) else col, radius = if (state.isAiming) 2.8f else 2f, center = Offset(cx, cy))
        if (state.isAiming) {
            val br = gap + len + 6f
            drawArc(
                color = Color(0xFF00E5FF).copy(alpha = 0.72f),
                startAngle = 150f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(cx - br, cy - br),
                size = Size(br * 2f, br * 2f),
                style = Stroke(width = 2f)
            )
            drawArc(
                color = Color(0xFF00E5FF).copy(alpha = 0.72f),
                startAngle = -30f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(cx - br, cy - br),
                size = Size(br * 2f, br * 2f),
                style = Stroke(width = 2f)
            )
        }
    }

    // Center Hitmarker "X" when a shot hits an enemy
    if (state.hitMarkerTimerSec > 0f) {
        val isHead = state.lastHitZone == HitZone.HEAD
        val hitCol = if (isHead) Color(0xFFFF1744) else Color(0xFFFFEA00)
        val inner = 6f
        val outer = if (isHead) 18f else 14f
        val sw = if (isHead) 3.2f else 2.5f
        drawLine(hitCol, Offset(cx - outer, cy - outer), Offset(cx - inner, cy - inner), strokeWidth = sw)
        drawLine(hitCol, Offset(cx + inner, cy - inner), Offset(cx + outer, cy - outer), strokeWidth = sw)
        drawLine(hitCol, Offset(cx - outer, cy + outer), Offset(cx - inner, cy + inner), strokeWidth = sw)
        drawLine(hitCol, Offset(cx + inner, cy + inner), Offset(cx + outer, cy + outer), strokeWidth = sw)
    }
}
