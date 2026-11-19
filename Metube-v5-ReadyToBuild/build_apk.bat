@echo off
echo Memulai kompilasi Metube Debug APK (ARM64)...
call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo Gagal melakukan build APK. Pastikan JDK 17 dan Android SDK sudah terpasang.
    pause
    exit /b %errorlevel%
)
echo Build selesai! Lokasi APK:
echo app\build\outputs\apk\debug\app-debug.apk
pause
