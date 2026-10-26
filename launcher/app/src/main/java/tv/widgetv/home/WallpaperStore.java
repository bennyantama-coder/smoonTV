package tv.widgetv.home;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Copies a picked or downloaded image into internal storage so it survives reboot and USB removal. */
final class WallpaperStore {
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final int MAX_EDGE = 1920;

    static final class Hit {
        final Uri uri;
        final String name;

        Hit(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }

    private WallpaperStore() {
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), "wallpaper.jpg");
    }

    static boolean canReadImages(Context c) {
        return c.checkSelfPermission(readPermission()) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    static String readPermission() {
        if (Build.VERSION.SDK_INT >= 33) return android.Manifest.permission.READ_MEDIA_IMAGES;
        return android.Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    static List<Hit> scan(Context c) {
        ArrayList<Hit> out = new ArrayList<>();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME};
        try (Cursor cursor = c.getContentResolver().query(collection, proj, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) return out;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            while (cursor.moveToNext() && out.size() < 40) {
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                out.add(new Hit(ContentUris.withAppendedId(collection, id), name == null || name.isEmpty() ? "Gambar" : name));
            }
        } catch (RuntimeException ignored) {
            return out;
        }
        return out;
    }

    /** Null on success, otherwise a short message for the TV. */
    static String saveUri(Context c, Uri uri) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return "Gambar tidak bisa dibuka";
            return saveStream(c, in);
        } catch (SecurityException denied) {
            return "Izin baca gambar ditolak";
        } catch (Exception ex) {
            return "Gambar tidak bisa dibaca";
        }
    }

    /** Null on success. Accepts a direct image URL or a public Drive file link. */
    static String download(Context c, String rawUrl) {
        String url = WallpaperUrls.direct(rawUrl);
        if (url == null) return "URL tidak valid. Gunakan tautan http(s) gambar.";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "WidgeTV/1.1");
            int code = conn.getResponseCode();
            if (code != 200) return "Unduh gagal (HTTP " + code + ")";
            int len = conn.getContentLength();
            if (len > MAX_BYTES) return "Gambar terlalu besar (maks. 12 MB)";
            String type = conn.getContentType();
            if (type != null && (type.startsWith("text/") || type.contains("html"))) {
                return driveHint();
            }
            try (InputStream in = conn.getInputStream()) {
                return saveStream(c, in);
            }
        } catch (Exception ex) {
            return "Unduh gagal. Periksa internet atau pakai Files.";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static Bitmap decode(Context c) {
        File f = file(c);
        if (!f.isFile() || f.length() == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample(bounds.outWidth, bounds.outHeight);
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (OutOfMemoryError oom) {
            return null;
        }
    }

    private static String saveStream(Context c, InputStream in) {
        try {
            byte[] data = readLimited(in);
            if (data == null) return "Gambar terlalu besar (maks. 12 MB)";
            if (!looksLikeImage(data)) return driveHint();
            return writeJpeg(c, data);
        } catch (OutOfMemoryError oom) {
            return "Gambar terlalu besar untuk TV ini";
        } catch (Exception ex) {
            return "Gambar tidak bisa dibaca";
        }
    }

    private static String writeJpeg(Context c, byte[] data) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return "Bukan berkas gambar";
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample(bounds.outWidth, bounds.outHeight);
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (bmp == null) return "Bukan berkas gambar";
        File out = file(c);
        File tmp = new File(c.getFilesDir(), "wallpaper.tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)) return "Gagal menyimpan gambar";
        } finally {
            bmp.recycle();
        }
        if (out.exists() && !out.delete()) {
            // overwrite via rename below still replaces when the filesystem allows it
        }
        if (!tmp.renameTo(out)) return "Gagal menyimpan gambar";
        return null;
    }

    private static byte[] readLimited(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        for (int n; (n = in.read(buf)) > 0; ) {
            total += n;
            if (total > MAX_BYTES) return null;
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static int sample(int w, int h) {
        int sample = 1;
        int edge = Math.max(w, h);
        while (edge / sample > MAX_EDGE) sample *= 2;
        return sample;
    }

    private static boolean looksLikeImage(byte[] data) {
        if (data.length < 12) return false;
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) return true; // jpeg
        if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50) return true; // png
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') return true;
        if (data[0] == 'B' && data[1] == 'M') return true;
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') return true; // webp
        return false;
    }

    private static String driveHint() {
        return "Bukan gambar langsung. Unduh ke USB lalu pilih lewat Files. Tautan Drive harus file publik.";
    }
}
