package tv.widgetv.home;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class HomePrefs {
    static SharedPreferences of(Context c) { return c.getSharedPreferences("home_editor", Context.MODE_PRIVATE); }
    static int revision(Context c) { return of(c).getInt("revision", 0); }
    static void save(Context c, SharedPreferences.Editor e) {
        e.putInt("revision", revision(c) + 1).apply();
    }
    static List<String> dock(Context c) {
        String value = of(c).getString("dock", null);
        if (value == null) return DockOrder.clean(Arrays.asList(c.getResources().getStringArray(R.array.dock_packages)));
        try {
            JSONArray a = new JSONArray(value);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
            return DockOrder.clean(out);
        } catch (Exception e) { return new ArrayList<>(); }
    }
    static void setDock(Context c, List<String> packages) {
        save(c, of(c).edit().putString("dock", new JSONArray(DockOrder.clean(packages)).toString()));
    }
    static void setCity(Context c, CityLocation city) {
        save(c, of(c).edit().putString("city", city.name).putString("lat", Double.toString(city.lat))
                .putString("lng", Double.toString(city.lng)).putString("zone", city.zone));
    }
}
