package tv.widgetv.home;

import java.util.ArrayList;
import java.util.List;

/** Ordered dock package names stored as newline-separated text. */
final class DockOrder {
    static final int MAX = 8;

    private DockOrder() {
    }

    static List<String> parse(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split("\n")) {
            String s = part.trim();
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    static String format(List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String p : pkgs) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(p);
        }
        return sb.toString();
    }

    static List<String> move(List<String> pkgs, int index, int dir) {
        ArrayList<String> out = new ArrayList<>(pkgs);
        int j = index + dir;
        if (index < 0 || index >= out.size() || j < 0 || j >= out.size()) return out;
        String t = out.set(j, out.get(index));
        out.set(index, t);
        return out;
    }

    static List<String> without(List<String> pkgs, int index) {
        ArrayList<String> out = new ArrayList<>(pkgs);
        if (index >= 0 && index < out.size()) out.remove(index);
        return out;
    }

    static List<String> plus(List<String> pkgs, String pkg) {
        ArrayList<String> out = new ArrayList<>(pkgs);
        if (pkg == null || pkg.isEmpty() || out.contains(pkg) || out.size() >= MAX) return out;
        out.add(pkg);
        return out;
    }
}
