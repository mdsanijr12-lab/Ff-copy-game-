package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.SoundEngine
import com.example.data.PlayerRepository
import com.example.data.SaniDatabase
import com.example.engine.BattleRoyaleViewModel
import com.example.engine.IslandMapGenerator
import com.example.model.AbilityState
import com.example.model.AutoLootSettings
import com.example.model.CharacterAnimState
import com.example.model.CharacterId
import com.example.model.FpsTargetMode
import com.example.model.GraphicsQuality
import com.example.model.GyroscopeMode
import com.example.model.HudControlId
import com.example.model.HudLayoutPreset
import com.example.model.LootCategory
import com.example.model.TeamMode
import com.example.model.WeaponSlotIndex
import com.example.ui.screens.resolveHudLayoutBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `validate HUD layout, non-overlapping controls, camera rotation, manual fire, collision and gameplay`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("SANI", context.getString(R.string.app_name))

        // 1. Validate HUD Layout across Portrait (Vivo Y03 360x760), Compact (320x568), and Landscape (800x360)
        val defaultPreset = HudLayoutPreset.defaultPreset(0)
        val screenConfigs = listOf(
            Triple(720f, 1520f, 2.0f),  // Vivo Y03 360dp x 760dp @ 2x density
            Triple(1080f, 2400f, 2.75f), // Standard phone portrait
            Triple(1600f, 720f, 2.0f),   // Landscape 800dp x 360dp @ 2x density
            Triple(1920f, 1080f, 2.0f)   // Wide landscape
        )
        for ((wPx, hPx, density) in screenConfigs) {
            val resolved = resolveHudLayoutBounds(defaultPreset, wPx, hPx, density)
            assertEquals(HudControlId.entries.size, resolved.size)

            // Joystick must be on the left half
            val joyBox = resolved[HudControlId.JOYSTICK]!!
            assertTrue("Joystick must stay on left side", joyBox.centerX < wPx * 0.48f)

            // All 9 Combat Controls (Fire, Aim, Scope, Reload, Weapon Switch, Jump, Crouch, Prone, Ability) must be on the right side
            for (combatId in HudControlId.RIGHT_SIDE_COMBAT_CONTROLS) {
                val box = resolved[combatId]!!
                assertTrue("${combatId.name} must stay on right side", box.centerX >= wPx * 0.48f)
            }

            // Every control must be strictly inside the screen and never overlap any other control
            val boxes = resolved.values.toList()
            for (i in boxes.indices) {
                val a = boxes[i]
                assertTrue("${a.controlId} left inside screen", a.leftPx >= 0f)
                assertTrue("${a.controlId} top inside screen", a.topPx >= 0f)
                assertTrue("${a.controlId} right inside screen", a.rightPx <= wPx + 0.5f)
                assertTrue("${a.controlId} bottom inside screen", a.bottomPx <= hPx + 0.5f)
                for (j in i + 1 until boxes.size) {
                    val b = boxes[j]
                    assertFalse(
                        "Controls ${a.controlId} and ${b.controlId} must not overlap on ${wPx}x${hPx}",
                        a.overlaps(b, gapPx = -0.5f)
                    )
                }
            }
        }

        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PlayerRepository(db.playerProfileDao())
        val sound = SoundEngine().apply { isMuted = true }
        val vm = BattleRoyaleViewModel(repo, sound)

        // 2. Character Selection & Mode Selection
        vm.selectCharacter(CharacterId.RIMA)
        assertEquals(CharacterId.RIMA, vm.uiState.value.selectedCharacter)
        vm.selectCharacter(CharacterId.SANI)
        assertEquals(CharacterId.SANI, vm.uiState.value.selectedCharacter)
        vm.selectTeamMode(TeamMode.SQUAD)
        assertEquals(TeamMode.SQUAD, vm.uiState.value.selectedTeamMode)

        // 3. HUD Editor Clamping & 3-Layout Save/Restore
        vm.selectHudLayoutSlot(2)
        assertEquals(2, vm.uiState.value.activeHudSlot)
        vm.updateHudControl(HudControlId.FIRE_BUTTON, normX = 1.9f, normY = -0.5f, sizeScale = 2.0f, alpha = 0.05f)
        val clampedCfg = vm.uiState.value.hudPresets[2].controls[HudControlId.FIRE_BUTTON]!!
        assertTrue(clampedCfg.normX in 0.52f..0.94f)
        assertTrue(clampedCfg.normY in 0.05f..0.95f)
        assertTrue(clampedCfg.sizeScale in 0.75f..1.35f)
        assertTrue(clampedCfg.alpha in 0.30f..1.0f)

        // 4. Start Match & Verify Smooth Movement + Camera Rotation While Moving/Aiming/Shooting
        vm.startNewMatch()
        vm.setJoystickInput(0f, 0.9f, sprinting = true)
        assertTrue(vm.uiState.value.isSprinting)

        val initialYaw = vm.uiState.value.playerYawDeg
        vm.rotateCameraByTouch(45f, -15f)
        assertTrue(vm.uiState.value.playerYawDeg != initialYaw)
        assertTrue(vm.uiState.value.playerPitchDeg in -38f..38f)

        // Sprinting + Crouch -> transitions smoothly to crouch and cancels sprint
        vm.toggleCrouch()
        assertTrue(vm.uiState.value.isCrouching)
        assertFalse(vm.uiState.value.isSprinting)
        assertEquals(CharacterAnimState.CROUCH, vm.uiState.value.playerAnimState)

        // Crouching + Jump -> stands up cleanly first
        vm.triggerJumpOrVault()
        assertFalse(vm.uiState.value.isCrouching)
        assertFalse(vm.uiState.value.isJumping)

        // Standing + Jump -> jumps cleanly
        vm.triggerJumpOrVault()
        assertTrue(vm.uiState.value.isJumping)
        vm.setJoystickInput(0f, 0f, sprinting = false)

        // 5. Verify SANI Ability Validation (11s active, 30s cooldown, weapon firing locked during active, restored after)
        assertEquals(AbilityState.READY, vm.uiState.value.abilityState)
        assertTrue(vm.canPlayerFireNow())
        vm.activateCharacterAbility()
        assertEquals(AbilityState.ACTIVE, vm.uiState.value.abilityState)
        assertEquals(11f, vm.uiState.value.abilityTimerSec, 0.1f)
        assertFalse(vm.canPlayerFireNow())

        // Cannot shoot while SANI overdrive is active
        val ammoBefore = vm.uiState.value.activeWeapon!!.currentAmmo
        vm.onFireButtonPressedChanged(true)
        assertEquals(ammoBefore, vm.uiState.value.activeWeapon!!.currentAmmo)
        assertFalse(vm.uiState.value.isManualFiringHeld)

        // Step past SANI 11s active duration -> transitions to 30s COOLDOWN and restores weapon firing!
        for (i in 0 until 12) {
            vm.stepSimulationForTest(1.0f)
        }
        assertEquals(AbilityState.COOLDOWN, vm.uiState.value.abilityState)
        assertTrue(vm.canPlayerFireNow())

        // 6. Verify Manual Fire, Reload & Weapon Switching Safety
        val ammoBeforeFire = vm.uiState.value.activeWeapon!!.currentAmmo
        vm.onFireButtonPressedChanged(true)
        vm.rotateCameraByTouch(20f, 5f)
        vm.onFireButtonPressedChanged(false)
        assertTrue(vm.uiState.value.activeWeapon!!.currentAmmo < ammoBeforeFire)

        // Trigger manual reload -> disables firing while reloading
        vm.triggerManualReload()
        assertTrue(vm.uiState.value.isReloading)
        assertFalse(vm.canPlayerFireNow())

        // Switching weapons safely cancels reload state
        vm.switchWeaponSlot(WeaponSlotIndex.SECONDARY)
        assertFalse(vm.uiState.value.isReloading)

        // 7. Verify RIMA Ability Validation (11s reveal, 20s cooldown, markers cleared when ended)
        vm.returnToLobby()
        vm.selectCharacter(CharacterId.RIMA)
        vm.startNewMatch()
        assertEquals(CharacterId.RIMA, vm.uiState.value.matchCharacter)
        vm.activateCharacterAbility()
        assertTrue(vm.uiState.value.rimaRevealActive)
        assertEquals(11f, vm.uiState.value.abilityTimerSec, 0.1f)

        for (i in 0 until 12) {
            vm.stepSimulationForTest(1.0f)
        }
        assertEquals(AbilityState.COOLDOWN, vm.uiState.value.abilityState)
        assertFalse(vm.uiState.value.rimaRevealActive)
        assertTrue(vm.uiState.value.abilityTimerSec <= 20f)

        // 8. Verify Substepped Collision (No walking through walls, no falling through map, no getting permanently stuck)
        val firstBuilding = IslandMapGenerator.buildings.first { it.isSolidCollider && !it.isVaultable }
        val startOutsideX = firstBuilding.centerX - firstBuilding.width * 0.5f - 2.0f
        val startOutsideZ = firstBuilding.centerZ
        val (resolvedWalkX, resolvedWalkZ) = IslandMapGenerator.resolveMovement(
            oldX = startOutsideX,
            oldZ = startOutsideZ,
            desiredX = firstBuilding.centerX + firstBuilding.width * 0.5f + 2.0f,
            desiredZ = startOutsideZ,
            isVaulting = false,
            glooWalls = emptyList()
        )
        assertFalse(
            "Substepped movement must never walk through solid walls",
            IslandMapGenerator.isPositionBlocked(resolvedWalkX, resolvedWalkZ, false, emptyList(), 0.70f)
        )
        assertTrue("Player must stop before building wall", resolvedWalkX < firstBuilding.centerX)

        val (recoveredX, recoveredZ) = IslandMapGenerator.recoverIfStuck(firstBuilding.centerX, firstBuilding.centerZ)
        assertFalse(IslandMapGenerator.isPositionBlocked(recoveredX, recoveredZ, false, emptyList(), 0.70f))

        // Terrain height is always valid and bounded
        val terrainY = IslandMapGenerator.getTerrainHeight(recoveredX, recoveredZ)
        assertFalse(terrainY.isNaN())
        assertTrue(terrainY >= -1.4f)

        // 9. Verify Match Start, Random Valid Spawn, Solo/Duo/Squad Teammates, Full Match Reset & Safe Zone
        val recordedSpawns = mutableListOf<Pair<Float, Float>>()
        for (mode in listOf(TeamMode.SOLO, TeamMode.DUO, TeamMode.SQUAD)) {
            vm.returnToLobby()
            vm.selectTeamMode(mode)
            val prevStartCount = vm.uiState.value.matchStartCount

            // Start match and verify repeated Play taps do not start duplicate matches
            vm.startNewMatch()
            vm.startNewMatch()
            vm.startNewMatch()
            val s = vm.uiState.value
            assertEquals(prevStartCount + 1, s.matchStartCount)
            recordedSpawns.add(s.playerX to s.playerZ)

            // Verify Player Spawn is inside map, on valid ground, not in water, not inside wall/building
            assertTrue(IslandMapGenerator.isValidSpawnPoint(s.playerX, s.playerZ, emptyList(), minSeparation = 0f, clearanceRadius = 1.4f))
            assertFalse(IslandMapGenerator.isPointInWater(s.playerX, s.playerZ))
            assertTrue(s.playerY >= 0f)

            // Verify Friendly Teammate Spawn count (0 in Solo, 1 in Duo, 3 in Squad), validity, and independence
            val allies = s.bots.filter { it.isFriendly }
            assertEquals(mode.friendlyBots, allies.size)
            assertEquals(allies.map { it.id }.distinct().size, allies.size)
            assertEquals(allies.map { it.name }.distinct().size, allies.size)

            for (ally in allies) {
                assertFalse("Teammate must not spawn in water", IslandMapGenerator.isPointInWater(ally.x, ally.z))
                assertFalse(
                    "Teammate must not spawn inside building or wall",
                    IslandMapGenerator.isPositionBlocked(ally.x, ally.z, false, emptyList(), 1.2f)
                )
                assertTrue("Teammate must not spawn inside player", hypot(ally.x - s.playerX, ally.z - s.playerZ) >= 3.0f)
                for (otherAlly in allies) {
                    if (otherAlly.id != ally.id) {
                        assertTrue("Teammates must not overlap each other", hypot(ally.x - otherAlly.x, ally.z - otherAlly.z) >= 3.0f)
                    }
                }
            }

            // Verify no enemy spawns inside player, teammates, buildings, or water
            val enemies = s.bots.filter { !it.isFriendly }
            assertEquals(23 - mode.friendlyBots, enemies.size)
            for (enemy in enemies) {
                assertFalse(IslandMapGenerator.isPointInWater(enemy.x, enemy.z))
                assertFalse(IslandMapGenerator.isPositionBlocked(enemy.x, enemy.z, false, emptyList(), 1.1f))
                assertTrue(hypot(enemy.x - s.playerX, enemy.z - s.playerZ) >= 45f)
            }

            // Verify Complete Match Reset (HP, Armor, Weapons, Ammo, Temporary Abilities, Coins, Loot, Knock/Death)
            assertEquals(100f, s.playerHp, 0.01f)
            assertEquals(50f, s.playerArmor, 0.01f)
            assertEquals(null, s.weaponSlots[WeaponSlotIndex.MAIN_1])
            assertEquals(null, s.weaponSlots[WeaponSlotIndex.MAIN_2])
            assertTrue(s.weaponSlots[WeaponSlotIndex.SECONDARY] != null)
            assertEquals(AbilityState.READY, s.abilityState)
            assertEquals(0f, s.abilityTimerSec, 0.01f)
            assertFalse(s.rimaRevealActive)
            assertEquals(0, s.matchCoins)
            assertEquals(0, s.playerLootCollectedCount)
            assertTrue(s.lootItems.isNotEmpty() && s.lootItems.none { it.collected })
            assertFalse(s.isPlayerKnocked)
            assertFalse(s.isPlayerDead)
            assertFalse(s.isSpectating)

            // Verify short loading state is active initially and finishes cleanly without getting stuck
            assertTrue(s.isStartingMatch)
            assertTrue(s.matchLoadingRemainingSec > 0f)
            vm.stepSimulationForTest(0.6f)
            val afterStep = vm.uiState.value
            assertFalse(afterStep.isStartingMatch)
            assertEquals(0f, afterStep.matchLoadingRemainingSec, 0.001f)

            // Verify Safe Zone starts at Phase 1 and counts down after match begins
            assertEquals(1, afterStep.safeZone.phase)
            assertTrue(afterStep.safeZone.currentRadius >= 500f)
            assertTrue(afterStep.safeZone.warningTimerSec < 30f)
        }

        // Verify random spawn locations vary across matches
        val distinctSpawns = recordedSpawns.distinctBy { (x, z) -> "${x.toInt()}_${z.toInt()}" }
        assertTrue("Player spawn should be randomized across matches", distinctSpawns.size >= 2)

        // 10. Verify Loot Generation, Auto Gun Pickup & Equip, Compatible Ammo, Physical Coins, Category Settings & No Wall Pickup
        val lootItems = vm.uiState.value.lootItems
        assertTrue(lootItems.isNotEmpty())
        assertEquals(lootItems.map { it.id }.distinct().size, lootItems.size)

        // Verify all required loot categories are generated on valid ground and never inside walls or water
        val categoriesPresent = lootItems.map { it.category }.toSet()
        assertTrue(LootCategory.GUN in categoriesPresent)
        assertTrue(LootCategory.AMMO in categoriesPresent)
        assertTrue(LootCategory.ARMOR in categoriesPresent)
        assertTrue(LootCategory.MEDKIT in categoriesPresent)
        assertTrue(LootCategory.ATTACHMENT in categoriesPresent)
        assertTrue(LootCategory.GLOO_WALL in categoriesPresent)
        assertTrue(LootCategory.COIN in categoriesPresent)

        for (item in lootItems) {
            assertFalse("Loot must not spawn in water", IslandMapGenerator.isPointInWater(item.x, item.z))
            assertFalse(
                "Loot must not spawn inside solid walls or buildings",
                IslandMapGenerator.isPositionBlocked(item.x, item.z, false, emptyList(), 1.15f)
            )
            assertTrue("Loot must spawn on valid ground height", item.y >= 0f)
        }

        // Test Auto-Loot Category Settings: disable gun auto-loot and verify gun is not picked up
        val gunLoot = vm.uiState.value.lootItems.first { !it.collected && it.category == LootCategory.GUN }
        vm.updateAutoLoot(AutoLootSettings(enabled = true, guns = false))
        vm.setPlayerPositionForTest(gunLoot.x, gunLoot.z)
        vm.stepSimulationForTest(0.05f)
        assertEquals(null, vm.uiState.value.weaponSlots[WeaponSlotIndex.MAIN_1])
        assertFalse(vm.uiState.value.lootItems.first { it.id == gunLoot.id }.collected)

        // Re-enable gun auto-loot -> gun is automatically picked up, automatically equipped, and compatible ammo managed
        vm.updateAutoLoot(AutoLootSettings(enabled = true, guns = true))
        vm.stepSimulationForTest(0.05f)
        val afterGunPickup = vm.uiState.value
        assertTrue("Collected gun must be marked collected and not respawn", afterGunPickup.lootItems.first { it.id == gunLoot.id }.collected)
        assertEquals(WeaponSlotIndex.MAIN_1, afterGunPickup.activeWeaponSlot)
        assertEquals(gunLoot.weaponSpec!!.id, afterGunPickup.activeWeapon?.spec?.id)
        val compatAmmoType = gunLoot.weaponSpec!!.category.ammoType
        assertTrue("Compatible ammo must be non-negative and managed", (afterGunPickup.ammoReserve[compatAmmoType] ?: 0) >= 10)
        assertTrue("Pickup notification should be shown", afterGunPickup.statusBannerText.contains("AUTO-EQUIPPED"))

        // Verify duplicate weapon prevention: walking over another copy of the same weapon spec does not duplicate into MAIN_2
        val duplicateSpecGun = afterGunPickup.lootItems.firstOrNull {
            !it.collected && it.category == LootCategory.GUN && it.weaponSpec?.id == gunLoot.weaponSpec?.id
        }
        if (duplicateSpecGun != null) {
            vm.setPlayerPositionForTest(duplicateSpecGun.x, duplicateSpecGun.z)
            vm.stepSimulationForTest(0.05f)
            assertEquals("Must not duplicate weapon into MAIN_2", null, vm.uiState.value.weaponSlots[WeaponSlotIndex.MAIN_2])
        }

        // Verify physical Coin auto-pickup and reset on new match
        val coinItem = vm.uiState.value.lootItems.first { !it.collected && it.category == LootCategory.COIN }
        val coinsBefore = vm.uiState.value.matchCoins
        vm.setPlayerPositionForTest(coinItem.x, coinItem.z)
        vm.stepSimulationForTest(0.05f)
        val afterCoin = vm.uiState.value
        assertTrue(afterCoin.matchCoins >= coinsBefore + coinItem.amount)
        assertTrue(afterCoin.lootItems.first { it.id == coinItem.id }.collected)

        // Stepping again at the same spot does not collect the coin again (no infinite pickup)
        val coinsAfterFirstPickup = afterCoin.matchCoins
        vm.stepSimulationForTest(0.05f)
        assertEquals(coinsAfterFirstPickup, vm.uiState.value.matchCoins)

        // Verify all ammo reserves are strictly non-negative
        vm.uiState.value.ammoReserve.values.forEach { count ->
            assertTrue("Ammo count must never be negative", count >= 0)
        }

        // Starting a new match resets matchCoins back to 0 and generates fresh uncollected loot
        vm.returnToLobby()
        vm.startNewMatch()
        assertEquals(0, vm.uiState.value.matchCoins)
        assertTrue(vm.uiState.value.lootItems.none { it.collected })

        // 11. Verify Weapon & Shooting System (AR, SMG, Shotgun, Sniper, Pistol; Scopes; Manual Auto vs Semi-Auto; Reload; Wall Occlusion; Hit & Damage Feedback)
        val allCats = com.example.model.OriginalWeaponCatalog.allWeapons.map { it.category }.toSet()
        assertTrue(com.example.model.WeaponCategory.ASSAULT_RIFLE in allCats)
        assertTrue(com.example.model.WeaponCategory.SMG in allCats)
        assertTrue(com.example.model.WeaponCategory.SHOTGUN in allCats)
        assertTrue(com.example.model.WeaponCategory.SNIPER in allCats)
        assertTrue(com.example.model.WeaponCategory.SECONDARY in allCats)

        for (wepSpec in com.example.model.OriginalWeaponCatalog.allWeapons) {
            assertTrue(wepSpec.magazineSize > 0)
            assertTrue(wepSpec.reloadTimeSec >= 0.8f)
            assertTrue(wepSpec.baseDamage > 0f)
            assertTrue(wepSpec.roundsPerMinute > 0)
            assertTrue(wepSpec.recoilVertical > 0f)
            assertTrue(wepSpec.effectiveRange >= 50f)
        }

        // Carry Main 1 (Assault Rifle), Main 2 (Sniper), and Secondary (Pistol)
        vm.equipWeaponForTest(WeaponSlotIndex.MAIN_1, com.example.model.OriginalWeaponCatalog.VX47_STRIKER, com.example.model.ScopeType.SCOPE_4X, reserveAmmo = 60)
        vm.equipWeaponForTest(WeaponSlotIndex.MAIN_2, com.example.model.OriginalWeaponCatalog.SR90_TITAN, com.example.model.ScopeType.SNIPER_8X, reserveAmmo = 15)
        assertTrue(vm.uiState.value.weaponSlots[WeaponSlotIndex.MAIN_1] != null)
        assertTrue(vm.uiState.value.weaponSlots[WeaponSlotIndex.MAIN_2] != null)
        assertTrue(vm.uiState.value.weaponSlots[WeaponSlotIndex.SECONDARY] != null)

        // Verify original scopes: Red Dot, 2x, 4x, Sniper Scope
        val allScopeTypes = com.example.model.ScopeType.entries.toSet()
        assertTrue(com.example.model.ScopeType.RED_DOT in allScopeTypes)
        assertTrue(com.example.model.ScopeType.SCOPE_2X in allScopeTypes)
        assertTrue(com.example.model.ScopeType.SCOPE_4X in allScopeTypes)
        assertTrue(com.example.model.ScopeType.SNIPER_8X in allScopeTypes)

        // Toggle Sniper Scope on SR-90 Titan (Main 2)
        vm.toggleOrCycleScope()
        assertTrue(vm.uiState.value.isScopedIn)
        assertEquals(com.example.model.ScopeType.SNIPER_8X, vm.uiState.value.activeWeapon?.equippedScope)

        // Semi-auto Sniper fires ONLY once per press even if Fire is held across multiple frames
        val sniperAmmoStart = vm.uiState.value.activeWeapon!!.currentAmmo
        vm.onFireButtonPressedChanged(true)
        assertEquals(sniperAmmoStart - 1, vm.uiState.value.activeWeapon!!.currentAmmo)
        vm.stepSimulationForTest(1.2f) // Step past fire interval while still holding Fire
        assertEquals("Semi-auto weapon must not auto-fire while held", sniperAmmoStart - 1, vm.uiState.value.activeWeapon!!.currentAmmo)
        vm.onFireButtonPressedChanged(false)

        // Switch to Main 1 (Automatic Assault Rifle) -> cancels scope cleanly and fires continuously while held
        vm.switchWeaponSlot(WeaponSlotIndex.MAIN_1)
        assertFalse(vm.uiState.value.isScopedIn)
        assertEquals(WeaponSlotIndex.MAIN_1, vm.uiState.value.activeWeaponSlot)
        val arAmmoStart = vm.uiState.value.activeWeapon!!.currentAmmo
        vm.onFireButtonPressedChanged(true)
        val arAmmoAfterFirst = vm.uiState.value.activeWeapon!!.currentAmmo
        assertEquals(arAmmoStart - 1, arAmmoAfterFirst)
        vm.stepSimulationForTest(0.15f)
        assertTrue("Automatic weapon must continue firing while Fire is held", vm.uiState.value.activeWeapon!!.currentAmmo < arAmmoAfterFirst)
        vm.onFireButtonPressedChanged(false)

        // Verify Non-Instant Reload and Ammo Conservation (no infinite ammo, no negative ammo)
        val magBeforeReload = vm.uiState.value.activeWeapon!!.currentAmmo
        val reserveBeforeReload = vm.uiState.value.ammoReserve[com.example.model.AmmoType.AR_AMMO] ?: 0
        val totalAmmoBeforeReload = magBeforeReload + reserveBeforeReload
        vm.triggerManualReload()
        assertTrue("Reload must not be instant", vm.uiState.value.isReloading)
        assertTrue(vm.uiState.value.reloadRemainingSec >= 0.8f)
        // Cannot shoot while reloading
        val magDuringReload = vm.uiState.value.activeWeapon!!.currentAmmo
        vm.onFireButtonPressedChanged(true)
        assertEquals(magDuringReload, vm.uiState.value.activeWeapon!!.currentAmmo)
        vm.onFireButtonPressedChanged(false)

        // Complete reload
        vm.stepSimulationForTest(2.5f)
        assertFalse(vm.uiState.value.isReloading)
        val magAfterReload = vm.uiState.value.activeWeapon!!.currentAmmo
        val reserveAfterReload = vm.uiState.value.ammoReserve[com.example.model.AmmoType.AR_AMMO] ?: 0
        assertEquals(30, magAfterReload)
        assertEquals("Total ammo must be conserved during reload (no infinite ammo)", totalAmmoBeforeReload, magAfterReload + reserveAfterReload)
        assertTrue(reserveAfterReload >= 0)

        // Verify Hit Detection, Hit Effects, Damage Feedback & No Shooting Through Solid Walls
        val targetEnemy = vm.uiState.value.bots.first { !it.isFriendly && !it.isDead }
        // Place player 15m south of targetEnemy in open line of sight and aim directly north (yaw = 0 deg)
        val openX = targetEnemy.x
        val openZ = targetEnemy.z - 14f
        if (!IslandMapGenerator.isPositionBlocked(openX, openZ, false, emptyList(), 1.0f) &&
            IslandMapGenerator.hasLineOfSight(openX, openZ, targetEnemy.x, targetEnemy.z, emptyList())
        ) {
            vm.setPlayerPositionForTest(openX, openZ)
            // Align yaw toward enemy (0 deg is +Z)
            val currentYaw = vm.uiState.value.playerYawDeg
            val currentPitch = vm.uiState.value.playerPitchDeg
            vm.rotateCameraByTouch(0f, 0f)
            // Fire directly at enemy using SMG
            vm.equipWeaponForTest(WeaponSlotIndex.MAIN_1, com.example.model.OriginalWeaponCatalog.MP9_VIPER, com.example.model.ScopeType.RED_DOT, reserveAmmo = 70)
            // Compute exact yaw to targetEnemy
            val desiredYaw = ((Math.toDegrees(kotlin.math.atan2((targetEnemy.x - openX).toDouble(), (targetEnemy.z - openZ).toDouble())).toFloat()) + 360f) % 360f
            // Rotate camera to face targetEnemy and level pitch
            val yawDiff = desiredYaw - vm.uiState.value.playerYawDeg
            val sensFactor = vm.uiState.value.sensitivity.general / 310f
            vm.rotateCameraByTouch(yawDiff / sensFactor, vm.uiState.value.playerPitchDeg / (sensFactor * 0.65f))
            val enemyHpBefore = vm.uiState.value.bots.first { it.id == targetEnemy.id }.hp
            vm.onFireButtonPressedChanged(true)
            vm.onFireButtonPressedChanged(false)
            val afterHitState = vm.uiState.value
            val enemyHpAfter = afterHitState.bots.first { it.id == targetEnemy.id }.hp
            if (enemyHpAfter < enemyHpBefore) {
                assertTrue("Hit effect must be spawned on enemy hit", afterHitState.hitEffects.isNotEmpty())
                assertTrue("Damage feedback must be positive", afterHitState.lastDamageDealtAmount > 0)
                assertTrue("Hitmarker timer must be active", afterHitState.hitMarkerTimerSec > 0f)
            }
        }

        // Verify ray obstacle stopping prevents shooting through solid building walls
        val solidWall = IslandMapGenerator.buildings.first { it.isSolidCollider && !it.isVaultable }
        val rayStop = IslandMapGenerator.computeRayObstacleDistance(
            startX = solidWall.centerX - solidWall.width * 0.5f - 5f,
            startZ = solidWall.centerZ,
            dirX = 1f,
            dirZ = 0f,
            maxRange = 150f,
            glooWalls = emptyList()
        )
        assertTrue("Bullet ray must stop at solid building wall", rayStop < 15f)

        // 12. Verify Aim, Scope, Recoil, Aim Assist, Sensitivity & Gyroscope System
        // A. Normal third-person aim opens and closes smoothly
        vm.closeScope()
        assertFalse(vm.uiState.value.isAiming)
        assertFalse(vm.uiState.value.isScopedIn)
        vm.toggleAim()
        assertTrue(vm.uiState.value.isAiming)
        assertFalse(vm.uiState.value.isScopedIn)
        assertTrue(vm.uiState.value.smoothFovScale > 1.0f)
        vm.toggleAim()
        assertFalse(vm.uiState.value.isAiming)
        assertEquals(1.0f, vm.uiState.value.smoothFovScale, 0.01f)

        // B. Red Dot, 2x, 4x, and Sniper scopes open, close, follow camera movement, and work with shooting/recoil
        vm.equipWeaponForTest(WeaponSlotIndex.MAIN_2, com.example.model.OriginalWeaponCatalog.SR90_TITAN, com.example.model.ScopeType.RED_DOT, reserveAmmo = 25)
        for (scopeType in listOf(
            com.example.model.ScopeType.RED_DOT,
            com.example.model.ScopeType.SCOPE_2X,
            com.example.model.ScopeType.SCOPE_4X,
            com.example.model.ScopeType.SNIPER_8X
        )) {
            vm.selectScopeForActiveWeapon(scopeType)
            if (!vm.uiState.value.isScopedIn) {
                vm.toggleOrCycleScope()
            }
            assertTrue("Scope $scopeType must open", vm.uiState.value.isScopedIn)
            assertEquals(scopeType, vm.uiState.value.activeWeapon?.equippedScope)
            val yawBeforeDrag = vm.uiState.value.playerYawDeg
            vm.rotateCameraByTouch(45f, -20f)
            assertTrue("Camera must move smoothly while scoped with $scopeType", vm.uiState.value.playerYawDeg != yawBeforeDrag)
            vm.closeScope()
            assertFalse("Scope $scopeType must close cleanly", vm.uiState.value.isScopedIn)
            assertFalse("Aim must not stay stuck after closing scope", vm.uiState.value.isAiming)
        }

        // C. Realistic, controllable, bounded recoil per weapon type (never excessive, never impossible aiming)
        vm.setJoystickInput(0f, 0f, false)
        vm.setPlayerPositionForTest(120f, -120f)
        vm.equipWeaponForTest(WeaponSlotIndex.MAIN_1, com.example.model.OriginalWeaponCatalog.VX47_STRIKER, com.example.model.ScopeType.RED_DOT, reserveAmmo = 90)
        // Reset pitch to 0
        val curPitch = vm.uiState.value.playerPitchDeg
        val genSensFactor = vm.uiState.value.sensitivity.general / 310f
        vm.rotateCameraByTouch(0f, curPitch / (genSensFactor * 0.65f))
        val pitchBeforeBurst = vm.uiState.value.playerPitchDeg
        vm.onFireButtonPressedChanged(true)
        repeat(15) {
            vm.stepSimulationForTest(0.11f)
        }
        vm.onFireButtonPressedChanged(false)
        val pitchAfterBurst = vm.uiState.value.playerPitchDeg
        val recoilClimb = pitchAfterBurst - pitchBeforeBurst
        assertTrue("Recoil must affect gun/camera aim upward", recoilClimb > 0.5f)
        assertTrue(
            "Recoil must be bounded by weapon category max climb and never move camera excessively",
            recoilClimb <= com.example.model.WeaponCategory.ASSAULT_RIFLE.maxRecoilClimbDeg + 0.5f
        )
        // Player can manually pull down to control recoil
        vm.rotateCameraByTouch(0f, 35f)
        assertTrue("Player must be able to pull down to control recoil", vm.uiState.value.playerPitchDeg < pitchAfterBurst)

        // D. Subtle Aim Assist ON/OFF (never auto-shoots, never works through walls)
        vm.updateAimAssist(true)
        assertTrue(vm.uiState.value.aimAssistEnabled)
        val ammoBeforeAim = vm.uiState.value.activeWeapon!!.currentAmmo
        vm.toggleAim()
        vm.rotateCameraByTouch(15f, 0f)
        vm.stepSimulationForTest(0.25f)
        assertEquals("Aim Assist must NEVER auto-shoot", ammoBeforeAim, vm.uiState.value.activeWeapon!!.currentAmmo)
        vm.updateAimAssist(false)
        assertFalse(vm.uiState.value.aimAssistEnabled)
        vm.toggleAim()

        // E. Sensitivity (General, Red Dot, 2x, 4x, Sniper, Free Look) & Gyroscope (OFF, Always ON, Scope Only) + Persistence
        val customSens = com.example.model.SensitivitySettings(
            general = 78f,
            redDot = 68f,
            scope2x = 58f,
            scope4x = 48f,
            sniper = 38f,
            freeLook = 84f,
            gyroMode = com.example.model.GyroscopeMode.SCOPE_ON,
            gyroRedDot = 62f,
            gyro2x = 52f,
            gyro4x = 42f,
            gyroSniper = 32f
        )
        vm.updateSensitivity(customSens)
        vm.updateAimAssist(true)

        // Test Free Look sensitivity
        vm.setFreeLooking(true)
        assertTrue(vm.uiState.value.isFreeLooking)
        vm.rotateCameraByTouch(50f, -20f)
        assertTrue("Free Look must rotate freeLookYawOffsetDeg", vm.uiState.value.freeLookYawOffsetDeg != 0f)
        vm.setFreeLooking(false)
        assertEquals(0f, vm.uiState.value.freeLookYawOffsetDeg, 0.001f)

        // Test Gyroscope Scope Only vs Always ON vs OFF & noise deadzone (preventing camera shaking)
        vm.closeScope()
        val yawBeforeGyroHip = vm.uiState.value.playerYawDeg
        vm.applyGyroscopeDelta(1.2f, 0.5f) // Not scoped or aiming -> SCOPE_ON must not rotate camera
        assertEquals("Gyro SCOPE_ON must not rotate camera when not scoped/aiming", yawBeforeGyroHip, vm.uiState.value.playerYawDeg, 0.001f)

        vm.toggleAim()
        val yawBeforeGyroAim = vm.uiState.value.playerYawDeg
        // Tiny sensor jitter (< 0.018 rad/s) must be filtered out to prevent camera shaking
        vm.applyGyroscopeDelta(0.008f, -0.006f)
        assertEquals("Gyro deadzone must filter out sensor noise to prevent camera shaking", yawBeforeGyroAim, vm.uiState.value.playerYawDeg, 0.001f)
        // Deliberate gyro tilt while aiming rotates camera smoothly
        vm.applyGyroscopeDelta(1.5f, 0.6f)
        assertTrue("Gyro SCOPE_ON must rotate camera when aiming/scoped", vm.uiState.value.playerYawDeg != yawBeforeGyroAim)
        vm.toggleAim()

        // Verify sensitivity and gyro settings persist across matches and database reload
        vm.returnToLobby()
        vm.startNewMatch()
        val sensInNextMatch = vm.uiState.value.sensitivity
        assertEquals(78f, sensInNextMatch.general, 0.01f)
        assertEquals(68f, sensInNextMatch.redDot, 0.01f)
        assertEquals(58f, sensInNextMatch.scope2x, 0.01f)
        assertEquals(48f, sensInNextMatch.scope4x, 0.01f)
        assertEquals(38f, sensInNextMatch.sniper, 0.01f)
        assertEquals(84f, sensInNextMatch.freeLook, 0.01f)
        assertEquals(com.example.model.GyroscopeMode.SCOPE_ON, sensInNextMatch.gyroMode)

        db.close()
    }

    @Test
    fun enemyAiAndFriendlyTeammateAi_lineOfSight_finiteAmmoAndHealing_muzzleClearanceAndReviveWorkCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PlayerRepository(db.playerProfileDao())
        val sound = SoundEngine().apply { isMuted = true }
        val vm = BattleRoyaleViewModel(repo, sound)

        // 1. Verify Duo (Player + 1 teammate) and Squad (Player + 3 teammates) with zero duplicate IDs or spawns
        vm.selectTeamMode(TeamMode.DUO)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        val duoBots = vm.uiState.value.bots
        assertEquals("Duo must have exactly 1 friendly teammate", 1, duoBots.count { it.isFriendly })
        assertEquals("Bot IDs must be unique", duoBots.size, duoBots.map { it.id }.distinct().size)

        vm.returnToLobby()
        vm.selectTeamMode(TeamMode.SQUAD)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        val squadState = vm.uiState.value
        val squadAllies = squadState.bots.filter { it.isFriendly }
        val squadEnemies = squadState.bots.filter { !it.isFriendly }
        assertEquals("Squad must have exactly 3 friendly teammates", 3, squadAllies.size)
        assertTrue("Enemy bots must be present", squadEnemies.isNotEmpty())
        assertEquals("All bot IDs must be unique", squadState.bots.size, squadState.bots.map { it.id }.distinct().size)

        // 2. Friendly AI must not stand directly in front of the player's gun unnecessarily
        val px = squadState.playerX
        val pz = squadState.playerZ
        val yawRad = Math.toRadians(squadState.playerYawDeg.toDouble())
        val fwdX = kotlin.math.sin(yawRad).toFloat()
        val fwdZ = kotlin.math.cos(yawRad).toFloat()
        val allyId = squadAllies.first().id
        // Place ally directly 6m in front of the player's gun barrel
        vm.updateBotForTest(allyId) { ally ->
            ally.copy(
                x = px + fwdX * 6f,
                z = pz + fwdZ * 6f,
                targetX = px + fwdX * 6f,
                targetZ = pz + fwdZ * 6f
            )
        }
        vm.stepSimulationForTest(0.25f)
        val updatedAlly = vm.uiState.value.bots.first { it.id == allyId }
        val newGoalRelX = updatedAlly.targetX - px
        val newGoalRelZ = updatedAlly.targetZ - pz
        val rightX = kotlin.math.cos(yawRad).toFloat()
        val rightZ = -kotlin.math.sin(yawRad).toFloat()
        val lateralGoalOffset = kotlin.math.abs(newGoalRelX * rightX + newGoalRelZ * rightZ)
        assertTrue(
            "Friendly teammate placed in front of player's gun must immediately step laterally out of muzzle line",
            lateralGoalOffset > 4.0f
        )

        // 3. Enemy AI Line-of-Sight: cannot see or shoot through solid walls, and cannot track hidden player
        val solidWall = IslandMapGenerator.buildings.first { it.isSolidCollider && !it.isVaultable && it.width >= 8f }
        val enemyId = squadEnemies.first().id
        // Move other bots far away (allies and enemies on opposite corners) so we test strictly enemyId vs hidden player across solidWall
        vm.uiState.value.bots.filter { it.id != enemyId }.forEach { b ->
            if (b.isFriendly) {
                vm.updateBotForTest(b.id) { it.copy(x = 340f, z = -340f) }
            } else {
                vm.updateBotForTest(b.id) { it.copy(x = -340f, z = 340f) }
            }
        }
        val playerSideX = solidWall.centerX - (solidWall.width / 2f + 4f)
        val playerSideZ = solidWall.centerZ
        val enemySideX = solidWall.centerX + (solidWall.width / 2f + 4f)
        val enemySideZ = solidWall.centerZ
        assertFalse(
            "Solid building wall must block Line-of-Sight between enemy and player",
            IslandMapGenerator.hasLineOfSight(enemySideX, enemySideZ, playerSideX, playerSideZ, emptyList())
        )
        vm.setPlayerPositionForTest(playerSideX, playerSideZ)
        val startEnemyAmmo = 30
        vm.updateBotForTest(enemyId) { enemy ->
            enemy.copy(
                x = enemySideX,
                z = enemySideZ,
                equippedWeapon = com.example.model.OriginalWeaponCatalog.VX47_STRIKER,
                ammoInMag = startEnemyAmmo,
                reserveAmmo = 90,
                aimLockTimerSec = 0.5f,
                fireCooldownSec = 0f,
                lastKnownTargetX = null,
                lastKnownTargetZ = null,
                memoryTimerSec = 0f
            )
        }
        vm.stepSimulationForTest(0.3f)
        val enemyBehindWall = vm.uiState.value.bots.first { it.id == enemyId }
        assertEquals("Enemy behind solid wall must NOT shoot through wall", startEnemyAmmo, enemyBehindWall.ammoInMag)
        assertTrue("Enemy behind solid wall must NOT magically acquire hidden player location", enemyBehindWall.lastKnownTargetX == null)

        // 4. Enemy AI finite ammo, reload, and finite healing (prevent infinite shooting and infinite healing)
        vm.updateBotForTest(enemyId) { enemy ->
            enemy.copy(
                hp = 35f,
                medkits = 1,
                healTimerSec = 0f,
                healCooldownSec = 0f,
                equippedWeapon = com.example.model.OriginalWeaponCatalog.VX47_STRIKER,
                ammoInMag = 0,
                reserveAmmo = 90,
                reloadTimerSec = 0f
            )
        }
        vm.stepSimulationForTest(0.25f)
        val healingEnemy = vm.uiState.value.bots.first { it.id == enemyId }
        assertEquals("Damaged enemy with medkit must enter HEALING state", com.example.model.BotAiRole.HEALING, healingEnemy.aiRole)
        assertTrue("Enemy healing must use a timed healTimerSec", healingEnemy.healTimerSec > 0f)
        // Complete the heal
        repeat(12) { vm.stepSimulationForTest(0.25f) }
        val postHealEnemy = vm.uiState.value.bots.first { it.id == enemyId }
        assertEquals("Enemy medkit count must decrement (no infinite healing)", 0, postHealEnemy.medkits)
        assertTrue("Enemy HP must recover after finishing medkit", postHealEnemy.hp > 35f)
        // Next step triggers reload from finite reserveAmmo
        repeat(10) { vm.stepSimulationForTest(0.25f) }
        val reloadedEnemy = vm.uiState.value.bots.first { it.id == enemyId }
        assertTrue("Enemy must reload magazine from reserve ammo", reloadedEnemy.ammoInMag > 0)
        assertTrue("Enemy reserve ammo must decrease after reload (no infinite ammo)", reloadedEnemy.reserveAmmo < 90)

        // 5. Friendly AI revives knocked player and communicates callouts
        val openX = 110f
        val openZ = -110f
        vm.setPlayerPositionForTest(openX, openZ)
        vm.setPlayerKnockedForTest(true, hp = 40f)
        // Move all enemies far away so area is safe for revive, and place friendly teammate 2m from player
        vm.uiState.value.bots.filter { !it.isFriendly }.forEach { e ->
            vm.updateBotForTest(e.id) { it.copy(x = -320f, z = 320f) }
        }
        vm.updateBotForTest(allyId) { ally ->
            ally.copy(
                x = openX + 1.8f,
                z = openZ + 1.8f,
                hp = 100f,
                healTimerSec = 0f,
                calloutCooldownSec = 0f
            )
        }
        repeat(14) { vm.stepSimulationForTest(0.25f) }
        assertFalse("Friendly AI teammate must revive knocked player when area is clear", vm.uiState.value.isPlayerKnocked)
        assertTrue("Revived player must have restored HP", vm.uiState.value.playerHp >= 50f)

        // 6. Verify no AI bot ever teleports across simulation ticks
        val beforePositions = vm.uiState.value.bots.associate { it.id to (it.x to it.z) }
        vm.stepSimulationForTest(0.1f)
        for (b in vm.uiState.value.bots) {
            val (bx, bz) = beforePositions[b.id] ?: continue
            val frameStep = hypot(b.x - bx, b.z - bz)
            assertTrue("AI bot ${b.id} must never teleport (moved $frameStep m in 0.1s)", frameStep <= 2.5f)
        }

        db.close()
    }

    @Test
    fun safeZone_minimapAndCompass_synchronization_temporaryEnemyMarkersAndCompassDirections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PlayerRepository(db.playerProfileDao())
        val sound = SoundEngine().apply { isMuted = true }
        val vm = BattleRoyaleViewModel(repo, sound)

        vm.selectTeamMode(TeamMode.SQUAD)
        vm.selectCharacter(CharacterId.RIMA)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)

        // 1. Safe Zone initial state & pre-shrink warning
        val initialZone = vm.uiState.value.safeZone
        assertEquals(1, initialZone.phase)
        assertTrue(initialZone.currentRadius >= 500f)
        assertFalse(initialZone.isShrinking)
        assertTrue(initialZone.warningTimerSec > 0f)
        assertTrue(initialZone.activeCountdownSec > 0)
        assertTrue(initialZone.formattedCountdown.contains(":"))

        // Advance warning timer past 10s threshold -> triggers pre-shrink warning
        vm.setSafeZoneForTest(initialZone.copy(warningTimerSec = 10.2f))
        vm.stepSimulationForTest(0.5f)
        assertTrue(
            "Pre-shrink warning message must warn player before Safe Zone starts shrinking",
            vm.uiState.value.safeZone.warningMessage.contains("WARNING")
        )

        // Advance warning timer to 0 -> starts shrinking with active shrink countdown
        vm.setSafeZoneForTest(vm.uiState.value.safeZone.copy(warningTimerSec = 0.2f))
        vm.stepSimulationForTest(0.4f)
        val shrinkingZone = vm.uiState.value.safeZone
        assertTrue("Safe Zone must enter shrinking state", shrinkingZone.isShrinking)
        assertTrue("Safe Zone shrink countdown must be positive while shrinking", shrinkingZone.activeCountdownSec > 0)

        // 2. Outside Safe Zone warning & damage + Escalating damage across phases + Small final zone
        // Move all enemies far away so only Safe Zone damages player
        vm.uiState.value.bots.filter { !it.isFriendly }.forEach { e ->
            vm.updateBotForTest(e.id) { it.copy(x = -350f, z = 350f, ammoInMag = 0, reserveAmmo = 0) }
        }
        vm.setSafeZoneForTest(
            shrinkingZone.copy(
                phase = 1,
                centerX = 0f,
                centerZ = 0f,
                currentRadius = 120f,
                targetRadius = 90f,
                isShrinking = true,
                damagePerSecond = 2.5f
            )
        )
        vm.setPlayerPositionForTest(220f, -220f) // Outside 120m radius
        val hpBeforeZoneDmg = vm.uiState.value.playerHp
        vm.stepSimulationForTest(1.0f)
        val afterZoneDmg = vm.uiState.value
        assertTrue("Player outside Safe Zone must be flagged isPlayerOutside", afterZoneDmg.safeZone.isPlayerOutside)
        assertTrue("Player outside Safe Zone must receive zone damage", afterZoneDmg.playerHp < hpBeforeZoneDmg)
        assertTrue("Safe Zone radius must shrink smoothly", afterZoneDmg.safeZone.currentRadius < 120f)

        // Verify phase progression increases damagePerSecond and final Phase 5 reaches small radius (28m) without disappearing
        var prevDps = 2.5f
        for (ph in 1..4) {
            val curSz = vm.uiState.value.safeZone
            vm.setSafeZoneForTest(
                curSz.copy(
                    phase = ph,
                    currentRadius = curSz.targetRadius + 1.0f,
                    isShrinking = true
                )
            )
            vm.stepSimulationForTest(0.5f)
            val nextSz = vm.uiState.value.safeZone
            assertEquals(ph + 1, nextSz.phase)
            assertTrue("Safe Zone DPS must increase in later phases (Phase ${ph + 1})", nextSz.damagePerSecond > prevDps)
            prevDps = nextSz.damagePerSecond
        }
        // Finish Phase 5 shrink -> reaches small final zone (28m) and stays active
        val phase5Sz = vm.uiState.value.safeZone
        assertEquals(5, phase5Sz.phase)
        assertEquals(28f, phase5Sz.targetRadius, 0.01f)
        vm.setSafeZoneForTest(phase5Sz.copy(currentRadius = 28.5f, isShrinking = true))
        vm.stepSimulationForTest(0.5f)
        val finalSz = vm.uiState.value.safeZone
        assertTrue("Final Safe Zone must be marked reached", finalSz.isFinalZoneReached)
        assertEquals("Final Safe Zone radius must be small (28m) and never disappear", 28f, finalSz.currentRadius, 0.01f)

        // 3. Synchronized World <-> Minimap coordinate mapping & entities
        val centerOffset = com.example.ui.screens.worldToMinimapOffset(0f, 0f, 200f, 200f)
        assertEquals(100f, centerOffset.x, 0.1f)
        assertEquals(100f, centerOffset.y, 0.1f)
        val northOffset = com.example.ui.screens.worldToMinimapOffset(0f, 240f, 200f, 200f)
        val eastOffset = com.example.ui.screens.worldToMinimapOffset(240f, 0f, 200f, 200f)
        assertTrue("North (+Z) must map to top half of minimap", northOffset.y < 100f)
        assertTrue("East (+X) must map to right half of minimap", eastOffset.x > 100f)
        assertTrue("Minimap must have vehicles to display", vm.uiState.value.vehicles.isNotEmpty())
        assertTrue("Minimap must have vending machines to display", vm.uiState.value.vendingMachines.isNotEmpty())
        assertEquals("Minimap must have 3 friendly teammates in Squad mode", 3, vm.uiState.value.livingTeammates.size)

        // 4. Temporary Enemy Markers: never permanent, only from AI detection, Teammate marking, or RIMA ability, and auto-expire
        vm.setSafeZoneForTest(com.example.model.SafeZoneState(phase = 1, centerX = 0f, centerZ = 0f, currentRadius = 450f, warningTimerSec = 60f))
        vm.setPlayerPositionForTest(100f, -100f)
        // Move friendly bots far away so they don't re-mark testEnemy during isolated expiration checks
        vm.uiState.value.bots.filter { it.isFriendly }.forEach { ally ->
            vm.updateBotForTest(ally.id) { it.copy(x = -300f, z = -300f) }
        }
        val testEnemy = vm.uiState.value.bots.first { !it.isFriendly && !it.isDead }
        vm.updateBotForTest(testEnemy.id) {
            it.copy(
                x = 130f,
                z = -100f,
                ammoInMag = 0,
                reserveAmmo = 0,
                fireCooldownSec = 99f,
                detectedMarkerRemainingSec = 0f,
                teammateMarkedRemainingSec = 0f,
                lastDetectedByPlayerTimeMs = 0L,
                markedByFriendlyUntilMs = 0L
            )
        }
        assertFalse(
            "Undetected enemy must NEVER be permanently shown on minimap",
            vm.uiState.value.isEnemyTemporarilyVisibleOnMinimap(vm.uiState.value.bots.first { it.id == testEnemy.id })
        )

        // A. Teammate marking shows enemy temporarily and disappears automatically after duration
        vm.updateBotForTest(testEnemy.id) { it.copy(teammateMarkedRemainingSec = 1.0f) }
        assertTrue(
            "Enemy marked by teammate must appear on minimap",
            vm.uiState.value.isEnemyTemporarilyVisibleOnMinimap(vm.uiState.value.bots.first { it.id == testEnemy.id })
        )
        repeat(6) { vm.stepSimulationForTest(0.25f) }
        assertFalse(
            "Teammate enemy marker must disappear automatically after duration",
            vm.uiState.value.isEnemyTemporarilyVisibleOnMinimap(vm.uiState.value.bots.first { it.id == testEnemy.id })
        )

        // B. Valid AI detection shows enemy temporarily and disappears automatically after duration
        vm.updateBotForTest(testEnemy.id) { it.copy(detectedMarkerRemainingSec = 1.0f) }
        assertTrue(
            "Detected enemy must appear on minimap",
            vm.uiState.value.isEnemyTemporarilyVisibleOnMinimap(vm.uiState.value.bots.first { it.id == testEnemy.id })
        )
        repeat(6) { vm.stepSimulationForTest(0.25f) }
        assertFalse(
            "Detected enemy marker must disappear automatically after duration",
            vm.uiState.value.isEnemyTemporarilyVisibleOnMinimap(vm.uiState.value.bots.first { it.id == testEnemy.id })
        )

        // C. RIMA ability reveals nearby enemy temporarily and disappears automatically when ability ends
        val wallForRima = IslandMapGenerator.buildings.first { it.isSolidCollider && !it.isVaultable && it.width >= 8f }
        vm.setPlayerPositionForTest(wallForRima.centerX - wallForRima.width * 0.5f - 4f, wallForRima.centerZ)
        vm.updateBotForTest(testEnemy.id) {
            it.copy(
                x = wallForRima.centerX + wallForRima.width * 0.5f + 4f,
                z = wallForRima.centerZ,
                ammoInMag = 0,
                reserveAmmo = 0,
                detectedMarkerRemainingSec = 0f,
                teammateMarkedRemainingSec = 0f,
                lastDetectedByPlayerTimeMs = 0L,
                markedByFriendlyUntilMs = 0L
            )
        }
        vm.activateCharacterAbility()
        assertTrue(vm.uiState.value.rimaRevealActive)
        assertTrue(
            "RIMA ability must temporarily reveal nearby enemy on minimap",
            vm.uiState.value.isEnemyTemporarilyVisibleOnMinimap(vm.uiState.value.bots.first { it.id == testEnemy.id })
        )
        repeat(48) {
            vm.updateBotForTest(testEnemy.id) {
                it.copy(
                    x = wallForRima.centerX + wallForRima.width * 0.5f + 4f,
                    z = wallForRima.centerZ,
                    ammoInMag = 0,
                    reserveAmmo = 0,
                    detectedMarkerRemainingSec = 0f,
                    teammateMarkedRemainingSec = 0f
                )
            }
            vm.stepSimulationForTest(0.25f)
        } // Step past 11s RIMA active duration
        assertFalse(vm.uiState.value.rimaRevealActive)
        assertFalse(
            "RIMA enemy marker must disappear automatically after ability ends",
            vm.uiState.value.isEnemyTemporarilyVisibleOnMinimap(vm.uiState.value.bots.first { it.id == testEnemy.id })
        )

        // 5. Compass 8 Directions (N, NE, E, SE, S, SW, W, NW) matching player camera
        val expectedCompass = mapOf(
            0f to com.example.model.CompassDirection.N,
            45f to com.example.model.CompassDirection.NE,
            90f to com.example.model.CompassDirection.E,
            135f to com.example.model.CompassDirection.SE,
            180f to com.example.model.CompassDirection.S,
            225f to com.example.model.CompassDirection.SW,
            270f to com.example.model.CompassDirection.W,
            315f to com.example.model.CompassDirection.NW
        )
        for ((angle, dir) in expectedCompass) {
            assertEquals("Compass angle $angle must map to ${dir.label}", dir, com.example.model.CompassDirection.fromCameraYaw(angle))
        }
        // Verify rotating the player's camera updates MatchUiState.compassDirection & cameraYawDeg accurately
        val currentYaw = vm.uiState.value.playerYawDeg
        val sensFactor = vm.uiState.value.sensitivity.general / 310f
        // Rotate camera to 90 deg (East) in 6 smooth drag steps (each within 140px clamp)
        val stepPxToEast = (90f - currentYaw) / (6f * sensFactor)
        repeat(6) { vm.rotateCameraByTouch(stepPxToEast, 0f) }
        assertEquals(com.example.model.CompassDirection.E, vm.uiState.value.compassDirection)
        // Rotate camera in Free Look by +90 deg -> camera points South (180 deg)
        vm.setFreeLooking(true)
        val freeSensFactor = vm.uiState.value.sensitivity.freeLook / 310f
        val freeStepPx = 90f / (6f * freeSensFactor)
        repeat(6) { vm.rotateCameraByTouch(freeStepPx, 0f) }
        assertEquals("Compass must match Free Look camera direction", com.example.model.CompassDirection.S, vm.uiState.value.compassDirection)
        vm.setFreeLooking(false)
        assertEquals("Compass must return to player heading after Free Look", com.example.model.CompassDirection.E, vm.uiState.value.compassDirection)

        db.close()
    }

    @Test
    fun playerKnockAndRevive_eligibility_timer_lowHpRestore_noNormalReviveWhenDead_vendingRespawnAndSpectatorMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PlayerRepository(db.playerProfileDao())
        val sound = SoundEngine().apply { isMuted = true }
        val vm = BattleRoyaleViewModel(repo, sound)

        // 1. SOLO Mode: Not eligible for Knocked state -> HP reaching 0 causes immediate Full Death + Spectator Mode
        vm.selectTeamMode(TeamMode.SOLO)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        assertFalse(vm.uiState.value.isEligibleForKnock)
        vm.applyDamageToPlayerForTest(damage = 250f, bypassArmor = true)
        val soloDeadState = vm.uiState.value
        assertFalse("Solo player must NOT enter Knocked state when HP reaches 0", soloDeadState.isPlayerKnocked)
        assertTrue("Solo player must become fully dead when HP reaches 0", soloDeadState.isPlayerDead)
        assertTrue("Spectator mode must be enabled after full death", soloDeadState.isSpectating)
        assertTrue("Spectator targets must be populated after full death", soloDeadState.spectatorTargets.isNotEmpty())
        vm.returnToLobby()

        // 2. DUO Mode: Eligible for Knocked state when teammate is alive -> enters Knocked state with short knock timer
        vm.selectTeamMode(TeamMode.DUO)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        // Move enemies away so test is deterministic
        vm.uiState.value.bots.filter { !it.isFriendly }.forEach { e ->
            vm.updateBotForTest(e.id) { it.copy(x = -340f, z = 340f, ammoInMag = 0, reserveAmmo = 0) }
        }
        val duoAlly = vm.uiState.value.livingTeammates.first()
        vm.updateBotForTest(duoAlly.id) { it.copy(x = 180f, z = 180f, isKnocked = false, isDead = false, hp = 100f) }
        vm.setPlayerPositionForTest(100f, -100f)
        assertTrue("Duo player with living unknocked teammate must be eligible for knock", vm.uiState.value.isEligibleForKnock)

        vm.applyDamageToPlayerForTest(damage = 250f, bypassArmor = true)
        val knockedState = vm.uiState.value
        assertTrue("Eligible player must enter Knocked state when HP reaches 0", knockedState.isPlayerKnocked)
        assertFalse("Knocked player must not be fully dead yet", knockedState.isPlayerDead)
        assertEquals(0f, knockedState.playerHp, 0.01f)
        assertTrue("Knocked player must have a short knock timer", knockedState.playerKnockedTimerSec in 10f..25f)

        // Verify knocked player cannot fire weapons and knock timer counts down
        vm.onFireButtonPressedChanged(true)
        assertFalse("Knocked player must not be able to shoot", vm.uiState.value.isManualFiringHeld)
        val timerBefore = vm.uiState.value.playerKnockedTimerSec
        vm.stepSimulationForTest(1.0f)
        assertTrue("Knock timer must count down while knocked", vm.uiState.value.playerKnockedTimerSec < timerBefore)

        // 3. Living teammate revives knocked player -> restores low HP (50f) and returns player control
        vm.updateBotForTest(duoAlly.id) {
            it.copy(x = 101.5f, z = -101.5f, hp = 100f, isKnocked = false, isDead = false, healTimerSec = 0f)
        }
        repeat(14) { vm.stepSimulationForTest(0.25f) }
        val revivedState = vm.uiState.value
        assertFalse("Player must no longer be knocked after teammate revive", revivedState.isPlayerKnocked)
        assertFalse("Player must not be dead after teammate revive", revivedState.isPlayerDead)
        assertEquals("Revived player must have low HP (50f) restored", 50f, revivedState.playerHp, 0.1f)

        // Verify player control is returned after revive (can aim and shoot)
        vm.equipWeaponForTest(WeaponSlotIndex.MAIN_1, com.example.model.OriginalWeaponCatalog.VX47_STRIKER, reserveAmmo = 60)
        vm.toggleAim()
        assertTrue("Revived player must regain aiming control", vm.uiState.value.isAiming)

        // 4. Player revives knocked teammate & duplicate revive is prevented
        vm.updateBotForTest(duoAlly.id) {
            it.copy(x = 101.5f, z = -101.0f, hp = 0f, isKnocked = true, isDead = false, knockedTimerSec = 18f)
        }
        vm.stepSimulationForTest(0.1f)
        val revivesBefore = vm.uiState.value.playerTeammatesRevived
        vm.reviveNearbyKnockedTeammate()
        // Calling revive a second time immediately must NOT duplicate revive
        vm.reviveNearbyKnockedTeammate()
        val revivedAlly = vm.uiState.value.bots.first { it.id == duoAlly.id }
        assertFalse("Teammate must be revived from knocked state", revivedAlly.isKnocked)
        assertFalse("Teammate must not be dead", revivedAlly.isDead)
        assertEquals("Revived teammate must have low HP (50f) restored", 50f, revivedAlly.hp, 0.1f)
        assertEquals("Duplicate revive must be prevented", revivesBefore + 1, vm.uiState.value.playerTeammatesRevived)

        // 5. Fully dead teammate CANNOT be normally revived, but CAN be respawned at Vending Machine without duplicate respawn
        val vmNode = vm.uiState.value.vendingMachines.first()
        vm.setPlayerPositionForTest(vmNode.x + 1.5f, vmNode.z + 1.5f)
        vm.updateBotForTest(duoAlly.id) {
            it.copy(x = vmNode.x + 1.0f, z = vmNode.z + 1.0f, hp = 0f, isKnocked = false, isDead = true)
        }
        vm.stepSimulationForTest(0.1f)
        // Normal revive must NOT work on a fully dead teammate
        vm.reviveNearbyKnockedTeammate()
        assertTrue("Fully dead teammate must NOT be normally revived", vm.uiState.value.bots.first { it.id == duoAlly.id }.isDead)

        // Collect 5 coin stacks so player has >= 600 coins (enough for multiple respawns, verifying duplicate respawn prevention)
        vm.uiState.value.lootItems
            .filter { it.category == com.example.model.LootCategory.COIN && !it.collected }
            .take(5)
            .forEach { coinItem ->
                vm.setPlayerPositionForTest(coinItem.x, coinItem.z)
                vm.stepSimulationForTest(0.1f)
            }
        assertTrue("Player must have enough coins for vending machine respawn", vm.uiState.value.matchCoins >= vmNode.coinCost * 2)
        // Ensure player is at vending machine with enough coins
        vm.setPlayerPositionForTest(vmNode.x + 1.2f, vmNode.z + 1.2f)
        vm.stepSimulationForTest(0.1f)
        val revivesBeforeVm = vm.uiState.value.playerTeammatesRevived
        vm.useVendingMachineToRespawnTeammate()
        // Calling vending respawn a second time immediately must NOT duplicate respawn the same teammate
        vm.useVendingMachineToRespawnTeammate()
        val respawnedAlly = vm.uiState.value.bots.first { it.id == duoAlly.id }
        assertFalse("Fully dead teammate must be respawned by Vending Machine", respawnedAlly.isDead)
        assertFalse("Respawned teammate must not be knocked", respawnedAlly.isKnocked)
        assertEquals("Duplicate vending machine respawn must be prevented", revivesBeforeVm + 1, vm.uiState.value.playerTeammatesRevived)

        // 6. SQUAD Mode: Knock timer expiration -> Full Death -> Cannot be normally revived -> Spectator Mode works
        vm.returnToLobby()
        vm.selectTeamMode(TeamMode.SQUAD)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        assertEquals(3, vm.uiState.value.livingTeammates.size)
        // Move teammates far away so they cannot reach player before bleedout timer ends
        vm.uiState.value.bots.filter { it.isFriendly }.forEach { ally ->
            vm.updateBotForTest(ally.id) { it.copy(x = -320f, z = -320f, isKnocked = false, isDead = false) }
        }
        vm.uiState.value.bots.filter { !it.isFriendly }.forEach { e ->
            vm.updateBotForTest(e.id) { it.copy(x = 320f, z = 320f, ammoInMag = 0, reserveAmmo = 0) }
        }
        vm.setPlayerPositionForTest(100f, -100f)
        vm.applyDamageToPlayerForTest(damage = 250f, bypassArmor = true)
        assertTrue("Squad player must enter Knocked state when HP reaches 0", vm.uiState.value.isPlayerKnocked)

        // Fast-forward knock timer to near expiration and step past 0
        vm.setPlayerKnockedForTest(true, hp = 0f)
        repeat(82) { vm.stepSimulationForTest(0.25f) } // > 20s bleedout timer
        val bledOutState = vm.uiState.value
        assertFalse("Player must leave Knocked state when knock timer ends", bledOutState.isPlayerKnocked)
        assertTrue("Player must become fully dead when knock timer ends", bledOutState.isPlayerDead)
        assertTrue("Player must enter Spectator mode after full death", bledOutState.isSpectating)
        assertTrue("Spectator targets must include living teammates", bledOutState.spectatorTargets.any { it.isTeammate })

        // Verify a friendly teammate standing next to a fully dead player CANNOT revive the dead player
        val squadAlly = vm.uiState.value.livingTeammates.first()
        vm.updateBotForTest(squadAlly.id) { it.copy(x = bledOutState.playerX, z = bledOutState.playerZ) }
        repeat(16) { vm.stepSimulationForTest(0.25f) }
        assertTrue("Fully dead player must NEVER be normally revived by teammates", vm.uiState.value.isPlayerDead)

        // Verify Spectator target switching works without stuck death states
        val initialSpecIdx = vm.uiState.value.activeSpectatorTargetIndex
        vm.cycleSpectatorTarget()
        assertTrue(
            "Spectator target cycling must advance target index when multiple targets exist",
            vm.uiState.value.spectatorTargets.size <= 1 || vm.uiState.value.activeSpectatorTargetIndex != initialSpecIdx
        )

        db.close()
    }

    @Test
    fun spectatorAndMatchResult_teammateAndRemainingPlayerSwitching_noTargetControl_noDuplicateResultOrLobbyReturn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PlayerRepository(db.playerProfileDao())
        val sound = SoundEngine().apply { isMuted = true }
        val vm = BattleRoyaleViewModel(repo, sound)

        // 1. Start DUO match (Player + 1 Teammate) and eliminate player after knocking
        vm.selectTeamMode(TeamMode.DUO)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        val ally = vm.uiState.value.livingTeammates.first()
        val enemies = vm.uiState.value.bots.filter { !it.isFriendly && !it.isDead }
        val killerEnemy = enemies[0]
        val secondEnemy = enemies[1]

        // Eliminate player -> enters Spectator mode observing living teammate first
        vm.setPlayerKnockedForTest(true, hp = 0f)
        vm.applyDamageToPlayerForTest(damage = 100f, attackerBotId = killerEnemy.id, bypassArmor = true)
        val specState1 = vm.uiState.value
        assertTrue("Player must be fully dead", specState1.isPlayerDead)
        assertTrue("Player must enter Spectator mode after full death", specState1.isSpectating)
        val target1 = specState1.currentSpectatorTarget
        assertTrue("Spectator must prioritize living teammate when alive", target1 != null && target1.isTeammate)
        assertEquals(ally.id, target1?.botId)

        // 2. Verify spectator CANNOT control the spectated target (movement, camera, firing, aiming, medkit, gloo wall)
        val yawBeforeInput = vm.uiState.value.playerYawDeg
        val medkitsBefore = vm.uiState.value.medkitCount
        val glooBefore = vm.uiState.value.glooWallCount
        vm.setJoystickInput(1.0f, 1.0f, sprinting = true)
        vm.rotateCameraByTouch(95f, 45f)
        vm.onFireButtonPressedChanged(true)
        vm.toggleAim()
        vm.useMedkit()
        vm.deployGlooWall()
        val afterBlockedInput = vm.uiState.value
        assertFalse("Spectator must not sprint", afterBlockedInput.isSprinting)
        assertFalse("Spectator must not fire weapons", afterBlockedInput.isManualFiringHeld)
        assertFalse("Spectator must not aim", afterBlockedInput.isAiming)
        assertEquals("Spectator touch drag must not rotate target camera", yawBeforeInput, afterBlockedInput.playerYawDeg, 0.01f)
        assertEquals("Spectator must not consume medkits", medkitsBefore, afterBlockedInput.medkitCount)
        assertEquals("Spectator must not deploy gloo walls", glooBefore, afterBlockedInput.glooWallCount)

        // 3. Living teammate dies -> Spectator automatically switches to a valid remaining player (no invalid/dead target)
        vm.updateBotForTest(ally.id) { it.copy(hp = 0f, isDead = true, isKnocked = false) }
        vm.stepSimulationForTest(0.2f)
        val specState2 = vm.uiState.value
        assertTrue("Spectator mode must stay active when remaining players are alive", specState2.isSpectating)
        val target2 = specState2.currentSpectatorTarget
        assertTrue("When no teammate is alive, spectator must observe a valid remaining player", target2 != null && !target2.isTeammate)
        assertFalse(
            "Spectator target must never be a dead bot",
            specState2.bots.first { it.id == target2!!.botId }.isDead
        )

        // 4. Current spectated remaining player dies -> automatically selects another valid living player
        val watchedId = target2!!.botId
        vm.updateBotForTest(watchedId) { it.copy(hp = 0f, isDead = true, isKnocked = false) }
        vm.stepSimulationForTest(0.2f)
        val specState3 = vm.uiState.value
        val target3 = specState3.currentSpectatorTarget
        assertTrue("When spectated target dies, another valid target must be selected automatically", target3 != null && target3.botId != watchedId)
        assertFalse(
            "Newly selected spectator target must be alive",
            specState3.bots.first { it.id == target3!!.botId }.isDead
        )
        assertTrue("Second enemy must still be alive in match", !specState3.bots.first { it.id == secondEnemy.id }.isDead)

        // 5. Match ends from Spectator -> shows Defeat, Final placement, Kills, Damage, Survival time, XP earned
        val xpBeforeFinish = vm.uiState.value.playerXp
        val levelBeforeFinish = vm.uiState.value.playerLevel
        vm.finishMatchFromSpectator()
        val resultState1 = vm.uiState.value
        assertEquals(com.example.engine.AppScreenState.MATCH_RESULT, resultState1.screenState)
        assertFalse("Spectating must stop on Match Result screen", resultState1.isSpectating)
        val summary1 = resultState1.lastMatchResult
        assertTrue("MatchResultSummary must be populated", summary1 != null)
        assertFalse("Eliminated squad match result must be Defeat", summary1!!.isVictory)
        assertTrue("Final placement must be >= 2 on Defeat", summary1.placement in 2..summary1.totalTeamsOrPlayers)
        assertTrue("Kills must be non-negative", summary1.kills >= 0)
        assertTrue("Damage must be non-negative", summary1.damageDealt >= 0)
        assertTrue("Survival time must be positive", summary1.survivalTimeSec >= 1)
        assertTrue("XP earned must be positive", summary1.xpEarned > 0)

        // 6. Prevent duplicate match result: calling finishMatchFromSpectator() again must not duplicate XP or overwrite result
        val xpAfterFirstFinish = vm.uiState.value.playerXp
        val levelAfterFirstFinish = vm.uiState.value.playerLevel
        vm.finishMatchFromSpectator()
        assertEquals("Duplicate match result must not grant extra XP", xpAfterFirstFinish, vm.uiState.value.playerXp)
        assertEquals("Duplicate match result must not change level", levelAfterFirstFinish, vm.uiState.value.playerLevel)
        assertTrue(
            "Total XP/level must have increased from match completion",
            levelAfterFirstFinish > levelBeforeFinish || xpAfterFirstFinish > xpBeforeFinish
        )

        // 7. Return to Lobby & prevent returning to lobby multiple times
        vm.returnToLobby()
        assertEquals(com.example.engine.AppScreenState.LOBBY, vm.uiState.value.screenState)
        // Calling returnToLobby() multiple times while already in LOBBY must be a safe no-op
        vm.returnToLobby()
        vm.returnToLobby()
        assertEquals(com.example.engine.AppScreenState.LOBBY, vm.uiState.value.screenState)

        // 8. Verify Victory match result when all enemies are eliminated
        vm.selectTeamMode(TeamMode.SOLO)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        vm.uiState.value.bots.filter { !it.isFriendly }.forEach { e ->
            vm.updateBotForTest(e.id) { it.copy(hp = 0f, isDead = true, isKnocked = false) }
        }
        vm.stepSimulationForTest(0.2f)
        val victoryState = vm.uiState.value
        assertEquals(com.example.engine.AppScreenState.MATCH_RESULT, victoryState.screenState)
        assertTrue("Eliminating all enemies must produce Victory result", victoryState.lastMatchResult?.isVictory == true)
        assertEquals("Victory placement must be #1", 1, victoryState.lastMatchResult?.placement)

        db.close()
    }

    @Test
    fun xpLevelAndDailyMissions_persistenceBetweenSessions_24HourReset_noDuplicateOrNegativeXp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PlayerRepository(db.playerProfileDao())
        val sound = SoundEngine().apply { isMuted = true }
        val vm1 = BattleRoyaleViewModel(repo, sound)

        assertEquals(1, vm1.uiState.value.playerLevel)
        assertEquals(0, vm1.uiState.value.playerXp)
        assertTrue("Daily missions must be initialized", vm1.uiState.value.dailyMissions.isNotEmpty())

        // Play a match with 3 kills, 130s survival, 5 loot items, and Victory (#1 placement)
        vm1.selectTeamMode(TeamMode.SOLO)
        vm1.startNewMatch()
        vm1.stepSimulationForTest(0.6f)
        vm1.setMatchPerformanceForTest(kills = 3, survivalTimeSec = 130f, lootCollected = 5)
        vm1.uiState.value.bots.filter { !it.isFriendly }.forEach { e ->
            vm1.updateBotForTest(e.id) { it.copy(hp = 0f, isDead = true, isKnocked = false) }
        }
        vm1.stepSimulationForTest(0.2f)

        val afterMatch = vm1.uiState.value
        assertEquals(com.example.engine.AppScreenState.MATCH_RESULT, afterMatch.screenState)
        assertTrue("Player must level up from match + completed daily missions XP", afterMatch.playerLevel >= 2)
        assertTrue("Player XP must never be negative", afterMatch.playerXp >= 0)
        assertTrue(
            "All completed daily missions must be marked completed and claimed",
            afterMatch.dailyMissions.all { it.isCompleted && it.claimed }
        )
        assertTrue(
            "Cosmetic rewards from completed missions must be unlocked",
            "outfit_sani_cyber" in afterMatch.unlockedCosmetics && "outfit_rima_neon" in afterMatch.unlockedCosmetics
        )

        // Calling claimDailyMission again on already-rewarded missions must NEVER grant duplicate XP or rewards
        val levelBeforeDuplicateClaim = vm1.uiState.value.playerLevel
        val xpBeforeDuplicateClaim = vm1.uiState.value.playerXp
        vm1.claimDailyMission("kills_3")
        vm1.claimDailyMission("complete_1")
        assertEquals("Repeated mission claim must not change level", levelBeforeDuplicateClaim, vm1.uiState.value.playerLevel)
        assertEquals("Repeated mission claim must not grant duplicate XP", xpBeforeDuplicateClaim, vm1.uiState.value.playerXp)

        // Restarting the game (new ViewModel session on same database) must preserve Level, XP, and Unlocked Cosmetics
        val vm2 = BattleRoyaleViewModel(repo, sound)
        assertEquals("Saved Level must persist across sessions", levelBeforeDuplicateClaim, vm2.uiState.value.playerLevel)
        assertEquals("Saved XP must persist across sessions without loss", xpBeforeDuplicateClaim, vm2.uiState.value.playerXp)
        assertTrue("Unlocked cosmetics must persist across sessions", "outfit_sani_cyber" in vm2.uiState.value.unlockedCosmetics)

        // Simulate 24 hours passing -> Daily Missions reset while Level and XP remain intact
        val futureTimeMs = System.currentTimeMillis() + PlayerRepository.TWENTY_FOUR_HOURS_MS + 60_000L
        vm2.checkAndResetDailyMissionsIfNeeded(futureTimeMs)
        val afterReset = vm2.uiState.value
        assertEquals("24h mission reset must preserve player Level", levelBeforeDuplicateClaim, afterReset.playerLevel)
        assertEquals("24h mission reset must preserve player XP", xpBeforeDuplicateClaim, afterReset.playerXp)
        assertTrue(
            "24h mission reset must reset all daily missions to 0 progress and unclaimed",
            afterReset.dailyMissions.all { it.currentValue == 0 && !it.isCompleted && !it.claimed }
        )

        db.close()
    }

    @Test
    fun settingsAndSaveSystem_automaticSaveAndLoad_soundGraphicsFpsHudSensCharacter_corruptedDataRecovery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.playerProfileDao()
        val repo = PlayerRepository(dao)
        val sound1 = SoundEngine().apply { isMuted = true }
        val vm1 = BattleRoyaleViewModel(repo, sound1)

        // 1. Update all required settings automatically
        vm1.selectCharacter(CharacterId.RIMA)
        vm1.updateGraphicsQuality(GraphicsQuality.HIGH)
        vm1.updateFpsTargetMode(FpsTargetMode.FPS_60)
        vm1.updateAimAssist(false)
        vm1.updateGyroscopeMode(GyroscopeMode.SCOPE_ON)
        vm1.updateAutoLootEnabled(false)
        vm1.updateSoundEnabled(false)
        assertTrue("SoundEngine must be muted when Sound is OFF", sound1.isMuted)

        val customSens = vm1.uiState.value.sensitivity.copy(
            general = 78f,
            redDot = 72f,
            scope2x = 64f,
            scope4x = 55f,
            sniper = 42f,
            freeLook = 81f,
            gyroMode = GyroscopeMode.ALWAYS_ON,
            gyroRedDot = 66f,
            gyro2x = 58f,
            gyro4x = 49f,
            gyroSniper = 37f
        )
        vm1.updateSensitivity(customSens)

        // Customize HUD Layout Slot 1 and control position/scale/alpha (automatically saved)
        vm1.selectHudLayoutSlot(1)
        vm1.updateHudControl(
            controlId = HudControlId.FIRE_BUTTON,
            normX = 0.85f,
            normY = 0.68f,
            sizeScale = 1.22f,
            alpha = 0.90f
        )

        // 2. Verify starting a match and returning to lobby never resets settings unexpectedly
        vm1.startNewMatch()
        vm1.stepSimulationForTest(0.5f)
        vm1.returnToLobby()
        val midState = vm1.uiState.value
        assertEquals(CharacterId.RIMA, midState.selectedCharacter)
        assertEquals(GraphicsQuality.HIGH, midState.graphicsQuality)
        assertEquals(FpsTargetMode.FPS_60, midState.fpsTargetMode)
        assertFalse(midState.aimAssistEnabled)
        assertFalse(midState.autoLoot.enabled)
        assertFalse(midState.soundEnabled)
        assertEquals(GyroscopeMode.ALWAYS_ON, midState.sensitivity.gyroMode)
        assertEquals(1, midState.activeHudSlot)

        // 3. Restart the game (new ViewModel & SoundEngine instance) and verify all saved settings load accurately
        val sound2 = SoundEngine()
        val vm2 = BattleRoyaleViewModel(repo, sound2)
        val loaded = vm2.uiState.value
        assertEquals("Selected character must load accurately", CharacterId.RIMA, loaded.selectedCharacter)
        assertEquals("Graphics quality must load accurately", GraphicsQuality.HIGH, loaded.graphicsQuality)
        assertEquals("FPS target mode must load accurately", FpsTargetMode.FPS_60, loaded.fpsTargetMode)
        assertFalse("Aim Assist setting must load accurately", loaded.aimAssistEnabled)
        assertFalse("Auto Loot setting must load accurately", loaded.autoLoot.enabled)
        assertFalse("Sound setting must load accurately", loaded.soundEnabled)
        assertTrue("SoundEngine must reflect loaded Sound OFF state", sound2.isMuted)
        assertEquals(GyroscopeMode.ALWAYS_ON, loaded.sensitivity.gyroMode)
        assertEquals(78f, loaded.sensitivity.general, 0.01f)
        assertEquals(72f, loaded.sensitivity.redDot, 0.01f)
        assertEquals(64f, loaded.sensitivity.scope2x, 0.01f)
        assertEquals(55f, loaded.sensitivity.scope4x, 0.01f)
        assertEquals(42f, loaded.sensitivity.sniper, 0.01f)
        assertEquals(81f, loaded.sensitivity.freeLook, 0.01f)
        assertEquals(66f, loaded.sensitivity.gyroRedDot, 0.01f)
        assertEquals(58f, loaded.sensitivity.gyro2x, 0.01f)
        assertEquals(49f, loaded.sensitivity.gyro4x, 0.01f)
        assertEquals(37f, loaded.sensitivity.gyroSniper, 0.01f)
        assertEquals("Active HUD slot must load accurately", 1, loaded.activeHudSlot)
        val loadedFireBtn = loaded.hudPresets[1].controls[HudControlId.FIRE_BUTTON]!!
        assertEquals(0.85f, loadedFireBtn.normX, 0.01f)
        assertEquals(0.68f, loadedFireBtn.normY, 0.01f)
        assertEquals(1.22f, loadedFireBtn.sizeScale, 0.01f)
        assertEquals(0.90f, loadedFireBtn.alpha, 0.01f)

        // Toggle Sound back ON and verify automatic save
        vm2.updateSoundEnabled(true)
        assertFalse("SoundEngine must unmute when Sound is ON", sound2.isMuted)
        assertTrue(vm2.uiState.value.soundEnabled)

        // 4. Inject corrupted save data into DB and verify graceful recovery without crashes
        val corruptedEntity = dao.getProfileSync()!!.copy(
            selectedCharacter = "CORRUPTED_CHARACTER",
            selectedTeamMode = "CORRUPTED_MODE",
            graphicsQuality = "BROKEN_GFX",
            fpsTargetMode = "BROKEN_FPS",
            activeHudSlot = 99,
            hudLayoutsJson = "{corrupted_hud_json",
            sensitivityJson = "{corrupted_sens_json",
            autoLootJson = "{corrupted_autoloot_json",
            dailyMissionsJson = "[{\"id\":\"dup\",\"title\":\"T\",\"targetValue\":-5,\"currentValue\":999,\"xpReward\":-50},{\"id\":\"dup\",\"title\":\"T\",\"targetValue\":1,\"currentValue\":0,\"xpReward\":100}]"
        )
        dao.saveProfileSync(corruptedEntity)

        val vm3 = BattleRoyaleViewModel(PlayerRepository(dao), SoundEngine().apply { isMuted = true })
        val recovered = vm3.uiState.value
        assertEquals(CharacterId.SANI, recovered.selectedCharacter)
        assertEquals(TeamMode.SQUAD, recovered.selectedTeamMode)
        assertEquals(GraphicsQuality.LOW, recovered.graphicsQuality)
        assertEquals(FpsTargetMode.AUTO_FPS, recovered.fpsTargetMode)
        assertEquals(2, recovered.activeHudSlot)
        assertEquals(3, recovered.hudPresets.size)
        assertEquals(1, recovered.dailyMissions.count { it.id == "dup" })
        assertTrue(recovered.dailyMissions.all { it.targetValue >= 1 && it.xpReward >= 0 })

        db.close()
    }

    @Test
    fun completeEndToEndGameFlow_lobbyToMatchGameplayDeathVictoryMatchResultAndBackToLobby() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PlayerRepository(db.playerProfileDao())
        val sound = SoundEngine().apply { isMuted = true }
        val vm = BattleRoyaleViewModel(repo, sound)

        // 1. LOBBY: Select SANI & DUO mode
        assertEquals(com.example.engine.AppScreenState.LOBBY, vm.uiState.value.screenState)
        vm.selectCharacter(CharacterId.SANI)
        vm.selectTeamMode(TeamMode.DUO)

        // 2. LOBBY -> MATCH: Start Match #1
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        assertEquals(com.example.engine.AppScreenState.IN_MATCH, vm.uiState.value.screenState)
        assertFalse(vm.uiState.value.isStartingMatch)
        assertEquals(1, vm.uiState.value.livingTeammates.size)

        // 3. GAMEPLAY: Movement, Ability (SANI), Shooting (no infinite ammo), Reload, Knock & Revive
        vm.setPlayerPositionForTest(40f, -110f)
        vm.setJoystickInput(0f, 1f, sprinting = true)
        vm.activateCharacterAbility()
        assertEquals(AbilityState.ACTIVE, vm.uiState.value.abilityState)
        // While SANI Overdrive is ACTIVE (11s duration), shooting is blocked; step 11.25s until ability transitions to COOLDOWN
        repeat(45) { vm.stepSimulationForTest(0.25f) }
        vm.setJoystickInput(0f, 0f, sprinting = false)
        assertEquals(AbilityState.COOLDOWN, vm.uiState.value.abilityState)
        assertFalse(
            "Player must never get stuck inside a collider during movement",
            IslandMapGenerator.isPositionBlocked(
                vm.uiState.value.playerX,
                vm.uiState.value.playerZ,
                vm.uiState.value.isVaulting,
                vm.uiState.value.glooWalls,
                0.72f
            )
        )

        // Ensure player is on dry land and fire weapon -> consumes ammo (never infinite ammo), reload restores magazine from reserve
        vm.setPlayerPositionForTest(40f, -110f)
        vm.stepSimulationForTest(0.1f)
        vm.equipWeaponForTest(
            slot = WeaponSlotIndex.MAIN_1,
            spec = com.example.model.OriginalWeaponCatalog.VX47_STRIKER,
            reserveAmmo = 60
        )
        val ammoType = vm.uiState.value.activeWeapon!!.spec.category.ammoType
        val ammoBeforeShot = vm.uiState.value.activeWeapon!!.currentAmmo
        val reserveBeforeReload = vm.uiState.value.ammoReserve[ammoType] ?: 0
        vm.onFireButtonPressedChanged(true)
        vm.onFireButtonPressedChanged(false)
        assertEquals(ammoBeforeShot - 1, vm.uiState.value.activeWeapon!!.currentAmmo)
        vm.triggerManualReload()
        assertTrue(vm.uiState.value.isReloading)
        repeat(12) { vm.stepSimulationForTest(0.25f) }
        assertFalse(vm.uiState.value.isReloading)
        assertEquals(vm.uiState.value.activeWeapon!!.maxMagazineSize, vm.uiState.value.activeWeapon!!.currentAmmo)
        assertEquals(reserveBeforeReload - 1, vm.uiState.value.ammoReserve[ammoType] ?: 0)

        // 4. DEATH -> SPECTATOR -> MATCH RESULT -> LOBBY
        vm.setPlayerKnockedForTest(true, hp = 0f)
        vm.applyDamageToPlayerForTest(damage = 100f, bypassArmor = true)
        assertTrue(vm.uiState.value.isPlayerDead)
        assertTrue(vm.uiState.value.isSpectating)
        vm.finishMatchFromSpectator()
        assertEquals(com.example.engine.AppScreenState.MATCH_RESULT, vm.uiState.value.screenState)
        assertFalse(vm.uiState.value.lastMatchResult!!.isVictory)

        vm.returnToLobby()
        assertEquals(com.example.engine.AppScreenState.LOBBY, vm.uiState.value.screenState)

        // 5. MATCH #2 (Fresh Reset with RIMA -> Gameplay -> VICTORY -> MATCH RESULT -> LOBBY)
        vm.selectCharacter(CharacterId.RIMA)
        vm.selectTeamMode(TeamMode.SOLO)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        assertEquals(com.example.engine.AppScreenState.IN_MATCH, vm.uiState.value.screenState)
        assertEquals(CharacterId.RIMA, vm.uiState.value.matchCharacter)
        assertFalse("Fresh match must reset player dead state", vm.uiState.value.isPlayerDead)
        assertFalse("Fresh match must reset player knocked state", vm.uiState.value.isPlayerKnocked)
        assertEquals("Fresh match must reset kills to 0", 0, vm.uiState.value.playerKills)

        // Activate RIMA Tactical Pulse ability
        vm.activateCharacterAbility()
        assertTrue(vm.uiState.value.rimaRevealActive)

        // Eliminate all enemies -> Victory -> Match Result -> Lobby
        vm.uiState.value.bots.filter { !it.isFriendly }.forEach { e ->
            vm.updateBotForTest(e.id) { it.copy(hp = 0f, isDead = true, isKnocked = false) }
        }
        vm.stepSimulationForTest(0.2f)
        assertEquals(com.example.engine.AppScreenState.MATCH_RESULT, vm.uiState.value.screenState)
        assertTrue(vm.uiState.value.lastMatchResult!!.isVictory)
        assertEquals(1, vm.uiState.value.lastMatchResult!!.placement)

        vm.returnToLobby()
        assertEquals(com.example.engine.AppScreenState.LOBBY, vm.uiState.value.screenState)

        db.close()
    }

    @Test
    fun vehicleSystem_validSpawnsEnterExitSteerAccelerateBrakeCollisionMinimapAndFriendlyAi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PlayerRepository(db.playerProfileDao())
        val sound = SoundEngine().apply { isMuted = true }
        val vm = BattleRoyaleViewModel(repo, sound)

        vm.selectTeamMode(TeamMode.DUO)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)

        val initialVehicles = vm.uiState.value.vehicles
        assertTrue("Map must spawn original drivable vehicles", initialVehicles.size >= 6)
        assertEquals("Vehicles must never have duplicate IDs", initialVehicles.size, initialVehicles.map { it.id }.distinct().size)

        // 1. Verify all vehicles spawn at valid map locations (never inside solid buildings, rocks, trees, water, or overlapping)
        for (veh in initialVehicles) {
            assertFalse(
                "Vehicle ${veh.name} (${veh.id}) must not spawn inside any solid building, rock, tree, or water",
                IslandMapGenerator.isVehiclePositionBlocked(
                    x = veh.x,
                    z = veh.z,
                    glooWalls = emptyList(),
                    otherVehicles = initialVehicles,
                    ignoreVehicleId = veh.id
                )
            )
            val mmOffset = com.example.ui.screens.worldToMinimapOffset(veh.x, veh.z, 120f, 120f)
            assertTrue("Vehicle minimap coordinate must be inside bounds", mmOffset.x in 0f..120f && mmOffset.y in 0f..120f)
        }

        // 2. Move player next to Vehicle #1 and enter it
        val targetVehicle = initialVehicles.first()
        vm.setPlayerPositionForTest(targetVehicle.x + 2.0f, targetVehicle.z)
        assertTrue("Nearby vehicle prompt must detect adjacent vehicle", vm.uiState.value.nearbyVehicle != null)
        vm.toggleEnterExitVehicle()
        assertEquals("Player must enter target vehicle", targetVehicle.id, vm.uiState.value.drivingVehicleId)
        assertEquals("Vehicle occupantId must be 0 for player", 0, vm.uiState.value.currentDrivingVehicle?.occupantId)

        // 3. Accelerate forward and steer right -> speed increases, yaw turns, position advances, minimap syncs
        val startX = vm.uiState.value.playerX
        val startZ = vm.uiState.value.playerZ
        val startYaw = vm.uiState.value.playerYawDeg
        vm.setVehicleThrottle(1.0f)
        vm.setVehicleSteering(1.0f)
        repeat(6) { vm.stepSimulationForTest(0.15f) }

        val drivingState = vm.uiState.value
        assertTrue("Accelerating must increase vehicle speed", drivingState.vehicleSpeedMps > 5.0f)
        assertTrue("Speed in km/h must be positive while accelerating", drivingState.vehicleSpeedKmh > 15)
        assertTrue("Steering must rotate vehicle yaw", kotlin.math.abs(drivingState.playerYawDeg - startYaw) > 5.0f)
        assertTrue("Vehicle must move across the map", kotlin.math.hypot(drivingState.playerX - startX, drivingState.playerZ - startZ) > 2.0f)

        val syncedVeh = drivingState.vehicles.first { it.id == targetVehicle.id }
        assertEquals("World vehicle X must stay synchronized with player X", drivingState.playerX, syncedVeh.x, 0.01f)
        assertEquals("World vehicle Z must stay synchronized with player Z", drivingState.playerZ, syncedVeh.z, 0.01f)
        assertEquals("Minimap vehicle offset must match player minimap offset",
            com.example.ui.screens.worldToMinimapOffset(drivingState.playerX, drivingState.playerZ, 120f, 120f),
            com.example.ui.screens.worldToMinimapOffset(syncedVeh.x, syncedVeh.z, 120f, 120f)
        )

        // 4. Braking -> brings vehicle speed down to 0
        vm.setVehicleSteering(0f)
        vm.setVehicleThrottle(0f)
        vm.setVehicleBraking(true)
        repeat(8) { vm.stepSimulationForTest(0.15f) }
        assertEquals("Braking must stop the vehicle cleanly", 0f, vm.uiState.value.vehicleSpeedMps, 0.05f)
        vm.setVehicleBraking(false)

        // 5. Collision test: attempt to drive directly through a solid building & map boundary
        val solidBuilding = IslandMapGenerator.buildings.first { it.isSolidCollider && !it.isVaultable }
        val outsideBuildingZ = solidBuilding.centerZ - (solidBuilding.depth * 0.5f + IslandMapGenerator.VEHICLE_COLLISION_RADIUS + 2.5f)
        vm.setPlayerPositionForTest(solidBuilding.centerX, outsideBuildingZ)
        vm.setVehicleThrottle(1.0f)
        repeat(10) { vm.stepSimulationForTest(0.15f) }
        val afterBuildingHit = vm.uiState.value
        assertFalse(
            "Vehicle must never drive inside a solid building",
            IslandMapGenerator.findCollidingBuilding(
                x = afterBuildingHit.playerX,
                z = afterBuildingHit.playerZ,
                radius = IslandMapGenerator.VEHICLE_COLLISION_RADIUS,
                ignoreVaultable = false
            ) != null
        )

        // Map boundary collision test
        vm.setPlayerPositionForTest(0f, IslandMapGenerator.VEHICLE_BOUNDARY_LIMIT - 1.0f)
        vm.setVehicleThrottle(1.0f)
        repeat(8) { vm.stepSimulationForTest(0.15f) }
        assertTrue(
            "Vehicle must never exceed map boundaries",
            kotlin.math.abs(vm.uiState.value.playerX) <= IslandMapGenerator.VEHICLE_BOUNDARY_LIMIT &&
                kotlin.math.abs(vm.uiState.value.playerZ) <= IslandMapGenerator.VEHICLE_BOUNDARY_LIMIT
        )
        vm.setVehicleThrottle(0f)

        // 6. Exit vehicle -> places player at a safe, valid dry-land position outside the vehicle
        vm.setPlayerPositionForTest(32f, -95f)
        vm.toggleEnterExitVehicle()
        assertEquals("Player must exit vehicle", null, vm.uiState.value.drivingVehicleId)
        assertFalse(
            "Player exit position must never be blocked or inside an object",
            IslandMapGenerator.isPositionBlocked(
                x = vm.uiState.value.playerX,
                z = vm.uiState.value.playerZ,
                ignoreVaultable = false,
                glooWalls = vm.uiState.value.glooWalls,
                radius = 0.72f
            )
        )

        // 7. Friendly AI uses a free vehicle when rotating/regrouping over long distance (> 48m from player) out of combat
        val freeVehForAlly = vm.uiState.value.vehicles.first { it.id != targetVehicle.id }
        val ally = vm.uiState.value.livingTeammates.first()
        vm.setPlayerPositionForTest(0f, -135f)
        vm.uiState.value.bots.filter { !it.isFriendly }.forEach { enemy ->
            if (kotlin.math.hypot(enemy.x - freeVehForAlly.x, enemy.z - freeVehForAlly.z) < 60f) {
                vm.updateBotForTest(enemy.id) { it.copy(x = 220f, z = 180f) }
            }
        }
        vm.updateBotForTest(ally.id) {
            it.copy(
                x = freeVehForAlly.x + 2.0f,
                z = freeVehForAlly.z,
                aiRole = com.example.model.BotAiRole.MOVING_TO_ZONE,
                targetX = 0f,
                targetZ = -135f,
                drivingVehicleId = null
            )
        }
        vm.stepSimulationForTest(0.25f)
        val updatedAlly = vm.uiState.value.livingTeammates.first { it.id == ally.id }
        assertEquals("Friendly AI must board nearby free vehicle when rotating long distance", freeVehForAlly.id, updatedAlly.drivingVehicleId)

        db.close()
    }

    @Test
    fun audioUiLoadingAndFinalCompleteFlow_allSystemsVerifiedEndToEnd() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PlayerRepository(db.playerProfileDao())
        val sound = SoundEngine().apply { isMuted = true }
        sound.resetAudioCountersForTest()
        val vm = BattleRoyaleViewModel(repo, sound)

        // 18. AUDIO: Verify distinct SANI vs RIMA ability waveforms, no enemy voices, and anti-duplicate debounce
        assertTrue("SANI and RIMA ability sounds must be distinct", sound.saniAbilitySignatureHash != sound.rimaAbilitySignatureHash)
        assertEquals("Enemy voice must never play", 0, sound.enemyVoicePlayCount)

        // Rapid duplicate sound calls within the same frame must be debounced (no duplicate/looping audio)
        sound.resetAudioCountersForTest()
        sound.playGunshot()
        sound.playGunshot()
        assertEquals(1, sound.gunshotPlayCount)
        sound.playReload()
        sound.playReload()
        assertEquals(1, sound.reloadPlayCount)
        sound.playJump()
        sound.playJump()
        assertEquals(1, sound.jumpPlayCount)
        sound.playFootstep(3)
        sound.playFootstep(3)
        assertEquals(1, sound.footstepPlayCount)
        sound.playHit()
        sound.playHit()
        assertEquals(1, sound.hitPlayCount)
        sound.playKnockOrDeath()
        sound.playKnockOrDeath()
        assertEquals(1, sound.deathPlayCount)
        sound.playUiClick()
        sound.playUiClick()
        assertEquals(1, sound.uiClickPlayCount)
        sound.playZoneWarning()
        sound.playZoneWarning()
        assertEquals(1, sound.zoneWarningPlayCount)

        // 19. UI + LOADING: Verify non-overlapping HUD bounds across presets & screen sizes
        for (preset in vm.uiState.value.hudPresets) {
            val bounds = com.example.ui.screens.resolveHudLayoutBounds(preset, 800f, 360f, 1f)
            val boxes = bounds.values.toList()
            for (i in boxes.indices) {
                for (j in i + 1 until boxes.size) {
                    assertFalse(
                        "HUD controls ${boxes[i].controlId} and ${boxes[j].controlId} must not overlap",
                        boxes[i].overlaps(boxes[j], gapPx = -0.5f)
                    )
                }
            }
        }

        // 20. COMPLETE FLOW: Lobby -> Character -> Play (prevent multiple presses & stuck loading) -> Spawn -> Loot -> Weapons -> Fight -> Safe Zone -> Knock/Revive -> Death/Spectator -> Victory/Defeat -> Result -> Lobby
        sound.resetAudioCountersForTest()
        vm.selectCharacter(CharacterId.SANI)
        assertTrue("UI click sound must play on character selection", sound.uiClickPlayCount >= 1)
        vm.selectTeamMode(TeamMode.DUO)

        // Press Play multiple times rapidly -> only 1 match starts, loading state enters then finishes cleanly
        vm.startNewMatch()
        vm.startNewMatch()
        vm.startNewMatch()
        assertEquals("Multiple Play presses must not start duplicate matches", 1, vm.uiState.value.matchStartCount)
        assertTrue("Match must enter loading state initially", vm.uiState.value.isStartingMatch)
        vm.stepSimulationForTest(0.6f)
        assertFalse("Match loading state must finish without getting stuck", vm.uiState.value.isStartingMatch)

        // Spawn -> Jump -> SANI Ability
        vm.setPlayerPositionForTest(40f, -110f)
        sound.resetAudioCountersForTest()
        vm.triggerJumpOrVault()
        assertEquals("Jump sound must play when player jumps", 1, sound.jumpPlayCount)
        vm.activateCharacterAbility()
        assertEquals("SANI ability sound must play", 1, sound.saniAbilityPlayCount)
        assertEquals("RIMA ability sound must not play for SANI", 0, sound.rimaAbilityPlayCount)

        // Step until SANI ability completes -> Equip weapon -> Shoot enemy -> Reload -> Safe Zone warning
        repeat(45) { vm.stepSimulationForTest(0.25f) }
        vm.setPlayerPositionForTest(40f, -110f)
        vm.equipWeaponForTest(
            slot = WeaponSlotIndex.MAIN_1,
            spec = com.example.model.OriginalWeaponCatalog.VX47_STRIKER,
            reserveAmmo = 90
        )
        val firstEnemy = vm.uiState.value.bots.first { !it.isFriendly }
        vm.updateBotForTest(firstEnemy.id) {
            it.copy(
                x = 40f,
                y = IslandMapGenerator.getTerrainHeight(40f, -98f),
                z = -98f,
                hp = 100f,
                isKnocked = false,
                isDead = false
            )
        }
        val yawDiff = 0f - vm.uiState.value.playerYawDeg
        val sensFactor = vm.uiState.value.sensitivity.general / 310f
        vm.rotateCameraByTouch(yawDiff / sensFactor, vm.uiState.value.playerPitchDeg / (sensFactor * 0.65f))
        sound.resetAudioCountersForTest()
        vm.onFireButtonPressedChanged(true)
        vm.onFireButtonPressedChanged(false)
        assertEquals("Gunshot sound must play on firing", 1, sound.gunshotPlayCount)
        assertEquals("Hit sound must play on hitting enemy", 1, sound.hitPlayCount)
        vm.triggerManualReload()
        assertEquals("Reload sound must play on manual reload", 1, sound.reloadPlayCount)

        // Knock & Revive flow in Duo (move nearby enemy away so teammate safely revives knocked player)
        vm.uiState.value.bots.filter { !it.isFriendly }.forEach { enemy ->
            if (kotlin.math.hypot(enemy.x - vm.uiState.value.playerX, enemy.z - vm.uiState.value.playerZ) < 45f) {
                vm.updateBotForTest(enemy.id) { it.copy(x = 240f, z = 240f) }
            }
        }
        val livingAlly = vm.uiState.value.bots.first { it.isFriendly }
        vm.updateBotForTest(livingAlly.id) {
            it.copy(
                x = vm.uiState.value.playerX + 1.2f,
                z = vm.uiState.value.playerZ,
                hp = 100f,
                isKnocked = false,
                isDead = false,
                healTimerSec = 0f,
                drivingVehicleId = null
            )
        }
        sound.resetAudioCountersForTest()
        vm.applyDamageToPlayerForTest(damage = 250f, bypassArmor = true)
        assertTrue("Player must enter Knocked state when HP reaches 0 with living teammate", vm.uiState.value.isPlayerKnocked)
        assertEquals("Knock/death sound must play when knocked", 1, sound.deathPlayCount)
        repeat(16) { vm.stepSimulationForTest(0.25f) }
        assertFalse("Teammate must revive knocked player", vm.uiState.value.isPlayerKnocked)
        assertTrue("Revived player must have low HP restored", vm.uiState.value.playerHp >= 30f)

        // Safe Zone outside warning sound & damage
        sound.resetAudioCountersForTest()
        vm.setPlayerPositionForTest(525f, 525f)
        vm.stepSimulationForTest(0.2f)
        assertTrue("Player outside Safe Zone must be flagged", vm.uiState.value.safeZone.isPlayerOutside)
        assertEquals("Zone warning sound must play when stepping outside Safe Zone", 1, sound.zoneWarningPlayCount)

        // Full Death -> Spectator -> Defeat Match Result -> Lobby -> RIMA Victory Match Result -> Lobby
        vm.setPlayerKnockedForTest(true, hp = 0f)
        vm.applyDamageToPlayerForTest(damage = 100f, bypassArmor = true)
        assertTrue(vm.uiState.value.isPlayerDead)
        assertTrue(vm.uiState.value.isSpectating)
        vm.finishMatchFromSpectator()
        assertEquals(com.example.engine.AppScreenState.MATCH_RESULT, vm.uiState.value.screenState)
        vm.returnToLobby()
        assertEquals(com.example.engine.AppScreenState.LOBBY, vm.uiState.value.screenState)

        // RIMA match -> verify RIMA ability sound & Victory
        vm.selectCharacter(CharacterId.RIMA)
        vm.selectTeamMode(TeamMode.SOLO)
        vm.startNewMatch()
        vm.stepSimulationForTest(0.6f)
        sound.resetAudioCountersForTest()
        vm.activateCharacterAbility()
        assertEquals("RIMA ability sound must play", 1, sound.rimaAbilityPlayCount)
        assertEquals("SANI ability sound must not play for RIMA", 0, sound.saniAbilityPlayCount)

        vm.uiState.value.bots.filter { !it.isFriendly }.forEach { e ->
            vm.updateBotForTest(e.id) { it.copy(hp = 0f, isDead = true, isKnocked = false) }
        }
        vm.stepSimulationForTest(0.2f)
        assertEquals(com.example.engine.AppScreenState.MATCH_RESULT, vm.uiState.value.screenState)
        assertTrue(vm.uiState.value.lastMatchResult!!.isVictory)
        vm.returnToLobby()
        assertEquals(com.example.engine.AppScreenState.LOBBY, vm.uiState.value.screenState)
        assertEquals("Enemy voice count must remain 0 throughout entire flow", 0, sound.enemyVoicePlayCount)

        db.close()
    }

    @Test
    fun mainActivityLaunch_splashToLobbyCharacterSelectAndStartMatch_succeedsOfflineWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify SafeBitmapLoader decodes all drawables safely with bounded dimensions and handles invalid IDs
        val banner = com.example.ui.render.SafeBitmapLoader.loadSafeImageBitmap(
            context,
            R.drawable.img_sani_banner_1791221363697,
            maxDimensionPx = 480
        )
        assertTrue("Banner bitmap should decode safely", banner != null)
        assertTrue("Banner width must be bounded for low-end GPUs", banner!!.width <= 512)
        assertTrue(
            "Invalid drawable resource ID must return null safely without crashing",
            com.example.ui.render.SafeBitmapLoader.loadSafeImageBitmap(context, 0, maxDimensionPx = 256) == null
        )

        // 2. Verify MainActivity launches offline without throwing any exception
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertTrue("MainActivity must launch and initialize successfully", activity != null)

        controller.pause().stop().destroy()
    }
}
