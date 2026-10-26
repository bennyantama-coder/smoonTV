package tv.widgetv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/** Boot, clock changes, and the prayer alarm. Playback itself lives in {@link AdhanService}. */
public final class AdhanReceiver extends BroadcastReceiver {
    /** A box that was off for hours should not play a stale adhan when it next wakes. */
    private static final long LATE_MS = 20L * 60_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AdhanScheduler.schedule(context);
            return;
        }
        if (AdhanScheduler.ACTION_STOP.equals(action)) {
            AdhanPlayer.stop(context);
            context.stopService(new Intent(context, AdhanService.class));
            return;
        }
        if (!AdhanScheduler.ACTION.equals(action)) return;
        if (!intent.getBooleanExtra(AdhanScheduler.EXTRA_PLAY, true)) {
            AdhanScheduler.schedule(context);
            return;
        }
        int prayer = intent.getIntExtra(AdhanScheduler.EXTRA_PRAYER, -1);
        long when = intent.getLongExtra(AdhanScheduler.EXTRA_WHEN, 0L);
        if (!AdhanCue.isPrayer(prayer) || when <= 0
                || !Config.get(context).prayerEnabled
                || !HomePrefs.adhanOn(context)
                || !HomePrefs.adhanPrayer(context, prayer)) {
            AdhanScheduler.schedule(context);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - when > LATE_MS || when - now > 60_000L) {
            AdhanScheduler.schedule(context);
            return;
        }
        if (!HomePrefs.claimAdhan(context, AdhanCue.stamp(prayer, when))) {
            AdhanScheduler.schedule(context);
            return;
        }
        holdCpu(context);
        AdhanScheduler.play(context, prayer, when);
        AdhanScheduler.schedule(context);
    }

    /** Keep the CPU up long enough for the service to start playback. The lock releases itself. */
    private static void holdCpu(Context context) {
        PowerManager pm = context.getSystemService(PowerManager.class);
        if (pm == null) return;
        PowerManager.WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "widgetv:adhan-start");
        lock.setReferenceCounted(false);
        lock.acquire(30_000L);
    }
}
