package id.kahyangan.navneon

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pencarian tempat. Memakai Google Places (Text Search) bila GOOGLE_PLACES_KEY diisi di local.properties,
 * jika tidak memakai OpenStreetMap Nominatim.
 */
object PlaceSearch {
    val usesGoogle: Boolean get() = "".isNotEmpty()
    val providerName: String get() = if (usesGoogle) "Google Places" else "OpenStreetMap"

    fun search(query: String, nearLat: Double?, nearLon: Double?): List<AppState.Place> =
        if (usesGoogle) google(query, nearLat, nearLon) else osm(query, nearLat, nearLon)

    private fun google(q: String, lat: Double?, lon: Double?): List<AppState.Place> {
        val body = JSONObject().put("textQuery", q).put("languageCode", "id").put("maxResultCount", 6)
        if (lat != null && lon != null) {
            body.put("locationBias", JSONObject().put("circle", JSONObject()
                .put("center", JSONObject().put("latitude", lat).put("longitude", lon))
                .put("radius", 30000.0)))
        }
        val res = Http.postJson(
            "https://places.googleapis.com/v1/places:searchText", body.toString(),
            mapOf("X-Goog-Api-Key" to "", "X-Goog-FieldMask" to "places.displayName,places.formattedAddress,places.location")
        )
        val arr = JSONObject(res).optJSONArray("places") ?: return emptyList()
        val out = ArrayList<AppState.Place>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val loc = p.optJSONObject("location") ?: continue
            out.add(AppState.Place(
                p.optJSONObject("displayName")?.optString("text").orEmpty().ifEmpty { "Tempat" },
                p.optString("formattedAddress"), loc.getDouble("latitude"), loc.getDouble("longitude")
            ))
        }
        return out
    }

    private fun osm(q: String, lat: Double?, lon: Double?): List<AppState.Place> {
        var url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&accept-language=id&q=" + Http.enc(q)
        if (lat != null && lon != null) {
            url += "&viewbox=%.4f,%.4f,%.4f,%.4f".format(java.util.Locale.US, lon - 0.4, lat + 0.4, lon + 0.4, lat - 0.4)
        }
        val arr = JSONArray(Http.get(url, 12000))
        val out = ArrayList<AppState.Place>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val full = p.optString("display_name")
            val first = full.substringBefore(",").trim()
            val name = p.optString("name").ifEmpty { first }
            out.add(AppState.Place(name, full.substringAfter(",", "").trim(), p.getDouble("lat"), p.getDouble("lon")))
        }
        return out
    }
}
