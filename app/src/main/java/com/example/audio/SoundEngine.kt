package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundEngine {
    private val sampleRate = 22050
    // Bounded queue prevents sound backlog or thread exhaustion on low-end devices (Vivo Y03)
    private val executor = ThreadPoolExecutor(
        1,
        1,
        2000L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(3),
        ThreadPoolExecutor.DiscardOldestPolicy()
    ).apply {
        try {
            allowCoreThreadTimeOut(true)
        } catch (_: Throwable) {
        }
    }
    @Volatile
    var isMuted: Boolean = false

    // Lazy-initialized PCM sound buffers (zero work on main thread during app launch; object-pooled for Vivo Y03)
    private val rifleShotPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildGunshotPcm(durationMs = 85, baseFreq = 190.0, decay = 34.0)
    }
    private val shotgunPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildGunshotPcm(durationMs = 130, baseFreq = 110.0, decay = 22.0)
    }
    private val sniperPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildGunshotPcm(durationMs = 160, baseFreq = 140.0, decay = 18.0)
    }
    private val reloadPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildDoubleClickPcm(freq1 = 580.0, freq2 = 820.0)
    }
    private val footstepNormalPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildFootstepPcm(volume = 0.28f, freq = 125.0)
    }
    private val footstepSprintPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildFootstepPcm(volume = 0.46f, freq = 155.0)
    }
    private val footstepCrouchPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildFootstepPcm(volume = 0.12f, freq = 100.0)
    }
    private val jumpPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildJumpPcm()
    }
    private val pickupPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildChimePcm(freqStart = 520.0, freqEnd = 780.0, durationMs = 75)
    }
    private val coinPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildChimePcm(freqStart = 880.0, freqEnd = 1320.0, durationMs = 90)
    }
    private val hitMarkerPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildChimePcm(freqStart = 1200.0, freqEnd = 650.0, durationMs = 45)
    }
    private val knockDeathPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildChimePcm(freqStart = 340.0, freqEnd = 120.0, durationMs = 210)
    }
    private val zoneWarningPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildChimePcm(freqStart = 440.0, freqEnd = 620.0, durationMs = 190)
    }
    private val saniAbilityPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildSaniOverdrivePcm()
    }
    private val rimaAbilityPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildRimaPulsePcm()
    }
    private val uiClickPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildChimePcm(freqStart = 680.0, freqEnd = 900.0, durationMs = 35)
    }
    private val friendlyCalloutPcm by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildDoubleClickPcm(freq1 = 940.0, freq2 = 1180.0)
    }

    val saniAbilitySignatureHash: Int get() = saniAbilityPcm.contentHashCode()
    val rimaAbilitySignatureHash: Int get() = rimaAbilityPcm.contentHashCode()

    // Anti-duplicate / anti-looping debounce timestamps (ms)
    private var lastFootstepTimeMs = 0L
    private var lastGunshotTimeMs = 0L
    private var lastReloadTimeMs = 0L
    private var lastJumpTimeMs = 0L
    private var lastPickupTimeMs = 0L
    private var lastCoinTimeMs = 0L
    private var lastHitTimeMs = 0L
    private var lastDeathTimeMs = 0L
    private var lastZoneTimeMs = 0L
    private var lastAbilityTimeMs = 0L
    private var lastUiClickTimeMs = 0L
    private var lastFriendlyCalloutTimeMs = 0L

    // Diagnostic counters for verifying audio events and duplicate prevention
    @Volatile var gunshotPlayCount: Int = 0
        private set
    @Volatile var reloadPlayCount: Int = 0
        private set
    @Volatile var footstepPlayCount: Int = 0
        private set
    @Volatile var jumpPlayCount: Int = 0
        private set
    @Volatile var pickupPlayCount: Int = 0
        private set
    @Volatile var hitPlayCount: Int = 0
        private set
    @Volatile var deathPlayCount: Int = 0
        private set
    @Volatile var zoneWarningPlayCount: Int = 0
        private set
    @Volatile var saniAbilityPlayCount: Int = 0
        private set
    @Volatile var rimaAbilityPlayCount: Int = 0
        private set
    @Volatile var uiClickPlayCount: Int = 0
        private set
    @Volatile var friendlyCalloutPlayCount: Int = 0
        private set
    val enemyVoicePlayCount: Int = 0

    fun resetAudioCountersForTest() {
        lastFootstepTimeMs = 0L
        lastGunshotTimeMs = 0L
        lastReloadTimeMs = 0L
        lastJumpTimeMs = 0L
        lastPickupTimeMs = 0L
        lastCoinTimeMs = 0L
        lastHitTimeMs = 0L
        lastDeathTimeMs = 0L
        lastZoneTimeMs = 0L
        lastAbilityTimeMs = 0L
        lastUiClickTimeMs = 0L
        lastFriendlyCalloutTimeMs = 0L
        gunshotPlayCount = 0
        reloadPlayCount = 0
        footstepPlayCount = 0
        jumpPlayCount = 0
        pickupPlayCount = 0
        hitPlayCount = 0
        deathPlayCount = 0
        zoneWarningPlayCount = 0
        saniAbilityPlayCount = 0
        rimaAbilityPlayCount = 0
        uiClickPlayCount = 0
        friendlyCalloutPlayCount = 0
    }

    fun playGunshot(isShotgun: Boolean = false, isSniper: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastGunshotTimeMs < 55L) return
        lastGunshotTimeMs = now
        gunshotPlayCount++
        val buffer = when {
            isSniper -> sniperPcm
            isShotgun -> shotgunPcm
            else -> rifleShotPcm
        }
        playPcmAsync(buffer)
    }

    fun playReload() {
        val now = System.currentTimeMillis()
        if (now - lastReloadTimeMs < 240L) return
        lastReloadTimeMs = now
        reloadPlayCount++
        playPcmAsync(reloadPcm)
    }

    fun playFootstep(mode: Int) {
        // mode: 0 = silent/prone, 1 = crouch, 2 = walk/run, 3 = sprint
        if (mode <= 0) return
        val now = System.currentTimeMillis()
        val minInterval = when (mode) {
            3 -> 250L
            2 -> 380L
            else -> 520L
        }
        if (now - lastFootstepTimeMs < minInterval) return
        lastFootstepTimeMs = now
        footstepPlayCount++
        val pcm = when (mode) {
            3 -> footstepSprintPcm
            2 -> footstepNormalPcm
            else -> footstepCrouchPcm
        }
        playPcmAsync(pcm)
    }

    fun playJump() {
        val now = System.currentTimeMillis()
        if (now - lastJumpTimeMs < 220L) return
        lastJumpTimeMs = now
        jumpPlayCount++
        playPcmAsync(jumpPcm)
    }

    fun playPickup() {
        val now = System.currentTimeMillis()
        if (now - lastPickupTimeMs < 70L) return
        lastPickupTimeMs = now
        pickupPlayCount++
        playPcmAsync(pickupPcm)
    }

    fun playCoinPickup() {
        val now = System.currentTimeMillis()
        if (now - lastCoinTimeMs < 70L) return
        lastCoinTimeMs = now
        pickupPlayCount++
        playPcmAsync(coinPcm)
    }

    fun playHit() {
        val now = System.currentTimeMillis()
        if (now - lastHitTimeMs < 50L) return
        lastHitTimeMs = now
        hitPlayCount++
        playPcmAsync(hitMarkerPcm)
    }

    fun playKnockOrDeath() {
        val now = System.currentTimeMillis()
        if (now - lastDeathTimeMs < 180L) return
        lastDeathTimeMs = now
        deathPlayCount++
        playPcmAsync(knockDeathPcm)
    }

    fun playZoneWarning() {
        val now = System.currentTimeMillis()
        if (now - lastZoneTimeMs < 450L) return
        lastZoneTimeMs = now
        zoneWarningPlayCount++
        playPcmAsync(zoneWarningPcm)
    }

    fun playSaniAbility() {
        val now = System.currentTimeMillis()
        if (now - lastAbilityTimeMs < 280L) return
        lastAbilityTimeMs = now
        saniAbilityPlayCount++
        playPcmAsync(saniAbilityPcm)
    }

    fun playRimaAbility() {
        val now = System.currentTimeMillis()
        if (now - lastAbilityTimeMs < 280L) return
        lastAbilityTimeMs = now
        rimaAbilityPlayCount++
        playPcmAsync(rimaAbilityPcm)
    }

    fun playUiClick() {
        val now = System.currentTimeMillis()
        if (now - lastUiClickTimeMs < 55L) return
        lastUiClickTimeMs = now
        uiClickPlayCount++
        playPcmAsync(uiClickPcm)
    }

    fun playFriendlyCalloutBeep() {
        val now = System.currentTimeMillis()
        if (now - lastFriendlyCalloutTimeMs < 1600L) return
        lastFriendlyCalloutTimeMs = now
        friendlyCalloutPlayCount++
        playPcmAsync(friendlyCalloutPcm)
    }

    private fun playPcmAsync(pcmData: ShortArray) {
        if (isMuted || pcmData.isEmpty()) return
        try {
            executor.execute {
                if (isMuted) return@execute
                var track: AudioTrack? = null
                try {
                    val byteCount = pcmData.size * 2
                    val minBuffer = try {
                        AudioTrack.getMinBufferSize(
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                    } catch (_: Throwable) {
                        1024
                    }
                    val safeBufferBytes = maxOf(byteCount, minBuffer, 1024)
                    track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(safeBufferBytes)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        track.write(pcmData, 0, pcmData.size)
                        track.play()
                        Thread.sleep((pcmData.size * 1000L / sampleRate) + 12L)
                        track.stop()
                    }
                } catch (_: Throwable) {
                    // Safe fallback in headless/test or low-memory environments
                } finally {
                    try {
                        track?.release()
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun buildGunshotPcm(durationMs: Int, baseFreq: Double, decay: Double): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        val rng = Random(42)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * decay)
            val noise = (rng.nextDouble() * 2.0 - 1.0) * 0.65
            val boom = sin(2.0 * PI * baseFreq * t) * 0.55
            val sample = ((noise + boom) * env * 0.85).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun buildFootstepPcm(volume: Float, freq: Double): ShortArray {
        val count = (sampleRate * 45) / 1000
        val out = ShortArray(count)
        val rng = Random(99)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 55.0)
            val thud = sin(2.0 * PI * freq * t) * 0.7 + (rng.nextDouble() - 0.5) * 0.3
            val sample = (thud * env * volume).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun buildJumpPcm(): ShortArray {
        val count = (sampleRate * 95) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val frac = i.toDouble() / count.coerceAtLeast(1)
            val t = i.toDouble() / sampleRate
            val freq = 180.0 + 260.0 * frac
            val env = exp(-t * 18.0)
            val sample = (sin(2.0 * PI * freq * t) * env * 0.42).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun buildChimePcm(freqStart: Double, freqEnd: Double, durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val frac = i.toDouble() / count.coerceAtLeast(1)
            val t = i.toDouble() / sampleRate
            val freq = freqStart + (freqEnd - freqStart) * frac
            val env = (1.0 - frac * 0.85)
            val sample = (sin(2.0 * PI * freq * t) * env * 0.45).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun buildDoubleClickPcm(freq1: Double, freq2: Double): ShortArray {
        val count = (sampleRate * 110) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val frac = i.toDouble() / count
            val t = i.toDouble() / sampleRate
            val active = if (frac < 0.42) freq1 else if (frac > 0.58) freq2 else 0.0
            val sample = if (active == 0.0) 0.0 else sin(2.0 * PI * active * t) * 0.40
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    // Unique SANI Kinetic Overdrive audio: rising high-energy electric turbine sweep
    private fun buildSaniOverdrivePcm(): ShortArray {
        val count = (sampleRate * 260) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val frac = i.toDouble() / count
            val t = i.toDouble() / sampleRate
            val freq = 260.0 + 780.0 * (frac * frac)
            val harmonic = sin(2.0 * PI * (freq * 1.5) * t) * 0.25
            val carrier = sin(2.0 * PI * freq * t) * 0.55
            val env = (1.0 - frac * 0.3)
            val sample = ((carrier + harmonic) * env * 0.65).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    // Unique RIMA Tactical Pulse audio: triple crystalline sonar radar ping
    private fun buildRimaPulsePcm(): ShortArray {
        val count = (sampleRate * 280) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val frac = i.toDouble() / count
            val t = i.toDouble() / sampleRate
            val pingFreq = when {
                frac < 0.30 -> 1046.5
                frac in 0.36..0.66 -> 1318.5
                frac > 0.72 -> 1567.9
                else -> 0.0
            }
            val sample = if (pingFreq == 0.0) 0.0 else sin(2.0 * PI * pingFreq * t) * 0.50
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
