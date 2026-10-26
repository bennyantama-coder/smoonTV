package tv.widgetv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Bounded, normalized local copy: original USB/Drive URI is never needed again. */
final class WallpaperStore {
    static final long MAX_BYTES = 20 * 1024 * 1024;
    static File file(Context c) { return new File(c.getFilesDir(), "wallpaper.jpg"); }
    static Bitmap decode(File f) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), (decoder, info, source) -> {
            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
            if (w < 1 || h < 1 || w > 50000 || h > 50000) throw new IllegalArgumentException("Ukuran gambar tidak didukung.");
            double scale = Math.min(1, Math.min(1920.0 / w, 1080.0 / h));
            decoder.setTargetSize(Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }
    static void importImage(Context c, InputStream in) throws IOException {
        if (in == null) throw new IOException("File tidak dapat dibuka.");
        File temp = File.createTempFile("wallpaper-", ".download", c.getCacheDir());
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                byte[] bytes = new byte[8192]; long count = 0;
                for (int n; (n = in.read(bytes)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Dibatalkan.");
                    count += n;
                    if (count > MAX_BYTES) throw new IOException("Gambar terlalu besar. Batas 20 MB.");
                    out.write(bytes, 0, n);
                }
            }
            Bitmap bmp;
            try { bmp = decode(temp); }
            catch (Exception e) { throw new IOException("File bukan gambar yang didukung. Gunakan JPG, PNG, atau WebP."); }
            AtomicFile target = new AtomicFile(file(c));
            FileOutputStream out = null;
            try {
                out = target.startWrite();
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw new IOException("Gagal menyimpan gambar.");
                target.finishWrite(out);
            } catch (IOException e) { target.failWrite(out); throw e; }
            finally { bmp.recycle(); }
        } finally { temp.delete(); }
    }
    static void download(Context c, String text) throws IOException {
        URL url;
        try { url = new URL(text.trim()); } catch (Exception e) { throw new IOException("Masukkan tautan gambar HTTPS yang lengkap."); }
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null)
                throw new IOException("Gunakan tautan HTTPS tanpa kata sandi.");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(15000); conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "image/*");
                int code = conn.getResponseCode();
                if (code >= 300 && code < 400) {
                    String next = conn.getHeaderField("Location");
                    if (next == null) throw new IOException("Pengalihan tautan tidak valid.");
                    url = new URL(url, next); continue;
                }
                if (code != 200) throw new IOException("Unduhan gagal (HTTP " + code + "). Unduh lewat browser lalu pilih file.");
                if (conn.getContentLengthLong() > MAX_BYTES) throw new IOException("Gambar melebihi 20 MB.");
                String type = conn.getContentType();
                if (type != null && type.toLowerCase(java.util.Locale.ROOT).contains("text/html"))
                    throw new IOException("Ini halaman web, bukan file gambar. Unduh gambar dahulu, lalu pilih File / USB.");
                try (InputStream in = conn.getInputStream()) { importImage(c, in); }
                return;
            } finally { conn.disconnect(); }
        }
        throw new IOException("Terlalu banyak pengalihan tautan.");
    }
}
