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

## Suivre le lieu en voyage (اتّباع الموقع)

Réglage à activer dans la carte des réglages, écran اليوم. Éteint par défaut :
tant qu'on n'y touche pas, l'application ne demande jamais la position.

Une fois activé, à chaque ouverture de l'application (et au retour à l'écran
après une mise en veille), la position est relevée, la ville la plus proche des
55 de la liste est cherchée, et ses horaires sont adoptés — l'adhan et le
rappel suivent, puisqu'ils sont reprogrammés à partir du mois affiché. Un
bandeau annonce le changement et propose « العودة إلى … » pour reprendre la
main. Au retour chez soi, la ville d'origine redevient la plus proche et le
tableau officiel qui y était enregistré est repris tel quel.

Trois garde-fous, réglables en tête de la section « 5 ter » d'`index.html` :

| Constante | Valeur | Rôle |
|---|---|---|
| `GEO_MARGE` | 15 km | La nouvelle ville doit être *nettement* plus proche, sinon la ville ne changerait pas d'un relevé à l'autre entre deux villes voisines. |
| `GEO_MAX`   | 150 km | Au-delà (hors du Maroc), aucune ville de la liste n'a de sens : rien ne change, car le calcul suppose le fuseau du pays. |
| `GEO_FLOU`  | 25 km | Une position trop imprécise ne décide de rien. |

Le même code sert aux deux plateformes :

- **Android** : `Loc.java` lit la position via `LocationManager`, sans Google
  Play Services. Il prend d'abord une position déjà connue de moins d'un quart
  d'heure (gratuite en batterie), sinon il demande une mesure au réseau puis au
  GPS, et abandonne au bout de vingt-cinq secondes. `MainActivity.requestLocation()`
  réclame l'autorisation au moment du clic, jamais au démarrage.
- **iPhone / navigateur** : `navigator.geolocation`, sans une ligne de code en
  plus. La page installée depuis GitHub Pages est servie en HTTPS, ce qu'iOS
  exige pour donner la position.

**Ce que ça ne fait pas** : il n'y a pas de suivi en arrière-plan. Si vous
voyagez pendant que l'application est fermée, l'adhan de la prochaine prière
utilise encore les horaires de la ville précédente ; ouvrir l'application une
fois sur place remet tout d'aplomb. Un suivi en arrière-plan exigerait
`ACCESS_BACKGROUND_LOCATION`, coûterait de la batterie, et reste impossible sur
iPhone hors App Store.

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
