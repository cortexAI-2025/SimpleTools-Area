# SimpleTools Area 1.3 — FREE et PRO

FREE comprend les mesures manuelles et sur carte sans quota, les parcelles GPS, la bibliothèque existante, le partage texte et les exports PDF, CSV et GeoJSON déjà inclus en 1.0. PRO ajoute les projets, zones exclues, outils de terrain, sauvegardes, trajets GPS ouverts et exports KML et GPX. Les projets importent une copie des polygones de la bibliothèque. Les originaux restent indépendants.

## Licence

- Produit Google Play non consommable `simpletools_area_pro_lifetime`. Le prix français cible est de 0,99 € TTC. L’écran utilise le prix formaté renvoyé par Play, dans la devise locale. L’achat est définitif et sans abonnement.
- Le client accepte un reçu Google Play signé correspondant au package, au produit et à un état acheté. Il exige une confirmation d’achat réussie avant d’ouvrir PRO. Un paiement en attente ne donne aucun accès.
- À l’ouverture et lors d’une restauration manuelle, l’application interroge Play quand Android indique un réseau validé. Un achat absent d’une réponse réussie retire l’accès. Une erreur réseau conserve le dernier reçu vérifié dans sa fenêtre de validité. Une révocation ne supprime pas les projets locaux.
- Hors ligne, le reçu signé en cache fonctionne 30 jours depuis le dernier contrôle réussi. Reconnectez-vous ou choisissez « Restaurer mes achats » après expiration. L’achat reste définitif. Le délai concerne le contrôle local du reçu.
- L’implémentation est entièrement cliente. Une révocation est détectée lorsque Play renvoie l’état mis à jour, sans notification serveur immédiate. La vérification locale ne protège pas contre la modification d’un appareil compromis.
- L’application bloque PRO en l’absence de clé publique. Les APK et AAB CI standard servent à la recette FREE. `AREA_PLAY_PUBLIC_KEY` ou `-PareaPlayPublicKey=…` fournit la clé publique lors de la compilation de recette Play ou de production.

[Guide d’intégration Google Play Billing](https://developer.android.com/google/play/billing/integrate)
