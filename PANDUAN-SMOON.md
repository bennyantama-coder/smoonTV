# WidgeTV Padang untuk SMOON 230T

## Status yang sebenarnya

Sumber disiapkan dari ashfaqhaq/widgetv commit a497eedf0c55a2c7d0bb927152dbe851f2a135f7.
Struktur proyek telah diperiksa sebelum workflow dibuat: proyek Android ada di launcher/, modul app,
Android Gradle Plugin 8.5.2, Gradle Wrapper 8.7, Java 17, compile/target SDK 34, minSdk 28 (Android 9).
Android SMOON Anda harus diperiksa; nama model tidak membuktikan versi Android.
Belum ada build APK yang lulus. Upaya menjalankan Gradle di lingkungan penyusunan gagal mengunduh
distribusi (Network is unreachable); Android SDK dan JDK compiler lokal juga belum tersedia.
Pemeriksaan XML, sintaks Bash, dan git diff bukan pengganti kompilasi, lint, atau pengujian perangkat.
Belum ada workflow yang dijalankan di akun GitHub, belum ada fork/repository baru yang dibuat.

## Konfigurasi

| Pengaturan | Nilai |
|---|---|
| Lokasi | Padang, koordinat pusat kota -0.9471, 100.4172 |
| Zona waktu aplikasi | Asia/Jakarta, tidak mengubah zona sistem TV |
| Metode awal | SINGAPORE |
| Asar | SHAFI (Syafi’i) |
| Suhu | Celsius |
| Jam kota kedua | Mati |
| Koreksi awal | 0 menit untuk lima waktu |
| Dock | Netflix TV/seluler dan YouTube TV/seluler, hanya bila paket terpasang |

Kode jam utama, tanggal, kalender perhitungan salat, label timeline, tanggal Hijriah, dan pergantian
pola harian memakai zona Padang. Tanggal perhitungan dikirim sebagai year/month/day eksplisit,
agar tidak kembali mengikuti zona perangkat lewat DateComponents.from(Date).

## Metode yang benar-benar tersedia

Diperiksa langsung pada PrayerPrefs.java dan dependency adhan-java 1.2.1:
KARACHI, MUSLIM_WORLD_LEAGUE, UMM_AL_QURA, EGYPTIAN, NORTH_AMERICA,
DUBAI, KUWAIT, QATAR, SINGAPORE, MOON_SIGHTING_COMMITTEE.
OTHER bukan opsi panel. KEMENAG tidak tersedia: jangan menuliskan KEMENAG dalam config.xml.
Singapore adalah pilihan awal yang tersedia, bukan klaim sama persis dengan jadwal resmi Kemenag Padang.
Mazhab SHAFI menentukan perhitungan Asar, terpisah dari metode sudut Subuh/Isya.

### Penyesuaian menit lewat remote

Pilih jam/timeline kemudian tekan OK untuk membuka Prayer settings.
Pilih baris Fajr/Subuh, Dhuhr/Zuhur, Asr/Asar, Maghrib, atau Isha/Isya offset dengan atas/bawah;
ubah dengan kiri/kanan, satu menit per langkah, batas -30 sampai +30 menit.
Nilai disimpan di aplikasi dan tidak memerlukan build ulang. Tidak ada offset Terbit dalam panel ini.
Gunakan rumus: koreksi = waktu acuan lokal dikurangi waktu aplikasi.
Contoh aplikasi 05:00, acuan 05:03: masukkan +3. Aplikasi 18:25, acuan 18:23: masukkan -2.
Bandingkan beberapa tanggal dengan jadwal Kemenag/masjid setempat: selisih metode sudut dapat berubah
menurut musim, sehingga satu offset tidak menjamin cocok sepanjang tahun.
Nilai awal hanya berlaku jika preferensi sebelumnya belum tersimpan; setelah memperbarui APK,
periksa lagi metode/mazhab/offset di panel karena pengaturan lama dipertahankan.

## Membangun lewat GitHub Actions

1. Fork https://github.com/ashfaqhaq/widgetv ke akun sendiri.
2. Ekstrak ZIP ini. Salin seluruh isinya ke root fork, termasuk folder tersembunyi .github/;
   pertahankan launcher/, content/, dan tools/ pada level yang sama dengan README.md.
   Cara Git lokal: checkout fork, timpa file dengan isi ZIP, lalu git add -A, commit, dan push.
   Jika memakai unggahan web, pastikan .github/workflows/android-apk.yml ikut diunggah.
3. Di fork, buka Actions dan aktifkan workflow bila GitHub memintanya.
4. Jalankan Build WidgeTV Padang APK lewat Run workflow, atau push ke main/master.
5. Tunggu seluruh job build hijau. Workflow menjalankan assembleDebug, lintDebug, testDebugUnitTest,
   lalu memeriksa tanda tangan APK dan membuat SHA256SUMS.txt. Proyek upstream tidak menyertakan
   unit test: keberhasilan task test yang kosong bukan bukti pengujian fungsi.
6. Unduh artifact WidgeTV-Padang-APK. Artifact APK hanya diunggah setelah semua langkah wajib lulus.
   Artifact WidgeTV-build-reports membantu membaca kegagalan lint/pengujian.
7. Jika gagal, simpan log langkah merah. Status gagal harus diperbaiki sebelum APK disebut siap.

APK ini varian debug bertanda tangan debug untuk sideload pribadi. Keystore runner GitHub bersifat
sementara: build berikutnya bisa memakai sertifikat berbeda dan menolak update di atas instalasi lama.
Untuk distribusi/update jangka panjang diperlukan keystore stabil; jangan memasukkan kunci privat ke repo.

## Instalasi tanpa menonaktifkan launcher

Transfer APK ke TV melalui USB/file manager dan izinkan instalasi dari sumber tersebut bila diminta.
Buka WidgeTV dari daftar aplikasi. Jangan jalankan prompt debloat upstream atau petunjuk takeover.
Jika sistem menawarkan pemilih Home, coba sekali dahulu; memilih WidgeTV secara manual bersifat opsional.
Jika firmware tidak menawarkan penggantian Home, gunakan dari daftar aplikasi; jangan memaksa dengan
menonaktifkan launcher bawaan atau setup wizard.

Alternatif ADB setelah perangkat benar-benar tersambung: adb install -r WidgeTV-Padang-debug.apk.
Tidak perlu USB debugging untuk instalasi dari USB/file manager. Port USB-A TV box tidak otomatis
mendukung sambungan ADB ke komputer; jangan mengandalkan kabel USB-A ke USB-A.
Skrip tools/build-install.sh hanya memasang APK, tidak mengubah default HOME.
Skrip tools/disable.sh dan tools/reapply.sh sengaja diblokir pada edisi ini.

Pintasan bukan pemasang Netflix/YouTube dan tidak membuka sertifikasi DRM. Jika aplikasi tidak terpasang,
tile tidak muncul. Bila versi TV dan seluler keduanya terpasang, keduanya dapat muncul di dock.
Build lulus belum membuktikan kompatibilitas remote, firmware, performa, atau Netflix pada SMOON;
uji buka/tutup aplikasi, navigasi D-pad, kembali ke Home, pergantian tanggal WIB dan offset salat di perangkat.

Sumber upstream dan atribusi MIT tetap disertakan pada LICENSE dan NOTICE.md.
