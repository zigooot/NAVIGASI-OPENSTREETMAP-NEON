package id.kahyangan.navneon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.util.Collections
import java.util.concurrent.Executors

/** Ubin peta OpenStreetMap (online) atau peta offline mapsforge bila wilayahnya sudah diunduh. */
object TileCache {
    const val DARK = 2

    private val cache = LruCache<String, Bitmap>(420)
    private val offKeys = Collections.synchronizedSet(HashSet<String>())
    private val pending = Collections.synchronizedSet(HashSet<String>())
    private val failed = Collections.synchronizedMap(HashMap<String, Long>())
    private val pool = Executors.newFixedThreadPool(6)

    /** Naik setiap kali sebuah ubin selesai dimuat, supaya mosaik peta tahu kapan perlu disusun ulang. */
    @Volatile var version = 0

    private fun url(z: Int, x: Int, y: Int): String = "https://tile.openstreetmap.org/$z/$x/$y.png"

    fun attribution(src: Int): String = "© OpenStreetMap contributors"

    fun get(src: Int, z: Int, x: Int, y: Int): Bitmap? {
        val n = 1 shl z
        if (y < 0 || y >= n) return null
        val xx = ((x % n) + n) % n
        val key = "$src/$z/$xx/$y"
        cache.get(key)?.let { return it }
        val ft = failed[key]
        if (ft != null) { if (System.currentTimeMillis() - ft < 15000) return null else failed.remove(key) }
        if (!pending.add(key)) return null
        // peta gelap memakai peta offline bila wilayahnya sudah diunduh
        val off = if (src == DARK) OfflineMaps.regionForTile(z, xx, y) else null
        pool.execute {
            try {
                val ob = if (off != null) OfflineMaps.render(off, z, xx, y) else null
                if (ob != null) {
                    cache.put(key, ob); offKeys.add(key)
                } else {
                    val b = Http.bytes(url(z, xx, y), 10000)
                    BitmapFactory.decodeByteArray(b, 0, b.size)?.let { cache.put(key, it) }
                }
            } catch (_: Exception) {
                failed[key] = System.currentTimeMillis()
            } finally {
                pending.remove(key)
                version++
                AppState.changed()
            }
        }
        return null
    }

    fun busy(): Boolean = pending.isNotEmpty()

    /** True bila ubin gelap ini berasal dari peta offline (tidak perlu dibalik warnanya). */
    fun isOffline(z: Int, x: Int, y: Int): Boolean { val n = 1 shl z; return offKeys.contains("$DARK/$z/${((x % n) + n) % n}/$y") }

    fun clearFailed() { failed.clear() }

    fun clearAll() { cache.evictAll(); failed.clear() }
}
