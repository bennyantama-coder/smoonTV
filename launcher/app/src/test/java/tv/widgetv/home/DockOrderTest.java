package tv.widgetv.home;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DockOrderTest {
    @Test
    public void parseDropsBlanksAndDuplicates() {
        assertEquals(Arrays.asList("a", "b"), DockOrder.parse("a\n\n b \na"));
        assertTrue(DockOrder.parse(null).isEmpty());
        assertEquals("", DockOrder.format(Collections.emptyList()));
    }

    @Test
    public void moveAndCap() {
        List<String> start = Arrays.asList("netflix", "youtube", "prime");
        assertEquals(Arrays.asList("youtube", "netflix", "prime"), DockOrder.move(start, 0, 1));
        assertEquals(start, DockOrder.move(start, 0, -1));
        assertEquals(Arrays.asList("netflix", "prime"), DockOrder.without(start, 1));
        assertEquals(Arrays.asList("netflix", "youtube", "prime", "maps"), DockOrder.plus(start, "maps"));
        assertEquals(start, DockOrder.plus(start, "netflix"));
        List<String> full = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8");
        assertEquals(full, DockOrder.plus(full, "9"));
    }

    @Test
    public void rowOrderFillsMissing() {
        assertEquals(Arrays.asList("quote", "clock", "weather", "prayer"), RowOrder.parse("quote,clock,nope"));
        assertEquals(Arrays.asList("clock", "weather", "prayer", "quote"), RowOrder.parse(null));
        List<String> moved = RowOrder.move(RowOrder.parse(null), 0, 1);
        assertEquals(Arrays.asList("weather", "clock", "prayer", "quote"), moved);
    }
}
