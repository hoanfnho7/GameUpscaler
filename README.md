# ⚡ GameUpscaler Pro (Shizuku SSAA & Hardware HUD)

Ứng dụng Android siêu nhẹ giúp kích hoạt khử răng cưa **SSAA (Supersampling Anti-Aliasing)** ở tầng hệ thống và hiển thị bảng theo dõi phần cứng thời gian thực (**Real-Time Hardware HUD**) cho các tựa game (Call of Duty: Mobile, PUBG Mobile, Genshin Impact,...) thông qua quyền **Shizuku** mà **không cần Root máy**.

Được tối ưu hóa chuyên sâu cho **Android 14 – 17** và các dòng máy **iQOO / Vivo (OriginOS)**.

---

## 📸 Hình ảnh thực tế

| Giao diện chính & Cấu hình | HUD đo thông số trong Game | Phím tắt Control Center |
| :---: | :---: | :---: |
| <img src="screenshots/main_ui.png" width="240" alt="Giao diện chính" /> | <img src="screenshots/hud_ingame.png" width="320" alt="HUD trong game" /> | <img src="screenshots/control_center.png" width="240" alt="Control Center Tile" /> |

---

## 🎯 Nguyên lý hoạt động

Trên các thiết bị màn hình độ phân giải cao (1.5K / 2K Ultra HD), nhiều tựa game tự động giới hạn độ phân giải dựng hình nội bộ (Internal Render Resolution) xuống mức 720p/1080p rồi kéo giãn lên tấm nền vật lý, gây mờ nhòe và răng cưa nặng ở vật thể xa.

**GameUpscaler Pro** khắc phục triệt để bằng cách:
1. Đọc trực tiếp thông số vật lý (`Physical size` và `Physical density`) thực tế của tấm nền.
2. Nâng khung hình dựng (`wm size`) và mật độ điểm ảnh (`wm density`) theo 4 mốc an toàn (**1.10x – 1.15x – 1.20x – 1.25x**), buộc engine game render ở độ phân giải cao hơn thực tế (SSAA) mà vẫn giữ nguyên tỷ lệ giao diện.
3. Giữ ngưỡng trần **1.25x** (dưới giới hạn 4000px chiều dài trên màn hình 2K) để đảm bảo IC cảm ứng (`InputDispatcher`) trên OriginOS nhận diện tọa độ chính xác 100%, không lệch tâm bắn.
4. Theo dõi tiến trình game ở nền (`pidof`) và **tự động Reset màn hình về mặc định** sau khi thoát game.

---

## ✨ Tính năng chính

### 1. Tùy chỉnh độ phân giải & Quản lý Game thông minh
- **4 mức SSAA linh hoạt:**
  - `1.10x` (Siêu nhẹ): Tăng độ nét nhẹ, tiết kiệm pin tối đa.
  - `1.15x` (Mát): Khử răng cưa tốt, giữ nhiệt độ ổn định.
  - `1.20x` (Vừa): Cân bằng giữa độ sắc nét và hiệu năng.
  - `1.25x` (Chuẩn): Độ nét tối đa, triệt tiêu răng cưa tầm xa.
- **Xem trước thông số thực tế:** Hiển thị trực tiếp độ phân giải và DPI gốc so với thông số sau khi Upscale ngay trên màn hình chính.
- **Cấu hình riêng từng Game (Per-Game Profile):** Bấm vào thẻ tỷ lệ trên từng dòng game để khóa cố định mức scale riêng cho từng trò (`🎯`).
- **Ghim & Lọc game yêu thích:** Bấm `⭐` để ghim game lên đầu và bật chế độ **Chỉ hiện Game đã ghim** để ẩn toàn bộ ứng dụng hệ thống.
- **Tạo Shortcut ngoài màn hình chính:** Nhấn giữ vào tên game để đẩy biểu tượng mở nhanh ra màn hình chính — chạm vào là tự kích SSAA ngầm và vào thẳng game.

### 2. Bảng đo phần cứng thời gian thực (12 Thông số & Cảm biến nhiệt)
Hỗ trợ bật/tắt độc lập từng chỉ số theo 3 nhóm chức năng:
- **Hiệu năng & Điện năng:**
  - `⚡ FPS (xx.x)`: Đo khung hình lẻ 1 chữ số thập phân theo thời gian thực qua `drm/measured_fps` và biến thiên từng layer của `SurfaceFlinger --timestats` (bắt chuẩn cả game 120/144Hz lẫn video YouTube/TikTok).
  - `🔌 Công suất`: Đo công suất tiêu thụ điện năng tức thời (Watt) không trễ qua `BatteryManager` HAL (tự phân biệt trạng thái xả pin và cắm sạc `⚡+`).
  - `🎮 VRAM`: Dung lượng bộ nhớ đồ họa GPU tiến trình game đang chiếm dụng qua `dumpsys gpu`.
  - `⚙️ Xung CPU`: Xung nhịp cao nhất hiện tại của các cụm nhân CPU (`GHz`/`MHz`).
- **Nhiệt độ lõi & Pin:**
  - `🔋 Pin °C`: Nhiệt độ viên pin chính xác từng `0.1°C` qua `BatteryManager`.
  - `🌡️ SoC °C`: Đỉnh nhiệt toàn chip (`max(CPU, GPU)`).
  - `🔥 CPU °C`: Đỉnh nhiệt cụm nhân xử lý CPU.
  - `📟 GPU °C`: Đỉnh nhiệt nhân đồ họa GPU.
- **Nhiệt độ linh kiện & Bộ nhớ:**
  - `🧬 DDR °C`: Nhiệt độ chip nhớ RAM (`DDR` / `LLCC`).
  - `📷 CAM °C`: Nhiệt độ cụm cảm biến Camera / OIS.
  - `📱 Vỏ máy °C`: Nhiệt độ khung vỏ / bo mạch (`skin` / `board`).
  - `💾 RAM`: Dung lượng RAM tiến trình game đang chiếm dụng (hoặc RAM hệ thống đã dùng).

### 3. Tùy biến HUD, Control Center & Kiến trúc tối ưu hiệu năng
- **4 mức kích thước & 2 bố cục:** Chuyển đổi nhanh giữa `Siêu gọn` • `Nhỏ` • `Vừa` • `To` (chạm trực tiếp vào thanh HUD để đổi cỡ nóng) và kiểu xếp **Ngang (`↔️`)** hoặc **Dọc (`↕️`)**.
- **Tách biệt tọa độ Dọc/Ngang & Reset nhanh:** Ghi nhớ vị trí riêng cho màn dọc và màn ngang (màn ngang mặc định nằm sát mép trên cùng tránh thanh điều hướng/đa nhiệm), kèm nút **`🎯 Reset vị trí`** trong app và thao tác kéo vào vòng tròn **`✕`** để ẩn nhanh.
- **Trình soi cảm biến (`🔍 Soi cảm biến`):** Quét và liệt kê toàn bộ `thermal_zone` cùng nhiệt độ thực tế đang hoạt động trên thiết bị.
- **Phím tắt Control Center (Quick Settings Tile):** Tích hợp nút **`HUD Đo FPS`** trên Control Center của OriginOS để bật/tắt nhanh thanh đo ở bất kỳ đâu; thông báo chạy nền im lặng (`IMPORTANCE_MIN`) tách riêng thao tác tắt HUD khỏi lệnh Reset độ phân giải.
- **Kiến trúc Zero-Fork Persistent Shell & Quét `300ms`:**
  - Giữ **1 tiến trình `sh` duy nhất** xuyên suốt phiên đo, cache sẵn đường dẫn cảm biến lúc khởi tạo và đọc bằng lệnh nội tại `read`/`printf` (không sinh tiến trình con mỗi nhịp).
  - Hỗ trợ 4 chế độ nhịp quét: **`⚡ Auto`** (400ms trong game / 800ms app thường), **`🚀 300ms`** (bắt đỉnh công suất và FPS theo mili-giây), **`Quét 1s`** và **`Quét 2s`**.
  - **Ngủ đông tuyệt đối khi tắt màn hình (`ACTION_SCREEN_OFF`):** Đóng băng 100% luồng đọc cảm biến khi khóa máy để tiết kiệm pin tối đa.
- **Cập nhật đè tiện lợi:** Sử dụng chữ ký số cố định (`upscaler.jks`) cho phép cài đè bản mới trực tiếp mà không cần gỡ app cũ hay cấp lại quyền Shizuku.

---

## 🛠️ Yêu cầu & Cấp quyền lần đầu

1. **Android 8.0 trở lên** (Đã tối ưu cho **Android 14 – 17 / OriginOS**).
2. Cài đặt và kích hoạt **[Shizuku](https://github.com/RikkaApps/Shizuku)** qua Gỡ lỗi không dây (Wireless Debugging).
3. **Lưu ý cho người dùng iQOO / Vivo (OriginOS):**
   - Trong *Cài đặt -> Pin*, cho phép **Shizuku** chạy nền không giới hạn để tránh bị ngắt kết nối giữa trận.
   - Trong *Cài đặt -> Ứng dụng -> iQOO Upscaler -> Quyền*, bật **Hiển thị trên ứng dụng khác**, **Hiển thị cửa sổ pop-up khi chạy ở nền** (để hiện HUD đo thông số) và **Lối tắt trên màn hình chính** (để tạo Shortcut game).

---

## 🔨 Tự động Build APK qua GitHub Actions

Dự án tích hợp sẵn workflow tự động biên dịch có bộ nhớ đệm Gradle (`actions/cache`) giúp xuất bản APK nhanh chóng:
1. Fork repository này về tài khoản GitHub của bạn.
2. Vào tab **Actions** -> Chọn **Build APK** -> Bấm **Run workflow**.
3. Tải file `app-debug.apk` mới nhất tại mục **Releases**.

---

## 📜 Giấy phép (License)

Phát hành dưới giấy phép MIT License.