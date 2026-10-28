package id.kahyangan.navneon

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

/**
 * Kartu navigasi: peta OpenStreetMap bergaya neon (tanpa satelit), mode navigasi yang mengikuti mobil,
 * kompas (peta berputar mengikuti arah lurus mobil), kartu belokan, dan bar tujuan.
 * Digambar penuh dengan Canvas pada ruang desain setinggi 825,6 satuan.
 */
class NavCardView @JvmOverloads constructor(private val ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private class Hit(val r: RectF, val id: String)

    private val H0 = 825.6f
    private var Wd = 1280f

    private val BG get() = pc(P.bg)
    private val CY get() = pc(P.cy)
    private val MG get() = pc(P.mg)
    private val PU get() = pc(P.pu)
    private val PUS get() = pc(P.pus)
    private val TXT = Color.parseColor("#ECEEF8")
    private val MUTED get() = pc(P.muted)

    private var scale = 1f
    private val hits = ArrayList<Hit>()
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val faces = HashMap<Int, Typeface>()
    private val icons = HashMap<Int, Drawable>()
    private val glassCache = HashMap<String, Bitmap>()
    private var cacheScale = -1f
    private var curDx = 0f
    private var fast = false
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val prefs = ctx.getSharedPreferences("navneon", Context.MODE_PRIVATE)

    /** Dipanggil saat tombol Cari tujuan ditekan; tampilkan kolom pencarian di sini. */
    var onSearch: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.BLACK)
        AppState.onChange = { postInvalidateOnAnimation() }
    }

    // ---------- tema ----------
    fun setTheme(name: String) {
        P.set(name); OfflineMaps.setTheme(P.name); TileCache.clearAll(); glassCache.clear()
        postInvalidateOnAnimation()
    }

    fun toggleCompass() { prefs.edit().putBoolean("compass", !compassOn()).apply(); postInvalidateOnAnimation() }

    // ---------- gambar ----------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val s = height / H0
        if (cacheScale != s) { glassCache.clear(); cacheScale = s }
        scale = s; Wd = width / s
        hits.clear(); curDx = 0f; fast = false
        canvas.save(); canvas.scale(s, s)
        drawCard(canvas)
        canvas.restore()
        if (fast || TileCache.busy()) postInvalidateOnAnimation() else postInvalidateDelayed(250)
    }

    private fun drawCard(c: Canvas) {
        val l = 24f; val t = 24f; val w = Wd - 48f; val h = H0 - 48f
        glass(c, l, t, l + w, t + h)
        val ml = l + 16f; val mt = t + 16f; val mw = w - 32f; val mh = h - 32f
        val clip = Path().apply { addRoundRect(RectF(ml, mt, ml + mw, mt + mh), 17f, 17f, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        pt.reset(); pt.color = pc(P.mapbg); c.drawRect(ml, mt, ml + mw, mt + mh, pt)
        val loc = AppState.location
        if (loc == null) {
            text(c, AppState.locStatus, ml + mw / 2, mt + mh / 2, 24f, MUTED, 400, Paint.Align.CENTER, mw - 20f)
        } else {
            interpPos(loc, System.currentTimeMillis())
            drawMap(c, ml, mt, mw, mh, dispLat, dispLon)
        }
        c.restore()
        controls(c, ml, mt, mw, mh)
        AppState.guideLive?.let { navOverlay(c, it, ml, mt, mw, mh, false) }
        toast(c, ml, mt, mw, mh)
    }

    private fun toast(c: Canvas, ml: Float, mt: Float, mw: Float, mh: Float) {
        if (System.currentTimeMillis() > AppState.toastUntil || AppState.toast.isEmpty()) return
        val w = min(mw - 40f, measure(AppState.toast, 26f) + 70f)
        val l = ml + (mw - w) / 2; val t = mt + 20f
        pt.reset(); pt.isAntiAlias = true; pt.color = cA(235, P.panel)
        c.drawRoundRect(RectF(l, t, l + w, t + 64f), 20f, 20f, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2f; pt.color = cA(140, P.cy)
        c.drawRoundRect(RectF(l, t, l + w, t + 64f), 20f, 20f, pt)
        text(c, AppState.toast, l + w / 2, t + 42f, 26f, TXT, 500, Paint.Align.CENTER, w - 40f)
        fast = true
    }

    /** Tombol zoom, posisi saya, kompas, dan bar tujuan. */
    private fun controls(c: Canvas, ml: Float, mt: Float, mw: Float, mh: Float) {
        val right = ml + mw - 12f
        val st0 = mt + 12f
        // cari tujuan dan SPBU terdekat
        val sw2 = 250f; val sh2 = 64f
        val sl = right - sw2
        frostBtn(c, sl, st0, sl + sw2, st0 + sh2, true, 18f, true)
        icon(c, R.drawable.ic_search, sl + 18f, st0 + 16f, 32f, Color.WHITE)
        text(c, "Cari tujuan", sl + 62f, st0 + 42f, 26f, Color.WHITE, 600)
        hit("search", sl, st0, sl + sw2, st0 + sh2)
        val pw = 270f; val pl = sl - 12f - pw
        val pumpOn = AppState.dest?.kind == "pump"
        frostBtn(c, pl, st0, pl + pw, st0 + sh2, pumpOn, 18f)
        icon(c, R.drawable.ic_pump, pl + 18f, st0 + 16f, 32f, if (pumpOn) MG else CY)
        text(c, if (pumpOn) "Batal SPBU" else "SPBU terdekat", pl + 62f, st0 + 42f, 26f, if (pumpOn) MG else CY, 600, Paint.Align.LEFT, pw - 76f)
        hit("pump-find", pl, st0, pl + pw, st0 + sh2)
        // zoom, posisi saya, kompas
        val st = st0 + 88f
        val s = 64f
        roundBtn(c, "zin", R.drawable.ic_plus, right - s, st, s, TXT)
        roundBtn(c, "zout", R.drawable.ic_minus, right - s, st + 74f, s, TXT)
        roundBtn(c, "zctr", R.drawable.ic_locate, right - s, st + 148f, s, CY)
        roundBtn(c, "map-compass", R.drawable.ic_nav, right - s, st + 222f, s, if (compassOn()) CY else MUTED)
        text(c, if (compassOn()) "Kompas" else "Utara", right - s - 12f, st + 222f + 42f, 22f, TXT, 500, Paint.Align.RIGHT)

        val d = AppState.dest
        if (d != null) {
            val bl = ml + 16f; val bh = 128f; val bt = mt + mh - bh - 16f; val bw = mw - 32f
            pt.reset(); pt.isAntiAlias = true; pt.color = cA(225, P.dark1)
            c.drawRoundRect(RectF(bl, bt, bl + bw, bt + bh), 22f, 22f, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 2f; pt.color = cA(110, P.mg)
            c.drawRoundRect(RectF(bl, bt, bl + bw, bt + bh), 22f, 22f, pt)
            val tw = bw - 28f - 320f - 150f - 40f
            text(c, d.name, bl + 28f, bt + 52f, 34f, TXT, 600, Paint.Align.LEFT, tw)
            text(c, destSub(), bl + 28f, bt + 94f, 28f, MG, 600, Paint.Align.LEFT, tw)
            val gl = bl + bw - 150f - 320f - 28f; val gt = bt + 32f
            frostBtn(c, gl, gt, gl + 320f, gt + 64f, true, 18f, true)
            text(c, if (AppState.guiding) "Hentikan pemanduan" else "Mulai navigasi", gl + 160f, gt + 42f, 24f, Color.WHITE, 600, Paint.Align.CENTER, 300f)
            hit(if (AppState.guiding) "guide-stop" else "dest-go", gl, gt, gl + 320f, gt + 64f)
            val xl = bl + bw - 150f - 14f
            frostBtn(c, xl, gt, xl + 150f, gt + 64f, false, 18f)
            text(c, "Batal", xl + 75f, gt + 42f, 24f, TXT, 600, Paint.Align.CENTER)
            hit("dest-clear", xl, gt, xl + 150f, gt + 64f)
        }
    }

    // ---------- sentuhan ----------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP) {
            val dx = e.x / scale; val dy = e.y / scale
            for (i in hits.indices.reversed()) {
                val h = hits[i]
                if (h.r.contains(dx, dy)) { handle(h.id); performClick(); return true }
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun handle(id: String) {
        when (id) {
            "zin" -> AppState.mapZoom = min(4, AppState.mapZoom + 1)
            "zout" -> AppState.mapZoom = max(-4, AppState.mapZoom - 1)
            "zctr" -> AppState.mapZoom = 0
            "search" -> onSearch?.invoke()
            "pump-find" -> if (AppState.dest?.kind == "pump") NavEngine.clearDestination() else NavEngine.findNearestPump()
            "map-compass" -> prefs.edit().putBoolean("compass", !compassOn()).apply()
            "dest-go" -> NavEngine.startGuidance()
            "guide-stop" -> NavEngine.stopGuidance()
            "dest-clear" -> NavEngine.clearDestination()
        }
        postInvalidateOnAnimation()
    }

    // ---------- util ----------
    private fun face(w: Int): Typeface = faces.getOrPut(w) {
        try {
            Typeface.Builder(ctx.assets, "exo2.ttf").setFontVariationSettings("'wght' $w").build() ?: fallback(w)
        } catch (e: Exception) { fallback(w) }
    }

    private fun fallback(w: Int): Typeface = when {
        w >= 700 -> Typeface.create("sans-serif", Typeface.BOLD)
        w >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }

    /** Layar mobil sekitar 15x8 cm: teks 17-23 unit terlalu kecil, dinaikkan ke 24. */
    private fun adj(s: Float): Float = if (s >= 17f && s < 24f) 24f else s

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        w: Int = 400, align: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f, glow: Int = 0, italic: Boolean = false
    ) {
        tp.reset()
        tp.isAntiAlias = true
        tp.textSize = adj(size)
        tp.color = color
        tp.typeface = if (italic) Typeface.create(face(w), Typeface.ITALIC) else face(w)
        tp.textAlign = align
        if (glow != 0) tp.setShadowLayer(adj(size) * 0.5f, 0f, 0f, glow)
        val out = if (maxW > 0 && tp.measureText(s) > maxW) TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString() else s
        c.drawText(out, x, y, tp)
        tp.clearShadowLayer()
    }

    private fun measure(s: String, size: Float, w: Int = 400): Float {
        tp.reset(); tp.textSize = adj(size); tp.typeface = face(w)
        return tp.measureText(s)
    }

    private fun icon(c: Canvas, res: Int, l: Float, t: Float, size: Float, color: Int) {
        val d = icons.getOrPut(res) { ContextCompat.getDrawable(ctx, res)!!.mutate() }
        d.setBounds(l.toInt(), t.toInt(), (l + size).toInt(), (t + size).toInt())
        d.setTint(color)
        d.draw(c)
    }

    private fun hit(id: String, l: Float, t: Float, r: Float, b: Float) { hits.add(Hit(RectF(l + curDx, t, r + curDx, b), id)) }


    // ---------- kaca ----------
    private fun glass(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val m = 24f
        val w = r - l
        val h = b - t
        val key = "${w.toInt()}x${h.toInt()}"
        val bmp = glassCache.getOrPut(key) {
            val bw = ((w + 2 * m) * scale).toInt().coerceAtLeast(1)
            val bh = ((h + 2 * m) * scale).toInt().coerceAtLeast(1)
            val bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bm)
            cv.scale(scale, scale)
            cv.translate(m, m)
            glassRaw(cv, 0f, 0f, w, h)
            bm
        }
        c.drawBitmap(bmp, null, RectF(l - m, t - m, r + m, b + m), bmpPaint)
    }

    private fun glassRaw(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val rr = RectF(l, t, r, b)
        pt.reset(); pt.isAntiAlias = true
        pt.shader = LinearGradient(l, t, l + (r - l) * 0.3f, b, intArrayOf(cA(128, P.gA), cA(150, P.gB), cA(160, P.gC)), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(rr, 24f, 24f, pt)
        pt.shader = null; pt.style = Paint.Style.STROKE; pt.strokeWidth = 2f; pt.color = cA(60, P.tint)
        c.drawRoundRect(rr, 24f, 24f, pt)
        // cahaya tepi atas (cyan) dan bawah (ungu)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 4f; pt.strokeCap = Paint.Cap.ROUND
        pt.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        pt.color = cA(150, P.cy)
        c.drawLine(l + 36f, t, r - 36f, t, pt)
        pt.color = cA(130, P.pu)
        c.drawLine(l + 36f, b, r - 36f, b, pt)
        pt.maskFilter = null
    }

    /** Tombol kaca buram (frosted glass): isi susu transparan, kilau atas, tepi terang, bayangan lembut. Di-cache per ukuran dan keadaan. */
    private fun frostBtn(c: Canvas, l: Float, t: Float, r: Float, b: Float, active: Boolean, rad: Float = 26f, accent: Boolean = false) {
        val m = 26f
        val w = r - l; val h = b - t
        val key = "fb${w.toInt()}x${h.toInt()}$active${rad.toInt()}$accent"
        val bmp = glassCache.getOrPut(key) {
            val bm = Bitmap.createBitmap(((w + 2 * m) * scale).toInt().coerceAtLeast(1), ((h + 2 * m) * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val cv = Canvas(bm); cv.scale(scale, scale); cv.translate(m, m)
            val rr = RectF(0f, 0f, w, h)
            // bayangan lembut di bawah
            pt.reset(); pt.isAntiAlias = true; pt.color = cA(150, P.scrim); pt.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
            cv.drawRoundRect(RectF(2f, 6f, w - 2f, h + 4f), rad, rad, pt)
            pt.maskFilter = null
            // isi susu: terang di atas, memudar ke bawah, sedikit warna tema
            pt.shader = if (accent) LinearGradient(0f, 0f, w, 0f, cA(238, P.pu), cA(238, P.mg), Shader.TileMode.CLAMP) else LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.argb(if (active) 120 else 92, 255, 255, 255), cA(if (active) 80 else 56, P.tint), cA(if (active) 64 else 38, P.pus)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            cv.drawRoundRect(rr, rad, rad, pt)
            pt.shader = null
            // kilau lembut di separuh atas
            cv.save(); cv.clipRect(0f, 0f, w, h * 0.5f)
            pt.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            cv.drawRoundRect(RectF(2f, 2f, w - 2f, h), rad - 2f, rad - 2f, pt)
            pt.shader = null; cv.restore()
            // tepi: terang di kiri atas, redup di kanan bawah
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 1.8f
            pt.shader = LinearGradient(0f, 0f, w, h, intArrayOf(Color.argb(190, 255, 255, 255), cA(70, P.cy), Color.argb(60, 255, 255, 255)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            cv.drawRoundRect(rr, rad, rad, pt)
            pt.shader = null
            if (active) {
                pt.strokeWidth = 3f; pt.color = cA(230, P.cy); pt.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
                cv.drawRoundRect(rr, rad, rad, pt)
                pt.maskFilter = null; pt.strokeWidth = 2f
                cv.drawRoundRect(rr, rad, rad, pt)
            }
            bm
        }
        c.drawBitmap(bmp, null, RectF(l - m, t - m, r + m, b + m), bmpPaint)
    }

    // ---------- kartu belokan ----------
    /** Kartu belokan, lencana kecepatan, dan ringkasan rute dari navigasi Google Maps yang sedang berjalan. */
    private fun navOverlay(c: Canvas, n: AppState.NavLive, ml: Float, mt: Float, mw: Float, mh: Float, summary: Boolean = true) {
        val g = 10f
        val bg = Color.argb(232, 8, 14, 26)
        pt.reset(); pt.isAntiAlias = true; pt.color = bg
        val cwd = if (summary) 214f else 440f
        c.drawRoundRect(RectF(ml + g, mt + g, ml + g + cwd, mt + g + 98f), 14f, 14f, pt)
        n.icon?.let { c.drawBitmap(it, null, RectF(ml + g + 12f, mt + g + 14f, ml + g + 58f, mt + g + 60f), bmpPaint) }
        text(c, n.dist.ifEmpty { "--" }, ml + g + 66f, mt + g + 50f, 30f, TXT, 700, Paint.Align.LEFT, cwd - 76f)
        text(c, n.instruction, ml + g + 14f, mt + g + 84f, if (summary) 18f else 24f, MUTED, 400, Paint.Align.LEFT, cwd - 26f)
        hit("drive-on", ml + g, mt + g, ml + g + cwd, mt + g + 98f)
        // lencana kecepatan
        val sp = AppState.speedKmh
        if (sp >= 0f) {
            pt.reset(); pt.isAntiAlias = true; pt.color = Color.parseColor("#1F9A4A")
            c.drawRoundRect(RectF(ml + g, mt + g + 108f, ml + g + 72f, mt + g + 168f), 12f, 12f, pt)
            text(c, Math.round(sp).toString(), ml + g + 36f, mt + g + 146f, 30f, Color.WHITE, 700, Paint.Align.CENTER)
            text(c, "km/j", ml + g + 36f, mt + g + 162f, 14f, Color.WHITE, 500, Paint.Align.CENTER)
        }
        if (!summary) return
        // ringkasan di bawah
        val ph = 74f; val py = mt + mh - ph - g
        pt.reset(); pt.isAntiAlias = true; pt.color = bg
        c.drawRoundRect(RectF(ml + g, py, ml + mw - g, py + ph), 16f, 16f, pt)
        val cw = (mw - 2 * g) / 3f
        listOf(n.sisa to "sisa", n.durasi to "waktu", n.tiba to "tiba").forEachIndexed { i, (v, k) ->
            val cx = ml + g + cw * i + cw / 2
            text(c, v.ifEmpty { "--" }, cx, py + 34f, 22f, TXT, 600, Paint.Align.CENTER, cw - 6f)
            text(c, k, cx, py + 58f, 15f, MUTED, 400, Paint.Align.CENTER)
        }
    }

    private fun destSub(): String {
        val s = AppState.destStatus
        return when {
            AppState.destMin > 0 -> "${Geo.fmtKm(AppState.destDistM)}, ${AppState.destMin} mnt"
            s.isNotEmpty() && AppState.destDistM <= 0 -> s
            AppState.destDistM > 0 -> Geo.fmtKm(AppState.destDistM)
            else -> "menghitung..."
        }
    }

    private fun roundBtn(c: Canvas, id: String, res: Int, l: Float, t: Float, s: Float, tint: Int) {
        frostBtn(c, l, t, l + s, t + s, false, 16f)
        icon(c, res, l + s * 0.22f, t + s * 0.22f, s * 0.56f, tint)
        hit(id, l, t, l + s, t + s)
    }

    private val darkMap = Paint().apply {
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            -0.52f, 0f, 0f, 0f, 135f,
            0f, -0.58f, 0f, 0f, 148f,
            0f, 0f, -0.82f, 0f, 205f,
            0f, 0f, 0f, 1f, 0f
        )))
    }

    private val tileDark = Paint().apply { isFilterBitmap = true }
    private var neonCache: Pair<String, Paint>? = null

    /** Ubin OSM online dibuat duotone sesuai tema: latar jadi gelap, garis dan teks jadi aksen neon (cadangan bila peta offline belum ada). */
    private fun neonPaint(): Paint {
        neonCache?.takeIf { it.first == P.name }?.let { return it.second }
        val bg = P.bg; val ac = P.pus
        // kecerahan >= 245 (daratan dan jalan putih) jadi warna latar, <= 190 (garis tepi, teks, air) jadi aksen penuh
        val l0 = 170f; val l1 = 238f
        fun row(i: Int): FloatArray {
            val a = ac[i] * 0.6f; val b = bg[i].toFloat()
            val d = (a - b) / (l1 - l0)
            return floatArrayOf(-d * 0.299f, -d * 0.587f, -d * 0.114f, 0f, a + d * l0)
        }
        val m = ColorMatrix(floatArrayOf(*row(0), *row(1), *row(2), 0f, 0f, 0f, 1f, 0f))
        val p = Paint().apply { isFilterBitmap = true; colorFilter = ColorMatrixColorFilter(m) }
        neonCache = Pair(P.name, p)
        return p
    }
    private var carBearing = 0f
    private val carMarker: Bitmap? by lazy { try { BitmapFactory.decodeResource(ctx.resources, R.drawable.car_marker, BitmapFactory.Options().apply { inScaled = false }) } catch (e: Exception) { null } }

    // ---------- peta: mosaik ubin datar disimpan sebagai bitmap, ditampilkan tiap frame lewat jaring miring (GPU) ----------
    private class Flat(
        val bmp: Bitmap, val z: Int, val src: Int, val tag: String, val cx0: Double, val cy0: Double, val th0: Float,
        val wD: Float, val hD: Float, val pcx: Float, val pcy: Float, val scyOff: Float, val incomplete: Boolean, val tileVer: Int, val builtAt: Long
    )
    private class MapSlot { @Volatile var flat: Flat? = null; @Volatile var building = false }
    private val mapSlots = HashMap<String, MapSlot>()
    private val mapPool = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mosM = 320f
    private val meshW = 12
    private val meshH = 16
    private val meshVerts = FloatArray((meshW + 1) * (meshH + 1) * 2)
    private var routeRef: List<DoubleArray>? = null
    private var routeZ = -1
    private var routeW = DoubleArray(0)
    private var routeBuf = FloatArray(0)
    private var navZ = 18

    // posisi tampilan (diinterpolasi di antara fiks GPS) dan arah mobil
    private var fixRef: android.location.Location? = null
    private var fixAt = 0L
    private var dispLat = 0.0
    private var dispLon = 0.0
    private var dispInit = false
    private var dispAt = 0L
    private var headDisp = 0f

    private fun compassOn() = prefs.getBoolean("compass", true)

    // ---------- kompas yang dikalibrasi menurut laju mobil ----------
    private var headTarget = 0f
    private var headSrc = "tahan"
    private var headRouteRef: List<DoubleArray>? = null
    private var headRouteIdx = 0

    private fun bearingTo(la1: Double, lo1: Double, la2: Double, lo2: Double): Float {
        val p1 = Math.toRadians(la1); val p2 = Math.toRadians(la2); val dl = Math.toRadians(lo2 - lo1)
        val y = sin(dl) * cos(p2); val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    /**
     * Arah mobil untuk kompas. GPS hanya memberi arah yang dapat dipercaya saat mobil cukup cepat, sehingga:
     * - diam (di bawah 3 km/j): arah ditahan, tidak berubah mengikuti derau GPS;
     * - saat dipandu dan dekat jalur: memakai arah ruas rute di depan mobil (stabil, tanpa goyangan GPS);
     * - selain itu memakai arah GPS bila laju sedikitnya 5 km/j.
     */
    private fun updateHeadingTarget(loc: android.location.Location) {
        val v = AppState.speedKmh
        if (v < 3f) { headSrc = "tahan"; return }
        val route = AppState.destRoute
        if (AppState.guiding && route != null && route.size > 3) {
            if (route !== headRouteRef) { headRouteRef = route; headRouteIdx = 0 }
            val lo = max(0, headRouteIdx - 5); val hi = min(route.size - 2, headRouteIdx + 90)
            var best = Double.MAX_VALUE; var bi = -1
            for (i in lo..hi) {
                val d = Geo.distM(loc.latitude, loc.longitude, route[i][0], route[i][1])
                if (d < best) { best = d; bi = i }
            }
            if (bi >= 0 && best < 40.0) {
                headRouteIdx = bi
                // arah ruas sekitar 25 m di depan, supaya tikungan terasa lebih awal
                var j = bi; var acc = 0.0
                while (j < route.size - 1 && acc < 25.0) { acc += Geo.distM(route[j][0], route[j][1], route[j + 1][0], route[j + 1][1]); j++ }
                if (j > bi) { headTarget = bearingTo(route[bi][0], route[bi][1], route[j][0], route[j][1]); headSrc = "rute"; return }
            }
        }
        if (loc.hasBearing() && v >= 5f) { headTarget = loc.bearing; headSrc = "gps"; return }
        headSrc = "tahan"
    }

    private fun interpPos(loc: android.location.Location, now: Long) {
        if (loc !== fixRef) { fixRef = loc; fixAt = now; updateHeadingTarget(loc) }
        carBearing = headTarget
        val v = if (AppState.speedKmh > 2f) AppState.speedKmh / 3.6 else 0.0
        val age = min(1.2, (now - fixAt) / 1000.0)
        val brg = Math.toRadians(carBearing.toDouble())
        val tLat = loc.latitude + v * age * cos(brg) / 111320.0
        val tLon = loc.longitude + v * age * sin(brg) / (111320.0 * cos(Math.toRadians(loc.latitude)))
        val dt = if (dispAt == 0L) 0.0 else ((now - dispAt) / 1000.0).coerceIn(0.0, 0.2)
        dispAt = now
        if (!dispInit || Geo.distM(dispLat, dispLon, tLat, tLon) > 150) { dispLat = tLat; dispLon = tLon; dispInit = true }
        else { val k = min(1.0, dt * 10.0); dispLat += (tLat - dispLat) * k; dispLon += (tLon - dispLon) * k }
        // kecepatan putar kompas mengikuti laju: pelan saat lambat (hindari goyang), cepat saat kencang (ikuti tikungan)
        val kmh = max(0f, AppState.speedKmh)
        val d = angDiff(headTarget, headDisp)
        if (headSrc != "tahan" && !(abs(d) < 1.5f && kmh < 20f)) {
            val rate = 1.5f + 6.5f * min(1f, kmh / 60f)
            headDisp = (headDisp + d * (1f - exp(-rate * dt.toFloat())) + 360f) % 360f
        }
        if (v > 0.0 || abs(d) > 0.4f || Geo.distM(dispLat, dispLon, tLat, tLon) > 0.3) fast = true
    }

    /** Zoom mode navigasi menurut kecepatan, dengan histeresis supaya tidak bolak-balik di batas. */
    private fun navZoom(kmh: Float): Int {
        navZ = when (navZ) {
            18 -> if (kmh >= 29f) 17 else 18
            17 -> if (kmh < 21f) 18 else if (kmh >= 74f) 16 else 17
            16 -> if (kmh < 66f) 17 else if (kmh >= 104f) 15 else 16
            else -> if (kmh < 96f) 16 else 15
        }
        return navZ
    }

    private fun angDiff(a: Float, b: Float): Float = ((a - b + 540f) % 360f) - 180f

    private fun buildFlat(slot: MapSlot, z: Int, src: Int, tag: String, cx0: Double, cy0: Double, th0: Float, mw: Float, mh: Float, scyOff: Float) {
        slot.building = true
        val ver = TileCache.version
        val neon = neonPaint()
        mapPool.execute {
            try {
                val wD = 1.8f * mw + 2 * mosM; val hD = 1.85f * mh + 2 * mosM
                val pcx = 0.9f * mw + mosM; val pcy = scyOff + 0.85f * mh + mosM
                val ms = min(min(scale, 0.9f), min(4000f / wD, 4000f / hD))
                val bm = Bitmap.createBitmap((wD * ms).toInt().coerceAtLeast(1), (hD * ms).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val cv = Canvas(bm)
                cv.scale(ms, ms); cv.translate(pcx, pcy); cv.rotate(-th0)
                val r = Math.hypot(max(pcx, wD - pcx).toDouble(), max(pcy, hD - pcy).toDouble()) + 256.0
                val rad = Math.toRadians(th0.toDouble()); val cT = cos(rad); val sT = sin(rad)
                val n = 1 shl z
                var incomplete = false; var drawn = 0; var missing = 0
                val tx0 = Math.floor((cx0 - r) / 256.0).toInt(); val tx1 = Math.floor((cx0 + r) / 256.0).toInt()
                val ty0 = Math.floor((cy0 - r) / 256.0).toInt(); val ty1 = Math.floor((cy0 + r) / 256.0).toInt()
                val dark = src == TileCache.DARK
                for (ty in max(ty0, 0)..min(ty1, n - 1)) for (tx in tx0..tx1) {
                    val rx = (tx * 256.0 - cx0).toFloat(); val ry = (ty * 256.0 - cy0).toFloat()
                    // titik tengah ubin di bingkai mosaik; lewati yang jauh di luar bitmap
                    val rcx = rx + 128f; val rcy = ry + 128f
                    val u = pcx + (rcx * cT + rcy * sT).toFloat(); val v = pcy + (-rcx * sT + rcy * cT).toFloat()
                    if (u < -260f || u > wD + 260f || v < -260f || v > hD + 260f) continue
                    val tb = TileCache.get(src, z, tx, ty)
                    if (tb == null) { incomplete = true; missing++; continue }
                    drawn++
                    val paint = if (dark && !TileCache.isOffline(z, tx, ty)) neon else tileDark
                    cv.drawBitmap(tb, null, RectF(rx, ry, rx + 256.6f, ry + 256.6f), paint)
                }
                slot.flat = Flat(bm, z, src, tag, cx0, cy0, th0, wD, hD, pcx, pcy, scyOff, incomplete, ver, System.currentTimeMillis())
            } catch (e: Exception) {
                android.util.Log.w("RushMap", "susun mosaik gagal: $e")
            } finally {
                slot.building = false
                AppState.changed()
            }
        }
    }

    private fun drawMap(c: Canvas, ml: Float, mt: Float, mw: Float, mh: Float, lat: Double, lon: Double) {
        val src = AppState.mapMode
        val dest = AppState.dest
        val route: List<DoubleArray>? = if (dest != null) AppState.destRoute else null
        val pts = ArrayList<DoubleArray>()
        pts.add(doubleArrayOf(lat, lon))
        if (dest != null) pts.add(doubleArrayOf(dest.lat, dest.lon))
        if (route != null) pts.addAll(route)
        var z = if (dest != null) 17 else 16
        val follow = AppState.guiding
        if (!follow) {
            while (z >= 4) {
                var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
                for (p in pts) {
                    val x = Geo.px(p[1], z); val y = Geo.py(p[0], z)
                    minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
                }
                if (maxX - minX <= mw * 0.62 && maxY - minY <= mh * 0.42) break
                z--
            }
            if (z < 4) z = 4
        } else {
            z = navZoom(max(0f, AppState.speedKmh))   // mode navigasi: ikuti mobil dari dekat, makin cepat makin jauh
        }
        val zoomDelta = AppState.mapZoom
        z = (z + zoomDelta).coerceIn(4, 19)
        var cx = Geo.px((pts.minOf { it[1] } + pts.maxOf { it[1] }) / 2, z)
        var cy = Geo.py((pts.minOf { it[0] } + pts.maxOf { it[0] }) / 2, z)
        if (zoomDelta > 0 || dest == null || follow) { cx = Geo.px(lon, z); cy = Geo.py(lat, z) }
        val scx = ml + mw / 2
        val scy = if (follow) mt + mh * 0.68f else mt + mh / 2
        val compass = follow && compassOn()
        val th = if (compass) headDisp else 0f
        val thR = Math.toRadians(th.toDouble()); val cT = cos(thR); val sT = sin(thR)

        // Miring ala 3D: bidang peta (trapesium, lebih lebar di bagian jauh) dipetakan ke kotak layar.
        val srcQuad = floatArrayOf(
            ml - mw * 0.4f, mt - mh * 0.85f, ml + mw * 1.4f, mt - mh * 0.85f,
            ml + mw, mt + mh, ml, mt + mh
        )
        val dstQuad = floatArrayOf(ml, mt, ml + mw, mt, ml + mw, mt + mh, ml, mt + mh)
        val tilt = Matrix()
        tilt.setPolyToPoly(srcQuad, 0, dstQuad, 0, 4)

        // mosaik: susun ulang di latar belakang hanya bila perlu
        val slot = mapSlots.getOrPut("${mw.toInt()}x${mh.toInt()}") { MapSlot() }
        val tag = "${P.name}|${OfflineMaps.installedCount()}"
        val fl = slot.flat
        val now = System.currentTimeMillis()
        if (!slot.building) {
            val stale = fl == null || fl.z != z || fl.src != src || fl.tag != tag || abs(fl.scyOff - (scy - mt)) > 1f ||
                abs(cx - fl.cx0) > 0.6 * mosM || abs(cy - fl.cy0) > 0.6 * mosM || abs(angDiff(th, fl.th0)) > 12f ||
                (fl.incomplete && fl.tileVer != TileCache.version && now - fl.builtAt > 300L)
            if (stale) buildFlat(slot, z, src, tag, cx, cy, th, mw, mh, scy - mt)
        }
        if (slot.building || (fl != null && fl.incomplete)) fast = true
        c.save()
        if (fl != null && fl.src == src && fl.tag == tag) {
            val f = Math.pow(2.0, (z - fl.z).toDouble())
            val dcx = fl.cx0 * f - cx; val dcy = fl.cy0 * f - cy
            val bx = dcx * cT + dcy * sT; val by = -dcx * sT + dcy * cT
            val phi = Math.toRadians((fl.th0 - th).toDouble()); val cp = cos(phi); val sp2 = sin(phi)
            var k = 0
            for (j in 0..meshH) for (i in 0..meshW) {
                val rx = (fl.wD * i / meshW - fl.pcx) * f; val ry = (fl.hD * j / meshH - fl.pcy) * f
                meshVerts[k++] = (scx + rx * cp - ry * sp2 + bx).toFloat()
                meshVerts[k++] = (scy + rx * sp2 + ry * cp + by).toFloat()
            }
            tilt.mapPoints(meshVerts)
            c.drawBitmapMesh(fl.bmp, meshW, meshH, meshVerts, 0, null, 0, bmpPaint)
        }
        c.restore()

        // lapisan gelap tipis di bagian jauh supaya kabut jarak terasa
        pt.reset()
        pt.shader = LinearGradient(0f, mt, 0f, mt + mh * 0.4f, cA(190, P.bg), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(ml, mt, ml + mw, mt + mh * 0.4f, pt)
        pt.shader = null

        val tmp = FloatArray(2)
        fun sp(la: Double, lo: Double): FloatArray {
            val dx = Geo.px(lo, z) - cx; val dy = Geo.py(la, z) - cy
            tmp[0] = (scx + dx * cT + dy * sT).toFloat(); tmp[1] = (scy - dx * sT + dy * cT).toFloat()
            tilt.mapPoints(tmp)
            return floatArrayOf(tmp[0], tmp[1])
        }
        if (route != null && route.size >= 2) {
            if (route !== routeRef || routeZ != z) {
                routeRef = route; routeZ = z
                routeW = DoubleArray(route.size * 2)
                route.forEachIndexed { i, p -> routeW[i * 2] = Geo.px(p[1], z); routeW[i * 2 + 1] = Geo.py(p[0], z) }
                if (routeBuf.size < route.size * 2) routeBuf = FloatArray(route.size * 2)
            }
            val nr = route.size
            for (i in 0 until nr) {
                val dx = routeW[i * 2] - cx; val dy = routeW[i * 2 + 1] - cy
                routeBuf[i * 2] = (scx + dx * cT + dy * sT).toFloat(); routeBuf[i * 2 + 1] = (scy - dx * sT + dy * cT).toFloat()
            }
            tilt.mapPoints(routeBuf, 0, routeBuf, 0, nr)
            val path = Path()
            path.moveTo(routeBuf[0], routeBuf[1])
            for (i in 1 until nr) path.lineTo(routeBuf[i * 2], routeBuf[i * 2 + 1])
            pt.reset(); pt.isAntiAlias = true; pt.style = Paint.Style.STROKE; pt.strokeCap = Paint.Cap.ROUND; pt.strokeJoin = Paint.Join.ROUND
            val straight = route.size == 2
            pt.strokeWidth = 14f; pt.color = cA(90, P.cy)
            c.drawPath(path, pt)
            pt.strokeWidth = 6f; pt.color = MG
            if (straight) pt.pathEffect = DashPathEffect(floatArrayOf(2f, 14f), 0f)
            c.drawPath(path, pt)
            pt.pathEffect = null
        }
        val me = sp(lat, lon)
        carMarker?.let { cm ->
            val ch2 = if (follow) 112f else 84f; val cw2 = ch2 * cm.width / cm.height
            pt.reset(); pt.isAntiAlias = true; pt.color = Color.argb(64, 255, 255, 255); pt.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(me[0], me[1], ch2 * 0.42f, pt)
            pt.reset(); pt.isFilterBitmap = true; pt.isAntiAlias = true
            pt.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            c.save(); c.rotate(if (compass) 0f else headDisp, me[0], me[1])
            c.drawBitmap(cm, null, RectF(me[0] - cw2 / 2, me[1] - ch2 / 2, me[0] + cw2 / 2, me[1] + ch2 / 2), pt)
            c.restore()
        } ?: run {
            pt.reset(); pt.isAntiAlias = true
            pt.color = pc(P.mapbg); c.drawCircle(me[0], me[1], 16f, pt)
            pt.color = Color.WHITE; c.drawCircle(me[0], me[1], 10f, pt)
        }
        if (dest != null) {
            val q = sp(dest.lat, dest.lon)
            pt.reset(); pt.isAntiAlias = true; pt.color = MG
            pt.maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL); c.drawCircle(q[0], q[1], 22f, pt)
            pt.maskFilter = null; c.drawCircle(q[0], q[1], 22f, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 3f; pt.color = Color.WHITE; c.drawCircle(q[0], q[1], 22f, pt)
            icon(c, if (dest.kind == "pump") R.drawable.ic_pump else R.drawable.ic_pin, q[0] - 13f, q[1] - 13f, 26f, Color.WHITE)
        }
        // penunjuk utara saat mode kompas: lingkaran kecil dengan huruf U (utara) di arah sebenarnya
        if (compass) {
            val ncx = ml + 46f; val ncy = mt + 238f
            pt.reset(); pt.isAntiAlias = true; pt.color = cA(205, P.dark2); c.drawCircle(ncx, ncy, 34f, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 2f; pt.color = cA(110, P.tint); c.drawCircle(ncx, ncy, 34f, pt)
            val nx = ncx + (-sin(thR)).toFloat() * 21f; val ny = ncy - cos(thR).toFloat() * 21f
            text(c, "U", nx, ny + 8f, 22f, MG, 700, Paint.Align.CENTER)
        }
        // atribusi sumber peta
        text(c, TileCache.attribution(src), ml + mw - 10f, mt + mh - 8f, 16f, Color.argb(210, 236, 238, 248), 400, Paint.Align.RIGHT, mw - 20f)
    }

}
