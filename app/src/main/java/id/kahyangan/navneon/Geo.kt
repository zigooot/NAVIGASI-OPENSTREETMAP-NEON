package id.kahyangan.navneon

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Geo {
    fun distM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6371000.0
        val rad = PI / 180
        val dLat = (bLat - aLat) * rad
        val dLon = (bLon - aLon) * rad
        val h = sin(dLat / 2).pow(2) + cos(aLat * rad) * cos(bLat * rad) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /** Koordinat piksel web-mercator pada zoom z (ubin 256 px). */
    fun px(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * 2.0.pow(z) * 256.0

    fun py(lat: Double, z: Int): Double {
        val rad = lat * PI / 180
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * 2.0.pow(z) * 256.0
    }

    fun fmtKm(m: Double): String {
        val v = m / 1000.0
        return String.format(java.util.Locale("id", "ID"), "%.1f km", v)
    }
}
