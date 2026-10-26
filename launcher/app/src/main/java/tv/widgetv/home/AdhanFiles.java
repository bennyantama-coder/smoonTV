package tv.widgetv.home;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Optional recordings copied from Files or USB. The built-in files stay in res/raw. */
final class AdhanFiles {
    static final long MAX_BYTES = 8L * 1024 * 1024;

    private AdhanFiles() {
    }

    static File file(Context c, boolean fajr) {
        return new File(c.getFilesDir(), fajr ? "adhan-fajr.user" : "adhan-other.user");
    }

    /** @return null when the recording was saved, otherwise a short Indonesian reason */
    static String save(Context c, Uri uri, boolean fajr) {
        if (uri == null) return "Berkas tidak bisa dibaca";
        File dest = file(c, fajr);
        File tmp = new File(c.getFilesDir(), dest.getName() + ".part");
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return "Berkas tidak bisa dibaca";
            byte[] head = new byte[16];
            int got = readFully(in, head);
            if (!audioHeader(head, got)) {
                tmp.delete();
                return "Bukan berkas audio";
            }
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(head, 0, got);
                byte[] buf = new byte[8192];
                long total = got;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    total += n;
                    if (total > MAX_BYTES) {
                        tmp.delete();
                        return "Berkas lebih dari 8 MB";
                    }
                    out.write(buf, 0, n);
                }
            }
            if (tmp.length() < 1024) {
                tmp.delete();
                return "Berkas terlalu kecil untuk rekaman";
            }
            if (dest.exists() && !dest.delete()) {
                tmp.delete();
                return "Gagal menyimpan rekaman";
            }
            if (!tmp.renameTo(dest)) {
                tmp.delete();
                return "Gagal menyimpan rekaman";
            }
            return null;
        } catch (IOException | RuntimeException e) {
            tmp.delete();
            return "Gagal membaca rekaman";
        }
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int got = 0;
        while (got < buf.length) {
            int n = in.read(buf, got, buf.length - got);
            if (n < 0) break;
            got += n;
        }
        return got;
    }

    /** Ogg, MP3, WAV, or M4A. HTML and other text are rejected. */
    private static boolean audioHeader(byte[] h, int n) {
        if (n < 3) return false;
        if (h[0] == '<' || h[0] == '{' || h[0] == '%') return false;
        if (n >= 4 && h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S') return true;
        if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') return true;
        if (n >= 4 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F') return true;
        if (n >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') return true;
        int b0 = h[0] & 0xFF;
        int b1 = h[1] & 0xFF;
        return b0 == 0xFF && (b1 & 0xE0) == 0xE0;
    }
}
