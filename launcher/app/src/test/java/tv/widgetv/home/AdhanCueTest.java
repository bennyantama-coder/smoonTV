package tv.widgetv.home;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AdhanCueTest {
    private static final long MIN = 60_000L;

    @Test
    public void prayersSkipSunrise() {
        assertEquals(0, AdhanCue.FAJR);
        assertEquals(1, AdhanCue.SUNRISE);
        assertEquals(5, AdhanCue.ISHA);
        assertFalse(AdhanCue.isPrayer(AdhanCue.SUNRISE));
        assertTrue(AdhanCue.isPrayer(AdhanCue.FAJR));
        assertEquals("Subuh", AdhanCue.label(AdhanCue.FAJR));
        assertEquals("Zuhur", AdhanCue.label(AdhanCue.DHUHR));
        assertEquals("Magrib", AdhanCue.label(AdhanCue.MAGHRIB));
    }

    @Test
    public void dueUsesGraceAndIgnoresSunrise() {
        long now = 1_000_000L;
        long[] times = {now - 10_000L, now - 5_000L, now + MIN, now + 2 * MIN, now + 3 * MIN, now + 4 * MIN};
        assertEquals(AdhanCue.FAJR, AdhanCue.due(now, times, AdhanCue.GRACE_MS));
        assertEquals(-1, AdhanCue.due(now, times, 9_000L));
        assertEquals(-1, AdhanCue.due(times[AdhanCue.FAJR] - 1, times, AdhanCue.GRACE_MS));
        times[AdhanCue.MAGHRIB] = now - 20_000L;
        times[AdhanCue.ISHA] = now - 5_000L;
        assertEquals(AdhanCue.ISHA, AdhanCue.due(now, times, AdhanCue.GRACE_MS));
        assertEquals(-1, AdhanCue.due(now, null, AdhanCue.GRACE_MS));
    }

    @Test
    public void stampIsStableForOneMinute() {
        assertEquals(AdhanCue.stamp(0, 120_000L), AdhanCue.stamp(0, 120_000L + 59_000L));
        assertFalse(AdhanCue.stamp(0, 120_000L).equals(AdhanCue.stamp(0, 180_000L)));
        assertFalse(AdhanCue.stamp(0, 120_000L).equals(AdhanCue.stamp(2, 120_000L)));
    }

    @Test
    public void nextSkipsDisabledPrayersAndSunrise() {
        long now = 5_000L;
        long[] times = {1_000L, 2_000L, 10_000L, 20_000L, 30_000L, 40_000L};
        boolean[] on = {true, true, true, false, true, true};
        AdhanCue.Next next = AdhanCue.next(now, times, 50_000L, on);
        assertEquals(AdhanCue.DHUHR, next.prayer);
        assertEquals(10_000L, next.when);
        assertTrue(next.play);
        on[AdhanCue.DHUHR] = false;
        on[AdhanCue.ASR] = true;
        on[AdhanCue.MAGHRIB] = false;
        on[AdhanCue.ISHA] = false;
        AdhanCue.Next asr = AdhanCue.next(now, times, 50_000L, on);
        assertEquals(AdhanCue.ASR, asr.prayer);
        assertTrue(asr.play);
    }

    @Test
    public void tomorrowFajrSchedulesEvenWhenSubuhIsSilent() {
        long now = 100_000L;
        long[] times = {1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 6_000L};
        boolean[] off = {false, false, true, true, true, true};
        AdhanCue.Next checkpoint = AdhanCue.next(now, times, now + 10 * MIN, off);
        assertEquals(AdhanCue.FAJR, checkpoint.prayer);
        assertFalse(checkpoint.play);
        off[AdhanCue.FAJR] = true;
        AdhanCue.Next play = AdhanCue.next(now, times, now + 10 * MIN, off);
        assertTrue(play.play);
        assertEquals(now + 10 * MIN, play.when);
        assertNull(AdhanCue.next(now, times, now, off));
        assertNull(AdhanCue.next(now, null, now + MIN, off));
    }
}
