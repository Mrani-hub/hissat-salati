# حصة صلاتي — projet Android

Enveloppe WebView autour du fichier `app/src/main/assets/index.html`.
Aucun serveur, aucun compte développeur, aucune publication : l'APK s'installe
directement sur le téléphone.

## Trois façons d'obtenir l'APK

### 1. GitHub Actions (aucune installation sur votre machine)
1. Créez un dépôt GitHub, poussez-y ce dossier.
2. Onglet **Actions** → workflow **APK** → **Run workflow**.
3. Cinq minutes plus tard, téléchargez l'artefact `hissat-salati-apk`.

### 2. Android Studio
Ouvrir le dossier, puis **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
Le fichier sort dans `app/build/outputs/apk/debug/`.

### 3. Ligne de commande
```
gradle assembleDebug          # ou ./gradlew assembleDebug
```

## Version iPhone

Apple interdit d'installer une application reçue par WhatsApp : sur iPhone, la
seule voie hors App Store est la page web installable. Le dossier `web/`
fabrique exactement cela à partir du **même** `index.html` que l'APK, et le
publie sur GitHub Pages (workflow **Site iPhone**).

On envoie alors un lien au lieu d'un fichier : la personne l'ouvre, fait
« Ajouter à l'écran d'accueil », et obtient une icône plein écran qui fonctionne
sans connexion. Seul l'adhan automatique, application fermée, reste impossible
en web — un verrou d'iOS.

Mode d'emploi complet : [`web/LISEZ-MOI.md`](web/LISEZ-MOI.md).

## Installation sur le téléphone
Copiez l'APK sur l'appareil, ouvrez-le, et autorisez l'installation depuis
« sources inconnues » pour l'application qui ouvre le fichier (Fichiers ou Chrome).

## Mettre à jour l'application
Remplacez `app/src/main/assets/index.html` par la nouvelle version,
augmentez `versionCode` dans `app/build.gradle`, reconstruisez.

## Ce qui change par rapport au navigateur
- Le partage passe par un pont natif : l'image du mois et le fichier partageable
  ouvrent directement le sélecteur Android (WhatsApp, Gmail…).
- « Lire l'image » appelle l'API d'Anthropic, qui exige une clé hors de
  claude.ai : dans l'APK, cette fonction échoue et renvoie vers la saisie
  manuelle ou le texte collé. Le calcul par ville et le collage du tableau des
  Habous fonctionnent normalement.
- La police Amiri (arabe, graisses 400 et 700) est embarquée dans le HTML en
  woff2 : aucun appel réseau pour les polices, rendu identique hors connexion.
  Le texte latin utilise la police système du téléphone.
- L'application ne contacte donc plus aucun serveur, sauf si vous ouvrez le
  site des Habous depuis l'onglet التحميل.
