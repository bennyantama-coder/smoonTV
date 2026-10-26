package tv.widgetv.home;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Ordered favorites, independent from installed/visible packages. */
final class DockOrder {
    static List<String> clean(List<String> source) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String p : source) if (p != null && !p.trim().isEmpty()) out.add(p.trim());
        return new ArrayList<>(out);
    }
    static List<String> add(List<String> source, String pkg) {
        List<String> out = new ArrayList<>(source);
        out.add(pkg);
        return clean(out);
    }
    static List<String> remove(List<String> source, String pkg) {
        List<String> out = clean(source);
        out.remove(pkg);
        return out;
    }
    static List<String> move(List<String> source, String pkg, int direction) {
        List<String> out = clean(source);
        int from = out.indexOf(pkg), to = from + Integer.signum(direction);
        if (from >= 0 && to >= 0 && to < out.size()) {
            out.remove(from);
            out.add(to, pkg);
        }
        return out;
    }
}
