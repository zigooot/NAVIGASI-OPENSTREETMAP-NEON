package id.kahyangan.navneon

import android.graphics.Bitmap
import android.location.Location
import android.os.Handler
import android.os.Looper

/** Satu-satunya sumber data kartu navigasi. */
object AppState {
    data class Place(val name: String, val address: String, val lat: Double, val lon: Double, val kind: String = "")

    /** Kartu belokan: jarak ke belokan, instruksi, serta ringkasan sisa rute. */
    data class NavLive(val dist: String, val instruction: String, val durasi: String, val sisa: String, val tiba: String, val icon: Bitmap?, val whenMs: Long)

    @Volatile var location: Location? = null
    @Volatile var locStatus: String = "GPS: mencari"
    @Volatile var speedKmh: Float = -1f
    @Volatile var locLocked: Boolean = false   // uji: posisi dikunci, GPS asli diabaikan

    // tujuan dan rute
    @Volatile var dest: Place? = null
    @Volatile var destRoute: List<DoubleArray>? = null
    @Volatile var destDistM: Double = 0.0
    @Volatile var destMin: Int = 0
    @Volatile var destStatus: String = ""

    // pemanduan belokan oleh aplikasi sendiri
    @Volatile var guiding: Boolean = false
    @Volatile var guideSteps: List<RouteRepo.Step> = emptyList()
    @Volatile var guideIdx: Int = 1
    @Volatile var guideTotalM: Double = 0.0
    @Volatile var guideLive: NavLive? = null

    // tampilan peta
    @Volatile var mapMode: Int = TileCache.DARK   // hanya peta neon
    @Volatile var mapZoom: Int = 0                // selisih zoom dari pengguna

    @Volatile var toast: String = ""
    @Volatile var toastUntil: Long = 0

    var onChange: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun changed() { main.post { onChange?.invoke() } }

    fun say(msg: String, ms: Long = 4000) {
        toast = msg; toastUntil = System.currentTimeMillis() + ms; changed()
    }
}
