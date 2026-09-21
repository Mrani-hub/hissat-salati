/* =====================================================================
   حصة صلاتي — couche « application web installable » (PWA)
   ---------------------------------------------------------------------
   Ce fichier n'existe QUE dans la version site web. L'APK Android charge
   le même index.html sans lui : le pont natif AndroidBridge y fait déjà
   tout ce travail (partage de l'APK, mises à jour, adhan).

   Ce que ce script ajoute, dans cet ordre :
     1. la mise en cache pour fonctionner sans connexion ;
     2. le bandeau « نسخة جديدة » quand une mise à jour est prête ;
     3. l'invitation à ajouter l'application à l'écran d'accueil ;
     4. la carte « تثبيت ومشاركة » : bouton WhatsApp, lien à copier,
        mode d'emploi iPhone et Android ;
     5. l'explication honnête de ce que le web ne peut pas faire : appeler
        l'adhan tout seul quand l'application est fermée.
   ===================================================================== */
(function () {
  "use strict";

  /* ---------- 1. Où sommes-nous ? ---------------------------------- */

  const UA = navigator.userAgent || "";

  // iPhone / iPad. (Un iPad récent se présente comme un Mac : on le
  // reconnaît au fait qu'il a un écran tactile.)
  const IOS = /iPad|iPhone|iPod/.test(UA) ||
              (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

  // Navigateur intégré à une autre application (WhatsApp, Facebook,
  // Instagram…). Ceux-là ne savent pas ajouter à l'écran d'accueil : il faut
  // d'abord rouvrir le lien dans Safari ou Chrome.
  const IN_APP = /FBAN|FBAV|Instagram|WhatsApp|Line\/|Snapchat|Twitter|MicroMessenger/i.test(UA);

  // « L'application tourne-t-elle depuis l'écran d'accueil ? »
  // matchMedia manque sur les très vieux iPhone : on ne veut pas que tout le
  // script s'arrête là, sinon la page perdrait aussi le mode hors connexion.
  const media = q => {
    try { return window.matchMedia ? window.matchMedia(q) : null; }
    catch (e) { return null; }
  };
  const ECRAN_ACCUEIL = media("(display-mode: standalone)");

  const installed = () =>
    (ECRAN_ACCUEIL ? ECRAN_ACCUEIL.matches : false) ||
    window.navigator.standalone === true;

  // Adresse propre à partager : sans « index.html », sans paramètres.
  const LIEN = location.origin + location.pathname.replace(/index\.html$/i, "");

  const $ = s => document.querySelector(s);
  const el = (tag, attrs, html) => {
    const n = document.createElement(tag);
    if (attrs) for (const k in attrs) n.setAttribute(k, attrs[k]);
    if (html != null) n.innerHTML = html;
    return n;
  };

  /* ---------- 2. Réglages d'affichage propres à l'écran d'accueil ---- */

  const css = document.createElement("style");
  css.textContent = [
    /* encoche et barre du bas de l'iPhone, une fois l'application lancée
       depuis l'écran d'accueil : le contenu ne doit pas passer dessous */
    "body.pwa-installed .wrap{",
    "  padding-top:calc(14px + env(safe-area-inset-top,0px));",
    "  padding-bottom:calc(56px + env(safe-area-inset-bottom,0px));}",
    /* pas de rebond élastique façon page web : sensation d'application */
    "body.pwa-installed{overscroll-behavior-y:none}",

    ".pwa-bar{display:flex;align-items:center;gap:10px;flex-wrap:wrap;",
    "  background:var(--panel,#fff);border:1px solid rgba(17,33,46,.14);",
    "  border-radius:10px;padding:10px 12px;margin:0 0 14px;",
    "  box-shadow:0 1px 2px rgba(17,33,46,.08)}",
    ".pwa-bar .pwa-txt{flex:1;min-width:190px;font-family:var(--arab);",
    "  direction:rtl;font-size:.98rem;line-height:1.5}",
    ".pwa-bar .pwa-sub{display:block;font-size:.78rem;color:var(--ink-soft,#5b6b78)}",
    ".pwa-x{border:0;background:transparent;font-size:1.2rem;line-height:1;",
    "  color:var(--ink-soft,#5b6b78);cursor:pointer;padding:2px 6px}",
    ".pwa-steps{direction:rtl;margin:8px 0 0;padding-inline-start:0;",
    "  padding-inline-end:1.2em;font-size:.9rem;line-height:1.9}",
    ".pwa-link{direction:ltr;text-align:left;font-size:.8rem;word-break:break-all;",
    "  font-family:ui-monospace,Menlo,Consolas,monospace;",
    "  background:rgba(17,33,46,.05);border-radius:8px;padding:8px 10px;margin:8px 0 0}"
  ].join("\n");
  document.head.appendChild(css);

  const marquerInstalle = () => document.body.classList.toggle("pwa-installed", installed());
  marquerInstalle();

  // Safari 14+ et Chrome utilisent addEventListener ; les versions plus
  // anciennes n'ont que addListener. On prend ce qui existe, et rien si
  // aucun des deux : la classe est de toute façon posée au démarrage.
  if (ECRAN_ACCUEIL) {
    if (ECRAN_ACCUEIL.addEventListener) ECRAN_ACCUEIL.addEventListener("change", marquerInstalle);
    else if (ECRAN_ACCUEIL.addListener) ECRAN_ACCUEIL.addListener(marquerInstalle);
  }

  /* ---------- 3. Hors connexion + mises à jour ----------------------- */

  let attente = null;   // le nouveau service worker, prêt mais pas encore activé

  function bandeauMaj() {
    if ($("#pwaUpd")) return;
    const b = el("div", { class: "pwa-bar", id: "pwaUpd" },
      '<span class="pwa-txt">تتوفّر نسخة جديدة من التطبيق' +
      '<span class="pwa-sub">اضغط للتحديث — مواقيتك ومتابعتك محفوظة</span></span>');
    const btn = el("button", { class: "act", type: "button" }, "تحديث الآن");
    btn.addEventListener("click", () => {
      btn.disabled = true;
      btn.textContent = "جارٍ التحديث…";
      if (attente) attente.postMessage({ type: "skipWaiting" });
      else location.reload();
    });
    b.appendChild(btn);
    const w = $(".wrap");
    if (w) w.insertBefore(b, w.firstChild);
  }

  if ("serviceWorker" in navigator) {
    window.addEventListener("load", async () => {
      try {
        const reg = await navigator.serviceWorker.register("./sw.js", { scope: "./" });

        // une version neuve attendait déjà (application rouverte après coup)
        if (reg.waiting && navigator.serviceWorker.controller) {
          attente = reg.waiting;
          bandeauMaj();
        }

        // une version neuve arrive pendant que l'application est ouverte
        reg.addEventListener("updatefound", () => {
          const sw = reg.installing;
          if (!sw) return;
          sw.addEventListener("statechange", () => {
            // « installed » + un contrôleur existant = ce n'est pas la
            // première installation, c'est bien une mise à jour
            if (sw.state === "installed" && navigator.serviceWorker.controller) {
              attente = sw;
              bandeauMaj();
            }
          });
        });

        // la nouvelle version a pris la main : on recharge une seule fois
        let recharge = false;
        navigator.serviceWorker.addEventListener("controllerchange", () => {
          if (recharge) return;
          recharge = true;
          location.reload();
        });

        // à chaque retour au premier plan, on demande s'il y a du neuf,
        // sans rien imposer à l'utilisateur
        const verifier = () => { try { reg.update(); } catch (e) {} };
        document.addEventListener("visibilitychange", () => {
          if (document.visibilityState === "visible") verifier();
        });
        setTimeout(verifier, 3000);
      } catch (e) {
        // pas de service worker (navigation privée, vieux navigateur) :
        // l'application marche quand même, simplement pas hors connexion
      }
    });
  }

  /* ---------- 4. Ajouter à l'écran d'accueil ------------------------- */

  let invite = null;   // Android/Chrome fournit une vraie invitation système

  window.addEventListener("beforeinstallprompt", e => {
    e.preventDefault();
    invite = e;
    rendre();
  });
  window.addEventListener("appinstalled", () => {
    invite = null;
    rendre();
  });

  async function installer() {
    if (!invite) return false;
    invite.prompt();
    const r = await invite.userChoice;
    invite = null;
    rendre();
    return !!r && r.outcome === "accepted";
  }

  /* ---------- 5. Partager le lien ------------------------------------ */

  const TEXTE = "حصة صلاتي — مواقيت الصلاة والرواتب والأذكار، بدون إنترنت:";

  async function partager(note) {
    // Le vrai sélecteur du téléphone : WhatsApp, Messages, Mail…
    if (navigator.share) {
      try {
        await navigator.share({ title: "حصة صلاتي", text: TEXTE, url: LIEN });
        return note("تمت المشاركة", "ok");
      } catch (e) {
        if (e && e.name === "AbortError") return note("", "");
      }
    }
    // Sinon : le lien dans le presse-papier
    try {
      await navigator.clipboard.writeText(LIEN);
      return note("نُسخ الرابط. ألصقه في واتساب.", "ok");
    } catch (e) {
      return note("انسخ الرابط الظاهر أسفله يدويًا.", "");
    }
  }

  function whatsapp() {
    // wa.me ouvre WhatsApp avec le message déjà écrit ; l'utilisateur
    // n'a plus qu'à choisir le destinataire.
    const u = "https://wa.me/?text=" + encodeURIComponent(TEXTE + " " + LIEN);
    window.open(u, "_blank", "noopener");
  }

  /* ---------- 6. La carte dans l'onglet التحميل ---------------------- */

  function carte() {
    let c = $("#pwacard");
    if (c) return c;

    c = el("div", { class: "card pad", id: "pwacard", style: "margin-top:14px" });

    // point d'insertion : la place qu'occupe « مشاركة التطبيق » dans l'APK
    const ancre = $("#sharecard") || $("#updcard");
    if (ancre && ancre.parentNode) ancre.parentNode.insertBefore(c, ancre);
    else { const w = $(".wrap"); (w || document.body).appendChild(c); }
    return c;
  }

  function rendre() {
    const c = carte();

    /* --- bloc « installation », selon la situation exacte du visiteur --- */
    let install;
    if (installed()) {
      install = '<p class="note" style="margin:0">التطبيق مثبّت على هذا الجهاز ✓ — يعمل بدون إنترنت.</p>';
    } else if (IN_APP) {
      install =
        '<p class="note" style="margin:0 0 6px"><b>افتح هذا الرابط في المتصفّح أولًا.</b> ' +
        'من داخل واتساب أو فيسبوك لا يمكن تثبيت التطبيق.</p>' +
        '<ol class="pwa-steps">' +
        '<li>اضغط زر <b>⋯</b> أو <b>⌄</b> في زاوية هذه الصفحة.</li>' +
        '<li>اختر <b>' + (IOS ? "فتح في Safari" : "فتح في Chrome") + '</b>.</li>' +
        '<li>ثم تابع خطوات التثبيت التي ستظهر هنا.</li></ol>';
    } else if (IOS) {
      install =
        '<p class="note" style="margin:0">أضِف التطبيق إلى الشاشة الرئيسية ليعمل بدون إنترنت وبملء الشاشة:</p>' +
        '<ol class="pwa-steps">' +
        '<li>اضغط زر المشاركة (المربّع والسهم ↑) في شريط سفاري.</li>' +
        '<li>مرِّر القائمة واختر <b>إضافة إلى الشاشة الرئيسية</b>.</li>' +
        '<li>اضغط <b>إضافة</b>: تظهر أيقونة «حصة صلاتي» مع باقي تطبيقاتك.</li></ol>';
    } else if (invite) {
      install =
        '<p class="note" style="margin:0 0 10px">ثبّت التطبيق على جهازك: يفتح بملء الشاشة ويعمل بدون إنترنت.</p>' +
        '<div class="row" style="margin-top:0">' +
        '<button class="act" id="pwaInstall" type="button">تثبيت التطبيق</button></div>';
    } else {
      install =
        '<p class="note" style="margin:0">من قائمة المتصفّح اختر <b>تثبيت التطبيق</b> ' +
        'أو <b>إضافة إلى الشاشة الرئيسية</b>، ليعمل بدون إنترنت.</p>';
    }

    /* --- l'adhan : ce que le web sait faire, et ce qu'il ne sait pas --- */
    const adhan =
      '<p class="note" style="margin:0">نسخة الويب لا ترفع الأذان والتطبيق مغلق: هذا قيد في الأيفون نفسه، ' +
      'إذ لا يسمح لأي صفحة ويب بتشغيل صوت في وقت محدّد. أمّا المواقيت والعدّ التنازلي والرواتب ' +
      'والأذكار والمتابعة فتعمل كلّها كالمعتاد، وبدون إنترنت.' +
      (IOS ? "" :
        '<br>على الأندرويد، نسخة التطبيق المستقلّة ترفع الأذان بصوت كامل: ' +
        '<a href="https://github.com/Mrani-hub/hissat-salati/releases/latest" ' +
        'target="_blank" rel="noopener">تنزيل تطبيق أندرويد</a>.') +
      '</p>';

    c.innerHTML =
      '<h3>تثبيت التطبيق</h3>' + install +
      '<h3 style="margin-top:16px">مشاركة التطبيق</h3>' +
      '<p class="note" style="margin:0 0 10px">أرسل الرابط لمن تحب: يفتحه، يضيفه إلى شاشته الرئيسية، ' +
      'ويستعمله بدون إنترنت. يعمل على الأيفون والأندرويد والحاسوب.</p>' +
      '<div class="row" style="margin-top:0">' +
      '<button class="act" id="pwaShare" type="button">مشاركة الرابط</button>' +
      '<button class="act ghost" id="pwaWa" type="button">إرسال عبر واتساب</button></div>' +
      '<div class="status" id="pwaNote"></div>' +
      '<p class="pwa-link">' + LIEN + '</p>' +
      '<h3 style="margin-top:16px">الأذان والتنبيهات</h3>' + adhan;

    const note = (m, cls) => {
      const n = $("#pwaNote");
      if (!n) return;
      n.textContent = m || "";
      n.className = "status " + (cls || "");
    };

    const bi = $("#pwaInstall"); if (bi) bi.addEventListener("click", installer);
    const bs = $("#pwaShare");   if (bs) bs.addEventListener("click", () => partager(note));
    const bw = $("#pwaWa");      if (bw) bw.addEventListener("click", whatsapp);
  }

  /* ---------- 7. Le bandeau d'invitation, en haut de la page --------- */

  const CLE = "pwa_bandeau_ferme";   // « l'utilisateur a fermé le bandeau »

  function bandeauInstall() {
    if (installed() || $("#pwaAdd")) return;
    try { if (localStorage.getItem(CLE) === "1") return; } catch (e) {}
    if (!IOS && !IN_APP && !invite) return;   // rien d'utile à proposer

    const texte = IN_APP
      ? 'افتح الرابط في ' + (IOS ? "Safari" : "Chrome") + ' لتثبيت التطبيق' +
        '<span class="pwa-sub">من داخل واتساب لا يمكن التثبيت</span>'
      : 'أضِف «حصة صلاتي» إلى شاشتك الرئيسية' +
        '<span class="pwa-sub">' +
        (IOS ? "زر المشاركة ← إضافة إلى الشاشة الرئيسية"
             : "يفتح بملء الشاشة ويعمل بدون إنترنت") + '</span>';

    const b = el("div", { class: "pwa-bar", id: "pwaAdd" },
                 '<span class="pwa-txt">' + texte + '</span>');

    if (invite && !IN_APP) {
      const btn = el("button", { class: "act", type: "button" }, "تثبيت");
      btn.addEventListener("click", async () => { if (await installer()) b.remove(); });
      b.appendChild(btn);
    } else {
      const btn = el("button", { class: "act ghost", type: "button" }, "كيف؟");
      btn.addEventListener("click", () => {
        const t = $("#t-load"); if (t) t.click();
        const c = $("#pwacard"); if (c) c.scrollIntoView({ behavior: "smooth", block: "start" });
      });
      b.appendChild(btn);
    }

    const x = el("button", { class: "pwa-x", type: "button", "aria-label": "إغلاق" }, "✕");
    x.addEventListener("click", () => {
      b.remove();
      try { localStorage.setItem(CLE, "1"); } catch (e) {}
    });
    b.appendChild(x);

    const w = $(".wrap");
    if (w) w.insertBefore(b, w.firstChild);
  }

  /* ---------- 8. Démarrage ------------------------------------------- */

  function demarrer() {
    rendre();
    // on laisse la page finir son propre affichage avant de proposer quoi que ce soit
    setTimeout(bandeauInstall, 1200);
  }

  if (document.readyState === "loading")
    document.addEventListener("DOMContentLoaded", demarrer);
  else
    demarrer();
})();
