# Préparer SimpleTools Area 1.3.0 pour Google Play

- Package `com.simpletools.area`. Code de version 2. Vérifier que ce code est supérieur à tout code déjà téléversé.
- SDK cible 36, minimum 26. [Exigence Google Play](https://support.google.com/googleplay/android-developer/answer/11926878)
- Créer ou retrouver la fiche de l’application dans le compte AIWORKPAY. Configurer Play App Signing et utiliser la vraie clé d’upload du compte pour signer l’AAB.
- Configurer le produit ponctuel `simpletools_area_pro_lifetime`, option d’achat non consommable, à 0,99 € TTC en France. Vérifier le prix, la disponibilité par pays et l’état du produit dans la Console.
- Copier la clé publique de licence RSA de l’application depuis la Console. Construire l’AAB de recette avec `AREA_PLAY_PUBLIC_KEY` et `./gradlew :app:bundleRelease`. Vérifier la clé au moment de la recette d’achat. Signer l’AAB hors du dépôt.
- Ouvrir un canal de test interne et ajouter les comptes testeurs de licence. Installer depuis Play sur le Galaxy A14 et exécuter la recette documentée. [Tests de facturation Google Play](https://developer.android.com/google/play/billing/test)
- Héberger la politique de confidentialité sur une URL publique de l’éditeur et renseigner cette URL. Vérifier la déclaration Sécurité des données et celle du service de premier plan de type localisation dans la Console.
- Vérifier la compatibilité des bibliothèques natives MapLibre et de l’AAB avec les pages de 16 Kio. [Compatibilité Android 16 Kio](https://developer.android.com/guide/practices/page-sizes)

## Éléments pour la politique de confidentialité

Les mesures et projets sont enregistrés sur l’appareil. Le GPS sert à recentrer la carte sur demande ou à enregistrer une session démarrée explicitement. Durant une session, une notification Android indique l’utilisation du service de localisation. Les projets sont supprimables depuis l’application et exportables sur demande.

La carte standard envoie des requêtes HTTPS pour les tuiles visibles à `tile.openstreetmap.org`. Ces requêtes révèlent l’adresse IP, la zone affichée, le niveau de zoom et les métadonnées HTTP au fournisseur. Si l’utilisateur configure un fournisseur satellite, son serveur reçoit ces requêtes lorsque le fond satellite est affiché. Le code n’envoie ni la liste des sommets ni les projets à un serveur SimpleTools. Les exports transmis via le partage Android sont confiés à la destination choisie. Google Play traite les achats et fournit à l’application un reçu signé conservé localement.
