# Géométrie WGS84

SimpleTools Area utilise GeographicLib Java 2.1. Les distances utilisent le problème géodésique inverse de Karney. Les polygones utilisent l’aire algébrique de leurs arêtes géodésiques sur l’ellipsoïde, avec normalisation à la plus petite des deux régions complémentaires. Aucun fond cartographique n’est nécessaire aux calculs.

La projection azimutale équivalente locale de Lambert reste utilisée pour le dessin et les contrôles topologiques des parcelles. Les segments de moins d’un millimètre sont fusionnés dans les anneaux, les auto-intersections sont rejetées. Les trous doivent être strictement intérieurs, sans chevauchement, contact ni imbrication. Les contrôles topologiques sont conçus pour des parcelles locales. Les surfaces nettes soustraient les trous. Le périmètre net cumule les bords extérieurs et ceux des trous, et la clôture estimée additionne les bords extérieurs. Les surfaces cumulées additionnent les mesures sans dissoudre les éventuels chevauchements entre parcelles distinctes. La division indicative calcule une surface cible par part.

Le rayon d’incertitude GPS affiché vient du fournisseur Android et décrit une position, pas la précision finale de la surface. Les résultats sont indicatifs.

Les références et tolérances sont dans `GeoMathTest.kt`. Le script `docs/reference/ref.py` reproduit les références géodésiques existantes.
