package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.PlayerRepository
import com.example.data.SaniDatabase
import com.example.engine.AppScreenState
import com.example.engine.BattleRoyaleViewModel
import com.example.ui.render.World3DViewport
import com.example.ui.screens.GameHudOverlay
import com.example.ui.screens.HudEditorScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.MatchLoadingOverlay
import com.example.ui.screens.MatchResultScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {
            // Safe fallback on OEM devices with custom window decor
        }
        val appCtx = applicationContext
        val database = SaniDatabase.getInstanceOrNull(appCtx)
        val dao = try {
            database?.playerProfileDao()
        } catch (_: Throwable) {
            null
        }
        val repository = PlayerRepository(dao = dao, appContext = appCtx)

        setContent {
            MyApplicationTheme {
                val brViewModel: BattleRoyaleViewModel = viewModel(
                    factory = BattleRoyaleViewModel.provideFactory(repository)
                )
                SaniBattleRoyaleApp(viewModel = brViewModel)
            }
        }
    }
}

@Composable
fun SaniBattleRoyaleApp(
    viewModel: BattleRoyaleViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isSplashComplete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isSplashComplete) {
            delay(120L)
            isSplashComplete = true
        }
    }

    // Hardware Gyroscope Sensor Integration (OFF / Always On / Scope On) — fail-safe on devices without gyroscope
    DisposableEffect(context) {
        val sensorManager = try {
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        } catch (_: Throwable) {
            null
        }
        val gyroSensor = try {
            sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        } catch (_: Throwable) {
            null
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                if (values.size >= 2) {
                    viewModel.applyGyroscopeDelta(
                        gyroYawRateRad = values[1],
                        gyroPitchRateRad = values[0]
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensorManager != null && gyroSensor != null) {
            try {
                sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
            } catch (_: Throwable) {
            }
        }
        onDispose {
            try {
                sensorManager?.unregisterListener(listener)
            } catch (_: Throwable) {
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!isSplashComplete && state.screenState == AppScreenState.LOBBY) {
            StartupSplashScreen(onSkip = { isSplashComplete = true })
        } else {
            when (state.screenState) {
                AppScreenState.LOBBY -> {
                    LobbyScreen(
                        state = state,
                        viewModel = viewModel
                    )
                }

                AppScreenState.HUD_EDITOR -> {
                    HudEditorScreen(
                        state = state,
                        viewModel = viewModel
                    )
                }

                AppScreenState.IN_MATCH -> {
                    BackHandler {
                        viewModel.returnToLobby()
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        World3DViewport(
                            state = state,
                            onCameraDrag = { dx, dy ->
                                viewModel.rotateCameraByTouch(dx, dy)
                            }
                        )
                        GameHudOverlay(
                            state = state,
                            viewModel = viewModel
                        )
                        if (state.isStartingMatch && state.matchLoadingRemainingSec > 0f) {
                            MatchLoadingOverlay(state = state)
                        }
                    }
                }

                AppScreenState.MATCH_RESULT -> {
                    BackHandler {
                        viewModel.returnToLobby()
                    }
                    MatchResultScreen(
                        state = state,
                        onReturnToLobby = { viewModel.returnToLobby() }
                    )
                }
            }
        }
    }
}

@Composable
fun StartupSplashScreen(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { 0.85f }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .clickable { onSkip() }
            .padding(24.dp)
            .testTag("startup_splash_screen")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            Text(
                text = "SANI",
                color = Color(0xFF00E5FF),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "BATTLE ROYALE",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = Color(0xFF00E676),
                trackColor = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Initializing essential systems...",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        }
    }
}
