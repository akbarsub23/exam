# 📱 Exambro CBT — SMK Negeri 1 Gempol
### Build otomatis via GitHub Actions · Download APK langsung dari GitHub

---

## 🚀 Cara Upload ke GitHub & Build APK Otomatis

### LANGKAH 1 — Buat Repository GitHub
1. Buka https://github.com → login → klik **"New"**
2. Nama: `exambro-cbt-smkn1gempol` → pilih **Public** → **Create repository**

---

### LANGKAH 2 — Upload Project

**Cara termudah (tanpa install Git):**
1. Di halaman repo baru, klik **"uploading an existing file"**
2. Extract ZIP project ini → drag & drop seluruh isi folder ke GitHub
3. Klik **"Commit changes"**

**Atau via Git di PC:**
```bash
git init
git add .
git commit -m "Exambro CBT SMKN1 Gempol"
git branch -M main
git remote add origin https://github.com/USERNAME/exambro-cbt-smkn1gempol.git
git push -u origin main
```

---

### LANGKAH 3 — Tunggu Build Selesai
1. Buka tab **"Actions"** di repo
2. Lihat workflow **"Build APK"** berjalan ⏳
3. Tunggu ~5-10 menit → ✅ selesai

---

### LANGKAH 4 — Download APK
1. Buka tab **"Releases"** di sidebar repo
2. Download file `.apk` dari versi terbaru
3. Bagikan link ke siswa via WhatsApp / Google Drive

> Setiap kali ada perubahan dan di-commit, APK baru otomatis ter-build!

---

### ⚠️ PENTING: Gradle Wrapper JAR

File `gradle/wrapper/gradle-wrapper.jar` tidak disertakan di ZIP.
Cara mendapatkannya (lakukan sekali):

**Opsi A — Dari Android Studio:**
1. Buat project Android baru di Android Studio
2. Copy `gradle/wrapper/gradle-wrapper.jar` ke project ini
3. Commit & push ke GitHub

**Opsi B — Via terminal (jika Gradle terinstall):**
```bash
gradle wrapper --gradle-version 8.2
```

---

## ⚙️ Ganti URL Server
Edit `app/src/main/java/.../ui/LandingActivity.kt`:
```kotlin
Network("Jaringan Lokal",  "http://192.168.1.100",        "192.168.1.100"),
Network("Jaringan Online", "https://lms.semakinpol.my.id","lms.semakinpol.my.id")
```

---

## 📲 Alur Siswa
```
Download APK → Install → Buka app → Pilih jaringan → Masuk Ujian ✅
```
HP terkunci otomatis. Keluar bebas via tombol "Keluar Ujian".

---
*SMK Negeri 1 Gempol · Pasuruan*
