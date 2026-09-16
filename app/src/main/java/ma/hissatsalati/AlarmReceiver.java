package ma.hissatsalati;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/** Reçoit l'alarme : lance l'adhan, ou affiche le rappel 5 minutes avant. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String type = intent.getStringExtra("type");
        String prayer = intent.getStringExtra("prayer");
        String time = intent.getStringExtra("time");
        long at = intent.getLongExtra("at", 0);
        Schedule.markFired(c, at);

        if ("adhan".equals(type)) {
            Intent s = new Intent(c, AdhanService.class)
                    .putExtra("prayer", prayer).putExtra("time", time);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } else if ("reminder".equals(type)) {
            Notif.ensureChannels(c);
            Intent open = new Intent(c, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 2, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, Notif.CH_REMINDER)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(c.getString(R.string.reminder_title, prayer))
                    .setContentText(c.getString(R.string.reminder_text, time))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            try { nm.notify(Notif.ID_REMINDER, b.build()); } catch (SecurityException ignored) {}
        }
        Schedule.scheduleNext(c);
    }
}
