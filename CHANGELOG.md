# Changelog

## 5.0.3

- Pemberitahuan versi baru: halaman pengecekan perangkat menampilkan kartu "Versi baru tersedia"
  dengan tombol Unduh bila admin sudah mengisi versi terbaru di server (`/app-update`). Selama
  ujian hanya ada penanda kecil "ada versi baru" di bilah bawah. Versi di bawah minimum server
  menampilkan kartu merah (login memang ditolak server sampai diperbarui).
- Gagal memeriksa (offline atau server bermasalah) tidak menampilkan apa pun dan tidak
  mengganggu ujian. Aplikasi hanya memberi tahu; pemasangan tetap manual.

## 5.0.2

- Perbaikan arah kamera depan di tablet yang dipakai lanskap: gambar kini diputar mengikuti
  rotasi layar sehingga tegak (sebelumnya tampil miring), dan disesuaikan ulang saat layar diputar.
- Bila di suatu tablet gambar masih miring, ketuk kotak kamera untuk memutarnya 90 derajat;
  pilihan disimpan. Kotak tetap bisa digeser.

## 5.0.1

- Cermin diri selama ujian, seperti preview webcam di aplikasi Windows: kamera depan tampil
  kecil di pojok layar dengan tanda LIVE. Hanya ditampilkan di layar siswa; gambar tidak
  direkam, tidak disimpan, dan tidak dikirim ke server. Kotaknya bisa digeser.
- Halaman pengecekan perangkat menampilkan baris "Kamera depan" (opsional, tidak memengaruhi
  ringkasan) dan meminta izin kamera. Tanpa kamera atau izin, ujian tetap berjalan tanpa preview.
- Halaman ujian tetap tidak boleh memakai kamera/mikrofon (izin web ditolak).

## 5.0.0

- Halaman pengecekan perangkat dirombak: kartu berikon dengan penanda lulus/peringatan/gagal,
  logo dan nomor versi di atas, serta info perangkat (model, versi Android, prosesor, RAM,
  penyimpanan kosong, ukuran layar, baterai) di samping koneksi, server ujian, dan WebView.
- Bilah informasi dipindah ke bawah dan isinya disamakan dengan taskbar Windows: logo dan
  versi, jaringan, baterai, jam dan tanggal, tombol Refresh dan Keluar berikon.

- Rilis pertama aplikasi Android CBT Teladan, server ujian `https://cbtteladan.com`.
- Sematkan layar wajib selama ujian; panel peringatan bila terlepas atau layar terbagi.
- Tanpa tangkapan/rekam layar, salin/tempel, dan tautan ke luar server ujian.
- Header `X-CBT-Client`/`X-CBT-Version`, User-Agent `CBTTeladan/5.0.0 cbt-teladan/5.0.0`,
  dan tanda tangan `X-CBT-Timestamp`/`X-CBT-Signature` di setiap request ke server ujian.
- Pemeriksaan perangkat: koneksi ke server, baterai, versi WebView.
