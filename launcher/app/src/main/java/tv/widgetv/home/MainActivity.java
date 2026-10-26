package tv.widgetv.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen. The activity owns the D-pad completely: nothing is focusable, focus
 * is a (zone, index) pair, and one gold ring (GlassHighlight) springs to whatever that points at.
 * Zones top to bottom: pills (TOP), prayer timeline (or, with prayer_enabled=false, the clock header),
 * dock, grid; plus the Inputs dropdown and the settings panel as modal zones. Hold OK to open the
 * on-TV editor (dock, wallpaper, city, widget rows, adhan alarm), which is stored in HomePrefs. Everything time-based
 * updates on ACTION_TIME_TICK only. Defaults come from res/values/config.xml (Config).
 */
public class MainActivity extends Activity {
    private static final String TAG = Config.TAG;
    private static final int COLS = 4;
    static final int REQ_WALL = 4101;
    static final int REQ_READ = 4102;
    static final int REQ_ADHAN_FAJR = 4103;
    static final int REQ_ADHAN_OTHER = 4104;
    /** Debug broadcast action: "<applicationId>.DEBUG_SKY". */
    private String debugAction() {
        return getPackageName() + ".DEBUG_SKY";
    }

    private enum Zone { TOP, TIMELINE, DOCK, GRID, MENU, PANEL }

    private ScrollView page;
    private FrameLayout content;
    private SkyView sky;
    private PrayerTimeline timeline;
    private GlassHighlight glass;
    private TextView clock, awayClock, date, ssid;
    private ImageView[] pills; // the three round buttons: Inputs, Wi-Fi, Settings
    private Typeface ui;
    private LinearLayout menu, panel;
    private RecyclerView dockList, gridList;
    private TileAdapter dockAdapter, gridAdapter;
    private List<TvInputs.Entry> menuEntries = new ArrayList<>();

    private Zone zone = Zone.DOCK;
    private int pillIdx, dockIdx, gridIdx, menuIdx, panelIdx;
    private String pendingPkg;        // focus to restore once apps are loaded
    private boolean resumed;
    private boolean needScroll = true;
    private int firstScreenH;
    private String lastFocusLog = "";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private AppLoader loader;
    private List<String> lastSignature;   // io thread only
    private SharedPreferences prefs;
    private Weather weather;
    private PrayerDay day;
    /** Debug builds only: a fixed "now" for the timeline and sky (adb fake_time), or -1. */
    private int fakeMinutes = -1; // debug fake_time as minutes of the day, or -1
    /** Debug builds only: a fixed local date for the thought of the day (adb fake_date), or null. */
    private LocalDate fakeDate;
    private Thoughts thoughts;
    private View hereWeather, thought;
    private ImageView awayFlag, wallpaper;
    private View wallScrim;
    private TextView weatherCity, editHint, adhanBanner;
    private Editor editor;
    private int wallGen;
    private boolean okLong;
    /** The OK release that opened the editor must not activate the first row. */
    private boolean swallowOkUp;
    private final Runnable okLongRunnable = () -> {
        okLong = true;
        openEditor();
    };
    private TextView hereTemp, awayTemp, awayRange, thoughtTag, thoughtText, thoughtSource;
    private LocalDate shownThoughtDate;
    private Config cfg;
    private View header, awayRow;
    /** The panel's rows (ROW_* ids): all nine with prayer times on, only font and text size without. */
    private int[] panelRows;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        page = findViewById(R.id.page);
        content = findViewById(R.id.content);
        sky = findViewById(R.id.sky);
        timeline = findViewById(R.id.timeline);
        glass = findViewById(R.id.glass);
        clock = findViewById(R.id.clock);
        awayClock = findViewById(R.id.away_clock);
        ssid = findViewById(R.id.ssid);
        hereWeather = findViewById(R.id.here_weather);
        hereTemp = findViewById(R.id.here_temp);
        weatherCity = findViewById(R.id.weather_city);
        wallpaper = findViewById(R.id.wallpaper);
        wallScrim = findViewById(R.id.wallpaper_scrim);
        editHint = findViewById(R.id.edit_hint);
        adhanBanner = findViewById(R.id.adhan_banner);
        awayFlag = findViewById(R.id.away_flag);
        awayTemp = findViewById(R.id.away_temp);
        awayRange = findViewById(R.id.away_range);
        thought = findViewById(R.id.thought);
        thoughtTag = findViewById(R.id.thought_tag);
        thoughtText = findViewById(R.id.thought_text);
        thoughtSource = findViewById(R.id.thought_source);
        thoughts = new Thoughts(this);
        cfg = Config.get(this);
        header = findViewById(R.id.header);
        awayRow = findViewById(R.id.away_row);
        panelRows = cfg.prayerEnabled ? new int[]{ROW_ASR, ROW_METHOD, ROW_OFFSET, ROW_OFFSET + 1, ROW_OFFSET + 2,
                ROW_OFFSET + 3, ROW_OFFSET + 4, ROW_FONT, ROW_TEXT_SIZE} : new int[]{ROW_FONT, ROW_TEXT_SIZE};
        timeline.setVisibility(cfg.prayerEnabled ? View.VISIBLE : View.GONE);
        // Second clock: hidden entirely when off; the flag is hidden when its drawable name is unknown.
        awayRow.setVisibility(cfg.secondEnabled ? View.VISIBLE : View.GONE);
        if (cfg.secondFlag != 0) awayFlag.setImageResource(cfg.secondFlag);
        else awayFlag.setVisibility(View.GONE);
        awayClock.setContentDescription(cfg.secondLabel);
        ui = getResources().getFont(R.font.alexandria);
        date = findViewById(R.id.date);
        menu = findViewById(R.id.menu);
        panel = findViewById(R.id.panel);
        pills = new ImageView[]{findViewById(R.id.btn_inputs), findViewById(R.id.btn_wifi),
                findViewById(R.id.btn_settings)};
        weather = new Weather(this);
        weatherCity.setText(HomePrefs.place(this).name);
        editHint.setVisibility(HomePrefs.hintPending(this) ? View.VISIBLE : View.GONE);

        Rect window = windowBounds();
        firstScreenH = window.height();
        findViewById(R.id.first_screen).getLayoutParams().height = firstScreenH;
        // Dock band and grid share the same 4 columns: width minus 2 x (40 margin + 16 band padding).
        int cardW = (window.width() - dp(112)) / COLS - dp(24);
        int cardH = cardW * 9 / 16;
        loader = new AppLoader(getApplicationContext(), cardW, cardH);
        // v2 slim dock: tiles at ~70% of the grid tile height, still 16:9 banners, centred in their columns.
        int dockH = Math.round(cardH * 0.7f);
        dockAdapter = new TileAdapter(dockH * 16 / 9, dockH);
        gridAdapter = new TileAdapter(-1, cardH);
        dockList = findViewById(R.id.dock);
        dockList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dockList.setItemAnimator(null);
        dockList.setNestedScrollingEnabled(false);
        dockList.setFocusable(false);
        dockList.setAdapter(dockAdapter);
        setUpList(gridList = findViewById(R.id.grid), gridAdapter);
        editor = new Editor(this);
        ((FrameLayout) findViewById(R.id.root)).addView(editor.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        AdhanPlayer.setUi(new AdhanPlayer.Ui() {
            @Override
            public void onPlaying(int prayer, boolean preview) {
                if (isDestroyed()) return;
                showAdhanBanner(prayer);
            }

            @Override
            public void onStopped() {
                if (isDestroyed()) return;
                adhanBanner.setVisibility(View.GONE);
            }
        });
        if (AdhanPlayer.playing()) showAdhanBanner(AdhanPlayer.activePrayer());
        AdhanScheduler.schedule(this);
        applyWidgets();
        showWallpaper();

        prefs = getSharedPreferences("focus", MODE_PRIVATE);
        try {
            zone = Zone.valueOf(prefs.getString("zone", Zone.DOCK.name()));
        } catch (IllegalArgumentException old) {
            zone = Zone.DOCK;
        }
        if (zone == Zone.MENU) zone = Zone.TOP;
        if (zone == Zone.PANEL) zone = Zone.TIMELINE;
        pillIdx = prefs.getInt("pill", 0);
        pendingPkg = prefs.getString("pkg", null);

        content.getViewTreeObserver().addOnGlobalLayoutListener(this::onLayoutChanged);
        page.setOnScrollChangeListener((v, x, y, ox, oy) -> sky.setDim(y / (firstScreenH * 0.6f)));

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkgFilter.addDataScheme("package");
        register(packageReceiver, pkgFilter, false);
        if (BuildConfig.DEBUG) register(debugReceiver, new IntentFilter(debugAction()), true);

        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(networkCallback, main);
    }

    private void setUpList(RecyclerView list, TileAdapter adapter) {
        list.setLayoutManager(new GridLayoutManager(this, COLS));
        list.setItemAnimator(null);
        list.setNestedScrollingEnabled(false);
        list.setFocusable(false);
        list.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(timeReceiver, timeFilter, false);
        updateClock(); // also the weather stale check
        loadApps(false);
        if (AdhanPlayer.playing()) showAdhanBanner(AdhanPlayer.activePrayer());
        AdhanScheduler.schedule(this);
    }

    private void showAdhanBanner(int prayer) {
        if (!AdhanCue.isPrayer(prayer)) return;
        adhanBanner.setText("Adzan " + AdhanCue.label(prayer) + " · OK hentikan");
        adhanBanner.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        if (AdhanPlayer.previewing()) AdhanPlayer.stop(this);
        unregisterReceiver(timeReceiver);
        main.removeCallbacks(okLongRunnable);
        if (zone == Zone.MENU) closeMenu();
        if (zone == Zone.PANEL && (editor == null || !editor.isOpen())) closePanel();
        String pkg = focusedPkg();
        prefs.edit().putString("zone", zone.name()).putInt("pill", pillIdx).putString("pkg", pkg).apply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // HOME pressed while already home: back to the first dock tile. Returning from an app keeps focus.
        // Android pauses the activity before onNewIntent, so "already home" = our window still has focus.
        boolean atHome = resumed || hasWindowFocus();
        if (BuildConfig.DEBUG) Log.d(TAG, "onNewIntent: resumed=" + resumed + " windowFocus=" + hasWindowFocus());
        if (atHome) {
            pendingPkg = null; // a still-loading app list must not restore the old focus over this
            if (dockAdapter.getItemCount() == 0 && zone != Zone.MENU && zone != Zone.PANEL) {
                zone = Zone.DOCK;
                dockIdx = 0; // applied by applyApps; with tiles present focus() below un-highlights the old one first
            }
        }
        if (atHome && dockAdapter.getItemCount() > 0) {
            if (zone == Zone.MENU) closeMenu();
            if (zone == Zone.PANEL) closePanel();
            focus(Zone.DOCK, 0, true);
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(okLongRunnable);
        if (AdhanPlayer.previewing()) AdhanPlayer.stop(this);
        AdhanPlayer.setUi(null);
        super.onDestroy();
        unregisterReceiver(packageReceiver);
        if (BuildConfig.DEBUG) unregisterReceiver(debugReceiver);
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkCallback);
        io.shutdownNow();
        weather.shutdown();
    }

    // ---------------------------------------------------------------- apps

    private void loadApps(boolean force) {
        List<String> dockOrder = HomePrefs.dockPackages(this);
        io.execute(() -> {
            Map<String, AppLoader.Candidate> found = loader.query();
            List<String> sig = new ArrayList<>();
            for (AppLoader.Candidate c : found.values()) sig.add(c.key());
            sig.addAll(dockOrder);
            if (!force && sig.equals(lastSignature)) return;
            lastSignature = sig;

            List<AppEntry> dock = new ArrayList<>();
            for (String pkg : dockOrder) {
                AppLoader.Candidate c = found.remove(pkg);
                if (c != null) dock.add(loader.entry(c));
            }
            List<AppEntry> grid = new ArrayList<>();
            for (AppLoader.Candidate c : found.values()) grid.add(loader.entry(c));
            grid.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
            main.post(() -> applyApps(dock, grid));
        });
    }

    private void applyApps(List<AppEntry> dock, List<AppEntry> grid) {
        String keep = pendingPkg != null ? pendingPkg : focusedPkg();
        pendingPkg = null;
        int i = indexOf(dock, keep);
        if (i >= 0) dockIdx = i;
        int g = indexOf(grid, keep);
        if (g >= 0) gridIdx = g;
        dockIdx = clamp(dockIdx, dock.size());
        gridIdx = clamp(gridIdx, grid.size());
        boolean editing = editor != null && editor.isOpen();
        if (!editing) {
            if (zone == Zone.GRID && grid.isEmpty()) zone = Zone.DOCK;
            if (zone == Zone.DOCK && dock.isEmpty()) zone = prayerShown() ? Zone.TIMELINE : Zone.TOP;
        }
        dockAdapter.focused = !editing && zone == Zone.DOCK ? dockIdx : -1;
        gridAdapter.focused = !editing && zone == Zone.GRID ? gridIdx : -1;
        dockAdapter.setItems(dock);
        gridAdapter.setItems(grid);
        if (editing) {
            Log.i(TAG, "apps: dock=" + dock.size() + " grid=" + grid.size());
            return;
        }
        if (zone == Zone.TOP) setLook(true);
        needScroll = true; // scroll + ring are placed by onLayoutChanged once the tiles exist
        Log.i(TAG, "apps: dock=" + dock.size() + " grid=" + grid.size());
    }

    private static int indexOf(List<AppEntry> list, String pkg) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).pkg().equals(pkg)) return i;
        return -1;
    }

    private String focusedPkg() {
        if (zone == Zone.DOCK && dockIdx < dockAdapter.items.size()) return dockAdapter.items.get(dockIdx).pkg();
        if (zone == Zone.GRID && gridIdx < gridAdapter.items.size()) return gridAdapter.items.get(gridIdx).pkg();
        return null;
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Uri data = intent.getData();
            String pkg = data == null ? null : data.getSchemeSpecificPart();
            io.execute(() -> {
                if (pkg != null) loader.forget(pkg);
            });
            loadApps(true);
        }
    };

    // ---------------------------------------------------------------- D-pad

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        if (stopAdhanKey(code, down)) return true;
        if (editor != null && editor.isOpen()) {
            if (editor.typing()) {
                if (!down && code == KeyEvent.KEYCODE_BACK) {
                    editor.stopTyping();
                    return true;
                }
                return super.dispatchKeyEvent(ev);
            }
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) editor.onDpad(code, ev.getRepeatCount());
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (!down && !ev.isCanceled()) {
                        if (swallowOkUp) swallowOkUp = false;
                        else editor.onOk();
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_INFO:
                    if (!down) editor.onBack();
                    return true;
                default:
                    return super.dispatchKeyEvent(ev);
            }
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) move(code);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down) {
                    if (ev.getRepeatCount() == 0) {
                        okLong = false;
                        main.removeCallbacks(okLongRunnable);
                        main.postDelayed(okLongRunnable, 550);
                    }
                } else {
                    main.removeCallbacks(okLongRunnable);
                    if (!ev.isCanceled() && !okLong) select();
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                if (!down) openEditor();
                return true;
            case KeyEvent.KEYCODE_BACK:
                // Not the stock Home app: Back leaves WidgeTV unless a menu or the prayer panel is open.
                if (!down && zone == Zone.MENU) {
                    closeMenu();
                    return true;
                }
                if (!down && zone == Zone.PANEL) {
                    closePanel();
                    return true;
                }
                return super.dispatchKeyEvent(ev);
            default:
                return super.dispatchKeyEvent(ev);
        }
    }

    private void move(int code) {
        int dockN = dockAdapter.getItemCount(), gridN = gridAdapter.getItemCount();
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, dn = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        switch (zone) {
            case TOP:
                if (left && pillIdx > 0) focus(Zone.TOP, pillIdx - 1, true);
                else if (right && pillIdx < pills.length - 1) focus(Zone.TOP, pillIdx + 1, true);
                else if (dn) focusDownFromTop();
                break;
            case TIMELINE:
                if (up) focus(Zone.TOP, pillIdx, true);
                else if (dn && dockN > 0) focus(Zone.DOCK, clamp(dockIdx, dockN), true);
                else if (dn && gridN > 0) focus(Zone.GRID, clamp(gridIdx, gridN), true);
                break;
            case DOCK:
                if (left && dockIdx > 0) focus(Zone.DOCK, dockIdx - 1, true);
                else if (right && dockIdx < dockN - 1) focus(Zone.DOCK, dockIdx + 1, true);
                else if (up) focusUpFromDock();
                else if (dn && gridN > 0) focus(Zone.GRID, Math.min(dockIdx, gridN - 1), true);
                break;
            case GRID: {
                int col = gridIdx % COLS, row = gridIdx / COLS;
                if (left && col > 0) focus(Zone.GRID, gridIdx - 1, true);
                else if (right && col < COLS - 1 && gridIdx + 1 < gridN) focus(Zone.GRID, gridIdx + 1, true);
                else if (up && row > 0) focus(Zone.GRID, gridIdx - COLS, true);
                else if (up && dockN > 0) focus(Zone.DOCK, clamp(dockIdx, dockN), true); // last focused dock tile
                else if (up && prayerShown()) focus(Zone.TIMELINE, 0, true);
                else if (up) focus(Zone.TOP, pillIdx, true);
                else if (dn && gridIdx + COLS < gridN) focus(Zone.GRID, gridIdx + COLS, true);
                else if (dn && (row + 1) * COLS < gridN) focus(Zone.GRID, gridN - 1, true); // short last row
                break;
            }
            case MENU:
                if (up && menuIdx > 0) focus(Zone.MENU, menuIdx - 1, true);
                else if (dn && menuIdx < menuEntries.size() - 1) focus(Zone.MENU, menuIdx + 1, true);
                break;
            case PANEL:
                if (up && panelIdx > 0) focus(Zone.PANEL, panelIdx - 1, true);
                else if (dn && panelIdx < panelRows.length - 1) focus(Zone.PANEL, panelIdx + 1, true);
                else if (left || right) changeSetting(panelRows[panelIdx], right ? 1 : -1);
                break;
        }
    }

    private void select() {
        switch (zone) {
            case TOP:
                if (pillIdx == 0) openMenu();
                else if (pillIdx == 1) start(new Intent(Settings.ACTION_WIFI_SETTINGS));
                else start(new Intent(Settings.ACTION_SETTINGS));
                break;
            case TIMELINE:
                openPanel();
                break;
            case DOCK:
                if (dockIdx < dockAdapter.items.size()) start(dockAdapter.items.get(dockIdx).launchIntent());
                break;
            case GRID:
                if (gridIdx < gridAdapter.items.size()) start(gridAdapter.items.get(gridIdx).launchIntent());
                break;
            case MENU:
                Intent intent = menuEntries.get(menuIdx).intent;
                closeMenu();
                start(intent);
                break;
            case PANEL:
                closePanel();
                break;
        }
    }

    private void start(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "cannot start " + intent, e);
        }
    }

    // ---------------------------------------------------------------- focus rendering

    private void focus(Zone z, int idx, boolean animate) {
        setLook(false);
        zone = z;
        switch (z) {
            case TOP: pillIdx = idx; break;
            case DOCK: dockIdx = idx; break;
            case GRID: gridIdx = idx; break;
            case MENU: menuIdx = idx; break;
            case PANEL: panelIdx = idx; break;
            default: break;
        }
        dockAdapter.focused = z == Zone.DOCK ? dockIdx : -1;
        gridAdapter.focused = z == Zone.GRID ? gridIdx : -1;
        setLook(true);
        if (z == Zone.DOCK && dockList.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) dockList.getLayoutManager()).scrollToPositionWithOffset(dockIdx, dp(24));
        }
        scrollToZone(animate);
        updateGlass(animate);
    }

    private View focusedView() {
        switch (zone) {
            case TOP: return pills[pillIdx];
            case TIMELINE: return cfg.prayerEnabled ? timeline : header;
            case DOCK: return dockList.getLayoutManager().findViewByPosition(dockIdx);
            case GRID: return gridList.getLayoutManager().findViewByPosition(gridIdx);
            case MENU: return menu.getChildAt(menuIdx);
            default: return panel.getChildAt(panelIdx + 1); // child 0 is the title
        }
    }

    private void setLook(boolean on) {
        View v = focusedView();
        if (v == null) return;
        switch (zone) {
            case DOCK:
            case GRID:
                TileAdapter.setLook(v, on);
                break;
            case TOP: {
                ImageView b = (ImageView) v;
                b.setBackgroundResource(on ? R.drawable.bg_btn_focus : R.drawable.bg_btn);
                b.setImageTintList(ColorStateList.valueOf(on ? Lapis.LAPIS_900 : Lapis.PLASTER_100));
                ssid.setVisibility(on && pillIdx == 1 ? View.VISIBLE : View.GONE); // SSID only while Wi-Fi is focused
                break;
            }
            case MENU:
            case PANEL:
                rowLook(v, on);
                break;
            default:
                break;
        }
    }

    /** Dropdown / panel row: gold fill with lapis text when focused. */
    private static void rowLook(View row, boolean on) {
        row.setBackgroundResource(on ? R.drawable.bg_row_focus : 0);
        if (row instanceof TextView) {
            ((TextView) row).setTextColor(on ? Lapis.LAPIS_900 : Lapis.PLASTER_100);
        } else if (row instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) row;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = (TextView) g.getChildAt(i);
                t.setTextColor(on ? Lapis.LAPIS_900 : (i == 0 ? Lapis.PLASTER_100 : Lapis.GOLD_400));
            }
        }
    }

    /** Re-place the ring after any layout pass (data loaded, SSID text width changed, menu shown). */
    private void onLayoutChanged() {
        if (needScroll && focusedView() != null) {
            needScroll = false;
            if (zone == Zone.TOP) setLook(true);
            scrollToZone(false);
        }
        updateGlass(true);
    }

    private void updateGlass(boolean animate) {
        View v = focusedView();
        if (v == null || v.getWidth() == 0) return;
        RectF r;
        float radius;
        if (zone == Zone.DOCK || zone == Zone.GRID) {
            r = rectInContent(v.findViewById(R.id.card));
            float s = TileAdapter.FOCUS_SCALE - 1f;
            r.inset(-(r.width() * s / 2 + dp(3)), -(r.height() * s / 2 + dp(3)));
            radius = dp(12) * TileAdapter.FOCUS_SCALE + dp(3);
        } else if (zone == Zone.TIMELINE) {
            r = rectInContent(v);
            r.inset(-dp(14), -dp(8));
            radius = dp(16);
        } else {
            r = rectInContent(v);
            radius = r.height() / 2;
        }
        boolean ring = zone == Zone.TIMELINE || zone == Zone.DOCK || zone == Zone.GRID;
        String focusLog = "focus: zone=" + zone + " ring=" + ring + " rect=" + r.toShortString();
        if (BuildConfig.DEBUG && !focusLog.equals(lastFocusLog)) Log.d(TAG, lastFocusLog = focusLog);
        glass.setRingVisible(ring);
        glass.moveTo(r, radius, animate);
    }

    /** Dock/timeline/top bar keep the first screen in view; grid rows sit 80dp below the top edge. */
    private void scrollToZone(boolean smooth) {
        int y = 0;
        if (zone == Zone.GRID) {
            View v = focusedView();
            if (v == null) return;
            y = Math.max(0, (int) rectInContent(v).top - dp(80));
        }
        if (smooth) page.smoothScrollTo(0, y);
        else page.scrollTo(0, y);
    }

    private RectF rectInContent(View v) {
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        content.offsetDescendantRectToMyCoords(v, r);
        return new RectF(r);
    }

    // ---------------------------------------------------------------- Inputs dropdown

    private void openMenu() {
        menuEntries = TvInputs.load(this);
        if (menuEntries.isEmpty()) return;
        menu.removeAllViews();
        for (TvInputs.Entry e : menuEntries) {
            TextView row = rowText(e.label, 16, Lapis.PLASTER_100);
            row.setPadding(dp(16), 0, dp(16), 0);
            menu.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        }
        // Right-aligned under the button row (the buttons sit at the screen's right edge).
        RectF pill = rectInContent(pills[0]);
        RectF last = rectInContent(pills[pills.length - 1]);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menu.getLayoutParams();
        lp.leftMargin = (int) last.right - lp.width;
        lp.topMargin = (int) pill.bottom + dp(12);
        menu.setLayoutParams(lp);
        menu.setAlpha(0f);
        menu.setVisibility(View.VISIBLE);
        menu.animate().alpha(1f).setDuration(160).start();
        focus(Zone.MENU, 0, true);
    }

    private void closeMenu() {
        menu.setVisibility(View.GONE);
        menu.removeAllViews();
        zone = Zone.TOP; // the rows are gone; focus() below has nothing to un-highlight
        focus(Zone.TOP, 0, true);
    }

    private TextView rowText(CharSequence text, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(ui);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        return t;
    }

    // ---------------------------------------------------------------- Prayer settings panel

    /** Row ids: Asr school, method, the five offsets (Fajr, Dhuhr, Asr, Maghrib, Isha), clock font, text size. */
    private static final int ROW_ASR = 0, ROW_METHOD = 1, ROW_OFFSET = 2, ROW_FONT = 7, ROW_TEXT_SIZE = 8;

    private void openPanel() {
        panel.removeAllViews();
        LinearLayout title = new LinearLayout(this);
        String city = HomePrefs.place(this).name;
        String titleText = cfg.prayerEnabled
                ? getString(R.string.prayer_settings) + (city.isEmpty() ? "" : "  ·  " + city)
                : getString(R.string.display_settings);
        title.addView(rowText(titleText, 15, Lapis.GOLD_400),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        title.addView(rowText("◀ ▶ change   ·   OK close", 11, Lapis.MIST_400),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        title.setPadding(dp(14), 0, dp(14), 0);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        for (int i = 0; i < panelRows.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(dp(14), 0, dp(14), 0);
            row.addView(rowText("", 14, Lapis.PLASTER_100), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            row.addView(rowText("", 14, Lapis.GOLD_400),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            panel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        }
        renderPanel();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        int panelH = dp(30 + 28 * panelRows.length + 16);
        if (cfg.prayerEnabled) {
            RectF tl = rectInContent(timeline);
            lp.leftMargin = (int) (tl.centerX() - dp(210));
            // Just under the timeline's prayer times (they stay visible), never past the first screen's bottom.
            lp.topMargin = (int) Math.min(tl.top + dp(52), firstScreenH - dp(8) - panelH);
        } else {
            RectF hd = rectInContent(header); // under the clock header it was opened from
            lp.leftMargin = (int) hd.left;
            lp.topMargin = (int) Math.min(hd.bottom + dp(16), firstScreenH - dp(8) - panelH);
        }
        panel.setLayoutParams(lp);
        panel.setAlpha(0f);
        panel.setVisibility(View.VISIBLE);
        panel.animate().alpha(1f).setDuration(160).start();
        focus(Zone.PANEL, 0, true);
    }

    private void closePanel() {
        panel.setVisibility(View.GONE);
        panel.removeAllViews();
        zone = Zone.TIMELINE;
        focus(Zone.TIMELINE, 0, true);
    }

    private void renderPanel() {
        if (panel.getChildCount() < panelRows.length + 1) return;
        for (int k = 0; k < panelRows.length; k++) {
            ViewGroup row = (ViewGroup) panel.getChildAt(k + 1);
            int i = panelRows[k];
            String label, value;
            if (i == ROW_ASR) {
                label = "Asr school";
                value = PrayerPrefs.hanafi(this) ? "Hanafi" : "Shafi'i";
            } else if (i == ROW_METHOD) {
                label = "Method";
                value = PrayerPrefs.METHOD_NAMES[PrayerPrefs.methodIndex(this)];
            } else if (i == ROW_FONT) {
                label = "Clock font";
                value = PrayerPrefs.CLOCK_FONTS[PrayerPrefs.clockFont(this)];
            } else if (i == ROW_TEXT_SIZE) {
                label = "Text size";
                value = PrayerPrefs.TEXT_SIZES[PrayerPrefs.textSize(this)];
            } else {
                int prayer = PrayerPrefs.OFFSET_PRAYERS[i - ROW_OFFSET];
                int off = PrayerPrefs.offset(this, prayer);
                label = PrayerDay.NAMES[prayer] + " offset";
                value = (off > 0 ? "+" : off < 0 ? "−" : "") + Math.abs(off) + " min   ·   "
                        + clockText(day.times[prayer]);
            }
            ((TextView) row.getChildAt(0)).setText(label);
            ((TextView) row.getChildAt(1)).setText("‹  " + value + "  ›");
            rowLook(row, zone == Zone.PANEL && k == panelIdx);
        }
    }

    private void changeSetting(int row, int dir) {
        if (row == ROW_ASR) {
            PrayerPrefs.setHanafi(this, !PrayerPrefs.hanafi(this));
        } else if (row == ROW_METHOD) {
            int n = PrayerPrefs.METHODS.length;
            PrayerPrefs.setMethodIndex(this, (PrayerPrefs.methodIndex(this) + dir + n) % n);
        } else if (row == ROW_FONT) {
            PrayerPrefs.setClockFont(this, 1 - PrayerPrefs.clockFont(this));
            applyClockFont();
        } else if (row == ROW_TEXT_SIZE) {
            PrayerPrefs.setTextSize(this, (PrayerPrefs.textSize(this) + dir + 3) % 3);
            applyTextSize();
        } else {
            int prayer = PrayerPrefs.OFFSET_PRAYERS[row - ROW_OFFSET];
            PrayerPrefs.setOffset(this, prayer, PrayerPrefs.offset(this, prayer) + dir);
        }
        refreshPrayer();
        renderPanel();
        AdhanScheduler.schedule(this);
        Log.i(TAG, "prayer settings: hanafi=" + PrayerPrefs.hanafi(this) + " method="
                + PrayerPrefs.METHODS[PrayerPrefs.methodIndex(this)] + " offsets=" + Arrays.toString(PrayerPrefs.offsets(this))
                + " clockFont=" + PrayerPrefs.CLOCK_FONTS[PrayerPrefs.clockFont(this)]
                + " textSize=" + PrayerPrefs.TEXT_SIZES[PrayerPrefs.textSize(this)] + " times=" + timesLog());
    }

    // ---------------------------------------------------------------- clock, date, prayer, weather

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    /**
     * "Now" for everything on screen. Debug builds can fake it (adb fake_time / fake_date) for a day
     * simulation; the TV's real clock and zone are never touched. Release builds always use the real clock.
     */
    private ZoneId zone() {
        return HomePrefs.zone(this);
    }

    private long nowMillis() {
        if (fakeMinutes < 0 && fakeDate == null) return System.currentTimeMillis();
        ZoneId zone = zone();
        java.time.LocalDate d = fakeDate != null ? fakeDate : java.time.LocalDate.now(zone);
        java.time.LocalTime t = fakeMinutes >= 0 ? java.time.LocalTime.of(fakeMinutes / 60, fakeMinutes % 60)
                : java.time.LocalTime.now(zone);
        return ZonedDateTime.of(d, t, zone).toInstant().toEpochMilli();
    }

    private ZonedDateTime nowZoned() {
        return java.time.Instant.ofEpochMilli(nowMillis()).atZone(zone());
    }

    /** Clocks + prayer day + date line + thought, without the weather stale check. */
    private void renderClocks() {
        clock.setText(hm(nowZoned(), DateFormat.is24HourFormat(this)));
        refreshPrayer();
    }

    private void updateClock() {
        boolean h24 = DateFormat.is24HourFormat(this);
        ZonedDateTime local = nowZoned();
        clock.setText(hm(local, h24));
        refreshPrayer(); // also redraws the date line and the second-clock line
        maybeAdhan();
        weather.maybeFetch(false, this::renderDateAndWeather);
    }

    /** Backup for the alarm while this screen is open. The claim stops a second play in the same minute. */
    private void maybeAdhan() {
        if (!resumed || day == null || !cfg.prayerEnabled || !HomePrefs.adhanOn(this)) return;
        if (fakeMinutes >= 0 || fakeDate != null) return;
        long now = System.currentTimeMillis();
        int prayer = AdhanCue.due(now, day.times, AdhanCue.GRACE_MS);
        if (!AdhanCue.isPrayer(prayer) || !HomePrefs.adhanPrayer(this, prayer)) return;
        long when = day.times[prayer];
        if (!HomePrefs.claimAdhan(this, AdhanCue.stamp(prayer, when))) return;
        AdhanScheduler.play(this, prayer, when);
        AdhanScheduler.schedule(this);
    }

    /** OK or Back stops the adhan. Inside the editor, OK still chooses a row and Back stops first. */
    private boolean stopAdhanKey(int code, boolean down) {
        if (!AdhanPlayer.playing()) return false;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK;
        if (!ok && !back) return false;
        if (editor != null && editor.isOpen() && ok) return false;
        if (down) {
            main.removeCallbacks(okLongRunnable);
            okLong = true;
            return true;
        }
        AdhanPlayer.stop(this);
        return true;
    }

    /** "12:30" (or "00:30" in 24-hour mode), no AM/PM. */
    private static String hm(ZonedDateTime z, boolean h24) {
        int h = z.getHour();
        return h24 ? String.format(Locale.US, "%02d:%02d", h, z.getMinute())
                : String.format(Locale.US, "%d:%02d", h % 12 == 0 ? 12 : h % 12, z.getMinute());
    }

    /** "3:00 am" style, for the second-clock line, the panel and the logs. */
    private static String hmAmPm(ZonedDateTime z, boolean h24) {
        return h24 ? hm(z, true) : hm(z, false) + (z.getHour() < 12 ? " am" : " pm");
    }

    private String clockText(long millis) {
        return hmAmPm(java.time.Instant.ofEpochMilli(millis).atZone(zone()), DateFormat.is24HourFormat(this));
    }

    /**
     * Header clock font from Prayer settings: Cormorant Garamond Light (wght 300) or Outfit Thin (wght 200).
     * The Kitchener line uses the same family a little heavier (500 / 300) so it stays readable at ~32px.
     */
    /**
     * "Text size" (Normal / Large / Larger) for the secondary text, in sp (= px at 1080p / 2). The main clock and
     * the dock never change. Spans that embed icons are rebuilt afterwards because their size follows the text.
     */
    private void applyTextSize() {
        int l = PrayerPrefs.textSize(this);
        float[] dateT = {14, 16, 18}, awayT = {22, 26, 30}, awayR = {10, 13, 15};
        // Weather and the clock are sized again in applyWidgetChrome.
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, dateT[l]);
        awayClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, awayT[l]);
        awayTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, awayT[l]);
        awayRange.setTextSize(TypedValue.COMPLEX_UNIT_SP, awayR[l]);
        float flag = awayT[l] / 22f; // the 22x15px flag grows with the second-clock time
        ViewGroup.LayoutParams fl = awayFlag.getLayoutParams();
        fl.width = dp(11 * flag);
        fl.height = dp(7.5f * flag);
        awayFlag.setLayoutParams(fl);
        applyWidgetChrome();
        if (day != null) renderDateAndWeather();
    }

    /** Per-widget size from the on-TV editor. Clock was previously fixed; the others override the global text step. */
    private void applyWidgetChrome() {
        int clockS = HomePrefs.size(this, HomePrefs.CLOCK);
        float[] clockSp = {58f, 75f, 104f};
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSp[clockS]);
        int weatherS = HomePrefs.size(this, HomePrefs.WEATHER);
        float[] weatherSp = {18f, 24f, 34f};
        hereTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, weatherSp[weatherS]);
        weatherCity.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(13f, weatherSp[weatherS] - 8f));
        int prayerS = HomePrefs.size(this, HomePrefs.PRAYER);
        float[] prayerTimes = {13f, 15f, 20f};
        float[] prayerNames = {10f, 11f, 15f};
        int timelineDp = Math.max(64, timeline.setTextScale(prayerTimes[prayerS], prayerNames[prayerS]));
        timeline.getLayoutParams().height = dp(timelineDp);
        timeline.requestLayout();
        int quoteS = HomePrefs.size(this, HomePrefs.QUOTE);
        float[] tag = {10f, 10f, 12f};
        float[] text = {16f, 19f, 26f};
        float[] src = {10f, 10f, 13f};
        thoughtTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, tag[quoteS]);
        thoughtText.setTextSize(TypedValue.COMPLEX_UNIT_SP, text[quoteS]);
        thoughtSource.setTextSize(TypedValue.COMPLEX_UNIT_SP, src[quoteS]);
    }

    /** Reorder and show or hide the four widget rows. The dock stays pinned to the bottom. */
    private void applyWidgets() {
        LinearLayout rows = findViewById(R.id.rows);
        rows.removeAllViews();
        for (String id : HomePrefs.rowOrder(this)) {
            View v = widgetView(id);
            if (v == null) continue;
            boolean on = HomePrefs.visible(this, id);
            if (HomePrefs.PRAYER.equals(id) && !cfg.prayerEnabled) on = false;
            if (HomePrefs.QUOTE.equals(id) && !cfg.thoughtsEnabled) on = false;
            if (HomePrefs.WEATHER.equals(id) && !cfg.weatherEnabled) on = false;
            v.setVisibility(on ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (HomePrefs.PRAYER.equals(id)) lp.topMargin = dp(14);
            if (HomePrefs.WEATHER.equals(id)) lp.topMargin = dp(2);
            if (HomePrefs.QUOTE.equals(id)) {
                lp.topMargin = dp(16);
                lp.leftMargin = dp(80);
                lp.rightMargin = dp(80);
            }
            rows.addView(v, lp);
        }
        if (!prayerShown() && zone == Zone.TIMELINE) zone = Zone.TOP;
        applyClockFont();
        applyTextSize();
    }

    private View widgetView(String id) {
        if (HomePrefs.CLOCK.equals(id)) return header;
        if (HomePrefs.WEATHER.equals(id)) return hereWeather;
        if (HomePrefs.PRAYER.equals(id)) return timeline;
        if (HomePrefs.QUOTE.equals(id)) return thought;
        return null;
    }

    private boolean prayerShown() {
        return cfg.prayerEnabled && HomePrefs.visible(this, HomePrefs.PRAYER);
    }

    private void focusDownFromTop() {
        if (prayerShown()) focus(Zone.TIMELINE, 0, true);
        else if (dockAdapter.getItemCount() > 0) focus(Zone.DOCK, clamp(dockIdx, dockAdapter.getItemCount()), true);
        else if (gridAdapter.getItemCount() > 0) focus(Zone.GRID, clamp(gridIdx, gridAdapter.getItemCount()), true);
    }

    private void focusUpFromDock() {
        if (prayerShown()) focus(Zone.TIMELINE, 0, true);
        else focus(Zone.TOP, pillIdx, true);
    }

    private void applyClockFont() {
        boolean outfit = PrayerPrefs.clockFont(this) == 1;
        Typeface tf = getResources().getFont(outfit ? R.font.outfit : R.font.cormorant_garamond);
        clock.setTypeface(tf);
        clock.setFontVariationSettings(outfit ? "'wght' 200" : "'wght' 300");
        awayClock.setTypeface(tf);
        awayClock.setFontVariationSettings(outfit ? "'wght' 300" : "'wght' 500");
        // Temperatures in the clock family: light beside the main clock, as heavy as the second-clock time beside it.
        hereTemp.setTypeface(tf);
        hereTemp.setFontVariationSettings(outfit ? "'wght' 200" : "'wght' 300");
        awayTemp.setTypeface(tf);
        awayTemp.setFontVariationSettings(outfit ? "'wght' 300" : "'wght' 500");
        // Cormorant's default figures are old-style (x-height); clocks want lining figures.
        for (TextView t : new TextView[]{clock, awayClock, hereTemp, awayTemp}) t.setFontFeatureSettings("lnum");
    }

    /**
     * Recompute today's sun/prayer times (settings or minute changed) and push them to the timeline and the
     * sky. With prayer_enabled=false the same astronomy only drives the sky (dawn, sunrise, noon, afternoon,
     * sunset, night) and the timeline stays hidden.
     */
    private void refreshPrayer() {
        long now = nowMillis();
        day = cfg.prayerEnabled ? PrayerPrefs.day(this, now) : PrayerPrefs.skyDay(this, now);
        if (cfg.prayerEnabled) timeline.setDay(day);
        sky.update(day);
        renderDateAndWeather();
        renderThought();
        if (BuildConfig.DEBUG && cfg.prayerEnabled) timeline.post(this::logExpectedDot);
    }

    private void renderDateAndWeather() {
        if (day == null) return;
        Weather.City[] w = weather.current();
        boolean h24 = DateFormat.is24HourFormat(this);
        ZonedDateTime local = nowZoned();
        ZonedDateTime away = local.withZoneSameInstant(cfg.secondZone);
        String weekday = away.toLocalDate().equals(local.toLocalDate()) ? ""
                : away.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK) + " ";
        awayClock.setText(weekday + hmAmPm(away, h24));

        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, local.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())), Lapis.PLASTER_100);
        if (cfg.prayerEnabled) {
            append(sb, "   ·   ", Lapis.MIST_400);
            append(sb, day.hijri(), Lapis.GOLD_400);
        }
        boolean showWeather = cfg.weatherEnabled && HomePrefs.visible(this, HomePrefs.WEATHER);
        weatherCity.setText(HomePrefs.place(this).name);
        if (w != null && w[0].hasRange && showWeather) {
            append(sb, "   ·   ", Lapis.MIST_400);
            sb.append(rangeText(w[0], Lapis.MIST_400));
        }
        date.setText(sb);

        // Away weather stays beside the second clock. The home-city row is shown or hidden by applyWidgets.
        boolean awayW = w != null && w.length > 1;
        awayTemp.setVisibility(awayW ? View.VISIBLE : View.GONE);
        awayRange.setVisibility(awayW && w[1].hasRange ? View.VISIBLE : View.GONE);
        if (awayW) {
            awayTemp.setText(tempText(w[1], Lapis.MIST_400, awayTemp.getTextSize()));
            awayRange.setText(rangeText(w[1], Lapis.alpha(Lapis.MIST_400, 0.7f)));
        }
        if (!showWeather) return;
        hereTemp.setText(w == null ? "…" : tempText(w[0], Lapis.PLASTER_100, hereTemp.getTextSize()));
    }

    /** "[icon] 32°" */
    private CharSequence tempText(Weather.City c, int color, float textPx) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendIcon(sb, Weather.icon(c), color, textPx * 0.8f);
        sb.append(" ").append(String.valueOf(c.temp)).append("°");
        return sb;
    }

    /** "↑34° ↓22°" in mist at ~70%. */
    private static CharSequence rangeText(Weather.City c, int color) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, "↑" + c.max + "°  ↓" + c.min + "°", color);
        // Alexandria's arrows are tiny; draw them with the system sans so they read at 3 m.
        for (int i = 0; i < sb.length(); i++) {
            char ch = sb.charAt(i);
            if (ch == '↑' || ch == '↓') {
                sb.setSpan(new TypefaceSpan(Typeface.DEFAULT), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    /** Thought of the day; the pick only changes when the date (in the TV's time zone) changes. */
    private void renderThought() {
        if (!cfg.thoughtsEnabled || !HomePrefs.visible(this, HomePrefs.QUOTE)) {
            thought.setVisibility(View.GONE);
            return;
        }
        LocalDate today = fakeDate != null ? fakeDate : nowZoned().toLocalDate();
        if (today.equals(shownThoughtDate)) return;
        Thoughts.Thought t = thoughts.pick(today);

        shownThoughtDate = today;
        thought.setVisibility(t == null ? View.GONE : View.VISIBLE);
        if (t == null) return;
        thoughtTag.setText(t.friday ? "Jumu'ah Mubarak" : "Thought for today");
        thoughtText.setText("“" + t.text + "”");
        thoughtSource.setText(t.source);
        thoughtSource.setVisibility(t.source == null ? View.GONE : View.VISIBLE);
        Log.i(TAG, "thought: date=" + today + " friday=" + t.friday + " source=" + t.source);
    }

    private static void append(SpannableStringBuilder sb, CharSequence text, int color) {
        int s = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** A weather icon at ~0.9× the text size, centred on the line. */
    private void appendIcon(SpannableStringBuilder sb, int res, int color, float textPx) {
        Drawable d = getDrawable(res).mutate();
        d.setTint(color);
        int size = Math.round(textPx * 0.9f * 1.15f); // the 24-unit icon has ~2 units of padding per side
        d.setBounds(0, 0, size, size);
        int s = sb.length();
        sb.append("￼");
        // ALIGN_CENTER is API 29; Android 9 sits the icon on the line's bottom instead.
        int align = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? DynamicDrawableSpan.ALIGN_CENTER : DynamicDrawableSpan.ALIGN_BOTTOM;
        sb.setSpan(new ImageSpan(d, align), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String timesLog() {
        StringBuilder sb = new StringBuilder();
        for (int p : PrayerTimeline.COLUMNS) sb.append(PrayerDay.NAMES[p]).append('=').append(clockText(day.times[p])).append(' ');
        return sb.toString().trim();
    }

    /**
     * Debug receipt: the dot x the timeline should draw, computed here from the times and the view width
     * (independently of PrayerTimeline.onDraw, which logs the x it actually drew).
     */
    private void logExpectedDot() {
        int w = timeline.getWidth();
        if (w == 0 || day == null) return;
        float col = w / 5f;
        long now = day.now;
        long[] t = new long[5];
        for (int i = 0; i < 5; i++) t[i] = day.times[PrayerTimeline.COLUMNS[i]];
        String seg;
        float frac, x;
        if (now < t[0]) {
            frac = PrayerTimeline.fraction(now, day.yesterdayIsha, t[0]);
            x = col * 0.5f * frac;
            seg = "Isha(yesterday)->Fajr";
        } else if (now >= t[4]) {
            frac = PrayerTimeline.fraction(now, t[4], day.tomorrowFajr);
            x = col * 4.5f + col * 0.5f * frac;
            seg = "Isha->Fajr(tomorrow)";
        } else {
            int k = 0;
            while (k < 3 && now >= t[k + 1]) k++;
            frac = (now - t[k]) / (float) (t[k + 1] - t[k]);
            x = col * (k + 0.5f) + col * frac;
            seg = PrayerDay.NAMES[PrayerTimeline.COLUMNS[k]] + "->" + PrayerDay.NAMES[PrayerTimeline.COLUMNS[k + 1]];
        }
        Log.i(TAG, String.format(Locale.US, "timeline expected: now=%s%s segment=%s fraction=%.4f expectedX=%.1f times=%s",
                clockText(now), fakeMinutes >= 0 || fakeDate != null ? " (fake)" : "", seg, frac, x, timesLog()));
    }

    /** Debug builds only: adb preview hooks for the sky, the timeline clock and the weather. */
    private final BroadcastReceiver debugReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String period = intent.getStringExtra("period");
            if (period != null) {
                sky.forcedPeriod = Arrays.asList(SkyView.PERIODS).indexOf(period); // "auto" → -1
                if (sky.forcedPeriod < 0) sky.forcedPattern = -1;
            }
            if (intent.hasExtra("pattern")) sky.forcedPattern = intent.getIntExtra("pattern", -1);
            String fd = intent.getStringExtra("fake_date");
            if (fd != null) {
                try {
                    fakeDate = "auto".equals(fd) ? null : LocalDate.parse(fd);
                } catch (RuntimeException bad) {
                    fakeDate = null;
                }
                shownThoughtDate = null;
            }
            String fake = intent.getStringExtra("fake_time");
            if (fake != null) {
                fakeMinutes = -1;
                String[] hm = fake.split(":");
                if (hm.length == 2) fakeMinutes = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
            }
            Log.i(TAG, "debug: period=" + sky.forcedPeriod + " pattern=" + sky.forcedPattern + " fakeMinutes=" + fakeMinutes
                    + " fakeDate=" + fakeDate + " now=" + nowZoned());
            if (intent.getBooleanExtra("weather_refresh", false)) weather.maybeFetch(true, MainActivity.this::renderDateAndWeather);
            // Everything time-based (both clocks, weekday prefix, date line, sky, timeline, thought) reads nowMillis().
            renderClocks();
        }
    };

    // ---------------------------------------------------------------- network pill

    // Android 12+ only hands the SSID to callbacks that ask for location info; older versions read it from WifiManager.
    private final ConnectivityManager.NetworkCallback networkCallback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? new NetworkWatcher(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
            : new NetworkWatcher();

    private final class NetworkWatcher extends ConnectivityManager.NetworkCallback {
        NetworkWatcher() {
            super();
        }

        @RequiresApi(Build.VERSION_CODES.S)
        NetworkWatcher(int flags) {
            super(flags);
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            ssid.setText(networkLabel(caps));
        }

        @Override
        public void onLost(Network network) {
            ssid.setText(R.string.offline);
        }
    }

    /**
     * SSID needs ACCESS_FINE_LOCATION (granted over adb), and on Android 9-10 Location turned on too;
     * otherwise plain "Wi-Fi".
     */
    @SuppressWarnings("deprecation") // WifiManager.getConnectionInfo: the only SSID source before Android 12
    private String networkLabel(NetworkCapabilities caps) {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return getString(R.string.ethernet);
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return getString(R.string.wifi);
        WifiInfo info;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info = caps.getTransportInfo() instanceof WifiInfo ? (WifiInfo) caps.getTransportInfo() : null;
        } else {
            WifiManager wifi = getApplicationContext().getSystemService(WifiManager.class);
            info = wifi == null ? null : wifi.getConnectionInfo();
        }
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || ssid.equals(WifiManager.UNKNOWN_SSID)) return getString(R.string.wifi);
        ssid = ssid.replaceAll("^\"|\"$", "");
        return ssid.isEmpty() ? getString(R.string.wifi) : ssid;
    }

    // ---------------------------------------------------------------- on-TV editor

    private void openEditor() {
        if (editor.isOpen()) return;
        if (zone == Zone.MENU) closeMenu();
        if (zone == Zone.PANEL) closePanel();
        HomePrefs.markHintSeen(this);
        editHint.setVisibility(View.GONE);
        glass.setVisibility(View.INVISIBLE);
        swallowOkUp = true;
        editor.open();
    }

    void editorClosed() {
        swallowOkUp = false;
        if (!prayerShown() && zone == Zone.TIMELINE) zone = Zone.TOP;
        glass.setVisibility(View.VISIBLE);
        setLook(true);
        needScroll = true;
        updateGlass(false);
    }

    void editorReloadDock() {
        loadApps(true);
    }

    void editorReloadPlace() {
        Place p = HomePrefs.place(this);
        weather.relocate(p.lat, p.lng);
        weatherCity.setText(p.name);
        weather.maybeFetch(true, this::renderDateAndWeather);
        updateClock();
        AdhanScheduler.schedule(this);
    }

    void editorReloadWallpaper() {
        showWallpaper();
    }

    void editorReloadLayout() {
        applyWidgets();
    }

    void editorOpenPrayer() {
        editor.close();
        openPanel();
    }

    void rescheduleAdhan() {
        AdhanScheduler.schedule(this);
    }

    void previewAdhan(int prayer) {
        AdhanPlayer.play(this, prayer, true, null);
    }

    void editorPickAdhan(boolean fajr) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, fajr ? REQ_ADHAN_FAJR : REQ_ADHAN_OTHER);
        } catch (ActivityNotFoundException e) {
            editor.flash("Files tidak ada di TV ini");
        }
    }

    void editorPickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_WALL);
        } catch (ActivityNotFoundException e) {
            editor.flash("Files tidak ada di TV ini. Tempel URL gambar.");
        }
    }

    void downloadWallpaper(String url) {
        runIo(() -> {
            String err = WallpaperStore.download(getApplicationContext(), url);
            runMain(() -> {
                if (editor == null) return;
                if (err != null) {
                    editor.flash(err);
                    return;
                }
                HomePrefs.setWallpaperUrl(this, url.trim());
                showWallpaper();
                editor.flash("Wallpaper dari URL tersimpan");
            });
        });
    }

    List<AppEntry> editorApps() {
        ArrayList<AppEntry> all = new ArrayList<>();
        all.addAll(dockAdapter.items);
        all.addAll(gridAdapter.items);
        all.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return all;
    }

    String editorAppLabel(String pkg) {
        for (AppEntry e : dockAdapter.items) if (e.pkg().equals(pkg)) return e.label;
        for (AppEntry e : gridAdapter.items) if (e.pkg().equals(pkg)) return e.label;
        return null;
    }

    void runIo(Runnable r) {
        io.execute(r);
    }

    void runMain(Runnable r) {
        main.post(() -> {
            if (!isDestroyed()) r.run();
        });
    }

    private void showWallpaper() {
        String mode = HomePrefs.wallpaperMode(this);
        int gen = ++wallGen;
        if ("sky".equals(mode)) {
            recycleWallpaper();
            wallpaper.setImageDrawable(null);
            wallpaper.setVisibility(View.GONE);
            wallScrim.setVisibility(View.GONE);
            sky.setVisibility(View.VISIBLE);
            return;
        }
        io.execute(() -> {
            Bitmap bmp = WallpaperStore.decode(getApplicationContext());
            main.post(() -> {
                if (isDestroyed() || gen != wallGen) {
                    if (bmp != null) bmp.recycle();
                    return;
                }
                if (bmp == null) {
                    wallpaper.setVisibility(View.GONE);
                    wallScrim.setVisibility(View.GONE);
                    sky.setVisibility(View.VISIBLE);
                    if (editor != null && editor.isOpen()) editor.flash("Gambar wallpaper tidak ada. Langit dipakai lagi.");
                    return;
                }
                recycleWallpaper();
                wallpaper.setImageBitmap(bmp);
                wallpaper.setVisibility(View.VISIBLE);
                wallScrim.setVisibility(View.VISIBLE);
                sky.setVisibility(View.GONE);
            });
        });
    }

    private void recycleWallpaper() {
        Drawable prev = wallpaper.getDrawable();
        wallpaper.setImageDrawable(null);
        if (prev instanceof BitmapDrawable) {
            Bitmap old = ((BitmapDrawable) prev).getBitmap();
            if (old != null && !old.isRecycled()) old.recycle();
        }
    }

    private void savePickedWallpaper(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The picker still grants a temporary read for this result.
        }
        if (editor != null) editor.flash("Menyimpan gambar…");
        io.execute(() -> {
            String err = WallpaperStore.saveUri(getApplicationContext(), uri);
            main.post(() -> {
                if (editor == null) return;
                if (err != null) {
                    editor.flash(err);
                    return;
                }
                HomePrefs.setWallpaperFile(this);
                showWallpaper();
                editor.flash("Wallpaper tersimpan di TV");
            });
        });
    }

    private void savePickedAdhan(Uri uri, boolean fajr) {
        if (editor != null) editor.flash("Menyimpan rekaman…");
        io.execute(() -> {
            String err = AdhanFiles.save(getApplicationContext(), uri, fajr);
            main.post(() -> {
                if (isDestroyed()) return;
                if (err != null) {
                    if (editor != null) editor.flash(err);
                    return;
                }
                HomePrefs.setAdhanFile(this, fajr, true);
                if (editor != null) {
                    editor.flash(fajr ? "Rekaman Subuh tersimpan" : "Rekaman salat lain tersimpan");
                    editor.refresh();
                }
            });
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ADHAN_FAJR || requestCode == REQ_ADHAN_OTHER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                savePickedAdhan(data.getData(), requestCode == REQ_ADHAN_FAJR);
            } else if (editor != null) editor.flash("Tidak ada rekaman dipilih");
            return;
        }
        if (requestCode != REQ_WALL) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) savePickedWallpaper(data.getData());
        else if (editor != null) editor.flash("Tidak ada gambar dipilih");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ && editor != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            editor.onReadPermission(granted);
        }
    }

    // ---------------------------------------------------------------- utils

    /** RECEIVER_EXPORTED / RECEIVER_NOT_EXPORTED exist from Android 13; older versions take no flag. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // the flagless call only runs below Android 13
    private void register(BroadcastReceiver receiver, IntentFilter filter, boolean exported) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, exported ? RECEIVER_EXPORTED : RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    /** The window's size: WindowMetrics from Android 11, the display's real size before (home is full screen). */
    @SuppressWarnings("deprecation") // Display.getRealSize: the pre-Android 11 equivalent
    private Rect windowBounds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return getWindowManager().getCurrentWindowMetrics().getBounds();
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        return new Rect(0, 0, size.x, size.y);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int i, int n) {
        return Math.max(0, Math.min(i, n - 1));
    }
}
