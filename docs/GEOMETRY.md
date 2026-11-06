# Fiabilité des calculs géométriques

## Méthode

| Calcul | Méthode | Code |
|---|---|---|
| Surface | Projection **azimutale équivalente de Lambert** sur l'ellipsoïde **WGS84** (formules de Snyder, *Map Projections – A Working Manual*, §24), centrée sur le polygone, puis formule du lacet. Cette projection **conserve les aires** : la surface obtenue est celle de l'ellipsoïde, sans déformation d'échelle. Les degrés ne sont jamais traités comme des mètres. | `GeoMath.projectEqualArea`, `GeoMath.areaM2` |
| Périmètre / distance | Formule inverse de **Vincenty** (≈ 0,5 mm), repli Haversine si non convergente (points quasi antipodaux). | `GeoMath.distance` |
| Antiméridien | Le centre de projection utilise la longitude moyenne *circulaire* et les écarts de longitude sont normalisés dans [-180°, 180°]. | `GeoMath.centerOf` |

Les côtés sont des segments droits dans la projection (et non des géodésiques) : l'écart est négligeable pour une parcelle
(< 10⁻⁶ en relatif jusqu'à 1 km ; < 10⁻³ pour une cellule de 1° × 1°, mesuré dans les tests).

## Gestion des cas limites

- Coordonnées invalides (NaN, |lat| > 90, |lon| > 180) → **refus** (`INVALID_COORDINATE`).
- Points identiques / segments < 1 mm / point de fermeture répété → **supprimés** (nombre indiqué dans `removedPoints`).
- Moins de 3 points distincts → `TOO_FEW_POINTS`. Points alignés → `ZERO_AREA`.
- Polygone **auto‑intersecté** → **refus** (`SELF_INTERSECTING`) ; l'interface explique comment corriger (déplacer/supprimer un point). Aucune correction silencieuse.
- Polygones concaves : acceptés (formule du lacet).

## Précision numérique ≠ précision de mesure

Le calcul est exact à ~10⁻⁹ près sur ses entrées ; une mesure GPS de smartphone est précise à 3–15 m. Une surface calculée
n'est donc **jamais plus précise que les points saisis**. L'application affiche la précision GPS estimée, rejette les
positions dont la précision dépasse un seuil configurable et affiche : *« Mesure indicative. Ne remplace pas un relevé
effectué par un géomètre-expert. »*

## Valeurs de référence des tests

Générées par `docs/reference/ref.py` avec **GeographicLib 2.1** (Karney, arêtes géodésiques WGS84) :

| Cas | Surface de référence | Périmètre de référence |
|---|---|---|
| Carré 100 m, Paris | 10 000,0895549414 m² | 400,0017910986 m |
| Carré 1 km, équateur | 999 999,997937684 m² | 3 999,999987626 m |
| Carré 500 m, lat 70° | 250 026,840104894 m² | 2 000,1073709223 m |
| Polygone concave en L, Lyon | 7 870,032011479 m² | 415,706054062 m |
| Triangle 0,01° | 615 453,6071642 m² | 3 787,97238167 m |
| Cellule 1° × 1° | 12 308 778 361,469 m² | — |
| Polygone traversant l'antiméridien | 484 932 684,776 m² | — |
| Distance Flinders Peak → Buninyong | — | 54 972,271 m (Geoscience Australia) |

Reproduire : `pip install geographiclib && python docs/reference/ref.py`.
