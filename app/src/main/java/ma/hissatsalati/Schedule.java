package ma.hissatsalati;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Programme la prochaine alarme (adhan ou rappel 5 min avant) à partir du
 * calendrier envoyé par la page web. Une seule alarme est posée à la fois :
 * quand elle sonne, on calcule la suivante.
 */
public final class Schedule {
    static final String PREFS = "hissat";
    static final String K_JSON = "schedule";
    static final String K_LAST = "lastFired";
    static final int REQ = 1001;
    static final long REMINDER_MS = 5 * 60 * 1000L;

    static final String[] KEYS  = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    static final String[] NAMES = {"الصبح", "الظهر", "العصر", "المغرب", "العشاء"};

    private Schedule() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context c, String json) {
        prefs(c).edit().putString(K_JSON, json).apply();
    }

    static void markFired(Context c, long at) {
        prefs(c).edit().putLong(K_LAST, at).apply();
    }

    /** Heure locale du jour ISO "2026-09-16" à "13:28", en millisecondes. */
    static long epoch(String iso, String hhmm) {
        String[] d = iso.split("-");
        String[] t = hhmm.split(":");
        Calendar cal = Calendar.getInstance();
        cal.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]),
                Integer.parseInt(t[0]), Integer.parseInt(t[1]), 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static PendingIntent pending(Context c, Intent i) {
        return PendingIntent.getBroadcast(c, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent base = new Intent(c, AlarmReceiver.class);
        am.cancel(pending(c, base));

        String json = prefs(c).getString(K_JSON, null);
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            boolean adhan = o.optBoolean("adhan", false);
            boolean notify = o.optBoolean("notify", false);
            if (!adhan && !notify) return;

            long now = System.currentTimeMillis() + 1000;
            long last = prefs(c).getLong(K_LAST, 0);
            long best = Long.MAX_VALUE;
            String bestType = null, bestName = null, bestTime = null;

            JSONArray days = o.optJSONArray("days");
            if (days == null) return;
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.getJSONObject(i);
                String iso = day.optString("d", "");
                if (iso.isEmpty()) continue;
                for (int k = 0; k < KEYS.length; k++) {
                    String t = day.optString(KEYS[k], "");
                    if (t.isEmpty()) continue;
                    long at;
                    try { at = epoch(iso, t); } catch (Exception e) { continue; }
                    if (notify) {
                        long r = at - REMINDER_MS;
                        if (r > now && r > last && r < best) {
                            best = r; bestType = "reminder"; bestName = NAMES[k]; bestTime = t;
                        }
                    }
                    if (adhan && at > now && at > last && at < best) {
                        best = at; bestType = "adhan"; bestName = NAMES[k]; bestTime = t;
                    }
                }
            }
            if (bestType == null) return;

            Intent i = new Intent(c, AlarmReceiver.class)
                    .putExtra("type", bestType)
                    .putExtra("prayer", bestName)
                    .putExtra("time", bestTime)
                    .putExtra("at", best);
            PendingIntent pi = pending(c, i);
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
        } catch (Exception ignored) {
        }
    }
}
