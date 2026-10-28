package id.kahyangan.navneon

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Contoh pemakaian NavCardView. Tombol bawah: simulasi berkendara, hentikan, ganti tema.
 * Parameter uji lewat adb: --ed lat/lon (kunci posisi), --ed dlat/dlon (tujuan), --ez guide, --ez sim, --ei offroute, --ez red.
 */
class MainActivity : Activity() {
    private lateinit var card: NavCardView
    private var red = false

    // rumah dan Lapangan Pangsar (Ambarawa) untuk simulasi bawaan
    private val homeLat = -7.261166; private val homeLon = 110.411939
    private val destLat = -7.2666973; private val destLon = 110.4054526

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        NavEngine.init(this)
        card = NavCardView(this)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 8, 24, 8)
            fun btn(label: String, onClick: () -> Unit) = Button(this@MainActivity).apply {
                text = label; setTextColor(Color.WHITE); setBackgroundColor(Color.argb(150, 20, 20, 40)); isAllCaps = false
                setOnClickListener { onClick() }
            }.also { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 6, 0) }) }
            btn("Simulasi ke Lapangan Pangsar") { startDemo(-1) }
            btn("Simulasi + keluar jalur") { startDemo(12) }
            btn("Hentikan") { NavEngine.stopGuidance(); NavEngine.clearDestination() }
            btn("Ganti tema") { red = !red; card.setTheme(if (red) "red" else "purple") }
        }
        // kartu di atas, deretan tombol demo di bawahnya agar tidak menutupi bar tujuan
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(card, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        card.onSearch = { showSearch() }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) NavEngine.startGps(this)
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)

        // parameter uji
        val it = intent
        if (it.getBooleanExtra("red", false)) { red = true; card.setTheme("red") }
        if (it.hasExtra("lat") && it.hasExtra("lon")) NavEngine.lockAt(it.getDoubleExtra("lat", 0.0), it.getDoubleExtra("lon", 0.0))
        if (it.hasExtra("dlat") && it.hasExtra("dlon")) {
            NavEngine.setDestination(AppState.Place("Uji", "", it.getDoubleExtra("dlat", 0.0), it.getDoubleExtra("dlon", 0.0)))
            if (it.getBooleanExtra("guide", false)) NavEngine.startGuidance()
            if (it.getBooleanExtra("sim", false)) NavEngine.startSim(it.getIntExtra("offroute", -1))
        }
    }

    /** Cari tujuan lewat Nominatim (OpenStreetMap), lalu tetapkan dan mulai pemanduan. */
    private fun showSearch() {
        val et = android.widget.EditText(this).apply { hint = "Nama tempat atau alamat" }
        android.app.AlertDialog.Builder(this).setTitle("Cari tujuan").setView(et)
            .setPositiveButton("Cari") { _, _ -> runSearch(et.text.toString()) }
            .setNegativeButton("Batal", null).show()
    }

    private fun runSearch(q: String) {
        if (q.isBlank()) return
        val loc = AppState.location
        Thread {
            val res = try { PlaceSearch.search(q, loc?.latitude, loc?.longitude) } catch (e: Exception) { null }
            runOnUiThread {
                if (res == null) { AppState.say("Pencarian gagal. Periksa internet."); return@runOnUiThread }
                if (res.isEmpty()) { AppState.say("Tidak ada hasil"); return@runOnUiThread }
                val names = res.map { it.name + (if (it.address.isNotEmpty()) "\n" + it.address else "") }.toTypedArray()
                android.app.AlertDialog.Builder(this).setTitle("Hasil").setItems(names) { _, i ->
                    NavEngine.setDestination(res[i])
                    NavEngine.startGuidance()
                }.show()
            }
        }.start()
    }

    private fun startDemo(offAt: Int) {
        NavEngine.lockAt(homeLat, homeLon)
        NavEngine.setDestination(AppState.Place("Lapangan Pangsar", "Ambarawa", destLat, destLon))
        NavEngine.startGuidance()
        NavEngine.startSim(offAt)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) NavEngine.startGps(this)
    }
}
