package ma.hissatsalati;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Après un redémarrage ou un changement d'heure, les alarmes sont perdues : on les repose. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Schedule.scheduleNext(c);
    }
}
