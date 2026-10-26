package tv.widgetv.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

/** Short-lived playback when WidgeTV is not on screen. The notification has no sound of its own. */
public final class AdhanService extends Service {
    private static final String CHANNEL = "adhan";
    private static final int NOTE_ID = 4403;
    private static final int STOP_REQUEST = 4402;

    private MediaSession session;
    private boolean stopping;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int prayer = intent == null ? -1 : intent.getIntExtra(AdhanScheduler.EXTRA_PRAYER, -1);
        ensureChannel();
        ensureSession();
        startInForeground(note(prayer));
        if (!AdhanCue.isPrayer(prayer)) {
            finish();
            return START_NOT_STICKY;
        }
        AdhanPlayer.play(this, prayer, false, this::finish, true);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (!stopping) AdhanPlayer.stop(this);
        releaseSession();
        super.onDestroy();
    }

    private void finish() {
        if (stopping) return;
        stopping = true;
        releaseSession();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void startInForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTE_ID, notification);
        }
    }

    private Notification note(int prayer) {
        String title = AdhanCue.isPrayer(prayer) ? "Adzan " + AdhanCue.label(prayer) : "Adzan";
        Intent stop = new Intent(this, AdhanReceiver.class);
        stop.setAction(AdhanScheduler.ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getBroadcast(this, STOP_REQUEST, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action action = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_adhan), "Hentikan", stopPi).build();
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_adhan)
                .setContentTitle(title)
                .setContentText("OK atau Hentikan untuk berhenti")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(action);
        if (session != null) {
            builder.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()));
        }
        return builder.build();
    }

    private void ensureChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Adzan", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }

    private void ensureSession() {
        if (session != null) return;
        session = new MediaSession(this, "adhan");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onStop() {
                AdhanPlayer.stop(AdhanService.this);
            }

            @Override
            public void onPause() {
                AdhanPlayer.stop(AdhanService.this);
            }
        });
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_STOP)
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .build());
        session.setActive(true);
    }

    private void releaseSession() {
        if (session == null) return;
        session.setActive(false);
        session.release();
        session = null;
    }
}
