package com.example.model

import androidx.annotation.DrawableRes
import com.example.R

enum class CharacterId(
    val displayName: String,
    val roleTitle: String,
    val abilityName: String,
    val abilityDescription: String,
    val activeDurationSec: Float,
    val cooldownSec: Float,
    @DrawableRes val portraitRes: Int
) {
    SANI(
        displayName = "SANI",
        roleTitle = "Kinetic Sprinter",
        abilityName = "Kinetic Overdrive",
        abilityDescription = "Boosts running speed by +45% for 11 seconds. Cannot shoot while active. Cooldown: 30s.",
        activeDurationSec = 11f,
        cooldownSec = 30f,
        portraitRes = R.drawable.img_char_sani_1791221342729
    ),
    RIMA(
        displayName = "RIMA",
        roleTitle = "Recon Operative",
        abilityName = "Tactical Pulse",
        abilityDescription = "Temporarily reveals nearby enemies within 165m for 11 seconds. Indicators vanish when ability ends. Cooldown: 20s.",
        activeDurationSec = 11f,
        cooldownSec = 20f,
        portraitRes = R.drawable.img_char_rima_1791221352927
    )
}

enum class TeamMode(val label: String, val squadSize: Int, val friendlyBots: Int) {
    SOLO("Solo", 1, 0),
    DUO("Duo", 2, 1),
    SQUAD("Squad", 4, 3)
}

enum class CharacterAnimState {
    IDLE,
    WALK,
    RUN,
    SPRINT,
    JUMP,
    CROUCH,
    PRONE,
    VAULT,
    SWIM,
    AIM,
    SHOOT,
    RELOAD,
    KNOCK,
    DEATH,
    REVIVE,
    ABILITY
}

enum class AbilityState {
    READY,
    ACTIVE,
    COOLDOWN
}

enum class HitZone(val label: String, val damageMultiplier: Float) {
    MISS("Miss", 0f),
    HEAD("Headshot", 1.45f),
    BODY("Body Hit", 1.0f),
    LIMB("Limb Hit", 0.78f)
}

enum class WeaponCategory(
    val displayName: String,
    val isAutomatic: Boolean,
    val isSecondary: Boolean,
    val ammoType: AmmoType,
    val compatibleScopes: Set<ScopeType>,
    val maxRecoilClimbDeg: Float,
    val recoilRecoveryRateDegPerSec: Float
) {
    ASSAULT_RIFLE(
        "Assault Rifle",
        isAutomatic = true,
        isSecondary = false,
        ammoType = AmmoType.AR_AMMO,
        compatibleScopes = setOf(ScopeType.NONE, ScopeType.RED_DOT, ScopeType.SCOPE_2X, ScopeType.SCOPE_4X),
        maxRecoilClimbDeg = 6.2f,
        recoilRecoveryRateDegPerSec = 5.2f
    ),
    SMG(
        "SMG",
        isAutomatic = true,
        isSecondary = false,
        ammoType = AmmoType.SMG_AMMO,
        compatibleScopes = setOf(ScopeType.NONE, ScopeType.RED_DOT, ScopeType.SCOPE_2X),
        maxRecoilClimbDeg = 4.8f,
        recoilRecoveryRateDegPerSec = 6.0f
    ),
    SHOTGUN(
        "Shotgun",
        isAutomatic = false,
        isSecondary = false,
        ammoType = AmmoType.SG_AMMO,
        compatibleScopes = setOf(ScopeType.NONE, ScopeType.RED_DOT),
        maxRecoilClimbDeg = 5.5f,
        recoilRecoveryRateDegPerSec = 6.5f
    ),
    DMR(
        "Semi-Auto Rifle",
        isAutomatic = false,
        isSecondary = false,
        ammoType = AmmoType.AR_AMMO,
        compatibleScopes = setOf(ScopeType.NONE, ScopeType.RED_DOT, ScopeType.SCOPE_2X, ScopeType.SCOPE_4X),
        maxRecoilClimbDeg = 5.8f,
        recoilRecoveryRateDegPerSec = 5.8f
    ),
    SNIPER(
        "Sniper Rifle",
        isAutomatic = false,
        isSecondary = false,
        ammoType = AmmoType.SNIPER_AMMO,
        compatibleScopes = setOf(ScopeType.NONE, ScopeType.RED_DOT, ScopeType.SCOPE_2X, ScopeType.SCOPE_4X, ScopeType.SNIPER_8X),
        maxRecoilClimbDeg = 6.5f,
        recoilRecoveryRateDegPerSec = 5.0f
    ),
    SECONDARY(
        "Secondary Pistol",
        isAutomatic = false,
        isSecondary = true,
        ammoType = AmmoType.SMG_AMMO,
        compatibleScopes = setOf(ScopeType.NONE, ScopeType.RED_DOT),
        maxRecoilClimbDeg = 3.8f,
        recoilRecoveryRateDegPerSec = 6.8f
    )
}

enum class AmmoType(val label: String, val unitWeight: Int, val defaultPickupCount: Int) {
    AR_AMMO("5.56 AR Ammo", 1, 30),
    SMG_AMMO("9mm SMG Ammo", 1, 35),
    SG_AMMO("12G Shells", 2, 12),
    SNIPER_AMMO(".308 Sniper Ammo", 3, 10)
}

enum class ScopeType(val label: String, val zoomFactor: Float, val fovMultiplier: Float) {
    NONE("Iron Sight", 1.0f, 1.0f),
    RED_DOT("Red Dot", 1.35f, 0.82f),
    SCOPE_2X("2x Scope", 2.0f, 0.62f),
    SCOPE_4X("4x Scope", 4.0f, 0.42f),
    SNIPER_8X("Sniper Scope", 6.5f, 0.28f)
}

enum class AttachmentType(val label: String) {
    MUZZLE_COMPENSATOR("Tactical Muzzle"),
    EXTENDED_MAG("Extended Mag"),
    FOREGRIP("Stabilizer Grip")
}

data class WeaponSpec(
    val id: String,
    val name: String,
    val category: WeaponCategory,
    val baseDamage: Float,
    val fireIntervalSec: Float,
    val magazineSize: Int,
    val reloadTimeSec: Float,
    val effectiveRange: Float,
    val recoilVertical: Float,
    val recoilHorizontal: Float,
    val projectileSpeed: Float,
    val pellets: Int = 1
) {
    val roundsPerMinute: Int
        get() = (60f / fireIntervalSec.coerceAtLeast(0.04f)).toInt()
}

object OriginalWeaponCatalog {
    val VX47_STRIKER = WeaponSpec(
        id = "vx47_striker",
        name = "VX-47 Striker",
        category = WeaponCategory.ASSAULT_RIFLE,
        baseDamage = 26f,
        fireIntervalSec = 0.11f,
        magazineSize = 30,
        reloadTimeSec = 2.1f,
        effectiveRange = 175f,
        recoilVertical = 1.15f,
        recoilHorizontal = 0.35f,
        projectileSpeed = 260f
    )
    val KR556_PHANTOM = WeaponSpec(
        id = "kr556_phantom",
        name = "KR-556 Phantom",
        category = WeaponCategory.ASSAULT_RIFLE,
        baseDamage = 23f,
        fireIntervalSec = 0.095f,
        magazineSize = 32,
        reloadTimeSec = 1.95f,
        effectiveRange = 185f,
        recoilVertical = 0.92f,
        recoilHorizontal = 0.28f,
        projectileSpeed = 280f
    )
    val MP9_VIPER = WeaponSpec(
        id = "mp9_viper",
        name = "MP-9 Viper",
        category = WeaponCategory.SMG,
        baseDamage = 18f,
        fireIntervalSec = 0.075f,
        magazineSize = 35,
        reloadTimeSec = 1.6f,
        effectiveRange = 110f,
        recoilVertical = 0.78f,
        recoilHorizontal = 0.32f,
        projectileSpeed = 220f
    )
    val SG12_BREACHER = WeaponSpec(
        id = "sg12_breacher",
        name = "SG-12 Breacher",
        category = WeaponCategory.SHOTGUN,
        baseDamage = 14f,
        fireIntervalSec = 0.55f,
        magazineSize = 8,
        reloadTimeSec = 2.4f,
        effectiveRange = 55f,
        recoilVertical = 2.4f,
        recoilHorizontal = 0.45f,
        projectileSpeed = 190f,
        pellets = 6
    )
    val MK14_LYNX = WeaponSpec(
        id = "mk14_lynx",
        name = "MK-14 Lynx",
        category = WeaponCategory.DMR,
        baseDamage = 44f,
        fireIntervalSec = 0.28f,
        magazineSize = 15,
        reloadTimeSec = 2.2f,
        effectiveRange = 230f,
        recoilVertical = 1.55f,
        recoilHorizontal = 0.25f,
        projectileSpeed = 320f
    )
    val SR90_TITAN = WeaponSpec(
        id = "sr90_titan",
        name = "SR-90 Titan",
        category = WeaponCategory.SNIPER,
        baseDamage = 84f,
        fireIntervalSec = 0.95f,
        magazineSize = 5,
        reloadTimeSec = 2.7f,
        effectiveRange = 310f,
        recoilVertical = 3.1f,
        recoilHorizontal = 0.20f,
        projectileSpeed = 390f
    )
    val P18_TALON = WeaponSpec(
        id = "p18_talon",
        name = "P-18 Talon",
        category = WeaponCategory.SECONDARY,
        baseDamage = 21f,
        fireIntervalSec = 0.20f,
        magazineSize = 15,
        reloadTimeSec = 1.4f,
        effectiveRange = 95f,
        recoilVertical = 0.68f,
        recoilHorizontal = 0.18f,
        projectileSpeed = 210f
    )

    val allWeapons = listOf(
        VX47_STRIKER,
        KR556_PHANTOM,
        MP9_VIPER,
        SG12_BREACHER,
        MK14_LYNX,
        SR90_TITAN,
        P18_TALON
    )

    fun byId(id: String): WeaponSpec = allWeapons.firstOrNull { it.id == id } ?: VX47_STRIKER

    fun selectBestWeaponForDistance(distanceMeters: Float): WeaponSpec = when {
        distanceMeters < 26f -> MP9_VIPER
        distanceMeters < 90f -> VX47_STRIKER
        else -> MK14_LYNX
    }
}

data class WeaponInstance(
    val spec: WeaponSpec,
    val currentAmmo: Int = spec.magazineSize,
    val equippedScope: ScopeType = if (spec.category == WeaponCategory.SNIPER) ScopeType.SNIPER_8X else ScopeType.NONE,
    val hasMuzzle: Boolean = false,
    val hasExtendedMag: Boolean = false,
    val hasForegrip: Boolean = false
) {
    val maxMagazineSize: Int
        get() = if (hasExtendedMag) (spec.magazineSize * 1.35f).toInt() else spec.magazineSize

    val effectiveReloadSec: Float
        get() = if (hasExtendedMag) spec.reloadTimeSec * 0.88f else spec.reloadTimeSec

    val effectiveRecoil: Float
        get() = spec.recoilVertical * (if (hasMuzzle) 0.82f else 1f) * (if (hasForegrip) 0.80f else 1f)

    val effectiveHorizontalRecoil: Float
        get() = spec.recoilHorizontal * (if (hasMuzzle) 0.85f else 1f) * (if (hasForegrip) 0.78f else 1f)

    fun sanitized(): WeaponInstance {
        val validScope = if (equippedScope in spec.category.compatibleScopes) {
            equippedScope
        } else {
            if (spec.category == WeaponCategory.SNIPER) ScopeType.SNIPER_8X else ScopeType.NONE
        }
        return copy(
            currentAmmo = currentAmmo.coerceIn(0, maxMagazineSize),
            equippedScope = validScope
        )
    }
}

enum class WeaponSlotIndex(val label: String) {
    MAIN_1("Main 1"),
    MAIN_2("Main 2"),
    SECONDARY("Pistol")
}

enum class LootCategory {
    GUN,
    AMMO,
    ARMOR,
    HELMET,
    BACKPACK,
    SCOPE,
    ATTACHMENT,
    MEDKIT,
    GRENADE,
    GLOO_WALL,
    COIN,
    REPAIR_KIT
}

data class WorldLootItem(
    val id: Int,
    val x: Float,
    val z: Float,
    val y: Float,
    val category: LootCategory,
    val name: String,
    val weaponSpec: WeaponSpec? = null,
    val ammoType: AmmoType? = null,
    val tier: Int = 1,
    val scopeType: ScopeType? = null,
    val attachmentType: AttachmentType? = null,
    val amount: Int = 1,
    val collected: Boolean = false,
    val pickupCooldownUntilMs: Long = 0L
)

enum class BuildingType {
    HOUSE,
    TECH_TOWER,
    WAREHOUSE,
    WATCHTOWER,
    BRIDGE_DECK,
    LOW_WALL
}

data class MapBuilding(
    val id: Int,
    val name: String,
    val type: BuildingType,
    val centerX: Float,
    val centerZ: Float,
    val width: Float,
    val depth: Float,
    val height: Float,
    val floorY: Float = 0f,
    val isSolidCollider: Boolean = true,
    val isVaultable: Boolean = false,
    val colorHex: Long = 0xFF334155
)

data class MapBillboard(
    val id: Int,
    val x: Float,
    val z: Float,
    val yawDeg: Float,
    val width: Float = 14f,
    val height: Float = 8f,
    val isLargeBanner: Boolean = false
)

data class VendingMachineNode(
    val id: Int,
    val zoneName: String,
    val x: Float,
    val z: Float,
    val coinCost: Int = 300,
    val maxRespawns: Int = 2,
    val respawnsUsed: Int = 0
) {
    val remainingRespawns: Int get() = (maxRespawns - respawnsUsed).coerceAtLeast(0)
}

data class MapVehicle(
    val id: Int,
    val name: String,
    val x: Float,
    val z: Float,
    val yawDeg: Float,
    val maxSpeed: Float = 26f,
    val hp: Float = 400f,
    val occupantId: Int? = null
)

data class MapTree(
    val id: Int,
    val x: Float,
    val z: Float,
    val radius: Float = 1.0f,
    val height: Float = 7.5f
)

data class MapRock(
    val id: Int,
    val x: Float,
    val z: Float,
    val radius: Float = 1.6f,
    val height: Float = 2.1f
)

data class deployedGlooWall(
    val id: Int,
    val x: Float,
    val z: Float,
    val yawDeg: Float,
    val width: Float = 5.4f,
    val hp: Float = 220f,
    val remainingSec: Float = 45f
)

data class SafeZoneState(
    val phase: Int = 1,
    val centerX: Float = 0f,
    val centerZ: Float = 0f,
    val currentRadius: Float = 510f,
    val targetCenterX: Float = 0f,
    val targetCenterZ: Float = 0f,
    val targetRadius: Float = 350f,
    val isShrinking: Boolean = false,
    val warningTimerSec: Float = 30f,
    val shrinkTimerSec: Float = 0f,
    val isFinalZoneReached: Boolean = false,
    val isPlayerOutside: Boolean = false,
    val damagePerSecond: Float = 2.5f,
    val warningMessage: String = "Phase 1 - Safe Zone Shrink in 30s"
) {
    val activeCountdownSec: Int
        get() = when {
            isFinalZoneReached -> 0
            isShrinking -> kotlin.math.ceil(shrinkTimerSec.coerceAtLeast(0f)).toInt()
            else -> kotlin.math.ceil(warningTimerSec.coerceAtLeast(0f)).toInt()
        }

    val formattedCountdown: String
        get() {
            val totalSec = activeCountdownSec.coerceAtLeast(0)
            val mins = totalSec / 60
            val secs = totalSec % 60
            return "%02d:%02d".format(mins, secs)
        }
}

enum class CompassDirection(val label: String, val centerDeg: Float) {
    N("N", 0f),
    NE("NE", 45f),
    E("E", 90f),
    SE("SE", 135f),
    S("S", 180f),
    SW("SW", 225f),
    W("W", 270f),
    NW("NW", 315f);

    companion object {
        fun normalizeYaw(yawDeg: Float): Float {
            if (yawDeg.isNaN() || yawDeg.isInfinite()) return 0f
            return ((yawDeg % 360f) + 360f) % 360f
        }

        fun fromCameraYaw(yawDeg: Float): CompassDirection {
            val norm = normalizeYaw(yawDeg)
            return when {
                norm >= 337.5f || norm < 22.5f -> N
                norm < 67.5f -> NE
                norm < 112.5f -> E
                norm < 157.5f -> SE
                norm < 202.5f -> S
                norm < 247.5f -> SW
                norm < 292.5f -> W
                else -> NW
            }
        }
    }
}

enum class BotAiRole {
    LOOTING,
    PATROLLING,
    ENGAGING,
    TAKING_COVER,
    FLANKING,
    RETREATING,
    HEALING,
    MOVING_TO_ZONE,
    REVIVING_ALLY
}

data class CombatantBot(
    val id: Int,
    val name: String,
    val squadId: Int,
    val isFriendly: Boolean,
    val characterId: CharacterId,
    val x: Float,
    val y: Float,
    val z: Float,
    val yawDeg: Float,
    val hp: Float = 100f,
    val maxHp: Float = 100f,
    val armor: Float = 50f,
    val maxArmor: Float = 100f,
    val helmetTier: Int = 1,
    val armorTier: Int = 1,
    val isKnocked: Boolean = false,
    val knockedTimerSec: Float = 25f,
    val isDead: Boolean = false,
    val animState: CharacterAnimState = CharacterAnimState.IDLE,
    val animPhase: Float = 0f,
    val equippedWeapon: WeaponSpec = OriginalWeaponCatalog.VX47_STRIKER,
    val ammoInMag: Int = 30,
    val reserveAmmo: Int = 90,
    val reloadTimerSec: Float = 0f,
    val medkits: Int = 2,
    val healTimerSec: Float = 0f,
    val healCooldownSec: Float = 0f,
    val aimLockTimerSec: Float = 0f,
    val drivingVehicleId: Int? = null,
    val jumpTimerSec: Float = 0f,
    val isCrouching: Boolean = false,
    val lastKnownTargetX: Float? = null,
    val lastKnownTargetZ: Float? = null,
    val memoryTimerSec: Float = 0f,
    val aiRole: BotAiRole = BotAiRole.LOOTING,
    val targetX: Float = 0f,
    val targetZ: Float = 0f,
    val fireCooldownSec: Float = 0f,
    val calloutCooldownSec: Float = 0f,
    val detectedMarkerRemainingSec: Float = 0f,
    val teammateMarkedRemainingSec: Float = 0f,
    val lastDetectedByPlayerTimeMs: Long = 0L,
    val markedByFriendlyUntilMs: Long = 0L,
    val stuckFrames: Int = 0,
    val lastX: Float = x,
    val lastZ: Float = z,
    val kills: Int = 0,
    val damageDealt: Float = 0f
)

data class FootstepIndicator(
    val id: Int,
    val angleRelToCameraDeg: Float,
    val isFriendly: Boolean,
    val intensity: Float, // 0.3 crouch, 0.6 walk, 1.0 sprint
    val timestampMs: Long,
    val remainingSec: Float = 0.9f
)

data class FireDirectionIndicator(
    val id: Int,
    val attackerId: Int,
    val angleRelToCameraDeg: Float, // -45..45 Front, 45..135 Right, -135..-45 Left, else Rear
    val remainingSec: Float = 1.6f
)

data class BulletTrace(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val startZ: Float,
    val endX: Float,
    val endY: Float,
    val endZ: Float,
    val isFriendly: Boolean,
    val remainingSec: Float = 0.14f
)

data class HitImpactEffect(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val damage: Int,
    val hitZone: HitZone,
    val remainingSec: Float = 0.55f
)

data class FriendlyVoiceCallout(
    val botName: String,
    val message: String,
    val remainingSec: Float = 3.2f
)

enum class GraphicsQuality(val label: String, val drawDistance: Float, val maxTreesRendered: Int, val particleScale: Float) {
    LOW("Low (Vivo Y03 Optimized)", 180f, 22, 0.4f),
    MEDIUM("Medium", 260f, 40, 0.75f),
    HIGH("High", 360f, 65, 1.0f)
}

enum class FpsTargetMode(val label: String, val targetFps: Int) {
    FPS_30("30 FPS", 30),
    FPS_45("45 FPS", 45),
    FPS_60("60 FPS", 60),
    AUTO_FPS("Auto FPS (Stable)", 45)
}

enum class GyroscopeMode(val label: String) {
    OFF("OFF"),
    ALWAYS_ON("Always ON"),
    SCOPE_ON("Scope Only")
}

data class SensitivitySettings(
    val general: Float = 65f,
    val redDot: Float = 60f,
    val scope2x: Float = 52f,
    val scope4x: Float = 44f,
    val sniper: Float = 35f,
    val freeLook: Float = 70f,
    val gyroMode: GyroscopeMode = GyroscopeMode.OFF,
    val gyroRedDot: Float = 55f,
    val gyro2x: Float = 48f,
    val gyro4x: Float = 40f,
    val gyroSniper: Float = 30f
) {
    private fun clean(v: Float, defaultVal: Float): Float =
        if (v.isNaN() || v.isInfinite()) defaultVal else v.coerceIn(10f, 100f)

    fun sanitized(): SensitivitySettings = copy(
        general = clean(general, 65f),
        redDot = clean(redDot, 60f),
        scope2x = clean(scope2x, 52f),
        scope4x = clean(scope4x, 44f),
        sniper = clean(sniper, 35f),
        freeLook = clean(freeLook, 70f),
        gyroRedDot = clean(gyroRedDot, 55f),
        gyro2x = clean(gyro2x, 48f),
        gyro4x = clean(gyro4x, 40f),
        gyroSniper = clean(gyroSniper, 30f)
    )
}

data class AutoLootSettings(
    val enabled: Boolean = true,
    val guns: Boolean = true,
    val ammo: Boolean = true,
    val armor: Boolean = true,
    val helmet: Boolean = true,
    val attachments: Boolean = true,
    val healing: Boolean = true,
    val grenades: Boolean = true,
    val glooWall: Boolean = true,
    val backpack: Boolean = true,
    val otherItems: Boolean = true
)

enum class HudControlId(
    val displayName: String,
    val defaultNormX: Float,
    val defaultNormY: Float,
    val defaultSizeScale: Float = 1.0f,
    val defaultAlpha: Float = 0.92f
) {
    // Left side: Movement Joystick, Minimap, Backpack, Quick Healing/Cover, HP & Armor
    MINIMAP("Mini-Map", 0.14f, 0.11f, 1.0f, 0.92f),
    INVENTORY_BUTTON("Backpack", 0.09f, 0.34f, 0.95f, 0.92f),
    MEDKIT_BUTTON("Medkit", 0.23f, 0.34f, 0.95f, 0.92f),
    GLOO_BUTTON("Gloo Wall", 0.37f, 0.34f, 0.95f, 0.92f),
    JOYSTICK("Joystick", 0.18f, 0.72f, 1.05f, 0.88f),
    HP_ARMOR_BAR("HP & Armor Status", 0.25f, 0.94f, 1.0f, 0.95f),

    // Top Right/Center: Compass & Match Stats
    COMPASS("Compass", 0.68f, 0.06f, 1.0f, 0.92f),

    // Right side: All Combat Controls (Fire, Aim, Scope, Reload, Weapon Switch, Jump, Crouch, Prone, Ability)
    WEAPON_BAR("Weapon Switch", 0.74f, 0.18f, 1.0f, 0.94f),
    SCOPE_BUTTON("Scope", 0.66f, 0.36f, 0.95f, 0.92f),
    AIM_BUTTON("Aim", 0.88f, 0.36f, 0.95f, 0.92f),
    ABILITY_BUTTON("Ability", 0.62f, 0.53f, 1.0f, 0.95f),
    JUMP_BUTTON("Jump", 0.90f, 0.53f, 0.95f, 0.92f),
    RELOAD_BUTTON("Reload", 0.60f, 0.72f, 0.95f, 0.92f),
    FIRE_BUTTON("Fire", 0.83f, 0.72f, 1.10f, 0.96f),
    PRONE_BUTTON("Prone", 0.66f, 0.91f, 0.92f, 0.92f),
    CROUCH_BUTTON("Crouch", 0.88f, 0.91f, 0.92f, 0.92f);

    val isRightSideCombatControl: Boolean
        get() = this in RIGHT_SIDE_COMBAT_CONTROLS

    companion object {
        val RIGHT_SIDE_COMBAT_CONTROLS = setOf(
            FIRE_BUTTON,
            AIM_BUTTON,
            SCOPE_BUTTON,
            RELOAD_BUTTON,
            WEAPON_BAR,
            JUMP_BUTTON,
            CROUCH_BUTTON,
            PRONE_BUTTON,
            ABILITY_BUTTON
        )
    }
}

data class HudControlConfig(
    val controlId: HudControlId,
    val normX: Float = controlId.defaultNormX,
    val normY: Float = controlId.defaultNormY,
    val sizeScale: Float = controlId.defaultSizeScale,
    val alpha: Float = controlId.defaultAlpha
) {
    private fun finiteOr(v: Float, fallback: Float): Float =
        if (v.isNaN() || v.isInfinite()) fallback else v

    fun clamped(): HudControlConfig {
        val minX = if (controlId.isRightSideCombatControl) 0.52f else 0.06f
        val maxX = if (controlId == HudControlId.JOYSTICK) 0.45f else 0.94f
        return copy(
            normX = finiteOr(normX, controlId.defaultNormX).coerceIn(minX, maxX),
            normY = finiteOr(normY, controlId.defaultNormY).coerceIn(0.05f, 0.95f),
            sizeScale = finiteOr(sizeScale, controlId.defaultSizeScale).coerceIn(0.75f, 1.35f),
            alpha = finiteOr(alpha, controlId.defaultAlpha).coerceIn(0.30f, 1.0f)
        )
    }
}

data class HudLayoutPreset(
    val slotIndex: Int, // 0, 1, 2 (max 3 layouts)
    val name: String,
    val controls: Map<HudControlId, HudControlConfig>
) {
    companion object {
        fun defaultPreset(slotIndex: Int): HudLayoutPreset {
            val map = HudControlId.entries.associateWith { id ->
                HudControlConfig(controlId = id)
            }
            return HudLayoutPreset(
                slotIndex = slotIndex.coerceIn(0, 2),
                name = "Layout ${(slotIndex.coerceIn(0, 2)) + 1}",
                controls = map
            )
        }
    }
}

data class DailyMission(
    val id: String,
    val title: String,
    val targetValue: Int,
    val currentValue: Int,
    val xpReward: Int,
    val cosmeticRewardId: String? = null,
    val cosmeticRewardName: String? = null,
    val claimed: Boolean = false
) {
    val isCompleted: Boolean get() = currentValue >= targetValue
}

enum class CosmeticCategory(val label: String) {
    CHARACTER("Character"),
    OUTFIT("Outfit"),
    WEAPON_SKIN("Weapon Skin"),
    BACKPACK_SKIN("Backpack Cosmetic"),
    HELMET_SKIN("Helmet Cosmetic")
}

data class CosmeticItem(
    val id: String,
    val name: String,
    val category: CosmeticCategory,
    val unlockLevel: Int,
    val description: String,
    val accentColorHex: Long
)

object CosmeticCatalog {
    val items = listOf(
        CosmeticItem("char_sani_default", "SANI - Varsity Operative", CosmeticCategory.CHARACTER, 1, "Default SANI BR-A1 Operative", 0xFF00E5FF),
        CosmeticItem("char_rima_default", "RIMA - Techwear Scout", CosmeticCategory.CHARACTER, 1, "Free Selectable RIMA BR-A1 Operative", 0xFFFF4081),
        CosmeticItem("outfit_sani_cyber", "SANI - Cyber Visor Jersey", CosmeticCategory.OUTFIT, 2, "Electric Blue SANI Banner Edition Jersey", 0xFF2979FF),
        CosmeticItem("outfit_rima_neon", "RIMA - Neon Pink Tactical", CosmeticCategory.OUTFIT, 3, "High-contrast Emerald & Pink Techwear", 0xFFFF80AB),
        CosmeticItem("skin_vx47_cobalt", "VX-47 - Cobalt Lightning", CosmeticCategory.WEAPON_SKIN, 4, "Cosmetic anodized blue finish for VX-47 Striker", 0xFF00B0FF),
        CosmeticItem("skin_bag_aegis", "Aegis Carbon Pack", CosmeticCategory.BACKPACK_SKIN, 5, "Tactical carbon-weave backpack cosmetic", 0xFF00E676),
        CosmeticItem("skin_helm_visor", "SANI Visor Mk-II Helmet", CosmeticCategory.HELMET_SKIN, 6, "Glowing cyan tactical helmet cosmetic", 0xFF18FFFF),
        CosmeticItem("skin_sr90_aurora", "SR-90 - Aurora Pulse", CosmeticCategory.WEAPON_SKIN, 8, "Cosmetic pulse finish for SR-90 Titan", 0xFFE040FB)
    )
}

data class MatchResultSummary(
    val isVictory: Boolean,
    val placement: Int,
    val totalTeamsOrPlayers: Int,
    val kills: Int,
    val damageDealt: Int,
    val survivalTimeSec: Int,
    val xpEarned: Int,
    val rewardsUnlocked: List<String>,
    val teamPerformance: List<TeamMemberStats>,
    val headshots: Int,
    val coinsCollected: Int,
    val teammatesRevived: Int
)

data class TeamMemberStats(
    val name: String,
    val characterName: String,
    val kills: Int,
    val damage: Int,
    val status: String
)
