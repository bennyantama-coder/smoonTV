# WidgeTV Padang untuk SMOON 230T

## Status yang sebenarnya

Sumber awal dari ashfaqhaq/widgetv. Proyek Android ada di `launcher/`: Android Gradle Plugin 8.5.2,
Gradle Wrapper 8.7, Java 17, compile/target SDK 34, minSdk 28 (Android 9).
Versi aplikasi ini: 1.2 (versionCode 3), bisa disunting dari remote. Adzan Subuh dan adzan salat lain
dibunyikan oleh alarm pada waktu yang sudah dikoreksi.

`./gradlew assembleDebug lintDebug testDebugUnitTest` lulus di lingkungan penyusunan
(JDK 21 menjalankan toolchain Java 17, Android SDK 34, build-tools 34.0.0). APK debug tertanda v2.
Itu setara dengan job workflow. Centang hijau di GitHub Actions pada commit ini adalah bukti run
Actions itu sendiri; unduh artifact hanya dari run yang hijau.

Android SMOON harus dicek di perangkat. Nama model tidak membuktikan versi Android.
Build lulus belum membuktikan remote, firmware, atau Netflix di SMOON 230T.

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

Selama kota belum diganti di TV, jam, tanggal, kalender salat, label timeline, tanggal Hijriah,
dan pola harian memakai Asia/Jakarta (Padang). Setelah kota diganti, jam dan salat mengikuti zona
kota itu; zona sistem TV tetap tidak diubah. Tanggal perhitungan dikirim sebagai year/month/day
eksplisit, agar tidak kembali mengikuti zona perangkat lewat DateComponents.from(Date).

## Metode yang benar-benar tersedia

Diperiksa langsung pada PrayerPrefs.java dan dependency adhan-java 1.2.1:
KARACHI, MUSLIM_WORLD_LEAGUE, UMM_AL_QURA, EGYPTIAN, NORTH_AMERICA,
DUBAI, KUWAIT, QATAR, SINGAPORE, MOON_SIGHTING_COMMITTEE.
OTHER bukan opsi panel. KEMENAG tidak tersedia: jangan menuliskan KEMENAG dalam config.xml.
Singapore adalah pilihan awal yang tersedia, bukan klaim sama persis dengan jadwal resmi Kemenag Padang.
Mazhab SHAFI menentukan perhitungan Asar, terpisah dari metode sudut Subuh/Isya.

### Penyesuaian menit lewat remote

Tekan OK sebentar pada garis waktu salat, atau buka editor (tahan OK) lalu pilih "Waktu salat & koreksi".
Pilih baris Fajr/Subuh, Dhuhr/Zuhur, Asr/Asar, Maghrib, atau Isha/Isya offset dengan atas/bawah;
ubah dengan kiri/kanan, satu menit per langkah, batas -30 sampai +30 menit.
Nilai disimpan di aplikasi dan tidak memerlukan build ulang. Tidak ada offset Terbit dalam panel ini.
Gunakan rumus: koreksi = waktu acuan lokal dikurangi waktu aplikasi.
Contoh aplikasi 05:00, acuan 05:03: masukkan +3. Aplikasi 18:25, acuan 18:23: masukkan -2.
Bandingkan beberapa tanggal dengan jadwal Kemenag/masjid setempat: selisih metode sudut dapat berubah
menurut musim, sehingga satu offset tidak menjamin cocok sepanjang tahun.
Nilai awal hanya berlaku jika preferensi sebelumnya belum tersimpan; setelah memperbarui APK,
periksa lagi metode/mazhab/offset di panel karena pengaturan lama dipertahankan.

## Sunting di TV (tanpa build ulang)

Buka WidgeTV dari daftar aplikasi, lalu **tahan tombol OK** kira-kira setengah detik.
Tombol Menu pada remote melakukan hal yang sama. Panel "Sunting WidgeTV" terbuka.
Setiap perubahan langsung ditulis ke penyimpanan aplikasi dan tetap ada setelah reboot
atau setelah TV dimatikan. Menghapus data aplikasi atau meng-uninstall mengembalikan bawaan.

| Bagian | Remote | Isi |
|---|---|---|
| Dock aplikasi | Kiri/kanan geser, OK tandai hapus, OK lagi hapus, "Tambah aplikasi" | Aplikasi yang sudah terpasang, maksimal 8. Netflix (`com.netflix.ninja` / `com.netflix.mediaclient`) dan YouTube (`com.google.android.youtube.tv` / `com.google.android.youtube`) tetap di dock sampai Anda mengubahnya, dan hanya terlihat jika paketnya terpasang. |
| Wallpaper | OK pada pilihan | Langit bawaan; gambar lewat Files atau USB; daftar gambar di penyimpanan (minta izin bila perlu); tempel URL. |
| Lokasi | OK pada kota | Padang (bawaan), Payakumbuh, Bukittinggi, Pekanbaru, Jakarta, Madinah, Makkah, atau koordinat + zona waktu. Untuk waktu salat dan cuaca. Jam mengikuti zona kota itu. Zona sistem TV tidak diubah. |
| Tata letak baris | OK tampil/sembunyi, kiri/kanan ukuran; ganti mode untuk urutan | Jam, cuaca, waktu salat, kutipan. Ukuran: Kecil / Biasa / Besar. Dock tetap di bawah. |
| Waktu salat & koreksi | Panel yang sudah ada: kiri/kanan ubah | Metode, mazhab, offset menit. Lihat bagian Metode. |
| Adzan & alarm | OK hidup/mati tiap waktu, kiri/kanan volume, Dengarkan untuk coba | Subuh memakai rekaman berbeda. Empat salat lain memakai satu rekaman. Lihat bagian Adzan. |

Back menutup panel, atau keluar dari WidgeTV ke launcher bawaan bila tidak ada panel.

## Adzan

Dua rekaman ikut di dalam APK. Keduanya rekaman sungguhan, lisensinya bebas dibagikan, dan bunyinya berbeda:

- **Subuh** memakai adzan fajar dari Islamic Center Malmö (muazin Besim Azemi), 19 Agustus 2012.
  Rekaman ini memuat kalimat *as-salatu khayrun min an-nawm* (salat lebih baik daripada tidur), dua kali,
  sesudah *hayya 'ala al-falah*. Sumber: Wikimedia Commons, CC BY 3.0. Audio dipotong dari video,
  dikeraskan, dan disimpan sebagai Ogg.
- **Zuhur, Asar, Magrib, dan Isya** memakai satu rekaman lain, tanpa kalimat itu: "The Adhan" oleh
  Aaqib Azeez, diunggah Atcovi di Wikimedia Commons, CC BY-SA 4.0. Berkas MP3 tidak diubah.

Alarm hidup sejak APK dipasang, termasuk Subuh. Matikan Subuh dari editor bila TV tidak perlu berbunyi
saat fajar. Alarm memakai waktu salat yang sudah ditambah koreksi menit. Terbit tidak ada adzannya.

Cara kerjanya di TV:

1. Tahan OK, pilih **Adzan & alarm**.
2. Setiap waktu bisa dimatikan sendiri. Volume: Pelan, Sedang, Keras.
3. **Dengarkan Subuh** dan **Dengarkan salat lain** memutar bedanya tanpa menggeser alarm.
4. OK atau Back saat adzan berbunyi menghentikannya. Layar menampilkan "Adzan … · OK hentikan".
5. Rekaman sendiri dari Files atau USB (mp3, ogg, wav, m4a, paling besar 8 MB) menggantikan slot
   Subuh atau slot salat lain. Kiri kembali ke rekaman bawaan.

Alarm dijadwalkan ulang setelah reboot, setelah jam diubah, dan setelah kota atau koreksi menit diganti.
Paling andal saat WidgeTV sedang terbuka. Bila aplikasi di-force stop, atau kotak mati lewat dari waktu
salat, adzan yang terlewat tidak diputar belakangan; alarm berikutnya yang berbunyi. TV tidak dinyalakan
paksa. Volume TV perlu cukup terdengar.

## Wallpaper dan Google Drive

Path yang dipakai: Files/USB dan URL. Gambar disalin ke penyimpanan internal aplikasi (diperkecil, JPEG)
supaya tetap ada bila USB dicabut.

Login Google Drive (OAuth) **belum** dibuat: terlalu berat untuk potongan pertama dan mudah rusak
bila token TV tidak selesai. Cara yang jalan sekarang:

1. Di ponsel atau komputer, unduh gambar dari Drive.
2. Salin ke USB, colok ke SMOON, lalu di WidgeTV pilih **Pilih dari Files atau USB**.
3. Atau tempel URL gambar langsung (`https://…/foto.jpg`).
4. Tautan publik berbentuk `https://drive.google.com/file/d/ID/view` dicoba diubah ke unduhan langsung.
   Bila Google membalas halaman HTML (file tidak publik atau minta konfirmasi), TV menolaknya.
   Unduh manual, lalu pakai Files.

### Lokasi

Bawaan tetap Padang, -0.9471, 100.4172, Asia/Jakarta. Kota lain memakai pusat kota
(Makkah di Masjidil Haram, Madinah di Masjid Nabawi). "Koordinat sendiri" menggeser lintang/bujur
0,01 derajat per tekanan (tahan tombol untuk 0,1) dan memilih zona: Jakarta, Pontianak, Makassar,
Jayapura, Singapore, Kuala Lumpur, Riyadh, atau UTC.

## Membangun lewat GitHub Actions

Workflow: **Build WidgeTV Padang APK** (`.github/workflows/android-apk.yml`).
Ia berjalan pada push ke `main`/`master`, pada pull request, dan lewat **Run workflow**.

1. Buka tab **Actions** di repo ini. Aktifkan Actions bila GitHub meminta.
2. Pilih **Build WidgeTV Padang APK** di sidebar kiri.
3. Klik **Run workflow**, pilih branch yang berisi kode ini, lalu **Run workflow** lagi.
4. Tunggu job `build` hijau. Job itu menjalankan `assembleDebug`, `lintDebug`, `testDebugUnitTest`,
   memeriksa tanda tangan APK, dan menulis `SHA256SUMS.txt`.
5. Di run yang hijau, buka **Artifacts** dan unduh **WidgeTV-Padang-editable**.
   Di dalamnya ada `WidgeTV-Padang-editable-debug.apk` dan `SHA256SUMS.txt`.
   Artifact hanya diunggah setelah langkah wajib lulus.
   Artifact **WidgeTV-build-reports** membantu membaca kegagalan lint atau tes.
6. Jika ada langkah merah, jangan pasang APK dari run itu. Simpan lognya.

Setup Android di workflow hanya meminta paket `platform-tools`, lalu `sdkmanager` memasang
`platforms;android-34` dan `build-tools;34.0.0`. Paket SDK lama bernama `tools` tidak dipakai;
cmdline-tools baru menolak nama itu.

APK ini varian debug bertanda tangan debug untuk sideload pribadi. Keystore runner GitHub bersifat
sementara: build berikutnya bisa memakai sertifikat berbeda dan menolak update di atas instalasi lama.
Cabut pemasangan lama, lalu pasang yang baru, bila TV menolak update.
Untuk distribusi jangka panjang diperlukan keystore stabil; jangan memasukkan kunci privat ke repo.

## Instalasi di SMOON 230T tanpa mengganti launcher bawaan

WidgeTV **bukan** Home default. Manifest hanya memakai `LEANBACK_LAUNCHER` dan `LAUNCHER`,
tanpa kategori `HOME`, jadi pemasangan tidak mengambil alih tombol Home.

1. Unduh artifact **WidgeTV-Padang-editable** dari run Actions yang hijau (lihat di atas).
2. Keluarkan `WidgeTV-Padang-editable-debug.apk`. Salin ke USB, atau ke penyimpanan TV lewat file manager.
3. Colok USB ke SMOON. Buka aplikasi Files / file manager bawaan.
4. Buka APK itu. Bila TV minta, izinkan pemasangan dari sumber tersebut untuk file manager itu saja.
5. Setelah terpasang, buka **WidgeTV dari daftar aplikasi**. Jangan nonaktifkan launcher bawaan,
   setup wizard, atau aplikasi sistem. Jangan jalankan skrip debloat.
6. Tombol Home tetap membuka launcher stok. Back dari WidgeTV (bila panel sunting tertutup) kembali ke sana.
7. Tahan OK untuk sunting dock, wallpaper, kota, dan baris widget.

Tidak perlu USB debugging untuk pemasangan dari USB. Port USB-A TV box tidak otomatis menjadi ADB
ke komputer; jangan mengandalkan kabel USB-A ke USB-A.

Alternatif bila ADB jaringan sudah tersambung dan diizinkan di TV:
`adb install -r WidgeTV-Padang-editable-debug.apk`.
`tools/build-install.sh` hanya membangun dan memasang APK, tidak mengubah Home.
`tools/disable.sh` dan `tools/reapply.sh` sengaja diblokir pada edisi ini.

Pintasan dock bukan pemasang Netflix atau YouTube dan tidak membuka DRM. Bila aplikasi tidak terpasang,
ubinnya tidak muncul. Bila versi TV dan seluler keduanya terpasang, keduanya bisa ada di dock
sampai Anda menghapusnya di mode sunting.

Sumber upstream dan atribusi MIT tetap disertakan pada LICENSE dan NOTICE.md.
