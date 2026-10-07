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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = SaniDatabase.getInstance(applicationContext)
        val repository = PlayerRepository(database.playerProfileDao())

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

    // Hardware Gyroscope Sensor Integration (OFF / Always On / Scope On)
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
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
            sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
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
