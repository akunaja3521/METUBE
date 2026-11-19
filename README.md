# Metube Android App (v5)

Pemutar video lokal & streaming resmi untuk Android (Target: Samsung Galaxy A05s, ARM64, Min SDK 26, Target SDK 35).

## Cara Mengubah Source Code Ini Menjadi File APK

### Metode 1: Tanpa Instal Android Studio (Via GitHub Actions)
1. Buat repository baru di GitHub (bisa Private).
2. Unggah seluruh isi folder proyek ini ke repository tersebut.
3. Buka tab **Actions** di GitHub, jalankan workflow **Build Metube Android APK**.
4. Setelah selesai (~2-3 menit), unduh file **Metube-v5-arm64-debug-apk** (`app-debug.apk`) dan pindahkan ke HP Samsung Galaxy A05s untuk diinstal.

### Metode 2: Menggunakan Android Studio
1. Buka folder `Metube-v5-ReadyToBuild` di Android Studio.
2. Tunggu Gradle Sync selesai.
3. Pilih menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Ambil file APK di `app/build/outputs/apk/debug/app-debug.apk`.
