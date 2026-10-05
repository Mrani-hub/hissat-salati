package ma.hissatsalati;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Widget de l'écran d'accueil : la prochaine prière, le temps qu'il reste, et
 * les cinq heures du jour — sans ouvrir l'application.
 *
 * Comment il se met à jour
 * ------------------------
 * Android ne rafraîchit un widget de lui-même qu'une fois toutes les trente
 * minutes au mieux, ce qui ne suffit pas pour un compte à rebours. On pose donc
 * notre propre réveil à chaque minute pleine, en RTC et non RTC_WAKEUP : il ne
 * réveille jamais le téléphone, il attend que celui-ci soit debout. Un widget
 * dont l'écran est éteint ne coûte donc rien, et il est à jour dès que l'écran
 * s'allume.
 *
 * Le réveil est reposé après chaque dessin, et arrêté dès que le dernier widget
 * est retiré de l'écran d'accueil.
 */
public class Widget extends AppWidgetProvider {

    static final String ACTION_TICK = "ma.hissatsalati.WIDGET_TICK";
    private static final int REQ_TICK = 1002;
    private static final int REQ_OPEN = 1003;

    /** Les cinq colonnes du bas : le lever du soleil n'y figure pas. */
    private static final int[] CELL_KEY  = {0, 2, 3, 4, 5};   // indices dans Times.KEYS
    private static final int[] CELL_NAME = {R.id.wN0, R.id.wN1, R.id.wN2, R.id.wN3, R.id.wN4};
    private static final int[] CELL_TIME = {R.id.wT0, R.id.wT1, R.id.wT2, R.id.wT3, R.id.wT4};

    private static final int DORE  = 0xFFE3B457;   // la prière mise en valeur
    private static final int PLEIN = 0xFFFFFFFF;
    private static final int PALE  = 0x8CFFFFFF;   // blanc atténué : les prières déjà passées

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) draw(c, m, id);
        scheduleTick(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);                       // laisse passer onUpdate, onDeleted...
        if (ACTION_TICK.equals(i.getAction())) refresh(c);
    }

    @Override
    public void onEnabled(Context c) {
        scheduleTick(c);
    }

    @Override
    public void onDisabled(Context c) {
        cancelTick(c);                               // plus aucun widget posé : plus de réveil
    }

    /**
     * Redessine tous les widgets posés, puis repose le réveil.
     * Appelé à chaque minute, au démarrage du téléphone, quand l'heure change,
     * quand l'adhan sonne, et quand la page envoie un nouveau calendrier.
     */
    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids;
        try {
            ids = m.getAppWidgetIds(new ComponentName(c, Widget.class));
        } catch (Exception e) {
            return;
        }
        if (ids == null || ids.length == 0) { cancelTick(c); return; }
        for (int id : ids) draw(c, m, id);
        scheduleTick(c);
    }

    /* ---------------- le réveil de la minute ---------------- */

    private static PendingIntent tick(Context c) {
        Intent i = new Intent(c, Widget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, REQ_TICK, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleTick(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        long at = now - (now % 60000L) + 60000L;     // la prochaine minute pleine
        try {
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) am.setExact(AlarmManager.RTC, at, tick(c));
            else am.set(AlarmManager.RTC, at, tick(c));
        } catch (Exception ignored) {
        }
    }

    private static void cancelTick(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) try { am.cancel(tick(c)); } catch (Exception ignored) {}
    }

    /* ---------------- le dessin ---------------- */

    private static void draw(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        Times t = Times.read(c);

        v.setTextViewText(R.id.wCity, t.city.isEmpty() ? c.getString(R.string.app_name) : t.city);
        v.setTextViewText(R.id.wDate, t.dateLine);

        if (!t.has || t.nextWhen == 0) {
            // le mois enregistré ne couvre pas aujourd'hui : on renvoie vers l'application
            v.setTextViewText(R.id.wLead, c.getString(R.string.widget_lead_next));
            v.setTextViewText(R.id.wName, "—");
            v.setTextViewText(R.id.wAt, "");
            v.setTextViewText(R.id.wLeft, c.getString(R.string.widget_no_month));
            v.setProgressBar(R.id.wBar, 100, 0, false);
            for (int i = 0; i < 5; i++) {
                v.setTextViewText(CELL_NAME[i], Times.NAMES[CELL_KEY[i]]);
                v.setTextViewText(CELL_TIME[i], "—");
                v.setTextColor(CELL_NAME[i], PALE);
                v.setTextColor(CELL_TIME[i], PALE);
            }
        } else if (t.running) {
            // la demi-heure qui suit l'adhan : c'est la prière en cours qu'on annonce
            long min = t.since / 60000L;
            v.setTextViewText(R.id.wLead, c.getString(R.string.widget_lead_now));
            v.setTextViewText(R.id.wName, Times.NAMES[t.cur]);
            v.setTextViewText(R.id.wAt, t.time[t.cur]);
            v.setTextViewText(R.id.wLeft, min < 1
                    ? c.getString(R.string.widget_just_now)
                    : c.getString(R.string.widget_since, min));
            v.setProgressBar(R.id.wBar, 100, t.progress(), false);
            fillRow(v, t);
        } else {
            v.setTextViewText(R.id.wLead, t.tomorrow
                    ? c.getString(R.string.widget_lead_tomorrow)
                    : c.getString(R.string.widget_lead_next));
            v.setTextViewText(R.id.wName, t.nextName);
            v.setTextViewText(R.id.wAt, t.nextTime);
            v.setTextViewText(R.id.wLeft, left(c, t.nextWhen - System.currentTimeMillis()));
            v.setProgressBar(R.id.wBar, 100, t.progress(), false);
            fillRow(v, t);
        }

        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.wRoot, PendingIntent.getActivity(c, REQ_OPEN, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        try { m.updateAppWidget(id, v); } catch (Exception ignored) {}
    }

    /** La rangée du bas : la prochaine prière en doré, celles déjà passées atténuées. */
    private static void fillRow(RemoteViews v, Times t) {
        int vedette = t.running ? t.cur : t.nextPrayer();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            int k = CELL_KEY[i];
            boolean vue = k == vedette;
            boolean passee = !vue && t.when[k] > 0 && t.when[k] <= now;
            int couleur = vue ? DORE : (passee ? PALE : PLEIN);
            v.setTextViewText(CELL_NAME[i], Times.NAMES[k]);
            v.setTextViewText(CELL_TIME[i], t.time[k].isEmpty() ? "—" : t.time[k]);
            v.setTextColor(CELL_NAME[i], couleur);
            v.setTextColor(CELL_TIME[i], couleur);
        }
    }

    /** Le temps restant, arrondi à la minute : « بعد 3 س و 12 د ». */
    private static String left(Context c, long ms) {
        if (ms <= 0) return c.getString(R.string.widget_due);
        long min = (ms + 59999L) / 60000L;           // arrondi vers le haut
        long h = min / 60, r = min % 60;
        if (h == 0) return c.getString(R.string.widget_in_m, r);
        if (r == 0) return c.getString(R.string.widget_in_h, h);
        return c.getString(R.string.widget_in_hm, h, r);
    }
}
