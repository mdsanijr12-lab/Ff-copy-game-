package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.engine.BattleRoyaleViewModel
import com.example.engine.MatchUiState
import com.example.model.CharacterId
import com.example.model.CosmeticCatalog
import com.example.model.FpsTargetMode
import com.example.model.GraphicsQuality
import com.example.model.GyroscopeMode
import com.example.model.TeamMode
import kotlin.math.roundToInt

private enum class LobbyDialogType {
    NONE,
    DAILY_MISSIONS,
    COSMETIC_VAULT,
    SETTINGS
}

@Composable
fun LobbyScreen(
    state: MatchUiState,
    viewModel: BattleRoyaleViewModel,
    modifier: Modifier = Modifier
) {
    var activeDialog by remember { mutableStateOf(LobbyDialogType.NONE) }
    val selectedChar = state.selectedCharacter

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070B14), Color(0xFF0F172A), Color(0xFF091326))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("lobby_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Header Bar: SANI Title, Level, XP Progress Bar, Next Reward & Action Buttons
            TopLobbyBar(
                state = state,
                onOpenMissions = { activeDialog = LobbyDialogType.DAILY_MISSIONS },
                onOpenVault = { activeDialog = LobbyDialogType.COSMETIC_VAULT },
                onOpenHudEditor = { viewModel.openHudEditor() },
                onOpenSettings = { activeDialog = LobbyDialogType.SETTINGS }
            )

            // 2. Featured SANI Branding Banner + Active Character Showcase
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111C30)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // In-world SANI billboard banner preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_sani_banner_1791221363697),
                            contentDescription = "SANI Official Arena Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(bottomEnd = 10.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "SANI BATTLE ROYALE ISLAND",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Character Selection Row: SANI vs RIMA
                    Text(
                        text = "SELECT OPERATIVE FOR NEXT MATCH",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CharacterId.entries.forEach { charId ->
                            val isSelected = state.selectedCharacter == charId
                            val accent = if (charId == CharacterId.SANI) Color(0xFF00E5FF) else Color(0xFFFF4081)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF0B1320)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) accent else Color(0xFF334155),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable(enabled = !state.isStartingMatch) { viewModel.selectCharacter(charId) }
                                    .testTag("select_char_${charId.name.lowercase()}")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    ) {
                                        Image(
                                            painter = painterResource(id = charId.portraitRes),
                                            contentDescription = "${charId.displayName} Operative Reference",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isSelected) {
                                            Surface(
                                                color = accent,
                                                shape = RoundedCornerShape(bottomStart = 8.dp),
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Text(
                                                    text = "SELECTED",
                                                    color = Color.Black,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (charId == CharacterId.SANI) Icons.Default.Bolt else Icons.Default.Radar,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = charId.displayName,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Text(
                                        text = charId.abilityName,
                                        color = accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = charId.abilityDescription,
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Mode Selector (Solo / Duo / Squad) + DEPLOY MATCH Button
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111C30)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "TEAM MODE (SOLO / DUO / SQUAD)",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TeamMode.entries.forEach { mode ->
                            val active = state.selectedTeamMode == mode
                            Surface(
                                color = if (active) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clickable(enabled = !state.isStartingMatch) { viewModel.selectTeamMode(mode) }
                                    .testTag("mode_${mode.name.lowercase()}")
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = mode.label.uppercase(),
                                        color = if (active) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = if (mode.friendlyBots == 0) "1 Player" else "You + ${mode.friendlyBots} AI",
                                        color = if (active) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.startNewMatch() },
                        enabled = !state.isStartingMatch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            disabledContainerColor = Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("deploy_match_button")
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Deploy",
                            tint = if (state.isStartingMatch) Color(0xFF00E5FF) else Color.Black
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.isStartingMatch) {
                                "DEPLOYING TO ${state.spawnZoneName}..."
                            } else {
                                "START BATTLE ROYALE (${selectedChar.displayName} • ${state.selectedTeamMode.label.uppercase()})"
                            },
                            color = if (state.isStartingMatch) Color(0xFF00E5FF) else Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        if (state.isStartingMatch) {
            MatchLoadingOverlay(state = state)
        }

        // Modals for Daily Missions, Cosmetic Vault, and Settings
        when (activeDialog) {
            LobbyDialogType.DAILY_MISSIONS -> DailyMissionsDialog(
                state = state,
                onClaim = { viewModel.claimDailyMission(it) },
                onDismiss = { activeDialog = LobbyDialogType.NONE }
            )
            LobbyDialogType.COSMETIC_VAULT -> CosmeticVaultDialog(
                state = state,
                onEquip = { viewModel.equipCosmetic(it) },
                onDismiss = { activeDialog = LobbyDialogType.NONE }
            )
            LobbyDialogType.SETTINGS -> SettingsDialog(
                state = state,
                viewModel = viewModel,
                onDismiss = { activeDialog = LobbyDialogType.NONE }
            )
            LobbyDialogType.NONE -> Unit
        }
    }
}

@Composable
private fun TopLobbyBar(
    state: MatchUiState,
    onOpenMissions: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenHudEditor: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C30)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF00E5FF),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("lobby_level_badge")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Lv.${state.playerLevel.coerceAtLeast(1)}",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SANI BATTLE ROYALE",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "XP ${state.playerXp.coerceAtLeast(0)}/${state.xpForNextLevel.coerceAtLeast(1)} • Next: ${state.nextRewardLabel}",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("lobby_xp_progress_text")
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (state.playerXp.coerceAtLeast(0).toFloat() / state.xpForNextLevel.coerceAtLeast(1)).coerceIn(0f, 1f) },
                color = Color(0xFF00E5FF),
                trackColor = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .testTag("lobby_xp_progress_bar")
            )

            Spacer(Modifier.height(10.dp))
            val completedMissionsCount = state.dailyMissions.count { it.isCompleted }
            val totalMissionsCount = state.dailyMissions.size.coerceAtLeast(1)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LobbyNavChip(
                    label = "MISSIONS ($completedMissionsCount/$totalMissionsCount)",
                    onClick = onOpenMissions,
                    testTag = "open_missions_button",
                    enabled = !state.isStartingMatch,
                    modifier = Modifier.weight(1f)
                )
                LobbyNavChip(
                    label = "COSMETICS",
                    onClick = onOpenVault,
                    testTag = "open_cosmetics_button",
                    enabled = !state.isStartingMatch,
                    modifier = Modifier.weight(1f)
                )
                LobbyNavChip(
                    label = "HUD EDITOR",
                    onClick = onOpenHudEditor,
                    testTag = "open_hud_editor_button",
                    enabled = !state.isStartingMatch,
                    modifier = Modifier.weight(1f)
                )
                LobbyNavChip(
                    label = "SETTINGS",
                    onClick = onOpenSettings,
                    testTag = "open_settings_button",
                    enabled = !state.isStartingMatch,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LobbyNavChip(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .height(48.dp)
            .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun DailyMissionsDialog(
    state: MatchUiState,
    onClaim: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DAILY MISSIONS (RESETS EVERY 24H)",
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Resets every 24h • Cosmetics & XP only (no gameplay advantage)",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(290.dp)
                ) {
                    items(state.dailyMissions) { mission ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mission_item_${mission.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mission.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val rewardStr = buildString {
                                        append("+${mission.xpReward.coerceAtLeast(0)} XP")
                                        mission.cosmeticRewardName?.let { append(" • $it") }
                                    }
                                    val safeCur = mission.currentValue.coerceIn(0, mission.targetValue)
                                    Text(
                                        text = "$safeCur/${mission.targetValue} | $rewardStr",
                                        color = if (mission.isCompleted) Color(0xFF00E676) else Color(0xFF00E5FF),
                                        fontSize = 10.sp
                                    )
                                }
                                Button(
                                    onClick = { onClaim(mission.id) },
                                    enabled = mission.isCompleted && !mission.claimed,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (mission.isCompleted) Color(0xFF00E676) else Color(0xFF00E5FF),
                                        disabledContainerColor = if (mission.isCompleted) Color(0xFF064E3B) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier.testTag("mission_claim_${mission.id}")
                                ) {
                                    Text(
                                        text = when {
                                            mission.isCompleted && mission.claimed -> "COMPLETED"
                                            mission.isCompleted -> "CLAIM"
                                            else -> "IN PROGRESS"
                                        },
                                        color = if (mission.isCompleted && !mission.claimed) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CLOSE")
                }
            }
        }
    }
}

@Composable
private fun CosmeticVaultDialog(
    state: MatchUiState,
    onEquip: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "COSMETICS VAULT (COSMETIC ONLY)",
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Outfits, weapon skins, helmet & backpack cosmetics (Zero Pay-to-Win).",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(CosmeticCatalog.items) { item ->
                        val isUnlocked = item.id in state.unlockedCosmetics || state.playerLevel >= item.unlockLevel
                        val isEquipped = state.equippedOutfitId == item.id || state.equippedWeaponSkinId == item.id
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.name} (${item.category.label})",
                                        color = Color(item.accentColorHex),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = item.description,
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                }
                                Button(
                                    onClick = { onEquip(item.id) },
                                    enabled = isUnlocked && !isEquipped,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                                ) {
                                    Text(
                                        text = when {
                                            isEquipped -> "EQUIPPED"
                                            isUnlocked -> "EQUIP"
                                            else -> "LV.${item.unlockLevel}"
                                        },
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("CLOSE")
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    state: MatchUiState,
    viewModel: BattleRoyaleViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Text(
                    text = "GAME SETTINGS (VIVO Y03 OPTIMIZED)",
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Graphics Quality (Low, Medium, High)
                    Text("Graphics: Low / Medium / High", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GraphicsQuality.entries.forEach { q ->
                            val sel = state.graphicsQuality == q
                            Surface(
                                color = if (sel) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.updateGraphicsQuality(q) }
                                    .testTag("settings_graphics_${q.name.lowercase()}")
                            ) {
                                Text(
                                    text = q.name,
                                    color = if (sel) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // 2. FPS Target (30, 45, 60, Auto)
                    Text("FPS: 30 / 45 / 60 / Auto", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FpsTargetMode.entries.forEach { m ->
                            val sel = state.fpsTargetMode == m
                            Surface(
                                color = if (sel) Color(0xFF00E676) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.updateFpsTargetMode(m) }
                                    .testTag("settings_fps_${m.name.lowercase()}")
                            ) {
                                Text(
                                    text = if (m == FpsTargetMode.AUTO_FPS) "AUTO" else "${m.targetFps}",
                                    color = if (sel) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // 3. Aim Assist: ON / OFF
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Aim Assist: ${if (state.aimAssistEnabled) "ON" else "OFF"}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = state.aimAssistEnabled,
                            onCheckedChange = { viewModel.updateAimAssist(it) },
                            modifier = Modifier.testTag("settings_aim_assist_switch")
                        )
                    }

                    // 4. Sound: ON / OFF
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Sound: ${if (state.soundEnabled) "ON" else "OFF"}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = state.soundEnabled,
                            onCheckedChange = { viewModel.updateSoundEnabled(it) },
                            modifier = Modifier.testTag("settings_sound_switch")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    // 5. Camera Sensitivity Sliders (General, Red Dot, 2x, 4x, Sniper, Free Look)
                    Text("Camera Sensitivity", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val sens = state.sensitivity
                    SensitivitySliderRow("General", sens.general) { viewModel.updateSensitivity(sens.copy(general = it)) }
                    SensitivitySliderRow("Red Dot", sens.redDot) { viewModel.updateSensitivity(sens.copy(redDot = it)) }
                    SensitivitySliderRow("2x Scope", sens.scope2x) { viewModel.updateSensitivity(sens.copy(scope2x = it)) }
                    SensitivitySliderRow("4x Scope", sens.scope4x) { viewModel.updateSensitivity(sens.copy(scope4x = it)) }
                    SensitivitySliderRow("Sniper", sens.sniper) { viewModel.updateSensitivity(sens.copy(sniper = it)) }
                    SensitivitySliderRow("Free Look", sens.freeLook) { viewModel.updateSensitivity(sens.copy(freeLook = it)) }

                    HorizontalDivider(color = Color(0xFF334155))

                    // 6. Gyroscope: OFF / Always ON / Scope Only + Gyroscope Sensitivities
                    Text("Gyroscope Mode", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GyroscopeMode.entries.forEach { gm ->
                            val sel = sens.gyroMode == gm
                            Surface(
                                color = if (sel) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.updateGyroscopeMode(gm) }
                                    .testTag("settings_gyro_${gm.name.lowercase()}")
                            ) {
                                Text(
                                    text = gm.label,
                                    color = if (sel) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                    SensitivitySliderRow("Gyro Red Dot", sens.gyroRedDot) { viewModel.updateSensitivity(sens.copy(gyroRedDot = it)) }
                    SensitivitySliderRow("Gyro 2x", sens.gyro2x) { viewModel.updateSensitivity(sens.copy(gyro2x = it)) }
                    SensitivitySliderRow("Gyro 4x", sens.gyro4x) { viewModel.updateSensitivity(sens.copy(gyro4x = it)) }
                    SensitivitySliderRow("Gyro Sniper", sens.gyroSniper) { viewModel.updateSensitivity(sens.copy(gyroSniper = it)) }

                    HorizontalDivider(color = Color(0xFF334155))

                    // 7. Auto Loot: ON / OFF + Category Toggles
                    val al = state.autoLoot
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Auto Loot: ${if (al.enabled) "ON" else "OFF"}",
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = al.enabled,
                            onCheckedChange = { viewModel.updateAutoLootEnabled(it) },
                            modifier = Modifier.testTag("settings_auto_loot_switch")
                        )
                    }
                    AutoLootToggleRow("Guns", al.guns) { viewModel.updateAutoLoot(al.copy(guns = it)) }
                    AutoLootToggleRow("Compatible Ammo", al.ammo) { viewModel.updateAutoLoot(al.copy(ammo = it)) }
                    AutoLootToggleRow("Armor / Vest", al.armor) { viewModel.updateAutoLoot(al.copy(armor = it)) }
                    AutoLootToggleRow("Helmet", al.helmet) { viewModel.updateAutoLoot(al.copy(helmet = it)) }
                    AutoLootToggleRow("Scopes & Attachments", al.attachments) { viewModel.updateAutoLoot(al.copy(attachments = it)) }
                    AutoLootToggleRow("Medkits & Healing", al.healing) { viewModel.updateAutoLoot(al.copy(healing = it)) }
                    AutoLootToggleRow("Grenades", al.grenades) { viewModel.updateAutoLoot(al.copy(grenades = it)) }
                    AutoLootToggleRow("Gloo Wall", al.glooWall) { viewModel.updateAutoLoot(al.copy(glooWall = it)) }
                    AutoLootToggleRow("Backpack", al.backpack) { viewModel.updateAutoLoot(al.copy(backpack = it)) }
                    AutoLootToggleRow("Other Useful Items", al.otherItems) { viewModel.updateAutoLoot(al.copy(otherItems = it)) }
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("SAVE & CLOSE")
                }
            }
        }
    }
}

@Composable
private fun SensitivitySliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label (${value.roundToInt()})",
            color = Color(0xFFCBD5E1),
            fontSize = 10.sp,
            modifier = Modifier.width(105.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 10f..100f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF)),
            modifier = Modifier.weight(1f).height(24.dp)
        )
    }
}

@Composable
private fun AutoLootToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = Color(0xFFCBD5E1), fontSize = 11.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Complete Match Result Screen displaying:
 * - Victory / Defeat
 * - Placement
 * - Kills
 * - Damage
 * - Survival Time
 * - XP
 * - Rewards
 * - Team Performance
 * - Match Statistics
 * - Return to Lobby button
 */
@Composable
fun MatchResultScreen(
    state: MatchUiState,
    onReturnToLobby: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = state.lastMatchResult ?: com.example.model.MatchResultSummary(
        isVictory = false,
        placement = 2,
        totalTeamsOrPlayers = when (state.selectedTeamMode) {
            TeamMode.SOLO -> 24
            TeamMode.DUO -> 12
            TeamMode.SQUAD -> 6
        },
        kills = state.playerKills.coerceAtLeast(0),
        damageDealt = state.playerDamageDealt.toInt().coerceAtLeast(0),
        survivalTimeSec = state.matchElapsedSec.toInt().coerceAtLeast(1),
        xpEarned = 100,
        rewardsUnlocked = emptyList<String>(),
        teamPerformance = emptyList<com.example.model.TeamMemberStats>(),
        headshots = state.playerHeadshots.coerceAtLeast(0),
        coinsCollected = state.playerCoinsCollectedTotal.coerceAtLeast(0),
        teammatesRevived = state.playerTeammatesRevived.coerceAtLeast(0)
    )
    var returnToLobbyTriggered by remember(result) { mutableStateOf(false) }
    val safePlacement = result.placement.coerceAtLeast(1)
    val safeTotal = result.totalTeamsOrPlayers.coerceAtLeast(safePlacement)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070B14), Color(0xFF111C30), Color(0xFF070B14))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .testTag("match_result_screen")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    2.dp,
                    if (result.isVictory) Color(0xFFFFD600) else Color(0xFF00E5FF),
                    RoundedCornerShape(18.dp)
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Text(
                    text = if (result.isVictory) "VICTORY — BOOYAH!" else "DEFEAT — MATCH COMPLETE",
                    color = if (result.isVictory) Color(0xFFFFD600) else Color(0xFFFF5252),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("match_result_outcome")
                )
                Text(
                    text = "FINAL PLACEMENT: #$safePlacement / $safeTotal",
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("match_result_placement")
                )

                Spacer(Modifier.height(12.dp))

                // Core Match Stats Grid (Kills, Damage, Survival Time, XP Earned)
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ResultMetricBox("KILLS", "${result.kills.coerceAtLeast(0)}")
                    ResultMetricBox("DAMAGE", "${result.damageDealt.coerceAtLeast(0)}")
                    ResultMetricBox("SURVIVAL TIME", "${result.survivalTimeSec.coerceAtLeast(1)}s")
                    ResultMetricBox("XP EARNED", "+${result.xpEarned.coerceAtLeast(0)}")
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Headshots: ${result.headshots} • Coins Collected: ${result.coinsCollected} • Teammates Revived: ${result.teammatesRevived}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )

                if (result.rewardsUnlocked.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "REWARDS UNLOCKED: ${result.rewardsUnlocked.joinToString()}",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "TEAM PERFORMANCE",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))

                result.teamPerformance.forEach { member ->
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = member.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kills: ${member.kills} | Dmg: ${member.damage} | ${member.status}",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!returnToLobbyTriggered) {
                            returnToLobbyTriggered = true
                            onReturnToLobby()
                        }
                    },
                    enabled = !returnToLobbyTriggered,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("return_to_lobby_button")
                ) {
                    Text(
                        text = "RETURN TO LOBBY",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultMetricBox(title: String, value: String) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(title, color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * Lightweight, low-end-optimized short loading screen displayed when starting a Solo, Duo, or Squad match.
 */
@Composable
fun MatchLoadingOverlay(
    state: MatchUiState,
    modifier: Modifier = Modifier
) {
    val chosenChar = state.matchCharacter
    val accent = if (chosenChar == CharacterId.SANI) Color(0xFF00E5FF) else Color(0xFFFF4081)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF2070B14))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)
            .testTag("match_loading_screen")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111C30)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, accent, RoundedCornerShape(18.dp))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, accent, RoundedCornerShape(12.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_sani_banner_1791221363697),
                        contentDescription = "SANI Battle Royale Loading Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "DEPLOYING TO ${state.spawnZoneName}",
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "OPERATIVE: ${chosenChar.displayName} • MODE: ${state.selectedTeamMode.label.uppercase()}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.selectedTeamMode.friendlyBots == 0) {
                        "Solo Drop • 24 Combatants • Safe Zone Active"
                    } else {
                        "Squad Drop (+${state.selectedTeamMode.friendlyBots} Friendly AI) • Safe Zone Active"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { state.matchLoadingProgress.coerceIn(0.15f, 1f) },
                    color = Color(0xFF00E676),
                    trackColor = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

