package ma.hissatsalati;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

/**
 * Position approximative du téléphone, sans Google Play Services.
 *
 * Elle ne sert qu'à une chose : savoir de quelle ville de la liste on est le plus
 * proche, pour adopter ses horaires quand on voyage. Une précision au kilomètre
 * suffit donc largement — d'où ACCESS_COARSE_LOCATION seulement, jamais le GPS fin.
 * La position ne quitte pas le téléphone : elle est remise telle quelle à la page,
 * qui fait son calcul en mémoire et n'appelle aucun serveur.
 *
 * Trois étapes, de la moins coûteuse à la plus coûteuse en batterie :
 *   1. une position déjà connue et récente (moins d'un quart d'heure) : on la prend ;
 *   2. sinon on demande une mesure aux fournisseurs actifs (réseau, puis GPS) ;
 *   3. au bout de vingt-cinq secondes on abandonne, et on se rabat sur la position
 *      connue même ancienne — en voyage, une position d'il y a deux heures reste
 *      bien plus juste que la ville d'origine.
 */
public final class Loc {

    /** Réponse à une demande de position. Toujours appelée sur le fil principal. */
    public interface OnFix {
        void onFix(double lat, double lon, float acc);
        /** "permission" : refusée · "disabled" : service de localisation éteint · "timeout" : aucune mesure. */
        void onError(String code);
    }

    private static final long FRESH_MS   = 15 * 60 * 1000L;   // position encore valable
    private static final long TIMEOUT_MS = 25 * 1000L;        // attente maximale d'une mesure

    private Loc() {}

    /** L'utilisateur a-t-il autorisé la localisation ? */
    static boolean granted(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
    }

    static void get(Context c, OnFix cb) {
        if (!granted(c)) { cb.onError("permission"); return; }

        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { cb.onError("disabled"); return; }

        Location known = lastKnown(lm);
        if (known != null && System.currentTimeMillis() - known.getTime() < FRESH_MS) {
            hand(cb, known);
            return;
        }
        live(lm, known, cb);
    }

    /** La meilleure des positions déjà en mémoire, tous fournisseurs confondus (aucun coût). */
    private static Location lastKnown(LocationManager lm) {
        Location best = null;
        for (String p : new String[]{LocationManager.NETWORK_PROVIDER,
                                     LocationManager.GPS_PROVIDER,
                                     LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException | IllegalArgumentException ignored) {}
        }
        return best;
    }

    /** Demande une mesure fraîche, avec abandon au bout de TIMEOUT_MS. */
    private static void live(LocationManager lm, Location fallback, OnFix cb) {
        final Handler h = new Handler(Looper.getMainLooper());
        final boolean[] done = {false};
        final LocationListener[] box = new LocationListener[1];

        LocationListener l = new LocationListener() {
            @Override public void onLocationChanged(Location loc) {
                if (done[0] || loc == null) return;
                done[0] = true;
                h.removeCallbacksAndMessages(null);
                release(lm, box[0]);
                hand(cb, loc);
            }
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
            @Override public void onStatusChanged(String p, int s, Bundle b) {}
        };
        box[0] = l;

        boolean asked = false;
        for (String p : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
            try {
                if (!lm.isProviderEnabled(p)) continue;
                lm.requestLocationUpdates(p, 0L, 0f, l, Looper.getMainLooper());
                asked = true;
            } catch (SecurityException | IllegalArgumentException ignored) {}
        }

        if (!asked) {                       // localisation éteinte dans les réglages du téléphone
            release(lm, l);
            if (fallback != null) hand(cb, fallback); else cb.onError("disabled");
            return;
        }

        h.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            release(lm, box[0]);
            if (fallback != null) hand(cb, fallback); else cb.onError("timeout");
        }, TIMEOUT_MS);
    }

    private static void release(LocationManager lm, LocationListener l) {
        if (l == null) return;
        try { lm.removeUpdates(l); } catch (SecurityException ignored) {}
    }

    private static void hand(OnFix cb, Location l) {
        cb.onFix(l.getLatitude(), l.getLongitude(), l.getAccuracy());
    }
}
