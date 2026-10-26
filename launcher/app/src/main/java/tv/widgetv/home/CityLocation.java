package tv.widgetv.home;

import java.time.ZoneId;

final class CityLocation {
    final String name, zone;
    final double lat, lng;
    CityLocation(String name, double lat, double lng, String zone) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 60)
            throw new IllegalArgumentException("Nama kota wajib diisi (maksimal 60 karakter).");
        if (!Double.isFinite(lat) || lat < -90 || lat > 90 || !Double.isFinite(lng) || lng < -180 || lng > 180)
            throw new IllegalArgumentException("Lintang harus -90…90 dan bujur -180…180.");
        try { ZoneId.of(zone); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Zona waktu tidak valid, contoh Asia/Jakarta."); }
        this.name = name.trim(); this.lat = lat; this.lng = lng; this.zone = zone;
    }
    static final CityLocation[] CITIES = {
        new CityLocation("Padang", -0.9471, 100.4172, "Asia/Jakarta"),
        new CityLocation("Payakumbuh", -0.229, 100.6308, "Asia/Jakarta"),
        new CityLocation("Bukittinggi", -0.3056, 100.3692, "Asia/Jakarta"),
        new CityLocation("Pekanbaru", 0.5071, 101.4478, "Asia/Jakarta"),
        new CityLocation("Medan", 3.5952, 98.6722, "Asia/Jakarta"),
        new CityLocation("Banda Aceh", 5.5483, 95.3238, "Asia/Jakarta"),
        new CityLocation("Palembang", -2.9761, 104.7754, "Asia/Jakarta"),
        new CityLocation("Jakarta", -6.2088, 106.8456, "Asia/Jakarta"),
        new CityLocation("Bandung", -6.9175, 107.6191, "Asia/Jakarta"),
        new CityLocation("Semarang", -6.9667, 110.4167, "Asia/Jakarta"),
        new CityLocation("Yogyakarta", -7.7956, 110.3695, "Asia/Jakarta"),
        new CityLocation("Surabaya", -7.2575, 112.7521, "Asia/Jakarta"),
        new CityLocation("Denpasar", -8.65, 115.2167, "Asia/Makassar"),
        new CityLocation("Makassar", -5.1477, 119.4327, "Asia/Makassar"),
        new CityLocation("Balikpapan", -1.2379, 116.8529, "Asia/Makassar"),
        new CityLocation("Manado", 1.4748, 124.8421, "Asia/Makassar"),
        new CityLocation("Jayapura", -2.5337, 140.7181, "Asia/Jayapura"),
        new CityLocation("Singapura", 1.3521, 103.8198, "Asia/Singapore"),
        new CityLocation("Kuala Lumpur", 3.139, 101.6869, "Asia/Kuala_Lumpur"),
        new CityLocation("Dubai", 25.2048, 55.2708, "Asia/Dubai"),
        new CityLocation("Madinah", 24.4672, 39.6024, "Asia/Riyadh"),
        new CityLocation("Makkah", 21.4225, 39.8262, "Asia/Riyadh")
    };
}
