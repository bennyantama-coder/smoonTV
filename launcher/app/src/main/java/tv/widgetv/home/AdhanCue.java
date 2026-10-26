package tv.widgetv.home;

/**
 * Which prayer should sound, and when the next one is. Pure logic so it can be tested without a TV.
 * Indices match {@link PrayerDay}: 0 Subuh, 1 sunrise, 2 Zuhur, 3 Asar, 4 Magrib, 5 Isya.
 * Sunrise is never an adhan. Subuh uses a different recording from the other four.
 */
final class AdhanCue {
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    static final int[] PRAYERS = {FAJR, DHUHR, ASR, MAGHRIB, ISHA};
    /** While the app is open, a prayer that started this recently can still sound if the alarm did not. */
    static final long GRACE_MS = 90_000L;

    private AdhanCue() {
    }

    static boolean isPrayer(int index) {
        for (int p : PRAYERS) if (p == index) return true;
        return false;
    }

    /** Indonesian name used on the TV. */
    static String label(int prayer) {
        switch (prayer) {
            case FAJR:
                return "Subuh";
            case DHUHR:
                return "Zuhur";
            case ASR:
                return "Asar";
            case MAGHRIB:
                return "Magrib";
            case ISHA:
                return "Isya";
            default:
                return "";
        }
    }

    /**
     * Latest of the five prayers at or before {@code now}, and no older than {@code graceMs}.
     * Returns -1 when nothing in that window is due. Sunrise is ignored.
     */
    static int due(long now, long[] times, long graceMs) {
        if (times == null || graceMs < 0) return -1;
        int best = -1;
        long bestAt = Long.MIN_VALUE;
        for (int p : PRAYERS) {
            if (p >= times.length) continue;
            long at = times[p];
            if (at <= now && now - at <= graceMs && at >= bestAt) {
                best = p;
                bestAt = at;
            }
        }
        return best;
    }

    /** One play per prayer per minute. Alarm and the open screen share this key. */
    static String stamp(int prayer, long whenMillis) {
        return prayer + "@" + (whenMillis / 60_000L);
    }

    static final class Next {
        final int prayer;
        final long when;
        /** False when this alarm only wakes the scheduler (Subuh is off, but Zuhur tomorrow still needs an alarm). */
        final boolean play;

        Next(int prayer, long when, boolean play) {
            this.prayer = prayer;
            this.when = when;
            this.play = play;
        }
    }

    /**
     * Soonest enabled prayer strictly after {@code now}. When today's five are past, returns tomorrow's
     * Subuh: it plays only when Subuh is enabled, and otherwise exists so the next day can be scheduled.
     */
    static Next next(long now, long[] times, long tomorrowFajr, boolean[] enabled) {
        if (times == null || enabled == null) return null;
        long soonest = Long.MAX_VALUE;
        int which = -1;
        for (int p : PRAYERS) {
            if (p >= times.length || p >= enabled.length || !enabled[p]) continue;
            long at = times[p];
            if (at > now && at < soonest) {
                soonest = at;
                which = p;
            }
        }
        if (which >= 0) return new Next(which, soonest, true);
        long horizon = 48L * 3_600_000L;
        if (tomorrowFajr > now && tomorrowFajr - now < horizon) {
            boolean play = FAJR < enabled.length && enabled[FAJR];
            return new Next(FAJR, tomorrowFajr, play);
        }
        return null;
    }
}
