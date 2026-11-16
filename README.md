# CBT Teladan untuk Android

Aplikasi ujian CBT Teladan untuk HP dan tablet Android (Android 7.0 ke atas).
Pasangan dari aplikasi desktop (`cbtteladan-frontend`) dengan aturan yang sama:
siswa wajib memakai aplikasi resmi versi minimum tertentu atau Safe Exam Browser.

## Fitur keamanan

- **Sematkan layar (screen pinning)**: ujian baru terbuka setelah layar
  disematkan. Bila terlepas, halaman ujian ditutup panel peringatan dan halaman
  ujian mencatat pelanggaran.
- **Tanpa tangkapan/rekam layar** (`FLAG_SECURE`), jendela mengambang aplikasi
  lain disembunyikan (Android 12+), layar terbagi ditolak.
- **Hanya server ujian**: tautan ke situs lain diblokir, hanya HTTPS, dan
  sertifikat buatan pengguna tidak dipercaya (lalu lintas sulit disadap).
- **Tanpa salin/tempel**, tanpa menu tekan-lama, tombol kembali dinonaktifkan.
- **Tanda tangan request**: setiap request ke server membawa `X-CBT-Timestamp`
  dan `X-CBT-Signature` (HMAC-SHA256 atas timestamp dengan kunci
  `CBT_APP_SECRET`), sama dengan aplikasi desktop v5.

## Rilis

1. Sekali saja, isi GitHub Secrets repo ini (Settings > Secrets and variables > Actions):

   | Secret | Isi |
   | --- | --- |
   | `CBT_APP_SECRET` | Sama persis dengan `CBT_APP_SECRET` di `.env` server dan di repo desktop |
   | `ANDROID_KEYSTORE_BASE64` | Isi berkas keystore rilis dalam base64 |
   | `ANDROID_KEYSTORE_PASSWORD` | Kata sandi keystore |
   | `ANDROID_KEY_ALIAS` | Alias kunci, mis. `cbtteladan` |
   | `ANDROID_KEY_PASSWORD` | Kata sandi kunci |

   Membuat keystore (di komputer sendiri, simpan berkas dan kata sandinya baik-baik;
   tanpa keystore yang sama, versi berikutnya tidak bisa dipasang menimpa versi lama):

   ```bash
   keytool -genkeypair -v -keystore cbtteladan-release.jks -alias cbtteladan \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 cbtteladan-release.jks   # salin hasilnya ke ANDROID_KEYSTORE_BASE64
   ```

2. Naikkan `appVersionName` di `app/build.gradle.kts`, catat di `CHANGELOG.md`.
3. Buat tag yang sama dengan versinya, mis. `v5.0.0`. GitHub Actions membangun
   `CBT-Teladan-5.0.0.apk` dan menerbitkannya di halaman Releases.
   Tag seperti `v5.0.0-rc.1` diterbitkan sebagai pre-release untuk uji coba.

Build di setiap push/PR hanya menghasilkan APK debug **tanpa kunci** untuk
pengujian (artefak "cbt-teladan-debug-apk"); jangan dibagikan ke siswa.

## Untuk siswa

1. Unduh `CBT-Teladan-x.y.z.apk` dari tautan yang dibagikan sekolah, lalu pasang
   (izinkan "Instal aplikasi tidak dikenal" bila diminta).
2. Buka **CBT Teladan**, periksa hasil pemeriksaan perangkat, lalu ketuk
   **Mulai Ujian** dan pilih **Sematkan/Pin** saat diminta.
3. Jika layar tidak tersemat: buka Pengaturan > Keamanan (atau Keamanan &
   privasi > Setelan lainnya) > **Sematkan aplikasi / App pinning**, aktifkan,
   lalu coba lagi.
4. Untuk keluar: tombol **Keluar** di bilah atas.

## Pengembangan

- Buka folder ini di Android Studio, atau `./gradlew assembleDebug`.
- URL server: `CBT_EXAM_URL=https://contoh.sch.id ./gradlew assembleDebug`
  (bawaan `https://cbtteladan.com`).
- Uji unit: `./gradlew testDebugUnitTest`.
