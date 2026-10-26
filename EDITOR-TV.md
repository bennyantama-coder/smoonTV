# WidgeTV 2.0 — editor langsung di TV

Buka ikon pengaturan kanan atas, tombol Menu remote, atau tahan OK sekitar 0,65 detik.
Tahan OK pada aplikasi membuka tindakan untuk aplikasi itu; pengaturan utama tetap tersedia lewat ikon roda gigi.

- Aplikasi favorit: tambahkan dari daftar aplikasi terpasang, hapus pintasan, geser kiri/kanan.
  Dock layar utama satu baris mendatar dan menggulir dengan remote. Tidak ada batas empat aplikasi.
  Menghapus pintasan tidak mencopot aplikasi; aplikasi kembali tersedia di grid bawah.
  Menghapus semua favorit diperbolehkan. Urutan tersimpan sesudah restart.
- Wallpaper File / USB: pilih JPG, PNG, WebP dengan pemilih file Android.
  Gambar disalin ke penyimpanan internal aplikasi, dibatasi 20 MB, diperkecil maksimal 1920×1080
  dengan proporsi dipertahankan, lalu ditampilkan center-crop. USB bisa dicabut setelah penyimpanan selesai.
- Wallpaper internet: tautan langsung HTTPS. Halaman web, halaman pencarian, dan tautan berbagi Drive
  tidak dianggap gambar. Jika situs memerlukan login, unduh gambar dulu lalu pilih file.
- Google Drive: pilih Drive dalam pemilih file Android jika provider Drive tersedia. Tidak semua firmware
  TV menyediakan pemilih dokumen atau aplikasi Drive. Jika tidak ada, unduh gambar Drive di komputer,
  salin ke USB lalu pilih File / USB. Aplikasi tidak meminta kredensial Google.
- Kegelapan wallpaper 0/20/35/50/65%; kembali ke langit bawaan kapan saja.
- Lokasi: 22 kota pilihan offline (termasuk Padang, Payakumbuh, Bukittinggi, Madinah, Makkah),
  atau masukkan nama, koordinat dan zona waktu IANA. Lokasi dipakai untuk jam, tanggal, salat, dan cuaca.
  Ini koordinat pusat kota, bukan penentuan posisi rumah dengan GPS.
  Metode salat, mazhab dan koreksi menit tidak diubah otomatis saat berpindah kota; periksa kesesuaiannya.
- Widget salat, cuaca dan kutipan dapat disembunyikan/ditampilkan; tersedia tiga ukuran tulisan.
  Jam/timeline pada Home tetap membuka pengaturan metode salat dan koreksi menit yang sudah ada.

Tekan Kembali dari halaman editor sampai kembali ke Home untuk menerapkan perubahan.
Launcher dan aplikasi sistem tetap aktif. Perubahan ini tidak memerlukan debloat atau akses root.

## Build dan instalasi

Workflow asli menjalankan assembleDebug, lintDebug, testDebugUnitTest, verifikasi signature dan SHA256.
Unit test baru memeriksa urutan/duplikasi/favorit kosong serta validasi lokasi dan zona lintas tengah malam.
Build hijau bukan pengujian remote SMOON atau provider Google Drive di perangkat nyata.

APK debug dari runner berbeda dapat memiliki sertifikat berbeda dari versi lama. Jika muncul
INSTALL_FAILED_UPDATE_INCOMPATIBLE, simpan/catat preferensi versi lama sebelum mencopot WidgeTV lama
(hanya WidgeTV, jangan launcher sistem); instalasi ulang akan menghapus preferensi WidgeTV lama.
Untuk pembaruan rutin tanpa masalah ini, diperlukan keystore penandatanganan stabil yang disimpan sebagai secret CI.

## Uji di perangkat

1. Tambah setidaknya enam favorit, geser melampaui empat tile, pindahkan urutan, hapus semua lalu tambahkan kembali.
2. Tutup/buka aplikasi dan restart TV; cek urutan dan pilihan tetap tersimpan.
3. Pilih gambar USB, cabut USB sesudah tersimpan; cek gambar tetap tampil. Batalkan pemilih file dan cek gambar lama tetap ada.
4. Coba gambar HTTPS yang valid, halaman HTML, gambar rusak, dan file >20 MB; gambar lama harus tetap tampil ketika gagal.
5. Pilih Payakumbuh, lalu Dubai; cek jam, tanggal, label kota dan cuaca mengikuti lokasi. Periksa lagi koreksi salat.
6. Uji tombol Kembali, Menu, tahan OK, semua favorit kosong, dan aplikasi favorit dicopot di pengaturan Android.
