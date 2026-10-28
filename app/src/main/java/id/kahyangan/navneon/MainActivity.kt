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
            setPadding(24, 0, 24, 18)
            fun btn(label: String, onClick: () -> Unit) = Button(this@MainActivity).apply {
                text = label; setTextColor(Color.WHITE); setBackgroundColor(Color.argb(150, 20, 20, 40)); isAllCaps = false
                setOnClickListener { onClick() }
            }.also { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 6, 0) }) }
            btn("Simulasi ke Lapangan Pangsar") { startDemo(-1) }
            btn("Simulasi + keluar jalur") { startDemo(12) }
            btn("Hentikan") { NavEngine.stopGuidance(); NavEngine.clearDestination() }
            btn("Ganti tema") { red = !red; card.setTheme(if (red) "red" else "purple") }
        }
        val root = FrameLayout(this)
        root.addView(card, FrameLayout.LayoutParams(-1, -1))
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)

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
