package com.tonotune.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the launcher icon the way launchers mask it, for visual review (build/screenshots/icons). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconRenderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val out = File("build/screenshots/icons").apply { mkdirs() }

    private fun masked(size: Int, mask: (Path, Float) -> Unit, mono: Boolean = false): Bitmap {
        val icon = ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Adaptive layers are 108dp with the visible area being the centre 72dp.
        val pad = size * 18f / 72f
        val layers = if (mono) listOf(icon.monochrome!!) else listOf(icon.background!!, icon.foreground!!)
        if (mono) c.drawColor(Color.parseColor("#2A3A40"))
        layers.forEachIndexed { i, d ->
            d.setBounds(-pad.toInt(), -pad.toInt(), (size + pad).toInt(), (size + pad).toInt())
            if (mono) d.setTint(Color.parseColor("#B8EAF5"))
            d.draw(c)
        }
        // Apply the launcher mask.
        val path = Path().also { mask(it, size.toFloat()) }
        val m = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(m).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
        c.drawBitmap(m, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        return bmp
    }

    private fun save(b: Bitmap, name: String) = File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test fun renderIcons() {
        val circle = { p: Path, s: Float -> p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW) }
        val squircle = { p: Path, s: Float -> p.addRoundRect(RectF(0f, 0f, s, s), s * 0.3f, s * 0.3f, Path.Direction.CW) }
        save(masked(432, circle), "launcher_circle")
        save(masked(432, squircle), "launcher_squircle")
        save(masked(432, circle, mono = true), "launcher_themed")
        save(masked(96, squircle), "launcher_small")
        val stat = ContextCompat.getDrawable(ctx, R.drawable.ic_stat_tono)!!
        val sb = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(sb).apply { drawColor(Color.parseColor("#20232B")); stat.setBounds(0, 0, 96, 96); stat.draw(this) }
        save(sb, "notification")
        assertTrue(File(out, "launcher_circle.png").length() > 0)
    }
}
