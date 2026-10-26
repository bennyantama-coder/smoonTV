package tv.widgetv.home;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WallpaperUrlsTest {
    @Test
    public void plainHttpsPassesThrough() {
        assertEquals("https://example.com/a.jpg", WallpaperUrls.direct("  https://example.com/a.jpg "));
        assertEquals("http://example.com/a.png", WallpaperUrls.direct("http://example.com/a.png"));
    }

    @Test
    public void driveFileLinkBecomesDirect() {
        assertEquals("https://drive.google.com/uc?export=download&id=abc_DEF-123",
                WallpaperUrls.direct("https://drive.google.com/file/d/abc_DEF-123/view?usp=sharing"));
        assertEquals("https://drive.google.com/uc?export=download&id=xyz",
                WallpaperUrls.direct("https://drive.google.com/open?id=xyz"));
    }

    @Test
    public void rejectsJunk() {
        assertNull(WallpaperUrls.direct(null));
        assertNull(WallpaperUrls.direct(""));
        assertNull(WallpaperUrls.direct("not a url"));
        assertNull(WallpaperUrls.direct("javascript:alert(1)"));
        assertNull(WallpaperUrls.direct("https://example.com/a b.jpg"));
    }
}
