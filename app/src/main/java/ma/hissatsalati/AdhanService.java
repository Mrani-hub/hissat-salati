package ma.hissatsalati;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.VolumeProviderCompat;

/**
 * Joue l'adhan au premier plan. S'arrête : à la fin du fichier, par le bouton
 * « إيقاف », ou dès qu'un bouton de volume est pressé.
 * Les touches de volume sont captées par une session média « à distance » : Android
 * lui envoie chaque appui (+ ou −) même si le volume est déjà au minimum ou au maximum,
 * ce que la simple écoute du changement de volume ne garantit pas.
 */
public class AdhanService extends Service {
    static final String ACTION_STOP = "ma.hissatsalati.STOP_ADHAN";
    static volatile boolean playing = false;

    private MediaPlayer mp;
    private BroadcastReceiver volumeWatcher;
    private MediaSessionCompat session;
    private long startedAt = 0;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAdhan();
            return START_NOT_STICKY;
        }
        String prayer = intent == null ? "" : intent.getStringExtra("prayer");
        String time = intent == null ? "" : intent.getStringExtra("time");
        if (prayer == null) prayer = "";
        if (time == null) time = "";

        Notif.ensureChannels(this);
        Notification n = buildNotification(prayer, time);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notif.ID_ADHAN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(Notif.ID_ADHAN, n);
        }

        if (mp != null) { stopPlayer(); }
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.adhan);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(p -> stopAdhan());
            mp.setOnErrorListener((p, w, e) -> { stopAdhan(); return true; });
            mp.prepare();
            mp.start();
            playing = true;
            startedAt = SystemClock.elapsedRealtime();
        } catch (Exception e) {
            stopAdhan();
            return START_NOT_STICKY;
        }

        startSession();

        // filet de sécurité : un changement de volume arrête aussi l'adhan
        if (volumeWatcher == null) {
            volumeWatcher = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    // on ignore un éventuel changement système juste au démarrage
                    if (SystemClock.elapsedRealtime() - startedAt > 1500) stopAdhan();
                }
            };
            ContextCompat.registerReceiver(this, volumeWatcher,
                    new IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                    ContextCompat.RECEIVER_EXPORTED);
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String prayer, String time) {
        Intent full = new Intent(this, AdhanActivity.class)
                .putExtra("prayer", prayer).putExtra("time", time)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(this, 3, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 4,
                new Intent(this, AdhanService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, Notif.CH_ADHAN)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(getString(R.string.adhan_title, prayer))
                .setContentText(getString(R.string.adhan_text, time))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .addAction(0, getString(R.string.stop_adhan), stop)
                .build();
    }

    /** Session média active : les touches de volume arrivent dans onAdjustVolume. */
    private void startSession() {
        if (session != null) return;
        try {
            session = new MediaSessionCompat(this, "adhan");
            session.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_STOP)
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f).build());
            session.setPlaybackToRemote(new VolumeProviderCompat(
                    VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, 100, 50) {
                @Override public void onAdjustVolume(int direction) {
                    // direction : +1 volume +, −1 volume −, 0 simple affichage → on ignore le 0
                    if (direction != 0 && SystemClock.elapsedRealtime() - startedAt > 800) stopAdhan();
                }
                @Override public void onSetVolumeTo(int volume) { stopAdhan(); }
            });
            session.setActive(true);
        } catch (Exception e) {
            session = null;
        }
    }

    private void stopSession() {
        if (session == null) return;
        try { session.setActive(false); session.release(); } catch (Exception ignored) {}
        session = null;
    }

    private void stopPlayer() {
        try { if (mp != null) { if (mp.isPlaying()) mp.stop(); mp.release(); } } catch (Exception ignored) {}
        mp = null;
        playing = false;
    }

    private void stopAdhan() {
        stopPlayer();
        stopSession();
        if (volumeWatcher != null) {
            try { unregisterReceiver(volumeWatcher); } catch (Exception ignored) {}
            volumeWatcher = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopPlayer();
        stopSession();
        if (volumeWatcher != null) {
            try { unregisterReceiver(volumeWatcher); } catch (Exception ignored) {}
            volumeWatcher = null;
        }
        super.onDestroy();
    }
}
