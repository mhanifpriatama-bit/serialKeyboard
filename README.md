# Serial Keyboard

Aplikasi Android yang membaca data dari perangkat USB serial (Arduino, ESP32, scanner barcode serial, timbangan, dll.) dan mengetikkannya sebagai input keyboard ke aplikasi mana pun.

## Cara kerja

Aplikasi ini adalah keyboard (Input Method Service). Saat dipilih sebagai keyboard aktif, layanan membuka port USB serial lewat kabel OTG, membaca datanya, lalu memasukkan teks ke kolom input yang sedang fokus. Cara ini tidak membutuhkan izin Accessibility.

## Build

1. Buka folder ini di Android Studio (Koala atau lebih baru, JDK 17).
2. Tunggu Gradle sync selesai. Jika wrapper belum ada, jalankan `gradle wrapper --gradle-version 8.7` di folder proyek.
3. Colokkan HP lewat USB debugging lalu tekan Run.

Library yang dipakai: [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) (lewat JitPack). Mendukung CDC/ACM, FTDI, CP210x, CH340/CH341, dan PL2303.

## Pemakaian

1. Buka aplikasi, tekan **Aktifkan keyboard**, lalu nyalakan *USB Serial Keyboard*.
2. Atur baud rate, data bits, stop bits, dan parity sesuai perangkat, lalu tekan **Simpan**.
3. Colokkan perangkat serial lewat kabel OTG.
4. Ketuk kolom input di aplikasi mana pun, lalu pilih *USB Serial Keyboard* (tombol **Pilih keyboard** atau ikon keyboard di bilah navigasi).
5. Izinkan akses USB saat diminta. Data serial akan diketik otomatis.

Untuk kembali mengetik biasa, tekan **Ganti keyboard** di panel keyboard ini.

## Mode pengetikan

- **Per baris**: teks dikumpulkan sampai ada `\n` atau `\r`, lalu diketik sekaligus. Anda bisa menambahkan Enter atau Tab setelah tiap baris.
- **Langsung per karakter**: setiap byte yang masuk langsung diketik. `\n` menjadi tombol Enter dan `\t` menjadi Tab.

## Catatan

- Izin USB diminta ulang setiap perangkat dicolok ulang.
- Teks didekode sebagai UTF-8. Untuk data ASCII (barcode, sensor) tidak ada masalah.
- Perubahan pengaturan diterapkan otomatis dalam beberapa detik, tanpa memutus keyboard.
