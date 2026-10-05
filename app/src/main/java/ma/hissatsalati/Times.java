package ma.hissatsalati;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/**
 * Lecture du calendrier que la page a déposé dans les préférences, et calcul
 * de la prière en cours ou à venir.
 *
 * C'est la même logique que la fonction timeline() d'index.html, réécrite ici
 * parce que le widget de l'écran d'accueil n'a pas de WebView : il doit savoir
 * seul quelle prière annoncer, sans que l'application soit ouverte.
 *
 * Aucune heure n'est recalculée : on se contente de relire le tableau envoyé
 * par la page, qui vient soit du calendrier officiel des Habous, soit du calcul
 * par ville. Les deux côtés affichent donc toujours la même chose.
 */
final class Times {

    /** Les six repères du jour, dans l'ordre, tels que la page les envoie. */
    static final String[] KEYS  = {"fajr", "shuruq", "dhuhr", "asr", "maghrib", "isha"};
    static final String[] NAMES = {"الصبح", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    /** Le lever du soleil n'est pas une prière : on ne l'annonce pas « en cours ». */
    static final boolean[] IS_PRAYER = {true, false, true, true, true, true};

    /**
     * Minutes pendant lesquelles la prière reste annoncée « en cours » après son adhan,
     * repère par repère. Le Maghreb n'en garde qu'un quart d'heure : son temps est court
     * et l'Icha suit de près, mieux vaut annoncer la suivante plus tôt.
     * La page applique exactement les mêmes valeurs (constante WINDOW d'index.html).
     */
    static final int[] WINDOW_MIN = {30, 0, 30, 30, 15, 30};

    static long windowMs(int k) { return WINDOW_MIN[k] * 60 * 1000L; }

    private static final String[] AR_DAYS =
            {"الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};

    boolean has = false;          // le calendrier couvre-t-il la journée d'aujourd'hui ?
    String city = "";             // ville du tableau en cours
    String dateLine = "";         // « الأحد 23 ربيع الثاني »
    final String[] time = new String[6];   // « 13:28 » ou "" quand l'heure manque
    final long[] when = new long[6];       // la même heure en millisecondes, 0 si absente

    int cur = -1;                 // dernier repère déjà passé
    long since = 0;               // temps écoulé depuis ce repère
    boolean running = false;      // on est dans la demi-heure qui suit l'adhan

    int next = -1;                // prochain repère du jour, -1 si la journée est finie
    String nextName = "", nextTime = "";
    long nextWhen = 0;
    boolean tomorrow = false;     // le prochain repère est le Subh de demain

    private Times() {}

    /** Date du jour au format « 2026-10-05 », à partir d'un instant. */
    private static String isoOf(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static String arDay(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return AR_DAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private static JSONObject dayFor(JSONArray days, String iso) {
        if (days == null) return null;
        for (int i = 0; i < days.length(); i++) {
            JSONObject d = days.optJSONObject(i);
            if (d != null && iso.equals(d.optString("d", ""))) return d;
        }
        return null;
    }

    /** Lit le calendrier enregistré et situe l'instant présent dedans. */
    static Times read(Context c) {
        Times x = new Times();
        for (int k = 0; k < 6; k++) x.time[k] = "";
        String json = Schedule.prefs(c).getString(Schedule.K_JSON, null);
        if (json == null) return x;
        try {
            JSONObject o = new JSONObject(json);
            x.city = o.optString("city", "");
            String hmonth = o.optString("hmonth", "");
            JSONArray days = o.optJSONArray("days");

            long now = System.currentTimeMillis();
            JSONObject day = dayFor(days, isoOf(now));
            if (day == null) return x;        // le mois affiché ne couvre pas aujourd'hui
            x.has = true;
            x.dateLine = (arDay(now) + " " + day.optString("h", "") + " " + hmonth).trim();

            String iso = day.optString("d", "");
            for (int k = 0; k < 6; k++) {
                String t = day.optString(KEYS[k], "");
                x.time[k] = t;
                if (t.isEmpty()) continue;
                try { x.when[k] = Schedule.epoch(iso, t); } catch (Exception e) { x.time[k] = ""; }
            }

            for (int k = 5; k >= 0; k--) {
                if (x.when[k] > 0 && x.when[k] <= now) { x.cur = k; break; }
            }
            if (x.cur >= 0) {
                x.since = now - x.when[x.cur];
                x.running = IS_PRAYER[x.cur] && x.since < windowMs(x.cur);
            }

            for (int k = 0; k < 6; k++) {
                if (x.when[k] > 0 && x.when[k] > now) { x.next = k; break; }
            }
            if (x.next >= 0) {
                x.nextName = NAMES[x.next];
                x.nextTime = x.time[x.next];
                x.nextWhen = x.when[x.next];
            } else {
                // la journée est finie : on annonce le Subh de demain
                String iso2 = isoOf(now + 86400000L);
                JSONObject d2 = dayFor(days, iso2);
                String f = d2 != null ? d2.optString("fajr", "") : x.time[0];
                if (!f.isEmpty()) {
                    try {
                        x.nextWhen = Schedule.epoch(iso2, f);
                        x.nextName = NAMES[0];
                        x.nextTime = f;
                        x.tomorrow = true;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {
        }
        return x;
    }

    /** Prochaine des cinq prières (le lever du soleil est sauté), pour la mettre en valeur. */
    int nextPrayer() {
        for (int k = 0; k < 6; k++) {
            if (IS_PRAYER[k] && when[k] > 0 && when[k] > System.currentTimeMillis()) return k;
        }
        return -1;
    }

    /** Part du créneau déjà écoulée, de 0 à 100, pour la barre du widget. */
    int progress() {
        if (running) return (int) Math.min(100, since * 100 / windowMs(cur));
        if (cur < 0 || nextWhen <= when[cur]) return 0;
        long total = nextWhen - when[cur];
        return (int) Math.max(0, Math.min(100, (System.currentTimeMillis() - when[cur]) * 100 / total));
    }
}
