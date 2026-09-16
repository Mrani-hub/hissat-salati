package ma.hissatsalati;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/** Canaux de notification : un pour l'adhan (silencieux, le son vient du lecteur), un pour les rappels. */
public final class Notif {
    static final String CH_ADHAN = "adhan";
    static final String CH_REMINDER = "reminder";
    static final int ID_ADHAN = 2;
    static final int ID_REMINDER = 3;

    private Notif() {}

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel a = new NotificationChannel(CH_ADHAN,
                c.getString(R.string.ch_adhan), NotificationManager.IMPORTANCE_HIGH);
        a.setSound(null, null);
        a.enableVibration(false);
        nm.createNotificationChannel(a);
        NotificationChannel r = new NotificationChannel(CH_REMINDER,
                c.getString(R.string.ch_reminder), NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(r);
    }
}
