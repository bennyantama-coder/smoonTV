package tv.widgetv.home;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a pasted image address into something {@code HttpURLConnection} can fetch.
 * Public Google Drive file links are rewritten to the direct-download URL. Drive OAuth is not handled.
 */
final class WallpaperUrls {
    private static final Pattern DRIVE_PATH = Pattern.compile(
            "https?://drive\\.google\\.com/file/d/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DRIVE_QUERY = Pattern.compile("[?&]id=([A-Za-z0-9_-]+)");

    private WallpaperUrls() {
    }

    /** Absolute http(s) URL, or null when the text is not usable. */
    static String direct(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.indexOf(' ') >= 0 || s.indexOf('\n') >= 0) return null;
        Matcher path = DRIVE_PATH.matcher(s);
        if (path.find()) return drive(path.group(1));
        if (s.contains("drive.google.com")) {
            Matcher query = DRIVE_QUERY.matcher(s);
            if (query.find()) return drive(query.group(1));
        }
        if (s.startsWith("https://") || s.startsWith("http://")) return s;
        return null;
    }

    private static String drive(String id) {
        return "https://drive.google.com/uc?export=download&id=" + id;
    }
}
