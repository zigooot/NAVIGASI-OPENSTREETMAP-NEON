# Navigasi OpenStreetMap Neon

Kartu navigasi mandiri yang diambil dari proyek Rush Launcher (Toyota Rush G AT 2024, Android Auto).
Isinya peta OpenStreetMap bergaya neon, mode navigasi yang mengikuti mobil, kompas, dan pemanduan belokan demi belokan.
Tidak memakai Google Maps, Google Navigation SDK, maupun peta satelit.

## Fitur

- **Peta neon, tanpa satelit.** Peta bawaan satu-satunya adalah OpenStreetMap bergaya neon (tema Purple atau Black Red).
  - Jika wilayahnya sudah diunduh, peta digambar dari file offline mapsforge dengan tema neon buatan sendiri (jalan induk cyan/magenta, jalan kecil violet, bangunan, nama jalan, SPBU).
  - Jika belum, ubin OSM online diubah menjadi duotone neon (daratan gelap, garis dan teks bernuansa aksen).
  - Ubin offline yang tidak punya data (misalnya Bali di dalam kotak batas peta Jawa) otomatis diganti ubin online.
- **Mode navigasi.** Rute mobil lewat OSRM (profil `driving`), peta mengikuti mobil dengan mobil di bagian bawah layar dan jalur di depannya, zoom menyesuaikan kecepatan (18, 17, 16, 15 dengan histeresis).
  - Kartu belokan: ikon panah, jarak ke belokan, instruksi berbahasa Indonesia, lencana kecepatan.
  - Bar tujuan: nama, jarak, waktu, tombol mulai dan hentikan pemanduan.
  - Hitung ulang rute otomatis jika posisi lebih dari 70 m dari rute dua kali berturut-turut.
- **Kompas yang dikalibrasi menurut laju mobil.** Peta berputar sehingga arah lurus mobil selalu ke atas; penunjuk U menandai arah utara. Tombol Kompas/Utara untuk beralih.
  - Di bawah 3 km/jam arah ditahan (tidak ikut derau GPS saat mobil diam atau merayap).
  - Saat dipandu dan posisi dekat jalur (kurang dari 40 m), arah diambil dari ruas rute sekitar 25 m di depan mobil: stabil dan tikungan terasa lebih awal.
  - Di luar itu dipakai arah GPS bila laju sedikitnya 5 km/jam.
  - Kecepatan putar mengikuti laju: pelan saat lambat (1,5 per detik, mencegah goyang) hingga cepat saat kencang (8 per detik pada 60 km/jam ke atas, mengikuti tikungan). Selisih kurang dari 1,5 derajat diabaikan di bawah 20 km/jam.
- **Cari tujuan dan SPBU terdekat.** Pencarian lewat Nominatim (OpenStreetMap), SPBU terdekat lewat Overpass; hasilnya langsung menjadi tujuan dan dipandu.
- **Halus.** Mosaik ubin disusun di latar belakang dan ditampilkan lewat jaring (mesh) GPU. Posisi mobil diinterpolasi antar fiks GPS, jadi gerak tetap mulus walau GPS hanya 1 Hz (sekitar 60 fps di uji).
- **Ikon mobil.** Mobil neon putih, berputar mengikuti arah.
- **Simulasi berkendara** untuk uji tanpa GPS, termasuk simulasi keluar jalur.

## Struktur

```
app/src/main/java/id/kahyangan/navneon/
  NavCardView.kt    tampilan kartu (Canvas): peta, kompas, kartu belokan, bar tujuan, kontrol
  NavEngine.kt      GPS, rute OSRM, pemanduan, hitung ulang, SPBU terdekat, simulasi
  PlaceSearch.kt    pencarian tempat (Nominatim)
  PumpRepo.kt       SPBU terdekat (Overpass)
  AppState.kt       data bersama (posisi, tujuan, rute, pemanduan)
  TileCache.kt      ubin OSM online + ubin offline
  OfflineMaps.kt    unduh dan gambar peta offline mapsforge (katalog peta-hu)
  RouteRepo.kt      permintaan rute dan langkah belokan (OSRM)
  ManeuverIcons.kt  ikon belokan
  Theme.kt          palet Purple dan Black Red
  Geo.kt, Http.kt   pembantu
  MainActivity.kt   contoh pemakaian + parameter uji
```

## Memakai di aplikasi lain

```kotlin
NavEngine.init(this)                 // sekali, mis. di onCreate
val card = NavCardView(this)         // taruh di layout seperti View biasa
setContentView(card)
NavEngine.startGps(this)             // butuh izin ACCESS_FINE_LOCATION

NavEngine.setDestination(AppState.Place("Lapangan Pangsar", "Ambarawa", -7.2666973, 110.4054526))
NavEngine.startGuidance()            // pemanduan belokan; NavEngine.stopGuidance() untuk berhenti
card.setTheme("red")                 // "purple" (bawaan) atau "red"
card.toggleCompass()                 // peta berputar / utara di atas
card.onSearch = { /* tampilkan kolom cari, lalu NavEngine.setDestination(...) */ }
NavEngine.findNearestPump()          // SPBU terdekat menjadi tujuan
```

`NavCardView` menggambar pada ruang desain setinggi 825,6 satuan dan menyesuaikan skala dengan tinggi View, sehingga cocok dipasang di layar mobil mana pun.

## Peta offline

Katalog dan format: [peta-hu](https://github.com/chabibachmad-dev/peta-hu) (mapsforge, data © OpenStreetMap, ODbL).
Salin file `.map` ke `Android/data/id.kahyangan.navneon/files/maps/` (mis. `java.map`), atau panggil `OfflineMaps.download("java")`.
File yang dipakai harus utuh; nama file sama dengan id wilayah (`java`, `sumatra`, `kalimantan`, `sulawesi`, `nusa-tenggara`, `maluku`, `papua`).

## Parameter uji (adb)

```
adb shell am start -n id.kahyangan.navneon/.MainActivity \
  --ed lat -7.261166 --ed lon 110.411939 \
  --ed dlat -7.2666973 --ed dlon 110.4054526 \
  --ez guide true --ez sim true --ei offroute 12 --ez red false
```

`--ed lat/lon` mengunci posisi, `dlat/dlon` menetapkan tujuan, `guide` memulai pemanduan, `sim` menjalankan simulasi berkendara,
`offroute N` menggeser mobil keluar jalur pada detik ke-N, `red` memakai tema Black Red.

## Build

```
gradlew assembleDebug
```

Butuh JDK 17 dan Android SDK 34. Dependensi dari Maven Central dan Google; mapsforge 0.25.0.

## Batasan

- Rute memakai server publik OSRM (cocok untuk pemakaian pribadi, tanpa jaminan), jadi rute butuh internet. Tidak ada panduan suara.
- Bundaran dan putar balik punya ikon dan teks, tetapi belum diuji di jalan nyata.
- Mode kompas memutar ubin raster; label nama jalan ikut berputar sehingga bisa terbalik saat menuju selatan.

## Lisensi dan atribusi

Data peta © kontributor OpenStreetMap (ODbL). Pustaka mapsforge berlisensi LGPL-3.0. Tampilkan atribusi OpenStreetMap di aplikasi
(sudah muncul pada kartu).
