# ⚡ GameUpscaler Pro

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="GameUpscaler Pro Logo" />
</p>

<p align="center">
  <b>Tăng độ nét game (SSAA 1.10x – 1.25x) & Thanh đo thông số phần cứng thời gian thực (FPS, Watt, Nhiệt độ) qua Shizuku</b><br />
  <i>Hoàn toàn miễn phí • Mã nguồn mở 100% • Không cần Root • Không quảng cáo</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%20%E2%80%93%2017-blue.svg?style=flat-square" alt="Android" />
  <img src="https://img.shields.io/badge/Root-Not%20Required-success.svg?style=flat-square" alt="No Root" />
  <img src="https://img.shields.io/badge/Powered%20By-Shizuku-green.svg?style=flat-square" alt="Shizuku" />
  <img src="https://img.shields.io/badge/License-MIT-orange.svg?style=flat-square" alt="MIT License" />
</p>

---

## 🇻🇳 Tiếng Việt

**GameUpscaler Pro** là công cụ tối ưu trải nghiệm chơi game trên Android, ứng dụng kỹ thuật **Super-Sampling Anti-Aliasing (SSAA)** qua **Shizuku** để nâng độ phân giải render ảo (1.10x – 1.25x). Nhờ đó, hình ảnh trong game trở nên sắc nét hơn rõ rệt, triệt tiêu răng cưa viền 3D mà vẫn giữ nguyên tỉ lệ cảm ứng chính xác. Ngoài ra, ứng dụng tích hợp thanh đo phần cứng nổi (HUD Telemetry) giúp bạn theo dõi FPS thực tế, công suất xả pin (Watt) và nhiệt độ từng linh kiện trong lúc combat.

### 📱 Giao diện 3 Tab Hiện đại

1. **📊 Tab 1: Giám sát hệ thống (System Monitor)**
   - Theo dõi nhanh trạng thái kết nối Shizuku (có nút mở nhanh Shizuku nếu chưa bật).
   - Thanh hiển thị dung lượng RAM đang dùng / còn trống trực quan.
   - Theo dõi nhiệt độ Pin, mức % pin, công suất sạc/xả tức thời (Watt).
   - Kiểm tra nhiệt độ SoC đỉnh và xung nhịp CPU.
   - Nút **Khôi phục màn hình gốc (Reset)** tức thì bất cứ lúc nào.
   - Dải game yêu thích mở nhanh (Pinned Games).

2. **⚡ Tab 2: Chọn độ phân giải (SSAA Upscaler & Kho Game)**
   - Hướng dẫn trực quan cơ chế SSAA cho người mới.
   - Thẻ so sánh độ phân giải thực tế: Gốc ➔ Sau khi kích hoạt.
   - 4 mức chọn độ nét định sẵn:
     - **1.10x**: Nhẹ nhàng, rất mát máy, siêu tiết kiệm pin.
     - **1.15x (Khuyên dùng)**: Cân bằng hoàn hảo giữa độ nét đồ họa và FPS mượt mà.
     - **1.20x**: Sắc nét cao cho máy tầm trung khá trở lên.
     - **1.25x**: Khử răng cưa tối đa cho máy flagship.
   - Tự động hoàn tác về độ phân giải gốc ngay khi bạn thoát game.
   - Tìm kiếm, quản lý game, ghim game yêu thích (`⭐`), đặt mức nét riêng cho từng game, và nhấn giữ để tạo lối tắt mở nhanh ngoài màn hình chính.

3. **🎛️ Tab 3: Tinh chỉnh thanh đo HUD (HUD Overlay Tuning)**
   - Khung giả lập xem trước thanh đo thực tế (Live Preview).
   - Nút bật thử nghiệm trực tiếp thanh đo nổi trên màn hình.
   - Tùy chỉnh hướng hiển thị: **Ngang 1 dòng** hoặc **Cột dọc**.
   - 4 kích cỡ chữ: **Siêu gọn**, **Nhỏ**, **Vừa**, **To**.
   - 4 độ trong suốt: **Nền đậm**, **Kính mờ**, **Siêu trong**, **Chỉ chữ**.
   - Tùy chỉnh nhịp quét cảm biến (150ms, 300ms, 1s, Auto).
   - 12 thông số phần cứng tùy chọn bật/tắt (FPS 0.1, Công suất Watt, VRAM đồ họa, Nhiệt độ Pin, SoC, CPU, GPU, RAM, Vỏ máy...).

---

### 🚀 Hướng dẫn cài đặt & Sử dụng (Dành cho người mới)

Chỉ cần 3 bước cực kỳ đơn giản:

#### Bước 1: Kích hoạt Shizuku
1. Cài đặt app **[Shizuku](https://shizuku.rikka.app)** từ Google Play hoặc GitHub.
2. Mở Shizuku và kích hoạt qua **Ghép nối không dây (Wireless Debugging)** hoặc cắm máy tính gõ 1 lệnh ADB (không cần Root).
> 💡 *Mẹo cho máy Xiaomi / Vivo / iQOO / Oppo:* Trong Tùy chọn nhà phát triển, nhớ bật thêm mục **Gỡ lỗi không dây (Cài đặt bảo mật)** để Shizuku có quyền điều chỉnh màn hình.

#### Bước 2: Cấp quyền cửa sổ nổi (Overlay)
- Mở **GameUpscaler Pro**, nếu bạn muốn dùng thanh đo FPS thì cấp quyền **Hiển thị trên ứng dụng khác** khi được hỏi.

#### Bước 3: Vào game và tận hưởng
- Chuyển sang Tab **⚡ Độ phân giải**, chọn mức nét mong muốn (khuyên dùng `1.15x`).
- Chạm vào game muốn chơi trong danh sách để khởi chạy!
- Khi vào game, thanh đo HUD sẽ tự động xuất hiện. Khi chơi xong thoát game, màn hình sẽ **tự động trở về độ phân giải gốc**.

---

## 🇺🇸 English

**GameUpscaler Pro** is a modern, lightweight Android utility designed to sharpen 3D game visuals (**SSAA 1.10x – 1.25x**) and display an in-game **Hardware Telemetry HUD** (FPS, Power Draw, Temperatures, VRAM) via **Shizuku**. Works seamlessly on **Android 8.0 – 17** across all device brands without requiring Root.

### ✨ Highlights & Architecture

- **100% Free Forever:** Zero ads, zero background tracking, no subscriptions.
- **SSAA Resolution Upscaler:** Smooths jagged edges and elevates rendering sharpness while maintaining touch calibration. Automatically restores native resolution upon exit.
- **3-Tab Modern Material UI:**
  - **📊 System Monitor:** At-a-glance telemetry (RAM usage, battery discharge Wattage, cell temperature, SoC peaks, quick reset).
  - **⚡ Upscaler & Library:** 4 resolution presets (1.10x – 1.25x), per-game custom scales, favorite pinning, and Home Screen shortcuts.
  - **🎛️ HUD Setup:** Live in-app preview, customizable horizontal/vertical layouts, 4 sizing scales, 4 transparency styles, and 12 hardware sensors.
- **Performance Logging:** Tap the floating HUD during gameplay to record real-time FPS and Wattage curves, then view interactive graphs in **Perf Insights**.
- **Control Center Quick Tile:** Toggle the telemetry HUD anytime directly from Quick Settings (`FPS HUD`).

---

### 🛠️ Quick Setup Guide

1. Install and activate **[Shizuku](https://shizuku.rikka.app)** on your phone.
2. Download the latest `app-release.apk` from the **Releases** tab.
3. Open **GameUpscaler Pro**, choose your SSAA factor (`1.15x` recommended), tap your game to launch, and enjoy!

---

### 🧹 Repo Maintenance (For Maintainer)

- **Clean Old Releases:** If GitHub Actions generated redundant release tags, go to the **Actions** tab ➔ select **Clean Old Releases** ➔ click **Run workflow** to automatically wipe old tags and keep only the latest release.
- **Normal Commits:** Regular code pushes now build APK artifacts for testing without flooding GitHub Releases. Official releases are generated on version tags (`v*`) or manual trigger.

---

## 📜 License

Distributed under the [MIT License](LICENSE).
