package id.kahyangan.navneon

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.StreamRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh

/**
 * Peta offline format mapsforge per wilayah (katalog peta-hu, data © OpenStreetMap, ODbL).
 * File diunduh ke penyimpanan aplikasi, lalu ubin 256 px dirender langsung di ponsel.
 */
object OfflineMaps {
    class Region(val id: String, val nama: String, val cakupan: String, val size: Long, val bbox: DoubleArray) // minLat, minLon, maxLat, maxLon

    val regions = listOf(
        Region("nusa-tenggara", "Bali & Nusa Tenggara", "Bali, Lombok, Sumbawa, Flores, Sumba, Timor", 171141753L, doubleArrayOf(-11.662985, 114.408874, -6.675509, 128.485096)),
        Region("java", "Jawa", "Jawa, Madura, Jakarta, Banten, Yogyakarta", 909114880L, doubleArrayOf(-9.541155, 104.139393, -3.951935, 116.581418)),
        Region("sumatra", "Sumatra", "Sumatra, Riau, Kepulauan Riau, Bangka Belitung", 272403072L, doubleArrayOf(-7.17719, 92.41697, 7.07911, 109.768523)),
        Region("kalimantan", "Kalimantan", "Kalimantan Barat, Tengah, Selatan, Timur, Utara", 145248870L, doubleArrayOf(-5.59958, 104.96063, 4.499767, 119.926747)),
        Region("sulawesi", "Sulawesi", "Sulawesi Utara, Tengah, Selatan, Tenggara, Barat, Gorontalo", 153626329L, doubleArrayOf(-8.02115, 116.251826, 5.971223, 127.952265)),
        Region("maluku", "Maluku", "Maluku & Maluku Utara", 28897323L, doubleArrayOf(-9.524903, 123.89282, 3.727232, 135.411982)),
        Region("papua", "Papua", "Papua & Papua Barat", 38257223L, doubleArrayOf(-9.495116, 128.281858, 2.075967, 141.058957))
    )

    private const val BASE = "https://github.com/chabibachmad-dev/peta-hu/releases/download/peta-2026-10/"
    private var app: Application? = null
    private var dir: File? = null

    @Volatile var dlId: String? = null
    @Volatile var dlBytes = 0L
    @Volatile var dlTotal = 1L
    @Volatile private var cancel = false

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext as Application
        dir = (ctx.getExternalFilesDir("maps") ?: File(ctx.filesDir, "maps")).also { it.mkdirs() }
    }

    private fun file(id: String) = File(dir, "$id.map")

    fun installed(id: String): Boolean = dir != null && file(id).exists()

    fun installedCount(): Int = if (dir == null) 0 else regions.count { installed(it.id) }

    fun inBox(r: Region, lat: Double, lon: Double) = lat >= r.bbox[0] && lat <= r.bbox[2] && lon >= r.bbox[1] && lon <= r.bbox[3]

    /** Wilayah terpasang terkecil yang memuat titik ini, atau null. */
    fun regionFor(lat: Double, lon: Double): Region? =
        regions.filter { installed(it.id) && inBox(it, lat, lon) }.minByOrNull { it.size }

    // ---------- unduhan ----------
    fun download(id: String) {
        val r = regions.firstOrNull { it.id == id } ?: return
        if (dlId != null || dir == null) return
        val part = File(dir, "$id.map.part")
        dlId = id; cancel = false; dlBytes = part.length(); dlTotal = r.size
        Thread {
            try {
                val con = URL(BASE + "$id.map").openConnection() as HttpURLConnection
                con.connectTimeout = 15000; con.readTimeout = 20000; con.instanceFollowRedirects = true
                if (dlBytes > 0) con.setRequestProperty("Range", "bytes=$dlBytes-")
                val code = con.responseCode
                if (code != 200 && code != 206) throw IOException("HTTP $code")
                if (code == 200) { part.delete(); dlBytes = 0 }
                var last = 0L
                con.inputStream.use { ins ->
                    FileOutputStream(part, code == 206).use { os ->
                        val buf = ByteArray(64 * 1024)
                        while (!cancel) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n); dlBytes += n
                            val now = System.currentTimeMillis()
                            if (now - last > 400) { last = now; AppState.changed() }
                        }
                    }
                }
                if (!cancel) {
                    if (part.length() < r.size * 0.98) throw IOException("file tidak lengkap")
                    part.renameTo(file(id))
                    AppState.say("Peta ${r.nama} siap dipakai")
                }
            } catch (e: Exception) {
                android.util.Log.w("RushMaps", "unduhan gagal: $e")
                if (!cancel) AppState.say("Unduhan peta gagal. Ketuk Unduh untuk melanjutkan.")
            } finally {
                dlId = null; AppState.changed()
            }
        }.start()
    }

    fun cancelDownload() { cancel = true }

    fun delete(id: String) {
        synchronized(this) {
            renderers.remove(id)?.second?.close()
            file(id).delete(); File(dir, "$id.map.part").delete()
        }
        AppState.changed()
    }

    fun partialBytes(id: String): Long = if (dir == null) 0L else File(dir, "$id.map.part").length()

    // ---------- gambar ubin ----------
    private class Rend(val store: MapFile, val renderer: DatabaseRenderer, val future: RenderThemeFuture) { fun close() { try { store.close() } catch (_: Exception) {} } }
    private val renderers = HashMap<String, Pair<Rend, Rend>>()
    @Volatile private var themeName = "purple"

    /** Ganti warna peta mengikuti tema aplikasi; penggambar dibuat ulang saat dipakai berikutnya. */
    @Synchronized
    fun setTheme(name: String) {
        if (name == themeName) return
        themeName = name
        renderers.values.forEach { it.first.close() }
        renderers.clear()
    }

    /** Tema gambar peta gaya cyberpunk (sintaks render theme mapsforge). */
    private fun themeXml(): String {
        val red = themeName == "red"
        val bg = if (red) "#070505" else "#08071a"
        val land = if (red) "#0d0808" else "#0b0a22"
        val water = if (red) "#2b1117" else "#07304f"
        val park = if (red) "#150d0c" else "#0c2a2c"
        val resi = if (red) "#120b0b" else "#0f0e2a"
        val indus = if (red) "#170e0e" else "#151030"
        val bld = if (red) "#321618" else "#2a1f5c"
        val bldLine = if (red) "#5a2428" else "#4b3a9a"
        val minor = if (red) "#5c1a20" else "#3d3199"
        val mid = if (red) "#8e2229" else "#6c58e8"
        val main = if (red) "#c93a2e" else "#a066ff"
        val moto = if (red) "#ffd0c0" else "#2ee8ff"
        val label = if (red) "#ffe0dc" else "#ebe6ff"
        val poi = if (red) "#ff6a3d" else "#ff3fd2"
        return """<?xml version="1.0" encoding="UTF-8"?>
<rendertheme xmlns="http://mapsforge.org/renderTheme" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://mapsforge.org/renderTheme https://raw.githubusercontent.com/mapsforge/mapsforge/master/resources/renderTheme.xsd" version="4" map-background="$land" map-background-outside="$bg">
  <rule e="way" k="natural" v="issea|sea"><area fill="$water"/></rule>
  <rule e="way" k="natural" v="nosea"><area fill="$land"/></rule>
  <rule e="way" k="landuse" v="residential|retail|commercial"><area fill="$resi"/></rule>
  <rule e="way" k="landuse" v="industrial|railway|military"><area fill="$indus"/></rule>
  <rule e="way" k="landuse" v="forest|grass|meadow|farmland|orchard|vineyard|cemetery|recreation_ground|village_green"><area fill="$park"/></rule>
  <rule e="way" k="leisure" v="park|garden|pitch|golf_course|nature_reserve|playground"><area fill="$park"/></rule>
  <rule e="way" k="natural" v="wood|scrub|grassland|wetland|heath"><area fill="$park"/></rule>
  <rule e="way" k="natural" v="water|bay"><area fill="$water"/></rule>
  <rule e="way" k="landuse" v="reservoir|basin"><area fill="$water"/></rule>
  <rule e="way" k="waterway" v="river|canal|stream|drain|ditch" zoom-min="11"><line stroke="$water" stroke-width="1.6" stroke-linecap="round"/></rule>
  <rule e="way" k="railway" v="rail|light_rail|subway|tram" zoom-min="12"><line stroke="$bldLine" stroke-width="0.9" stroke-dasharray="6,5"/></rule>
  <rule e="way" k="building" v="*" zoom-min="15"><area fill="$bld" stroke="$bldLine" stroke-width="0.4"/></rule>
  <rule e="way" k="highway" v="service|track|path|footway|pedestrian|cycleway|steps|bridleway" zoom-min="16"><line stroke="$minor" stroke-width="0.9" stroke-linecap="round"/></rule>
  <rule e="way" k="highway" v="residential|unclassified|living_street|road" zoom-min="13"><line stroke="$minor" stroke-width="1.5" stroke-linecap="round"/></rule>
  <rule e="way" k="highway" v="tertiary|tertiary_link|secondary_link"><line stroke="$mid" stroke-width="2.0" stroke-linecap="round"/></rule>
  <rule e="way" k="highway" v="secondary|primary_link"><line stroke="$mid" stroke-width="2.6" stroke-linecap="round"/></rule>
  <rule e="way" k="highway" v="primary|trunk_link"><line stroke="$main" stroke-width="3.0" stroke-linecap="round"/></rule>
  <rule e="way" k="highway" v="trunk|motorway|motorway_link"><line stroke="$moto" stroke-width="3.4" stroke-linecap="round"/></rule>
  <rule e="way" k="highway" v="primary|secondary|tertiary|trunk|motorway|residential|unclassified" zoom-min="16"><pathText k="name" font-style="bold" font-size="12" fill="$label" stroke="$bg" stroke-width="2.0"/></rule>
  <rule e="node" k="place" v="city|town"><caption k="name" font-style="bold" font-size="20" fill="$label" stroke="$bg" stroke-width="3.0"/></rule>
  <rule e="node" k="place" v="village|suburb|neighbourhood|hamlet|quarter" zoom-min="12"><caption k="name" font-style="bold" font-size="15" fill="$label" stroke="$bg" stroke-width="2.5"/></rule>
  <rule e="node" k="amenity" v="fuel" zoom-min="14"><circle radius="4" fill="$poi" scale-radius="true"/><caption k="name" dy="-9" font-style="bold" font-size="11" fill="$poi" stroke="$bg" stroke-width="2.0"/></rule>
</rendertheme>"""
    }
    private var model: DisplayModel? = null

    @Synchronized
    private fun rend(id: String): Rend? {
        renderers[id]?.let { return it.first }
        val a = app ?: return null
        if (!file(id).exists()) return null
        AndroidGraphicFactory.createInstance(a)
        DisplayModel.setDeviceScaleFactor(1.7f)
        val dm = model ?: DisplayModel().also { it.setFixedTileSize(256); model = it }
        val store = MapFile(file(id))
        val gf = AndroidGraphicFactory.INSTANCE
        val future = RenderThemeFuture(gf, StreamRenderTheme("/", ByteArrayInputStream(themeXml().toByteArray(Charsets.UTF_8))), dm)
        future.run()
        val r = Rend(store, DatabaseRenderer(store, gf, InMemoryTileCache(2), null, true, false, null), future)
        renderers[id] = Pair(r, r)
        return r
    }

    private fun tileLat(y: Int, z: Int) = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * y / (1 shl z)))))
    private fun tileLon(x: Int, z: Int) = x.toDouble() / (1 shl z) * 360.0 - 180.0

    /** Wilayah yang bisa menggambar ubin ini (titik tengahnya ada di dalam wilayah terpasang). */
    fun regionForTile(z: Int, x: Int, y: Int): Region? = regionFor(tileLat(y, z) * 0.5 + tileLat(y + 1, z) * 0.5, tileLon(x, z) * 0.5 + tileLon(x + 1, z) * 0.5)

    @Synchronized
    fun render(r: Region, z: Int, x: Int, y: Int): Bitmap? {
        return try {
            val rd = rend(r.id) ?: return null
            val dm = model ?: return null
            // ubin tanpa data (di luar daratan file, misalnya Bali di kotak batas Jawa) dilewati agar diganti ubin online
            val data = rd.store.readMapData(Tile(x, y, z.toByte(), 256))
            if (data == null || (data.ways.isEmpty() && data.pois.isEmpty() && !data.isWater)) return null
            val job = RendererJob(Tile(x, y, z.toByte(), 256), rd.store, rd.future, dm, 1f, false, false)
            val tb = rd.renderer.executeJob(job) ?: return null
            val src = AndroidGraphicFactory.getBitmap(tb)
            val out = src.copy(Bitmap.Config.ARGB_8888, false)
            tb.decrementRefCount()
            out
        } catch (e: Exception) {
            android.util.Log.w("RushMaps", "gagal gambar ubin $z/$x/$y: $e")
            null
        }
    }
}
