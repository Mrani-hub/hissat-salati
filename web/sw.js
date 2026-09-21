/* =====================================================================
   حصة صلاتي — service worker
   ---------------------------------------------------------------------
   Rôle : garder une copie de l'application dans le téléphone, pour qu'elle
   s'ouvre instantanément et fonctionne sans connexion, exactement comme
   l'APK Android.

   VERSION est remplacée à la construction par l'empreinte du fichier
   index.html (voir build.mjs). Elle change donc à chaque modification de la
   page : le navigateur voit un service worker différent, télécharge la
   nouvelle version en arrière-plan, et la page affiche le bandeau « نسخة
   جديدة ». Rien n'est remplacé sous les doigts de l'utilisateur tant qu'il
   n'a pas accepté.
   ===================================================================== */

const VERSION = "__VERSION__";
const CACHE   = "hissat-salati-" + VERSION;

/* Tout ce dont l'application a besoin pour s'ouvrir hors connexion.
   La page est un fichier unique : polices, images et code sont dedans. */
const FILES = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
  "./icons/icon-maskable.png",
  "./icons/apple-touch-icon.png"
];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)));
});

/* Une nouvelle version est active : on jette les caches des versions passées. */
self.addEventListener("activate", e => {
  e.waitUntil((async () => {
    const noms = await caches.keys();
    await Promise.all(
      noms.filter(n => n.startsWith("hissat-salati-") && n !== CACHE)
          .map(n => caches.delete(n))
    );
    await self.clients.claim();
  })());
});

/* La page demande à passer tout de suite sur la nouvelle version. */
self.addEventListener("message", e => {
  if (e.data && e.data.type === "skipWaiting") self.skipWaiting();
});

self.addEventListener("fetch", e => {
  const req = e.request;

  // On ne s'occupe que des lectures de nos propres fichiers.
  // Le site des Habous et les autres adresses passent directement au réseau.
  if (req.method !== "GET") return;
  if (new URL(req.url).origin !== self.location.origin) return;

  /* Ouverture de l'application : le cache d'abord (démarrage immédiat, et
     fonctionne dans l'avion), le réseau seulement s'il n'y a rien en cache. */
  if (req.mode === "navigate") {
    e.respondWith((async () => {
      const cache = await caches.open(CACHE);
      return (await cache.match("./index.html")) || fetch(req);
    })());
    return;
  }

  e.respondWith((async () => {
    const cache = await caches.open(CACHE);
    const hit = await cache.match(req, { ignoreSearch: true });
    if (hit) return hit;
    try {
      const net = await fetch(req);
      // On garde une copie des fichiers servis correctement, pour la prochaine fois.
      if (net && net.ok && net.type === "basic") cache.put(req, net.clone());
      return net;
    } catch (err) {
      return new Response("", { status: 504, statusText: "hors connexion" });
    }
  })());
});
