package com.example.ui.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-end Android (Vivo Y03) crash-safe bitmap loader.
 * Prevents density-upscaled OutOfMemoryError and HardwareUI "Bitmap too large to be uploaded into a texture"
 * crashes by decoding drawables with inScaled = false, RGB_565 config, and bounded dimensions.
 * If an asset is missing or fails to decode, returns null safely without crashing the app.
 */
object SafeBitmapLoader {
    private val bitmapCache = ConcurrentHashMap<Int, ImageBitmap>()

    fun loadSafeImageBitmap(
        context: Context,
        @DrawableRes resId: Int,
        maxDimensionPx: Int = 480
    ): ImageBitmap? {
        if (resId == 0) return null
        bitmapCache[resId]?.let { return it }

        return try {
            val resources = context.resources ?: return null
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inScaled = false
            }
            BitmapFactory.decodeResource(resources, resId, boundsOpts)
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            var sampleSize = 1
            val safeMax = maxDimensionPx.coerceAtLeast(64)
            while ((rawW / sampleSize) > safeMax || (rawH / sampleSize) > safeMax) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
                inScaled = false
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeResource(resources, resId, decodeOpts) ?: return null
            val imgBitmap = decoded.asImageBitmap()
            bitmapCache[resId] = imgBitmap
            imgBitmap
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
fun rememberSafeImageBitmap(
    @DrawableRes resId: Int,
    maxDimensionPx: Int = 480
): ImageBitmap? {
    val context = LocalContext.current
    return remember(resId, maxDimensionPx) {
        SafeBitmapLoader.loadSafeImageBitmap(context, resId, maxDimensionPx)
    }
}

@Composable
fun SafeDrawableImage(
    @DrawableRes resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxDimensionPx: Int = 480,
    fallbackAccentColor: Color = Color(0xFF00E5FF)
) {
    val bitmap = rememberSafeImageBitmap(resId = resId, maxDimensionPx = maxDimensionPx)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0B1320),
                        fallbackAccentColor.copy(alpha = 0.28f),
                        Color(0xFF111C30)
                    )
                )
            )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.width > 4f && size.height > 4f) {
                    drawCircle(
                        color = fallbackAccentColor.copy(alpha = 0.22f),
                        radius = size.minDimension * 0.32f,
                        center = Offset(size.width * 0.5f, size.height * 0.45f)
                    )
                }
            }
        }
    }
}
