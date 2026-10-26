package tv.widgetv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * One exact alarm for the next prayer. The open screen is a backup for the same minute;
 * {@link HomePrefs#claimAdhan} stops the two paths from both sounding.
 */
final class AdhanScheduler {
    static final String ACTION = "tv.widgetv.home.ADHAN";
    static final String ACTION_STOP = "tv.widgetv.home.ADHAN_STOP";
    static final String EXTRA_PRAYER = "prayer";
    static final String EXTRA_WHEN = "when";
    static final String EXTRA_PLAY = "play";
    private static final int REQUEST = 4401;

    private AdhanScheduler() {
    }

    static void schedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        PendingIntent pending = pending(app);
        alarms.cancel(pending);
        if (!Config.get(app).prayerEnabled || !HomePrefs.adhanOn(app)) return;
        long now = System.currentTimeMillis();
        PrayerDay day = PrayerPrefs.day(app, now);
        boolean[] enabled = new boolean[6];
        for (int p : AdhanCue.PRAYERS) enabled[p] = HomePrefs.adhanPrayer(app, p);
        AdhanCue.Next next = AdhanCue.next(now, day.times, day.tomorrowFajr, enabled);
        if (next == null) return;
        Intent intent = new Intent(app, AdhanReceiver.class);
        intent.setAction(ACTION);
        intent.putExtra(EXTRA_PRAYER, next.prayer);
        intent.putExtra(EXTRA_WHEN, next.when);
        intent.putExtra(EXTRA_PLAY, next.play);
        PendingIntent fire = PendingIntent.getBroadcast(app, REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.when, fire);
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.when, fire);
            }
        } catch (SecurityException denied) {
            try {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.when, fire);
            } catch (RuntimeException ignored) {
                Log.w(Config.TAG, "adhan: alarm not scheduled");
            }
        }
    }

    static void play(Context context, int prayer, long when) {
        Intent service = new Intent(context, AdhanService.class);
        service.putExtra(EXTRA_PRAYER, prayer);
        service.putExtra(EXTRA_WHEN, when);
        try {
            context.startForegroundService(service);
        } catch (RuntimeException denied) {
            Log.w(Config.TAG, "adhan: foreground service refused", denied);
            AdhanPlayer.play(context, prayer, false, null);
        }
    }

    private static PendingIntent pending(Context app) {
        Intent intent = new Intent(app, AdhanReceiver.class);
        intent.setAction(ACTION);
        return PendingIntent.getBroadcast(app, REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
