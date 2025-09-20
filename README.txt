
# Deep3DAppStarter
- Aç: Android Studio → File > Open → bu klasörü seç
- MainActivity: Bluetooth cihaz adını yaz → Realtime veya Grid ekranına geç
- Realtime: Bağlan → Kalibrasyon → Başlat (checkbox ile şimdilik "yürüyüş"ü simüle edebilirsin)
- Grid: X,Y gir; Hücre (m); Başlat → adım atarken otomatik doldurur

**Önemli:** `RfcommBtSource.readC()` içindeki paket çözmeyi cihazın gerçek formatına göre uyarlayın.
Şu an örnek olarak her iki byte'tan `int16` okur. Cihazınız farklıysa burayı düzenleyin.
