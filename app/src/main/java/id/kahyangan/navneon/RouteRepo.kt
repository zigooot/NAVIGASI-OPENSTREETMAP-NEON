package id.kahyangan.navneon

import org.json.JSONObject

object RouteRepo {
    /** Satu langkah rute: aksi di titik (lat, lon), lalu menempuh distM sampai aksi berikutnya. */
    class Step(val text: String, val icon: String, val distM: Double, val lat: Double, val lon: Double)

    class Result(val pts: List<DoubleArray>, val distM: Double, val min: Int, val steps: List<Step>)

    /** Rute mengemudi lewat OSRM dengan langkah belokan. Mengembalikan null bila gagal. */
    fun route(lat: Double, lon: Double, tLat: Double, tLon: Double): Result? {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/$lon,$lat;$tLon,$tLat?overview=full&geometries=geojson&steps=true"
            val r = JSONObject(Http.get(url, 12000)).getJSONArray("routes").getJSONObject(0)
            val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
            val pts = ArrayList<DoubleArray>()
            for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                pts.add(doubleArrayOf(c.getDouble(1), c.getDouble(0)))
            }
            val steps = ArrayList<Step>()
            try {
                val st = r.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
                for (i in 0 until st.length()) {
                    val s = st.getJSONObject(i)
                    val m = s.getJSONObject("maneuver")
                    val loc = m.getJSONArray("location")
                    val type = m.optString("type"); val mod = m.optString("modifier")
                    steps.add(Step(text(type, mod, s.optString("name"), m.optInt("exit")), iconKey(type, mod), s.optDouble("distance", 0.0), loc.getDouble(1), loc.getDouble(0)))
                }
            } catch (_: Exception) {}
            Result(pts, r.getDouble("distance"), Math.max(1, Math.round(r.getDouble("duration") / 60).toInt()), steps)
        } catch (_: Exception) {
            null
        }
    }

    private fun text(type: String, mod: String, name: String, exit: Int): String {
        val road = if (name.isNotEmpty()) " ke $name" else ""
        return when (type) {
            "depart" -> "Mulai berkendara" + (if (name.isNotEmpty()) " di $name" else "")
            "arrive" -> "Tiba di tujuan"
            "roundabout", "rotary", "roundabout turn" -> if (exit > 0) "Bundaran, keluar ke-$exit$road" else "Masuk bundaran$road"
            "exit roundabout", "exit rotary" -> "Keluar bundaran$road"
            else -> when (mod) {
                "left" -> "Belok kiri$road"
                "right" -> "Belok kanan$road"
                "slight left" -> "Serong kiri$road"
                "slight right" -> "Serong kanan$road"
                "sharp left" -> "Belok tajam kiri$road"
                "sharp right" -> "Belok tajam kanan$road"
                "uturn" -> "Putar balik"
                "straight" -> "Lurus$road"
                else -> "Lanjut$road"
            }
        }
    }

    private fun iconKey(type: String, mod: String): String = when {
        type == "arrive" -> "arrive"
        type == "depart" -> "straight"
        type.contains("roundabout") || type.contains("rotary") -> "roundabout"
        else -> when (mod) {
            "left" -> "left"; "right" -> "right"
            "slight left" -> "sleft"; "slight right" -> "sright"
            "sharp left" -> "hleft"; "sharp right" -> "hright"
            "uturn" -> "uturn"
            else -> "straight"
        }
    }
}
