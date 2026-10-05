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

## Widget de l'écran d'accueil (الصلاة القادمة)

Une vignette à poser sur l'écran d'accueil : la prochaine prière, son heure, le
temps qui reste, et les cinq heures du jour. On n'ouvre plus l'application pour
jeter un coup d'œil.

Pour l'ajouter : appui long sur un espace vide de l'écran d'accueil →
**Widgets** → **حصة صلاتي**. Un appui sur la vignette ouvre l'application.

Le widget ne calcule rien lui-même. Il relit le tableau du mois que la page a
déjà envoyé à Android pour programmer l'adhan (`Schedule`), et y cherche sa
place dans la journée exactement comme la carte « اليوم » : pendant la
demi-heure qui suit l'adhan il annonce *la prière en cours*, ensuite *la
prochaine*. Les deux affichages ne peuvent donc pas se contredire.

**La question de la mise à jour.** Android ne rafraîchit un widget de lui-même
qu'une fois toutes les trente minutes au mieux — inutilisable pour un compte à
rebours. `Widget.java` pose donc son propre réveil à chaque minute pleine, en
`RTC` et non `RTC_WAKEUP` : **il ne réveille jamais le téléphone**, il attend
que celui-ci soit debout. Écran éteint, le widget ne coûte rien ; écran allumé,
il est juste à la minute. Le réveil est reposé après chaque dessin, et arrêté
dès que le dernier widget est retiré de l'écran d'accueil.

**L'horloge du milieu** est un `TextClock` : il se met à jour tout seul, dans le
processus de l'écran d'accueil, sans réveil ni redessin de notre part. Il est
forcé en 24 heures (`format12Hour="@null"`) pour s'accorder aux horaires du
tableau, quel que soit le réglage du téléphone.

**Le clignotement** du nom, dans la dernière demi-heure avant l'adhan, ne peut pas
venir du widget lui-même : ce qu'un widget affiche est figé jusqu'au prochain
dessin, et redessiner plusieurs fois par seconde coûterait la batterie qu'on
économise par ailleurs. Le nom est donc écrit **deux fois**, dans un `ViewFlipper`
qui les alterne de lui-même toutes les trois quarts de seconde. Hors urgence les
deux exemplaires sont blancs et l'alternance ne se voit pas ; à l'approche de
l'heure, le second passe au doré et le nom se met à clignoter — sans qu'Android
ait rien à redessiner. Le temps restant passe au doré en même temps, pour que
l'urgence reste visible même sur un lanceur qui refuse d'animer ses widgets.

Fichiers concernés : `Widget.java` (le réveil et le dessin), `Times.java` (où
en est-on dans la journée), `res/layout/widget.xml` (les cases à remplir),
`res/xml/widget_info.xml` (la fiche lue par l'écran d'accueil).

## La fenêtre « الصلاة الحالية »

Après l'adhan, l'application continue d'annoncer la prière qui vient de commencer
au lieu de passer tout de suite à la suivante. Cette durée est de **trente minutes**,
sauf pour le **Maghreb où elle tombe à quinze** : son temps est court et l'Icha suit
de près, mieux vaut annoncer la suivante plus tôt.

Les valeurs sont écrites **deux fois**, et doivent le rester à l'identique :

| Où | Quoi |
|---|---|
| `index.html` | `WINDOW` (30) et `WINDOW_PAR` (`{maghrib:15}`) |
| `Times.java` | `WINDOW_MIN` = `{30, 0, 30, 30, 15, 30}` — dans l'ordre صبح، شروق، ظهر، عصر، مغرب، عشاء |

C'est la seule chose que la page et le widget doivent se dire à l'identique sans
passer l'un par l'autre : si l'une des deux change, l'autre doit suivre, sinon la
carte « اليوم » et la vignette de l'écran d'accueil se contrediraient.

## La Qibla (اتجاه القبلة)

Quatrième onglet. Deux choses bien distinctes y sont affichées, et c'est tout
l'intérêt de l'écran :

- **L'angle** de la Qibla est un pur calcul de géométrie sur la sphère, depuis
  les coordonnées du lieu vers la Kaaba. Il ne dépend d'aucun capteur, ne peut
  pas se dérégler, et reste affiché même sur un téléphone sans boussole.
  Ce n'est pas « la direction sur la carte » : sur un globe, le chemin le plus
  court depuis le Maroc part presque plein est (95° depuis Rabat), et non vers
  le sud-est comme une carte plate le laisse croire.
- **Le cap du téléphone** vient du magnétomètre. Lui peut manquer, ou mentir
  près d'un aimant de coque, dans une voiture ou contre du béton armé.

L'aiguille montre la différence des deux : quand la Kaaba vient se poser sur le
repère bleu du haut, on est face à la Qibla. Tant que la boussole est éteinte,
la rose garde le nord en haut et le repère reste estompé.

Le lieu est celui de la ville affichée. Le bouton **تحديد موقعي** affine avec la
position réelle, utile loin d'une ville de la liste ; cette position reste en
mémoire vive et n'est jamais enregistrée ni envoyée.

Le même code sert aux deux plateformes :

- **Android** : `Compass.java` lit le capteur de rotation (`TYPE_ROTATION_VECTOR`,
  et à défaut accéléromètre + magnétomètre). Il corrige l'écart entre nord
  magnétique et nord géographique (`GeomagneticField`, environ −1° au Maroc),
  lisse les mesures sur le cercle, et n'envoie à la page que dix valeurs par
  seconde au plus. Le capteur s'éteint dès qu'on quitte l'onglet ou que l'écran
  passe en veille.
- **iPhone / navigateur** : `DeviceOrientationEvent`. iOS exige que
  l'autorisation soit demandée à la suite d'un geste, c'est pourquoi tout part
  du bouton **تشغيل البوصلة** et jamais du chargement de la page.
  `webkitCompassHeading` donne déjà le nord géographique.

Si le téléphone n'a pas de boussole, ou si elle ne répond pas, un message le dit
et renvoie aux repères du bas de l'écran : l'angle depuis le nord, le sens du
soleil levant, et l'ombre de midi — qui, elle, ne tombe jamais en panne.

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
