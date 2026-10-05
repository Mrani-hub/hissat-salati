package ma.hissatsalati;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Après un redémarrage ou un changement d'heure, les alarmes sont perdues : on les repose.
 * Le widget de l'écran d'accueil perd de même son réveil de la minute, et son contenu
 * devient faux après un changement de fuseau : on le redessine dans le même mouvement.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Schedule.scheduleNext(c);
        Widget.refresh(c);
    }
}
