package tv.widgetv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native focus navigation stays available on D-pad-only TV boxes. */
public class HomeEditorActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private LinearLayout body;
    private boolean busy;
    private String page = "home";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Map<String, AppLoader.Candidate> apps;
    private SharedPreferences prefs;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = HomePrefs.of(this);
        apps = new AppLoader(this, 160, 90).query();
        showHome();
        String pkg = getIntent().getStringExtra("package");
        if (state == null && pkg != null && apps.containsKey(pkg))
            body.post(() -> appActions(pkg));
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void screen(String title, String hint) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff0c1b33);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(48), dp(24), dp(48), dp(24));
        scroll.addView(body);
        setContentView(scroll);
        TextView h = new TextView(this); h.setText(title); h.setTextSize(26); h.setTextColor(0xffedc56b);
        body.addView(h);
        TextView sub = new TextView(this); sub.setText(hint); sub.setTextSize(15); sub.setTextColor(0xffcfdbed);
        sub.setPadding(0, dp(8), 0, dp(14)); body.addView(sub);
        status = sub;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(17); b.setAllCaps(false); b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18), dp(10), dp(18), dp(10)); b.setMinHeight(dp(48));
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable(); focused.setColor(0xffedc56b); focused.setCornerRadius(dp(8));
        GradientDrawable normal = new GradientDrawable(); normal.setColor(0xff1b3456); normal.setCornerRadius(dp(8));
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{android.R.attr.state_pressed}, focused);
        bg.addState(new int[]{}, normal);
        b.setBackground(bg);
        b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused},{android.R.attr.state_pressed},{}},
                new int[]{0xff0c1b33,0xff0c1b33,Color.WHITE}));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(8);
        body.addView(b, lp);
        b.setOnClickListener(v -> { if (!busy) action.run(); });
        return b;
    }
    private void firstFocus() { if (body.getChildCount() > 2) body.getChildAt(2).requestFocus(); }
    private void showHome() {
        page = "home";
        screen("Atur tampilan WidgeTV", "Gunakan panah dan OK. Perubahan tersimpan otomatis; Kembali untuk melihat hasilnya.");
        button("Aplikasi favorit — tambah, kurangi, urutkan", this::showDock);
        button("Wallpaper — File / USB / internet / Google Drive", this::showWallpaper);
        button("Lokasi — " + prefs.getString("city", Config.get(this).cityLabel), this::showLocation);
        button("Widget — tampilkan / sembunyikan dan ukuran teks", this::showWidgets);
        button("Pengaturan Android", () -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException e) { message("Pengaturan Android tidak tersedia."); }
        });
        button("Selesai — kembali ke layar utama", this::finish);
        firstFocus();
    }
    private String label(String pkg) {
        AppLoader.Candidate c = apps.get(pkg);
        return c == null ? pkg + " (belum terpasang)" : String.valueOf(c.label);
    }
    private void showDock() { showDock(null); }
    private void showDock(String focusPkg) {
        page = "dock";
        screen("Aplikasi favorit", "Pintasan berjajar mendatar. Di layar utama gunakan kiri / kanan untuk menggulir. Menghapus pintasan tidak menghapus aplikasinya.");
        button("+ Tambah aplikasi", this::chooseApp);
        List<String> favorites = HomePrefs.dock(this);
        Button target = null;
        for (int i = 0; i < favorites.size(); i++) {
            String pkg = favorites.get(i);
            Button b = button((i + 1) + ". " + label(pkg), () -> appActions(pkg));
            if (pkg.equals(focusPkg)) target = b;
        }
        if (favorites.isEmpty()) {
            TextView empty = new TextView(this); empty.setText("Belum ada pintasan. Semua aplikasi tetap tersedia di daftar aplikasi.");
            empty.setTextColor(Color.WHITE); body.addView(empty);
        }
        button("Kembali", this::showHome);
        if (target != null) target.requestFocus(); else firstFocus();
    }
    private void appActions(String pkg) {
        boolean favorite = HomePrefs.dock(this).contains(pkg);
        String[] options = favorite ? new String[]{"Geser ke kiri", "Geser ke kanan", "Hapus dari favorit"} : new String[]{"Tambahkan ke favorit"};
        new AlertDialog.Builder(this).setTitle(label(pkg)).setItems(options, (d, which) -> {
            List<String> list = HomePrefs.dock(this);
            if (!favorite) list = DockOrder.add(list, pkg);
            else if (which == 2) list = DockOrder.remove(list, pkg);
            else list = DockOrder.move(list, pkg, which == 0 ? -1 : 1);
            HomePrefs.setDock(this, list); showDock(pkg);
        }).setNegativeButton("Batal", null).show();
    }
    private void chooseApp() {
        apps = new AppLoader(this, 160, 90).query();
        List<String> candidates = new ArrayList<>(apps.keySet());
        candidates.removeAll(HomePrefs.dock(this));
        candidates.sort((a,b) -> label(a).compareToIgnoreCase(label(b)));
        if (candidates.isEmpty()) { message("Semua aplikasi yang terdeteksi sudah menjadi favorit."); return; }
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), 0, dp(20), 0);
        EditText search = new EditText(this); search.setHint("Cari aplikasi terpasang"); search.setSingleLine(true); box.addView(search);
        ListView list = new ListView(this); box.addView(list, new LinearLayout.LayoutParams(-1, dp(240)));
        List<String> filtered = new ArrayList<>();
        Runnable refresh = () -> {
            String query = search.getText().toString().toLowerCase(java.util.Locale.ROOT);
            filtered.clear(); List<String> labels = new ArrayList<>();
            for (String p : candidates) if (label(p).toLowerCase(java.util.Locale.ROOT).contains(query)) { filtered.add(p); labels.add(label(p)); }
            list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        };
        refresh.run();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { refresh.run(); }
            public void afterTextChanged(Editable e) {}
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Tambah aplikasi").setView(box).setNegativeButton("Batal", null).create();
        list.setOnItemClickListener((p,v,pos,id) -> {
            String pkg = filtered.get(pos);
            HomePrefs.setDock(this, DockOrder.add(HomePrefs.dock(this), pkg));
            dialog.dismiss(); showDock(pkg);
        });
        dialog.setOnShowListener(d -> list.requestFocus()); dialog.show();
    }
    private void showWallpaper() {
        page = "wallpaper";
        screen("Wallpaper", "Gunakan JPG, PNG, atau WebP, maksimal 20 MB. Gambar disalin ke aplikasi agar tetap tersedia setelah USB dicabut.");
        button("Pilih gambar — Downloads / penyimpanan / USB", this::pickImage);
        button("Unduh dari tautan gambar HTTPS", this::askUrl);
        button("Ambil dari Google Drive", () -> new AlertDialog.Builder(this)
                .setTitle("Gambar dari Google Drive")
                .setMessage("Jika Google Drive tersedia dalam pemilih file Android, pilih Drive dan gambar Anda. Jika Drive tidak muncul di TV, unduh gambarnya dahulu lewat komputer atau browser, lalu pilih dari USB / Downloads. Tautan berbagi Drive bukan tautan gambar langsung.")
                .setPositiveButton("Buka pemilih file", (d,w) -> pickImage()).setNegativeButton("Kembali", null).show());
        button("Kegelapan gambar: " + prefs.getInt("dim", 35) + "%", () -> {
            String[] labels = {"0% — terang", "20%", "35% — disarankan", "50%", "65% — gelap"};
            int[] values = {0,20,35,50,65};
            new AlertDialog.Builder(this).setTitle("Kegelapan wallpaper").setItems(labels, (d,i) -> {
                HomePrefs.save(this, prefs.edit().putInt("dim", values[i])); showWallpaper();
            }).show();
        });
        button("Gunakan kembali langit dan motif bawaan", () -> {
            HomePrefs.save(this, prefs.edit().putBoolean("custom_wallpaper", false)); showWallpaper();
        });
        button("Kembali", this::showHome); firstFocus();
    }
    private void pickImage() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick, PICK_IMAGE); }
        catch (ActivityNotFoundException e) {
            try { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_IMAGE); }
            catch (ActivityNotFoundException noPicker) { message("TV belum memiliki pemilih file gambar. Gunakan tautan HTTPS atau pasang file manager yang mendukung pemilihan gambar."); }
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        work(() -> { try (InputStream in = getContentResolver().openInputStream(uri)) { WallpaperStore.importImage(this, in); } });
    }
    private void askUrl() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("https://contoh.com/gambar.jpg");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Tautan langsung ke gambar")
                .setMessage("Tempel URL gambar HTTPS, bukan halaman hasil pencarian atau halaman berbagi Drive. Untuk situs yang perlu login, unduh dahulu lalu gunakan pemilih file.")
                .setView(input).setPositiveButton("Unduh", (d,w) -> {
                    String url = input.getText().toString(); work(() -> WallpaperStore.download(this, url));
                }).setNegativeButton("Batal", null).show();
    }
    private interface Task { void run() throws Exception; }
    private void work(Task task) {
        if (busy) return;
        busy = true; status.setText("Memuat dan menyimpan gambar…");
        worker.execute(() -> {
            String error = null;
            try { task.run(); }
            catch (Exception e) { error = e.getMessage() == null ? "Gambar tidak dapat dibuka." : e.getMessage(); }
            final String result = error;
            runOnUiThread(() -> {
                busy = false;
                if (isFinishing() || isDestroyed()) return;
                if (result == null) {
                    HomePrefs.save(this, prefs.edit().putBoolean("custom_wallpaper", true));
                    showWallpaper(); message("Wallpaper disimpan. Tekan Kembali untuk melihat hasilnya.");
                } else { showWallpaper(); message(result); }
            });
        });
    }
    private void showLocation() {
        page = "location";
        screen("Lokasi", "Aktif: " + prefs.getString("city", Config.get(this).cityLabel)
                + ". Koordinat dan zona waktu mengikuti kota. Metode, mazhab, dan koreksi menit salat tetap; periksa lagi setelah berganti kota.");
        button("Pilih kota", () -> {
            String[] names = new String[CityLocation.CITIES.length];
            for (int i=0;i<names.length;i++) names[i] = CityLocation.CITIES[i].name + " · " + CityLocation.CITIES[i].zone;
            new AlertDialog.Builder(this).setTitle("Pilih lokasi").setItems(names, (d,i) -> {
                HomePrefs.setCity(this, CityLocation.CITIES[i]); showLocation();
            }).setNegativeButton("Batal", null).show();
        });
        button("Lokasi lain — nama, koordinat, zona waktu", this::manualCity);
        button("Kembali", this::showHome); firstFocus();
    }
    private void manualCity() {
        Config cfg = Config.get(this);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),0,dp(20),0);
        EditText name = field(form, "Nama kota", prefs.getString("city",cfg.cityLabel));
        EditText lat = field(form, "Lintang (selatan bernilai negatif)", prefs.getString("lat",Double.toString(cfg.lat)));
        EditText lng = field(form, "Bujur (timur bernilai positif)", prefs.getString("lng",Double.toString(cfg.lng)));
        EditText zone = field(form, "Zona waktu, contoh Asia/Jakarta", prefs.getString("zone",cfg.primaryZone.getId()));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Lokasi manual").setView(scroll)
                .setPositiveButton("Simpan", null).setNegativeButton("Batal", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                CityLocation city = new CityLocation(name.getText().toString(), Double.parseDouble(lat.getText().toString().trim()),
                        Double.parseDouble(lng.getText().toString().trim()), zone.getText().toString().trim());
                HomePrefs.setCity(this,city); dialog.dismiss(); showLocation();
            } catch (NumberFormatException e) { message("Koordinat harus berupa angka; gunakan titik untuk desimal."); }
            catch (IllegalArgumentException e) { message(e.getMessage()); }
        })); dialog.show();
    }
    private EditText field(LinearLayout form,String label,String value) {
        TextView t = new TextView(this); t.setText(label); t.setTextSize(14); form.addView(t);
        EditText e = new EditText(this); e.setText(value); e.setSingleLine(true); form.addView(e); return e;
    }
    private void showWidgets() {
        page = "widgets";
        screen("Widget", "Jadwal salat: pilih jam / timeline di layar utama untuk metode, mazhab, dan koreksi menit.");
        toggle("prayer_enabled", "Jadwal salat", getResources().getBoolean(R.bool.prayer_enabled));
        toggle("weather_enabled", "Cuaca", getResources().getBoolean(R.bool.weather_enabled));
        toggle("thoughts_enabled", "Kutipan harian", getResources().getBoolean(R.bool.thoughts_enabled));
        button("Ukuran tulisan", () -> new AlertDialog.Builder(this).setTitle("Ukuran tulisan")
                .setItems(new String[]{"Normal", "Besar", "Lebih besar"}, (d,i) -> {
                    PrayerPrefs.setTextSize(this,i); HomePrefs.save(this,prefs.edit()); showWidgets();
                }).show());
        button("Kembali", this::showHome); firstFocus();
    }
    private void toggle(String key, String label, boolean initial) {
        boolean value = prefs.getBoolean(key, initial);
        button(label + ": " + (value ? "Tampil" : "Tersembunyi"), () -> {
            HomePrefs.save(this, prefs.edit().putBoolean(key, !value)); showWidgets();
        });
    }
    private void message(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() {
        if (busy) { message("Tunggu gambar selesai dimuat."); return; }
        if (!"home".equals(page)) showHome(); else super.onBackPressed();
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
