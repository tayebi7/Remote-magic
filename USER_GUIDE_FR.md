# Guide d’utilisation REMOTE MAGIC

## Démarrage en 4 étapes
1. Allumez la TV avec sa télécommande ; téléphone et TV sur **le même Wi-Fi**.
2. Touchez le **bouton de recherche 🔍** en haut à gauche de la télécommande (une fois connecté, il affiche « Connecté »).
3. Choisissez l’onglet **Wi-Fi** ou **Bluetooth**, puis **Rechercher la TV**.
4. Touchez votre TV, acceptez la demande ou saisissez le code, puis revenez à « Télécommande ».

## 1. Introduction
Transforme un téléphone Android 8+ en télécommande. Les protocoles n’ont pas encore été vérifiés sur une TV réelle : signalez tout problème.

## 2. Installation de l’APK
Sur GitHub : **Actions** → dernière exécution réussie de « Build APK » → `remote-magic-apk` (ou **Releases**). Décompressez, ouvrez l’APK et autorisez la source.

## 3. Écran principal
Le menu ☰ ouvre toutes les pages. « Accueil » affiche nom, fabricant, IP, type et état de connexion, avec Déconnecter / Reconnecter.

## 4. Connexion Wi-Fi
- Google TV / Android TV : saisir le code à 6 caractères.
- Samsung, LG : accepter sur la TV.
- Roku : direct (activez le contrôle réseau si les commandes sont refusées).

## 5. Connexion Bluetooth
Android 9+ avec profil HID. Sur la TV : ajouter un accessoire. Dans l’application : onglet Bluetooth → Rechercher → autoriser → choisir la TV. Pas de lancement d’applications. La touche Marche tente un réveil Bluetooth, possible seulement si la TV garde le Bluetooth actif en veille.

## 6. Ajouter une TV
Utilisez l’onglet **IP manuelle** si la recherche échoue. Les TV connectées sont enregistrées (favori ⭐, adresse MAC, suppression).

## 7. Les touches
Haut : Retour, Accueil, Marche. Puis croix directionnelle et OK ; Partage écran (recopie d’écran Android, en Wi-Fi), Voix, Souris, Clavier ; volume, chaînes, menu, réglages, source, quitter ; médias (dont Stop) ; pavé numérique masquable avec * # Enter. Une touche grisée n’est pas prise en charge. « Return » = Retour.

**Souris** : Bluetooth HID, LG et Samsung seulement. Calibrez en tenant le téléphone immobile. Pavé tactile : glisser, toucher, double toucher, appui long (Bluetooth). Défilement : Bluetooth et LG.

**Clavier** : sélectionnez un champ sur la TV, tapez puis envoyez. En Bluetooth, caractères anglais (US) uniquement. Seul LG confirme la réception.

## 8. Commandes vocales
Exemples : « ouvre YouTube », « monte le son », « baisse le son », « coupe le son », « chaîne suivante », « accueil », « paramètres », « allume la TV », « éteins la TV ». Le service vocal du téléphone peut envoyer l’audio à son fournisseur.

## 9. Les trois raccourcis
YouTube, IPTV, Radio. Pour IPTV et Radio, saisissez l’ID de l’application dans « Applications » (un lien sur Android TV). Android TV ne confirme pas l’installation ; le Bluetooth ne lance pas d’applications.

## 10. Réveil depuis la veille
Activez **Wake on LAN** sur la TV. L’adresse MAC est nécessaire (souvent automatique pour Samsung et Roku, sinon « Appareils enregistrés » → ✏️). **Une TV débranchée ou sans fonction de réveil ne peut pas être allumée.**

## 11. Partager la carte
« Accueil » → partager ou enregistrer (Android 10+). L’image de la TV est un dessin générique. La capture d’écran réelle n’existe que pour LG et peut être refusée.

## 12. Paramètres
Langue, thème, vibration, sons, connexion automatique (Wi-Fi), ordre des **sections**, suppression des appareils, réinitialisation.

## 13. Dépannage
- TV introuvable : même réseau, pas de VPN ni réseau invité, essayez l’IP manuelle.
- Connexion refusée : acceptez sur la TV ou retirez le téléphone de sa liste.
- « TV certificate changed » : supprimez l’appareil puis associez de nouveau.

## 14. Compatibilité
Android TV / Google TV, Samsung Tizen (2016+), LG webOS, Roku, Bluetooth HID. **Non pris en charge :** Apple TV / iOS, infrarouge, BLE.

## 15. FAQ
- Internet requis ? Non, le réseau local suffit.
- Mes données ? Aucun serveur ; les jetons sont chiffrés avec Android Keystore ; aucun mot de passe Wi-Fi n’est demandé.
