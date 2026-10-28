package id.kahyangan.navneon

import android.graphics.Color

/** Palet warna tema (sama dengan tabel THEMES di prototipe HTML). */
internal class Palette(
    val cy: IntArray,
    val mg: IntArray,
    val pu: IntArray,
    val pus: IntArray,
    val deep: IntArray,
    val pu2: IntArray,
    val bg: IntArray,
    val muted: IntArray,
    val tint: IntArray,
    val gA: IntArray,
    val gB: IntArray,
    val gC: IntArray,
    val glow: IntArray,
    val panel: IntArray,
    val mapbg: IntArray,
    val dock: IntArray,
    val unlit: IntArray,
    val unlitRed: IntArray,
    val coverDark: IntArray,
    val scrim: IntArray,
    val dark1: IntArray,
    val dark2: IntArray,
    val dark3: IntArray,
    val rim1: IntArray,
    val rim2: IntArray,
    val tachTo: IntArray,
    val spdTo: IntArray,
    val rpmTo: IntArray,
    val sideSpd: IntArray,
    val sideRpm: IntArray,
    val sideLbl: IntArray,
    val shadowEnd: IntArray
) {
    var red = false
}

internal fun pc(a: IntArray): Int = Color.rgb(a[0], a[1], a[2])
internal fun cA(alpha: Int, a: IntArray): Int = Color.argb(alpha.coerceIn(0, 255), a[0], a[1], a[2])

internal object P {
    private val purple = Palette(
        cy = intArrayOf(46, 232, 255),
        mg = intArrayOf(255, 63, 210),
        pu = intArrayOf(155, 92, 255),
        pus = intArrayOf(199, 155, 255),
        deep = intArrayOf(106, 43, 224),
        pu2 = intArrayOf(176, 123, 255),
        bg = intArrayOf(6, 5, 13),
        muted = intArrayOf(169, 174, 200),
        tint = intArrayOf(170, 180, 255),
        gA = intArrayOf(48, 52, 92),
        gB = intArrayOf(24, 24, 48),
        gC = intArrayOf(18, 14, 36),
        glow = intArrayOf(150, 40, 220),
        panel = intArrayOf(18, 17, 42),
        mapbg = intArrayOf(11, 11, 30),
        dock = intArrayOf(201, 204, 224),
        unlit = intArrayOf(42, 27, 82),
        unlitRed = intArrayOf(74, 19, 66),
        coverDark = intArrayOf(27, 18, 64),
        scrim = intArrayOf(3, 2, 8),
        dark1 = intArrayOf(8, 7, 20),
        dark2 = intArrayOf(14, 12, 30),
        dark3 = intArrayOf(20, 20, 40),
        rim1 = intArrayOf(200, 190, 255),
        rim2 = intArrayOf(220, 190, 255),
        tachTo = intArrayOf(70, 24, 150),
        spdTo = intArrayOf(160, 36, 140),
        rpmTo = intArrayOf(36, 120, 165),
        sideSpd = intArrayOf(140, 40, 150),
        sideRpm = intArrayOf(40, 70, 170),
        sideLbl = intArrayOf(120, 130, 190),
        shadowEnd = intArrayOf(20, 10, 44)
    )
    private val red = Palette(
        cy = intArrayOf(255, 45, 58),
        mg = intArrayOf(255, 106, 61),
        pu = intArrayOf(183, 28, 34),
        pus = intArrayOf(255, 150, 140),
        deep = intArrayOf(122, 14, 20),
        pu2 = intArrayOf(224, 72, 79),
        bg = intArrayOf(5, 5, 5),
        muted = intArrayOf(186, 172, 172),
        tint = intArrayOf(214, 150, 150),
        gA = intArrayOf(46, 40, 42),
        gB = intArrayOf(24, 20, 21),
        gC = intArrayOf(16, 12, 12),
        glow = intArrayOf(200, 20, 30),
        panel = intArrayOf(20, 14, 15),
        mapbg = intArrayOf(12, 8, 8),
        dock = intArrayOf(216, 196, 196),
        unlit = intArrayOf(43, 21, 23),
        unlitRed = intArrayOf(74, 16, 20),
        coverDark = intArrayOf(42, 10, 12),
        scrim = intArrayOf(2, 1, 1),
        dark1 = intArrayOf(10, 6, 6),
        dark2 = intArrayOf(22, 12, 12),
        dark3 = intArrayOf(30, 22, 22),
        rim1 = intArrayOf(255, 200, 200),
        rim2 = intArrayOf(255, 210, 200),
        tachTo = intArrayOf(74, 10, 14),
        spdTo = intArrayOf(150, 50, 30),
        rpmTo = intArrayOf(140, 20, 28),
        sideSpd = intArrayOf(150, 60, 30),
        sideRpm = intArrayOf(130, 16, 22),
        sideLbl = intArrayOf(150, 120, 120),
        shadowEnd = intArrayOf(24, 6, 8)
    ).also { it.red = true }
    private var cur = purple
    var name = "purple"
    val isRed get() = cur.red
    fun set(n: String) { name = if (n == "red") "red" else "purple"; cur = if (name == "red") red else purple }
    val cy get() = cur.cy
    val mg get() = cur.mg
    val pu get() = cur.pu
    val pus get() = cur.pus
    val deep get() = cur.deep
    val pu2 get() = cur.pu2
    val bg get() = cur.bg
    val muted get() = cur.muted
    val tint get() = cur.tint
    val gA get() = cur.gA
    val gB get() = cur.gB
    val gC get() = cur.gC
    val glow get() = cur.glow
    val panel get() = cur.panel
    val mapbg get() = cur.mapbg
    val dock get() = cur.dock
    val unlit get() = cur.unlit
    val unlitRed get() = cur.unlitRed
    val coverDark get() = cur.coverDark
    val scrim get() = cur.scrim
    val dark1 get() = cur.dark1
    val dark2 get() = cur.dark2
    val dark3 get() = cur.dark3
    val rim1 get() = cur.rim1
    val rim2 get() = cur.rim2
    val tachTo get() = cur.tachTo
    val spdTo get() = cur.spdTo
    val rpmTo get() = cur.rpmTo
    val sideSpd get() = cur.sideSpd
    val sideRpm get() = cur.sideRpm
    val sideLbl get() = cur.sideLbl
    val shadowEnd get() = cur.shadowEnd
}
