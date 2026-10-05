package ma.hissatsalati;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * La direction du nord géographique, pour l'aiguille de la Qibla.
 *
 * Trois précautions, parce qu'une boussole de téléphone est un instrument fragile :
 *
 * 1. Nord magnétique contre nord géographique. Le capteur montre le nord
 *    magnétique ; la direction de la Kaaba est un angle géographique. L'écart
 *    entre les deux (la déclinaison) vaut environ -1 degré au Maroc, davantage
 *    ailleurs. GeomagneticField le donne pour le lieu et la date, et on l'ajoute.
 *
 * 2. Lissage. Les valeurs brutes sautent de deux ou trois degrés sans arrêt, ce
 *    qui rend l'aiguille illisible. On fait une moyenne glissante sur le cercle
 *    (par le sinus et le cosinus, sinon la moyenne de 359 et 1 donnerait 180).
 *
 * 3. Débit. On n'envoie à la page que dix mesures par seconde au maximum, et
 *    seulement quand l'angle a vraiment bougé : chaque envoi traverse le pont
 *    JavaScript, ce n'est pas gratuit.
 *
 * L'écran de l'application est bloqué en portrait (voir le manifeste), il n'y a
 * donc pas de rotation d'écran à compenser.
 */
final class Compass implements SensorEventListener {

    interface OnHeading {
        /** degres : 0 = nord géographique, 90 = est. precision : valeur SENSOR_STATUS_*, -1 si inconnue. */
        void onHeading(float degres, int precision);
    }

    private static final long MIN_MS = 100;        // dix envois par seconde au plus
    private static final float MIN_DEG = 0.4f;     // en dessous, l'aiguille ne bougerait pas à l'oeil
    private static final float LISSAGE = 0.18f;    // part de la nouvelle mesure dans la moyenne

    private final SensorManager sm;
    private final OnHeading rappel;

    private final float[] matrice = new float[9];
    private final float[] angles = new float[3];
    private final float[] gravite = new float[3];
    private final float[] champ = new float[3];
    private boolean aGravite = false, aChamp = false;

    private float declinaison = 0f;
    private int precision = -1;
    private boolean enMarche = false;

    private float sin = 0f, cos = 0f;              // moyenne glissante sur le cercle
    private boolean premier = true;
    private long dernierEnvoi = 0;
    private float dernierAngle = -999f;

    Compass(Context c, OnHeading rappel) {
        this.sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        this.rappel = rappel;
    }

    /** Le téléphone a-t-il de quoi faire une boussole ? */
    static boolean available(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return false;
        if (sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) return true;
        return sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null;
    }

    /** Le lieu d'observation, pour corriger l'écart entre nord magnétique et nord vrai. */
    void setPlace(double lat, double lon) {
        try {
            declinaison = new GeomagneticField(
                    (float) lat, (float) lon, 0f, System.currentTimeMillis()).getDeclination();
        } catch (Exception e) {
            declinaison = 0f;
        }
    }

    boolean start() {
        if (sm == null) return false;
        if (enMarche) return true;
        premier = true;
        dernierAngle = -999f;
        Sensor rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rot != null) {
            enMarche = sm.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME);
            if (enMarche) return true;
        }
        Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (acc == null || mag == null) return false;
        boolean a = sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
        boolean b = sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME);
        enMarche = a && b;
        if (!enMarche) stop();
        return enMarche;
    }

    void stop() {
        if (sm == null) return;
        try { sm.unregisterListener(this); } catch (Exception ignored) {}
        enMarche = false;
        aGravite = false;
        aChamp = false;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        Float brut = null;
        if (e.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            try {
                SensorManager.getRotationMatrixFromVector(matrice, e.values);
                SensorManager.getOrientation(matrice, angles);
                brut = (float) Math.toDegrees(angles[0]);
            } catch (Exception ignored) {
                return;
            }
        } else if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(e.values, 0, gravite, 0, 3);
            aGravite = true;
        } else if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(e.values, 0, champ, 0, 3);
            aChamp = true;
        }
        if (brut == null) {
            if (!aGravite || !aChamp) return;
            if (!SensorManager.getRotationMatrix(matrice, null, gravite, champ)) return;
            SensorManager.getOrientation(matrice, angles);
            brut = (float) Math.toDegrees(angles[0]);
        }

        float vrai = (brut + declinaison) % 360f;
        if (vrai < 0) vrai += 360f;

        // moyenne glissante sur le cercle : on lisse le point, pas le nombre
        double r = Math.toRadians(vrai);
        if (premier) {
            sin = (float) Math.sin(r);
            cos = (float) Math.cos(r);
            premier = false;
        } else {
            sin += (float) ((Math.sin(r) - sin) * LISSAGE);
            cos += (float) ((Math.cos(r) - cos) * LISSAGE);
        }
        float lisse = (float) Math.toDegrees(Math.atan2(sin, cos));
        if (lisse < 0) lisse += 360f;

        long maintenant = System.currentTimeMillis();
        if (maintenant - dernierEnvoi < MIN_MS) return;
        float ecart = Math.abs(lisse - dernierAngle);
        if (ecart > 180f) ecart = 360f - ecart;
        if (dernierAngle > -900f && ecart < MIN_DEG) return;
        dernierEnvoi = maintenant;
        dernierAngle = lisse;
        rappel.onHeading(lisse, precision);
    }

    @Override
    public void onAccuracyChanged(Sensor s, int p) {
        // seul le magnétomètre (ou la fusion qui s'appuie sur lui) renseigne sur la fiabilité
        if (s == null) return;
        int t = s.getType();
        if (t == Sensor.TYPE_MAGNETIC_FIELD || t == Sensor.TYPE_ROTATION_VECTOR) precision = p;
    }
}
