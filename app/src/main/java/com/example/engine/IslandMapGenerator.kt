package com.example.engine

import com.example.model.AmmoType
import com.example.model.AttachmentType
import com.example.model.BuildingType
import com.example.model.CombatantBot
import com.example.model.LootCategory
import com.example.model.MapBillboard
import com.example.model.MapBuilding
import com.example.model.MapRock
import com.example.model.MapTree
import com.example.model.MapVehicle
import com.example.model.OriginalWeaponCatalog
import com.example.model.ScopeType
import com.example.model.VendingMachineNode
import com.example.model.WorldLootItem
import com.example.model.deployedGlooWall
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

object IslandMapGenerator {
    const val PLAYABLE_LIMIT = 530f
    const val RIVER_HALF_WIDTH = 28f
    const val VEHICLE_COLLISION_RADIUS = 1.65f
    const val VEHICLE_BOUNDARY_LIMIT = PLAYABLE_LIMIT - 4.0f

    data class NamedZone(val name: String, val x: Float, val z: Float)

    val namedZones = listOf(
        NamedZone("SANI CITADEL", 0f, -150f),
        NamedZone("TECH PLAZA", 190f, -160f),
        NamedZone("HARBOR DOCKS", -220f, 170f),
        NamedZone("EMERALD RIDGE", 210f, 180f),
        NamedZone("SOLAR OUTPOST", -190f, -170f),
        NamedZone("RIVER CROSSING", 0f, 95f)
    )

    val bridgeXPositions = listOf(-220f, 0f, 220f)

    val buildings: List<MapBuilding> by lazy { buildStaticStructures() }
    val trees: List<MapTree> by lazy { buildStaticTrees() }
    val rocks: List<MapRock> by lazy { buildStaticRocks() }
    val billboards: List<MapBillboard> by lazy { buildSaniBillboards() }

    fun buildInitialVendingMachines(): List<VendingMachineNode> = listOf(
        VendingMachineNode(1, "SANI Citadel Plaza", 18f, -132f),
        VendingMachineNode(2, "Tech Plaza East", 174f, -142f),
        VendingMachineNode(3, "Harbor Docks Depot", -202f, 152f),
        VendingMachineNode(4, "Emerald Ridge Camp", 194f, 162f),
        VendingMachineNode(5, "Solar Outpost Hub", -174f, -152f),
        VendingMachineNode(6, "River Crossing South", 16f, 84f)
    )

    fun buildInitialVehicles(): List<MapVehicle> {
        val rawTemplates = listOf(
            MapVehicle(1, "SANI Tactical Rover", 32f, -95f, 0f, maxSpeed = 28f),
            MapVehicle(2, "Sand Buggy X2", -140f, -85f, 90f, maxSpeed = 30f),
            MapVehicle(3, "SANI Tactical Rover", 150f, -90f, 180f, maxSpeed = 28f),
            MapVehicle(4, "Armored Transport", -45f, 75f, 0f, maxSpeed = 24f),
            MapVehicle(5, "Sand Buggy X2", 165f, 110f, 270f, maxSpeed = 30f),
            MapVehicle(6, "SANI Tactical Rover", -180f, 115f, 45f, maxSpeed = 28f)
        )
        val spawned = ArrayList<MapVehicle>(rawTemplates.size)
        val seenIds = HashSet<Int>(rawTemplates.size)
        for (tpl in rawTemplates) {
            if (!seenIds.add(tpl.id)) continue
            val (validX, validZ) = findValidVehicleSpawnPosition(tpl.x, tpl.z, spawned)
            spawned.add(tpl.copy(x = validX, z = validZ, occupantId = null))
        }
        return spawned
    }

    private fun buildStaticStructures(): List<MapBuilding> {
        val list = mutableListOf<MapBuilding>()
        var nextId = 1

        // 3 Bridges across the central river (Z in -36..36)
        for (bx in bridgeXPositions) {
            list.add(
                MapBuilding(
                    id = nextId++,
                    name = "Bridge Deck",
                    type = BuildingType.BRIDGE_DECK,
                    centerX = bx,
                    centerZ = 0f,
                    width = 18f,
                    depth = 72f,
                    height = 1.6f,
                    floorY = 0.8f,
                    isSolidCollider = false,
                    isVaultable = false,
                    colorHex = 0xFF475569
                )
            )
        }

        // Major buildings, houses, warehouses, and low vaultable walls around each NamedZone
        for (zone in namedZones) {
            val zx = zone.x
            val zz = zone.z
            list.add(
                MapBuilding(
                    id = nextId++,
                    name = "${zone.name} Hall",
                    type = BuildingType.TECH_TOWER,
                    centerX = zx,
                    centerZ = zz,
                    width = 18f,
                    depth = 14f,
                    height = 11f,
                    floorY = getGroundTerrainHeight(zx, zz),
                    isSolidCollider = true,
                    isVaultable = false,
                    colorHex = 0xFF1E293B
                )
            )
            val offsets = listOf(-32f to -22f, 34f to -18f, -26f to 28f, 30f to 26f)
            offsets.forEachIndexed { idx, (ox, oz) ->
                val hx = zx + ox
                val hz = zz + oz
                list.add(
                    MapBuilding(
                        id = nextId++,
                        name = "House ${idx + 1}",
                        type = if (idx % 2 == 0) BuildingType.HOUSE else BuildingType.WAREHOUSE,
                        centerX = hx,
                        centerZ = hz,
                        width = 11f,
                        depth = 10f,
                        height = 5.8f,
                        floorY = getGroundTerrainHeight(hx, hz),
                        isSolidCollider = true,
                        isVaultable = false,
                        colorHex = if (idx % 2 == 0) 0xFF334155 else 0xFF3F3F46
                    )
                )
            }
            val coverOffsets = listOf(-15f to 8f, 15f to 8f, 0f to 20f, -14f to -16f)
            coverOffsets.forEach { (cx, cz) ->
                val wx = zx + cx
                val wz = zz + cz
                list.add(
                    MapBuilding(
                        id = nextId++,
                        name = "Low Tactical Wall",
                        type = BuildingType.LOW_WALL,
                        centerX = wx,
                        centerZ = wz,
                        width = 6.5f,
                        depth = 1.6f,
                        height = 1.3f,
                        floorY = getGroundTerrainHeight(wx, wz),
                        isSolidCollider = true,
                        isVaultable = true,
                        colorHex = 0xFF64748B
                    )
                )
            }
        }
        return list
    }

    private fun buildStaticTrees(): List<MapTree> {
        val rng = Random(2026)
        val result = mutableListOf<MapTree>()
        var id = 1
        for (i in 0 until 125) {
            val x = rng.nextFloat() * 900f - 450f
            val z = rng.nextFloat() * 900f - 450f
            if (isPointInWater(x, z)) continue
            if (isOnRoad(x, z)) continue
            if (buildings.any { hypot(x - it.centerX, z - it.centerZ) < 16f }) continue
            if (hypot(x - 0f, z - (-125f)) < 14f) continue // Keep spawn clear
            result.add(
                MapTree(
                    id = id++,
                    x = x,
                    z = z,
                    radius = 0.95f,
                    height = 6.5f + (i % 4) * 1.1f
                )
            )
        }
        return result
    }

    private fun buildStaticRocks(): List<MapRock> {
        val rng = Random(7070)
        val result = mutableListOf<MapRock>()
        var id = 1
        for (i in 0 until 55) {
            val x = rng.nextFloat() * 860f - 430f
            val z = rng.nextFloat() * 860f - 430f
            if (isPointInWater(x, z)) continue
            if (isOnRoad(x, z)) continue
            if (buildings.any { hypot(x - it.centerX, z - it.centerZ) < 15f }) continue
            if (trees.any { hypot(x - it.x, z - it.z) < 5f }) continue
            if (hypot(x - 0f, z - (-125f)) < 14f) continue
            result.add(
                MapRock(
                    id = id++,
                    x = x,
                    z = z,
                    radius = 1.45f + (i % 3) * 0.35f,
                    height = 1.9f + (i % 3) * 0.45f
                )
            )
        }
        return result
    }

    private fun buildSaniBillboards(): List<MapBillboard> = listOf(
        MapBillboard(1, 0f, -112f, 0f, width = 16f, height = 9f, isLargeBanner = true),
        MapBillboard(2, 26f, -38f, 15f, width = 14f, height = 8f, isLargeBanner = false),
        MapBillboard(3, -26f, 42f, 180f, width = 14f, height = 8f, isLargeBanner = false),
        MapBillboard(4, 165f, -125f, -35f, width = 16f, height = 9f, isLargeBanner = true),
        MapBillboard(5, -185f, 130f, 40f, width = 15f, height = 8.5f, isLargeBanner = true),
        MapBillboard(6, 180f, 140f, 210f, width = 15f, height = 8.5f, isLargeBanner = false),
        MapBillboard(7, -165f, -135f, 25f, width = 15f, height = 8.5f, isLargeBanner = false),
        MapBillboard(8, 0f, 125f, 180f, width = 16f, height = 9f, isLargeBanner = true)
    )

    fun isOnBridge(x: Float, z: Float): Boolean {
        if (abs(z) > 36f) return false
        return bridgeXPositions.any { bx -> abs(x - bx) < 9f }
    }

    fun isOnRoad(x: Float, z: Float): Boolean {
        val mainNorthSouth = abs(x) < 8f
        val westNorthSouth = abs(x + 220f) < 7f && abs(z) < 240f
        val eastNorthSouth = abs(x - 220f) < 7f && abs(z) < 240f
        val northHighway = abs(z + 150f) < 7.5f && abs(x) < 260f
        val southHighway = abs(z - 150f) < 7.5f && abs(x) < 260f
        return mainNorthSouth || westNorthSouth || eastNorthSouth || northHighway || southHighway
    }

    fun isPointInWater(x: Float, z: Float): Boolean {
        if (isOnBridge(x, z)) return false
        val riverCenterZ = sin(x * 0.012f) * 10f
        val inRiver = abs(z - riverCenterZ) < RIVER_HALF_WIDTH
        val inOuterCoast = hypot(x, z) > 495f
        return inRiver || inOuterCoast
    }

    private fun getGroundTerrainHeight(x: Float, z: Float): Float {
        val riverCenterZ = sin(x * 0.012f) * 10f
        val distToRiver = abs(z - riverCenterZ)
        if (distToRiver < RIVER_HALF_WIDTH) {
            return -1.4f
        }
        val hill1 = max(0f, 1f - hypot(x - 120f, z - 90f) / 110f) * 9.5f
        val hill2 = max(0f, 1f - hypot(x + 140f, z + 110f) / 120f) * 11.0f
        val hill3 = max(0f, 1f - hypot(x - 210f, z - 180f) / 95f) * 8.0f
        val gentleUndulation = (sin(x * 0.025f) * cos(z * 0.025f) + 1f) * 0.8f
        return hill1 + hill2 + hill3 + gentleUndulation
    }

    fun getTerrainHeight(x: Float, z: Float): Float {
        val cx = x.coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT)
        val cz = z.coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT)
        if (isOnBridge(cx, cz)) return 1.8f
        if (isPointInWater(cx, cz)) return -0.9f
        val h = getGroundTerrainHeight(cx, cz)
        return if (h.isNaN() || h.isInfinite()) 0f else h.coerceIn(-1.4f, 35f)
    }

    /**
     * Checks if a point is inside a solid building/house/low-wall.
     */
    fun findCollidingBuilding(
        x: Float,
        z: Float,
        radius: Float = 0.85f,
        ignoreVaultable: Boolean = false
    ): MapBuilding? {
        for (b in buildings) {
            if (!b.isSolidCollider) continue
            if (ignoreVaultable && b.isVaultable) continue
            val halfW = b.width * 0.5f + radius
            val halfD = b.depth * 0.5f + radius
            if (abs(x - b.centerX) < halfW && abs(z - b.centerZ) < halfD) {
                return b
            }
        }
        return null
    }

    /**
     * Simplified circular collision for trees and rocks (fast for low-end devices).
     */
    fun collidesWithTreeOrRock(x: Float, z: Float, radius: Float = 0.65f): Boolean {
        for (r in rocks) {
            val minDist = r.radius + radius
            if (abs(x - r.x) < minDist && abs(z - r.z) < minDist && hypot(x - r.x, z - r.z) < minDist) {
                return true
            }
        }
        for (t in trees) {
            val minDist = t.radius + radius * 0.7f
            if (abs(x - t.x) < minDist && abs(z - t.z) < minDist && hypot(x - t.x, z - t.z) < minDist) {
                return true
            }
        }
        return false
    }

    fun collidesWithGlooWall(x: Float, z: Float, glooWalls: List<deployedGlooWall>, radius: Float = 0.85f): Boolean {
        for (gw in glooWalls) {
            if (abs(x - gw.x) < 2.8f && abs(z - gw.z) < 2.8f &&
                hypot(x - gw.x, z - gw.z) < (gw.width * 0.5f + radius)
            ) {
                return true
            }
        }
        return false
    }

    fun isPositionBlocked(
        x: Float,
        z: Float,
        ignoreVaultable: Boolean,
        glooWalls: List<deployedGlooWall>,
        radius: Float = 0.80f
    ): Boolean {
        if (abs(x) > PLAYABLE_LIMIT || abs(z) > PLAYABLE_LIMIT) return true
        if (findCollidingBuilding(x, z, radius = radius, ignoreVaultable = ignoreVaultable) != null) return true
        if (collidesWithTreeOrRock(x, z, radius = radius * 0.75f)) return true
        if (collidesWithGlooWall(x, z, glooWalls, radius = radius)) return true
        return false
    }

    /**
     * Resolves movement with substepped anti-tunneling and smooth wall/rock/tree sliding collision.
     * Prevents walking through walls even during SANI speed boost or low-end frame spikes.
     */
    fun resolveMovement(
        oldX: Float,
        oldZ: Float,
        desiredX: Float,
        desiredZ: Float,
        isVaulting: Boolean,
        glooWalls: List<deployedGlooWall>
    ): Pair<Float, Float> {
        val (safeOldX, safeOldZ) = if (isPositionBlocked(
                oldX.coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT),
                oldZ.coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT),
                ignoreVaultable = isVaulting,
                glooWalls = glooWalls
            )
        ) {
            recoverIfStuck(oldX, oldZ, glooWalls)
        } else {
            oldX.coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT) to oldZ.coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT)
        }
        val targetX = desiredX.coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT)
        val targetZ = desiredZ.coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT)

        val totalDist = hypot(targetX - safeOldX, targetZ - safeOldZ)
        if (totalDist < 0.0001f) return safeOldX to safeOldZ

        val steps = (totalDist / 0.35f).toInt().coerceIn(1, 96)
        val stepDx = (targetX - safeOldX) / steps
        val stepDz = (targetZ - safeOldZ) / steps

        var curX = safeOldX
        var curZ = safeOldZ

        for (i in 0 until steps) {
            val nextX = (curX + stepDx).coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT)
            val nextZ = (curZ + stepDz).coerceIn(-PLAYABLE_LIMIT, PLAYABLE_LIMIT)

            if (!isPositionBlocked(nextX, nextZ, ignoreVaultable = isVaulting, glooWalls = glooWalls)) {
                curX = nextX
                curZ = nextZ
                continue
            }

            // Slide along X axis if X movement is non-zero
            val canSlideX = abs(stepDx) > 0.0001f &&
                !isPositionBlocked(nextX, curZ, ignoreVaultable = isVaulting, glooWalls = glooWalls)
            // Slide along Z axis if Z movement is non-zero
            val canSlideZ = abs(stepDz) > 0.0001f &&
                !isPositionBlocked(curX, nextZ, ignoreVaultable = isVaulting, glooWalls = glooWalls)

            when {
                canSlideX && (!canSlideZ || abs(stepDx) >= abs(stepDz)) -> {
                    curX = nextX
                }
                canSlideZ -> {
                    curZ = nextZ
                }
                else -> {
                    break
                }
            }
        }

        return curX to curZ
    }

    /**
     * Safe Anti-Stuck System:
     * Only moves the character if their current position is actually invalid/overlapping a solid collider,
     * nudging them a very small distance to the nearest valid position inside the map.
     */
    fun recoverIfStuck(x: Float, z: Float, glooWalls: List<deployedGlooWall> = emptyList()): Pair<Float, Float> {
        val clampedX = x.coerceIn(-PLAYABLE_LIMIT + 2f, PLAYABLE_LIMIT - 2f)
        val clampedZ = z.coerceIn(-PLAYABLE_LIMIT + 2f, PLAYABLE_LIMIT - 2f)

        // If position is already valid, return immediately without touching coordinates!
        if (!isPositionBlocked(clampedX, clampedZ, ignoreVaultable = false, glooWalls = glooWalls, radius = 0.70f)) {
            return clampedX to clampedZ
        }

        // First check if inside a building AABB and push minimally outside nearest face
        val collider = findCollidingBuilding(clampedX, clampedZ, radius = 0.75f, ignoreVaultable = false)
        if (collider != null) {
            val dx = clampedX - collider.centerX
            val dz = clampedZ - collider.centerZ
            val halfW = collider.width * 0.5f + 1.25f
            val halfD = collider.depth * 0.5f + 1.25f
            val candX = (collider.centerX + (if (dx >= 0f) halfW else -halfW)).coerceIn(-PLAYABLE_LIMIT + 2f, PLAYABLE_LIMIT - 2f)
            val candZ = (collider.centerZ + (if (dz >= 0f) halfD else -halfD)).coerceIn(-PLAYABLE_LIMIT + 2f, PLAYABLE_LIMIT - 2f)
            if (abs(dx / halfW) > abs(dz / halfD)) {
                if (!isPositionBlocked(candX, clampedZ, false, glooWalls, 0.70f)) return candX to clampedZ
            } else {
                if (!isPositionBlocked(clampedX, candZ, false, glooWalls, 0.70f)) return clampedX to candZ
            }
        }

        // Spiral search in small radial increments (1.5m .. 9.0m) for nearest valid non-blocked position
        val radii = floatArrayOf(1.5f, 2.8f, 4.2f, 6.0f, 8.5f)
        for (r in radii) {
            for (angleIdx in 0 until 12) {
                val rad = (angleIdx * (2.0 * PI / 12.0))
                val nx = (clampedX + cos(rad).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 2f, PLAYABLE_LIMIT - 2f)
                val nz = (clampedZ + sin(rad).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 2f, PLAYABLE_LIMIT - 2f)
                if (!isPositionBlocked(nx, nz, ignoreVaultable = false, glooWalls = glooWalls, radius = 0.70f)) {
                    return nx to nz
                }
            }
        }

        return clampedX to clampedZ
    }

    /**
     * Finds a safe respawn position near a Vending Machine that is strictly NOT inside walls,
     * rocks, trees, water, or near living enemies.
     */
    fun findSafeRespawnPosition(
        vmX: Float,
        vmZ: Float,
        bots: List<CombatantBot>,
        glooWalls: List<deployedGlooWall>
    ): Pair<Float, Float> {
        val radii = floatArrayOf(4.0f, 6.5f, 9.0f, 12.0f)
        for (r in radii) {
            for (angleIdx in 0 until 12) {
                val rad = angleIdx * (2.0 * PI / 12.0)
                val cx = (vmX + cos(rad).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 8f, PLAYABLE_LIMIT - 8f)
                val cz = (vmZ + sin(rad).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 8f, PLAYABLE_LIMIT - 8f)
                if (isPointInWater(cx, cz)) continue
                if (isPositionBlocked(cx, cz, ignoreVaultable = false, glooWalls = glooWalls, radius = 1.0f)) continue
                val nearEnemy = bots.any { !it.isFriendly && !it.isDead && hypot(it.x - cx, it.z - cz) < 10f }
                if (!nearEnemy) {
                    return cx to cz
                }
            }
        }
        return recoverIfStuck(vmX + 4f, vmZ + 4f, glooWalls)
    }

    /**
     * Checks whether a vehicle center (x, z) collides with map boundaries, coastline water,
     * solid buildings, low walls, rocks, trees, Gloo Walls, Vending Machines, Billboards, or other vehicles.
     * Bridges over the central river are drivable because isPointInWater returns false on bridges.
     */
    fun isVehiclePositionBlocked(
        x: Float,
        z: Float,
        glooWalls: List<deployedGlooWall> = emptyList(),
        otherVehicles: List<MapVehicle> = emptyList(),
        ignoreVehicleId: Int? = null,
        radius: Float = VEHICLE_COLLISION_RADIUS
    ): Boolean {
        if (x.isNaN() || z.isNaN() || x.isInfinite() || z.isInfinite()) return true
        if (abs(x) > VEHICLE_BOUNDARY_LIMIT || abs(z) > VEHICLE_BOUNDARY_LIMIT) return true
        if (hypot(x, z) > 488f) return true
        if (isPointInWater(x, z)) return true
        if (findCollidingBuilding(x, z, radius = radius, ignoreVaultable = false) != null) return true
        if (collidesWithTreeOrRock(x, z, radius = radius * 0.82f)) return true
        if (collidesWithGlooWall(x, z, glooWalls, radius = radius)) return true
        for (vm in staticVendingMachines) {
            val minDist = radius + 0.95f
            if (abs(x - vm.x) < minDist && abs(z - vm.z) < minDist && hypot(x - vm.x, z - vm.z) < minDist) {
                return true
            }
        }
        for (bb in billboards) {
            val minDist = radius + 0.95f
            if (abs(x - bb.x) < minDist && abs(z - bb.z) < minDist && hypot(x - bb.x, z - bb.z) < minDist) {
                return true
            }
        }
        for (veh in otherVehicles) {
            if (veh.id == ignoreVehicleId) continue
            val minDist = radius + 1.20f
            if (abs(x - veh.x) < minDist && abs(z - veh.z) < minDist && hypot(x - veh.x, z - veh.z) < minDist) {
                return true
            }
        }
        return false
    }

    /**
     * Ensures a vehicle spawns at a valid dry-land coordinate strictly outside solid buildings,
     * rocks, trees, water, vending machines, billboards, and other vehicles.
     */
    fun findValidVehicleSpawnPosition(
        prefX: Float,
        prefZ: Float,
        existingVehicles: List<MapVehicle> = emptyList()
    ): Pair<Float, Float> {
        val safeBound = PLAYABLE_LIMIT - 28f
        val startX = prefX.coerceIn(-safeBound, safeBound)
        val startZ = prefZ.coerceIn(-safeBound, safeBound)
        if (!isVehiclePositionBlocked(startX, startZ, emptyList(), existingVehicles, null, radius = 2.0f) &&
            existingVehicles.none { hypot(startX - it.x, startZ - it.z) < 14f }
        ) {
            return startX to startZ
        }
        val radii = floatArrayOf(3.0f, 6.0f, 10.0f, 15.0f, 22.0f, 30.0f)
        for (r in radii) {
            for (angleIdx in 0 until 16) {
                val rad = angleIdx * (2.0 * PI / 16.0)
                val cx = (startX + cos(rad).toFloat() * r).coerceIn(-safeBound, safeBound)
                val cz = (startZ + sin(rad).toFloat() * r).coerceIn(-safeBound, safeBound)
                if (!isVehiclePositionBlocked(cx, cz, emptyList(), existingVehicles, null, radius = 2.0f) &&
                    existingVehicles.none { hypot(cx - it.x, cz - it.z) < 14f }
                ) {
                    return cx to cz
                }
            }
        }
        return recoverVehicleIfStuck(startX, startZ, emptyList(), existingVehicles, null)
    }

    /**
     * Prevents vehicles from ever getting stuck inside buildings, rocks, trees, water, or map edges.
     */
    fun recoverVehicleIfStuck(
        x: Float,
        z: Float,
        glooWalls: List<deployedGlooWall> = emptyList(),
        otherVehicles: List<MapVehicle> = emptyList(),
        ignoreVehicleId: Int? = null
    ): Pair<Float, Float> {
        val clampedX = if (x.isNaN() || x.isInfinite()) 32f else x.coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)
        val clampedZ = if (z.isNaN() || z.isInfinite()) -95f else z.coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)

        if (!isVehiclePositionBlocked(clampedX, clampedZ, glooWalls, otherVehicles, ignoreVehicleId)) {
            return clampedX to clampedZ
        }

        val collider = findCollidingBuilding(clampedX, clampedZ, radius = VEHICLE_COLLISION_RADIUS, ignoreVaultable = false)
        if (collider != null) {
            val dx = clampedX - collider.centerX
            val dz = clampedZ - collider.centerZ
            val halfW = collider.width * 0.5f + VEHICLE_COLLISION_RADIUS + 0.7f
            val halfD = collider.depth * 0.5f + VEHICLE_COLLISION_RADIUS + 0.7f
            val candX = (collider.centerX + (if (dx >= 0f) halfW else -halfW)).coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)
            val candZ = (collider.centerZ + (if (dz >= 0f) halfD else -halfD)).coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)
            if (abs(dx / halfW) > abs(dz / halfD)) {
                if (!isVehiclePositionBlocked(candX, clampedZ, glooWalls, otherVehicles, ignoreVehicleId)) return candX to clampedZ
            } else {
                if (!isVehiclePositionBlocked(clampedX, candZ, glooWalls, otherVehicles, ignoreVehicleId)) return clampedX to candZ
            }
        }

        val radii = floatArrayOf(2.4f, 4.2f, 6.5f, 9.5f, 13.5f, 18.5f, 25.0f)
        for (r in radii) {
            for (angleIdx in 0 until 16) {
                val rad = angleIdx * (2.0 * PI / 16.0)
                val nx = (clampedX + cos(rad).toFloat() * r).coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)
                val nz = (clampedZ + sin(rad).toFloat() * r).coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)
                if (!isVehiclePositionBlocked(nx, nz, glooWalls, otherVehicles, ignoreVehicleId)) {
                    return nx to nz
                }
            }
        }

        return 32f to -95f
    }

    /**
     * Sub-stepped anti-tunneling collision resolution for vehicles.
     * Prevents driving through solid buildings, low walls, rocks, trees, water, or map boundaries.
     * Returns Triple(resolvedX, resolvedZ, hitObstacle).
     */
    fun resolveVehicleMovement(
        oldX: Float,
        oldZ: Float,
        desiredX: Float,
        desiredZ: Float,
        glooWalls: List<deployedGlooWall> = emptyList(),
        otherVehicles: List<MapVehicle> = emptyList(),
        ignoreVehicleId: Int? = null
    ): Triple<Float, Float, Boolean> {
        val (safeOldX, safeOldZ) = if (isVehiclePositionBlocked(oldX, oldZ, glooWalls, otherVehicles, ignoreVehicleId)) {
            recoverVehicleIfStuck(oldX, oldZ, glooWalls, otherVehicles, ignoreVehicleId)
        } else {
            oldX.coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT) to
                oldZ.coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)
        }

        val clampedDesiredX = desiredX.coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)
        val clampedDesiredZ = desiredZ.coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)
        val hitBoundary = abs(desiredX) > VEHICLE_BOUNDARY_LIMIT || abs(desiredZ) > VEHICLE_BOUNDARY_LIMIT || hypot(desiredX, desiredZ) > 488f

        val totalDist = hypot(clampedDesiredX - safeOldX, clampedDesiredZ - safeOldZ)
        if (totalDist < 0.0001f) return Triple(safeOldX, safeOldZ, hitBoundary)

        val steps = (totalDist / 0.38f).toInt().coerceIn(1, 96)
        val stepDx = (clampedDesiredX - safeOldX) / steps
        val stepDz = (clampedDesiredZ - safeOldZ) / steps

        var curX = safeOldX
        var curZ = safeOldZ
        var collided = hitBoundary

        for (i in 0 until steps) {
            val nextX = (curX + stepDx).coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)
            val nextZ = (curZ + stepDz).coerceIn(-VEHICLE_BOUNDARY_LIMIT, VEHICLE_BOUNDARY_LIMIT)

            if (!isVehiclePositionBlocked(nextX, nextZ, glooWalls, otherVehicles, ignoreVehicleId)) {
                curX = nextX
                curZ = nextZ
                continue
            }

            collided = true
            val canSlideX = abs(stepDx) > 0.0001f &&
                !isVehiclePositionBlocked(nextX, curZ, glooWalls, otherVehicles, ignoreVehicleId)
            val canSlideZ = abs(stepDz) > 0.0001f &&
                !isVehiclePositionBlocked(curX, nextZ, glooWalls, otherVehicles, ignoreVehicleId)

            when {
                canSlideX && (!canSlideZ || abs(stepDx) >= abs(stepDz)) -> curX = nextX
                canSlideZ -> curZ = nextZ
                else -> break
            }
        }

        return Triple(curX, curZ, collided)
    }

    /**
     * Finds a valid dry-land dismount position beside a vehicle so the player or AI never exits inside a building or water.
     */
    fun findSafeVehicleExitPosition(
        vehX: Float,
        vehZ: Float,
        vehYawDeg: Float,
        glooWalls: List<deployedGlooWall> = emptyList(),
        vehicles: List<MapVehicle> = emptyList()
    ): Pair<Float, Float> {
        val yawRad = Math.toRadians(vehYawDeg.toDouble())
        val rightX = cos(yawRad).toFloat()
        val rightZ = -sin(yawRad).toFloat()
        val fwdX = sin(yawRad).toFloat()
        val fwdZ = cos(yawRad).toFloat()

        // Try left door, right door, rear, front
        val candidates = arrayOf(
            (vehX - rightX * 2.5f) to (vehZ - rightZ * 2.5f),
            (vehX + rightX * 2.5f) to (vehZ + rightZ * 2.5f),
            (vehX - fwdX * 3.0f) to (vehZ - fwdZ * 3.0f),
            (vehX + fwdX * 3.0f) to (vehZ + fwdZ * 3.0f)
        )
        for ((cx, cz) in candidates) {
            val bx = cx.coerceIn(-PLAYABLE_LIMIT + 4f, PLAYABLE_LIMIT - 4f)
            val bz = cz.coerceIn(-PLAYABLE_LIMIT + 4f, PLAYABLE_LIMIT - 4f)
            if (!isPointInWater(bx, bz) &&
                !isPositionBlocked(bx, bz, ignoreVaultable = false, glooWalls = glooWalls, radius = 0.80f) &&
                vehicles.none { hypot(bx - it.x, bz - it.z) < 1.80f }
            ) {
                return bx to bz
            }
        }

        val radii = floatArrayOf(2.6f, 3.8f, 5.2f, 7.0f)
        for (r in radii) {
            for (angleIdx in 0 until 12) {
                val rad = angleIdx * (2.0 * PI / 12.0)
                val bx = (vehX + cos(rad).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 4f, PLAYABLE_LIMIT - 4f)
                val bz = (vehZ + sin(rad).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 4f, PLAYABLE_LIMIT - 4f)
                if (!isPointInWater(bx, bz) &&
                    !isPositionBlocked(bx, bz, ignoreVaultable = false, glooWalls = glooWalls, radius = 0.80f) &&
                    vehicles.none { hypot(bx - it.x, bz - it.z) < 1.80f }
                ) {
                    return bx to bz
                }
            }
        }

        return recoverIfStuck(vehX + 2.4f, vehZ, glooWalls)
    }

    /**
     * Line-of-sight check so neither player nor bots can shoot or see through solid walls, rocks, or Gloo Walls.
     */
    fun hasLineOfSight(
        x1: Float,
        z1: Float,
        x2: Float,
        z2: Float,
        glooWalls: List<deployedGlooWall>
    ): Boolean {
        val dist = hypot(x2 - x1, z2 - z1)
        if (dist < 1.4f) return true
        val steps = (dist / 2.2f).toInt().coerceIn(3, 90)
        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val sx = x1 + (x2 - x1) * t
            val sz = z1 + (z2 - z1) * t
            if (findCollidingBuilding(sx, sz, radius = 0.2f, ignoreVaultable = true) != null) return false
            if (collidesWithGlooWall(sx, sz, glooWalls, radius = 0.3f)) return false
            for (r in rocks) {
                if (abs(sx - r.x) < r.radius && abs(sz - r.z) < r.radius && hypot(sx - r.x, sz - r.z) < r.radius * 0.9f) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Computes the exact distance along a firing ray `(dirX, dirZ)` from `(startX, startZ)` up to `maxRange`
     * before hitting a solid building wall, rock, or Gloo Wall. Prevents bullets and traces from passing through solid walls.
     */
    fun computeRayObstacleDistance(
        startX: Float,
        startZ: Float,
        dirX: Float,
        dirZ: Float,
        maxRange: Float,
        glooWalls: List<deployedGlooWall>
    ): Float {
        val safeMax = maxRange.coerceIn(2f, 400f)
        val stepSize = 1.6f
        val steps = (safeMax / stepSize).toInt().coerceIn(2, 240)
        for (i in 1..steps) {
            val d = (i * stepSize).coerceAtMost(safeMax)
            val sx = startX + dirX * d
            val sz = startZ + dirZ * d
            if (findCollidingBuilding(sx, sz, radius = 0.22f, ignoreVaultable = true) != null) {
                return d
            }
            if (collidesWithGlooWall(sx, sz, glooWalls, radius = 0.32f)) {
                return d
            }
            for (r in rocks) {
                if (abs(sx - r.x) < r.radius && abs(sz - r.z) < r.radius && hypot(sx - r.x, sz - r.z) < r.radius * 0.9f) {
                    return d
                }
            }
        }
        return safeMax
    }

    /**
     * Helps AI bots navigate across bridges when crossing the river and steer around obstacles.
     */
    fun computeAiWaypoint(
        botX: Float,
        botZ: Float,
        goalX: Float,
        goalZ: Float,
        glooWalls: List<deployedGlooWall>
    ): Pair<Float, Float> {
        // If bot and goal are on opposite sides of the river (Z = -30..30), route via nearest bridge
        val crossesRiver = (botZ < -30f && goalZ > 30f) || (botZ > 30f && goalZ < -30f)
        val rawTargetX: Float
        val rawTargetZ: Float
        if (crossesRiver && !isOnBridge(botX, botZ)) {
            val nearestBridgeX = bridgeXPositions.minByOrNull { abs(it - botX) } ?: 0f
            if (abs(botX - nearestBridgeX) > 7f) {
                rawTargetX = nearestBridgeX
                rawTargetZ = if (botZ < 0f) -34f else 34f
            } else {
                rawTargetX = nearestBridgeX
                rawTargetZ = if (goalZ > 0f) 38f else -38f
            }
        } else {
            rawTargetX = goalX
            rawTargetZ = goalZ
        }

        // Check if a solid obstacle is immediately ahead (~3.5m); if so, steer left or right
        val dx = rawTargetX - botX
        val dz = rawTargetZ - botZ
        val d = hypot(dx, dz)
        if (d < 1.5f) return rawTargetX to rawTargetZ
        val dirX = dx / d
        val dirZ = dz / d
        val probeX = botX + dirX * 3.6f
        val probeZ = botZ + dirZ * 3.6f
        if (!isPositionBlocked(probeX, probeZ, ignoreVaultable = false, glooWalls = glooWalls, radius = 0.85f)) {
            return rawTargetX to rawTargetZ
        }

        // Try steering +55 deg, -55 deg, +105 deg, or -105 deg around the obstacle so AI never gets stuck in buildings
        val steerAngles = floatArrayOf(0.96f, -0.96f, 1.83f, -1.83f, 2.65f)
        for (ang in steerAngles) {
            val c = cos(ang)
            val s = sin(ang)
            val rx = dirX * c - dirZ * s
            val rz = dirZ * c + dirX * s
            val candX = (botX + rx * 7.5f).coerceIn(-PLAYABLE_LIMIT + 25f, PLAYABLE_LIMIT - 25f)
            val candZ = (botZ + rz * 7.5f).coerceIn(-PLAYABLE_LIMIT + 25f, PLAYABLE_LIMIT - 25f)
            if (!isPositionBlocked(candX, candZ, ignoreVaultable = false, glooWalls = glooWalls, radius = 0.95f) &&
                !isPointInWater(candX, candZ)
            ) {
                return candX to candZ
            }
        }
        return rawTargetX to rawTargetZ
    }

    /**
     * Finds a valid ground cover position near (botX, botZ) shielded from (threatX, threatZ)
     * behind a nearby rock or building wall.
     */
    fun findNearbyCoverPoint(
        botX: Float,
        botZ: Float,
        threatX: Float,
        threatZ: Float,
        glooWalls: List<deployedGlooWall>
    ): Pair<Float, Float> {
        val tdx = botX - threatX
        val tdz = botZ - threatZ
        val tDist = hypot(tdx, tdz).coerceAtLeast(1f)
        val awayX = tdx / tDist
        val awayZ = tdz / tDist

        // 1. Check nearby environmental rocks within 26m and place bot on the far side from threat
        for (rk in rocks) {
            if (abs(rk.x - botX) > 26f || abs(rk.z - botZ) > 26f) continue
            val coverX = (rk.x + awayX * (rk.radius + 1.8f)).coerceIn(-PLAYABLE_LIMIT + 25f, PLAYABLE_LIMIT - 25f)
            val coverZ = (rk.z + awayZ * (rk.radius + 1.8f)).coerceIn(-PLAYABLE_LIMIT + 25f, PLAYABLE_LIMIT - 25f)
            if (!isPositionBlocked(coverX, coverZ, ignoreVaultable = false, glooWalls = glooWalls, radius = 0.9f) &&
                !isPointInWater(coverX, coverZ)
            ) {
                return coverX to coverZ
            }
        }

        // 2. Check nearby solid buildings within 32m and place bot on the far exterior side from threat
        for (b in buildings) {
            if (!b.isSolidCollider || b.isVaultable) continue
            if (abs(b.centerX - botX) > 32f || abs(b.centerZ - botZ) > 32f) continue
            val offsetDist = max(b.width, b.depth) * 0.5f + 2.2f
            val coverX = (b.centerX + awayX * offsetDist).coerceIn(-PLAYABLE_LIMIT + 25f, PLAYABLE_LIMIT - 25f)
            val coverZ = (b.centerZ + awayZ * offsetDist).coerceIn(-PLAYABLE_LIMIT + 25f, PLAYABLE_LIMIT - 25f)
            if (!isPositionBlocked(coverX, coverZ, ignoreVaultable = false, glooWalls = glooWalls, radius = 0.9f) &&
                !isPointInWater(coverX, coverZ)
            ) {
                return coverX to coverZ
            }
        }

        // 3. Fallback lateral retreat step on open ground
        val latX = (botX + awayX * 12f - awayZ * 8f).coerceIn(-PLAYABLE_LIMIT + 25f, PLAYABLE_LIMIT - 25f)
        val latZ = (botZ + awayZ * 12f + awayX * 8f).coerceIn(-PLAYABLE_LIMIT + 25f, PLAYABLE_LIMIT - 25f)
        if (!isPositionBlocked(latX, latZ, ignoreVaultable = false, glooWalls = glooWalls, radius = 0.9f)) {
            return latX to latZ
        }
        return botX to botZ
    }

    private val staticVendingMachines: List<VendingMachineNode> by lazy { buildInitialVendingMachines() }
    private val staticVehicles: List<MapVehicle> by lazy { buildInitialVehicles() }

    /**
     * Checks whether a candidate world coordinate is a valid ground spawn point:
     * - Strictly inside the playable map area and island coastline
     * - Strictly on dry valid ground (not in river or ocean water)
     * - Strictly not inside any solid building, house, wall, rock, or tree
     * - Strictly not inside any Vending Machine, Vehicle, or Billboard
     * - Strictly separated from any already-spawned player or bot
     */
    fun isValidSpawnPoint(
        x: Float,
        z: Float,
        existingSpawns: List<Pair<Float, Float>> = emptyList(),
        minSeparation: Float = 3.5f,
        clearanceRadius: Float = 1.45f
    ): Boolean {
        if (x.isNaN() || z.isNaN() || x.isInfinite() || z.isInfinite()) return false
        val maxSpawnCoord = PLAYABLE_LIMIT - 30f
        if (abs(x) > maxSpawnCoord || abs(z) > maxSpawnCoord) return false
        if (hypot(x, z) > 445f) return false
        if (abs(z) < 36f) return false
        if (isPointInWater(x, z)) return false
        val terrainY = getTerrainHeight(x, z)
        if (terrainY.isNaN() || terrainY < 0f) return false
        if (isPositionBlocked(x, z, ignoreVaultable = false, glooWalls = emptyList(), radius = clearanceRadius)) {
            return false
        }
        // Avoid spawning inside static billboards
        for (bb in billboards) {
            if (abs(x - bb.x) < 4.5f && abs(z - bb.z) < 4.5f && hypot(x - bb.x, z - bb.z) < 4.0f) {
                return false
            }
        }
        // Avoid spawning inside initial vending machines or vehicles
        for (vm in staticVendingMachines) {
            if (abs(x - vm.x) < 3.5f && abs(z - vm.z) < 3.5f && hypot(x - vm.x, z - vm.z) < 3.2f) {
                return false
            }
        }
        for (veh in staticVehicles) {
            if (abs(x - veh.x) < 4.5f && abs(z - veh.z) < 4.5f && hypot(x - veh.x, z - veh.z) < 4.2f) {
                return false
            }
        }
        // Avoid spawning inside or overlapping another player/bot
        for ((ox, oz) in existingSpawns) {
            if (hypot(x - ox, z - oz) < minSeparation) {
                return false
            }
        }
        return true
    }

    /**
     * Finds a valid random ground spawn point for the player on the existing map.
     * Guarantees the spawn is inside the map, on dry ground, not inside any wall/building/object,
     * and varies across matches.
     */
    fun findValidRandomPlayerSpawn(
        rng: Random,
        previousSpawnX: Float? = null,
        previousSpawnZ: Float? = null
    ): Triple<Float, Float, String> {
        // Shuffle named zones so each match explores different parts of SANI Island
        val zoneIndices = namedZones.indices.shuffled(rng)
        for (zIdx in zoneIndices) {
            val zone = namedZones[zIdx]
            for (attempt in 0 until 12) {
                val angle = rng.nextFloat() * (2f * PI.toFloat())
                val dist = 20f + rng.nextFloat() * 42f
                val cx = zone.x + cos(angle) * dist
                val cz = zone.z + sin(angle) * dist
                val farEnoughFromLast = previousSpawnX == null || previousSpawnZ == null ||
                    hypot(cx - previousSpawnX, cz - previousSpawnZ) > 35f
                if (farEnoughFromLast && isValidSpawnPoint(cx, cz, emptyList(), minSeparation = 0f, clearanceRadius = 1.6f)) {
                    return Triple(cx, cz, zone.name)
                }
            }
        }

        // Try open-field random ground coordinates across the island
        for (attempt in 0 until 40) {
            val cx = rng.nextFloat() * 680f - 340f
            val cz = rng.nextFloat() * 680f - 340f
            if (isValidSpawnPoint(cx, cz, emptyList(), minSeparation = 0f, clearanceRadius = 1.6f)) {
                val nearestZone = namedZones.minByOrNull { hypot(cx - it.x, cz - it.z) }?.name ?: "SANI ISLAND"
                return Triple(cx, cz, nearestZone)
            }
        }

        // Deterministic ring scan fallback around named zones (guaranteed O(1) termination)
        val fallbackRadii = floatArrayOf(22f, 27f, 33f, 40f)
        for (zone in namedZones) {
            for (r in fallbackRadii) {
                for (a in 0 until 16) {
                    val rad = a * (2.0 * PI / 16.0)
                    val cx = zone.x + cos(rad).toFloat() * r
                    val cz = zone.z + sin(rad).toFloat() * r
                    if (isValidSpawnPoint(cx, cz, emptyList(), minSeparation = 0f, clearanceRadius = 1.5f)) {
                        return Triple(cx, cz, zone.name)
                    }
                }
            }
        }

        val (safeX, safeZ) = recoverIfStuck(0f, -125f)
        return Triple(safeX, safeZ, "SANI CITADEL")
    }

    /**
     * Finds a valid ground spawn position for a friendly teammate near the player in Duo / Squad mode.
     * Guarantees teammates never spawn inside buildings/walls/rocks/water and never overlap the player or each other.
     */
    fun findValidTeammateSpawn(
        playerX: Float,
        playerZ: Float,
        teammateIndex: Int,
        totalTeammates: Int,
        occupiedSpawns: List<Pair<Float, Float>>,
        rng: Random
    ): Pair<Float, Float> {
        val baseAngle = (teammateIndex.toDouble() * (2.0 * PI / totalTeammates.coerceAtLeast(1))) +
            (rng.nextDouble() * 0.4 - 0.2)
        val radii = floatArrayOf(7.0f, 9.5f, 12.0f, 15.0f, 18.5f, 22.0f)
        for (r in radii) {
            for (step in 0 until 16) {
                val offsetSign = if (step % 2 == 0) 1 else -1
                val angleStep = (step / 2) * (PI / 8.0) * offsetSign
                val angle = baseAngle + angleStep
                val cx = (playerX + cos(angle).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 30f, PLAYABLE_LIMIT - 30f)
                val cz = (playerZ + sin(angle).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 30f, PLAYABLE_LIMIT - 30f)
                if (isValidSpawnPoint(cx, cz, occupiedSpawns, minSeparation = 3.6f, clearanceRadius = 1.45f)) {
                    return cx to cz
                }
            }
        }
        // Fallback: spiral search from player position
        for (r in 5..35 step 3) {
            for (a in 0 until 16) {
                val rad = a * (2.0 * PI / 16.0)
                val cx = playerX + cos(rad).toFloat() * r
                val cz = playerZ + sin(rad).toFloat() * r
                if (isValidSpawnPoint(cx, cz, occupiedSpawns, minSeparation = 3.0f, clearanceRadius = 1.2f)) {
                    return cx to cz
                }
            }
        }
        return recoverIfStuck(playerX + (teammateIndex + 1) * 5f, playerZ + 5f)
    }

    /**
     * Finds a valid ground spawn position for an enemy bot on the map, keeping safe initial distance
     * from the player and avoiding all walls, buildings, water, and other combatants.
     */
    fun findValidEnemySpawn(
        playerX: Float,
        playerZ: Float,
        enemyIndex: Int,
        totalEnemies: Int,
        occupiedSpawns: List<Pair<Float, Float>>,
        rng: Random
    ): Pair<Float, Float> {
        val baseAngle = (enemyIndex.toFloat() / totalEnemies.coerceAtLeast(1)) * (2f * PI.toFloat()) +
            (rng.nextFloat() * 0.25f - 0.125f)
        val baseDist = 90f + (enemyIndex % 5) * 58f + rng.nextFloat() * 22f

        for (attempt in 0 until 20) {
            val angle = baseAngle + attempt * 0.38f
            val dist = (baseDist + (attempt % 4) * 24f).coerceIn(75f, 410f)
            val cx = (cos(angle) * dist).coerceIn(-430f, 430f)
            val cz = (sin(angle) * dist).coerceIn(-430f, 430f)
            if (hypot(cx - playerX, cz - playerZ) < 55f) continue
            if (isValidSpawnPoint(cx, cz, occupiedSpawns, minSeparation = 4.0f, clearanceRadius = 1.35f)) {
                return cx to cz
            }
        }

        // Fallback deterministic ring search
        val fallbackRadii = floatArrayOf(110f, 165f, 220f, 280f, 340f)
        for (r in fallbackRadii) {
            for (a in 0 until 24) {
                val rad = (a + enemyIndex) * (2.0 * PI / 24.0)
                val cx = cos(rad).toFloat() * r
                val cz = sin(rad).toFloat() * r
                if (hypot(cx - playerX, cz - playerZ) >= 50f &&
                    isValidSpawnPoint(cx, cz, occupiedSpawns, minSeparation = 3.5f, clearanceRadius = 1.25f)
                ) {
                    return cx to cz
                }
            }
        }

        return recoverIfStuck(
            (playerX + 120f).coerceIn(-380f, 380f),
            (playerZ + 120f).coerceIn(-380f, 380f)
        )
    }

    /**
     * Checks whether a candidate coordinate is a valid, reachable ground position for loot:
     * - Strictly inside playable island bounds and on dry ground (never in river or ocean water)
     * - Strictly outside all buildings, walls, rocks, trees, vending machines, vehicles, and billboards
     * - Separated from already-spawned loot items so loot never duplicates or stacks on the same spot
     */
    fun isValidLootSpawnPoint(
        x: Float,
        z: Float,
        occupiedLoot: List<Pair<Float, Float>> = emptyList(),
        minSeparation: Float = 1.35f
    ): Boolean {
        if (x.isNaN() || z.isNaN() || x.isInfinite() || z.isInfinite()) return false
        val maxCoord = PLAYABLE_LIMIT - 28f
        if (abs(x) > maxCoord || abs(z) > maxCoord) return false
        if (hypot(x, z) > 450f) return false
        if (isPointInWater(x, z)) return false
        val terrainY = getTerrainHeight(x, z)
        if (terrainY.isNaN() || terrainY < 0f) return false
        if (isPositionBlocked(x, z, ignoreVaultable = false, glooWalls = emptyList(), radius = 1.25f)) {
            return false
        }
        for (bb in billboards) {
            if (abs(x - bb.x) < 3.5f && abs(z - bb.z) < 3.5f && hypot(x - bb.x, z - bb.z) < 3.2f) {
                return false
            }
        }
        for (vm in staticVendingMachines) {
            if (abs(x - vm.x) < 3.0f && abs(z - vm.z) < 3.0f && hypot(x - vm.x, z - vm.z) < 2.8f) {
                return false
            }
        }
        for (veh in staticVehicles) {
            if (abs(x - veh.x) < 4.0f && abs(z - veh.z) < 4.0f && hypot(x - veh.x, z - veh.z) < 3.8f) {
                return false
            }
        }
        for (i in occupiedLoot.indices) {
            val (ox, oz) = occupiedLoot[i]
            if (abs(x - ox) < minSeparation && abs(z - oz) < minSeparation && hypot(x - ox, z - oz) < minSeparation) {
                return false
            }
        }
        return true
    }

    /**
     * Finds a valid, reachable ground position near (desiredX, desiredZ) for spawning a loot item.
     */
    fun findValidLootSpawnPoint(
        desiredX: Float,
        desiredZ: Float,
        occupiedLoot: List<Pair<Float, Float>>,
        rng: Random
    ): Pair<Float, Float> {
        val clampedX = desiredX.coerceIn(-PLAYABLE_LIMIT + 30f, PLAYABLE_LIMIT - 30f)
        val clampedZ = desiredZ.coerceIn(-PLAYABLE_LIMIT + 30f, PLAYABLE_LIMIT - 30f)
        if (isValidLootSpawnPoint(clampedX, clampedZ, occupiedLoot)) {
            return clampedX to clampedZ
        }

        val radii = floatArrayOf(2.0f, 3.6f, 5.5f, 8.0f, 11.5f, 15.5f, 20.0f)
        val startAngleIdx = rng.nextInt(12)
        for (r in radii) {
            for (step in 0 until 12) {
                val angleIdx = (startAngleIdx + step) % 12
                val rad = angleIdx * (2.0 * PI / 12.0)
                val cx = (clampedX + cos(rad).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 30f, PLAYABLE_LIMIT - 30f)
                val cz = (clampedZ + sin(rad).toFloat() * r).coerceIn(-PLAYABLE_LIMIT + 30f, PLAYABLE_LIMIT - 30f)
                if (isValidLootSpawnPoint(cx, cz, occupiedLoot)) {
                    return cx to cz
                }
            }
        }

        // Fallback scan around named zones
        for (zone in namedZones) {
            for (a in 0 until 16) {
                val rad = a * (2.0 * PI / 16.0)
                val cx = zone.x + cos(rad).toFloat() * 24f
                val cz = zone.z + sin(rad).toFloat() * 24f
                if (isValidLootSpawnPoint(cx, cz, occupiedLoot, minSeparation = 1.1f)) {
                    return cx to cz
                }
            }
        }

        val (rx, rz) = recoverIfStuck(clampedX, if ( abs(clampedZ) < 36f ) -45f else clampedZ)
        return rx to rz
    }

    /**
     * Generates a fresh, randomized, balanced loot state at valid ground locations for each new match.
     * Includes Weapons, Ammo, Armor, Medkits, Attachments, Gloo walls, and Coins.
     * Loot never spawns inside walls/water/unreachable objects, never duplicates, and never respawns during a match.
     */
    fun generateMatchLoot(
        matchSeed: Long,
        playerSpawnX: Float = 0f,
        playerSpawnZ: Float = -125f
    ): List<WorldLootItem> {
        val rng = Random(matchSeed)
        val primaryWeapons = OriginalWeaponCatalog.allWeapons.filter { !it.category.isSecondary }
        val allScopes = listOf(ScopeType.RED_DOT, ScopeType.SCOPE_2X, ScopeType.SCOPE_4X, ScopeType.SNIPER_8X)
        val allAttachments = AttachmentType.entries

        val spawnHubs = ArrayList<Pair<Float, Float>>(48)
        // 1. Starter loot hub near the player's spawn point (outside initial 4.0m standing radius)
        spawnHubs.add((playerSpawnX + 7.5f) to (playerSpawnZ + 7.5f))
        // 2. Named zone tactical loot rings (placed outside central hall buildings)
        namedZones.forEach { z ->
            spawnHubs.add((z.x + 20f) to (z.z - 16f))
            spawnHubs.add((z.x - 22f) to (z.z + 18f))
            spawnHubs.add((z.x + 18f) to (z.z + 22f))
            spawnHubs.add((z.x - 19f) to (z.z - 20f))
        }
        // 3. Randomized field loot hubs on valid ground across the island
        var randomHubsAdded = 0
        var attempts = 0
        while (randomHubsAdded < 16 && attempts < 60) {
            attempts++
            val rx = rng.nextFloat() * 680f - 340f
            val rz = rng.nextFloat() * 680f - 340f
            if (abs(rz) >= 38f && !isPointInWater(rx, rz) &&
                !isPositionBlocked(rx, rz, ignoreVaultable = false, glooWalls = emptyList(), radius = 2.0f)
            ) {
                spawnHubs.add(rx to rz)
                randomHubsAdded++
            }
        }

        val items = ArrayList<WorldLootItem>(spawnHubs.size * 4 + 8)
        val occupiedPositions = ArrayList<Pair<Float, Float>>(spawnHubs.size * 4 + 8)
        var nextId = 1

        for (hubIdx in spawnHubs.indices) {
            val (hx, hz) = spawnHubs[hubIdx]
            // Select a primary weapon so every loot hub provides a functional battle rifle/SMG/shotgun/DMR/sniper
            val weapon = primaryWeapons[rng.nextInt(primaryWeapons.size)]

            // 1. Weapon on valid ground
            val (wx, wz) = findValidLootSpawnPoint(
                desiredX = hx + (rng.nextFloat() * 4f - 2f),
                desiredZ = hz + (rng.nextFloat() * 4f - 2f),
                occupiedLoot = occupiedPositions,
                rng = rng
            )
            occupiedPositions.add(wx to wz)
            items.add(
                WorldLootItem(
                    id = nextId++,
                    x = wx,
                    z = wz,
                    y = getTerrainHeight(wx, wz),
                    category = LootCategory.GUN,
                    name = weapon.name,
                    weaponSpec = weapon,
                    amount = weapon.magazineSize,
                    collected = false
                )
            )

            // 2. Compatible Ammo right near the weapon
            val (ax, az) = findValidLootSpawnPoint(
                desiredX = wx + 1.8f,
                desiredZ = wz + 1.2f,
                occupiedLoot = occupiedPositions,
                rng = rng
            )
            occupiedPositions.add(ax to az)
            items.add(
                WorldLootItem(
                    id = nextId++,
                    x = ax,
                    z = az,
                    y = getTerrainHeight(ax, az),
                    category = LootCategory.AMMO,
                    name = weapon.category.ammoType.label,
                    ammoType = weapon.category.ammoType,
                    amount = weapon.category.ammoType.defaultPickupCount,
                    collected = false
                )
            )

            // 3. Physical Match Coins on valid ground
            val (cx, cz) = findValidLootSpawnPoint(
                desiredX = hx - 2.2f,
                desiredZ = hz + 2.2f,
                occupiedLoot = occupiedPositions,
                rng = rng
            )
            occupiedPositions.add(cx to cz)
            val coinAmount = if (hubIdx % 3 == 0) 150 else 100
            items.add(
                WorldLootItem(
                    id = nextId++,
                    x = cx,
                    z = cz,
                    y = getTerrainHeight(cx, cz),
                    category = LootCategory.COIN,
                    name = "Battle Coins",
                    amount = coinAmount,
                    collected = false
                )
            )

            // 4. Tactical gear / utility item (Armor, Medkits, Attachments, Gloo Walls, Scopes, Helmet, Backpack)
            val (ux, uz) = findValidLootSpawnPoint(
                desiredX = hx + (rng.nextFloat() * 5f - 2.5f),
                desiredZ = hz - 2.8f,
                occupiedLoot = occupiedPositions,
                rng = rng
            )
            occupiedPositions.add(ux to uz)
            val uy = getTerrainHeight(ux, uz)
            val roll = (hubIdx + rng.nextInt(8)) % 8
            when (roll) {
                0 -> {
                    val tier = rng.nextInt(1, 4)
                    items.add(WorldLootItem(nextId++, ux, uz, uy, LootCategory.ARMOR, "Vest Lv.$tier", tier = tier))
                }
                1 -> {
                    items.add(WorldLootItem(nextId++, ux, uz, uy, LootCategory.MEDKIT, "Medkit", amount = 1))
                }
                2 -> {
                    val att = allAttachments[rng.nextInt(allAttachments.size)]
                    items.add(WorldLootItem(nextId++, ux, uz, uy, LootCategory.ATTACHMENT, att.label, attachmentType = att))
                }
                3 -> {
                    items.add(WorldLootItem(nextId++, ux, uz, uy, LootCategory.GLOO_WALL, "Gloo Wall", amount = 2))
                }
                4 -> {
                    val sc = allScopes[rng.nextInt(allScopes.size)]
                    items.add(WorldLootItem(nextId++, ux, uz, uy, LootCategory.SCOPE, sc.label, scopeType = sc))
                }
                5 -> {
                    val tier = rng.nextInt(2, 4)
                    items.add(WorldLootItem(nextId++, ux, uz, uy, LootCategory.HELMET, "Helmet Lv.$tier", tier = tier))
                }
                6 -> {
                    val tier = rng.nextInt(2, 4)
                    items.add(WorldLootItem(nextId++, ux, uz, uy, LootCategory.BACKPACK, "Backpack Lv.$tier", tier = tier))
                }
                else -> {
                    items.add(WorldLootItem(nextId++, ux, uz, uy, LootCategory.GRENADE, "Frag Grenade", amount = 1))
                }
            }
        }

        // Guarantee starter cluster near player spawn also has a Medkit, Armor Vest, Attachment, and Gloo Wall nearby
        val starterExtras = listOf(
            Triple(LootCategory.ARMOR, "Vest Lv.2", 2),
            Triple(LootCategory.MEDKIT, "Medkit", 1),
            Triple(LootCategory.GLOO_WALL, "Gloo Wall", 2),
            Triple(LootCategory.ATTACHMENT, AttachmentType.EXTENDED_MAG.label, 1)
        )
        starterExtras.forEachIndexed { idx, (cat, label, num) ->
            val (ex, ez) = findValidLootSpawnPoint(
                desiredX = playerSpawnX + (idx - 1.5f) * 2.6f,
                desiredZ = playerSpawnZ - 7.5f,
                occupiedLoot = occupiedPositions,
                rng = rng
            )
            occupiedPositions.add(ex to ez)
            val ey = getTerrainHeight(ex, ez)
            val extraItem = when (cat) {
                LootCategory.ARMOR -> WorldLootItem(nextId++, ex, ez, ey, cat, label, tier = num)
                LootCategory.ATTACHMENT -> WorldLootItem(
                    nextId++, ex, ez, ey, cat, label,
                    attachmentType = AttachmentType.EXTENDED_MAG
                )
                else -> WorldLootItem(nextId++, ex, ez, ey, cat, label, amount = num)
            }
            items.add(extraItem)
        }

        return items
    }
}
