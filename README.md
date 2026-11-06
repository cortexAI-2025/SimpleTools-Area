# SimpleTools Area — GPS Surface Calculator

Application Android **native** (Kotlin, Jetpack Compose, Material 3) pour mesurer la **surface** et le **périmètre**
d'un terrain. Gratuite, **sans publicité, sans compte, sans collecte de données**, local‑first.
Éditeur : SimpleTools (cortexAI-2025). Langues : français (défaut) et anglais.

> Mesure indicative. Ne remplace pas un relevé effectué par un géomètre-expert.

## Fonctionnalités (V1.0.0)

- **Mesurer sur carte** (MapLibre + tuiles OpenStreetMap) : ajouter des points (toucher la carte ou réticule central),
  sélectionner / **déplacer** / supprimer un point, annuler / rétablir, polygone bleu translucide, surface et périmètre
  en temps réel, recentrage GPS, choix d'unité, enregistrement.
- **Mesurer avec GPS en marchant** : permission de localisation, précision GPS affichée, démarrer / pause / reprise /
  terminer, tracé progressif, filtrage des positions imprécises ou aberrantes, seuil de déplacement configurable,
  **service de premier plan de type localisation** (notification persistante) pour continuer écran verrouillé.
  La localisation en arrière‑plan n'est **pas** demandée.
- **Calcul manuel** : rectangle, carré, triangle (base/hauteur ou 3 côtés), cercle, trapèze, polygone régulier, polygone
  par coordonnées (x y), avec validation et unités (m, cm, km, ft).
- **Mesurer une distance** sur la carte (m / km).
- **Unités** : m², ha, km², acres, ft², m, km — automatique ou manuel ; équivalents m² + ha toujours affichés.
- **Bibliothèque locale** (Room/SQLite) : consulter, rechercher, renommer, modifier le tracé, dupliquer, supprimer avec
  confirmation. Persistante après redémarrage.
- **Export / partage** : PDF (nom, date, surface, périmètre, croquis vectoriel, coordonnées), CSV, GeoJSON, via le partage
  Android natif (FileProvider).
- **Paramètres** : langue, thème (clair / sombre / système), unités, paramètres GPS, fond satellite personnalisé,
  confidentialité, à propos.

## Architecture

Module unique `:app`, organisé en paquets (suffisant pour une petite application ; découpage en modules Gradle
possible plus tard) :

```
core/geometry   GeoMath (surface, distance, validation), Units, ShapeCalculator   — pur Kotlin, testé en JVM
core/location   TrackFilter, GpsRecorder (machine d'état), LocationSource, service de premier plan
core/maps       styles MapLibre, composable MeasureMap (polygone, sommets déplaçables, position)
core/database   Room (ParcelEntity, DAO)       core/settings   DataStore
feature/*       measurement, calculator, library, export, settings, home  (MVVM + Repository)
```

MVVM, Repository Pattern, Coroutines/Flow, injection manuelle (`AreaApp`). Aucun SDK de tracking, aucune clé d'API.
Préparation à *SimpleTools Pro / Aura* : la logique métier est isolée de l'UI et des sources de données (interfaces
`LocationSource`, `SettingsStore`, `ParcelRepository`), ce qui permettra d'y brancher plus tard commandes vocales,
analyse IA ou synchronisation sans toucher au cœur. Rien de cela n'est implémenté en V1.

## Cartographie, hors‑ligne et conditions des fournisseurs

- Fond standard : tuiles raster `tile.openstreetmap.org`, soumises à la
  [Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/) (attribution affichée, pas de
  téléchargement massif ; **aucun cache hors‑ligne de masse n'est fourni**).
- **Fond satellite** : aucun fournisseur n'est inclus. Saisir dans *Paramètres → Fond satellite* le modèle d'URL d'un
  fournisseur dont vous avez le droit d'utiliser les tuiles.
- **Sans réseau** : fond uni, mais le dessin, le calcul, la mesure GPS (si le GPS du téléphone fonctionne), la
  sauvegarde et les exports restent disponibles. Des cartes hors‑ligne ne sont possibles que si des tuiles ont été
  mises en cache légalement.
- Le PDF n'inclut **pas** de capture de fond de carte (droits des fournisseurs) ; il contient un croquis vectoriel.

## Construire et tester

Prérequis : JDK 17, Android SDK 35.

```bash
./gradlew :app:testDebugUnitTest      # tests unitaires (JVM + Robolectric)
./gradlew :app:assembleDebug          # APK : app/build/outputs/apk/debug/app-debug.apk
```

Installation sur un téléphone : activer les sources inconnues, installer l'APK (ou `adb install -r app-debug.apk`).
La CI (`.github/workflows/android-ci.yml`) compile, lance lint + tests, produit l'APK debug et publie APK, rapports et
logs comme artefacts.

## Tests

Voir `app/src/test`. Les valeurs de référence géodésiques viennent de GeographicLib (voir `docs/GEOMETRY.md`).
Limite connue : les écrans Compose et la carte MapLibre ne sont pas couverts par des tests instrumentés automatisés.

## Confidentialité

Aucun compte, aucune pub, aucun traceur ; mesures stockées uniquement sur l'appareil ; localisation utilisée seulement
quand l'application est ouverte ; seules les tuiles de carte sont téléchargées depuis le fournisseur de cartes.

Licence : voir `LICENSE`.
