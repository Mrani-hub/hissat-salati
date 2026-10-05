/* =====================================================================
   حصة صلاتي — fabrication du site installable (PWA)
   ---------------------------------------------------------------------
   Une seule source de vérité : app/src/main/assets/index.html, le fichier
   que l'APK Android embarque déjà. Ce script en fait une copie enrichie
   des quelques lignes qui transforment une page web en application
   installable, et dépose le tout dans web/_site/.

   Ali ne modifie donc QUE index.html : l'APK et le site se mettent à jour
   ensemble, sans risque d'oublier l'un des deux.

   Usage :  node web/build.mjs
   ===================================================================== */

import { createHash } from "node:crypto";
import { readFile, writeFile, mkdir, copyFile, rm, readdir } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ICI    = dirname(fileURLToPath(import.meta.url));   // …/apk/web
const RACINE = join(ICI, "..");                           // …/apk
const ASSETS = join(RACINE, "app", "src", "main", "assets");
const SOURCE = join(ASSETS, "index.html");
/* Le Coran et sa police ne sont pas dans la page : ce sont des fichiers voisins,
   chargés seulement quand on ouvre l'onglet القرآن. Ils doivent donc suivre. */
const VOISINS = ["quran-warsh.js", "UthmanicWarsh_V21.ttf"];
const SORTIE = join(ICI, "_site");

const stop = msg => { console.error("\n  ERREUR : " + msg + "\n"); process.exit(1); };

/* ---------- 1. La page d'origine ------------------------------------ */

if (!existsSync(SOURCE)) stop("page introuvable : " + SOURCE);
let html = await readFile(SOURCE, "utf8");

// Empreinte du contenu : elle change dès qu'une virgule change dans la page.
// C'est elle qui déclenche le bandeau « نسخة جديدة » chez les utilisateurs.
const VERSION = createHash("sha256").update(html).digest("hex").slice(0, 12);

/* ---------- 2. Les lignes qui rendent la page installable ------------ */

/* Adresse publique du site. WhatsApp, Messenger et Telegram n'affichent une
   vignette (icône + nom + description) que si l'adresse de l'image est
   ENTIÈRE : « ./icons/… » ne leur suffit pas, il leur faut « https://… ».
   Elle vient du workflow (SITE_URL), sinon on la déduit du nom du dépôt. */
function adressePublique() {
  const fournie = (process.env.SITE_URL || "").trim();
  if (fournie) return fournie.endsWith("/") ? fournie : fournie + "/";
  const depot = process.env.GITHUB_REPOSITORY || "";      // « Mrani-hub/hissat-salati »
  const [proprietaire, nom] = depot.split("/");
  if (proprietaire && nom) return `https://${proprietaire.toLowerCase()}.github.io/${nom}/`;
  return "";                                              // construction locale
}
const BASE = adressePublique();

/* La vignette du lien partagé. Sans adresse publique connue (essai local),
   on n'écrit rien plutôt que des adresses relatives qui ne marcheraient pas. */
const VIGNETTE = !BASE ? "" : `
<meta property="og:type" content="website">
<meta property="og:site_name" content="حصة صلاتي">
<meta property="og:title" content="حصة صلاتي — مواقيت الصلاة">
<meta property="og:description" content="المواقيت والرواتب والأذكار والمتابعة. يعمل بدون إنترنت. أضِفه إلى شاشتك الرئيسية.">
<meta property="og:locale" content="ar_MA">
<meta property="og:url" content="${BASE}">
<meta property="og:image" content="${BASE}icons/icon-512.png">
<meta property="og:image:type" content="image/png">
<meta property="og:image:width" content="512">
<meta property="og:image:height" content="512">
<meta property="og:image:alt" content="حصة صلاتي">
<meta name="twitter:card" content="summary">
`;

const TETE = `
<!-- ajouté par web/build.mjs : rend la page installable sur l'écran d'accueil -->
<link rel="manifest" href="./manifest.webmanifest">
<meta name="description" content="مواقيت الصلاة والرواتب والأذكار — يعمل بدون إنترنت">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
<meta name="apple-mobile-web-app-title" content="حصة صلاتي">
<link rel="apple-touch-icon" sizes="180x180" href="./icons/apple-touch-icon.png">
<link rel="icon" type="image/png" sizes="192x192" href="./icons/icon-192.png">
<link rel="icon" type="image/png" sizes="512x512" href="./icons/icon-512.png">
${VIGNETTE}`;

// Le paramètre ?v= force le navigateur à relire pwa.js quand la page change,
// au lieu de ressortir une vieille copie de son propre cache.
const PIED = `<script src="./pwa.js?v=${VERSION}"></script>\n`;

if (!html.includes("</head>")) stop("la page n'a pas de </head> : rien à modifier.");
if (!html.includes("</body>")) stop("la page n'a pas de </body> : rien à modifier.");
if (html.includes('rel="manifest"')) stop("la page contient déjà un manifeste : injection annulée.");

/* La page embarque déjà une icône Apple de 96 px en base64, qui sert dans
   l'APK. Sur l'écran d'accueil d'un iPhone la place fait 180 px : gardée,
   cette icône-là serait agrandie et floue. On la retire de la copie web
   (jamais de l'originale) pour laisser la place au fichier de 180 px. */
const AVANT = html.length;
html = html.replace(/^[ \t]*<link rel="apple-touch-icon"[^>]*>\r?\n?/gim, "");
if (html.length === AVANT)
  console.log("  note : aucune icône Apple intégrée à retirer (ce n'est pas une erreur)");

html = html.replace("</head>", TETE + "</head>");

// avant la dernière balise fermante du corps, pour que la page soit déjà
// construite et que pwa.js trouve les éléments qu'il complète
const fin = html.lastIndexOf("</body>");
html = html.slice(0, fin) + PIED + html.slice(fin);

/* ---------- 3. Écriture du dossier à publier ------------------------- */

await rm(SORTIE, { recursive: true, force: true });
await mkdir(join(SORTIE, "icons"), { recursive: true });

await writeFile(join(SORTIE, "index.html"), html, "utf8");

// Le service worker reçoit l'empreinte : nouveau contenu = nouveau cache.
const sw = (await readFile(join(ICI, "sw.js"), "utf8")).replace("__VERSION__", VERSION);
if (sw.includes("__VERSION__")) stop("sw.js : le repère __VERSION__ n'a pas été remplacé.");
await writeFile(join(SORTIE, "sw.js"), sw, "utf8");

for (const nom of VOISINS) {
  const src = join(ASSETS, nom);
  if (!existsSync(src)) stop("fichier voisin introuvable : " + src);
  await copyFile(src, join(SORTIE, nom));
}

await copyFile(join(ICI, "pwa.js"), join(SORTIE, "pwa.js"));
await copyFile(join(ICI, "manifest.webmanifest"), join(SORTIE, "manifest.webmanifest"));

for (const nom of await readdir(join(ICI, "icons"))) {
  if (nom.endsWith(".png")) await copyFile(join(ICI, "icons", nom), join(SORTIE, "icons", nom));
}

// GitHub Pages fait passer les sites par Jekyll, qui ignore les dossiers
// commençant par « _ ». Ce fichier vide le désactive.
await writeFile(join(SORTIE, ".nojekyll"), "");

/* ---------- 4. Compte rendu ------------------------------------------ */

const ko = n => (n / 1024).toFixed(0) + " Ko";
console.log("\n  Site fabriqué : " + SORTIE);
console.log("  version       : " + VERSION);
console.log("  adresse       : " + (BASE || "inconnue (essai local) — pas de vignette WhatsApp"));
console.log("  page          : " + ko(Buffer.byteLength(html)) + " (polices, code, données du calendrier)");
for (const nom of VOISINS)
  console.log("  " + nom.padEnd(14).slice(0, 14) + ": " + ko((await readFile(join(ASSETS, nom))).length) + " (chargé à la demande)");
console.log("\n  Essai local   : npx serve web/_site   puis ouvrir l'adresse affichée\n");
