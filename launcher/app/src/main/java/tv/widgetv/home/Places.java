package tv.widgetv.home;

/**
 * Cities the remote can pick for prayer times and weather. Padang matches res/values/config.xml.
 * Coordinates are city centres (Masjid al-Haram / Masjid Nabawi for Makkah and Madinah).
 */
final class Place {
    final String id;
    final String name;
    final String zone;
    final double lat;
    final double lng;

    Place(String id, String name, String zone, double lat, double lng) {
        this.id = id;
        this.name = name;
        this.zone = zone;
        this.lat = lat;
        this.lng = lng;
    }
}

final class Places {
    static final Place PADANG = new Place("padang", "Padang", "Asia/Jakarta", -0.9471, 100.4172);

    static final Place[] CITIES = {
            PADANG,
            new Place("payakumbuh", "Payakumbuh", "Asia/Jakarta", -0.2297, 100.6308),
            new Place("bukittinggi", "Bukittinggi", "Asia/Jakarta", -0.3051, 100.3692),
            new Place("pekanbaru", "Pekanbaru", "Asia/Jakarta", 0.5071, 101.4478),
            new Place("jakarta", "Jakarta", "Asia/Jakarta", -6.2088, 106.8456),
            new Place("madinah", "Madinah", "Asia/Riyadh", 24.4672, 39.6112),
            new Place("makkah", "Makkah", "Asia/Riyadh", 21.4225, 39.8262)
    };

    /** Zones the coordinate editor cycles through. Asia/Jakarta stays first. */
    static final String[] ZONES = {
            "Asia/Jakarta", "Asia/Pontianak", "Asia/Makassar", "Asia/Jayapura",
            "Asia/Singapore", "Asia/Kuala_Lumpur", "Asia/Riyadh", "UTC"
    };

    private Places() {
    }

    static Place byId(String id) {
        if (id == null) return null;
        for (Place p : CITIES) if (p.id.equals(id)) return p;
        return null;
    }
}
