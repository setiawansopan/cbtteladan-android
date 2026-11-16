# Changelog

## 5.0.0

- Rilis pertama aplikasi Android CBT Teladan, server ujian `https://cbtteladan.com`.
- Sematkan layar wajib selama ujian; panel peringatan bila terlepas atau layar terbagi.
- Tanpa tangkapan/rekam layar, salin/tempel, dan tautan ke luar server ujian.
- Header `X-CBT-Client`/`X-CBT-Version`, User-Agent `CBTTeladan/5.0.0 cbt-teladan/5.0.0`,
  dan tanda tangan `X-CBT-Timestamp`/`X-CBT-Signature` di setiap request ke server ujian.
- Pemeriksaan perangkat: koneksi ke server, baterai, versi WebView.
