package id.kahyangan.navneon

import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Mencari SPBU terdekat. Hanya dijalankan saat pengguna menekan tombol, tidak otomatis. */
object PumpRepo {
    private val mirrors = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    private fun query(lat: Double, lon: Double, radius: Double): List<AppState.Pump> {
        val dLat = radius / 111000.0
        val dLon = radius / (111000.0 * Math.cos(Math.toRadians(lat)))
        val bbox = "%.5f,%.5f,%.5f,%.5f".format(java.util.Locale.US, lat - dLat, lon - dLon, lat + dLat, lon + dLon)
        val q = "[out:json][timeout:40];node[\"amenity\"=\"fuel\"]($bbox);out 60;"
        val pool = Executors.newFixedThreadPool(mirrors.size)
        try {
            val body = pool.invokeAny(mirrors.map { u -> Callable { Http.postForm(u, "data=" + Http.enc(q), 45000) } }, 50, TimeUnit.SECONDS)
            val els = JSONObject(body).optJSONArray("elements") ?: return emptyList()
            val out = ArrayList<AppState.Pump>()
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                val tags = e.optJSONObject("tags")
                var name = ""
                if (tags != null) {
                    for (k in listOf("name", "brand", "operator")) {
                        val v = tags.optString(k)
                        if (v.isNotEmpty()) { name = v; break }
                    }
                }
                if (name.isEmpty()) name = "SPBU tanpa nama"
                val la = e.getDouble("lat")
                val lo = e.getDouble("lon")
                out.add(AppState.Pump(la, lo, name, Geo.distM(lat, lon, la, lo)))
            }
            return out
        } finally {
            pool.shutdownNow()
        }
    }

    /** SPBU terdekat dari posisi ini, atau null bila tidak ada dalam 15 km. Melempar bila jaringan gagal. */
    fun nearest(lat: Double, lon: Double): AppState.Pump? {
        var list = query(lat, lon, 5000.0)
        if (list.isEmpty()) list = query(lat, lon, 15000.0)
        return list.minByOrNull { it.distM }
    }
}
