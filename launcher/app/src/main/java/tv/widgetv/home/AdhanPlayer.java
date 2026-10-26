package tv.widgetv.home;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * One adhan at a time. Subuh plays {@code res/raw/adhan_fajr}; the other prayers play {@code res/raw/adhan}.
 * A file the user picked on the TV replaces that slot when it can be opened.
 */
final class AdhanPlayer {
    interface Ui {
        void onPlaying(int prayer, boolean preview);

        void onStopped();
    }

    private static MediaPlayer player;
    private static AudioManager audioManager;
    private static AudioFocusRequest focusRequest;
    private static Ui ui;
    private static Runnable onDone;
    private static boolean preview;
    private static int prayer = -1;

    private AdhanPlayer() {
    }

    static void setUi(Ui next) {
        ui = next;
    }

    static boolean playing() {
        return player != null;
    }

    static boolean previewing() {
        return player != null && preview;
    }

    /** Prayer index while audio is open, or -1. */
    static int activePrayer() {
        return player == null ? -1 : prayer;
    }

    static void applyVolume(Context context) {
        MediaPlayer mp = player;
        if (mp == null) return;
        float gain = HomePrefs.adhanGain(context);
        try {
            mp.setVolume(gain, gain);
        } catch (IllegalStateException ignored) {
            // released between the check and the call
        }
    }

    static void play(Context context, int which, boolean asPreview, Runnable done) {
        play(context, which, asPreview, done, false);
    }

    /**
     * @param sameOwner true when the service is replacing its own playback and must stay running
     */
    static void play(Context context, int which, boolean asPreview, Runnable done, boolean sameOwner) {
        Context app = context.getApplicationContext();
        Runnable previous = onDone;
        onDone = null;
        boolean wasPlaying = player != null;
        releaseMedia();
        abandonFocus();
        if (!sameOwner && wasPlaying && ui != null) ui.onStopped();
        if (!sameOwner && previous != null) previous.run();
        preview = asPreview;
        prayer = which;
        onDone = done;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        requestFocus(app, attrs);
        boolean custom = HomePrefs.adhanUsesFile(app, which == AdhanCue.FAJR);
        boolean opened = begin(app, which, custom, attrs) || (custom && begin(app, which, false, attrs));
        if (!opened) stop(app);
    }

    static void stop(Context context) {
        Runnable done = onDone;
        onDone = null;
        boolean wasPlaying = player != null;
        releaseMedia();
        abandonFocus();
        preview = false;
        if (wasPlaying && ui != null) ui.onStopped();
        if (done != null) done.run();
    }

    /** @return false when the file could not be opened */
    private static boolean begin(Context app, int which, boolean custom, AudioAttributes attrs) {
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setAudioAttributes(attrs);
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
            open(app, mp, which, custom);
            mp.setOnPreparedListener(ready -> start(app, ready));
            mp.setOnCompletionListener(doneMp -> stop(app));
            mp.setOnErrorListener((m, what, extra) -> {
                Log.w(Config.TAG, "adhan: playback error " + what + "/" + extra);
                if (custom && player == m) {
                    releaseMedia();
                    if (begin(app, which, false, attrs)) return true;
                }
                stop(app);
                return true;
            });
            mp.prepareAsync();
            return true;
        } catch (IOException | RuntimeException failed) {
            Log.w(Config.TAG, "adhan: could not open recording", failed);
            releaseMedia();
            return false;
        }
    }

    private static void start(Context app, MediaPlayer ready) {
        if (ready != player) return;
        float gain = HomePrefs.adhanGain(app);
        try {
            ready.setVolume(gain, gain);
            ready.start();
        } catch (IllegalStateException ignored) {
            stop(app);
            return;
        }
        if (ui != null) ui.onPlaying(prayer, preview);
    }

    private static void open(Context app, MediaPlayer mp, int which, boolean custom) throws IOException {
        if (custom) {
            File file = AdhanFiles.file(app, which == AdhanCue.FAJR);
            mp.setDataSource(file.getAbsolutePath());
            return;
        }
        int res = which == AdhanCue.FAJR ? R.raw.adhan_fajr : R.raw.adhan;
        try (AssetFileDescriptor afd = app.getResources().openRawResourceFd(res)) {
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        }
    }

    /** Ask other apps to yield. Losing focus does not cut the adhan off. */
    private static void requestFocus(Context app, AudioAttributes attrs) {
        abandonFocus();
        audioManager = app.getSystemService(AudioManager.class);
        if (audioManager == null) return;
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> {
                    // The call to prayer keeps playing. Focus only asks a movie to duck.
                })
                .build();
        audioManager.requestAudioFocus(focusRequest);
    }

    private static void releaseMedia() {
        MediaPlayer mp = player;
        player = null;
        if (mp == null) return;
        try {
            mp.setOnPreparedListener(null);
            mp.setOnCompletionListener(null);
            mp.setOnErrorListener(null);
            mp.reset();
            mp.release();
        } catch (RuntimeException ignored) {
            // already released
        }
    }

    private static void abandonFocus() {
        if (audioManager != null && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
        focusRequest = null;
        audioManager = null;
    }
}
