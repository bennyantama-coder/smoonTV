package tv.widgetv.home;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Clock, weather, prayer, and quote rows. Unknown ids are dropped; missing ones are appended. */
final class RowOrder {
    static final String[] DEFAULT = {"clock", "weather", "prayer", "quote"};

    private RowOrder() {
    }

    static List<String> parse(String raw) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String s = part.trim();
                if (known(s)) seen.add(s);
            }
        }
        for (String d : DEFAULT) seen.add(d);
        return new ArrayList<>(seen);
    }

    static String format(List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (String id : order) {
            if (!known(id)) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    static List<String> move(List<String> order, int index, int dir) {
        return DockOrder.move(order, index, dir);
    }

    private static boolean known(String id) {
        for (String d : DEFAULT) if (d.equals(id)) return true;
        return false;
    }
}
