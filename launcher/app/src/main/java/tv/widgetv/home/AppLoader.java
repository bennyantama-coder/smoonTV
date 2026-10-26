package tv.widgetv.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.palette.graphics.Palette;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finds launchable apps and renders their tile art. Not thread-safe: call it from one
 * background thread only. Rendered entries are cached per component until forget(pkg).
 */
final class AppLoader {
    /** A launchable activity and whether it came from LEANBACK_LAUNCHER. */
    static final class Candidate {
        final ActivityInfo info;
        final CharSequence label;
        final boolean leanback;

        Candidate(ActivityInfo info, CharSequence label, boolean leanback) {
            this.info = info;
            this.label = label;
            this.leanback = leanback;
        }

        String key() {
            return info.packageName + "/" + info.name + "/" + label;
        }
    }

    private final Context ctx;
    private final PackageManager pm;
    private final int artW;
    private final int artH;
    private final Map<ComponentName, AppEntry> cache = new HashMap<>();

    AppLoader(Context ctx, int artW, int artH) {
        this.ctx = ctx;
        this.pm = ctx.getPackageManager();
        this.artW = artW;
        this.artH = artH;
    }

    /** One activity per package, LEANBACK_LAUNCHER preferred over LAUNCHER. Cheap (no drawables). */
    Map<String, Candidate> query() {
        Map<String, Candidate> byPkg = new LinkedHashMap<>();
        addAll(byPkg, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addAll(byPkg, Intent.CATEGORY_LAUNCHER);
        byPkg.remove(ctx.getPackageName());
        return byPkg;
    }

    private void addAll(Map<String, Candidate> byPkg, String category) {
        boolean leanback = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category);
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(intent, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!byPkg.containsKey(pkg)) byPkg.put(pkg, new Candidate(ri.activityInfo, ri.loadLabel(pm), leanback));
        }
    }

    AppEntry entry(Candidate c) {
        ComponentName cn = new ComponentName(c.info.packageName, c.info.name);
        AppEntry cached = cache.get(cn);
        if (cached != null) return cached;
        Bitmap art = render(c.info);
        AppEntry e = new AppEntry(cn, String.valueOf(c.label), c.leanback, art);
        cache.put(cn, e);
        return e;
    }

    void forget(String pkg) {
        cache.keySet().removeIf(cn -> cn.getPackageName().equals(pkg));
    }

    /** Banner scaled to the tile size; apps without a banner get their icon on a tinted tile. */
    private Bitmap render(ActivityInfo ai) {
        Bitmap bmp = Bitmap.createBitmap(artW, artH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Drawable banner = ai.loadBanner(pm);
        if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
        if (banner != null) {
            banner.setBounds(0, 0, artW, artH);
            banner.draw(c);
            return bmp;
        }
        Drawable icon = ai.loadIcon(pm);
        Bitmap small = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, 48, 48);
        icon.draw(new Canvas(small));
        int base = Palette.from(small).generate().getDominantColor(0xFF3A3A3C);
        small.recycle();
        c.drawColor(scaleValue(base, 0.45f));
        int size = artH * 3 / 5;
        icon.setBounds((artW - size) / 2, (artH - size) / 2, (artW + size) / 2, (artH + size) / 2);
        icon.draw(c);
        return bmp;
    }

    private static int scaleValue(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }
}
