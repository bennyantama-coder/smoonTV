package tv.widgetv.home;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class EditorLogicTest {
    @Test public void favoritesCanGrowBeyondOneScreenAndRemainOrdered() {
        List<String> apps = Arrays.asList("netflix", "youtube", "vlc", "browser", "music", "files");
        assertEquals(Arrays.asList("netflix", "youtube", "vlc", "browser", "files", "music"), DockOrder.move(apps,"files",-1));
        assertEquals(apps, DockOrder.add(apps,"youtube"));
        assertEquals(6,apps.size());
    }
    @Test public void removeLastFavoriteReallyLeavesAnEmptyDock() {
        assertEquals(Collections.emptyList(), DockOrder.remove(Collections.singletonList("netflix"),"netflix"));
    }
    @Test public void movesStopAtEdgesAndIgnoreUnknownPackages() {
        List<String> apps=Arrays.asList("a","b");
        assertEquals(apps,DockOrder.move(apps,"a",-1));
        assertEquals(apps,DockOrder.move(apps,"b",1));
        assertEquals(apps,DockOrder.move(apps,"missing",1));
    }
    @Test public void duplicatesAndBlankNamesAreRemoved() {
        assertEquals(Arrays.asList("a","b"),DockOrder.clean(Arrays.asList("a",null," ","a"," b ")));
    }
    @Test public void locationsCarryTheirOwnZoneAcrossMidnight() {
        Instant instant=Instant.parse("2026-10-26T18:00:00Z");
        assertEquals(27,instant.atZone(ZoneId.of(CityLocation.CITIES[0].zone)).getDayOfMonth());
        assertEquals(26,instant.atZone(ZoneId.of("Asia/Riyadh")).getDayOfMonth());
        for (CityLocation city:CityLocation.CITIES) assertNotNull(ZoneId.of(city.zone));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectNaN() { new CityLocation("X",Double.NaN,100,"Asia/Jakarta"); }
    @Test(expected=IllegalArgumentException.class) public void rejectInvalidCoordinates() { new CityLocation("X",-91,100,"Asia/Jakarta"); }
    @Test(expected=IllegalArgumentException.class) public void rejectInvalidZone() { new CityLocation("X",0,100,"Padang"); }
    @Test(expected=IllegalArgumentException.class) public void rejectEmptyName() { new CityLocation(" ",0,100,"Asia/Jakarta"); }
}
