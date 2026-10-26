package tv.widgetv.home;

import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen remote editor. Every change is written to {@link HomePrefs} immediately.
 * Long-press OK (or the Menu key) opens it. Back steps out; the home screen stays underneath.
 */
final class Editor {
    private enum Screen { HOME, DOCK, ADD, WALL, GALLERY, URL, PLACE, CUSTOM, ROWS }

    private static final class Item {
        final String label;
        final String value;
        final String tag;

        Item(String label, String value) {
            this(label, value, null);
        }

        Item(String label, String value, String tag) {
            this.label = label;
            this.value = value == null ? "" : value;
            this.tag = tag;
        }
    }

    private final MainActivity activity;
    private final FrameLayout root;
    private final TextView title;
    private final TextView status;
    private final TextView footer;
    private final EditText urlField;
    private final ScrollView scroll;
    private final LinearLayout rows;
    private final Typeface ui;

    private final ArrayDeque<Screen> stack = new ArrayDeque<>();
    private final List<WallpaperStore.Hit> gallery = new ArrayList<>();
    private List<Item> items = new ArrayList<>();
    private int index;
    private int pendingRemove = -1;
    private boolean typing;
    private boolean rowSizeMode = true;
    private boolean open;
    private double customLat = Places.PADANG.lat;
    private double customLng = Places.PADANG.lng;
    private int customZone;

    Editor(MainActivity activity) {
        this.activity = activity;
        ui = activity.getResources().getFont(R.font.alexandria);
        root = new FrameLayout(activity);
        root.setVisibility(View.GONE);
        root.setBackgroundColor(0xE60C1B33);
        root.setFocusable(false);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_menu);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        int maxW = activity.getResources().getDisplayMetrics().widthPixels - dp(48);
        int cardW = Math.min(dp(640), maxW);

        title = label("", 18, Lapis.GOLD_400);
        status = label("", 13, Lapis.MIST_400);
        status.setMaxLines(3);
        status.setSingleLine(false);
        footer = label("", 12, Lapis.MIST_400);
        footer.setMaxLines(3);
        footer.setSingleLine(false);

        urlField = new EditText(activity);
        urlField.setSingleLine(true);
        urlField.setHint("https://…");
        urlField.setTextColor(Lapis.PLASTER_100);
        urlField.setHintTextColor(Lapis.MIST_400);
        urlField.setTypeface(ui);
        urlField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        urlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        urlField.setBackgroundColor(0x660C1B33);
        urlField.setPadding(dp(12), dp(8), dp(12), dp(8));
        urlField.setVisibility(View.GONE);
        urlField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                stopTyping();
                return true;
            }
            return false;
        });

        rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setFocusable(false);
        scroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int listH = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.58f);

        card.addView(title, matchWrap());
        card.addView(status, matchWrap());
        LinearLayout.LayoutParams urlLp = matchWrap();
        urlLp.topMargin = dp(8);
        card.addView(urlField, urlLp);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listH);
        scrollLp.topMargin = dp(8);
        card.addView(scroll, scrollLp);
        LinearLayout.LayoutParams footLp = matchWrap();
        footLp.topMargin = dp(8);
        card.addView(footer, footLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        root.addView(card, cardLp);
    }

    View view() {
        return root;
    }

    boolean isOpen() {
        return open;
    }

    boolean typing() {
        return open && typing;
    }

    void open() {
        stack.clear();
        stack.push(Screen.HOME);
        index = 0;
        pendingRemove = -1;
        rowSizeMode = true;
        open = true;
        root.setVisibility(View.VISIBLE);
        flash("Tersimpan otomatis di TV ini");
        show();
    }

    void close() {
        stopTyping();
        open = false;
        stack.clear();
        root.setVisibility(View.GONE);
        activity.editorClosed();
    }

    void flash(String message) {
        status.setText(message == null ? "" : message);
    }

    void onDpad(int code, int repeat) {
        if (items.isEmpty()) return;
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            int next = index + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            if (next < 0 || next >= items.size()) return;
            index = next;
            if (pendingRemove >= 0) {
                pendingRemove = -1;
                show();
            } else {
                paint();
                scrollToIndex();
            }
            return;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            nudge(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, repeat);
        }
    }

    void onOk() {
        if (items.isEmpty()) return;
        Screen screen = stack.peek();
        Item item = items.get(index);
        switch (screen) {
            case HOME:
                onHome(index);
                break;
            case DOCK:
                onDockOk();
                break;
            case ADD:
                onAddOk(item);
                break;
            case WALL:
                onWallOk(index);
                break;
            case GALLERY:
                onGalleryOk(item);
                break;
            case URL:
                if (index == 0) saveUrl();
                else onBack();
                break;
            case PLACE:
                onPlaceOk(item);
                break;
            case CUSTOM:
                onCustomOk();
                break;
            case ROWS:
                onRowsOk();
                break;
            default:
                break;
        }
    }

    /** @return true when the event is consumed */
    boolean onBack() {
        if (typing) {
            stopTyping();
            return true;
        }
        if (stack.size() <= 1) {
            close();
            return true;
        }
        stack.pop();
        index = 0;
        pendingRemove = -1;
        show();
        return true;
    }

    void stopTyping() {
        typing = false;
        urlField.clearFocus();
    }

    private void onHome(int i) {
        switch (i) {
            case 0:
                push(Screen.DOCK);
                break;
            case 1:
                push(Screen.WALL);
                break;
            case 2:
                push(Screen.PLACE);
                break;
            case 3:
                push(Screen.ROWS);
                break;
            case 4:
                activity.editorOpenPrayer();
                break;
            default:
                close();
                break;
        }
    }

    private void onDockOk() {
        List<String> dock = HomePrefs.dockPackages(activity);
        if (index == dock.size()) {
            push(Screen.ADD);
            return;
        }
        if (index == dock.size() + 1) {
            onBack();
            return;
        }
        if (pendingRemove == index) {
            int next = Math.max(0, index - 1);
            HomePrefs.setDock(activity, DockOrder.without(dock, index));
            pendingRemove = -1;
            index = Math.min(next, Math.max(0, HomePrefs.dockPackages(activity).size()));
            activity.editorReloadDock();
            flash("Dihapus dari dock");
            show();
            return;
        }
        pendingRemove = index;
        flash("OK lagi untuk hapus · Back batal");
        show();
    }

    private void onAddOk(Item item) {
        if (item.tag == null) {
            if (index == items.size() - 1) onBack();
            return;
        }
        List<String> now = HomePrefs.dockPackages(activity);
        if (now.size() >= DockOrder.MAX) {
            flash("Dock penuh (maksimum 8)");
            return;
        }
        List<String> next = DockOrder.plus(now, item.tag);
        if (next.size() == now.size()) {
            flash("Sudah ada di dock");
            return;
        }
        HomePrefs.setDock(activity, next);
        activity.editorReloadDock();
        flash("Ditambahkan");
        onBack();
    }

    private void onWallOk(int i) {
        switch (i) {
            case 0:
                HomePrefs.setWallpaperSky(activity);
                activity.editorReloadWallpaper();
                flash("Langit bawaan");
                show();
                break;
            case 1:
                activity.editorPickImage();
                break;
            case 2:
                push(Screen.GALLERY);
                loadGallery();
                break;
            case 3:
                push(Screen.URL);
                typing = true;
                urlField.requestFocus();
                break;
            default:
                onBack();
                break;
        }
    }

    private void onGalleryOk(Item item) {
        if ("perm".equals(item.tag)) {
            activity.requestPermissions(new String[]{WallpaperStore.readPermission()}, MainActivity.REQ_READ);
            flash("Izinkan baca gambar, lalu kembali ke daftar ini");
            return;
        }
        if ("files".equals(item.tag)) {
            activity.editorPickImage();
            return;
        }
        if ("back".equals(item.tag) || item.tag == null) {
            onBack();
            return;
        }
        int n;
        try {
            n = Integer.parseInt(item.tag);
        } catch (NumberFormatException bad) {
            return;
        }
        if (n < 0 || n >= gallery.size()) return;
        WallpaperStore.Hit hit = gallery.get(n);
        flash("Menyimpan " + hit.name + "…");
        activity.runIo(() -> {
            String err = WallpaperStore.saveUri(activity.getApplicationContext(), hit.uri);
            activity.runMain(() -> {
                if (!open) return;
                if (err != null) {
                    flash(err);
                    return;
                }
                HomePrefs.setWallpaperFile(activity);
                activity.editorReloadWallpaper();
                flash("Wallpaper tersimpan di TV");
                if (!stack.isEmpty() && stack.peek() == Screen.GALLERY) onBack();
            });
        });
    }

    private void onPlaceOk(Item item) {
        if ("custom".equals(item.tag)) {
            Place current = HomePrefs.place(activity);
            customLat = current.lat;
            customLng = current.lng;
            customZone = zoneIndex(current.zone);
            push(Screen.CUSTOM);
            return;
        }
        if ("back".equals(item.tag)) {
            onBack();
            return;
        }
        HomePrefs.setPlaceId(activity, item.tag);
        activity.editorReloadPlace();
        flash(HomePrefs.place(activity).name + " · salat & cuaca");
        show();
    }

    private void onCustomOk() {
        if (index == 3) {
            HomePrefs.setCustomPlace(activity, customLat, customLng, Places.ZONES[customZone]);
            activity.editorReloadPlace();
            flash("Lokasi sendiri tersimpan");
            onBack();
            return;
        }
        if (index == 4) onBack();
    }

    private void onRowsOk() {
        if (index == 0) {
            rowSizeMode = !rowSizeMode;
            show();
            return;
        }
        if (index == items.size() - 1) {
            onBack();
            return;
        }
        if (!rowSizeMode) return;
        String id = items.get(index).tag;
        boolean on = !HomePrefs.visible(activity, id);
        HomePrefs.setVisible(activity, id, on);
        activity.editorReloadLayout();
        flash(widgetName(id) + (on ? " tampil" : " disembunyikan"));
        show();
    }

    private void nudge(int dir, int repeat) {
        Screen screen = stack.peek();
        if (screen == Screen.DOCK) {
            List<String> dock = HomePrefs.dockPackages(activity);
            if (index >= dock.size()) return;
            pendingRemove = -1;
            List<String> next = DockOrder.move(dock, index, dir);
            if (next.equals(dock)) return;
            HomePrefs.setDock(activity, next);
            index = index + dir;
            activity.editorReloadDock();
            show();
            return;
        }
        if (screen == Screen.CUSTOM && index <= 2) {
            double step = repeat >= 8 ? 0.1 : 0.01;
            if (index == 0) customLat = clamp(customLat + dir * step, -90, 90);
            else if (index == 1) customLng = clamp(customLng + dir * step, -180, 180);
            else customZone = (customZone + dir + Places.ZONES.length) % Places.ZONES.length;
            show();
            return;
        }
        if (screen == Screen.ROWS && index > 0 && index < items.size() - 1) {
            String id = items.get(index).tag;
            if (rowSizeMode) {
                HomePrefs.setSize(activity, id, HomePrefs.size(activity, id) + dir);
                activity.editorReloadLayout();
                show();
            } else {
                List<String> order = HomePrefs.rowOrder(activity);
                int at = order.indexOf(id);
                List<String> next = RowOrder.move(order, at, dir);
                if (next.equals(order)) return;
                HomePrefs.setRowOrder(activity, next);
                activity.editorReloadLayout();
                index = 1 + next.indexOf(id);
                show();
            }
        }
    }

    private void saveUrl() {
        String text = urlField.getText() == null ? "" : urlField.getText().toString().trim();
        stopTyping();
        if (WallpaperUrls.direct(text) == null) {
            flash("URL tidak valid. Contoh: https://…/gambar.jpg");
            return;
        }
        flash("Mengunduh…");
        activity.downloadWallpaper(text);
    }

    private void loadGallery() {
        gallery.clear();
        if (!WallpaperStore.canReadImages(activity)) {
            show();
            flash("Butuh izin untuk melihat gambar di USB");
            return;
        }
        flash("Membaca penyimpanan…");
        activity.runIo(() -> {
            List<WallpaperStore.Hit> hits = WallpaperStore.scan(activity);
            activity.runMain(() -> {
                if (!open || stack.isEmpty() || stack.peek() != Screen.GALLERY) return;
                gallery.clear();
                gallery.addAll(hits);
                show();
                flash(hits.isEmpty() ? "Tidak ada gambar. Coba Files atau URL." : hits.size() + " gambar");
            });
        });
    }

    void onReadPermission(boolean granted) {
        if (!open || stack.isEmpty() || stack.peek() != Screen.GALLERY) {
            flash(granted ? "Izin diberikan" : "Izin ditolak. Gunakan Files atau URL.");
            return;
        }
        if (!granted) {
            flash("Izin ditolak. Gunakan Files atau URL.");
            show();
            return;
        }
        loadGallery();
    }

    private void push(Screen screen) {
        stack.push(screen);
        index = 0;
        pendingRemove = -1;
        if (screen != Screen.URL) stopTyping();
        show();
    }

    private void show() {
        Screen screen = stack.peek();
        title.setText(titleFor(screen));
        urlField.setVisibility(screen == Screen.URL ? View.VISIBLE : View.GONE);
        items = build(screen);
        if (index >= items.size()) index = Math.max(0, items.size() - 1);
        rows.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setFocusable(false);
            TextView left = label(item.label, 15, Lapis.PLASTER_100);
            TextView right = label(item.value, 14, Lapis.GOLD_400);
            row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            row.addView(right, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        }
        footer.setText(help(screen));
        paint();
        scrollToIndex();
    }

    private List<Item> build(Screen screen) {
        ArrayList<Item> out = new ArrayList<>();
        switch (screen) {
            case HOME:
                int n = installedDockCount();
                out.add(new Item("Dock aplikasi", n + " terpasang"));
                out.add(new Item("Wallpaper", wallLabel()));
                out.add(new Item("Lokasi", HomePrefs.place(activity).name));
                out.add(new Item("Tata letak baris", ""));
                out.add(new Item("Waktu salat & koreksi", ""));
                out.add(new Item("Tutup", ""));
                break;
            case DOCK:
                List<String> dock = HomePrefs.dockPackages(activity);
                for (int i = 0; i < dock.size(); i++) {
                    String pkg = dock.get(i);
                    String name = activity.editorAppLabel(pkg);
                    String label = name != null ? name : pkg;
                    if (name == null) label = label + " · tidak terpasang";
                    String value = pendingRemove == i ? "Hapus?" : "‹ ›";
                    out.add(new Item((i + 1) + "  " + label, value, pkg));
                }
                out.add(new Item("Tambah aplikasi", ""));
                out.add(new Item("Kembali", ""));
                break;
            case ADD:
                List<String> have = HomePrefs.dockPackages(activity);
                for (AppEntry e : activity.editorApps()) {
                    if (have.contains(e.pkg())) continue;
                    out.add(new Item(e.label, "", e.pkg()));
                }
                if (out.isEmpty()) out.add(new Item("Tidak ada aplikasi lain", ""));
                out.add(new Item("Kembali", ""));
                break;
            case WALL:
                String mode = HomePrefs.wallpaperMode(activity);
                out.add(new Item("Langit bawaan", "sky".equals(mode) ? "●" : ""));
                out.add(new Item("Pilih dari Files atau USB", ""));
                out.add(new Item("Gambar di penyimpanan", ""));
                out.add(new Item("Tempel URL gambar", "url".equals(mode) ? "●" : ""));
                out.add(new Item("Kembali", ""));
                break;
            case GALLERY:
                if (!WallpaperStore.canReadImages(activity)) {
                    out.add(new Item("Izinkan baca gambar", "", "perm"));
                    out.add(new Item("Pilih dari Files atau USB", "", "files"));
                } else if (gallery.isEmpty()) {
                    out.add(new Item("Tidak ada gambar di penyimpanan", ""));
                    out.add(new Item("Pilih dari Files atau USB", "", "files"));
                } else {
                    for (int i = 0; i < gallery.size(); i++) {
                        out.add(new Item(gallery.get(i).name, "", Integer.toString(i)));
                    }
                }
                out.add(new Item("Kembali", "", "back"));
                break;
            case URL:
                out.add(new Item("Simpan URL", ""));
                out.add(new Item("Batal", ""));
                break;
            case PLACE:
                Place current = HomePrefs.place(activity);
                for (Place p : Places.CITIES) {
                    out.add(new Item(p.name, p.id.equals(current.id) ? "●" : "", p.id));
                }
                out.add(new Item("Koordinat sendiri", "custom".equals(current.id) ? "●" : "", "custom"));
                out.add(new Item("Kembali", "", "back"));
                break;
            case CUSTOM:
                out.add(new Item("Lintang", String.format(Locale.US, "%.4f", customLat)));
                out.add(new Item("Bujur", String.format(Locale.US, "%.4f", customLng)));
                out.add(new Item("Zona waktu", Places.ZONES[customZone]));
                out.add(new Item("Simpan", ""));
                out.add(new Item("Batal", ""));
                break;
            case ROWS:
                out.add(new Item(rowSizeMode ? "Mode: Ukuran" : "Mode: Urutan", "OK ganti"));
                for (String id : HomePrefs.rowOrder(activity)) {
                    String state = HomePrefs.visible(activity, id) ? "Tampil" : "Sembunyi";
                    out.add(new Item(widgetName(id), state + " · " + sizeName(HomePrefs.size(activity, id)), id));
                }
                out.add(new Item("Kembali", ""));
                break;
            default:
                break;
        }
        return out;
    }

    private void paint() {
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            boolean on = i == index;
            row.setBackgroundResource(on ? R.drawable.bg_row_focus : 0);
            if (row instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) row;
                for (int c = 0; c < g.getChildCount(); c++) {
                    if (g.getChildAt(c) instanceof TextView) {
                        ((TextView) g.getChildAt(c)).setTextColor(on ? Lapis.LAPIS_900
                                : (c == 0 ? Lapis.PLASTER_100 : Lapis.GOLD_400));
                    }
                }
            }
        }
    }

    private void scrollToIndex() {
        rows.post(() -> {
            if (index < 0 || index >= rows.getChildCount()) return;
            View child = rows.getChildAt(index);
            int y = Math.max(0, child.getTop() - dp(36));
            scroll.smoothScrollTo(0, y);
        });
    }

    private int installedDockCount() {
        int n = 0;
        for (String pkg : HomePrefs.dockPackages(activity)) {
            if (activity.editorAppLabel(pkg) != null) n++;
        }
        return n;
    }

    private String wallLabel() {
        switch (HomePrefs.wallpaperMode(activity)) {
            case "file":
                return "Gambar";
            case "url":
                return "URL";
            default:
                return "Langit";
        }
    }

    private static String widgetName(String id) {
        if (HomePrefs.CLOCK.equals(id)) return "Jam";
        if (HomePrefs.WEATHER.equals(id)) return "Cuaca";
        if (HomePrefs.PRAYER.equals(id)) return "Waktu salat";
        if (HomePrefs.QUOTE.equals(id)) return "Kutipan";
        return id;
    }

    private static String sizeName(int size) {
        if (size <= 0) return "Kecil";
        if (size == 1) return "Biasa";
        return "Besar";
    }

    private static int zoneIndex(String zone) {
        for (int i = 0; i < Places.ZONES.length; i++) if (Places.ZONES[i].equals(zone)) return i;
        return 0;
    }

    private static String titleFor(Screen screen) {
        switch (screen) {
            case DOCK:
                return "Dock aplikasi";
            case ADD:
                return "Tambah aplikasi";
            case WALL:
                return "Wallpaper";
            case GALLERY:
                return "Gambar di TV";
            case URL:
                return "URL gambar";
            case PLACE:
                return "Lokasi";
            case CUSTOM:
                return "Koordinat sendiri";
            case ROWS:
                return "Tata letak";
            default:
                return "Sunting WidgeTV";
        }
    }

    private String help(Screen screen) {
        switch (screen) {
            case DOCK:
                return "Kiri/kanan geser · OK tandai hapus · OK lagi hapus. Netflix & YouTube tetap bawaan sampai dock diubah.";
            case ADD:
                return "OK menambah aplikasi yang sudah terpasang. Maksimum 8.";
            case WALL:
                return "Files/USB atau URL. Login Google Drive menyusul: unduh gambarnya, atau tempel tautan file publik.";
            case GALLERY:
                return "Gambar yang sudah ada di USB atau penyimpanan TV.";
            case URL:
                return "Back selesai mengetik, lalu OK Simpan. drive.google.com/file/d/… dicoba bila file publik.";
            case PLACE:
                return "Kota ini dipakai untuk waktu salat dan cuaca. Jam mengikuti zona kota. Zona sistem TV tidak diubah.";
            case CUSTOM:
                return "Kiri/kanan ubah 0,01° (tahan untuk 0,1°). OK pada Simpan.";
            case ROWS:
                return rowSizeMode
                        ? "OK tampil/sembunyi · kiri/kanan ukuran. OK pada Mode untuk mengubah urutan."
                        : "Kiri naik · kanan turun. Dock tetap di bawah.";
            default:
                return "Atas/bawah pilih · OK buka · Back tutup. Tahan OK dari layar utama untuk membuka lagi.";
        }
    }

    private TextView label(String text, float sp, int color) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(ui);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
