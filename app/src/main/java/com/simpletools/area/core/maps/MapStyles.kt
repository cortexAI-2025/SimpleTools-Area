package com.simpletools.area.core.maps

/**
 * Styles MapLibre construits localement (aucune clé d'API).
 *
 * - Standard : tuiles raster OpenStreetMap (tile.openstreetmap.org). L'usage est soumis à la Tile Usage Policy
 *   d'OSMF (usage raisonnable, attribution obligatoire, pas de téléchargement massif ni de pré-chargement).
 *   Pour un usage intensif, configurer un autre fournisseur ou un serveur de tuiles dédié.
 * - Satellite : désactivé par défaut. L'utilisateur peut saisir dans les paramètres le modèle d'URL d'un fournisseur
 *   dont il a le droit d'utiliser les tuiles ({z}/{x}/{y}).
 * - Sans réseau, le fond uni reste affiché et le dessin / la mesure continuent de fonctionner.
 */
object MapStyles {
    const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"
    private const val OSM_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    fun isValidTileTemplate(url: String): Boolean {
        val u = url.trim()
        return u.startsWith("https://") && u.contains("{z}") && u.contains("{x}") && u.contains("{y}") &&
            !u.contains("\"") && !u.contains("\\") && !u.contains("\n")
    }

    fun styleJson(dark: Boolean, satelliteUrl: String?): String {
        val bg = if (dark) "#16202C" else "#E8EEF4"
        val useSat = satelliteUrl != null && isValidTileTemplate(satelliteUrl)
        val tiles = if (useSat) satelliteUrl!!.trim() else OSM_URL
        val attribution = if (useSat) "Custom tiles" else OSM_ATTRIBUTION
        return """
        {"version":8,"name":"simpletools",
         "sources":{"base":{"type":"raster","tiles":["$tiles"],"tileSize":256,"maxzoom":19,"attribution":"$attribution"}},
         "layers":[
           {"id":"bg","type":"background","paint":{"background-color":"$bg"}},
           {"id":"base","type":"raster","source":"base"}
         ]}
        """.trimIndent()
    }
}
