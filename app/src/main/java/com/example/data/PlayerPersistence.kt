package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.AutoLootSettings
import com.example.model.CharacterId
import com.example.model.CosmeticCatalog
import com.example.model.DailyMission
import com.example.model.FpsTargetMode
import com.example.model.GraphicsQuality
import com.example.model.GyroscopeMode
import com.example.model.HudControlConfig
import com.example.model.HudControlId
import com.example.model.HudLayoutPreset
import com.example.model.SensitivitySettings
import com.example.model.TeamMode
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "player_profile")
data class PlayerProfileEntity(
    @PrimaryKey val id: Int = 1,
    val selectedCharacter: String = CharacterId.SANI.name,
    val selectedTeamMode: String = TeamMode.SQUAD.name,
    val level: Int = 1,
    val xp: Int = 0,
    val unlockedCosmeticsCsv: String = "char_sani_default,char_rima_default",
    val equippedOutfitId: String = "char_sani_default",
    val equippedWeaponSkinId: String = "skin_vx47_cobalt",
    val activeHudSlot: Int = 0,
    val hudLayoutsJson: String = "",
    val graphicsQuality: String = GraphicsQuality.LOW.name,
    val fpsTargetMode: String = FpsTargetMode.AUTO_FPS.name,
    val aimAssistEnabled: Boolean = true,
    val sensitivityJson: String = "",
    val autoLootJson: String = "",
    val dailyMissionsJson: String = "",
    val lastMissionResetDay: Long = 0L
)

@Dao
interface PlayerProfileDao {
    @Query("SELECT * FROM player_profile WHERE id = 1")
    fun observeProfile(): Flow<PlayerProfileEntity?>

    @Query("SELECT * FROM player_profile WHERE id = 1")
    suspend fun getProfileOnce(): PlayerProfileEntity?

    @Query("SELECT * FROM player_profile WHERE id = 1")
    fun getProfileSync(): PlayerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: PlayerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveProfileSync(profile: PlayerProfileEntity)
}

@Database(entities = [PlayerProfileEntity::class], version = 1, exportSchema = false)
abstract class SaniDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: SaniDatabase? = null

        fun getInstance(context: Context): SaniDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SaniDatabase::class.java,
                    "sani_battle_royale.db"
                )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class PlayerRepository(private val dao: PlayerProfileDao) {
    val profileFlow: Flow<PlayerProfileEntity?> = dao.observeProfile()

    @Volatile
    private var cachedProfile: PlayerProfileEntity? = null

    fun getOrInitProfileSync(nowMs: Long = System.currentTimeMillis()): PlayerProfileEntity {
        val currentDay = (nowMs.coerceAtLeast(0L)) / TWENTY_FOUR_HOURS_MS
        val existing = try {
            dao.getProfileSync()
        } catch (_: Exception) {
            cachedProfile
        }
        if (existing == null) {
            val initial = PlayerProfileEntity(
                id = 1,
                level = 1,
                xp = 0,
                hudLayoutsJson = encodeHudLayouts(
                    listOf(
                        HudLayoutPreset.defaultPreset(0),
                        HudLayoutPreset.defaultPreset(1),
                        HudLayoutPreset.defaultPreset(2)
                    )
                ),
                sensitivityJson = encodeSensitivity(SensitivitySettings()),
                autoLootJson = encodeAutoLoot(AutoLootSettings()),
                dailyMissionsJson = encodeMissions(defaultDailyMissions()),
                lastMissionResetDay = currentDay
            )
            saveProfileSync(initial)
            return initial
        }
        val sanitized = sanitizeProfileEntity(existing)
        // Check 24-hour daily mission reset (unfinished missions do not carry over; Level & XP are preserved)
        if (sanitized.lastMissionResetDay != currentDay) {
            val resetProfile = sanitized.copy(
                dailyMissionsJson = encodeMissions(defaultDailyMissions()),
                lastMissionResetDay = currentDay
            )
            saveProfileSync(resetProfile)
            return resetProfile
        }
        if (sanitized != existing) {
            saveProfileSync(sanitized)
        } else {
            cachedProfile = sanitized
        }
        return sanitized
    }

    suspend fun getOrInitProfile(nowMs: Long = System.currentTimeMillis()): PlayerProfileEntity {
        return getOrInitProfileSync(nowMs)
    }

    fun saveProfileSync(entity: PlayerProfileEntity) {
        val clean = sanitizeProfileEntity(entity)
        cachedProfile = clean
        try {
            dao.saveProfileSync(clean)
        } catch (_: Exception) {
            // Ignored if database is already closed during test teardown
        }
    }

    suspend fun saveProfile(entity: PlayerProfileEntity) {
        val clean = sanitizeProfileEntity(entity)
        cachedProfile = clean
        try {
            dao.saveProfile(clean)
        } catch (_: Exception) {
            saveProfileSync(clean)
        }
    }

    companion object {
        const val TWENTY_FOUR_HOURS_MS: Long = 24L * 60L * 60L * 1000L

        fun sanitizeProfileEntity(entity: PlayerProfileEntity): PlayerProfileEntity {
            val safeChar = CharacterId.entries.firstOrNull { it.name == entity.selectedCharacter } ?: CharacterId.SANI
            val safeMode = TeamMode.entries.firstOrNull { it.name == entity.selectedTeamMode } ?: TeamMode.SQUAD
            val safeGfx = GraphicsQuality.entries.firstOrNull { it.name == entity.graphicsQuality } ?: GraphicsQuality.LOW
            val safeFps = FpsTargetMode.entries.firstOrNull { it.name == entity.fpsTargetMode } ?: FpsTargetMode.AUTO_FPS
            val safeLevel = entity.level.coerceAtLeast(1)
            val safeXp = entity.xp.coerceAtLeast(0)
            val safeSlot = entity.activeHudSlot.coerceIn(0, 2)
            val safeUnlocked = (
                entity.unlockedCosmeticsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() } +
                    listOf("char_sani_default", "char_rima_default")
                ).distinct().joinToString(",")
            val safeHud = encodeHudLayouts(decodeHudLayouts(entity.hudLayoutsJson))
            val soundOn = decodeSoundEnabled(entity.sensitivityJson, entity.autoLootJson)
            val safeSens = encodeSensitivity(decodeSensitivity(entity.sensitivityJson), soundOn)
            val safeAutoLoot = encodeAutoLoot(decodeAutoLoot(entity.autoLootJson), soundOn)
            val safeMissions = encodeMissions(decodeMissions(entity.dailyMissionsJson))

            return entity.copy(
                id = 1,
                selectedCharacter = safeChar.name,
                selectedTeamMode = safeMode.name,
                level = safeLevel,
                xp = safeXp,
                unlockedCosmeticsCsv = safeUnlocked,
                equippedOutfitId = entity.equippedOutfitId.ifBlank { "char_sani_default" },
                equippedWeaponSkinId = entity.equippedWeaponSkinId.ifBlank { "skin_vx47_cobalt" },
                activeHudSlot = safeSlot,
                hudLayoutsJson = safeHud,
                graphicsQuality = safeGfx.name,
                fpsTargetMode = safeFps.name,
                sensitivityJson = safeSens,
                autoLootJson = safeAutoLoot,
                dailyMissionsJson = safeMissions
            )
        }

        fun defaultDailyMissions(): List<DailyMission> = listOf(
            DailyMission(
                id = "complete_1",
                title = "Play 1 Battle Royale Match",
                targetValue = 1,
                currentValue = 0,
                xpReward = 180,
                cosmeticRewardId = null,
                cosmeticRewardName = null
            ),
            DailyMission(
                id = "kills_3",
                title = "Get 3 Kills in Matches",
                targetValue = 3,
                currentValue = 0,
                xpReward = 250,
                cosmeticRewardId = "outfit_sani_cyber",
                cosmeticRewardName = "SANI - Cyber Visor Jersey"
            ),
            DailyMission(
                id = "survive_10m",
                title = "Survive for 120 Seconds",
                targetValue = 120,
                currentValue = 0,
                xpReward = 300,
                cosmeticRewardId = "outfit_rima_neon",
                cosmeticRewardName = "RIMA - Neon Pink Tactical"
            ),
            DailyMission(
                id = "top_5_placement",
                title = "Finish in Top 5 Placement",
                targetValue = 1,
                currentValue = 0,
                xpReward = 280,
                cosmeticRewardId = "skin_vx47_cobalt",
                cosmeticRewardName = "VX-47 - Cobalt Lightning"
            ),
            DailyMission(
                id = "loot_5",
                title = "Collect 5 Loot Items",
                targetValue = 5,
                currentValue = 0,
                xpReward = 150,
                cosmeticRewardId = "skin_bag_aegis",
                cosmeticRewardName = "Aegis Carbon Pack"
            )
        )

        fun encodeHudLayouts(presets: List<HudLayoutPreset>): String {
            val arr = JSONArray()
            for (i in 0 until 3) {
                val preset = presets.firstOrNull { it.slotIndex == i }
                    ?: presets.getOrNull(i)
                    ?: HudLayoutPreset.defaultPreset(i)
                val obj = JSONObject()
                obj.put("schemaVersion", 2)
                obj.put("slotIndex", i)
                obj.put("name", preset.name.ifBlank { "Layout ${i + 1}" })
                val controlsObj = JSONObject()
                HudControlId.entries.forEach { id ->
                    val cfg = (preset.controls[id] ?: HudControlConfig(controlId = id)).clamped()
                    val cObj = JSONObject()
                    cObj.put("normX", cfg.normX.toDouble())
                    cObj.put("normY", cfg.normY.toDouble())
                    cObj.put("sizeScale", cfg.sizeScale.toDouble())
                    cObj.put("alpha", cfg.alpha.toDouble())
                    controlsObj.put(id.name, cObj)
                }
                obj.put("controls", controlsObj)
                arr.put(obj)
            }
            return arr.toString()
        }

        fun decodeHudLayouts(json: String): List<HudLayoutPreset> {
            val defaults = listOf(
                HudLayoutPreset.defaultPreset(0),
                HudLayoutPreset.defaultPreset(1),
                HudLayoutPreset.defaultPreset(2)
            )
            if (json.isBlank()) return defaults
            return try {
                val arr = JSONArray(json)
                val result = mutableListOf<HudLayoutPreset>()
                for (i in 0 until 3) {
                    val obj = arr.optJSONObject(i)
                    if (obj == null || obj.optInt("schemaVersion", 1) < 2) {
                        result.add(HudLayoutPreset.defaultPreset(i))
                    } else {
                        val controlsObj = obj.optJSONObject("controls")
                        val map = mutableMapOf<HudControlId, HudControlConfig>()
                        HudControlId.entries.forEach { id ->
                            val cObj = controlsObj?.optJSONObject(id.name)
                            if (cObj != null) {
                                map[id] = HudControlConfig(
                                    controlId = id,
                                    normX = cObj.optDouble("normX", id.defaultNormX.toDouble()).toFloat(),
                                    normY = cObj.optDouble("normY", id.defaultNormY.toDouble()).toFloat(),
                                    sizeScale = cObj.optDouble("sizeScale", id.defaultSizeScale.toDouble()).toFloat(),
                                    alpha = cObj.optDouble("alpha", id.defaultAlpha.toDouble()).toFloat()
                                ).clamped()
                            } else {
                                map[id] = HudControlConfig(controlId = id)
                            }
                        }
                        result.add(
                            HudLayoutPreset(
                                slotIndex = i,
                                name = obj.optString("name", "Layout ${i + 1}").ifBlank { "Layout ${i + 1}" },
                                controls = map
                            )
                        )
                    }
                }
                result
            } catch (_: Exception) {
                defaults
            }
        }

        fun encodeSensitivity(raw: SensitivitySettings, soundEnabled: Boolean = true): String {
            val s = raw.sanitized()
            return JSONObject().apply {
                put("general", s.general.toDouble())
                put("redDot", s.redDot.toDouble())
                put("scope2x", s.scope2x.toDouble())
                put("scope4x", s.scope4x.toDouble())
                put("sniper", s.sniper.toDouble())
                put("freeLook", s.freeLook.toDouble())
                put("gyroMode", s.gyroMode.name)
                put("gyroRedDot", s.gyroRedDot.toDouble())
                put("gyro2x", s.gyro2x.toDouble())
                put("gyro4x", s.gyro4x.toDouble())
                put("gyroSniper", s.gyroSniper.toDouble())
                put("soundEnabled", soundEnabled)
            }.toString()
        }

        fun decodeSensitivity(json: String): SensitivitySettings {
            if (json.isBlank()) return SensitivitySettings()
            return try {
                val o = JSONObject(json)
                val modeName = o.optString("gyroMode", GyroscopeMode.OFF.name)
                val mode = GyroscopeMode.entries.firstOrNull { it.name == modeName } ?: GyroscopeMode.OFF
                SensitivitySettings(
                    general = o.optDouble("general", 65.0).toFloat(),
                    redDot = o.optDouble("redDot", 60.0).toFloat(),
                    scope2x = o.optDouble("scope2x", 52.0).toFloat(),
                    scope4x = o.optDouble("scope4x", 44.0).toFloat(),
                    sniper = o.optDouble("sniper", 35.0).toFloat(),
                    freeLook = o.optDouble("freeLook", 70.0).toFloat(),
                    gyroMode = mode,
                    gyroRedDot = o.optDouble("gyroRedDot", 55.0).toFloat(),
                    gyro2x = o.optDouble("gyro2x", 48.0).toFloat(),
                    gyro4x = o.optDouble("gyro4x", 40.0).toFloat(),
                    gyroSniper = o.optDouble("gyroSniper", 30.0).toFloat()
                ).sanitized()
            } catch (_: Exception) {
                SensitivitySettings()
            }
        }

        fun encodeAutoLoot(a: AutoLootSettings, soundEnabled: Boolean = true): String {
            return JSONObject().apply {
                put("enabled", a.enabled)
                put("guns", a.guns)
                put("ammo", a.ammo)
                put("armor", a.armor)
                put("helmet", a.helmet)
                put("attachments", a.attachments)
                put("healing", a.healing)
                put("grenades", a.grenades)
                put("glooWall", a.glooWall)
                put("backpack", a.backpack)
                put("otherItems", a.otherItems)
                put("soundEnabled", soundEnabled)
            }.toString()
        }

        fun decodeAutoLoot(json: String): AutoLootSettings {
            if (json.isBlank()) return AutoLootSettings()
            return try {
                val o = JSONObject(json)
                AutoLootSettings(
                    enabled = o.optBoolean("enabled", true),
                    guns = o.optBoolean("guns", true),
                    ammo = o.optBoolean("ammo", true),
                    armor = o.optBoolean("armor", true),
                    helmet = o.optBoolean("helmet", true),
                    attachments = o.optBoolean("attachments", true),
                    healing = o.optBoolean("healing", true),
                    grenades = o.optBoolean("grenades", true),
                    glooWall = o.optBoolean("glooWall", true),
                    backpack = o.optBoolean("backpack", true),
                    otherItems = o.optBoolean("otherItems", true)
                )
            } catch (_: Exception) {
                AutoLootSettings()
            }
        }

        fun decodeSoundEnabled(sensitivityJson: String, autoLootJson: String): Boolean {
            try {
                if (sensitivityJson.isNotBlank()) {
                    val o = JSONObject(sensitivityJson)
                    if (o.has("soundEnabled")) return o.optBoolean("soundEnabled", true)
                }
            } catch (_: Exception) {
            }
            try {
                if (autoLootJson.isNotBlank()) {
                    val o = JSONObject(autoLootJson)
                    if (o.has("soundEnabled")) return o.optBoolean("soundEnabled", true)
                }
            } catch (_: Exception) {
            }
            return true
        }

        fun encodeMissions(missions: List<DailyMission>): String {
            val arr = JSONArray()
            missions.distinctBy { it.id }.forEach { m ->
                val safeTarget = m.targetValue.coerceAtLeast(1)
                val safeCurrent = m.currentValue.coerceIn(0, safeTarget)
                val safeXp = m.xpReward.coerceAtLeast(0)
                arr.put(
                    JSONObject().apply {
                        put("id", m.id)
                        put("title", m.title)
                        put("targetValue", safeTarget)
                        put("currentValue", safeCurrent)
                        put("xpReward", safeXp)
                        put("cosmeticRewardId", m.cosmeticRewardId ?: "")
                        put("cosmeticRewardName", m.cosmeticRewardName ?: "")
                        put("claimed", m.claimed)
                    }
                )
            }
            return arr.toString()
        }

        fun decodeMissions(json: String): List<DailyMission> {
            if (json.isBlank()) return defaultDailyMissions()
            return try {
                val arr = JSONArray(json)
                val list = mutableListOf<DailyMission>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                    val title = o.optString("title", "").takeIf { it.isNotBlank() } ?: continue
                    val target = o.optInt("targetValue", 1).coerceAtLeast(1)
                    val current = o.optInt("currentValue", 0).coerceIn(0, target)
                    val xpReward = o.optInt("xpReward", 100).coerceAtLeast(0)
                    val cosId = o.optString("cosmeticRewardId", "").takeIf { it.isNotBlank() }
                    val cosName = o.optString("cosmeticRewardName", "").takeIf { it.isNotBlank() }
                    list.add(
                        DailyMission(
                            id = id,
                            title = title,
                            targetValue = target,
                            currentValue = current,
                            xpReward = xpReward,
                            cosmeticRewardId = cosId,
                            cosmeticRewardName = cosName,
                            claimed = o.optBoolean("claimed", false)
                        )
                    )
                }
                val deduped = list.distinctBy { it.id }
                if (deduped.isEmpty()) defaultDailyMissions() else deduped
            } catch (_: Exception) {
                defaultDailyMissions()
            }
        }

        fun xpRequiredForLevel(level: Int): Int = 300 + (level - 1) * 150

        fun nextCosmeticForLevel(level: Int): String {
            val next = CosmeticCatalog.items.firstOrNull { it.unlockLevel > level }
                ?: CosmeticCatalog.items.last()
            return "Lv.${next.unlockLevel}: ${next.name}"
        }
    }
}
