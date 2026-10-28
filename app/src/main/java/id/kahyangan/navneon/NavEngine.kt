package id.kahyangan.navneon

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Mesin navigasi: posisi GPS, rute OSRM (mobil), pemanduan belokan demi belokan, hitung ulang saat keluar jalur,
 * dan simulasi berkendara untuk uji.
 */
object NavEngine {
    private val io = Executors.newSingleThreadExecutor()
    private var lastDestLoc: Location? = null
    private var started = false

    fun init(ctx: Context) {
        OfflineMaps.init(ctx)
    }

    /** Mulai membaca GPS (butuh izin lokasi). Aman dipanggil berulang. */
    @SuppressLint("MissingPermission")
    fun startGps(ctx: Context) {
        if (started) return
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        started = true
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { onLoc(it) }
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (lm.isProviderEnabled(p)) {
                try { lm.requestLocationUpdates(p, 1000L, 3f, listener, Looper.getMainLooper()) } catch (_: Exception) {}
                lm.getLastKnownLocation(p)?.let { if (AppState.location == null) onLoc(it) }
            }
        }
    }

    /** Masukkan satu fiks posisi (dari GPS, atau dari simulasi). */
    fun onLoc(loc: Location) {
        if (AppState.locLocked && loc.provider != "sim") return
        AppState.location = loc
        AppState.locStatus = "GPS: aktif"
        AppState.speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else -1f
        updateGuidance(loc)
        AppState.changed()
        AppState.dest?.let { d ->
            if (Geo.distM(loc.latitude, loc.longitude, d.lat, d.lon) < 60) {
                clearDestination()
                AppState.say("Tiba di tujuan")
            } else {
                val ld = lastDestLoc
                if (ld == null || ld.distanceTo(loc) > 300) io.execute { computeDestRoute() }
            }
        }
    }

    /** Kunci posisi (uji tanpa GPS); simulasi tetap boleh menggerakkan posisi. */
    fun lockAt(lat: Double, lon: Double) {
        AppState.locLocked = true
        onLoc(Location("uji").apply { latitude = lat; longitude = lon })
    }

    /** Menetapkan tujuan hasil pencarian dan menghitung rutenya. */
    fun setDestination(p: AppState.Place) {
        AppState.dest = p
        AppState.mapZoom = 0
        val loc = AppState.location
        if (loc != null) {
            AppState.destRoute = listOf(doubleArrayOf(loc.latitude, loc.longitude), doubleArrayOf(p.lat, p.lon))
            AppState.destDistM = Geo.distM(loc.latitude, loc.longitude, p.lat, p.lon)
        } else {
            AppState.destRoute = null
            AppState.destDistM = 0.0
        }
        AppState.destMin = 0
        AppState.destStatus = "Menghitung rute"
        lastDestLoc = null
        AppState.changed()
        io.execute { computeDestRoute() }
    }

    fun clearDestination() {
        AppState.guiding = false; AppState.guideLive = null; AppState.guideSteps = emptyList()
        AppState.dest = null
        AppState.destRoute = null
        AppState.destStatus = ""
        AppState.mapZoom = 0
        lastDestLoc = null
        AppState.changed()
    }

    // ---------- pemanduan belokan ----------
    private var offCount = 0

    fun startGuidance() {
        if (AppState.dest == null) return
        AppState.guiding = true; AppState.guideIdx = 1; offCount = 0
        AppState.say("Pemanduan dimulai")
        AppState.location?.let { updateGuidance(it) }
        AppState.changed()
    }

    fun stopGuidance() {
        AppState.guiding = false; AppState.guideLive = null
        AppState.changed()
    }

    private fun fmtNear(d: Double): String =
        if (d < 1000) "${(Math.round(d / 10.0) * 10).toInt().coerceAtLeast(10)} m" else Geo.fmtKm(d)

    private fun updateGuidance(loc: Location) {
        if (!AppState.guiding) return
        val steps = AppState.guideSteps
        if (steps.size < 2) { AppState.guideLive = null; return }
        var idx = AppState.guideIdx.coerceIn(1, steps.size - 1)
        while (idx < steps.size - 1 && Geo.distM(loc.latitude, loc.longitude, steps[idx].lat, steps[idx].lon) < 30) idx++
        AppState.guideIdx = idx
        val st = steps[idx]
        val d = Geo.distM(loc.latitude, loc.longitude, st.lat, st.lon)
        var remain = d
        for (k in idx until steps.size) remain += steps[k].distM
        val mins = max(1, Math.round(AppState.destMin * remain / AppState.guideTotalM).toInt())
        val eta = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(System.currentTimeMillis() + mins * 60000L))
        AppState.guideLive = AppState.NavLive(fmtNear(d), st.text, "$mins mnt", Geo.fmtKm(remain), eta, ManeuverIcons.get(st.icon), System.currentTimeMillis())
        // keluar jalur: jauh dari seluruh titik rute dua kali berturut-turut, hitung ulang
        val route = AppState.destRoute
        if (route != null && route.size > 2) {
            var best = Double.MAX_VALUE
            for (p in route) { val dd = Geo.distM(loc.latitude, loc.longitude, p[0], p[1]); if (dd < best) best = dd }
            if (best > 70) {
                offCount++
                if (offCount >= 2) { android.util.Log.i("RushRoute", "keluar jalur ${best.toInt()} m, hitung ulang"); offCount = 0; lastDestLoc = null; AppState.say("Menghitung ulang rute"); io.execute { computeDestRoute() } }
            } else offCount = 0
        }
    }

    private fun bearingDeg(la1: Double, lo1: Double, la2: Double, lo2: Double): Float {
        val p1 = Math.toRadians(la1); val p2 = Math.toRadians(la2); val dl = Math.toRadians(lo2 - lo1)
        val y = Math.sin(dl) * Math.cos(p2); val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
        return ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    // ---------- simulasi berkendara (uji): bergerak di sepanjang rute ----------
    private var simOn = false
    private var simDist = 0.0
    private var simRoute: List<DoubleArray>? = null
    private var simCum = DoubleArray(0)

    private var simTick = 0
    private var simOffLat = 0.0

    /** offAt: detik ke berapa mobil digeser sekitar 200 m keluar jalur (uji hitung ulang rute); -1 = tidak. */
    fun startSim(offAt: Int = -1) {
        if (simOn) return
        simOn = true; simDist = 0.0; simTick = 0; simOffLat = 0.0
        val h = Handler(Looper.getMainLooper())
        h.post(object : Runnable {
            override fun run() {
                val route = AppState.destRoute
                if (!simOn || AppState.dest == null) { simOn = false; return }
                if (route !== simRoute) {
                    simRoute = route; simDist = 0.0; simOffLat = 0.0
                    simCum = DoubleArray(route?.size ?: 0)
                    if (route != null) for (i in 1 until route.size) simCum[i] = simCum[i - 1] + Geo.distM(route[i - 1][0], route[i - 1][1], route[i][0], route[i][1])
                }
                if (route != null && route.size > 2 && simDist < simCum.last()) {
                    simDist += 14.0   // 14 m/detik, sekitar 50 km/j
                    simTick++
                    if (simTick == offAt) simOffLat = 0.0018
                    var i = 1
                    while (i < route.size - 1 && simCum[i] < simDist) i++
                    val seg = (simCum[i] - simCum[i - 1]).coerceAtLeast(0.01)
                    val f = ((simDist - simCum[i - 1]) / seg).coerceIn(0.0, 1.0)
                    val la = route[i - 1][0] + (route[i][0] - route[i - 1][0]) * f
                    val lo = route[i - 1][1] + (route[i][1] - route[i - 1][1]) * f
                    val l = Location("sim")
                    l.latitude = la + simOffLat; l.longitude = lo; l.speed = 14f; l.accuracy = 5f; l.time = System.currentTimeMillis()
                    l.bearing = bearingDeg(route[i - 1][0], route[i - 1][1], route[i][0], route[i][1])
                    AppState.locLocked = true
                    onLoc(l)
                }
                h.postDelayed(this, 1000)
            }
        })
    }

    private fun computeDestRoute() {
        val loc = AppState.location ?: return
        val d = AppState.dest ?: return
        lastDestLoc = loc
        val r = RouteRepo.route(loc.latitude, loc.longitude, d.lat, d.lon)
        android.util.Log.i("RushRoute", "rute baru dari ${loc.latitude},${loc.longitude}: ${r?.pts?.size ?: -1} titik, ${r?.distM?.toInt() ?: -1} m")
        if (AppState.dest === d) {
            if (r != null) {
                AppState.destRoute = r.pts
                AppState.destDistM = r.distM
                AppState.destMin = r.min
                AppState.destStatus = ""
                AppState.guideSteps = r.steps
                AppState.guideTotalM = r.steps.sumOf { it.distM }.coerceAtLeast(1.0)
                AppState.guideIdx = 1
                if (AppState.guiding) updateGuidance(loc)
            } else {
                AppState.destStatus = "Rute gagal dihitung, memakai garis lurus"
            }
            AppState.changed()
        }
    }

}
