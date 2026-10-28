package id.kahyangan.navneon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Ikon belokan putih (96 px) yang digambar sendiri, dipakai kartu navigasi. */
object ManeuverIcons {
    private val cache = HashMap<String, Bitmap>()

    fun get(key: String): Bitmap = synchronized(cache) { cache.getOrPut(key) { draw(key) } }

    private fun draw(key: String): Bitmap {
        val bm = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 11f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        fun head(x: Float, y: Float, deg: Float) { // kepala panah menghadap deg (0 = atas)
            c.save(); c.rotate(deg, x, y)
            c.drawPath(Path().apply { moveTo(x, y - 16f); lineTo(x - 17f, y + 8f); lineTo(x + 17f, y + 8f); close() }, f)
            c.restore()
        }
        fun turn(deg: Float) {
            val bx = 48f; val by = 88f; val kx = 48f; val ky = 54f
            val len = 30f; val rad = Math.toRadians(deg.toDouble())
            val ex = kx + (Math.sin(rad) * len).toFloat(); val ey = ky - (Math.cos(rad) * len).toFloat()
            c.drawPath(Path().apply { moveTo(bx, by); lineTo(kx, ky); lineTo(ex, ey) }, p)
            head(ex + (Math.sin(rad) * 6f).toFloat(), ey - (Math.cos(rad) * 6f).toFloat(), deg)
        }
        when (key) {
            "left" -> turn(-90f)
            "right" -> turn(90f)
            "sleft" -> turn(-40f)
            "sright" -> turn(40f)
            "hleft" -> turn(-140f)
            "hright" -> turn(140f)
            "uturn" -> {
                c.drawPath(Path().apply { moveTo(62f, 88f); lineTo(62f, 40f); arcTo(RectF(30f, 14f, 62f, 66f), 0f, -180f); lineTo(30f, 56f) }, p)
                head(30f, 62f, 180f)
            }
            "roundabout" -> {
                c.drawCircle(48f, 52f, 18f, p)
                c.drawLine(48f, 90f, 48f, 70f, p)
                c.drawLine(48f, 34f, 48f, 24f, p)
                head(48f, 16f, 0f)
            }
            "arrive" -> {
                c.drawCircle(48f, 40f, 20f, p)
                f.color = Color.WHITE; c.drawCircle(48f, 40f, 7f, f)
                c.drawLine(48f, 60f, 48f, 88f, p)
            }
            else -> { c.drawLine(48f, 90f, 48f, 30f, p); head(48f, 22f, 0f) }
        }
        return bm
    }
}
