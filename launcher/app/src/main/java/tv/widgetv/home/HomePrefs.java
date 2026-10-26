package tv.widgetv.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Choices made on the TV with the remote. SharedPreferences "home" survives reboot.
 * Until a value is saved, the screen keeps res/values/config.xml (Padang, Netflix/YouTube dock, sky).
 */
final class HomePrefs {
    static final String CLOCK = "clock";
    static final String WEATHER = "weather";
    static final String PRAYER = "prayer";
    static final String QUOTE = "quote";

    private HomePrefs() {
    }

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    /** Dock order. Unset means the config.xml defaults, including packages that are not installed yet. */
    static List<String> dockPackages(Context c) {
        if (!of(c).contains("dock")) {
            return new ArrayList<>(Arrays.asList(c.getResources().getStringArray(R.array.dock_packages)));
        }
        return DockOrder.parse(of(c).getString("dock", ""));
    }

    static void setDock(Context c, List<String> pkgs) {
        of(c).edit().putString("dock", DockOrder.format(pkgs)).apply();
    }

    static boolean dockCustomized(Context c) {
        return of(c).contains("dock");
    }

    static Place place(Context c) {
        String id = of(c).getString("place_id", Places.PADANG.id);
        if ("custom".equals(id)) {
            double lat = parseCoord(of(c).getString("custom_lat", "-0.9471"), -0.9471, 90);
            double lng = parseCoord(of(c).getString("custom_lng", "100.4172"), 100.4172, 180);
            String zone = of(c).getString("custom_zone", "Asia/Jakarta");
            String name = of(c).getString("custom_name", "Lokasi sendiri");
            if (name == null || name.isEmpty()) name = "Lokasi sendiri";
            return new Place("custom", name, zone == null || zone.isEmpty() ? "Asia/Jakarta" : zone, lat, lng);
        }
        Place p = Places.byId(id);
        return p != null ? p : Places.PADANG;
    }

    static void setPlaceId(Context c, String id) {
        of(c).edit().putString("place_id", id).apply();
    }

    static void setCustomPlace(Context c, double lat, double lng, String zone) {
        of(c).edit()
                .putString("place_id", "custom")
                .putString("custom_lat", String.format(Locale.US, "%.5f", lat))
                .putString("custom_lng", String.format(Locale.US, "%.5f", lng))
                .putString("custom_zone", zone)
                .putString("custom_name", "Lokasi sendiri")
                .apply();
    }

    static java.time.ZoneId zone(Context c) {
        try {
            return java.time.ZoneId.of(place(c).zone);
        } catch (RuntimeException bad) {
            return java.time.ZoneId.of("Asia/Jakarta");
        }
    }

    static String wallpaperMode(Context c) {
        String m = of(c).getString("wallpaper", "sky");
        if ("file".equals(m) || "url".equals(m)) return m;
        return "sky";
    }

    static void setWallpaperSky(Context c) {
        of(c).edit().putString("wallpaper", "sky").apply();
    }

    static void setWallpaperFile(Context c) {
        of(c).edit().putString("wallpaper", "file").apply();
    }

    static void setWallpaperUrl(Context c, String url) {
        of(c).edit().putString("wallpaper", "url").putString("wallpaper_url", url).apply();
    }

    static String wallpaperUrl(Context c) {
        return of(c).getString("wallpaper_url", "");
    }

    static List<String> rowOrder(Context c) {
        return RowOrder.parse(of(c).getString("rows", null));
    }

    static void setRowOrder(Context c, List<String> order) {
        of(c).edit().putString("rows", RowOrder.format(order)).apply();
    }

    static boolean visible(Context c, String widget) {
        return of(c).getBoolean("vis_" + widget, true);
    }

    static void setVisible(Context c, String widget, boolean on) {
        of(c).edit().putBoolean("vis_" + widget, on).apply();
    }

    /** 0 kecil, 1 biasa, 2 besar. */
    static int size(Context c, String widget) {
        return Math.max(0, Math.min(2, of(c).getInt("size_" + widget, 1)));
    }

    static void setSize(Context c, String widget, int size) {
        of(c).edit().putInt("size_" + widget, Math.max(0, Math.min(2, size))).apply();
    }

    static boolean hintPending(Context c) {
        return !of(c).getBoolean("hint_seen", false);
    }

    static void markHintSeen(Context c) {
        of(c).edit().putBoolean("hint_seen", true).apply();
    }

    private static double parseCoord(String raw, double fallback, double limit) {
        try {
            double v = Double.parseDouble(raw);
            if (Math.abs(v) <= limit) return v;
        } catch (RuntimeException ignored) {
            // fall through
        }
        return fallback;
    }
}
