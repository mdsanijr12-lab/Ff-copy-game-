package com.example.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEngine
import com.example.data.PlayerProfileEntity
import com.example.data.PlayerRepository
import com.example.model.AbilityState
import com.example.model.AmmoType
import com.example.model.AttachmentType
import com.example.model.AutoLootSettings
import com.example.model.BotAiRole
import com.example.model.BulletTrace
import com.example.model.CharacterAnimState
import com.example.model.CharacterId
import com.example.model.CombatantBot
import com.example.model.CompassDirection
import com.example.model.CosmeticCatalog
import com.example.model.DailyMission
import com.example.model.FireDirectionIndicator
import com.example.model.FootstepIndicator
import com.example.model.FpsTargetMode
import com.example.model.FriendlyVoiceCallout
import com.example.model.GraphicsQuality
import com.example.model.GyroscopeMode
import com.example.model.HitImpactEffect
import com.example.model.HitZone
import com.example.model.HudControlConfig
import com.example.model.HudControlId
import com.example.model.HudLayoutPreset
import com.example.model.LootCategory
import com.example.model.MapVehicle
import com.example.model.MatchResultSummary
import com.example.model.OriginalWeaponCatalog
import com.example.model.SafeZoneState
import com.example.model.ScopeType
import com.example.model.SensitivitySettings
import com.example.model.TeamMemberStats
import com.example.model.TeamMode
import com.example.model.VendingMachineNode
import com.example.model.WeaponCategory
import com.example.model.WeaponInstance
import com.example.model.WeaponSlotIndex
import com.example.model.WeaponSpec
import com.example.model.WorldLootItem
import com.example.model.deployedGlooWall
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class AppScreenState {
    LOBBY,
    IN_MATCH,
    HUD_EDITOR,
    MATCH_RESULT
}

data class SpectatorTarget(
    val id: Int,
    val name: String,
    val isTeammate: Boolean,
    val characterId: CharacterId,
    val x: Float,
    val y: Float,
    val z: Float,
    val yawDeg: Float,
    val hp: Float
) {
    val botId: Int get() = id
}

data class MatchUiState(
    val screenState: AppScreenState = AppScreenState.LOBBY,
    // Persistent Lobby / Profile state
    val selectedCharacter: CharacterId = CharacterId.SANI,
    val selectedTeamMode: TeamMode = TeamMode.SQUAD,
    val playerLevel: Int = 1,
    val playerXp: Int = 0,
    val xpForNextLevel: Int = 300,
    val nextRewardLabel: String = "Lv.2: SANI - Cyber Visor Jersey",
    val unlockedCosmetics: Set<String> = setOf("char_sani_default", "char_rima_default"),
    val equippedOutfitId: String = "char_sani_default",
    val equippedWeaponSkinId: String = "skin_vx47_cobalt",
    val dailyMissions: List<DailyMission> = emptyList(),
    val hudPresets: List<HudLayoutPreset> = listOf(
        HudLayoutPreset.defaultPreset(0),
        HudLayoutPreset.defaultPreset(1),
        HudLayoutPreset.defaultPreset(2)
    ),
    val activeHudSlot: Int = 0,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.LOW,
    val effectiveGraphicsQuality: GraphicsQuality = GraphicsQuality.LOW,
    val fpsTargetMode: FpsTargetMode = FpsTargetMode.AUTO_FPS,
    val measuredFps: Int = 45,
    val aimAssistEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val sensitivity: SensitivitySettings = SensitivitySettings(),
    val autoLoot: AutoLootSettings = AutoLootSettings(),
    val isStartingMatch: Boolean = false,
    val matchLoadingProgress: Float = 0f,
    val matchLoadingRemainingSec: Float = 0f,
    val spawnZoneName: String = "SANI CITADEL",
    val matchStartCount: Int = 0,

    // Match-specific temporary state (completely reset on every new match)
    val matchCharacter: CharacterId = CharacterId.SANI,
    val playerX: Float = 0f,
    val playerY: Float = 0f,
    val playerZ: Float = -125f,
    val playerYawDeg: Float = 0f,
    val playerPitchDeg: Float = 0f,
    val playerHp: Float = 100f,
    val playerMaxHp: Float = 100f,
    val playerArmor: Float = 50f,
    val playerMaxArmor: Float = 100f,
    val helmetTier: Int = 1,
    val armorTier: Int = 1,
    val backpackTier: Int = 1,
    val isSprinting: Boolean = false,
    val isCrouching: Boolean = false,
    val isProne: Boolean = false,
    val isSwimming: Boolean = false,
    val isJumping: Boolean = false,
    val isVaulting: Boolean = false,
    val playerAnimState: CharacterAnimState = CharacterAnimState.IDLE,
    val playerAnimPhase: Float = 0f,

    // Knock / Death / Revive / Spectator
    val isPlayerKnocked: Boolean = false,
    val playerKnockedTimerSec: Float = 25f,
    val isBeingRevived: Boolean = false,
    val reviveProgressSec: Float = 0f,
    val isPlayerDead: Boolean = false,
    val isSpectating: Boolean = false,
    val killerBotId: Int? = null,
    val spectatorTargets: List<SpectatorTarget> = emptyList(),
    val activeSpectatorTargetIndex: Int = 0,
    val activeSpectatorTargetId: Int? = null,

    // Weapons & Inventory (Main 1, Main 2, Secondary)
    val weaponSlots: Map<WeaponSlotIndex, WeaponInstance?> = mapOf(
        WeaponSlotIndex.MAIN_1 to null,
        WeaponSlotIndex.MAIN_2 to null,
        WeaponSlotIndex.SECONDARY to WeaponInstance(OriginalWeaponCatalog.P18_TALON)
    ),
    val activeWeaponSlot: WeaponSlotIndex = WeaponSlotIndex.SECONDARY,
    val isAiming: Boolean = false,
    val isScopedIn: Boolean = false,
    val isFreeLooking: Boolean = false,
    val freeLookYawOffsetDeg: Float = 0f,
    val freeLookPitchOffsetDeg: Float = 0f,
    val smoothFovScale: Float = 1.0f,
    val smoothCamDistBehind: Float = 4.2f,
    val smoothShoulderOffsetX: Float = 0f,
    val isReloading: Boolean = false,
    val reloadRemainingSec: Float = 0f,
    val isManualFiringHeld: Boolean = false,
    val lastHitZone: HitZone = HitZone.MISS,
    val lastDamageDealtAmount: Int = 0,
    val lastDamageTargetName: String = "",
    val hitMarkerTimerSec: Float = 0f,
    val hitEffects: List<HitImpactEffect> = emptyList(),
    val ammoReserve: Map<AmmoType, Int> = mapOf(
        AmmoType.AR_AMMO to 30,
        AmmoType.SMG_AMMO to 45,
        AmmoType.SG_AMMO to 0,
        AmmoType.SNIPER_AMMO to 0
    ),
    val inventoryScopes: Set<ScopeType> = setOf(ScopeType.RED_DOT),
    val unequippedMuzzles: Int = 0,
    val unequippedExtendedMags: Int = 0,
    val unequippedForegrips: Int = 0,
    val medkitCount: Int = 2,
    val grenadeCount: Int = 1,
    val glooWallCount: Int = 2,
    val matchCoins: Int = 0,
    val drivingVehicleId: Int? = null,
    val vehicleSpeedMps: Float = 0f,
    val vehicleThrottleInput: Float = 0f,
    val vehicleBrakeInput: Boolean = false,
    val vehicleSteerInput: Float = 0f,

    // Ability state
    val abilityState: AbilityState = AbilityState.READY,
    val abilityTimerSec: Float = 0f,
    val rimaRevealActive: Boolean = false,

    // World entities
    val safeZone: SafeZoneState = SafeZoneState(),
    val lootItems: List<WorldLootItem> = emptyList(),
    val bots: List<CombatantBot> = emptyList(),
    val vendingMachines: List<VendingMachineNode> = emptyList(),
    val vehicles: List<MapVehicle> = emptyList(),
    val glooWalls: List<deployedGlooWall> = emptyList(),
    val bulletTraces: List<BulletTrace> = emptyList(),
    val footstepIndicators: List<FootstepIndicator> = emptyList(),
    val fireDirectionIndicators: List<FireDirectionIndicator> = emptyList(),
    val friendlyCallout: FriendlyVoiceCallout? = null,
    val nearbyVendingMachine: VendingMachineNode? = null,
    val nearbyKnockAllyToRevive: CombatantBot? = null,
    val nearbyVehicle: MapVehicle? = null,

    // Match statistics & UI Feedback
    val aliveCount: Int = 24,
    val playerKills: Int = 0,
    val playerHeadshots: Int = 0,
    val playerDamageDealt: Float = 0f,
    val playerLootCollectedCount: Int = 0,
    val playerCoinsCollectedTotal: Int = 0,
    val playerTeammatesRevived: Int = 0,
    val matchElapsedSec: Float = 0f,
    val statusBannerText: String = "",
    val isInventoryOpen: Boolean = false,
    val lastMatchResult: MatchResultSummary? = null
) {
    val activeWeapon: WeaponInstance?
        get() = weaponSlots[activeWeaponSlot]

    val currentDrivingVehicle: MapVehicle?
        get() = drivingVehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }

    val vehicleSpeedKmh: Int
        get() = (abs(vehicleSpeedMps) * 3.6f).roundToInt()

    val currentBackpackWeight: Int
        get() {
            val ammoWeight = ammoReserve.entries.sumOf { (type, count) -> (count / 10) * type.unitWeight }
            val itemWeight = medkitCount * 8 + grenadeCount * 6 + glooWallCount * 6 +
                (unequippedMuzzles + unequippedExtendedMags + unequippedForegrips) * 4
            return ammoWeight + itemWeight
        }

    val maxBackpackCapacity: Int
        get() = 110 + backpackTier * 55

    val deadTeammates: List<CombatantBot>
        get() = bots.filter { it.isFriendly && it.isDead }

    val livingTeammates: List<CombatantBot>
        get() = bots.filter { it.isFriendly && !it.isDead }

    val activeUnknockedTeammates: List<CombatantBot>
        get() = bots.filter { it.isFriendly && !it.isDead && !it.isKnocked }

    val isEligibleForKnock: Boolean
        get() = selectedTeamMode != TeamMode.SOLO && activeUnknockedTeammates.isNotEmpty()

    val currentSpectatorTarget: SpectatorTarget?
        get() = spectatorTargets.firstOrNull { it.botId == activeSpectatorTargetId }
            ?: spectatorTargets.getOrNull(activeSpectatorTargetIndex)
            ?: spectatorTargets.firstOrNull()

    val cameraYawDeg: Float
        get() = CompassDirection.normalizeYaw(
            playerYawDeg + if (isFreeLooking) freeLookYawOffsetDeg else 0f
        )

    val compassDirection: CompassDirection
        get() = CompassDirection.fromCameraYaw(cameraYawDeg)

    fun isEnemyTemporarilyVisibleOnMinimap(bot: CombatantBot, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (bot.isFriendly || bot.isDead) return false
        val distToPlayer = hypot(bot.x - playerX, bot.z - playerZ)
        val revealedByRima = rimaRevealActive && distToPlayer <= 165f
        val detectedByAiOrShot = bot.detectedMarkerRemainingSec > 0f ||
            (bot.lastDetectedByPlayerTimeMs > 0L && (nowMs - bot.lastDetectedByPlayerTimeMs) in 1..4000L)
        val markedByTeammate = bot.teammateMarkedRemainingSec > 0f ||
            (bot.markedByFriendlyUntilMs > nowMs)
        return revealedByRima || detectedByAiOrShot || markedByTeammate
    }
}

class BattleRoyaleViewModel(
    private val repository: PlayerRepository,
    val soundEngine: SoundEngine = SoundEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private var profileEntity: PlayerProfileEntity = PlayerProfileEntity()
    private var gameLoopJob: Job? = null
    private var loadingScreenJob: Job? = null
    private var matchSeedCounter: Long = 100L
    private var lastPlayerSpawnX: Float? = null
    private var lastPlayerSpawnZ: Float? = null

    // Input & physics state
    private var moveInputX: Float = 0f
    private var moveInputY: Float = 0f // +1 forward, -1 backward
    private var fireCooldownRemainingSec: Float = 0f
    private var jumpRemainingSec: Float = 0f
    private var vaultRemainingSec: Float = 0f
    private var peakAirborneY: Float = 0f
    private var wasAirborne: Boolean = false
    private var playerStuckFrames: Int = 0
    private var accumulatedRecoilPitch: Float = 0f
    private var accumulatedRecoilYaw: Float = 0f
    private var shotParitySign: Int = 1
    private var smoothedGyroYawRate: Float = 0f
    private var smoothedGyroPitchRate: Float = 0f
    private var adaptiveAiSkillFactor: Float = 1.0f
    private var aiTickCounter: Int = 0
    private var nextDynamicEntityId: Int = 1000
    private var statusBannerClearTimerSec: Float = 0f
    private var matchStartInProgress: Boolean = false
    private var matchFinishedForCurrentGame: Boolean = false

    init {
        val loaded = repository.getOrInitProfileSync()
        applyLoadedProfile(loaded)
    }

    fun checkAndResetDailyMissionsIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        val currentDay = (nowMs.coerceAtLeast(0L)) / PlayerRepository.TWENTY_FOUR_HOURS_MS
        if (profileEntity.lastMissionResetDay != currentDay) {
            val freshMissions = PlayerRepository.defaultDailyMissions()
            profileEntity = profileEntity.copy(
                lastMissionResetDay = currentDay,
                dailyMissionsJson = PlayerRepository.encodeMissions(freshMissions)
            )
            _uiState.update { it.copy(dailyMissions = freshMissions) }
            persistCurrentProfile()
        }
    }

    private fun applyLoadedProfile(entity: PlayerProfileEntity) {
        val safeLevel = entity.level.coerceAtLeast(1)
        val safeXp = entity.xp.coerceAtLeast(0)
        profileEntity = entity.copy(level = safeLevel, xp = safeXp)
        val charId = CharacterId.entries.firstOrNull { it.name == entity.selectedCharacter } ?: CharacterId.SANI
        val mode = TeamMode.entries.firstOrNull { it.name == entity.selectedTeamMode } ?: TeamMode.SQUAD
        val gfx = GraphicsQuality.entries.firstOrNull { it.name == entity.graphicsQuality } ?: GraphicsQuality.LOW
        val fpsMode = FpsTargetMode.entries.firstOrNull { it.name == entity.fpsTargetMode } ?: FpsTargetMode.AUTO_FPS
        val unlocked = (
            entity.unlockedCosmeticsCsv.split(",").filter { it.isNotBlank() }.toSet() +
                CosmeticCatalog.items.filter { it.unlockLevel <= safeLevel }.map { it.id }
            )
        val hudPresets = PlayerRepository.decodeHudLayouts(entity.hudLayoutsJson)
        val sens = PlayerRepository.decodeSensitivity(entity.sensitivityJson)
        val autoLoot = PlayerRepository.decodeAutoLoot(entity.autoLootJson)
        val soundOn = PlayerRepository.decodeSoundEnabled(entity.sensitivityJson, entity.autoLootJson)
        if (!soundOn) {
            soundEngine.isMuted = true
        }
        val missions = PlayerRepository.decodeMissions(entity.dailyMissionsJson)

        _uiState.update { state ->
            state.copy(
                selectedCharacter = charId,
                selectedTeamMode = mode,
                playerLevel = safeLevel,
                playerXp = safeXp,
                xpForNextLevel = PlayerRepository.xpRequiredForLevel(safeLevel),
                nextRewardLabel = PlayerRepository.nextCosmeticForLevel(safeLevel),
                unlockedCosmetics = unlocked,
                equippedOutfitId = entity.equippedOutfitId.ifBlank { "char_sani_default" },
                equippedWeaponSkinId = entity.equippedWeaponSkinId.ifBlank { "skin_vx47_cobalt" },
                activeHudSlot = entity.activeHudSlot.coerceIn(0, 2),
                hudPresets = hudPresets,
                graphicsQuality = gfx,
                effectiveGraphicsQuality = gfx,
                fpsTargetMode = fpsMode,
                aimAssistEnabled = entity.aimAssistEnabled,
                soundEnabled = soundOn,
                sensitivity = sens,
                autoLoot = autoLoot,
                dailyMissions = missions
            )
        }
    }

    private fun persistCurrentProfile() {
        val s = _uiState.value
        val prevLevel = profileEntity.level.coerceAtLeast(1)
        val prevXp = profileEntity.xp.coerceAtLeast(0)
        val curLevel = s.playerLevel.coerceAtLeast(1)
        val curXp = s.playerXp.coerceAtLeast(0)
        val safeLevel = maxOf(curLevel, prevLevel)
        val safeXp = when {
            curLevel > prevLevel -> curXp
            curLevel == prevLevel -> maxOf(curXp, prevXp)
            else -> prevXp
        }
        val mergedUnlocked = (
            profileEntity.unlockedCosmeticsCsv.split(",").filter { it.isNotBlank() } +
                s.unlockedCosmetics +
                CosmeticCatalog.items.filter { it.unlockLevel <= safeLevel }.map { it.id }
            ).toSet()
        val updated = profileEntity.copy(
            selectedCharacter = s.selectedCharacter.name,
            selectedTeamMode = s.selectedTeamMode.name,
            level = safeLevel,
            xp = safeXp,
            unlockedCosmeticsCsv = mergedUnlocked.joinToString(","),
            equippedOutfitId = s.equippedOutfitId.ifBlank { "char_sani_default" },
            equippedWeaponSkinId = s.equippedWeaponSkinId.ifBlank { "skin_vx47_cobalt" },
            activeHudSlot = s.activeHudSlot.coerceIn(0, 2),
            hudLayoutsJson = PlayerRepository.encodeHudLayouts(s.hudPresets),
            graphicsQuality = s.graphicsQuality.name,
            fpsTargetMode = s.fpsTargetMode.name,
            aimAssistEnabled = s.aimAssistEnabled,
            sensitivityJson = PlayerRepository.encodeSensitivity(s.sensitivity, s.soundEnabled),
            autoLootJson = PlayerRepository.encodeAutoLoot(s.autoLoot, s.soundEnabled),
            dailyMissionsJson = PlayerRepository.encodeMissions(s.dailyMissions)
        )
        profileEntity = updated
        repository.saveProfileSync(updated)
    }

    // =========================================================================
    // LOBBY & SETTINGS ACTIONS
    // =========================================================================

    fun selectCharacter(characterId: CharacterId) {
        soundEngine.playUiClick()
        _uiState.update { it.copy(selectedCharacter = characterId) }
        persistCurrentProfile()
    }

    fun selectTeamMode(mode: TeamMode) {
        soundEngine.playUiClick()
        _uiState.update { it.copy(selectedTeamMode = mode) }
        persistCurrentProfile()
    }

    fun equipCosmetic(itemId: String) {
        val s = _uiState.value
        if (itemId !in s.unlockedCosmetics) return
        soundEngine.playUiClick()
        val item = CosmeticCatalog.items.firstOrNull { it.id == itemId } ?: return
        _uiState.update {
            if (item.category.name.contains("WEAPON")) {
                it.copy(equippedWeaponSkinId = itemId)
            } else {
                it.copy(equippedOutfitId = itemId)
            }
        }
        persistCurrentProfile()
    }

    fun claimDailyMission(missionId: String) {
        val s = _uiState.value
        val mission = s.dailyMissions.firstOrNull { it.id == missionId } ?: return
        if (!mission.isCompleted || mission.claimed) return
        soundEngine.playCoinPickup()

        val updatedMissions = s.dailyMissions.map {
            if (it.id == missionId) it.copy(claimed = true) else it
        }
        val extraUnlocked = mission.cosmeticRewardId?.let { setOf(it) } ?: emptySet()

        awardXpAndSave(
            addedXp = mission.xpReward.coerceAtLeast(0),
            updatedMissions = updatedMissions,
            extraUnlocked = extraUnlocked
        )
    }

    fun updateGraphicsQuality(quality: GraphicsQuality) {
        soundEngine.playUiClick()
        _uiState.update {
            it.copy(graphicsQuality = quality, effectiveGraphicsQuality = quality)
        }
        persistCurrentProfile()
    }

    fun updateFpsTargetMode(mode: FpsTargetMode) {
        soundEngine.playUiClick()
        _uiState.update {
            it.copy(
                fpsTargetMode = mode,
                effectiveGraphicsQuality = it.graphicsQuality
            )
        }
        persistCurrentProfile()
    }

    fun updateAimAssist(enabled: Boolean) {
        soundEngine.playUiClick()
        _uiState.update { it.copy(aimAssistEnabled = enabled) }
        persistCurrentProfile()
    }

    fun updateSoundEnabled(enabled: Boolean) {
        soundEngine.isMuted = !enabled
        if (enabled) {
            soundEngine.playUiClick()
        }
        _uiState.update { it.copy(soundEnabled = enabled) }
        persistCurrentProfile()
    }

    fun updateSensitivity(newSens: SensitivitySettings) {
        val cleanSens = newSens.sanitized()
        _uiState.update { it.copy(sensitivity = cleanSens) }
        persistCurrentProfile()
    }

    fun updateGyroscopeMode(mode: GyroscopeMode) {
        soundEngine.playUiClick()
        smoothedGyroYawRate = 0f
        smoothedGyroPitchRate = 0f
        _uiState.update { state ->
            state.copy(sensitivity = state.sensitivity.copy(gyroMode = mode).sanitized())
        }
        persistCurrentProfile()
    }

    fun updateAutoLootEnabled(enabled: Boolean) {
        soundEngine.playUiClick()
        _uiState.update { state ->
            state.copy(autoLoot = state.autoLoot.copy(enabled = enabled))
        }
        persistCurrentProfile()
    }

    fun updateAutoLoot(newAutoLoot: AutoLootSettings) {
        _uiState.update { it.copy(autoLoot = newAutoLoot) }
        persistCurrentProfile()
    }

    // =========================================================================
    // HUD EDITOR ACTIONS (Max 3 layouts, move/resize/alpha/reset, clamped)
    // =========================================================================

    fun openHudEditor() {
        soundEngine.playUiClick()
        _uiState.update { it.copy(screenState = AppScreenState.HUD_EDITOR) }
    }

    fun closeHudEditor() {
        soundEngine.playUiClick()
        persistCurrentProfile()
        _uiState.update { it.copy(screenState = AppScreenState.LOBBY) }
    }

    fun selectHudLayoutSlot(slotIndex: Int) {
        val clampedSlot = slotIndex.coerceIn(0, 2)
        soundEngine.playUiClick()
        _uiState.update { it.copy(activeHudSlot = clampedSlot) }
        persistCurrentProfile()
    }

    fun updateHudControl(
        controlId: HudControlId,
        normX: Float? = null,
        normY: Float? = null,
        sizeScale: Float? = null,
        alpha: Float? = null
    ) {
        _uiState.update { state ->
            val slot = state.activeHudSlot.coerceIn(0, 2)
            val currentPreset = state.hudPresets[slot]
            val existing = currentPreset.controls[controlId] ?: HudControlConfig(controlId)
            val updatedControl = existing.copy(
                normX = normX ?: existing.normX,
                normY = normY ?: existing.normY,
                sizeScale = sizeScale ?: existing.sizeScale,
                alpha = alpha ?: existing.alpha
            ).clamped()
            val updatedMap = currentPreset.controls.toMutableMap().apply {
                put(controlId, updatedControl)
            }
            val updatedPresets = state.hudPresets.mapIndexed { idx, preset ->
                if (idx == slot) preset.copy(controls = updatedMap) else preset
            }
            state.copy(hudPresets = updatedPresets)
        }
        persistCurrentProfile()
    }

    fun resetActiveHudLayout() {
        soundEngine.playUiClick()
        _uiState.update { state ->
            val slot = state.activeHudSlot.coerceIn(0, 2)
            val updatedPresets = state.hudPresets.mapIndexed { idx, preset ->
                if (idx == slot) HudLayoutPreset.defaultPreset(slot) else preset
            }
            state.copy(hudPresets = updatedPresets)
        }
        persistCurrentProfile()
    }

    fun saveActiveHudLayout() {
        soundEngine.playCoinPickup()
        persistCurrentProfile()
    }

    // =========================================================================
    // MATCH START & COMPLETE FRESH RESET (Section 28)
    // =========================================================================

    fun startNewMatch() {
        val current = _uiState.value
        if (matchStartInProgress || current.isStartingMatch || current.screenState == AppScreenState.IN_MATCH) {
            return
        }
        matchStartInProgress = true
        matchFinishedForCurrentGame = false
        loadingScreenJob?.cancel()
        gameLoopJob?.cancel()

        try {
            soundEngine.playUiClick()

            val s = _uiState.value
            matchSeedCounter += 7919L
            val matchSeed = System.currentTimeMillis() xor matchSeedCounter
            val rng = Random(matchSeed)
            val chosenChar = s.selectedCharacter
            val mode = s.selectedTeamMode

            // Reset temporary input & physics states
            moveInputX = 0f
            moveInputY = 0f
            fireCooldownRemainingSec = 0f
            jumpRemainingSec = 0f
            vaultRemainingSec = 0f
            peakAirborneY = 0f
            wasAirborne = false
            playerStuckFrames = 0
            accumulatedRecoilPitch = 0f
            accumulatedRecoilYaw = 0f
            shotParitySign = 1
            smoothedGyroYawRate = 0f
            smoothedGyroPitchRate = 0f
            aiTickCounter = 0

            // 1. Find a valid random spawn location on the existing map (on ground, never in water/building/wall)
            val (spawnX, spawnZ, zoneName) = IslandMapGenerator.findValidRandomPlayerSpawn(
                rng = rng,
                previousSpawnX = lastPlayerSpawnX,
                previousSpawnZ = lastPlayerSpawnZ
            )
            lastPlayerSpawnX = spawnX
            lastPlayerSpawnZ = spawnZ
            val spawnY = IslandMapGenerator.getTerrainHeight(spawnX, spawnZ)

            // Track all occupied spawn points so no teammate or enemy spawns inside another player
            val occupiedSpawns = ArrayList<Pair<Float, Float>>(24)
            occupiedSpawns.add(spawnX to spawnZ)

            // 2. Fresh per-match loot, vending machines, and vehicles
            val freshLoot = IslandMapGenerator.generateMatchLoot(matchSeed, spawnX, spawnZ)
            val freshVending = IslandMapGenerator.buildInitialVendingMachines()
            val freshVehicles = IslandMapGenerator.buildInitialVehicles()

            // 3. Spawn friendly teammates in Duo (1) and Squad (3) independently and never inside objects
            val bots = ArrayList<CombatantBot>(23)
            var botId = 1
            val friendlyNames = listOf("ALPHA-2", "BRAVO-3", "DELTA-4")
            val allyWeapons = listOf(
                OriginalWeaponCatalog.P18_TALON,
                OriginalWeaponCatalog.MP9_VIPER,
                OriginalWeaponCatalog.VX47_STRIKER
            )
            for (i in 0 until mode.friendlyBots) {
                val (safeFx, safeFz) = IslandMapGenerator.findValidTeammateSpawn(
                    playerX = spawnX,
                    playerZ = spawnZ,
                    teammateIndex = i,
                    totalTeammates = mode.friendlyBots,
                    occupiedSpawns = occupiedSpawns,
                    rng = rng
                )
                occupiedSpawns.add(safeFx to safeFz)
                val allyChar = if (i % 2 == 0) CharacterId.RIMA else CharacterId.SANI
                val patrolAngle = (i * (2.0 * PI / mode.friendlyBots.coerceAtLeast(1))) + 0.45
                val targetX = (safeFx + cos(patrolAngle).toFloat() * 18f)
                    .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 30f, IslandMapGenerator.PLAYABLE_LIMIT - 30f)
                val rawTargetZ = (safeFz + sin(patrolAngle).toFloat() * 18f)
                    .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 30f, IslandMapGenerator.PLAYABLE_LIMIT - 30f)
                val targetZ = if (safeFz >= 0f) max(38f, rawTargetZ) else min(-38f, rawTargetZ)
                val startWep = allyWeapons[i % allyWeapons.size]

                bots.add(
                    CombatantBot(
                        id = botId++,
                        name = friendlyNames[i % friendlyNames.size],
                        squadId = 0,
                        isFriendly = true,
                        characterId = allyChar,
                        x = safeFx,
                        y = IslandMapGenerator.getTerrainHeight(safeFx, safeFz),
                        z = safeFz,
                        lastX = safeFx,
                        lastZ = safeFz,
                        yawDeg = ((i * 120f) % 360f),
                        hp = 100f,
                        maxHp = 100f,
                        armor = 50f,
                        isKnocked = false,
                        isDead = false,
                        equippedWeapon = startWep,
                        ammoInMag = startWep.magazineSize,
                        reserveAmmo = startWep.magazineSize * 3,
                        targetX = targetX,
                        targetZ = targetZ
                    )
                )
            }

            // 4. Spawn enemy combatants at valid ground positions across the island
            val enemyCount = 23 - mode.friendlyBots
            val botWeapons = listOf(
                OriginalWeaponCatalog.P18_TALON,
                OriginalWeaponCatalog.VX47_STRIKER,
                OriginalWeaponCatalog.MP9_VIPER,
                OriginalWeaponCatalog.SG12_BREACHER,
                OriginalWeaponCatalog.MK14_LYNX,
                OriginalWeaponCatalog.KR556_PHANTOM
            )
            for (i in 0 until enemyCount) {
                val squadId = 1 + (i / mode.squadSize)
                val (safeEx, safeEz) = IslandMapGenerator.findValidEnemySpawn(
                    playerX = spawnX,
                    playerZ = spawnZ,
                    enemyIndex = i,
                    totalEnemies = enemyCount,
                    occupiedSpawns = occupiedSpawns,
                    rng = rng
                )
                occupiedSpawns.add(safeEx to safeEz)
                val startWep = botWeapons[i % botWeapons.size]
                bots.add(
                    CombatantBot(
                        id = botId++,
                        name = "Operative-${10 + i}",
                        squadId = squadId,
                        isFriendly = false,
                        characterId = if (i % 2 == 0) CharacterId.SANI else CharacterId.RIMA,
                        x = safeEx,
                        y = IslandMapGenerator.getTerrainHeight(safeEx, safeEz),
                        z = safeEz,
                        lastX = safeEx,
                        lastZ = safeEz,
                        yawDeg = rng.nextFloat() * 360f,
                        hp = 100f,
                        maxHp = 100f,
                        armor = 50f,
                        isKnocked = false,
                        isDead = false,
                        equippedWeapon = startWep,
                        ammoInMag = startWep.magazineSize,
                        reserveAmmo = startWep.magazineSize * 3,
                        targetX = (safeEx + (rng.nextFloat() * 50f - 25f))
                            .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 30f, IslandMapGenerator.PLAYABLE_LIMIT - 30f),
                        targetZ = (safeEz + (rng.nextFloat() * 50f - 25f))
                            .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 30f, IslandMapGenerator.PLAYABLE_LIMIT - 30f)
                            .let { tz -> if (safeEz >= 0f) max(38f, tz) else min(-38f, tz) }
                    )
                )
            }

            // 5. Complete fresh reset of all player stats, weapons, ammo, abilities, coins, loot, knock/death, and Safe Zone
            _uiState.update { state ->
                state.copy(
                    screenState = AppScreenState.IN_MATCH,
                    isStartingMatch = true,
                    matchLoadingProgress = 0.35f,
                    matchLoadingRemainingSec = 0.50f,
                    spawnZoneName = zoneName,
                    matchCharacter = chosenChar,
                    playerX = spawnX,
                    playerY = spawnY,
                    playerZ = spawnZ,
                    playerYawDeg = 0f,
                    playerPitchDeg = 0f,
                    playerHp = 100f,
                    playerMaxHp = 100f,
                    playerArmor = 50f,
                    playerMaxArmor = 100f,
                    helmetTier = 1,
                    armorTier = 1,
                    backpackTier = 1,
                    isSprinting = false,
                    isCrouching = false,
                    isProne = false,
                    isSwimming = false,
                    isJumping = false,
                    isVaulting = false,
                    playerAnimState = CharacterAnimState.IDLE,
                    playerAnimPhase = 0f,
                    isPlayerKnocked = false,
                    playerKnockedTimerSec = 25f,
                    isBeingRevived = false,
                    reviveProgressSec = 0f,
                    isPlayerDead = false,
                    isSpectating = false,
                    killerBotId = null,
                    spectatorTargets = emptyList(),
                    activeSpectatorTargetIndex = 0,
                    activeSpectatorTargetId = null,
                    weaponSlots = mapOf(
                        WeaponSlotIndex.MAIN_1 to null,
                        WeaponSlotIndex.MAIN_2 to null,
                        WeaponSlotIndex.SECONDARY to WeaponInstance(OriginalWeaponCatalog.P18_TALON)
                    ),
                    activeWeaponSlot = WeaponSlotIndex.SECONDARY,
                    isAiming = false,
                    isScopedIn = false,
                    isFreeLooking = false,
                    freeLookYawOffsetDeg = 0f,
                    freeLookPitchOffsetDeg = 0f,
                    smoothFovScale = 1.0f,
                    smoothCamDistBehind = 4.2f,
                    smoothShoulderOffsetX = 0f,
                    isReloading = false,
                    reloadRemainingSec = 0f,
                    isManualFiringHeld = false,
                    lastHitZone = HitZone.MISS,
                    lastDamageDealtAmount = 0,
                    lastDamageTargetName = "",
                    hitMarkerTimerSec = 0f,
                    hitEffects = emptyList(),
                    ammoReserve = mapOf(
                        AmmoType.AR_AMMO to 30,
                        AmmoType.SMG_AMMO to 45,
                        AmmoType.SG_AMMO to 0,
                        AmmoType.SNIPER_AMMO to 0
                    ),
                    inventoryScopes = setOf(ScopeType.RED_DOT),
                    unequippedMuzzles = 0,
                    unequippedExtendedMags = 0,
                    unequippedForegrips = 0,
                    medkitCount = 2,
                    grenadeCount = 1,
                    glooWallCount = 2,
                    matchCoins = 0,
                    drivingVehicleId = null,
                    vehicleSpeedMps = 0f,
                    vehicleThrottleInput = 0f,
                    vehicleBrakeInput = false,
                    vehicleSteerInput = 0f,
                    abilityState = AbilityState.READY,
                    abilityTimerSec = 0f,
                    rimaRevealActive = false,
                    safeZone = SafeZoneState(
                        phase = 1,
                        centerX = 0f,
                        centerZ = 0f,
                        currentRadius = 510f,
                        targetCenterX = (spawnX * 0.35f + (rng.nextFloat() * 60f - 30f)).coerceIn(-120f, 120f),
                        targetCenterZ = (spawnZ * 0.35f + (rng.nextFloat() * 60f - 30f)).coerceIn(-120f, 120f),
                        targetRadius = 350f,
                        isShrinking = false,
                        warningTimerSec = 30f,
                        damagePerSecond = 2.5f,
                        warningMessage = "Phase 1 - Safe Zone Shrink in 30s"
                    ),
                    lootItems = freshLoot,
                    bots = bots,
                    vendingMachines = freshVending,
                    vehicles = freshVehicles,
                    glooWalls = emptyList(),
                    bulletTraces = emptyList(),
                    footstepIndicators = emptyList(),
                    fireDirectionIndicators = emptyList(),
                    friendlyCallout = if (mode.friendlyBots > 0) {
                        FriendlyVoiceCallout("SQUAD", "Dropped at $zoneName! Let's loot up!")
                    } else null,
                    nearbyVendingMachine = null,
                    nearbyKnockAllyToRevive = null,
                    nearbyVehicle = null,
                    aliveCount = 1 + bots.size,
                    playerKills = 0,
                    playerHeadshots = 0,
                    playerDamageDealt = 0f,
                    playerLootCollectedCount = 0,
                    playerCoinsCollectedTotal = 0,
                    playerTeammatesRevived = 0,
                    matchElapsedSec = 0f,
                    statusBannerText = "DEPLOYED AT $zoneName (${mode.label.uppercase()})",
                    isInventoryOpen = false,
                    matchStartCount = state.matchStartCount + 1,
                    lastMatchResult = null
                )
            }
            statusBannerClearTimerSec = 4.5f
            startGameLoop()

            // Short loading screen transition (never gets stuck)
            loadingScreenJob = viewModelScope.launch {
                try {
                    delay(220L)
                    _uiState.update { it.copy(matchLoadingProgress = 0.75f, matchLoadingRemainingSec = 0.25f) }
                    delay(220L)
                } finally {
                    matchStartInProgress = false
                    _uiState.update {
                        it.copy(
                            isStartingMatch = false,
                            matchLoadingProgress = 1f,
                            matchLoadingRemainingSec = 0f
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            matchStartInProgress = false
            _uiState.update {
                it.copy(
                    isStartingMatch = false,
                    matchLoadingProgress = 0f,
                    matchLoadingRemainingSec = 0f
                )
            }
            throw t
        }
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            var lastTimeNs = System.nanoTime()
            var frameCounter = 0
            var fpsWindowMs = 0L

            while (_uiState.value.screenState == AppScreenState.IN_MATCH) {
                val targetFps = _uiState.value.fpsTargetMode.targetFps
                val frameBudgetMs = (1000L / targetFps).coerceIn(16L, 33L)
                delay(frameBudgetMs)

                val nowNs = System.nanoTime()
                val dt = ((nowNs - lastTimeNs) / 1_000_000_000.0).toFloat().coerceIn(0.01f, 0.08f)
                lastTimeNs = nowNs

                frameCounter++
                fpsWindowMs += (dt * 1000f).toLong()
                if (fpsWindowMs >= 1000L) {
                    val currentFps = ((frameCounter * 1000L) / fpsWindowMs.coerceAtLeast(1L)).toInt()
                    frameCounter = 0
                    fpsWindowMs = 0L
                    evaluateAutoFpsOptimization(currentFps)
                }

                stepSimulation(dt)
            }
        }
    }

    private fun evaluateAutoFpsOptimization(measuredFps: Int) {
        _uiState.update { state ->
            val effectiveGfx = if (state.fpsTargetMode == FpsTargetMode.AUTO_FPS && measuredFps < 32) {
                GraphicsQuality.LOW
            } else {
                state.graphicsQuality
            }
            state.copy(measuredFps = measuredFps, effectiveGraphicsQuality = effectiveGfx)
        }
    }

    // =========================================================================
    // CORE GAMEPLAY STABILITY & STATE VALIDATION (Sections 1, 2, 5, 8, 9, 10, 11)
    // =========================================================================

    /**
     * Validates all 7 required conditions before any player shot can occur (Section 9).
     */
    fun canPlayerFireNow(s: MatchUiState = _uiState.value): Boolean {
        if (s.isPlayerDead || s.isPlayerKnocked || s.isSpectating) return false
        val wep = s.activeWeapon ?: return false
        if (wep.currentAmmo <= 0) return false
        if (s.isReloading) return false
        if (s.isSwimming) return false
        if (s.matchCharacter == CharacterId.SANI && s.abilityState == AbilityState.ACTIVE) return false
        if (s.drivingVehicleId != null) return false
        return true
    }

    fun setJoystickInput(x: Float, y: Float, sprinting: Boolean) {
        val current = _uiState.value
        if (current.isPlayerDead || current.isSpectating) {
            moveInputX = 0f
            moveInputY = 0f
            if (current.isSprinting) {
                _uiState.update { it.copy(isSprinting = false) }
            }
            return
        }
        moveInputX = x.coerceIn(-1f, 1f)
        moveInputY = y.coerceIn(-1f, 1f)
        _uiState.update { state ->
            val canSprint = sprinting &&
                y > 0.55f &&
                !state.isCrouching &&
                !state.isProne &&
                !state.isReloading &&
                !state.isAiming &&
                !state.isScopedIn &&
                !state.isPlayerKnocked &&
                !state.isPlayerDead &&
                !state.isSpectating
            state.copy(isSprinting = canSprint)
        }
    }

    fun setFreeLooking(enabled: Boolean) {
        val s = _uiState.value
        if (s.isPlayerDead || s.isSpectating) return
        _uiState.update { state ->
            if (enabled) {
                state.copy(isFreeLooking = true)
            } else {
                state.copy(
                    isFreeLooking = false,
                    freeLookYawOffsetDeg = 0f,
                    freeLookPitchOffsetDeg = 0f
                )
            }
        }
    }

    fun rotateCameraByTouch(deltaYawPx: Float, deltaPitchPx: Float, isFreeLookDrag: Boolean = false) {
        if (deltaYawPx.isNaN() || deltaPitchPx.isNaN() || deltaYawPx.isInfinite() || deltaPitchPx.isInfinite()) return
        val s = _uiState.value
        if (s.isPlayerDead || s.isSpectating) return
        val clampedDx = deltaYawPx.coerceIn(-140f, 140f)
        val clampedDy = deltaPitchPx.coerceIn(-140f, 140f)
        if (abs(clampedDx) < 0.001f && abs(clampedDy) < 0.001f) return

        val sens = s.sensitivity.sanitized()
        if (s.isFreeLooking || isFreeLookDrag) {
            val freeSensFactor = (sens.freeLook / 310f).coerceIn(0.03f, 0.45f)
            _uiState.update { state ->
                if (state.isFreeLooking) {
                    val nextOffsetYaw = (state.freeLookYawOffsetDeg + clampedDx * freeSensFactor).coerceIn(-150f, 150f)
                    val nextOffsetPitch = (state.freeLookPitchOffsetDeg - clampedDy * freeSensFactor * 0.65f).coerceIn(-35f, 35f)
                    state.copy(
                        freeLookYawOffsetDeg = nextOffsetYaw,
                        freeLookPitchOffsetDeg = nextOffsetPitch
                    )
                } else {
                    val rawYaw = (state.playerYawDeg + clampedDx * freeSensFactor) % 360f
                    val newYaw = if (rawYaw < 0f) rawYaw + 360f else rawYaw
                    val newPitch = (state.playerPitchDeg - clampedDy * freeSensFactor * 0.65f).coerceIn(-38f, 38f)
                    state.copy(playerYawDeg = newYaw, playerPitchDeg = newPitch)
                }
            }
            return
        }

        val activeWep = s.activeWeapon
        val scope = if (s.isScopedIn) (activeWep?.equippedScope ?: ScopeType.RED_DOT) else ScopeType.NONE
        val baseSens = when {
            s.isScopedIn -> when (scope) {
                ScopeType.NONE, ScopeType.RED_DOT -> sens.redDot * 0.88f
                ScopeType.SCOPE_2X -> sens.scope2x * 0.72f
                ScopeType.SCOPE_4X -> sens.scope4x * 0.54f
                ScopeType.SNIPER_8X -> sens.sniper * 0.40f
            }
            s.isAiming -> sens.general * 0.86f
            else -> sens.general
        }

        // Subtle Aim Assist (when ON): only for nearby visible enemies with clear Line-of-Sight; never auto-shoots
        val assistTarget = if (s.aimAssistEnabled && (s.isAiming || s.isScopedIn || s.isManualFiringHeld)) {
            findBestAimAssistTarget(s)
        } else null
        val aimFriction = if (assistTarget != null) 0.78f else 1.0f
        val sensFactor = ((baseSens / 310f) * aimFriction).coerceIn(0.015f, 0.45f)

        var subtleAssistYawNudge = 0f
        if (assistTarget != null && abs(clampedDx) > 0.05f) {
            val targetAngle = ((Math.toDegrees(atan2((assistTarget.x - s.playerX).toDouble(), (assistTarget.z - s.playerZ).toDouble())).toFloat()) + 360f) % 360f
            val diff = normalizeAngleDiff(targetAngle - s.playerYawDeg)
            if (abs(diff) < 6.5f) {
                subtleAssistYawNudge = (diff * 0.14f).coerceIn(-0.42f, 0.42f)
            }
        }

        val pitchDelta = -clampedDy * sensFactor * 0.65f
        // Allow player to manually pull down to control recoil smoothly without over-recovery later
        if (pitchDelta < 0f && accumulatedRecoilPitch > 0f) {
            accumulatedRecoilPitch = (accumulatedRecoilPitch + pitchDelta).coerceAtLeast(0f)
        }

        _uiState.update { state ->
            val rawYaw = (state.playerYawDeg + clampedDx * sensFactor + subtleAssistYawNudge) % 360f
            val newYaw = if (rawYaw < 0f) rawYaw + 360f else rawYaw
            val newPitch = (state.playerPitchDeg + pitchDelta).coerceIn(-38f, 38f)
            state.copy(playerYawDeg = newYaw, playerPitchDeg = newPitch)
        }
    }

    fun applyGyroscopeDelta(gyroYawRateRad: Float, gyroPitchRateRad: Float) {
        if (gyroYawRateRad.isNaN() || gyroPitchRateRad.isNaN() || gyroYawRateRad.isInfinite() || gyroPitchRateRad.isInfinite()) return
        val s = _uiState.value
        if (s.screenState != AppScreenState.IN_MATCH || s.isPlayerDead || s.isSpectating) return
        val sens = s.sensitivity.sanitized()
        val mode = sens.gyroMode
        if (mode == GyroscopeMode.OFF) return
        if (mode == GyroscopeMode.SCOPE_ON && !s.isScopedIn && !s.isAiming) return

        // Deadzone + low-pass smoothing to prevent gyroscope sensor jitter / camera shaking on low-end phones
        val cleanYawRate = if (abs(gyroYawRateRad) < 0.018f) 0f else gyroYawRateRad.coerceIn(-6.0f, 6.0f)
        val cleanPitchRate = if (abs(gyroPitchRateRad) < 0.018f) 0f else gyroPitchRateRad.coerceIn(-6.0f, 6.0f)
        smoothedGyroYawRate = smoothedGyroYawRate * 0.35f + cleanYawRate * 0.65f
        smoothedGyroPitchRate = smoothedGyroPitchRate * 0.35f + cleanPitchRate * 0.65f
        if (abs(smoothedGyroYawRate) < 0.005f && abs(smoothedGyroPitchRate) < 0.005f) return

        val scope = if (s.isScopedIn) (s.activeWeapon?.equippedScope ?: ScopeType.RED_DOT) else ScopeType.NONE
        val gyroSens = when {
            !s.isScopedIn -> sens.gyroRedDot * 0.90f
            scope == ScopeType.RED_DOT || scope == ScopeType.NONE -> sens.gyroRedDot * 0.85f
            scope == ScopeType.SCOPE_2X -> sens.gyro2x * 0.70f
            scope == ScopeType.SCOPE_4X -> sens.gyro4x * 0.52f
            scope == ScopeType.SNIPER_8X -> sens.gyroSniper * 0.38f
            else -> sens.gyroRedDot * 0.85f
        } * 0.045f

        val pitchDelta = smoothedGyroPitchRate * gyroSens * 0.7f
        if (pitchDelta < 0f && accumulatedRecoilPitch > 0f) {
            accumulatedRecoilPitch = (accumulatedRecoilPitch + pitchDelta).coerceAtLeast(0f)
        }

        _uiState.update { state ->
            val rawYaw = (state.playerYawDeg - smoothedGyroYawRate * gyroSens) % 360f
            val newYaw = if (rawYaw < 0f) rawYaw + 360f else rawYaw
            val newPitch = (state.playerPitchDeg + pitchDelta).coerceIn(-38f, 38f)
            state.copy(playerYawDeg = newYaw, playerPitchDeg = newPitch)
        }
    }

    /**
     * Manual Fire ONLY! Auto-fire is strictly OFF.
     * - Semi-auto weapons fire once per press (require releasing Fire before firing again).
     * - Automatic weapons fire on press and continue firing while Fire is held.
     */
    fun onFireButtonPressedChanged(pressed: Boolean) {
        val s = _uiState.value
        if (!pressed) {
            _uiState.update { it.copy(isManualFiringHeld = false) }
            return
        }
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSpectating) {
            _uiState.update { it.copy(isManualFiringHeld = false) }
            return
        }
        if (s.matchCharacter == CharacterId.SANI && s.abilityState == AbilityState.ACTIVE) {
            showStatusBanner("CANNOT SHOOT WHILE SANI OVERDRIVE IS ACTIVE!")
            _uiState.update { it.copy(isManualFiringHeld = false) }
            return
        }
        if (s.isSwimming) {
            showStatusBanner("WEAPON USE RESTRICTED WHILE SWIMMING!")
            _uiState.update { it.copy(isManualFiringHeld = false) }
            return
        }
        if (s.isReloading) {
            showStatusBanner("RELOADING...")
            _uiState.update { it.copy(isManualFiringHeld = false) }
            return
        }
        val wep = s.activeWeapon
        if (wep == null) {
            _uiState.update { it.copy(isManualFiringHeld = false) }
            return
        }
        if (wep.currentAmmo <= 0) {
            _uiState.update { it.copy(isManualFiringHeld = false) }
            triggerManualReload()
            return
        }

        // Semi-auto weapons fire strictly once per press
        val wasAlreadyHeld = s.isManualFiringHeld
        _uiState.update { it.copy(isManualFiringHeld = true, isSprinting = false) }
        if (!wasAlreadyHeld || wep.spec.category.isAutomatic) {
            if (fireCooldownRemainingSec <= 0f) {
                executeSinglePlayerShot()
            }
        }
    }

    fun toggleAim() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSwimming || s.isReloading || s.drivingVehicleId != null) return
        if (s.matchCharacter == CharacterId.SANI && s.abilityState == AbilityState.ACTIVE) return
        soundEngine.playUiClick()
        _uiState.update { state ->
            val nextAim = if (state.isScopedIn) false else !state.isAiming
            val nextFov = if (nextAim) 1.22f else 1.0f
            val nextDist = if (nextAim) 2.4f else 4.2f
            val nextShoulder = if (nextAim) 0.44f else 0f
            state.copy(
                isAiming = nextAim,
                isScopedIn = false,
                isFreeLooking = false,
                freeLookYawOffsetDeg = 0f,
                freeLookPitchOffsetDeg = 0f,
                isSprinting = false,
                smoothFovScale = nextFov,
                smoothCamDistBehind = nextDist,
                smoothShoulderOffsetX = nextShoulder
            )
        }
    }

    fun closeScope() {
        _uiState.update { state ->
            state.copy(
                isScopedIn = false,
                isAiming = false,
                smoothFovScale = 1.0f,
                smoothCamDistBehind = 4.2f,
                smoothShoulderOffsetX = 0f
            )
        }
    }

    fun selectScopeForActiveWeapon(scope: ScopeType) {
        val s = _uiState.value
        if (s.isPlayerDead || s.isPlayerKnocked) return
        val wep = s.activeWeapon ?: return
        if (scope !in wep.spec.category.compatibleScopes) {
            showStatusBanner("${scope.label.uppercase()} INCOMPATIBLE WITH ${wep.spec.name.uppercase()}")
            return
        }
        soundEngine.playUiClick()
        val updatedWep = wep.copy(equippedScope = scope).sanitized()
        _uiState.update { state ->
            val updatedSlots = state.weaponSlots.toMutableMap().apply {
                put(state.activeWeaponSlot, updatedWep)
            }
            val nextScoped = if (scope == ScopeType.NONE) false else state.isScopedIn
            val nextAim = if (scope == ScopeType.NONE) false else state.isAiming
            val nextFov = when {
                nextScoped -> (1f / scope.fovMultiplier).coerceIn(1.2f, 3.6f)
                nextAim -> 1.22f
                else -> 1.0f
            }
            state.copy(
                weaponSlots = updatedSlots,
                inventoryScopes = if (scope != ScopeType.NONE) state.inventoryScopes + scope else state.inventoryScopes,
                isScopedIn = nextScoped,
                isAiming = nextAim,
                smoothFovScale = nextFov
            )
        }
    }

    fun toggleOrCycleScope() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSwimming || s.isReloading || s.drivingVehicleId != null) return
        if (s.matchCharacter == CharacterId.SANI && s.abilityState == AbilityState.ACTIVE) return
        val wep = s.activeWeapon ?: return
        soundEngine.playUiClick()

        val ownedPlusEquipped = (s.inventoryScopes + wep.equippedScope)
        val compatibleOwned = ownedPlusEquipped
            .intersect(wep.spec.category.compatibleScopes)
            .sortedBy { it.zoomFactor }
        if (!s.isScopedIn) {
            val chosenScope = if (wep.equippedScope != ScopeType.NONE && wep.equippedScope in wep.spec.category.compatibleScopes) {
                wep.equippedScope
            } else {
                compatibleOwned.lastOrNull { it != ScopeType.NONE } ?: ScopeType.NONE
            }
            if (chosenScope == ScopeType.NONE) {
                showStatusBanner("NO COMPATIBLE SCOPE FOR ${wep.spec.name.uppercase()}")
                return
            }
            val updatedWep = wep.copy(equippedScope = chosenScope).sanitized()
            val targetFov = (1f / chosenScope.fovMultiplier).coerceIn(1.2f, 3.6f)
            _uiState.update { state ->
                val updatedSlots = state.weaponSlots.toMutableMap().apply {
                    put(state.activeWeaponSlot, updatedWep)
                }
                state.copy(
                    weaponSlots = updatedSlots,
                    inventoryScopes = state.inventoryScopes + chosenScope,
                    isAiming = true,
                    isScopedIn = true,
                    isFreeLooking = false,
                    freeLookYawOffsetDeg = 0f,
                    freeLookPitchOffsetDeg = 0f,
                    isSprinting = false,
                    smoothFovScale = targetFov,
                    smoothCamDistBehind = 0.25f,
                    smoothShoulderOffsetX = 0f
                )
            }
        } else {
            val list = listOf(ScopeType.NONE) + compatibleOwned.filter { it != ScopeType.NONE }
            val idx = list.indexOf(wep.equippedScope).coerceAtLeast(0)
            val nextIdx = (idx + 1) % list.size
            val nextScope = list[nextIdx]
            if (nextScope == ScopeType.NONE) {
                // When scope closes -> restore normal third-person camera and sensitivity cleanly
                closeScope()
            } else {
                val updatedWep = wep.copy(equippedScope = nextScope).sanitized()
                val targetFov = (1f / nextScope.fovMultiplier).coerceIn(1.2f, 3.6f)
                _uiState.update { state ->
                    val updatedSlots = state.weaponSlots.toMutableMap().apply {
                        put(state.activeWeaponSlot, updatedWep)
                    }
                    state.copy(
                        weaponSlots = updatedSlots,
                        isScopedIn = true,
                        isAiming = true,
                        smoothFovScale = targetFov,
                        smoothCamDistBehind = 0.25f,
                        smoothShoulderOffsetX = 0f
                    )
                }
                showStatusBanner("EQUIPPED ${nextScope.label.uppercase()}")
            }
        }
    }

    fun triggerManualReload() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isReloading || s.isSwimming) return
        val wep = s.activeWeapon ?: return
        if (wep.currentAmmo >= wep.maxMagazineSize) {
            showStatusBanner("MAGAZINE FULL")
            return
        }
        val ammoType = wep.spec.category.ammoType
        val available = (s.ammoReserve[ammoType] ?: 0).coerceAtLeast(0)
        if (available <= 0) {
            // Do not perform a fake reload! Give appropriate UI feedback.
            soundEngine.playUiClick()
            showStatusBanner("NO ${ammoType.label.uppercase()} AVAILABLE!")
            return
        }
        soundEngine.playReload()
        val reloadDuration = wep.effectiveReloadSec.coerceAtLeast(0.8f)
        accumulatedRecoilPitch = 0f
        accumulatedRecoilYaw = 0f
        _uiState.update {
            it.copy(
                isReloading = true,
                reloadRemainingSec = reloadDuration,
                isManualFiringHeld = false,
                isScopedIn = false,
                isAiming = false,
                smoothFovScale = 1.0f,
                smoothCamDistBehind = 4.2f,
                smoothShoulderOffsetX = 0f,
                isSprinting = false,
                playerAnimState = CharacterAnimState.RELOAD
            )
        }
    }

    /**
     * Fast weapon switching (Section 5):
     * - Stops invalid reload states
     * - Resets recoil & temporary firing/aiming/reload animation states
     * - Updates weapon model, ammo UI, crosshair, scope, fire mode, and attachments
     * - Never displays the wrong weapon's ammo
     */
    fun switchWeaponSlot(slot: WeaponSlotIndex) {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead) return
        val targetWeapon = s.weaponSlots[slot]?.sanitized()
        if (targetWeapon == null) {
            showStatusBanner("${slot.label.uppercase()} SLOT EMPTY")
            return
        }
        soundEngine.playUiClick()
        fireCooldownRemainingSec = 0f
        accumulatedRecoilPitch = 0f
        accumulatedRecoilYaw = 0f
        val updatedSlots = s.weaponSlots.toMutableMap().apply {
            put(slot, targetWeapon)
        }
        _uiState.update { state ->
            val nextAnim = if (state.playerAnimState in setOf(CharacterAnimState.RELOAD, CharacterAnimState.SHOOT, CharacterAnimState.AIM)) {
                CharacterAnimState.IDLE
            } else {
                state.playerAnimState
            }
            state.copy(
                weaponSlots = updatedSlots,
                activeWeaponSlot = slot,
                isReloading = false,
                reloadRemainingSec = 0f,
                isManualFiringHeld = false,
                isScopedIn = false,
                isAiming = false,
                smoothFovScale = 1.0f,
                smoothCamDistBehind = 4.2f,
                smoothShoulderOffsetX = 0f,
                playerAnimState = nextAnim
            )
        }
    }

    /**
     * Smooth Jump / Stand / Vault transitions (Section 2):
     * - If crouching or prone and presses jump -> stands up first when valid!
     * - If standing and near a vaultable low wall -> vaults cleanly.
     * - Otherwise jumps cleanly.
     */
    fun triggerJumpOrVault() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSwimming || s.isJumping || s.isVaulting || s.drivingVehicleId != null) return

        // If crouching or prone, pressing Jump stands the character up smoothly first!
        if (s.isCrouching || s.isProne) {
            soundEngine.playUiClick()
            _uiState.update {
                it.copy(
                    isCrouching = false,
                    isProne = false,
                    playerAnimState = CharacterAnimState.IDLE
                )
            }
            return
        }

        soundEngine.playJump()

        val rad = Math.toRadians(s.playerYawDeg.toDouble())
        val checkX = s.playerX + sin(rad).toFloat() * 2.4f
        val checkZ = s.playerZ + cos(rad).toFloat() * 2.4f
        val aheadBuilding = IslandMapGenerator.findCollidingBuilding(checkX, checkZ, radius = 0.9f, ignoreVaultable = false)

        if (aheadBuilding != null && aheadBuilding.isVaultable) {
            vaultRemainingSec = 0.52f
            _uiState.update {
                it.copy(
                    isVaulting = true,
                    isCrouching = false,
                    isProne = false,
                    isScopedIn = false,
                    playerAnimState = CharacterAnimState.VAULT
                )
            }
            showStatusBanner("VAULTING OBSTACLE")
        } else {
            jumpRemainingSec = 0.56f
            _uiState.update {
                it.copy(
                    isJumping = true,
                    isCrouching = false,
                    isProne = false,
                    playerAnimState = CharacterAnimState.JUMP
                )
            }
        }
    }

    /**
     * Smooth Crouch transition (Section 2):
     * If player is sprinting and presses crouch -> transitions smoothly to crouch!
     */
    fun toggleCrouch() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSwimming || s.drivingVehicleId != null) return
        soundEngine.playUiClick()
        jumpRemainingSec = 0f
        vaultRemainingSec = 0f
        _uiState.update { state ->
            val next = !state.isCrouching
            state.copy(
                isCrouching = next,
                isProne = false,
                isSprinting = false,
                isJumping = false,
                isVaulting = false,
                playerAnimState = if (next) CharacterAnimState.CROUCH else CharacterAnimState.IDLE
            )
        }
    }

    fun toggleProne() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSwimming || s.drivingVehicleId != null) return
        soundEngine.playUiClick()
        jumpRemainingSec = 0f
        vaultRemainingSec = 0f
        _uiState.update { state ->
            val next = !state.isProne
            state.copy(
                isProne = next,
                isCrouching = false,
                isSprinting = false,
                isJumping = false,
                isVaulting = false,
                playerAnimState = if (next) CharacterAnimState.PRONE else CharacterAnimState.IDLE
            )
        }
    }

    fun activateCharacterAbility() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSpectating) return
        if (s.abilityState != AbilityState.READY) {
            showStatusBanner("ABILITY ON COOLDOWN (${s.abilityTimerSec.toInt()}s)")
            return
        }

        val charId = s.matchCharacter
        if (charId == CharacterId.SANI) {
            soundEngine.playSaniAbility()
            _uiState.update {
                it.copy(
                    abilityState = AbilityState.ACTIVE,
                    abilityTimerSec = charId.activeDurationSec,
                    isManualFiringHeld = false,
                    isReloading = false,
                    reloadRemainingSec = 0f,
                    isScopedIn = false,
                    isAiming = false,
                    isCrouching = false,
                    isProne = false,
                    playerAnimState = CharacterAnimState.ABILITY
                )
            }
            showStatusBanner("SANI KINETIC OVERDRIVE ACTIVE (+45% SPEED, WEAPONS LOCKED)")
        } else {
            soundEngine.playRimaAbility()
            _uiState.update {
                it.copy(
                    abilityState = AbilityState.ACTIVE,
                    abilityTimerSec = charId.activeDurationSec,
                    rimaRevealActive = true,
                    playerAnimState = CharacterAnimState.ABILITY
                )
            }
            showStatusBanner("RIMA TACTICAL PULSE ACTIVE (NEARBY ENEMIES REVEALED FOR 11s)")
        }
    }

    fun useMedkit() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSpectating) return
        if (s.medkitCount <= 0) {
            showStatusBanner("NO MEDKITS IN INVENTORY!")
            return
        }
        if (s.playerHp >= s.playerMaxHp && s.playerArmor >= s.playerMaxArmor) {
            showStatusBanner("HP & ARMOR ALREADY FULL")
            return
        }
        soundEngine.playPickup()
        _uiState.update { state ->
            state.copy(
                medkitCount = (state.medkitCount - 1).coerceAtLeast(0),
                playerHp = (state.playerHp + 65f).coerceAtMost(state.playerMaxHp),
                playerArmor = (state.playerArmor + 35f).coerceAtMost(state.playerMaxArmor)
            )
        }
        showStatusBanner("USED MEDKIT (+65 HP)")
    }

    fun deployGlooWall() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSpectating || s.isSwimming) return
        if (s.glooWallCount <= 0) {
            showStatusBanner("NO GLOO WALLS REMAINING!")
            return
        }
        soundEngine.playPickup()
        val rad = Math.toRadians(s.playerYawDeg.toDouble())
        val gx = (s.playerX + sin(rad).toFloat() * 4.5f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT, IslandMapGenerator.PLAYABLE_LIMIT)
        val gz = (s.playerZ + cos(rad).toFloat() * 4.5f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT, IslandMapGenerator.PLAYABLE_LIMIT)
        val newWall = deployedGlooWall(
            id = nextDynamicEntityId++,
            x = gx,
            z = gz,
            yawDeg = s.playerYawDeg
        )
        _uiState.update { state ->
            state.copy(
                glooWallCount = (state.glooWallCount - 1).coerceAtLeast(0),
                glooWalls = state.glooWalls + newWall
            )
        }
        showStatusBanner("GLOO WALL DEPLOYED")
    }

    fun toggleInventorySheet() {
        val s = _uiState.value
        if (s.isPlayerDead || s.isSpectating) return
        soundEngine.playUiClick()
        _uiState.update { it.copy(isInventoryOpen = !it.isInventoryOpen) }
    }

    fun setVehicleThrottle(throttle: Float) {
        val cleanThrottle = if (throttle.isNaN() || throttle.isInfinite()) 0f else throttle.coerceIn(-1f, 1f)
        _uiState.update { state ->
            if (state.drivingVehicleId == null || state.isPlayerDead || state.isPlayerKnocked) {
                state.copy(vehicleThrottleInput = 0f)
            } else {
                state.copy(
                    vehicleThrottleInput = cleanThrottle,
                    vehicleBrakeInput = if (cleanThrottle > 0.05f) false else state.vehicleBrakeInput
                )
            }
        }
    }

    fun setVehicleSteering(steer: Float) {
        val cleanSteer = if (steer.isNaN() || steer.isInfinite()) 0f else steer.coerceIn(-1f, 1f)
        _uiState.update { state ->
            if (state.drivingVehicleId == null || state.isPlayerDead || state.isPlayerKnocked) {
                state.copy(vehicleSteerInput = 0f)
            } else {
                state.copy(vehicleSteerInput = cleanSteer)
            }
        }
    }

    fun setVehicleBraking(braking: Boolean) {
        _uiState.update { state ->
            if (state.drivingVehicleId == null || state.isPlayerDead || state.isPlayerKnocked) {
                state.copy(vehicleBrakeInput = false)
            } else {
                state.copy(
                    vehicleBrakeInput = braking,
                    vehicleThrottleInput = if (braking && state.vehicleThrottleInput > 0f) 0f else state.vehicleThrottleInput
                )
            }
        }
    }

    fun toggleEnterExitVehicle() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSpectating || s.isSwimming) return
        if (s.drivingVehicleId != null) {
            soundEngine.playUiClick()
            val activeVehId = s.drivingVehicleId
            val currentVeh = s.vehicles.firstOrNull { it.id == activeVehId }
            val vehX = currentVeh?.x ?: s.playerX
            val vehZ = currentVeh?.z ?: s.playerZ
            val vehYaw = currentVeh?.yawDeg ?: s.playerYawDeg
            val (safeVehX, safeVehZ) = IslandMapGenerator.recoverVehicleIfStuck(
                x = vehX,
                z = vehZ,
                glooWalls = s.glooWalls,
                otherVehicles = s.vehicles,
                ignoreVehicleId = activeVehId
            )
            val updatedVehicles = s.vehicles
                .distinctBy { it.id }
                .map { v ->
                    if (v.id == activeVehId) v.copy(x = safeVehX, z = safeVehZ, yawDeg = vehYaw, occupantId = null) else v
                }
            val (safeExitX, safeExitZ) = IslandMapGenerator.findSafeVehicleExitPosition(
                vehX = safeVehX,
                vehZ = safeVehZ,
                vehYawDeg = vehYaw,
                glooWalls = s.glooWalls,
                vehicles = updatedVehicles
            )
            val exitY = IslandMapGenerator.getTerrainHeight(safeExitX, safeExitZ)
            val nearAfterExit = updatedVehicles.firstOrNull { hypot(it.x - safeExitX, it.z - safeExitZ) < 6.0f }
            _uiState.update {
                it.copy(
                    drivingVehicleId = null,
                    vehicleSpeedMps = 0f,
                    vehicleThrottleInput = 0f,
                    vehicleBrakeInput = false,
                    vehicleSteerInput = 0f,
                    playerX = safeExitX,
                    playerY = exitY,
                    playerZ = safeExitZ,
                    vehicles = updatedVehicles,
                    nearbyVehicle = nearAfterExit
                )
            }
            showStatusBanner("EXITED VEHICLE")
            return
        }

        val candidate = s.nearbyVehicle ?: s.vehicles.firstOrNull { v ->
            val enemyDriving = s.bots.any { !it.isFriendly && !it.isDead && it.drivingVehicleId == v.id }
            !enemyDriving && hypot(v.x - s.playerX, v.z - s.playerZ) < 6.0f
        } ?: return

        soundEngine.playUiClick()
        val (safeStartVehX, safeStartVehZ) = IslandMapGenerator.recoverVehicleIfStuck(
            x = candidate.x,
            z = candidate.z,
            glooWalls = s.glooWalls,
            otherVehicles = s.vehicles,
            ignoreVehicleId = candidate.id
        )
        val vehY = IslandMapGenerator.getTerrainHeight(safeStartVehX, safeStartVehZ)
        val updatedBots = s.bots.map { b ->
            if (b.drivingVehicleId == candidate.id) {
                val (allyExitX, allyExitZ) = IslandMapGenerator.findSafeVehicleExitPosition(
                    vehX = safeStartVehX,
                    vehZ = safeStartVehZ,
                    vehYawDeg = candidate.yawDeg,
                    glooWalls = s.glooWalls,
                    vehicles = s.vehicles
                )
                b.copy(
                    drivingVehicleId = null,
                    x = allyExitX,
                    y = IslandMapGenerator.getTerrainHeight(allyExitX, allyExitZ),
                    z = allyExitZ
                )
            } else b
        }
        val updatedVehicles = s.vehicles
            .distinctBy { it.id }
            .map { v ->
                if (v.id == candidate.id) v.copy(x = safeStartVehX, z = safeStartVehZ, occupantId = 0) else v
            }
        _uiState.update {
            it.copy(
                drivingVehicleId = candidate.id,
                vehicleSpeedMps = 0f,
                vehicleThrottleInput = 0f,
                vehicleBrakeInput = false,
                vehicleSteerInput = 0f,
                playerX = safeStartVehX,
                playerY = vehY,
                playerZ = safeStartVehZ,
                playerYawDeg = candidate.yawDeg,
                bots = updatedBots,
                vehicles = updatedVehicles,
                nearbyVehicle = null,
                isCrouching = false,
                isProne = false,
                isReloading = false,
                reloadRemainingSec = 0f,
                isScopedIn = false,
                isAiming = false,
                isManualFiringHeld = false,
                isJumping = false,
                isVaulting = false
            )
        }
        showStatusBanner("DRIVING ${candidate.name.uppercase()}")
    }

    /**
     * Revive knocked teammate (Section 25):
     * - Living player can revive a knocked friendly teammate within range.
     * - Restores low HP (50f) and returns teammate to active combat state.
     * - Never allows normal revive of a fully dead teammate.
     * - Prevents duplicate revives.
     */
    fun reviveNearbyKnockedTeammate() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSpectating) return
        val ally = s.nearbyKnockAllyToRevive ?: s.bots.firstOrNull {
            it.isFriendly && it.isKnocked && !it.isDead && hypot(it.x - s.playerX, it.z - s.playerZ) < 4.8f
        }
        if (ally == null) {
            val deadNearby = s.bots.firstOrNull {
                it.isFriendly && it.isDead && hypot(it.x - s.playerX, it.z - s.playerZ) < 4.8f
            }
            if (deadNearby != null) {
                showStatusBanner("TEAMMATE ELIMINATED - USE VENDING MACHINE TO RESPAWN")
            }
            return
        }
        if (!ally.isFriendly || ally.isDead || !ally.isKnocked) return

        var didRevive = false
        _uiState.update { state ->
            if (state.isPlayerKnocked || state.isPlayerDead) return@update state
            val targetNow = state.bots.firstOrNull { it.id == ally.id }
            if (targetNow == null || !targetNow.isFriendly || targetNow.isDead || !targetNow.isKnocked) {
                return@update state
            }
            didRevive = true
            val updatedBots = state.bots.map { b ->
                if (b.id == ally.id && b.isKnocked && !b.isDead) {
                    b.copy(
                        isKnocked = false,
                        isDead = false,
                        knockedTimerSec = 20f,
                        hp = 50f,
                        animState = CharacterAnimState.IDLE
                    )
                } else b
            }
            state.copy(
                bots = updatedBots,
                playerTeammatesRevived = state.playerTeammatesRevived + 1,
                nearbyKnockAllyToRevive = null,
                friendlyCallout = FriendlyVoiceCallout(ally.name, "Thanks for the revive! Let's move!")
            )
        }
        if (didRevive) {
            soundEngine.playPickup()
            showStatusBanner("REVIVED TEAMMATE ${ally.name}!")
        }
    }

    /**
     * Vending Machine Respawn (Section 26):
     * - Verifies player is alive and unknocked
     * - Verifies teammate is fully dead (knocked or living teammates cannot be respawned)
     * - Verifies sufficient Coins & respawn limit
     * - Finds a safe spawn point not inside enemies, walls, rocks, trees, or water
     * - Deducts Coins and resets teammate combat state atomically (prevents duplicate respawn)
     */
    fun useVendingMachineToRespawnTeammate() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.isSpectating) return
        val vm = s.nearbyVendingMachine ?: s.vendingMachines.firstOrNull {
            hypot(it.x - s.playerX, it.z - s.playerZ) < 6.5f
        } ?: return
        val deadAlly = s.deadTeammates.firstOrNull { it.isDead && it.isFriendly }
        if (deadAlly == null) {
            showStatusBanner("NO FULLY DEAD TEAMMATES TO RESPAWN")
            return
        }
        if (vm.remainingRespawns <= 0) {
            showStatusBanner("VENDING MACHINE RESPAWN LIMIT REACHED (0 LEFT)")
            return
        }
        if (s.matchCoins < vm.coinCost) {
            showStatusBanner("INSUFFICIENT COINS (${s.matchCoins}/${vm.coinCost} REQUIRED)")
            return
        }

        val (safeSpawnX, safeSpawnZ) = IslandMapGenerator.findSafeRespawnPosition(
            vmX = vm.x,
            vmZ = vm.z,
            bots = s.bots,
            glooWalls = s.glooWalls
        )
        var didRespawn = false
        _uiState.update { state ->
            if (state.isPlayerKnocked || state.isPlayerDead) return@update state
            val currentVm = state.vendingMachines.firstOrNull { it.id == vm.id } ?: return@update state
            val currentDeadAlly = state.bots.firstOrNull { it.id == deadAlly.id && it.isFriendly && it.isDead }
                ?: return@update state
            if (currentVm.remainingRespawns <= 0 || state.matchCoins < currentVm.coinCost) {
                return@update state
            }
            didRespawn = true
            val updatedVms = state.vendingMachines.map { node ->
                if (node.id == currentVm.id) node.copy(respawnsUsed = node.respawnsUsed + 1) else node
            }
            val updatedBots = state.bots.map { bot ->
                if (bot.id == currentDeadAlly.id && bot.isDead && bot.isFriendly) {
                    bot.copy(
                        isDead = false,
                        isKnocked = false,
                        knockedTimerSec = 20f,
                        hp = 100f,
                        armor = 50f,
                        x = safeSpawnX,
                        y = IslandMapGenerator.getTerrainHeight(safeSpawnX, safeSpawnZ),
                        z = safeSpawnZ,
                        lastX = safeSpawnX,
                        lastZ = safeSpawnZ,
                        stuckFrames = 0,
                        aiRole = BotAiRole.PATROLLING,
                        targetX = safeSpawnX + 12f,
                        targetZ = safeSpawnZ + 12f,
                        animState = CharacterAnimState.IDLE,
                        equippedWeapon = OriginalWeaponCatalog.VX47_STRIKER,
                        ammoInMag = 30,
                        medkits = 2
                    )
                } else bot
            }
            state.copy(
                matchCoins = (state.matchCoins - currentVm.coinCost).coerceAtLeast(0),
                vendingMachines = updatedVms,
                nearbyVendingMachine = updatedVms.firstOrNull { it.id == currentVm.id },
                bots = updatedBots,
                playerTeammatesRevived = state.playerTeammatesRevived + 1,
                friendlyCallout = FriendlyVoiceCallout(currentDeadAlly.name, "Respawned and back in action!")
            )
        }
        if (didRespawn) {
            soundEngine.playCoinPickup()
            showStatusBanner("RESPAWNED ${deadAlly.name} AT VENDING MACHINE (-${vm.coinCost} COINS)")
        }
    }

    fun cycleSpectatorTarget() {
        val s = _uiState.value
        if (!s.isPlayerDead || !s.isSpectating) return
        val validTargets = buildValidSpectatorTargets(s.bots, s.killerBotId)
        if (validTargets.isEmpty()) return
        soundEngine.playUiClick()
        _uiState.update { state ->
            val currentIdx = validTargets.indexOfFirst { it.botId == state.activeSpectatorTargetId }
                .let { if (it >= 0) it else state.activeSpectatorTargetIndex.coerceIn(0, validTargets.size - 1) }
            val nextIdx = (currentIdx + 1) % validTargets.size
            val nextTarget = validTargets[nextIdx]
            state.copy(
                spectatorTargets = validTargets,
                activeSpectatorTargetIndex = nextIdx,
                activeSpectatorTargetId = nextTarget.botId,
                playerX = nextTarget.x,
                playerY = nextTarget.y,
                playerZ = nextTarget.z,
                playerYawDeg = nextTarget.yawDeg
            )
        }
    }

    fun returnToLobby() {
        checkAndResetDailyMissionsIfNeeded()
        if (_uiState.value.screenState == AppScreenState.LOBBY) {
            return
        }
        loadingScreenJob?.cancel()
        gameLoopJob?.cancel()
        matchStartInProgress = false
        matchFinishedForCurrentGame = false
        moveInputX = 0f
        moveInputY = 0f
        soundEngine.playUiClick()
        _uiState.update {
            it.copy(
                screenState = AppScreenState.LOBBY,
                isStartingMatch = false,
                matchLoadingProgress = 0f,
                matchLoadingRemainingSec = 0f,
                isManualFiringHeld = false,
                isSprinting = false,
                isSpectating = false,
                spectatorTargets = emptyList(),
                activeSpectatorTargetIndex = 0,
                activeSpectatorTargetId = null,
                isInventoryOpen = false,
                drivingVehicleId = null,
                vehicleSpeedMps = 0f,
                vehicleThrottleInput = 0f,
                vehicleBrakeInput = false,
                vehicleSteerInput = 0f
            )
        }
    }

    private fun showStatusBanner(msg: String) {
        statusBannerClearTimerSec = 2.8f
        _uiState.update { it.copy(statusBannerText = msg) }
    }

    // =========================================================================
    // MAIN SIMULATION STEP (Movement, Auto-Pickup, Combat, Safe Zone, AI)
    // =========================================================================

    fun stepSimulationForTest(dt: Float) {
        stepSimulation(dt)
    }

    private fun stepSimulation(dt: Float) {
        val state = _uiState.value
        if (state.screenState != AppScreenState.IN_MATCH) return

        if (state.matchLoadingRemainingSec > 0f || state.isStartingMatch) {
            val remLoad = (state.matchLoadingRemainingSec - dt).coerceAtLeast(0f)
            if (remLoad <= 0f) {
                matchStartInProgress = false
                _uiState.update {
                    it.copy(
                        isStartingMatch = false,
                        matchLoadingRemainingSec = 0f,
                        matchLoadingProgress = 1f
                    )
                }
            } else {
                val prog = (1f - (remLoad / 0.50f)).coerceIn(0.25f, 0.98f)
                _uiState.update {
                    it.copy(
                        matchLoadingRemainingSec = remLoad,
                        matchLoadingProgress = prog
                    )
                }
            }
        }

        if (statusBannerClearTimerSec > 0f) {
            statusBannerClearTimerSec = (statusBannerClearTimerSec - dt).coerceAtLeast(0f)
            if (statusBannerClearTimerSec == 0f && state.statusBannerText.isNotEmpty()) {
                _uiState.update { it.copy(statusBannerText = "") }
            }
        }

        // Smooth recoil recovery when not actively shooting (uses weapon category recovery rate)
        if (!state.isManualFiringHeld && (accumulatedRecoilPitch > 0f || abs(accumulatedRecoilYaw) > 0.001f)) {
            val recoveryRate = state.activeWeapon?.spec?.category?.recoilRecoveryRateDegPerSec ?: 5.5f
            val recoverPitch = min(accumulatedRecoilPitch, dt * recoveryRate)
            accumulatedRecoilPitch = (accumulatedRecoilPitch - recoverPitch).coerceAtLeast(0f)

            val recoverYawMag = min(abs(accumulatedRecoilYaw), dt * (recoveryRate * 0.75f))
            val recoverYaw = if (accumulatedRecoilYaw >= 0f) recoverYawMag else -recoverYawMag
            accumulatedRecoilYaw -= recoverYaw

            _uiState.update {
                val rawYaw = (it.playerYawDeg - recoverYaw) % 360f
                val newYaw = if (rawYaw < 0f) rawYaw + 360f else rawYaw
                it.copy(
                    playerPitchDeg = (it.playerPitchDeg - recoverPitch).coerceIn(-38f, 38f),
                    playerYawDeg = newYaw
                )
            }
        }

        // Smooth third-person camera & scope FOV interpolation
        val curForCam = _uiState.value
        val activeScope = curForCam.activeWeapon?.equippedScope ?: ScopeType.RED_DOT
        val targetFov = when {
            curForCam.isScopedIn -> (1f / activeScope.fovMultiplier).coerceIn(1.2f, 3.6f)
            curForCam.isAiming -> 1.22f
            else -> 1.0f
        }
        val targetCamDist = when {
            curForCam.isScopedIn -> 0.25f
            curForCam.isAiming -> 2.4f
            else -> 4.2f
        }
        val targetShoulder = if (curForCam.isAiming && !curForCam.isScopedIn) 0.44f else 0f
        if (abs(curForCam.smoothFovScale - targetFov) > 0.005f ||
            abs(curForCam.smoothCamDistBehind - targetCamDist) > 0.005f ||
            abs(curForCam.smoothShoulderOffsetX - targetShoulder) > 0.005f
        ) {
            val lerpAlpha = (dt * 14f).coerceIn(0.15f, 1.0f)
            _uiState.update { st ->
                st.copy(
                    smoothFovScale = st.smoothFovScale + (targetFov - st.smoothFovScale) * lerpAlpha,
                    smoothCamDistBehind = st.smoothCamDistBehind + (targetCamDist - st.smoothCamDistBehind) * lerpAlpha,
                    smoothShoulderOffsetX = st.smoothShoulderOffsetX + (targetShoulder - st.smoothShoulderOffsetX) * lerpAlpha
                )
            }
        }

        stepAbilityTimer(dt)
        stepWeaponAndReload(dt)

        if (!state.isPlayerDead) {
            stepPlayerMovementAndPhysics(dt)
            stepAutomaticLootAndGunPickup()
        }

        stepSafeZone(dt)
        stepBotsAndIndicators(dt)
        stepMatchLifecycleAndSpectator(dt)
    }

    private fun stepAbilityTimer(dt: Float) {
        val s = _uiState.value
        if (s.abilityState == AbilityState.READY) return

        val remaining = s.abilityTimerSec - dt
        if (s.abilityState == AbilityState.ACTIVE) {
            if (remaining <= 0f) {
                // Transition from ACTIVE -> COOLDOWN:
                // - For SANI: restores normal speed and normal shooting
                // - For RIMA: removes all temporary RIMA markers immediately
                val cooldown = s.matchCharacter.cooldownSec
                _uiState.update {
                    it.copy(
                        abilityState = AbilityState.COOLDOWN,
                        abilityTimerSec = cooldown,
                        rimaRevealActive = false
                    )
                }
                showStatusBanner("${s.matchCharacter.abilityName.uppercase()} ENDED - COOLDOWN ${cooldown.toInt()}s")
            } else {
                _uiState.update { it.copy(abilityTimerSec = remaining) }
            }
        } else if (s.abilityState == AbilityState.COOLDOWN) {
            if (remaining <= 0f) {
                _uiState.update {
                    it.copy(
                        abilityState = AbilityState.READY,
                        abilityTimerSec = 0f,
                        rimaRevealActive = false
                    )
                }
                showStatusBanner("${s.matchCharacter.abilityName.uppercase()} READY!")
            } else {
                _uiState.update { it.copy(abilityTimerSec = remaining) }
            }
        }
    }

    private fun stepWeaponAndReload(dt: Float) {
        if (fireCooldownRemainingSec > 0f) {
            fireCooldownRemainingSec = (fireCooldownRemainingSec - dt).coerceAtLeast(0f)
        }

        val s = _uiState.value
        if (s.hitMarkerTimerSec > 0f || s.hitEffects.isNotEmpty()) {
            val nextHitTimer = (s.hitMarkerTimerSec - dt).coerceAtLeast(0f)
            val nextEffects = if (s.hitEffects.isEmpty()) {
                emptyList()
            } else {
                s.hitEffects.mapNotNull { ef ->
                    val rem = ef.remainingSec - dt
                    if (rem > 0f) ef.copy(remainingSec = rem) else null
                }
            }
            _uiState.update {
                it.copy(
                    hitMarkerTimerSec = nextHitTimer,
                    hitEffects = nextEffects
                )
            }
        }

        val sAfterHit = _uiState.value
        if (sAfterHit.isReloading) {
            val rem = sAfterHit.reloadRemainingSec - dt
            if (rem <= 0f) {
                val wep = sAfterHit.activeWeapon
                if (wep != null) {
                    val ammoType = wep.spec.category.ammoType
                    val reserve = (sAfterHit.ammoReserve[ammoType] ?: 0).coerceAtLeast(0)
                    val needed = (wep.maxMagazineSize - wep.currentAmmo.coerceAtLeast(0)).coerceAtLeast(0)
                    val loaded = min(needed, reserve).coerceAtLeast(0)
                    val updatedWep = wep.copy(
                        currentAmmo = (wep.currentAmmo.coerceAtLeast(0) + loaded).coerceIn(0, wep.maxMagazineSize)
                    ).sanitized()
                    val updatedSlots = sAfterHit.weaponSlots.toMutableMap().apply {
                        put(sAfterHit.activeWeaponSlot, updatedWep)
                    }
                    val updatedReserve = sAfterHit.ammoReserve.toMutableMap().apply {
                        put(ammoType, (reserve - loaded).coerceIn(0, 999))
                    }
                    _uiState.update { state ->
                        val nextAnim = if (state.playerAnimState == CharacterAnimState.RELOAD) {
                            CharacterAnimState.IDLE
                        } else {
                            state.playerAnimState
                        }
                        state.copy(
                            weaponSlots = updatedSlots,
                            ammoReserve = updatedReserve,
                            isReloading = false,
                            reloadRemainingSec = 0f,
                            playerAnimState = nextAnim
                        )
                    }
                } else {
                    _uiState.update { it.copy(isReloading = false, reloadRemainingSec = 0f) }
                }
            } else {
                _uiState.update { it.copy(reloadRemainingSec = rem) }
            }
            return
        }

        // Automatic weapon continuous firing ONLY while player holds the Fire button
        val current = _uiState.value
        val activeWep = current.activeWeapon
        if (current.isManualFiringHeld &&
            activeWep != null &&
            activeWep.spec.category.isAutomatic &&
            fireCooldownRemainingSec <= 0f
        ) {
            executeSinglePlayerShot()
        }
    }

    /**
     * Executes a single validated player shot with:
     * - Full fire validation (`canPlayerFireNow`)
     * - Soft Aim Assist (no auto-fire, no lock-on)
     * - Solid wall/rock/Gloo ray stopping (`computeRayObstacleDistance`) & Line-of-Sight check
     * - Vertical pitch & horizontal cone hit validation (no fake hit detection)
     * - Hit zone classification (HEAD, BODY, LIMB, MISS) & range falloff
     * - Hit effects (`HitImpactEffect`) and damage feedback (`lastDamageDealtAmount`, `hitMarkerTimerSec`)
     * - Predictable balanced vertical + horizontal recoil
     */
    private fun executeSinglePlayerShot() {
        val s = _uiState.value
        val curWep = s.activeWeapon
        if (!canPlayerFireNow(s)) {
            if (curWep != null && curWep.currentAmmo <= 0 && !s.isReloading) {
                _uiState.update { it.copy(isManualFiringHeld = false) }
                triggerManualReload()
            }
            return
        }

        val wep = curWep ?: return
        fireCooldownRemainingSec = wep.spec.fireIntervalSec.coerceAtLeast(0.05f)
        soundEngine.playGunshot(
            isShotgun = wep.spec.category == WeaponCategory.SHOTGUN,
            isSniper = wep.spec.category == WeaponCategory.SNIPER
        )

        var effectiveYaw = s.playerYawDeg
        var subtleAimAssistCameraYaw = s.playerYawDeg
        if (s.aimAssistEnabled) {
            val candidate = findBestAimAssistTarget(s)
            if (candidate != null) {
                val targetAngle = ((Math.toDegrees(atan2((candidate.x - s.playerX).toDouble(), (candidate.z - s.playerZ).toDouble())).toFloat()) + 360f) % 360f
                val diff = normalizeAngleDiff(targetAngle - effectiveYaw)
                if (abs(diff) < 6.5f) {
                    effectiveYaw = (effectiveYaw + diff * 0.26f + 360f) % 360f
                    // Subtle camera tracking toward visible enemy while firing
                    subtleAimAssistCameraYaw = (s.playerYawDeg + (diff * 0.16f).coerceIn(-0.65f, 0.65f) + 360f) % 360f
                }
            }
        }

        val rad = Math.toRadians(effectiveYaw.toDouble())
        val dirX = sin(rad).toFloat()
        val dirZ = cos(rad).toFloat()
        val maxRange = wep.spec.effectiveRange
        val wallStopDist = IslandMapGenerator.computeRayObstacleDistance(
            startX = s.playerX,
            startZ = s.playerZ,
            dirX = dirX,
            dirZ = dirZ,
            maxRange = maxRange,
            glooWalls = s.glooWalls
        )

        var hitBot: CombatantBot? = null
        var hitDist = wallStopDist
        var hitPerpOffset = 0f
        var hitPitchDiff = 0f
        for (bot in s.bots) {
            if (bot.isDead || bot.isFriendly) continue
            val dx = bot.x - s.playerX
            val dz = bot.z - s.playerZ
            val dist = hypot(dx, dz)
            if (dist > maxRange || dist < 0.5f) continue

            val proj = dx * dirX + dz * dirZ
            if (proj <= 0f || proj >= hitDist) continue
            val perpDist = hypot(dx - dirX * proj, dz - dirZ * proj)
            val hitRadius = when {
                wep.spec.category == WeaponCategory.SHOTGUN -> 1.65f
                s.isScopedIn || s.isAiming -> 1.50f
                else -> 1.20f
            }
            if (perpDist <= hitRadius) {
                // Verify vertical pitch alignment so aiming far into the sky or ground cannot cause fake hits
                val expectedPitch = Math.toDegrees(
                    atan2(((bot.y + 1.0f) - (s.playerY + 1.35f)).toDouble(), dist.coerceAtLeast(1f).toDouble())
                ).toFloat()
                val pitchDiff = s.playerPitchDeg - expectedPitch
                if (abs(pitchDiff) <= 22f) {
                    // Verify line of sight (cannot shoot through walls, rocks, or Gloo Walls!)
                    if (IslandMapGenerator.hasLineOfSight(s.playerX, s.playerZ, bot.x, bot.z, s.glooWalls)) {
                        hitDist = proj
                        hitBot = bot
                        hitPerpOffset = perpDist
                        hitPitchDiff = pitchDiff
                    }
                }
            }
        }

        // Determine HitZone (HEAD, BODY, LIMB, or MISS)
        val detectedHitZone = if (hitBot == null) {
            HitZone.MISS
        } else if (hitPitchDiff in 2.2f..12.5f && hitPerpOffset < 0.65f) {
            HitZone.HEAD
        } else if (hitPerpOffset < 0.92f && hitPitchDiff in -8.5f..8.0f) {
            HitZone.BODY
        } else {
            HitZone.LIMB
        }

        val endX = s.playerX + dirX * hitDist
        val endZ = s.playerZ + dirZ * hitDist
        val trace = BulletTrace(
            id = nextDynamicEntityId++,
            startX = s.playerX,
            startY = s.playerY + 1.35f,
            startZ = s.playerZ,
            endX = endX,
            endY = s.playerY + 1.35f,
            endZ = endZ,
            isFriendly = true
        )

        val newAmmo = (wep.currentAmmo - 1).coerceIn(0, wep.maxMagazineSize)
        val updatedWep = wep.copy(currentAmmo = newAmmo).sanitized()
        val updatedSlots = s.weaponSlots.toMutableMap().apply {
            put(s.activeWeaponSlot, updatedWep)
        }

        var killsDelta = 0
        var headshotsDelta = 0
        var damageDelta = 0f
        var damageAppliedForThisShot = false
        var newHitEffect: HitImpactEffect? = null

        val updatedBots = if (hitBot != null && detectedHitZone != HitZone.MISS) {
            soundEngine.playHit()
            val distanceRatio = (hitDist / maxRange.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val rangeFalloff = if (distanceRatio > 0.75f) {
                1f - ((distanceRatio - 0.75f) / 0.25f) * 0.22f
            } else {
                1f
            }
            val rawDamage = wep.spec.baseDamage * wep.spec.pellets * detectedHitZone.damageMultiplier * rangeFalloff
            val mitigation = if (detectedHitZone == HitZone.HEAD) {
                1f - (hitBot.helmetTier * 0.12f)
            } else {
                1f - (hitBot.armorTier * 0.10f)
            }
            val finalDamage = (rawDamage * mitigation).coerceAtLeast(7f)

            newHitEffect = HitImpactEffect(
                id = nextDynamicEntityId++,
                x = hitBot.x,
                y = hitBot.y + if (detectedHitZone == HitZone.HEAD) 1.65f else 1.15f,
                z = hitBot.z,
                damage = finalDamage.roundToInt(),
                hitZone = detectedHitZone,
                remainingSec = 0.55f
            )

            s.bots.map { b ->
                // Guarantee damage is calculated and applied at most once per valid shot
                if (b.id != hitBot.id || damageAppliedForThisShot) b else {
                    damageAppliedForThisShot = true
                    damageDelta = finalDamage
                    val armorAbsorbed = if (detectedHitZone != HitZone.HEAD && b.armor > 0f) {
                        min(b.armor, finalDamage * 0.25f)
                    } else 0f
                    val remainingArmor = (b.armor - armorAbsorbed).coerceAtLeast(0f)
                    val remainingHp = b.hp - finalDamage
                    if (remainingHp <= 0f) {
                        val hasLivingSquadmate = s.selectedTeamMode != TeamMode.SOLO &&
                            s.bots.any { other -> other.id != b.id && other.squadId == b.squadId && !other.isDead && !other.isKnocked }
                        if (!b.isKnocked && hasLivingSquadmate) {
                            soundEngine.playKnockOrDeath()
                            showStatusBanner("KNOCKED ${b.name.uppercase()} (${detectedHitZone.label.uppercase()} -${finalDamage.roundToInt()})!")
                            b.copy(
                                hp = 45f,
                                armor = remainingArmor,
                                isKnocked = true,
                                knockedTimerSec = 22f,
                                animState = CharacterAnimState.KNOCK
                            )
                        } else {
                            killsDelta = 1
                            if (detectedHitZone == HitZone.HEAD) headshotsDelta = 1
                            soundEngine.playKnockOrDeath()
                            showStatusBanner("ELIMINATED ${b.name.uppercase()} (${detectedHitZone.label.uppercase()} -${finalDamage.roundToInt()})!")
                            b.copy(
                                hp = 0f,
                                armor = 0f,
                                isKnocked = false,
                                isDead = true,
                                animState = CharacterAnimState.DEATH
                            )
                        }
                    } else {
                        b.copy(
                            hp = remainingHp,
                            armor = remainingArmor,
                            detectedMarkerRemainingSec = 4.0f,
                            lastDetectedByPlayerTimeMs = System.currentTimeMillis()
                        )
                    }
                }
            }
        } else {
            s.bots
        }

        // Realistic, mobile-friendly per-weapon-type recoil pattern (bounded, controllable, never excessive)
        val stanceMultiplier = when {
            s.isProne -> 0.68f
            s.isCrouching -> 0.82f
            else -> 1.0f
        }
        val aimMultiplier = when {
            s.isScopedIn -> 0.82f
            s.isAiming -> 0.88f
            else -> 1.0f
        }
        val categoryVertFactor = when (wep.spec.category) {
            WeaponCategory.ASSAULT_RIFLE -> 0.28f
            WeaponCategory.SMG -> 0.22f
            WeaponCategory.SHOTGUN -> 0.30f
            WeaponCategory.SNIPER, WeaponCategory.DMR -> 0.32f
            WeaponCategory.SECONDARY -> 0.20f
        }
        val categoryHorizFactor = when (wep.spec.category) {
            WeaponCategory.ASSAULT_RIFLE -> 0.16f
            WeaponCategory.SMG -> 0.18f
            WeaponCategory.SHOTGUN -> 0.10f
            WeaponCategory.SNIPER, WeaponCategory.DMR -> 0.08f
            WeaponCategory.SECONDARY -> 0.08f
        }
        val maxClimb = wep.spec.category.maxRecoilClimbDeg
        val rawPitchKick = wep.effectiveRecoil * categoryVertFactor * stanceMultiplier * aimMultiplier
        val remainingClimbRoom = (maxClimb - accumulatedRecoilPitch).coerceAtLeast(0f)
        val appliedPitchKick = min(rawPitchKick, remainingClimbRoom)
        accumulatedRecoilPitch = (accumulatedRecoilPitch + appliedPitchKick).coerceIn(0f, maxClimb)

        shotParitySign = -shotParitySign
        val rawYawKick = wep.effectiveHorizontalRecoil * categoryHorizFactor * stanceMultiplier * aimMultiplier * shotParitySign
        val nextAccumYaw = (accumulatedRecoilYaw + rawYawKick).coerceIn(-1.4f, 1.4f)
        val appliedYawKick = nextAccumYaw - accumulatedRecoilYaw
        accumulatedRecoilYaw = nextAccumYaw

        val recoilPitch = (s.playerPitchDeg + appliedPitchKick).coerceIn(-38f, 38f)
        val recoilYaw = (subtleAimAssistCameraYaw + appliedYawKick + 360f) % 360f

        _uiState.update { state ->
            val updatedEffects = if (newHitEffect != null) {
                (state.hitEffects + newHitEffect).takeLast(6)
            } else {
                state.hitEffects
            }
            state.copy(
                weaponSlots = updatedSlots,
                bots = updatedBots,
                bulletTraces = (state.bulletTraces + trace).takeLast(14),
                hitEffects = updatedEffects,
                playerKills = state.playerKills + killsDelta,
                playerHeadshots = state.playerHeadshots + headshotsDelta,
                playerDamageDealt = state.playerDamageDealt + damageDelta,
                lastHitZone = detectedHitZone,
                lastDamageDealtAmount = if (damageDelta > 0f) damageDelta.roundToInt() else state.lastDamageDealtAmount,
                lastDamageTargetName = hitBot?.name ?: state.lastDamageTargetName,
                hitMarkerTimerSec = if (damageDelta > 0f) 0.38f else state.hitMarkerTimerSec,
                playerYawDeg = recoilYaw,
                playerPitchDeg = recoilPitch,
                playerAnimState = CharacterAnimState.SHOOT
            )
        }

        if (newAmmo == 0) {
            _uiState.update { it.copy(isManualFiringHeld = false) }
            val reserveAvailable = (_uiState.value.ammoReserve[wep.spec.category.ammoType] ?: 0) > 0
            if (reserveAvailable) {
                triggerManualReload()
            }
        }
    }

    private fun findBestAimAssistTarget(s: MatchUiState): CombatantBot? {
        var bestBot: CombatantBot? = null
        var bestAngleAbs = 6.5f
        for (b in s.bots) {
            if (b.isDead || b.isFriendly) continue
            val dist = hypot(b.x - s.playerX, b.z - s.playerZ)
            if (dist > 125f || dist < 1.0f) continue
            val targetAngle = ((Math.toDegrees(atan2((b.x - s.playerX).toDouble(), (b.z - s.playerZ).toDouble())).toFloat()) + 360f) % 360f
            val diff = abs(normalizeAngleDiff(targetAngle - s.playerYawDeg))
            if (diff < bestAngleAbs) {
                val expectedPitch = Math.toDegrees(
                    atan2(((b.y + 1.0f) - (s.playerY + 1.35f)).toDouble(), dist.coerceAtLeast(1f).toDouble())
                ).toFloat()
                if (abs(s.playerPitchDeg - expectedPitch) <= 18f &&
                    IslandMapGenerator.hasLineOfSight(s.playerX, s.playerZ, b.x, b.z, s.glooWalls)
                ) {
                    bestAngleAbs = diff
                    bestBot = b
                }
            }
        }
        return bestBot
    }

    private fun stepPlayerMovementAndPhysics(dt: Float) {
        val s = _uiState.value

        var isJumpingNow = s.isJumping
        var isVaultingNow = s.isVaulting
        if (jumpRemainingSec > 0f) {
            jumpRemainingSec = (jumpRemainingSec - dt).coerceAtLeast(0f)
            if (jumpRemainingSec == 0f) isJumpingNow = false
        }
        if (vaultRemainingSec > 0f) {
            vaultRemainingSec = (vaultRemainingSec - dt).coerceAtLeast(0f)
            if (vaultRemainingSec == 0f) isVaultingNow = false
        }

        val inputMag = hypot(moveInputX, moveInputY).coerceIn(0f, 1f)
        val activeDrivingVehicle = s.drivingVehicleId?.let { vid -> s.vehicles.firstOrNull { it.id == vid } }
        var nextYawDeg = s.playerYawDeg
        var nextVehicleSpeed = 0f

        val (safeX, safeZ) = if (activeDrivingVehicle != null && !s.isPlayerKnocked && !s.isPlayerDead) {
            val effThrottle = if (abs(s.vehicleThrottleInput) > 0.05f) s.vehicleThrottleInput else moveInputY
            val effSteer = if (abs(s.vehicleSteerInput) > 0.05f) s.vehicleSteerInput else moveInputX
            val isBraking = s.vehicleBrakeInput

            val maxFwd = activeDrivingVehicle.maxSpeed.coerceIn(18f, 32f)
            val maxRev = -9.0f
            val accelRate = 20.0f
            val brakeRate = 30.0f
            val frictionRate = 8.5f

            nextVehicleSpeed = when {
                isBraking -> {
                    if (s.vehicleSpeedMps > 0.1f) {
                        max(0f, s.vehicleSpeedMps - brakeRate * dt)
                    } else if (effThrottle < -0.1f) {
                        max(maxRev, s.vehicleSpeedMps - (accelRate * 0.65f) * dt)
                    } else {
                        0f
                    }
                }
                effThrottle > 0.08f -> {
                    val targetSpeed = maxFwd * effThrottle.coerceIn(0f, 1f)
                    val rate = if (s.vehicleSpeedMps < 0f) brakeRate else accelRate
                    val stepped = min(targetSpeed, s.vehicleSpeedMps + rate * dt)
                    if (stepped in 0.01f..4.2f && s.vehicleSpeedMps >= 0f) {
                        min(targetSpeed, max(stepped, 4.2f * effThrottle.coerceIn(0f, 1f)))
                    } else {
                        stepped
                    }
                }
                effThrottle < -0.08f -> {
                    if (s.vehicleSpeedMps > 0.4f) {
                        max(0f, s.vehicleSpeedMps - brakeRate * dt)
                    } else {
                        val targetRev = maxRev * abs(effThrottle).coerceIn(0f, 1f)
                        val steppedRev = max(targetRev, s.vehicleSpeedMps - (accelRate * 0.7f) * dt)
                        if (steppedRev in -2.6f..-0.01f && s.vehicleSpeedMps <= 0f) {
                            max(targetRev, min(steppedRev, -2.6f * abs(effThrottle).coerceIn(0f, 1f)))
                        } else {
                            steppedRev
                        }
                    }
                }
                else -> {
                    when {
                        s.vehicleSpeedMps > 0f -> max(0f, s.vehicleSpeedMps - frictionRate * dt)
                        s.vehicleSpeedMps < 0f -> min(0f, s.vehicleSpeedMps + frictionRate * dt)
                        else -> 0f
                    }
                }
            }

            if (abs(effSteer) > 0.05f && (abs(nextVehicleSpeed) > 0.05f || abs(effThrottle) > 0.05f)) {
                val turnSpeedFactor = (abs(nextVehicleSpeed) / 10f).coerceIn(0.55f, 1.15f)
                val reverseSign = if (nextVehicleSpeed < -0.25f) -1f else 1f
                val deltaYaw = effSteer * 96f * turnSpeedFactor * reverseSign * dt
                nextYawDeg = ((s.playerYawDeg + deltaYaw) % 360f + 360f) % 360f
            }

            val yawRad = Math.toRadians(nextYawDeg.toDouble())
            val forwardX = sin(yawRad).toFloat()
            val forwardZ = cos(yawRad).toFloat()
            val desiredX = s.playerX + forwardX * nextVehicleSpeed * dt
            val desiredZ = s.playerZ + forwardZ * nextVehicleSpeed * dt

            val (resX, resZ, hitObstacle) = IslandMapGenerator.resolveVehicleMovement(
                oldX = s.playerX,
                oldZ = s.playerZ,
                desiredX = desiredX,
                desiredZ = desiredZ,
                glooWalls = s.glooWalls,
                otherVehicles = s.vehicles,
                ignoreVehicleId = activeDrivingVehicle.id
            )
            val actualMoved = hypot(resX - s.playerX, resZ - s.playerZ)
            val expectedMove = abs(nextVehicleSpeed * dt)
            if (hitObstacle && expectedMove > 0.05f && actualMoved < expectedMove * 0.35f) {
                nextVehicleSpeed *= 0.2f
                if (abs(nextVehicleSpeed) < 1.2f) nextVehicleSpeed = 0f
            }

            if (abs(effThrottle) > 0.3f && actualMoved < 0.005f) {
                playerStuckFrames++
            } else {
                playerStuckFrames = 0
            }

            if (IslandMapGenerator.isVehiclePositionBlocked(resX, resZ, s.glooWalls, s.vehicles, activeDrivingVehicle.id) ||
                (playerStuckFrames >= 10 && IslandMapGenerator.isVehiclePositionBlocked(resX, resZ, s.glooWalls, s.vehicles, activeDrivingVehicle.id, radius = 1.85f))
            ) {
                playerStuckFrames = 0
                IslandMapGenerator.recoverVehicleIfStuck(resX, resZ, s.glooWalls, s.vehicles, activeDrivingVehicle.id)
            } else {
                resX.coerceIn(-IslandMapGenerator.VEHICLE_BOUNDARY_LIMIT, IslandMapGenerator.VEHICLE_BOUNDARY_LIMIT) to
                    resZ.coerceIn(-IslandMapGenerator.VEHICLE_BOUNDARY_LIMIT, IslandMapGenerator.VEHICLE_BOUNDARY_LIMIT)
            }
        } else {
            val baseMoveSpeed = when {
                s.isPlayerKnocked -> 1.8f
                s.isSwimming -> 5.2f
                s.isProne -> 2.4f
                s.isCrouching -> 4.2f
                s.isAiming || s.isScopedIn -> 4.6f
                s.isSprinting -> 9.4f
                else -> 6.8f
            }
            val speedMultiplier = if (s.matchCharacter == CharacterId.SANI && s.abilityState == AbilityState.ACTIVE && !s.isPlayerKnocked) {
                1.45f
            } else 1.0f
            val moveSpeed = baseMoveSpeed * speedMultiplier

            val yawRad = Math.toRadians(s.playerYawDeg.toDouble())
            val forwardX = sin(yawRad).toFloat()
            val forwardZ = cos(yawRad).toFloat()
            val rightX = cos(yawRad).toFloat()
            val rightZ = -sin(yawRad).toFloat()

            val worldVelX = (forwardX * moveInputY + rightX * moveInputX) * moveSpeed
            val worldVelZ = (forwardZ * moveInputY + rightZ * moveInputX) * moveSpeed

            val desiredX = s.playerX + worldVelX * dt
            val desiredZ = s.playerZ + worldVelZ * dt

            val (resolvedX, resolvedZ) = IslandMapGenerator.resolveMovement(
                oldX = s.playerX,
                oldZ = s.playerZ,
                desiredX = desiredX,
                desiredZ = desiredZ,
                isVaulting = isVaultingNow,
                glooWalls = s.glooWalls
            )

            // Safe Anti-Stuck System (Section 4):
            val isBlockedNow = IslandMapGenerator.isPositionBlocked(resolvedX, resolvedZ, isVaultingNow, s.glooWalls, 0.72f)
            val actualMovedDist = hypot(resolvedX - s.playerX, resolvedZ - s.playerZ)
            if (inputMag > 0.4f && actualMovedDist < 0.005f) {
                playerStuckFrames++
            } else {
                playerStuckFrames = 0
            }
            if (isBlockedNow) {
                playerStuckFrames = 0
                IslandMapGenerator.recoverIfStuck(resolvedX, resolvedZ, s.glooWalls)
            } else if (playerStuckFrames >= 10 && IslandMapGenerator.isPositionBlocked(resolvedX, resolvedZ, isVaultingNow, s.glooWalls, 0.92f)) {
                playerStuckFrames = 0
                IslandMapGenerator.recoverIfStuck(resolvedX, resolvedZ, s.glooWalls)
            } else {
                resolvedX.coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT, IslandMapGenerator.PLAYABLE_LIMIT) to
                    resolvedZ.coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT, IslandMapGenerator.PLAYABLE_LIMIT)
            }
        }

        // Automatic Water -> Swim and Land -> Normal transitions (Section 2)
        val inWater = IslandMapGenerator.isPointInWater(safeX, safeZ) && s.drivingVehicleId == null
        val groundY = IslandMapGenerator.getTerrainHeight(safeX, safeZ)
        val jumpOffset = if (isJumpingNow) sin((jumpRemainingSec / 0.56f) * PI).toFloat() * 1.65f else if (isVaultingNow) 1.25f else 0f
        val newY = max(groundY, groundY + jumpOffset)

        // Fall damage calculation
        var fallDamage = 0f
        val currentlyElevated = jumpOffset > 0.1f || (s.playerY - groundY) > 1.8f
        if (currentlyElevated) {
            if (!wasAirborne) {
                wasAirborne = true
                peakAirborneY = s.playerY
            } else {
                peakAirborneY = max(peakAirborneY, s.playerY)
            }
        } else if (wasAirborne) {
            val dropHeight = peakAirborneY - groundY
            wasAirborne = false
            peakAirborneY = groundY
            when {
                dropHeight < 3.4f -> fallDamage = 0f
                dropHeight < 6.0f -> fallDamage = 22f
                dropHeight < 9.2f -> fallDamage = 58f
                else -> fallDamage = 105f
            }
            if (fallDamage > 0f) {
                showStatusBanner("FALL DAMAGE (-${fallDamage.toInt()} HP)")
            }
        }

        if (inputMag > 0.15f && !inWater && !isJumpingNow && s.drivingVehicleId == null) {
            val footMode = when {
                s.isProne || s.isPlayerKnocked -> 0
                s.isCrouching -> 1
                s.isSprinting -> 3
                else -> 2
            }
            soundEngine.playFootstep(footMode)
        }

        // Cancel invalid states when entering water
        val nextCrouch = if (inWater) false else s.isCrouching
        val nextProne = if (inWater) false else s.isProne
        val nextAim = if (inWater) false else s.isAiming
        val nextScope = if (inWater) false else s.isScopedIn
        val nextFiring = if (inWater) false else s.isManualFiringHeld
        val nextReloading = if (inWater) false else s.isReloading

        val nextAnimState = when {
            s.isPlayerDead -> CharacterAnimState.DEATH
            s.isBeingRevived -> CharacterAnimState.REVIVE
            s.isPlayerKnocked -> CharacterAnimState.KNOCK
            s.matchCharacter == CharacterId.SANI && s.abilityState == AbilityState.ACTIVE && inputMag > 0.1f -> CharacterAnimState.ABILITY
            nextReloading -> CharacterAnimState.RELOAD
            isVaultingNow -> CharacterAnimState.VAULT
            isJumpingNow -> CharacterAnimState.JUMP
            inWater -> CharacterAnimState.SWIM
            nextFiring -> CharacterAnimState.SHOOT
            nextAim || nextScope -> CharacterAnimState.AIM
            nextProne -> CharacterAnimState.PRONE
            nextCrouch -> CharacterAnimState.CROUCH
            inputMag > 0.65f && s.isSprinting -> CharacterAnimState.SPRINT
            inputMag > 0.45f -> CharacterAnimState.RUN
            inputMag > 0.10f -> CharacterAnimState.WALK
            else -> CharacterAnimState.IDLE
        }

        val updatedVehicles = if (activeDrivingVehicle != null) {
            s.vehicles
                .distinctBy { it.id }
                .map { v ->
                    if (v.id == activeDrivingVehicle.id) {
                        v.copy(x = safeX, z = safeZ, yawDeg = nextYawDeg, occupantId = 0)
                    } else v
                }
        } else {
            s.vehicles.distinctBy { it.id }
        }

        val nearVm = if (!s.isPlayerKnocked && !s.isPlayerDead) {
            s.vendingMachines.firstOrNull { hypot(it.x - safeX, it.z - safeZ) < 6.5f }
        } else null
        val nearVeh = if (activeDrivingVehicle == null && !s.isPlayerKnocked && !s.isPlayerDead) {
            updatedVehicles.firstOrNull { v ->
                val enemyDriving = s.bots.any { !it.isFriendly && !it.isDead && it.drivingVehicleId == v.id }
                !enemyDriving && hypot(v.x - safeX, v.z - safeZ) < 6.0f
            }
        } else null
        val nearKnockedAlly = if (!s.isPlayerKnocked && !s.isPlayerDead) {
            s.bots.firstOrNull {
                it.isFriendly && it.isKnocked && !it.isDead && hypot(it.x - safeX, it.z - safeZ) < 4.8f
            }
        } else null

        _uiState.update { state ->
            state.copy(
                playerX = safeX,
                playerY = newY,
                playerZ = safeZ,
                playerYawDeg = nextYawDeg,
                drivingVehicleId = activeDrivingVehicle?.id,
                vehicleSpeedMps = if (activeDrivingVehicle != null) nextVehicleSpeed else 0f,
                isSwimming = inWater,
                isCrouching = nextCrouch,
                isProne = nextProne,
                isAiming = nextAim,
                isScopedIn = nextScope,
                isManualFiringHeld = nextFiring,
                isReloading = nextReloading,
                isJumping = isJumpingNow,
                isVaulting = isVaultingNow,
                playerAnimState = nextAnimState,
                playerAnimPhase = (state.playerAnimPhase + dt * (if (activeDrivingVehicle != null) abs(nextVehicleSpeed) * 0.45f else if (state.isSprinting) 9f else 5.5f)) % (2f * PI.toFloat()),
                vehicles = updatedVehicles,
                nearbyVendingMachine = nearVm,
                nearbyVehicle = nearVeh,
                nearbyKnockAllyToRevive = nearKnockedAlly
            )
        }

        if (fallDamage > 0f) {
            applyDamageToPlayer(fallDamage, attackerBotId = null)
        }
    }

    /**
     * Test helper to position the player at specific world coordinates for deterministic Robolectric testing.
     */
    fun setPlayerPositionForTest(x: Float, z: Float) {
        val y = IslandMapGenerator.getTerrainHeight(x, z)
        _uiState.update { state ->
            val updatedVehicles = if (state.drivingVehicleId != null) {
                state.vehicles.map { v ->
                    if (v.id == state.drivingVehicleId) v.copy(x = x, z = z, occupantId = 0) else v
                }
            } else state.vehicles
            val nearVeh = if (state.drivingVehicleId == null && !state.isPlayerKnocked && !state.isPlayerDead) {
                updatedVehicles.firstOrNull { hypot(it.x - x, it.z - z) < 6.0f }
            } else null
            val nearVm = if (!state.isPlayerKnocked && !state.isPlayerDead) {
                state.vendingMachines.firstOrNull { hypot(it.x - x, it.z - z) < 6.5f }
            } else null
            state.copy(
                playerX = x,
                playerY = y,
                playerZ = z,
                vehicles = updatedVehicles,
                nearbyVehicle = nearVeh,
                nearbyVendingMachine = nearVm
            )
        }
    }

    /**
     * Test helper to update a specific bot's state for deterministic Enemy & Friendly AI testing.
     */
    fun updateBotForTest(botId: Int, transform: (CombatantBot) -> CombatantBot) {
        _uiState.update { state ->
            state.copy(
                bots = state.bots.map { b -> if (b.id == botId) transform(b) else b }
            )
        }
    }

    /**
     * Test helper to set player knocked state for deterministic Friendly AI revive testing.
     */
    fun setPlayerKnockedForTest(knocked: Boolean, hp: Float = if (knocked) 40f else 100f) {
        _uiState.update { state ->
            state.copy(
                isPlayerKnocked = knocked,
                isPlayerDead = false,
                playerHp = hp,
                playerKnockedTimerSec = 20f,
                reviveProgressSec = 0f,
                isBeingRevived = false
            )
        }
    }

    /**
     * Test helper to apply combat or zone damage to the player for deterministic knock/revive/death testing.
     */
    fun applyDamageToPlayerForTest(damage: Float, attackerBotId: Int? = null, bypassArmor: Boolean = true) {
        applyDamageToPlayer(damage = damage, attackerBotId = attackerBotId, bypassArmor = bypassArmor)
    }

    /**
     * Test helper to configure SafeZoneState for deterministic Safe Zone testing.
     */
    fun setSafeZoneForTest(safeZone: SafeZoneState) {
        _uiState.update { state ->
            state.copy(safeZone = safeZone)
        }
    }

    /**
     * Test helper to configure match kills, survival time, and loot count for deterministic XP & Daily Mission testing.
     */
    fun setMatchPerformanceForTest(kills: Int, survivalTimeSec: Float, lootCollected: Int = 0) {
        _uiState.update { state ->
            state.copy(
                playerKills = kills.coerceAtLeast(0),
                matchElapsedSec = survivalTimeSec.coerceAtLeast(0f),
                playerLootCollectedCount = lootCollected.coerceAtLeast(0)
            )
        }
    }

    /**
     * Test helper to equip a specific weapon spec and scope into a target slot for deterministic testing.
     */
    fun equipWeaponForTest(
        slot: WeaponSlotIndex,
        spec: WeaponSpec,
        scope: ScopeType = if (spec.category == WeaponCategory.SNIPER) ScopeType.SNIPER_8X else ScopeType.NONE,
        reserveAmmo: Int = 60
    ) {
        val instance = WeaponInstance(
            spec = spec,
            currentAmmo = spec.magazineSize,
            equippedScope = scope
        ).sanitized()
        fireCooldownRemainingSec = 0f
        accumulatedRecoilPitch = 0f
        _uiState.update { state ->
            val updatedSlots = state.weaponSlots.toMutableMap().apply {
                put(slot, instance)
            }
            val updatedReserve = state.ammoReserve.toMutableMap().apply {
                put(spec.category.ammoType, reserveAmmo.coerceIn(0, 999))
            }
            val updatedScopes = if (scope != ScopeType.NONE) state.inventoryScopes + scope else state.inventoryScopes
            state.copy(
                weaponSlots = updatedSlots,
                activeWeaponSlot = slot,
                ammoReserve = updatedReserve,
                inventoryScopes = updatedScopes,
                isReloading = false,
                reloadRemainingSec = 0f,
                isManualFiringHeld = false,
                isScopedIn = false,
                isAiming = false
            )
        }
    }

    /**
     * Automatic Gun Pickup & Equip + Category-based Auto-Loot + Physical Coin Pickup:
     * - Zero-allocation fast path when no uncollected loot is within pickupRange (optimized for Vivo Y03).
     * - Line-of-sight wall check prevents picking up weapons, loot, or coins through solid walls or Gloo Walls.
     * - Two-pass priority processing equips guns and backpacks first, then immediately manages compatible ammo
     *   and attachments in the same tick.
     * - Prevents duplicate weapons, negative ammo, infinite swap loops, and loot respawning.
     */
    private fun stepAutomaticLootAndGunPickup() {
        val s = _uiState.value
        if (s.isPlayerKnocked || s.isPlayerDead || s.drivingVehicleId != null) return

        val nowMs = System.currentTimeMillis()
        val pickupRange = 4.0f
        val lootList = s.lootItems

        // Fast zero-allocation pre-check: return immediately if no uncollected item is within pickupRange
        var hasCandidateInRange = false
        for (i in 0 until lootList.size) {
            val item = lootList[i]
            if (!item.collected && item.pickupCooldownUntilMs <= nowMs) {
                val dx = item.x - s.playerX
                val dz = item.z - s.playerZ
                if (abs(dx) <= pickupRange && abs(dz) <= pickupRange && hypot(dx, dz) <= pickupRange) {
                    hasCandidateInRange = true
                    break
                }
            }
        }
        if (!hasCandidateInRange) return

        // Gather indices of reachable (non-wall-blocked) uncollected loot items in range
        val candidateIndices = ArrayList<Int>(8)
        for (i in 0 until lootList.size) {
            val item = lootList[i]
            if (item.collected || item.pickupCooldownUntilMs > nowMs) continue
            val dx = item.x - s.playerX
            val dz = item.z - s.playerZ
            if (abs(dx) > pickupRange || abs(dz) > pickupRange) continue
            val dist = hypot(dx, dz)
            if (dist > pickupRange) continue
            // Prevent pickup through walls or Gloo Walls
            if (dist > 0.70f && !IslandMapGenerator.hasLineOfSight(s.playerX, s.playerZ, item.x, item.z, s.glooWalls)) {
                continue
            }
            candidateIndices.add(i)
        }
        if (candidateIndices.isEmpty()) return

        val slots = s.weaponSlots.toMutableMap()
        var activeSlot = s.activeWeaponSlot
        val ammoMap = s.ammoReserve.toMutableMap()
        val scopes = s.inventoryScopes.toMutableSet()
        var muzzles = s.unequippedMuzzles.coerceAtLeast(0)
        var extMags = s.unequippedExtendedMags.coerceAtLeast(0)
        var foregrips = s.unequippedForegrips.coerceAtLeast(0)
        var medkits = s.medkitCount.coerceAtLeast(0)
        var grenades = s.grenadeCount.coerceAtLeast(0)
        var glooCount = s.glooWallCount.coerceAtLeast(0)
        var coins = s.matchCoins.coerceAtLeast(0)
        var coinsTotal = s.playerCoinsCollectedTotal.coerceAtLeast(0)
        var armorTier = s.armorTier
        var playerArmor = s.playerArmor
        var playerMaxArmor = s.playerMaxArmor
        var helmetTier = s.helmetTier
        var backpackTier = s.backpackTier
        var lootCollected = s.playerLootCollectedCount
        var anyItemPickedUp = false
        var anyCoinPickedUp = false
        var coinsAddedThisTick = 0
        var bannerMsg: String? = null
        val pickedUpIds = HashSet<Int>(candidateIndices.size)

        fun currentSimulatedWeight(): Int {
            val ammoW = ammoMap.entries.sumOf { (type, count) -> (count.coerceAtLeast(0) / 10) * type.unitWeight }
            val itemW = medkits * 8 + grenades * 6 + glooCount * 6 + (muzzles + extMags + foregrips) * 4
            return ammoW + itemW
        }

        // Pass 1: Coins, Backpacks, Guns, Armor, and Helmets (so backpack capacity & weapon slots update first)
        for (idx in candidateIndices) {
            val item = lootList[idx]
            if (item.collected || item.id in pickedUpIds) continue

            when (item.category) {
                LootCategory.COIN -> {
                    val amt = item.amount.coerceAtLeast(1)
                    coins = (coins + amt).coerceAtLeast(0)
                    coinsTotal = (coinsTotal + amt).coerceAtLeast(0)
                    coinsAddedThisTick += amt
                    anyCoinPickedUp = true
                    pickedUpIds.add(item.id)
                }

                LootCategory.BACKPACK -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.backpack) continue
                    if (item.tier > backpackTier) {
                        backpackTier = item.tier
                        lootCollected++
                        anyItemPickedUp = true
                        bannerMsg = "EQUIPPED BACKPACK LV.$backpackTier"
                        pickedUpIds.add(item.id)
                    }
                }

                LootCategory.GUN -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.guns) continue
                    val spec = item.weaponSpec ?: continue
                    // Never create duplicate weapons from one loot item or duplicate weapon specs in slots
                    val alreadyCarriesWeapon = slots.values.any { it?.spec?.id == spec.id }
                    if (alreadyCarriesWeapon) continue

                    val targetSlot: WeaponSlotIndex? = when {
                        spec.category.isSecondary -> WeaponSlotIndex.SECONDARY
                        slots[WeaponSlotIndex.MAIN_1] == null -> WeaponSlotIndex.MAIN_1
                        slots[WeaponSlotIndex.MAIN_2] == null -> WeaponSlotIndex.MAIN_2
                        else -> null // Both main slots already occupied; avoid infinite auto-swap loops
                    }

                    if (targetSlot != null) {
                        val attachAuto = s.autoLoot.enabled && s.autoLoot.attachments
                        val bestScope = if (attachAuto) {
                            scopes.filter { it in spec.category.compatibleScopes }
                                .maxByOrNull { it.zoomFactor }
                                ?: if (spec.category == WeaponCategory.SNIPER) ScopeType.SNIPER_8X else ScopeType.NONE
                        } else {
                            if (spec.category == WeaponCategory.SNIPER) ScopeType.SNIPER_8X else ScopeType.NONE
                        }
                        val useMuzzle = attachAuto && muzzles > 0
                        if (useMuzzle) muzzles--
                        val useMag = attachAuto && extMags > 0
                        if (useMag) extMags--
                        val useGrip = attachAuto && foregrips > 0
                        if (useGrip) foregrips--

                        if (spec.category == WeaponCategory.SNIPER) {
                            scopes.add(ScopeType.SNIPER_8X)
                        }
                        val initialAmmo = item.amount.coerceIn(1, spec.magazineSize)
                        val newWeaponInstance = WeaponInstance(
                            spec = spec,
                            currentAmmo = initialAmmo,
                            equippedScope = bestScope,
                            hasMuzzle = useMuzzle,
                            hasExtendedMag = useMag,
                            hasForegrip = useGrip
                        ).sanitized()

                        slots[targetSlot] = newWeaponInstance
                        activeSlot = targetSlot // Automatically equip the weapon!

                        // Automatically manage compatible reserve ammo when picking up a weapon
                        val aType = spec.category.ammoType
                        val currentReserve = (ammoMap[aType] ?: 0).coerceAtLeast(0)
                        if (s.autoLoot.enabled && s.autoLoot.ammo) {
                            val maxCap = 110 + backpackTier * 55
                            val bonusAmmo = (aType.defaultPickupCount / 2).coerceAtLeast(10)
                            val addedWeight = (bonusAmmo / 10).coerceAtLeast(1) * aType.unitWeight
                            if (currentSimulatedWeight() + addedWeight <= maxCap) {
                                ammoMap[aType] = (currentReserve + bonusAmmo).coerceIn(0, 999)
                            } else {
                                ammoMap[aType] = currentReserve
                            }
                        } else {
                            ammoMap[aType] = currentReserve
                        }

                        lootCollected++
                        anyItemPickedUp = true
                        bannerMsg = "AUTO-EQUIPPED ${spec.name.uppercase()}!"
                        pickedUpIds.add(item.id)
                    }
                }

                LootCategory.ARMOR -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.armor) continue
                    if (item.tier > armorTier || playerArmor < playerMaxArmor * 0.75f) {
                        armorTier = max(armorTier, item.tier)
                        playerMaxArmor = 50f + armorTier * 25f
                        playerArmor = playerMaxArmor
                        lootCollected++
                        anyItemPickedUp = true
                        if (bannerMsg == null) bannerMsg = "EQUIPPED VEST LV.$armorTier"
                        pickedUpIds.add(item.id)
                    }
                }

                LootCategory.HELMET -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.helmet) continue
                    if (item.tier > helmetTier) {
                        helmetTier = item.tier
                        lootCollected++
                        anyItemPickedUp = true
                        if (bannerMsg == null) bannerMsg = "EQUIPPED HELMET LV.$helmetTier"
                        pickedUpIds.add(item.id)
                    }
                }

                else -> Unit
            }
        }

        // Pass 2: Ammo, Scopes, Attachments, Medkits, Gloo Walls, Grenades, Repair Kits
        for (idx in candidateIndices) {
            val item = lootList[idx]
            if (item.collected || item.id in pickedUpIds) continue

            when (item.category) {
                LootCategory.AMMO -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.ammo) continue
                    val aType = item.ammoType ?: continue
                    val isCompatible = slots.values.filterNotNull().any { it.spec.category.ammoType == aType }
                    if (!isCompatible) continue
                    val amt = item.amount.coerceAtLeast(1)
                    val maxCap = 110 + backpackTier * 55
                    val currentRes = (ammoMap[aType] ?: 0).coerceAtLeast(0)
                    if (currentRes >= 999) continue
                    if (currentSimulatedWeight() + (amt / 10).coerceAtLeast(1) * aType.unitWeight > maxCap) continue

                    ammoMap[aType] = (currentRes + amt).coerceIn(0, 999)
                    lootCollected++
                    anyItemPickedUp = true
                    if (bannerMsg == null) bannerMsg = "+$amt ${aType.label.uppercase()}"
                    pickedUpIds.add(item.id)
                }

                LootCategory.SCOPE -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.attachments) continue
                    val sc = item.scopeType ?: continue
                    if (sc !in scopes) {
                        scopes.add(sc)
                        val curWep = slots[activeSlot]
                        if (curWep != null && sc in curWep.spec.category.compatibleScopes && sc.zoomFactor > curWep.equippedScope.zoomFactor) {
                            slots[activeSlot] = curWep.copy(equippedScope = sc).sanitized()
                        }
                        lootCollected++
                        anyItemPickedUp = true
                        if (bannerMsg == null) bannerMsg = "AUTO-EQUIPPED ${sc.label.uppercase()}"
                        pickedUpIds.add(item.id)
                    }
                }

                LootCategory.ATTACHMENT -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.attachments) continue
                    val maxCap = 110 + backpackTier * 55
                    if (currentSimulatedWeight() + 4 > maxCap) continue
                    val att = item.attachmentType ?: continue
                    val curWep = slots[activeSlot]
                    var consumed = false
                    if (curWep != null) {
                        when (att) {
                            AttachmentType.MUZZLE_COMPENSATOR -> if (!curWep.hasMuzzle) {
                                slots[activeSlot] = curWep.copy(hasMuzzle = true).sanitized()
                                consumed = true
                            }
                            AttachmentType.EXTENDED_MAG -> if (!curWep.hasExtendedMag) {
                                slots[activeSlot] = curWep.copy(hasExtendedMag = true).sanitized()
                                consumed = true
                            }
                            AttachmentType.FOREGRIP -> if (!curWep.hasForegrip) {
                                slots[activeSlot] = curWep.copy(hasForegrip = true).sanitized()
                                consumed = true
                            }
                        }
                    }
                    if (!consumed) {
                        when (att) {
                            AttachmentType.MUZZLE_COMPENSATOR -> muzzles++
                            AttachmentType.EXTENDED_MAG -> extMags++
                            AttachmentType.FOREGRIP -> foregrips++
                        }
                    }
                    lootCollected++
                    anyItemPickedUp = true
                    if (bannerMsg == null) bannerMsg = "PICKED UP ${att.label.uppercase()}"
                    pickedUpIds.add(item.id)
                }

                LootCategory.MEDKIT -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.healing) continue
                    val maxCap = 110 + backpackTier * 55
                    val amt = item.amount.coerceAtLeast(1)
                    if (currentSimulatedWeight() + 8 <= maxCap && medkits < 5 + backpackTier * 2) {
                        medkits += amt
                        lootCollected++
                        anyItemPickedUp = true
                        if (bannerMsg == null) bannerMsg = "PICKED UP MEDKIT ($medkits)"
                        pickedUpIds.add(item.id)
                    }
                }

                LootCategory.REPAIR_KIT -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.otherItems) continue
                    if (playerArmor < playerMaxArmor) {
                        playerArmor = playerMaxArmor
                        lootCollected++
                        anyItemPickedUp = true
                        if (bannerMsg == null) bannerMsg = "ARMOR REPAIRED"
                        pickedUpIds.add(item.id)
                    }
                }

                LootCategory.GRENADE -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.grenades) continue
                    val maxCap = 110 + backpackTier * 55
                    val amt = item.amount.coerceAtLeast(1)
                    if (currentSimulatedWeight() + 6 <= maxCap && grenades < 4 + backpackTier) {
                        grenades += amt
                        lootCollected++
                        anyItemPickedUp = true
                        if (bannerMsg == null) bannerMsg = "PICKED UP FRAG GRENADE ($grenades)"
                        pickedUpIds.add(item.id)
                    }
                }

                LootCategory.GLOO_WALL -> {
                    if (!s.autoLoot.enabled || !s.autoLoot.glooWall) continue
                    val maxCap = 110 + backpackTier * 55
                    val amt = item.amount.coerceAtLeast(1)
                    if (currentSimulatedWeight() + 6 <= maxCap && glooCount < 5 + backpackTier * 2) {
                        glooCount += amt
                        lootCollected++
                        anyItemPickedUp = true
                        if (bannerMsg == null) bannerMsg = "PICKED UP GLOO WALL ($glooCount)"
                        pickedUpIds.add(item.id)
                    }
                }

                else -> Unit
            }
        }

        if (pickedUpIds.isEmpty()) return

        if (anyCoinPickedUp && bannerMsg == null) {
            bannerMsg = "+$coinsAddedThisTick BATTLE COINS ($coins TOTAL)"
        }

        if (anyCoinPickedUp) {
            soundEngine.playCoinPickup()
        } else if (anyItemPickedUp) {
            soundEngine.playPickup()
        }

        val updatedLoot = ArrayList<WorldLootItem>(lootList.size)
        for (i in 0 until lootList.size) {
            val item = lootList[i]
            if (item.id in pickedUpIds) {
                updatedLoot.add(item.copy(collected = true))
            } else {
                updatedLoot.add(item)
            }
        }

        val slotSwitched = activeSlot != s.activeWeaponSlot
        if (slotSwitched) {
            fireCooldownRemainingSec = 0f
            accumulatedRecoilPitch = 0f
        }
        _uiState.update { state ->
            state.copy(
                lootItems = updatedLoot,
                weaponSlots = slots,
                activeWeaponSlot = activeSlot,
                isReloading = if (slotSwitched) false else state.isReloading,
                reloadRemainingSec = if (slotSwitched) 0f else state.reloadRemainingSec,
                isScopedIn = if (slotSwitched) false else state.isScopedIn,
                isAiming = if (slotSwitched) false else state.isAiming,
                isManualFiringHeld = if (slotSwitched) false else state.isManualFiringHeld,
                ammoReserve = ammoMap,
                inventoryScopes = scopes,
                unequippedMuzzles = muzzles,
                unequippedExtendedMags = extMags,
                unequippedForegrips = foregrips,
                medkitCount = medkits,
                grenadeCount = grenades,
                glooWallCount = glooCount,
                matchCoins = coins,
                playerCoinsCollectedTotal = coinsTotal,
                armorTier = armorTier,
                playerArmor = playerArmor,
                playerMaxArmor = playerMaxArmor,
                helmetTier = helmetTier,
                backpackTier = backpackTier,
                playerLootCollectedCount = lootCollected
            )
        }
        bannerMsg?.let { showStatusBanner(it) }
    }

    private fun stepSafeZone(dt: Float) {
        val s = _uiState.value
        val sz = s.safeZone

        var nextSz = sz
        if (sz.isFinalZoneReached) {
            val outsideFinal = !s.isPlayerDead &&
                hypot(s.playerX - sz.centerX, s.playerZ - sz.centerZ) > sz.currentRadius
            nextSz = sz.copy(
                isShrinking = false,
                warningTimerSec = 0f,
                shrinkTimerSec = 0f,
                isPlayerOutside = outsideFinal,
                warningMessage = if (outsideFinal) {
                    "DANGER: OUTSIDE SAFE ZONE! (-${sz.damagePerSecond.toInt()} HP/s)"
                } else {
                    "Final Safe Zone Active"
                }
            )
        } else if (!sz.isShrinking) {
            val prevWarn = sz.warningTimerSec
            val newWarn = (sz.warningTimerSec - dt).coerceAtLeast(0f)
            if (prevWarn > 10f && newWarn <= 10f) {
                soundEngine.playZoneWarning()
                showStatusBanner("WARNING: SAFE ZONE SHRINKING IN 10s!")
            }
            if (newWarn <= 0f) {
                soundEngine.playZoneWarning()
                val shrinkSpeed = 12.0f
                val estShrinkSec = ((sz.currentRadius - sz.targetRadius).coerceAtLeast(0f) / shrinkSpeed)
                nextSz = sz.copy(
                    isShrinking = true,
                    warningTimerSec = 0f,
                    shrinkTimerSec = estShrinkSec,
                    warningMessage = "Phase ${sz.phase} - Safe Zone Shrinking (${kotlin.math.ceil(estShrinkSec).toInt()}s)"
                )
                showStatusBanner("WARNING: SAFE ZONE IS SHRINKING!")
            } else {
                val secsLeft = kotlin.math.ceil(newWarn).toInt()
                nextSz = sz.copy(
                    warningTimerSec = newWarn,
                    shrinkTimerSec = 0f,
                    warningMessage = if (secsLeft <= 10) {
                        "WARNING: Safe Zone Shrink in ${secsLeft}s!"
                    } else {
                        "Phase ${sz.phase} - Shrink in ${secsLeft}s"
                    }
                )
            }
        } else {
            val shrinkSpeed = 12.0f
            val radiusRemainingBefore = (sz.currentRadius - sz.targetRadius).coerceAtLeast(0.001f)
            val shrinkDelta = min(radiusRemainingBefore, shrinkSpeed * dt)
            val newRadius = (sz.currentRadius - shrinkDelta).coerceAtLeast(sz.targetRadius)
            val stepFraction = (shrinkDelta / radiusRemainingBefore).coerceIn(0f, 1f)
            val newCx = (sz.centerX + (sz.targetCenterX - sz.centerX) * stepFraction)
                .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 35f, IslandMapGenerator.PLAYABLE_LIMIT - 35f)
            val newCz = (sz.centerZ + (sz.targetCenterZ - sz.centerZ) * stepFraction)
                .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 35f, IslandMapGenerator.PLAYABLE_LIMIT - 35f)
            val remShrinkSec = ((newRadius - sz.targetRadius) / shrinkSpeed).coerceAtLeast(0f)

            if (newRadius <= sz.targetRadius + 0.25f) {
                if (sz.phase >= 5) {
                    nextSz = sz.copy(
                        phase = 5,
                        centerX = sz.targetCenterX,
                        centerZ = sz.targetCenterZ,
                        currentRadius = sz.targetRadius,
                        targetRadius = sz.targetRadius,
                        isShrinking = false,
                        isFinalZoneReached = true,
                        warningTimerSec = 0f,
                        shrinkTimerSec = 0f,
                        warningMessage = "Final Safe Zone Active"
                    )
                    showStatusBanner("FINAL SAFE ZONE REACHED!")
                } else {
                    val nextPhase = (sz.phase + 1).coerceAtMost(5)
                    val nextTargetRadius = when (nextPhase) {
                        2 -> 240f
                        3 -> 145f
                        4 -> 75f
                        else -> 28f
                    }
                    val nextDps = when (nextPhase) {
                        2 -> 4.0f
                        3 -> 6.5f
                        4 -> 10.0f
                        else -> 14.5f
                    }
                    val nextWarnSec = when (nextPhase) {
                        2 -> 25f
                        3 -> 22f
                        4 -> 18f
                        else -> 15f
                    }
                    val shiftAngle = (nextPhase * 2.15f + matchSeedCounter * 0.37f)
                    val maxShift = (sz.targetRadius - nextTargetRadius).coerceAtLeast(0f) * 0.42f
                    val nextTargetCx = (sz.targetCenterX + sin(shiftAngle) * maxShift)
                        .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + nextTargetRadius + 25f, IslandMapGenerator.PLAYABLE_LIMIT - nextTargetRadius - 25f)
                    val nextTargetCz = (sz.targetCenterZ + cos(shiftAngle) * maxShift)
                        .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + nextTargetRadius + 25f, IslandMapGenerator.PLAYABLE_LIMIT - nextTargetRadius - 25f)

                    nextSz = sz.copy(
                        phase = nextPhase,
                        centerX = sz.targetCenterX,
                        centerZ = sz.targetCenterZ,
                        currentRadius = sz.targetRadius,
                        targetCenterX = nextTargetCx,
                        targetCenterZ = nextTargetCz,
                        targetRadius = nextTargetRadius,
                        isShrinking = false,
                        isFinalZoneReached = false,
                        warningTimerSec = nextWarnSec,
                        shrinkTimerSec = 0f,
                        damagePerSecond = nextDps,
                        warningMessage = "Phase $nextPhase - Safe Zone Shrink in ${nextWarnSec.toInt()}s"
                    )
                }
            } else {
                nextSz = sz.copy(
                    centerX = newCx,
                    centerZ = newCz,
                    currentRadius = newRadius,
                    shrinkTimerSec = remShrinkSec,
                    warningMessage = "Phase ${sz.phase} - Shrinking (${kotlin.math.ceil(remShrinkSec).toInt()}s)"
                )
            }
        }

        val isOutsideNow = !s.isPlayerDead &&
            hypot(s.playerX - nextSz.centerX, s.playerZ - nextSz.centerZ) > nextSz.currentRadius
        if (isOutsideNow) {
            if (!sz.isPlayerOutside) {
                soundEngine.playZoneWarning()
            }
            applyDamageToPlayer(nextSz.damagePerSecond * dt, attackerBotId = null, bypassArmor = true)
        }
        nextSz = nextSz.copy(isPlayerOutside = isOutsideNow)

        _uiState.update { it.copy(safeZone = nextSz) }
    }

    private fun applyDamageToPlayer(damage: Float, attackerBotId: Int?, bypassArmor: Boolean = false) {
        val s = _uiState.value
        if (s.isPlayerDead) return

        if (s.isPlayerKnocked) {
            val nextTimer = s.playerKnockedTimerSec - damage * 0.35f
            if (nextTimer <= 0f) {
                handlePlayerFullDeath(attackerBotId ?: s.killerBotId)
            } else {
                _uiState.update { it.copy(playerKnockedTimerSec = nextTimer) }
            }
            return
        }

        var remainingDmg = damage
        var newArmor = s.playerArmor
        if (!bypassArmor && newArmor > 0f) {
            val absorbed = min(newArmor, remainingDmg * 0.55f)
            newArmor -= absorbed
            remainingDmg -= absorbed
        }
        val newHp = s.playerHp - remainingDmg
        if (newHp <= 0f) {
            // Eligible for Knocked state in Duo/Squad when at least one friendly teammate is alive and not knocked
            if (s.isEligibleForKnock) {
                soundEngine.playKnockOrDeath()
                showStatusBanner("YOU ARE KNOCKED! TEAMMATE CHECKING SAFETY TO REVIVE!")
                _uiState.update {
                    it.copy(
                        playerHp = 0f,
                        playerArmor = newArmor.coerceAtLeast(0f),
                        isPlayerKnocked = true,
                        isPlayerDead = false,
                        isBeingRevived = false,
                        reviveProgressSec = 0f,
                        playerKnockedTimerSec = 20f,
                        drivingVehicleId = null,
                        vehicleSpeedMps = 0f,
                        vehicleThrottleInput = 0f,
                        vehicleBrakeInput = false,
                        vehicleSteerInput = 0f,
                        vehicles = it.vehicles.map { v -> if (v.occupantId == 0) v.copy(occupantId = null) else v },
                        isManualFiringHeld = false,
                        isReloading = false,
                        reloadRemainingSec = 0f,
                        isScopedIn = false,
                        isAiming = false,
                        isFreeLooking = false,
                        isCrouching = false,
                        isProne = false,
                        isJumping = false,
                        isVaulting = false,
                        isSprinting = false,
                        nearbyKnockAllyToRevive = null,
                        nearbyVendingMachine = null,
                        nearbyVehicle = null,
                        killerBotId = attackerBotId,
                        playerAnimState = CharacterAnimState.KNOCK
                    )
                }
            } else {
                handlePlayerFullDeath(attackerBotId)
            }
        } else {
            _uiState.update {
                it.copy(playerHp = newHp, playerArmor = newArmor.coerceAtLeast(0f))
            }
        }
    }

    private fun handlePlayerFullDeath(killerBotId: Int?) {
        val s = _uiState.value
        if (s.isPlayerDead || matchFinishedForCurrentGame) return
        soundEngine.playKnockOrDeath()
        moveInputX = 0f
        moveInputY = 0f
        val effectiveKillerId = killerBotId ?: s.killerBotId
        val validTargets = buildValidSpectatorTargets(s.bots, effectiveKillerId)
        val firstTarget = validTargets.firstOrNull()
        _uiState.update {
            it.copy(
                playerHp = 0f,
                isPlayerKnocked = false,
                isBeingRevived = false,
                reviveProgressSec = 0f,
                playerKnockedTimerSec = 0f,
                isPlayerDead = true,
                isSpectating = firstTarget != null,
                killerBotId = effectiveKillerId,
                spectatorTargets = validTargets,
                activeSpectatorTargetIndex = 0,
                activeSpectatorTargetId = firstTarget?.botId,
                playerX = firstTarget?.x ?: it.playerX,
                playerY = firstTarget?.y ?: it.playerY,
                playerZ = firstTarget?.z ?: it.playerZ,
                playerYawDeg = firstTarget?.yawDeg ?: it.playerYawDeg,
                drivingVehicleId = null,
                vehicleSpeedMps = 0f,
                vehicleThrottleInput = 0f,
                vehicleBrakeInput = false,
                vehicleSteerInput = 0f,
                vehicles = it.vehicles.map { v -> if (v.occupantId == 0) v.copy(occupantId = null) else v },
                isManualFiringHeld = false,
                isReloading = false,
                reloadRemainingSec = 0f,
                isScopedIn = false,
                isAiming = false,
                isFreeLooking = false,
                isCrouching = false,
                isProne = false,
                isJumping = false,
                isVaulting = false,
                isSprinting = false,
                isInventoryOpen = false,
                nearbyKnockAllyToRevive = null,
                nearbyVendingMachine = null,
                nearbyVehicle = null,
                playerAnimState = CharacterAnimState.DEATH
            )
        }
        if (firstTarget != null) {
            showStatusBanner("ELIMINATED - ENTERING SPECTATOR MODE")
        } else {
            finishMatch(isVictory = false)
        }
    }

    /**
     * Builds valid living spectator targets:
     * 1. Living teammates first (when any teammate is alive).
     * 2. If no teammate is alive, valid remaining living players (eliminator first if alive, then other living players).
     * Never includes dead or invalid targets.
     */
    private fun buildValidSpectatorTargets(bots: List<CombatantBot>, killerId: Int?): List<SpectatorTarget> {
        val livingTeammates = bots
            .filter { it.isFriendly && !it.isDead && it.hp > 0f && !it.x.isNaN() && !it.z.isNaN() }
            .sortedBy { it.id }
            .map { b ->
                SpectatorTarget(b.id, b.name, isTeammate = true, b.characterId, b.x, b.y, b.z, b.yawDeg, b.hp)
            }
        if (livingTeammates.isNotEmpty()) {
            return livingTeammates
        }

        val result = mutableListOf<SpectatorTarget>()
        if (killerId != null) {
            bots.firstOrNull {
                it.id == killerId && !it.isFriendly && !it.isDead && it.hp > 0f && !it.x.isNaN() && !it.z.isNaN()
            }?.let { k ->
                result.add(SpectatorTarget(k.id, k.name, isTeammate = false, k.characterId, k.x, k.y, k.z, k.yawDeg, k.hp))
            }
        }
        bots.filter {
            !it.isFriendly && !it.isDead && it.hp > 0f && it.id != killerId && !it.x.isNaN() && !it.z.isNaN()
        }
            .sortedBy { it.id }
            .take(8)
            .forEach { e ->
                result.add(SpectatorTarget(e.id, e.name, isTeammate = false, e.characterId, e.x, e.y, e.z, e.yawDeg, e.hp))
            }
        return result
    }

    private fun resolveSpectatorSelection(
        validTargets: List<SpectatorTarget>,
        currentTargetId: Int?,
        currentIndex: Int
    ): Pair<Int, SpectatorTarget>? {
        if (validTargets.isEmpty()) return null
        val matchByIdIdx = if (currentTargetId != null) {
            validTargets.indexOfFirst { it.botId == currentTargetId }
        } else -1
        if (matchByIdIdx >= 0) {
            return matchByIdIdx to validTargets[matchByIdIdx]
        }
        // Current target died or was not yet assigned -> automatically select another valid target
        val fallbackIdx = currentIndex.coerceIn(0, validTargets.size - 1)
        return fallbackIdx to validTargets[fallbackIdx]
    }

    // =========================================================================
    // AI NAVIGATION, COMBAT, LINE OF SIGHT, REVIVE SAFETY & DISTANCE LOD
    // =========================================================================

    private fun stepBotsAndIndicators(dt: Float) {
        val s = _uiState.value
        val sz = s.safeZone
        val nowMs = System.currentTimeMillis()
        aiTickCounter++

        adaptiveAiSkillFactor = (0.85f + s.playerKills * 0.05f).coerceIn(0.80f, 1.20f)

        val newFootsteps = mutableListOf<FootstepIndicator>()
        val newFireIndicators = s.fireDirectionIndicators
            .mapNotNull { ind ->
                val rem = ind.remainingSec - dt
                if (rem > 0f) ind.copy(remainingSec = rem) else null
            }.toMutableList()
        val newTraces = s.bulletTraces
            .mapNotNull { tr ->
                val rem = tr.remainingSec - dt
                if (rem > 0f) tr.copy(remainingSec = rem) else null
            }.toMutableList()

        var newFriendlyCallout = s.friendlyCallout?.let { c ->
            val rem = c.remainingSec - dt
            if (rem > 0f) c.copy(remainingSec = rem) else null
        }

        var playerRevivedByBot = false
        var reviveProgress = s.reviveProgressSec
        var isBeingRevivedNow = false

        val lootCollectedByBots = HashSet<Int>(4)
        val revivedBotIds = HashSet<Int>(2)
        val teammateMarkedEnemyIds = HashSet<Int>(4)
        val pendingDamageOnBot = HashMap<Int, Float>(8)
        val pendingAttackerForBot = HashMap<Int, Int>(8)
        val vehiclePositionsUpdates = HashMap<Int, Triple<Float, Float, Float>>(4)
        val occupiedVehicleIds = HashSet<Int>(4).apply {
            s.drivingVehicleId?.let { add(it) }
            s.bots.forEach { b ->
                if (!b.isDead && !b.isKnocked && b.drivingVehicleId != null) {
                    add(b.drivingVehicleId)
                }
            }
        }

        val playerAimRad = Math.toRadians(s.playerYawDeg.toDouble())
        val playerFwdX = sin(playerAimRad).toFloat()
        val playerFwdZ = cos(playerAimRad).toFloat()
        val playerRightX = cos(playerAimRad).toFloat()
        val playerRightZ = -sin(playerAimRad).toFloat()

        val updatedBots = s.bots.map { bot ->
            if (bot.isDead) return@map bot

            if (bot.isKnocked) {
                val remKnock = bot.knockedTimerSec - dt
                if (remKnock <= 0f) {
                    return@map bot.copy(
                        isDead = true,
                        isKnocked = false,
                        hp = 0f,
                        drivingVehicleId = null,
                        animState = CharacterAnimState.DEATH
                    )
                }
                return@map bot.copy(
                    knockedTimerSec = remKnock,
                    drivingVehicleId = null,
                    animState = CharacterAnimState.KNOCK
                )
            }

            val distToPlayer = hypot(bot.x - s.playerX, bot.z - s.playerZ)
            val distToZone = hypot(bot.x - sz.centerX, bot.z - sz.centerZ)
            var currentHp = bot.hp
            var currentArmor = bot.armor
            var currentArmorTier = bot.armorTier
            var currentHelmetTier = bot.helmetTier
            if (distToZone > sz.currentRadius) {
                currentHp -= sz.damagePerSecond * dt * 0.75f
                if (currentHp <= 0f) {
                    return@map bot.copy(
                        hp = 0f,
                        isDead = true,
                        isKnocked = false,
                        drivingVehicleId = null,
                        animState = CharacterAnimState.DEATH
                    )
                }
            }

            // Distance-tiered AI scheduling for Vivo Y03 optimization (Section 14):
            // - Friendly teammates & nearby enemies (< 95m): full decision every frame
            // - Medium (95m..210m): decision check every 2nd frame
            // - Far (> 210m): decision check every 4th frame
            val shouldRunFullDecision = when {
                bot.isFriendly || distToPlayer < 95f -> true
                distToPlayer < 210f -> (aiTickCounter + bot.id) % 2 == 0
                else -> (aiTickCounter + bot.id) % 4 == 0
            }

            var medkits = bot.medkits
            var healTimer = bot.healTimerSec
            var healCooldown = (bot.healCooldownSec - dt).coerceAtLeast(0f)
            var reloadTimer = bot.reloadTimerSec
            var ammoInMag = bot.ammoInMag
            var reserveAmmo = bot.reserveAmmo
            var chosenWeapon = bot.equippedWeapon
            var calloutCd = (bot.calloutCooldownSec - dt).coerceAtLeast(0f)
            var fireCd = (bot.fireCooldownSec - dt).coerceAtLeast(0f)
            var detectedMarkerRem = (bot.detectedMarkerRemainingSec - dt).coerceAtLeast(0f)
            var teammateMarkedRem = (bot.teammateMarkedRemainingSec - dt).coerceAtLeast(0f)
            var lastDetectedMs = if (detectedMarkerRem > 0f) bot.lastDetectedByPlayerTimeMs else 0L
            var markedUntil = if (teammateMarkedRem > 0f) bot.markedByFriendlyUntilMs else 0L
            var aimLockTimer = bot.aimLockTimerSec
            var jumpTimer = (bot.jumpTimerSec - dt).coerceAtLeast(0f)
            var isCrouching = bot.isCrouching
            var drivingVehId = bot.drivingVehicleId
            var lastKnownX = bot.lastKnownTargetX
            var lastKnownZ = bot.lastKnownTargetZ
            var memoryTimer = (bot.memoryTimerSec - dt).coerceAtLeast(0f)
            if (memoryTimer <= 0f) {
                lastKnownX = null
                lastKnownZ = null
            }

            var goalX = bot.targetX
            var goalZ = bot.targetZ
            var role = bot.aiRole
            var botKills = bot.kills
            var botDamageDealt = bot.damageDealt

            // 1. Finite Timed Healing (No Infinite Healing!)
            if (healTimer > 0f) {
                healTimer -= dt
                isCrouching = true
                role = BotAiRole.HEALING
                if (healTimer <= 0f) {
                    healTimer = 0f
                    if (medkits > 0) {
                        medkits--
                        currentHp = (currentHp + 55f).coerceAtMost(bot.maxHp)
                        healCooldown = 8.0f
                    }
                    isCrouching = false
                }
            } else if (currentHp < 52f && medkits > 0 && healCooldown <= 0f && distToZone <= sz.currentRadius) {
                healTimer = 2.2f
                reloadTimer = 0f
                drivingVehId = null
                isCrouching = true
                role = BotAiRole.HEALING
                val threatX = lastKnownX ?: s.playerX
                val threatZ = lastKnownZ ?: s.playerZ
                val (covX, covZ) = IslandMapGenerator.findNearbyCoverPoint(bot.x, bot.z, threatX, threatZ, s.glooWalls)
                goalX = covX
                goalZ = covZ
                if (bot.isFriendly && calloutCd <= 0f && newFriendlyCallout == null) {
                    newFriendlyCallout = FriendlyVoiceCallout(bot.name, "Taking cover to heal!")
                    calloutCd = 10f
                }
            }

            // 2. Finite Ammo & Reloading (No Infinite Ammo, No Infinite Shooting!)
            if (reloadTimer > 0f) {
                reloadTimer -= dt
                if (reloadTimer <= 0f) {
                    reloadTimer = 0f
                    val needed = (chosenWeapon.magazineSize - ammoInMag.coerceAtLeast(0)).coerceAtLeast(0)
                    val loaded = min(needed, reserveAmmo.coerceAtLeast(0))
                    ammoInMag = (ammoInMag.coerceAtLeast(0) + loaded).coerceIn(0, chosenWeapon.magazineSize)
                    reserveAmmo = (reserveAmmo - loaded).coerceAtLeast(0)
                }
            } else if (ammoInMag <= 0 && reserveAmmo > 0 && healTimer <= 0f) {
                reloadTimer = chosenWeapon.reloadTimeSec.coerceAtLeast(1.2f)
            }

            // 3. Ground Looting & Automatic Weapon Pickup/Equip for Bots (when near uncollected loot)
            if (shouldRunFullDecision && drivingVehId == null && healTimer <= 0f && distToPlayer < 165f) {
                val needsBetterGun = chosenWeapon.category.isSecondary
                val needsAmmo = reserveAmmo < chosenWeapon.magazineSize * 2
                val needsMedkit = medkits < 3
                val needsArmor = currentArmor < 80f || currentArmorTier < 2
                if (needsBetterGun || needsAmmo || needsMedkit || needsArmor) {
                    var bestLoot: WorldLootItem? = null
                    var bestLootDist = 24f
                    for (item in s.lootItems) {
                        if (item.collected || item.id in lootCollectedByBots) continue
                        val lx = item.x
                        val lz = item.z
                        if (abs(lx - bot.x) > bestLootDist || abs(lz - bot.z) > bestLootDist) continue
                        // Friendly bots never snatch loot right next to the human player (< 7.5m)
                        if (bot.isFriendly && hypot(lx - s.playerX, lz - s.playerZ) < 7.5f) continue
                        val matchNeed = when (item.category) {
                            LootCategory.GUN -> needsBetterGun || (item.weaponSpec != null && item.weaponSpec.baseDamage > chosenWeapon.baseDamage && chosenWeapon.category == item.weaponSpec.category)
                            LootCategory.AMMO -> needsAmmo
                            LootCategory.MEDKIT -> needsMedkit
                            LootCategory.ARMOR, LootCategory.HELMET -> needsArmor
                            else -> false
                        }
                        if (!matchNeed) continue
                        val d = hypot(lx - bot.x, lz - bot.z)
                        if (d < bestLootDist && IslandMapGenerator.hasLineOfSight(bot.x, bot.z, lx, lz, s.glooWalls)) {
                            bestLootDist = d
                            bestLoot = item
                        }
                    }
                    if (bestLoot != null) {
                        if (bestLootDist <= 3.8f) {
                            lootCollectedByBots.add(bestLoot.id)
                            when (bestLoot.category) {
                                LootCategory.GUN -> {
                                    val spec = bestLoot.weaponSpec
                                    if (spec != null) {
                                        chosenWeapon = spec
                                        ammoInMag = spec.magazineSize
                                        reserveAmmo = (reserveAmmo + spec.magazineSize * 2).coerceIn(spec.magazineSize, 150)
                                        reloadTimer = 0f
                                        if (bot.isFriendly && calloutCd <= 0f && newFriendlyCallout == null) {
                                            newFriendlyCallout = FriendlyVoiceCallout(bot.name, "Equipped ${spec.name}!")
                                            calloutCd = 10f
                                        }
                                    }
                                }
                                LootCategory.AMMO -> {
                                    reserveAmmo = (reserveAmmo + bestLoot.amount.coerceAtLeast(30)).coerceAtMost(180)
                                }
                                LootCategory.MEDKIT -> {
                                    medkits = (medkits + 1).coerceAtMost(4)
                                }
                                LootCategory.ARMOR -> {
                                    currentArmorTier = max(currentArmorTier, bestLoot.tier.coerceIn(1, 3))
                                    currentArmor = (currentArmor + 50f).coerceAtMost(100f)
                                }
                                LootCategory.HELMET -> {
                                    currentHelmetTier = max(currentHelmetTier, bestLoot.tier.coerceIn(1, 3))
                                }
                                else -> Unit
                            }
                        } else if (role == BotAiRole.PATROLLING || role == BotAiRole.LOOTING || (ammoInMag <= 0 && reserveAmmo <= 0)) {
                            role = BotAiRole.LOOTING
                            goalX = bestLoot.x
                            goalZ = bestLoot.z
                        }
                    }
                }
            }

            // 4. Target Acquisition with Strict Line-of-Sight & Fair Perception (No Seeing/Shooting Through Walls!)
            var targetEnemyX: Float? = null
            var targetEnemyZ: Float? = null
            var targetIsPlayer = false
            var targetBotId: Int? = null
            var distToTarget = 999f

            if (shouldRunFullDecision && healTimer <= 0f) {
                if (bot.isFriendly) {
                    // Friendly AI: check for visible enemies with unobstructed Line-of-Sight
                    // (when rotating to Safe Zone or > 65m from player, only stop for close threats < 50m)
                    var bestEnemy: CombatantBot? = null
                    var bestEnemyDist = if (distToZone > sz.currentRadius * 0.86f || distToPlayer > 65f) 50f else 115f
                    for (other in s.bots) {
                        if (other.isFriendly || other.isDead) continue
                        val d = hypot(other.x - bot.x, other.z - bot.z)
                        if (d < bestEnemyDist && IslandMapGenerator.hasLineOfSight(bot.x, bot.z, other.x, other.z, s.glooWalls)) {
                            bestEnemyDist = d
                            bestEnemy = other
                        }
                    }
                    val immediateEnemyThreatNearPlayer = bestEnemy != null &&
                        hypot(bestEnemy.x - s.playerX, bestEnemy.z - s.playerZ) < 24f

                    // Check if another friendly bot is knocked nearby
                    val knockedSquadmate = s.bots.firstOrNull { other ->
                        other.isFriendly && other.id != bot.id && other.isKnocked && !other.isDead && other.id !in revivedBotIds
                    }

                    // A. Priority 1: Revive knocked human player when safe (single channel per tick to prevent duplicate revive)
                    if (s.isPlayerKnocked && !s.isPlayerDead && !immediateEnemyThreatNearPlayer && !playerRevivedByBot) {
                        role = BotAiRole.REVIVING_ALLY
                        drivingVehId = null
                        isCrouching = distToPlayer < 4.0f
                        goalX = s.playerX
                        goalZ = s.playerZ
                        if (calloutCd <= 0f && newFriendlyCallout == null) {
                            newFriendlyCallout = FriendlyVoiceCallout(bot.name, "Hold on! Moving to revive you!")
                            soundEngine.playFriendlyCalloutBeep()
                            calloutCd = 9f
                        }
                        if (distToPlayer < 3.8f && !isBeingRevivedNow) {
                            isBeingRevivedNow = true
                            reviveProgress += dt
                            if (reviveProgress >= 3.0f) {
                                playerRevivedByBot = true
                            }
                        }
                    } else if (knockedSquadmate != null && bestEnemy == null) {
                        // B. Revive knocked friendly squadmate when clear
                        role = BotAiRole.REVIVING_ALLY
                        drivingVehId = null
                        goalX = knockedSquadmate.x
                        goalZ = knockedSquadmate.z
                        if (hypot(knockedSquadmate.x - bot.x, knockedSquadmate.z - bot.z) < 3.6f) {
                            revivedBotIds.add(knockedSquadmate.id)
                        }
                    } else if (bestEnemy != null && (ammoInMag > 0 || reserveAmmo > 0)) {
                        // C. Fight visible enemy, take cover, or flank (and mark enemy temporarily on minimap!)
                        drivingVehId = null
                        teammateMarkedEnemyIds.add(bestEnemy.id)
                        targetEnemyX = bestEnemy.x
                        targetEnemyZ = bestEnemy.z
                        targetBotId = bestEnemy.id
                        distToTarget = bestEnemyDist
                        lastKnownX = bestEnemy.x
                        lastKnownZ = bestEnemy.z
                        memoryTimer = 3.5f

                        if (currentHp < 42f) {
                            role = BotAiRole.TAKING_COVER
                            isCrouching = true
                            val (covX, covZ) = IslandMapGenerator.findNearbyCoverPoint(bot.x, bot.z, bestEnemy.x, bestEnemy.z, s.glooWalls)
                            goalX = covX
                            goalZ = covZ
                        } else if (distToTarget > 32f && (bot.id + aiTickCounter / 30) % 2 == 0) {
                            role = BotAiRole.FLANKING
                            isCrouching = false
                            val flankRad = atan2((bestEnemy.z - bot.z).toDouble(), (bestEnemy.x - bot.x).toDouble()) +
                                if (bot.id % 2 == 0) PI / 2.6 else -PI / 2.6
                            goalX = (bot.x + cos(flankRad).toFloat() * 16f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                            goalZ = (bot.z + sin(flankRad).toFloat() * 16f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                        } else {
                            role = BotAiRole.ENGAGING
                            isCrouching = distToTarget > 38f
                            if (hypot(bot.x - goalX, bot.z - goalZ) < 3.5f) {
                                val strafeRad = atan2((bestEnemy.z - bot.z).toDouble(), (bestEnemy.x - bot.x).toDouble()) +
                                    if ((aiTickCounter / 15 + bot.id) % 2 == 0) PI / 2.2 else -PI / 2.2
                                goalX = (bot.x + cos(strafeRad).toFloat() * 9f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                                goalZ = (bot.z + sin(strafeRad).toFloat() * 9f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                            }
                        }

                        if (calloutCd <= 0f && newFriendlyCallout == null) {
                            val callouts = listOf(
                                "Enemy spotted!",
                                "Engaging hostile target!",
                                "Enemy location marked!",
                                "Suppressing enemy fire!"
                            )
                            val msg = callouts[(bot.id + s.matchElapsedSec.toInt()) % callouts.size]
                            newFriendlyCallout = FriendlyVoiceCallout(bot.name, msg)
                            soundEngine.playFriendlyCalloutBeep()
                            calloutCd = 11f
                        }
                    } else if (distToZone > sz.currentRadius * 0.86f) {
                        // D. Enter the Safe Zone
                        role = BotAiRole.MOVING_TO_ZONE
                        isCrouching = false
                        goalX = sz.centerX + (bot.id - 2) * 10f
                        goalZ = sz.centerZ + (bot.id % 2) * 10f
                        if (calloutCd <= 0f && newFriendlyCallout == null && sz.isShrinking) {
                            newFriendlyCallout = FriendlyVoiceCallout(bot.name, "Safe Zone shrinking, moving in!")
                            calloutCd = 14f
                        }
                    } else {
                        // E. Independent Exploration & Looting (Never constantly following or blocking the player)
                        isCrouching = false
                        if (distToPlayer > 52f) {
                            // Regroup to wide tactical sector (18m..34m behind/flanking player) only if drifted > 52m away
                            val sectorAngle = playerAimRad + (bot.id * (2.0 * PI / 3.0)) + PI * 0.55
                            val sectorDist = 20f + (bot.id % 3) * 5f
                            role = BotAiRole.PATROLLING
                            goalX = (s.playerX + sin(sectorAngle).toFloat() * sectorDist)
                                .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                            goalZ = (s.playerZ + cos(sectorAngle).toFloat() * sectorDist)
                                .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                        } else if (role != BotAiRole.LOOTING && hypot(bot.x - goalX, bot.z - goalZ) < 6.5f) {
                            // Independently explore nearby buildings & loot points in their own sector
                            val exploreAngle = (bot.id * 2.1f + s.matchElapsedSec * 0.08f)
                            val anchorX = s.playerX + sin(exploreAngle) * 26f
                            val anchorZ = s.playerZ + cos(exploreAngle) * 26f
                            role = BotAiRole.PATROLLING
                            goalX = anchorX.coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 28f, IslandMapGenerator.PLAYABLE_LIMIT - 28f)
                            goalZ = anchorZ.coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 28f, IslandMapGenerator.PLAYABLE_LIMIT - 28f)
                        }

                        // Maintain spacing from other friendly teammates
                        for (otherAlly in s.bots) {
                            if (otherAlly.isFriendly && otherAlly.id != bot.id && !otherAlly.isDead) {
                                val adx = bot.x - otherAlly.x
                                val adz = bot.z - otherAlly.z
                                val adist = hypot(adx, adz)
                                if (adist < 5.0f && adist > 0.01f) {
                                    goalX = (goalX + (adx / adist) * 6.0f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                                    goalZ = (goalZ + (adz / adist) * 6.0f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                                }
                            }
                        }
                    }

                    // Always enforce player gun line-of-fire & personal space clearance (unless actively reviving)
                    if (role != BotAiRole.REVIVING_ALLY) {
                        val relX = bot.x - s.playerX
                        val relZ = bot.z - s.playerZ
                        val forwardProj = relX * playerFwdX + relZ * playerFwdZ
                        val lateralProj = relX * playerRightX + relZ * playerRightZ
                        val inPlayerMuzzleCone = forwardProj in -1.0f..20.0f && abs(lateralProj) < 3.6f
                        val tooCloseToPlayer = distToPlayer < 4.5f
                        if (inPlayerMuzzleCone || tooCloseToPlayer) {
                            val sideSign = if (lateralProj >= 0f) 1f else -1f
                            val clearX = s.playerX + playerRightX * (sideSign * 9.5f) - playerFwdX * 3.5f
                            val clearZ = s.playerZ + playerRightZ * (sideSign * 9.5f) - playerFwdZ * 3.5f
                            goalX = clearX.coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                            goalZ = clearZ.coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                        }
                    }
                } else {
                    // Enemy AI Decision Tree: Safe Zone, Revive Squadmate, Fair LOS Detection, Cover, Flank, Reposition, Explore
                    val knockedEnemySquadmate = if (s.selectedTeamMode != TeamMode.SOLO) {
                        s.bots.firstOrNull { other ->
                            !other.isFriendly && other.id != bot.id && other.squadId == bot.squadId &&
                                other.isKnocked && !other.isDead && other.id !in revivedBotIds &&
                                hypot(other.x - bot.x, other.z - bot.z) < 28f
                        }
                    } else null

                    if (distToZone > sz.currentRadius * 0.90f) {
                        role = BotAiRole.MOVING_TO_ZONE
                        isCrouching = false
                        goalX = sz.centerX + (bot.x - sz.centerX) * 0.32f
                        goalZ = sz.centerZ + (bot.z - sz.centerZ) * 0.32f
                    } else {
                        // Fair Line-of-Sight & Vision/Audio Cone Check for Player (No Wallhack / Unfair Info!)
                        val playerDetectRange = when {
                            s.isManualFiringHeld -> 108f
                            s.isProne -> 52f
                            s.isCrouching -> 64f
                            s.isSprinting -> 96f
                            else -> 86f
                        }
                        val angleToPlayer = ((Math.toDegrees(atan2((s.playerX - bot.x).toDouble(), (s.playerZ - bot.z).toDouble())).toFloat()) + 360f) % 360f
                        val inVisionOrHearing = abs(normalizeAngleDiff(angleToPlayer - bot.yawDeg)) <= 105f ||
                            s.isManualFiringHeld ||
                            (s.isSprinting && distToPlayer < 36f) ||
                            distToPlayer < 22f

                        val canSeePlayer = !s.isPlayerDead &&
                            distToPlayer <= playerDetectRange &&
                            inVisionOrHearing &&
                            IslandMapGenerator.hasLineOfSight(bot.x, bot.z, s.playerX, s.playerZ, s.glooWalls)

                        if (canSeePlayer && (ammoInMag > 0 || reserveAmmo > 0)) {
                            drivingVehId = null
                            targetEnemyX = s.playerX
                            targetEnemyZ = s.playerZ
                            targetIsPlayer = true
                            distToTarget = distToPlayer
                            lastKnownX = s.playerX
                            lastKnownZ = s.playerZ
                            memoryTimer = 3.5f

                            role = when {
                                currentHp < 38f -> BotAiRole.TAKING_COVER
                                (bot.id + aiTickCounter / 25) % 3 == 0 && distToTarget > 24f -> BotAiRole.FLANKING
                                else -> BotAiRole.ENGAGING
                            }
                            if (role == BotAiRole.TAKING_COVER) {
                                isCrouching = true
                                val (covX, covZ) = IslandMapGenerator.findNearbyCoverPoint(bot.x, bot.z, s.playerX, s.playerZ, s.glooWalls)
                                goalX = covX
                                goalZ = covZ
                            } else if (role == BotAiRole.FLANKING) {
                                isCrouching = false
                                val flankSign = if (bot.id % 2 == 0) 1.0 else -1.0
                                val flankRad = atan2((s.playerZ - bot.z).toDouble(), (s.playerX - bot.x).toDouble()) + flankSign * (PI / 2.5)
                                goalX = (bot.x + cos(flankRad).toFloat() * 18f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                                goalZ = (bot.z + sin(flankRad).toFloat() * 18f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                            } else {
                                // Periodically reposition laterally during engagement so bots don't stand still
                                isCrouching = distToTarget > 40f && (bot.id + aiTickCounter / 20) % 2 == 0
                                if (hypot(bot.x - goalX, bot.z - goalZ) < 3.5f) {
                                    val strafeRad = atan2((s.playerZ - bot.z).toDouble(), (s.playerX - bot.x).toDouble()) +
                                        if ((aiTickCounter / 15 + bot.id) % 2 == 0) PI / 2.2 else -PI / 2.2
                                    goalX = (bot.x + cos(strafeRad).toFloat() * 9f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                                    goalZ = (bot.z + sin(strafeRad).toFloat() * 9f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                                }
                            }
                        } else {
                            // Check for other visible enemy/friendly bots with Line-of-Sight
                            val visibleOpponent = s.bots.firstOrNull { other ->
                                other.id != bot.id && other.squadId != bot.squadId && !other.isDead &&
                                    hypot(other.x - bot.x, other.z - bot.z) < 90f &&
                                    IslandMapGenerator.hasLineOfSight(bot.x, bot.z, other.x, other.z, s.glooWalls)
                            }
                            if (visibleOpponent != null && (ammoInMag > 0 || reserveAmmo > 0)) {
                                drivingVehId = null
                                targetEnemyX = visibleOpponent.x
                                targetEnemyZ = visibleOpponent.z
                                targetBotId = visibleOpponent.id
                                distToTarget = hypot(visibleOpponent.x - bot.x, visibleOpponent.z - bot.z)
                                lastKnownX = visibleOpponent.x
                                lastKnownZ = visibleOpponent.z
                                memoryTimer = 3.0f
                                role = if (currentHp < 38f) BotAiRole.TAKING_COVER else BotAiRole.ENGAGING
                                isCrouching = currentHp < 38f
                            } else if (knockedEnemySquadmate != null) {
                                role = BotAiRole.REVIVING_ALLY
                                drivingVehId = null
                                goalX = knockedEnemySquadmate.x
                                goalZ = knockedEnemySquadmate.z
                                if (hypot(knockedEnemySquadmate.x - bot.x, knockedEnemySquadmate.z - bot.z) < 3.6f) {
                                    revivedBotIds.add(knockedEnemySquadmate.id)
                                }
                            } else if (lastKnownX != null && lastKnownZ != null && memoryTimer > 0f) {
                                // Move toward last seen position (does NOT track hidden player movements!)
                                role = BotAiRole.PATROLLING
                                isCrouching = false
                                goalX = lastKnownX
                                goalZ = lastKnownZ
                            } else if (role != BotAiRole.LOOTING && hypot(bot.x - goalX, bot.z - goalZ) < 6.5f) {
                                // Explore named zones and open ground across the island
                                isCrouching = false
                                val zoneIdx = (bot.id + (s.matchElapsedSec / 25f).toInt()) % IslandMapGenerator.namedZones.size
                                val zone = IslandMapGenerator.namedZones[zoneIdx]
                                val offsetAngle = (bot.id * 1.7f + s.matchElapsedSec * 0.1f)
                                val desiredExploreX = (zone.x * 0.45f + bot.x * 0.55f + sin(offsetAngle) * 34f)
                                    .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 28f, IslandMapGenerator.PLAYABLE_LIMIT - 28f)
                                val desiredExploreZ = (zone.z * 0.45f + bot.z * 0.55f + cos(offsetAngle) * 34f)
                                    .coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 28f, IslandMapGenerator.PLAYABLE_LIMIT - 28f)
                                goalX = desiredExploreX
                                goalZ = desiredExploreZ
                                role = BotAiRole.PATROLLING
                            }
                        }
                    }
                }
            }

            // 5. Appropriate Vehicle Usage by AI (Friendly AI when rotating to Safe Zone or regrouping > 45m; Enemy AI when rotating > 65m)
            var currentBotX = bot.x
            var currentBotZ = bot.z
            val distToGoal = hypot(goalX - currentBotX, goalZ - currentBotZ)
            val shouldUseVehicle = if (bot.isFriendly) {
                role == BotAiRole.MOVING_TO_ZONE || distToGoal > 42f || distToPlayer > 48f
            } else {
                role == BotAiRole.MOVING_TO_ZONE || distToGoal > 68f
            }

            if (drivingVehId == null &&
                targetEnemyX == null &&
                healTimer <= 0f &&
                role != BotAiRole.REVIVING_ALLY &&
                shouldUseVehicle
            ) {
                val searchRadius = if (bot.isFriendly) 28f else 15f
                val freeVehicle = s.vehicles.firstOrNull { v ->
                    v.id !in occupiedVehicleIds &&
                        v.occupantId == null &&
                        hypot(v.x - currentBotX, v.z - currentBotZ) < searchRadius &&
                        hypot(v.x - s.playerX, v.z - s.playerZ) > 6.5f &&
                        !IslandMapGenerator.isPointInWater(v.x, v.z)
                }
                if (freeVehicle != null) {
                    val distToVeh = hypot(freeVehicle.x - currentBotX, freeVehicle.z - currentBotZ)
                    if (distToVeh <= 5.8f) {
                        drivingVehId = freeVehicle.id
                        occupiedVehicleIds.add(freeVehicle.id)
                        currentBotX = freeVehicle.x
                        currentBotZ = freeVehicle.z
                        if (bot.isFriendly && calloutCd <= 0f && newFriendlyCallout == null) {
                            newFriendlyCallout = FriendlyVoiceCallout(bot.name, "Driving ${freeVehicle.name} to rotate!")
                            calloutCd = 12f
                        }
                    } else {
                        goalX = freeVehicle.x
                        goalZ = freeVehicle.z
                    }
                }
            } else if (drivingVehId != null && (
                targetEnemyX != null ||
                    role == BotAiRole.REVIVING_ALLY ||
                    distToGoal < 14f ||
                    (bot.isFriendly && distToPlayer < 14f && role != BotAiRole.MOVING_TO_ZONE) ||
                    IslandMapGenerator.isPointInWater(currentBotX, currentBotZ)
                )
            ) {
                occupiedVehicleIds.remove(drivingVehId)
                val (exitX, exitZ) = IslandMapGenerator.findSafeVehicleExitPosition(
                    vehX = currentBotX,
                    vehZ = currentBotZ,
                    vehYawDeg = bot.yawDeg,
                    glooWalls = s.glooWalls,
                    vehicles = s.vehicles
                )
                currentBotX = exitX
                currentBotZ = exitZ
                drivingVehId = null
            }

            // 6. Navigate toward goal using multi-angle obstacle/bridge steering (No Walking Through Walls, No Teleporting!)
            val (waypointX, waypointZ) = IslandMapGenerator.computeAiWaypoint(
                botX = currentBotX,
                botZ = currentBotZ,
                goalX = goalX,
                goalZ = goalZ,
                glooWalls = s.glooWalls
            )
            val dx = waypointX - currentBotX
            val dz = waypointZ - currentBotZ
            val distToWaypoint = hypot(dx, dz)
            val botSpeed = when {
                drivingVehId != null -> 15.5f
                isCrouching -> 3.8f
                role == BotAiRole.MOVING_TO_ZONE || role == BotAiRole.REVIVING_ALLY || role == BotAiRole.FLANKING -> 7.6f
                else -> 5.4f
            }
            var nextX = currentBotX
            var nextZ = currentBotZ
            var nextYaw = bot.yawDeg
            var stuckFrames = bot.stuckFrames

            if (distToWaypoint > 1.6f && (!isBeingRevivedNow || !bot.isFriendly) && healTimer <= 0f) {
                val rawStepX = (dx / distToWaypoint) * botSpeed * dt
                val rawStepZ = (dz / distToWaypoint) * botSpeed * dt
                // Check if a low vaultable wall is directly ahead -> trigger AI Jump/Vault!
                val aheadVault = IslandMapGenerator.findCollidingBuilding(
                    x = currentBotX + (dx / distToWaypoint) * 2.0f,
                    z = currentBotZ + (dz / distToWaypoint) * 2.0f,
                    radius = 0.85f,
                    ignoreVaultable = false
                )
                if (aheadVault != null && aheadVault.isVaultable && jumpTimer <= 0f && drivingVehId == null) {
                    jumpTimer = 0.48f
                    isCrouching = false
                }

                val (resX, resZ) = if (drivingVehId != null) {
                    val (vResX, vResZ, _) = IslandMapGenerator.resolveVehicleMovement(
                        oldX = currentBotX,
                        oldZ = currentBotZ,
                        desiredX = currentBotX + rawStepX,
                        desiredZ = currentBotZ + rawStepZ,
                        glooWalls = s.glooWalls,
                        otherVehicles = s.vehicles,
                        ignoreVehicleId = drivingVehId
                    )
                    vResX to vResZ
                } else {
                    IslandMapGenerator.resolveMovement(
                        oldX = currentBotX,
                        oldZ = currentBotZ,
                        desiredX = currentBotX + rawStepX,
                        desiredZ = currentBotZ + rawStepZ,
                        isVaulting = jumpTimer > 0f,
                        glooWalls = s.glooWalls
                    )
                }
                val moved = hypot(resX - currentBotX, resZ - currentBotZ)
                if (moved < 0.008f) {
                    stuckFrames++
                } else {
                    stuckFrames = 0
                }

                if (stuckFrames >= 5) {
                    stuckFrames = 0
                    if (drivingVehId != null) {
                        occupiedVehicleIds.remove(drivingVehId)
                        val (safeVehX, safeVehZ) = IslandMapGenerator.recoverVehicleIfStuck(
                            x = currentBotX,
                            z = currentBotZ,
                            glooWalls = s.glooWalls,
                            otherVehicles = s.vehicles,
                            ignoreVehicleId = drivingVehId
                        )
                        vehiclePositionsUpdates[drivingVehId] = Triple(safeVehX, safeVehZ, nextYaw)
                        drivingVehId = null
                    }
                    // Scan 8 radial directions to find a clear unblocked path out of any building/corner and step smoothly (NEVER teleport!)
                    var chosenEscRad = Math.toRadians(((bot.yawDeg + 135f + (aiTickCounter % 8) * 45f) % 360f).toDouble())
                    for (probeIdx in 0 until 8) {
                        val candidateDeg = (bot.yawDeg + 90f + probeIdx * 45f + (bot.id % 4) * 22.5f) % 360f
                        val candidateRad = Math.toRadians(candidateDeg.toDouble())
                        val probeX = currentBotX + sin(candidateRad).toFloat() * 4.5f
                        val probeZ = currentBotZ + cos(candidateRad).toFloat() * 4.5f
                        if (!IslandMapGenerator.isPositionBlocked(probeX, probeZ, ignoreVaultable = false, glooWalls = s.glooWalls, radius = 0.85f) &&
                            !IslandMapGenerator.isPointInWater(probeX, probeZ)
                        ) {
                            chosenEscRad = candidateRad
                            break
                        }
                    }
                    val stepEscX = sin(chosenEscRad).toFloat() * botSpeed * dt
                    val stepEscZ = cos(chosenEscRad).toFloat() * botSpeed * dt
                    val (escResX, escResZ) = IslandMapGenerator.resolveMovement(
                        oldX = currentBotX,
                        oldZ = currentBotZ,
                        desiredX = currentBotX + stepEscX,
                        desiredZ = currentBotZ + stepEscZ,
                        isVaulting = true,
                        glooWalls = s.glooWalls
                    )
                    nextX = escResX
                    nextZ = escResZ
                    goalX = (currentBotX + sin(chosenEscRad).toFloat() * 20f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                    goalZ = (currentBotZ + cos(chosenEscRad).toFloat() * 20f).coerceIn(-IslandMapGenerator.PLAYABLE_LIMIT + 25f, IslandMapGenerator.PLAYABLE_LIMIT - 25f)
                } else {
                    // Strictly cap per-frame displacement so AI can never teleport
                    val maxAllowedDelta = botSpeed * dt * 1.25f
                    if (moved <= maxAllowedDelta) {
                        nextX = resX
                        nextZ = resZ
                    } else {
                        nextX = currentBotX + ((resX - currentBotX) / moved) * maxAllowedDelta
                        nextZ = currentBotZ + ((resZ - currentBotZ) / moved) * maxAllowedDelta
                    }
                }
                nextYaw = ((Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()) + 360f) % 360f
            }

            if (drivingVehId != null) {
                vehiclePositionsUpdates[drivingVehId] = Triple(nextX, nextZ, nextYaw)
            }

            // 7. AI Aiming, Firing, Finite Ammo Deduction & Balanced Accuracy (Never Shoots Through Walls or Instantly Headshots!)
            if (targetEnemyX != null && targetEnemyZ != null && reloadTimer <= 0f && healTimer <= 0f && drivingVehId == null) {
                nextYaw = ((Math.toDegrees(atan2((targetEnemyX - nextX).toDouble(), (targetEnemyZ - nextZ).toDouble())).toFloat()) + 360f) % 360f
                aimLockTimer += dt
                val hasClearShot = IslandMapGenerator.hasLineOfSight(nextX, nextZ, targetEnemyX, targetEnemyZ, s.glooWalls)
                if (hasClearShot && aimLockTimer >= 0.18f && fireCd <= 0f && ammoInMag > 0) {
                    ammoInMag = (ammoInMag - 1).coerceAtLeast(0)
                    val baseInterval = chosenWeapon.fireIntervalSec.coerceIn(0.18f, 1.15f)
                    fireCd = (baseInterval * 2.2f / adaptiveAiSkillFactor).coerceIn(0.32f, 1.10f)

                    val fdx = targetEnemyX - nextX
                    val fdz = targetEnemyZ - nextZ
                    val fDist = hypot(fdx, fdz).coerceAtLeast(1f)
                    val rayMax = IslandMapGenerator.computeRayObstacleDistance(
                        startX = nextX,
                        startZ = nextZ,
                        dirX = fdx / fDist,
                        dirZ = fdz / fDist,
                        maxRange = fDist,
                        glooWalls = s.glooWalls
                    )
                    val traceEndX = nextX + (fdx / fDist) * min(fDist, rayMax)
                    val traceEndZ = nextZ + (fdz / fDist) * min(fDist, rayMax)

                    newTraces.add(
                        BulletTrace(
                            id = nextDynamicEntityId++,
                            startX = nextX,
                            startY = bot.y + 1.3f,
                            startZ = nextZ,
                            endX = traceEndX,
                            endY = bot.y + 1.2f,
                            endZ = traceEndZ,
                            isFriendly = bot.isFriendly
                        )
                    )

                    if (rayMax >= fDist - 1.2f) {
                        if (targetIsPlayer) {
                            // Valid AI firing detection: temporarily reveals shooter on minimap for 3.5s
                            detectedMarkerRem = 3.5f
                            lastDetectedMs = nowMs
                            val angleToAttacker = ((Math.toDegrees(atan2((nextX - s.playerX).toDouble(), (nextZ - s.playerZ).toDouble())).toFloat()) + 360f) % 360f
                            val relAngle = normalizeAngleDiff(angleToAttacker - s.playerYawDeg)
                            newFireIndicators.removeAll { it.attackerId == bot.id }
                            newFireIndicators.add(
                                FireDirectionIndicator(
                                    id = nextDynamicEntityId++,
                                    attackerId = bot.id,
                                    angleRelToCameraDeg = relAngle,
                                    remainingSec = 1.6f
                                )
                            )
                            val distanceFactor = (1f - (distToTarget / 140f)).coerceIn(0.22f, 0.90f)
                            val movementEvasionFactor = when {
                                s.matchCharacter == CharacterId.SANI && s.abilityState == AbilityState.ACTIVE -> 0.48f
                                s.isProne -> 0.58f
                                s.isCrouching || s.isSprinting || s.isJumping -> 0.72f
                                else -> 1.0f
                            }
                            val hitChance = (0.34f * adaptiveAiSkillFactor * distanceFactor * movementEvasionFactor).coerceIn(0.10f, 0.44f)
                            if (Random.nextFloat() < hitChance) {
                                // Balanced body/limb damage (never instant headshots)
                                val dmg = (chosenWeapon.baseDamage * 0.28f).coerceIn(6.5f, 11.0f)
                                botDamageDealt += dmg
                                applyDamageToPlayer(damage = dmg, attackerBotId = bot.id)
                            }
                        } else if (targetBotId != null) {
                            if (bot.isFriendly) {
                                teammateMarkedEnemyIds.add(targetBotId)
                            }
                            val hitProb = if (bot.isFriendly) 0.48f else 0.36f
                            if (Random.nextFloat() < hitProb) {
                                val botShotDmg = (chosenWeapon.baseDamage * 0.55f).coerceIn(10f, 26f)
                                botDamageDealt += botShotDmg
                                pendingDamageOnBot[targetBotId] = (pendingDamageOnBot[targetBotId] ?: 0f) + botShotDmg
                                pendingAttackerForBot[targetBotId] = bot.id
                            }
                        }
                    }
                }
            } else {
                aimLockTimer = 0f
            }

            // Footstep indicator when bot moves within 46m of player (automatically disappears when bot stops)
            if (distToPlayer < 46f && hypot(nextX - bot.x, nextZ - bot.z) > 0.01f) {
                val angleToBot = ((Math.toDegrees(atan2((nextX - s.playerX).toDouble(), (nextZ - s.playerZ).toDouble())).toFloat()) + 360f) % 360f
                val relAngle = normalizeAngleDiff(angleToBot - s.playerYawDeg)
                newFootsteps.add(
                    FootstepIndicator(
                        id = bot.id,
                        angleRelToCameraDeg = relAngle,
                        isFriendly = bot.isFriendly,
                        intensity = if ( isCrouching ) 0.35f else if (botSpeed > 6.5f) 1.0f else 0.65f,
                        timestampMs = nowMs,
                        remainingSec = 0.8f
                    )
                )
            }

            val inWater = IslandMapGenerator.isPointInWater(nextX, nextZ)
            val groundY = IslandMapGenerator.getTerrainHeight(nextX, nextZ)
            val jumpHeight = if (jumpTimer > 0f) sin((jumpTimer / 0.48f) * PI).toFloat() * 1.35f else 0f
            val botAnim = when {
                inWater -> CharacterAnimState.SWIM
                reloadTimer > 0f -> CharacterAnimState.RELOAD
                jumpTimer > 0f -> CharacterAnimState.JUMP
                targetEnemyX != null && ammoInMag > 0 -> CharacterAnimState.SHOOT
                isCrouching -> CharacterAnimState.CROUCH
                distToWaypoint > 1.6f && botSpeed > 6.5f -> CharacterAnimState.SPRINT
                distToWaypoint > 1.6f -> CharacterAnimState.RUN
                else -> CharacterAnimState.IDLE
            }

            bot.copy(
                x = nextX,
                y = groundY + jumpHeight,
                z = nextZ,
                lastX = bot.x,
                lastZ = bot.z,
                yawDeg = nextYaw,
                hp = currentHp,
                armor = currentArmor,
                armorTier = currentArmorTier,
                helmetTier = currentHelmetTier,
                medkits = medkits,
                healTimerSec = healTimer,
                healCooldownSec = healCooldown,
                equippedWeapon = chosenWeapon,
                ammoInMag = ammoInMag,
                reserveAmmo = reserveAmmo,
                reloadTimerSec = reloadTimer,
                aimLockTimerSec = aimLockTimer,
                drivingVehicleId = drivingVehId,
                jumpTimerSec = jumpTimer,
                isCrouching = isCrouching,
                lastKnownTargetX = lastKnownX,
                lastKnownTargetZ = lastKnownZ,
                memoryTimerSec = memoryTimer,
                aiRole = role,
                targetX = goalX,
                targetZ = goalZ,
                fireCooldownSec = fireCd,
                calloutCooldownSec = calloutCd,
                detectedMarkerRemainingSec = detectedMarkerRem,
                teammateMarkedRemainingSec = teammateMarkedRem,
                lastDetectedByPlayerTimeMs = lastDetectedMs,
                markedByFriendlyUntilMs = markedUntil,
                stuckFrames = stuckFrames,
                kills = botKills,
                damageDealt = botDamageDealt,
                animState = botAnim,
                animPhase = (bot.animPhase + dt * 6f) % (2f * PI.toFloat())
            )
        }

        // Apply bot-vs-bot combat damage, teammate enemy markings & squadmate revives
        val eliminatedShooterCredit = HashMap<Int, Int>(4)
        val postCombatBots = updatedBots.map { rawBot ->
            val b = if (!rawBot.isFriendly && !rawBot.isDead && rawBot.id in teammateMarkedEnemyIds) {
                rawBot.copy(
                    teammateMarkedRemainingSec = 5.0f,
                    markedByFriendlyUntilMs = nowMs + 5000L
                )
            } else rawBot
            if (b.isDead) return@map b
            if (b.id in revivedBotIds && b.isKnocked) {
                return@map b.copy(
                    isKnocked = false,
                    knockedTimerSec = 25f,
                    hp = 50f,
                    animState = CharacterAnimState.REVIVE
                )
            }
            val incomingDmg = pendingDamageOnBot[b.id] ?: 0f
            if (incomingDmg <= 0f) return@map b

            val armorAbsorb = min(b.armor, incomingDmg * 0.35f)
            val nextArmor = (b.armor - armorAbsorb).coerceAtLeast(0f)
            val nextHp = b.hp - (incomingDmg - armorAbsorb * 0.5f)
            if (nextHp <= 0f) {
                val hasLivingSquadmate = s.selectedTeamMode != TeamMode.SOLO && (
                    (b.isFriendly && !s.isPlayerDead && !s.isPlayerKnocked) ||
                        updatedBots.any { other -> other.id != b.id && other.squadId == b.squadId && !other.isDead && !other.isKnocked }
                )
                if (!b.isKnocked && hasLivingSquadmate) {
                    b.copy(
                        hp = 45f,
                        armor = nextArmor,
                        isKnocked = true,
                        knockedTimerSec = 25f,
                        drivingVehicleId = null,
                        animState = CharacterAnimState.KNOCK
                    )
                } else {
                    pendingAttackerForBot[b.id]?.let { killerId ->
                        eliminatedShooterCredit[killerId] = (eliminatedShooterCredit[killerId] ?: 0) + 1
                    }
                    b.copy(
                        hp = 0f,
                        armor = 0f,
                        isDead = true,
                        isKnocked = false,
                        drivingVehicleId = null,
                        animState = CharacterAnimState.DEATH
                    )
                }
            } else {
                b.copy(hp = nextHp, armor = nextArmor)
            }
        }

        val finalBots = if (eliminatedShooterCredit.isEmpty()) {
            postCombatBots
        } else {
            postCombatBots.map { b ->
                val bonusKills = eliminatedShooterCredit[b.id] ?: 0
                if (bonusKills > 0) b.copy(kills = b.kills + bonusKills) else b
            }
        }

        val updatedLootItems = if (lootCollectedByBots.isEmpty()) {
            s.lootItems
        } else {
            s.lootItems.map { item ->
                if (item.id in lootCollectedByBots) item.copy(collected = true) else item
            }
        }

        val updatedVehiclesList = s.vehicles
            .distinctBy { it.id }
            .map { v ->
                val upd = vehiclePositionsUpdates[v.id]
                val driverBot = finalBots.firstOrNull { !it.isDead && !it.isKnocked && it.drivingVehicleId == v.id }
                val nextOccupant = when {
                    s.drivingVehicleId == v.id && !s.isPlayerDead && !s.isPlayerKnocked -> 0
                    driverBot != null -> driverBot.id
                    else -> null
                }
                if (upd != null) {
                    v.copy(x = upd.first, z = upd.second, yawDeg = upd.third, occupantId = nextOccupant)
                } else if (v.occupantId != nextOccupant) {
                    v.copy(occupantId = nextOccupant)
                } else {
                    v
                }
            }

        val canApplyBotRevive = playerRevivedByBot && s.isPlayerKnocked && !s.isPlayerDead
        if (canApplyBotRevive) {
            soundEngine.playPickup()
            showStatusBanner("REVIVED BY TEAMMATE!")
        }

        var playerKnockedTimer = s.playerKnockedTimerSec
        var triggerFullDeath = false
        if (s.isPlayerKnocked && !s.isPlayerDead && !canApplyBotRevive && !isBeingRevivedNow) {
            playerKnockedTimer -= dt
            if (playerKnockedTimer <= 0f) {
                triggerFullDeath = true
            }
        }

        val updatedGlooWalls = s.glooWalls.mapNotNull { gw ->
            val rem = gw.remainingSec - dt
            if (rem > 0f) gw.copy(remainingSec = rem) else null
        }

        _uiState.update { state ->
            val applyReviveNow = canApplyBotRevive && state.isPlayerKnocked && !state.isPlayerDead
            state.copy(
                bots = finalBots,
                lootItems = updatedLootItems,
                vehicles = updatedVehiclesList,
                glooWalls = updatedGlooWalls,
                bulletTraces = newTraces.takeLast(16),
                footstepIndicators = newFootsteps.take(8),
                fireDirectionIndicators = newFireIndicators.take(6),
                friendlyCallout = newFriendlyCallout,
                isBeingRevived = if (applyReviveNow || state.isPlayerDead) false else isBeingRevivedNow,
                reviveProgressSec = if (applyReviveNow || state.isPlayerDead) 0f else if (isBeingRevivedNow) reviveProgress else 0f,
                isPlayerKnocked = if (applyReviveNow || state.isPlayerDead) false else state.isPlayerKnocked,
                playerHp = if (applyReviveNow) 50f else state.playerHp,
                playerKnockedTimerSec = if (applyReviveNow) 20f else playerKnockedTimer,
                playerAnimState = if (applyReviveNow) CharacterAnimState.IDLE else state.playerAnimState
            )
        }

        if (triggerFullDeath) {
            handlePlayerFullDeath(_uiState.value.killerBotId)
        }
    }

    private fun stepMatchLifecycleAndSpectator(dt: Float) {
        val s = _uiState.value
        if (s.screenState != AppScreenState.IN_MATCH || matchFinishedForCurrentGame) return

        val livingEnemies = s.bots.count { !it.isFriendly && !it.isDead }
        val livingAllies = s.bots.count { it.isFriendly && !it.isDead }
        val playerAlive = if (s.isPlayerDead) 0 else 1
        val totalAlive = playerAlive + livingAllies + livingEnemies

        // Spectator Stability (Section 27):
        // Never attach camera to dead or invalid objects; automatically switch to a valid living target
        if (s.isPlayerDead) {
            if (livingEnemies == 0) {
                finishMatch(isVictory = livingAllies > 0)
                return
            }
            val validTargets = buildValidSpectatorTargets(s.bots, s.killerBotId)
            val resolved = resolveSpectatorSelection(
                validTargets = validTargets,
                currentTargetId = s.activeSpectatorTargetId,
                currentIndex = s.activeSpectatorTargetIndex
            )
            if (resolved != null) {
                val (safeIdx, activeTarget) = resolved
                val targetChanged = s.activeSpectatorTargetId != activeTarget.botId
                val lerpFactor = if (targetChanged) 1f else (dt * 8f).coerceIn(0.2f, 1f)
                val smoothX = s.playerX + (activeTarget.x - s.playerX) * lerpFactor
                val smoothY = s.playerY + (activeTarget.y - s.playerY) * lerpFactor
                val smoothZ = s.playerZ + (activeTarget.z - s.playerZ) * lerpFactor
                _uiState.update {
                    it.copy(
                        isSpectating = true,
                        spectatorTargets = validTargets,
                        activeSpectatorTargetIndex = safeIdx,
                        activeSpectatorTargetId = activeTarget.botId,
                        playerX = smoothX,
                        playerY = smoothY,
                        playerZ = smoothZ,
                        playerYawDeg = activeTarget.yawDeg,
                        aliveCount = totalAlive,
                        matchElapsedSec = it.matchElapsedSec + dt
                    )
                }
                if (targetChanged && s.activeSpectatorTargetId != null) {
                    showStatusBanner("SPECTATING ${activeTarget.name.uppercase()}")
                }
            } else {
                finishMatch(isVictory = false)
                return
            }
            return
        }

        if (livingEnemies == 0) {
            finishMatch(isVictory = true)
            return
        }

        _uiState.update {
            it.copy(
                aliveCount = totalAlive,
                matchElapsedSec = it.matchElapsedSec + dt
            )
        }
    }

    fun finishMatchFromSpectator() {
        val s = _uiState.value
        val livingEnemies = s.bots.count { !it.isFriendly && !it.isDead }
        val livingAllies = s.bots.count { it.isFriendly && !it.isDead }
        finishMatch(isVictory = livingEnemies == 0 && (!s.isPlayerDead || livingAllies > 0))
    }

    private fun finishMatch(isVictory: Boolean) {
        checkAndResetDailyMissionsIfNeeded()
        val s = _uiState.value
        if (matchFinishedForCurrentGame || s.screenState == AppScreenState.MATCH_RESULT || s.lastMatchResult != null) {
            return
        }
        matchFinishedForCurrentGame = true
        loadingScreenJob?.cancel()
        gameLoopJob?.cancel()
        matchStartInProgress = false
        moveInputX = 0f
        moveInputY = 0f

        val livingEnemyTeams = s.bots.filter { !it.isFriendly && !it.isDead }.map { it.squadId }.distinct().size
        val totalTeams = when (s.selectedTeamMode) {
            TeamMode.SOLO -> 24
            TeamMode.DUO -> 12
            TeamMode.SQUAD -> 6
        }
        val placement = if (isVictory) 1 else (1 + livingEnemyTeams).coerceIn(2, totalTeams)

        val survivalSec = s.matchElapsedSec.toInt().coerceAtLeast(1)
        val safeKills = s.playerKills.coerceAtLeast(0)
        val safeDamage = s.playerDamageDealt.toInt().coerceAtLeast(0)
        val xpFromPlay = 80
        val xpFromKills = (safeKills * 45).coerceAtLeast(0)
        val xpFromPlacement = if (isVictory) 220 else (140 - placement * 12).coerceAtLeast(20)
        val xpFromSurvival = (survivalSec / 4).coerceIn(0, 160)
        val matchPerformanceXp = (xpFromPlay + xpFromKills + xpFromPlacement + xpFromSurvival).coerceAtLeast(10)

        var missionBonusXp = 0
        val missionCosmeticIds = mutableSetOf<String>()
        val updatedMissions = s.dailyMissions.map { m ->
            val nextVal = when (m.id) {
                "kills_3" -> (m.currentValue + safeKills).coerceIn(0, m.targetValue)
                "survive_10m" -> (m.currentValue + survivalSec).coerceIn(0, m.targetValue)
                "complete_1" -> (m.currentValue + 1).coerceIn(0, m.targetValue)
                "loot_5" -> (m.currentValue + s.playerLootCollectedCount.coerceAtLeast(0)).coerceIn(0, m.targetValue)
                "top_5_placement" -> if (placement <= 5) m.targetValue else m.currentValue.coerceIn(0, m.targetValue)
                else -> m.currentValue.coerceIn(0, m.targetValue)
            }
            val nowCompleted = nextVal >= m.targetValue
            if (nowCompleted && !m.claimed) {
                missionBonusXp += m.xpReward.coerceAtLeast(0)
                m.cosmeticRewardId?.takeIf { it.isNotBlank() }?.let { missionCosmeticIds.add(it) }
                m.copy(currentValue = nextVal, claimed = true)
            } else {
                m.copy(currentValue = nextVal)
            }
        }
        val totalXpEarned = (matchPerformanceXp + missionBonusXp).coerceAtLeast(10)

        val teamStats = mutableListOf(
            TeamMemberStats(
                name = "YOU (${s.matchCharacter.displayName})",
                characterName = s.matchCharacter.displayName,
                kills = safeKills,
                damage = safeDamage,
                status = if (s.isPlayerDead) "Eliminated" else "Survived"
            )
        )
        s.bots.filter { it.isFriendly }.forEach { ally ->
            teamStats.add(
                TeamMemberStats(
                    name = ally.name,
                    characterName = ally.characterId.displayName,
                    kills = ally.kills.coerceAtLeast(0),
                    damage = ally.damageDealt.toInt().coerceAtLeast(0),
                    status = if (ally.isDead) "Eliminated" else "Survived"
                )
            )
        }

        val unlockedNames = awardXpAndSave(
            addedXp = totalXpEarned,
            updatedMissions = updatedMissions,
            extraUnlocked = missionCosmeticIds
        )

        val summary = MatchResultSummary(
            isVictory = isVictory,
            placement = placement,
            totalTeamsOrPlayers = totalTeams,
            kills = safeKills,
            damageDealt = safeDamage,
            survivalTimeSec = survivalSec,
            xpEarned = totalXpEarned,
            rewardsUnlocked = unlockedNames,
            teamPerformance = teamStats,
            headshots = s.playerHeadshots.coerceAtLeast(0),
            coinsCollected = s.playerCoinsCollectedTotal.coerceAtLeast(0),
            teammatesRevived = s.playerTeammatesRevived.coerceAtLeast(0)
        )

        _uiState.update {
            it.copy(
                screenState = AppScreenState.MATCH_RESULT,
                isSpectating = false,
                isManualFiringHeld = false,
                isInventoryOpen = false,
                lastMatchResult = summary
            )
        }
    }

    private fun awardXpAndSave(
        addedXp: Int,
        updatedMissions: List<DailyMission>,
        extraUnlocked: Set<String>
    ): List<String> {
        val safeAddedXp = addedXp.coerceAtLeast(0)
        val s = _uiState.value
        var level = s.playerLevel.coerceAtLeast(1)
        var xp = (s.playerXp.coerceAtLeast(0) + safeAddedXp).coerceAtLeast(0)
        val unlockedSet = s.unlockedCosmetics.toMutableSet()
        val newlyUnlockedNames = mutableListOf<String>()

        extraUnlocked.forEach { cosId ->
            if (unlockedSet.add(cosId)) {
                CosmeticCatalog.items.firstOrNull { it.id == cosId }?.let { cos ->
                    if (cos.name !in newlyUnlockedNames) {
                        newlyUnlockedNames.add(cos.name)
                    }
                }
            }
        }

        while (xp >= PlayerRepository.xpRequiredForLevel(level)) {
            xp -= PlayerRepository.xpRequiredForLevel(level)
            level++
            CosmeticCatalog.items.filter { it.unlockLevel <= level }.forEach { cos ->
                if (unlockedSet.add(cos.id)) {
                    if (cos.name !in newlyUnlockedNames) {
                        newlyUnlockedNames.add(cos.name)
                    }
                }
            }
        }

        _uiState.update {
            it.copy(
                playerLevel = level,
                playerXp = xp.coerceAtLeast(0),
                xpForNextLevel = PlayerRepository.xpRequiredForLevel(level),
                nextRewardLabel = PlayerRepository.nextCosmeticForLevel(level),
                unlockedCosmetics = unlockedSet,
                dailyMissions = updatedMissions
            )
        }
        persistCurrentProfile()
        return newlyUnlockedNames
    }

    private fun normalizeAngleDiff(diffDeg: Float): Float {
        var d = (diffDeg + 180f) % 360f
        if (d < 0f) d += 360f
        return d - 180f
    }

    companion object {
        fun provideFactory(repository: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BattleRoyaleViewModel(repository) as T
                }
            }
    }
}
