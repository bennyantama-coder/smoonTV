package tv.widgetv.home;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PlacesTest {
    @Test
    public void padangMatchesConfig() {
        Place p = Places.byId("padang");
        assertNotNull(p);
        assertEquals("Padang", p.name);
        assertEquals("Asia/Jakarta", p.zone);
        assertEquals(-0.9471, p.lat, 0.00001);
        assertEquals(100.4172, p.lng, 0.00001);
    }

    @Test
    public void seedCities() {
        String[] ids = {"padang", "payakumbuh", "bukittinggi", "pekanbaru", "jakarta", "madinah", "makkah"};
        assertEquals(ids.length, Places.CITIES.length);
        for (String id : ids) {
            Place p = Places.byId(id);
            assertNotNull(id, p);
            assertEquals(id, p.id);
        }
        assertNull(Places.byId("kemenag"));
        assertEquals(Places.PADANG, Places.CITIES[0]);
    }
}
