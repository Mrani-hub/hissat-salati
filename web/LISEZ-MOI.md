# حصة صلاتي sur iPhone — comment ça marche

## D'abord, la mauvaise nouvelle, clairement

**Sur iPhone, on ne peut pas envoyer une application par WhatsApp.** Ce n'est pas
une limite de notre code, c'est un verrou d'Apple : tout fichier d'application iOS
doit être *signé pour chaque téléphone précis* avant d'y entrer. Il n'existe pas
d'équivalent du « .apk qu'on ouvre et qu'on installe ». Aucune application de
prière sur iPhone ne s'installe autrement que par l'App Store.

Publier sur l'App Store demanderait : un Mac, un compte développeur Apple à
99 $ par an, et la validation d'Apple à chaque mise à jour.

## La bonne nouvelle : ce dossier fait la même chose autrement

Au lieu d'envoyer un fichier, **on envoie un lien**. La personne l'ouvre, fait
« Ajouter à l'écran d'accueil », et obtient une icône حصة صلاتي sur son iPhone
qui s'ouvre en plein écran, sans barre Safari, et **fonctionne sans connexion**.

Pour l'utilisateur, la différence avec une vraie application est invisible.

| | APK Android | Lien iPhone (ce dossier) |
|---|---|---|
| Envoi par WhatsApp | le fichier | le lien |
| Installation | ouvrir le fichier | « Ajouter à l'écran d'accueil » |
| Icône sur le téléphone | oui | oui |
| Plein écran | oui | oui |
| Marche sans internet | oui | oui |
| Mise à jour | bouton dans l'application | automatique à l'ouverture |
| Adhan qui sonne, application fermée | **oui** | **non** — voir plus bas |
| Marche aussi sur Android et PC | non | oui |

## Ce que le lien ne peut pas faire : l'adhan automatique

Une page web, même posée sur l'écran d'accueil, **n'a pas le droit de jouer un son
à une heure précise quand elle est fermée**. C'est un choix d'Apple, pas un oubli
de notre part : cela empêcherait les sites de réveiller les gens la nuit.

Tout le reste fonctionne à l'identique : les mawaqit, le compte à rebours, les
rawatib, les adhkar, le suivi, l'import des Habous, le partage de l'image du mois.

Les utilisateurs Android qui veulent l'adhan gardent l'APK — la page leur propose
d'ailleurs le lien de téléchargement toute seule.

---

## Mise en service, une seule fois

1. Pousser ce dossier sur GitHub (dépôt `Mrani-hub/hissat-salati`).
2. Sur GitHub : **Settings → Pages → Build and deployment → Source :
   « GitHub Actions »**.
3. Onglet **Actions → Site iPhone → Run workflow**.
4. Deux minutes plus tard, l'adresse s'affiche dans le résumé du workflow :

   `https://mrani-hub.github.io/hissat-salati/`

C'est **ce lien** que tu envoies par WhatsApp. Il ne change jamais.

## Ce que voit la personne qui reçoit le lien

La page se comporte différemment selon d'où elle est ouverte — c'est voulu :

- **Ouvert depuis WhatsApp** : un bandeau prévient qu'il faut d'abord faire
  « Ouvrir dans Safari », parce que le navigateur interne de WhatsApp ne sait pas
  ajouter à l'écran d'accueil. C'est le piège n°1, il est traité en premier.
- **Ouvert dans Safari (iPhone)** : les trois étapes pour ajouter à l'écran
  d'accueil, avec le bouton partage de Safari.
- **Ouvert dans Chrome (Android/PC)** : un vrai bouton « تثبيت » système.
- **Déjà installé** : plus aucun bandeau, juste « التطبيق مثبّت ✓ ».

Dans tous les cas, l'onglet **التحميل** contient les boutons « مشاركة الرابط »
et « إرسال عبر واتساب » pour faire suivre à son tour.

## Mettre à jour l'application

**Tu ne changes qu'un seul fichier : `app/src/main/assets/index.html`.**
C'est le même que celui de l'APK. En le poussant sur GitHub :

- l'APK Android est reconstruit (workflow **APK**) ;
- le site iPhone est republié (workflow **Site iPhone**).

À la prochaine ouverture, les iPhone voient un bandeau « تتوفّر نسخة جديدة »
avec un bouton. Les mawaqit et le suivi enregistrés ne sont pas touchés.

## Essayer sur ton PC avant de publier

```
node web/build.mjs
npx serve web/_site
```

Puis ouvre l'adresse affichée. Attention : l'ajout à l'écran d'accueil et le
mode hors connexion **exigent une adresse en `https://`** — sur `localhost` ils
sont désactivés par le navigateur. Le vrai essai se fait sur l'adresse GitHub.

## À quoi sert chaque fichier

| Fichier | Rôle |
|---|---|
| `build.mjs` | fabrique `_site/` à partir de `index.html`. **Aucune copie manuelle** : une seule source pour l'APK et le site. |
| `pwa.js` | la carte « installer / partager », le mode d'emploi selon le téléphone, le bandeau de mise à jour. Chargé **uniquement** par le site, jamais par l'APK. |
| `sw.js` | garde l'application dans le téléphone pour qu'elle marche sans connexion, et gère le passage à une nouvelle version. |
| `manifest.webmanifest` | la carte d'identité : nom, icône, couleurs, plein écran. |
| `icons/` | l'icône aux tailles attendues par iPhone (180 px) et Android (192 et 512 px). |
| `_site/` | le résultat, reconstruit à chaque fois. **Pas dans Git** : ne jamais le modifier à la main. |

Le fichier `index.html` de l'APK n'est **jamais** modifié par ce dossier : la
copie web est enrichie au moment de la fabrication, l'original reste intact.
