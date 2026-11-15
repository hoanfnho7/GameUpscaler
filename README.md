# ⚡ GameUpscaler (Shizuku SSAA Resolution Scaler)

Ứng dụng Android siêu nhẹ giúp kích hoạt **SSAA (Supersampling Anti-Aliasing)** ở tầng hệ thống cho các tựa game (Call of Duty: Mobile, PUBG Mobile, Genshin Impact,...) thông qua quyền **Shizuku** mà **không cần Root máy**.

Được tối ưu hóa đặc biệt cho các dòng máy **iQOO / Vivo (OriginOS)** và tương thích hoàn toàn với mọi thiết bị Android khác.

---

## 🎯 Vấn đề giải quyết

Trên các thiết bị có màn hình độ phân giải cao (1.5K / 2K Ultra HD như iQOO Neo 11), hệ điều hành hoặc bản thân các tựa game thường tự động hạ độ phân giải dựng hình nội bộ (Internal Render Resolution) xuống mức 720p/1080p để giảm tải GPU. Khi khung hình thấp này bị kéo giãn lên tấm nền 2K vật lý sẽ gây ra hiện tượng **mờ nhòe và răng cưa (aliasing)** rất nặng ở các chi tiết xa.

**GameUpscaler** giải quyết triệt để bằng cách:
1. Tự động đọc độ phân giải vật lý (`Physical size`) và mật độ điểm ảnh (`Physical density`) gốc của thiết bị.
2. Nâng khung hình dựng (`wm size`) và DPI (`wm density`) lên theo tỷ lệ an toàn (**1.15x - 1.20x - 1.25x**), ép GPU render ở độ phân giải cao hơn thực tế rồi thu nhỏ lại (cơ chế khử răng cưa **SSAA** chất lượng cao nhất), đồng thời giữ nguyên kích thước giao diện hệ thống (UI không bị thu nhỏ).
3. Giữ nguyên cơ chế đồng bộ tần số quét tự nhiên của Game Space theo từng tựa game.
4. Tự động chạy ngầm theo dõi tiến trình game (`pidof`), ngay khi người dùng đóng game sẽ **tự động Reset màn hình về mặc định** để tiết kiệm pin.

---

## ✨ Tính năng nổi bật

- **Không cần Root / Không cần gõ lệnh Termux:** Giao diện trực quan Dark Mode, tự động quét danh sách ứng dụng và game có trên máy.
- **Tương thích mọi màn hình:** Tự động tính toán thông số pixel và DPI theo tỷ lệ màn hình thực tế của từng máy, không fix cứng thông số.
- **Giới hạn an toàn cho IC Cảm ứng (1.25x):** Đã tinh chỉnh mức trần `1.25x` (dưới ngưỡng 4000px chiều dài trên màn hình 2K) để đảm bảo `InputDispatcher` trên OriginOS nhận diện tọa độ cảm ứng chính xác 100%, không bị lệch tâm hay đơ cảm ứng.
- **Auto-Reset thông minh:** Chạy tiến trình theo dõi siêu nhẹ ở nền, tự trả màn hình về gốc sau 3 giây khi thoát game.
- **Nút Reset Khẩn Cấp:** Tích hợp nút cứu hộ ngay trên đầu ứng dụng để khôi phục độ phân giải gốc chỉ với 1 chạm.

---

## 🛠️ Yêu cầu hệ thống

1. Android 8.0 trở lên (Đã test ổn định trên **Android 14 - 17 / OriginOS**).
2. Đã cài đặt và kích hoạt ứng dụng **[Shizuku](https://github.com/RikkaApps/Shizuku)** (thông qua Gỡ lỗi không dây / Wireless Debugging).

---

## 🚀 Hướng dẫn sử dụng

1. Tải file `app-debug.apk` mới nhất trong mục **Releases** bên phải trang GitHub này.
2. Mở **Shizuku** và đảm bảo dịch vụ đang chạy (*Shizuku is running*).
3. Mở **iQOO Upscaler**, bấm **Cho phép (Allow)** khi bảng cấp quyền của Shizuku hiện lên.
4. Chọn mức nhân độ phân giải mong muốn:
   - **1.15x (Mát):** Khử răng cưa nhẹ, giữ nhiệt độ mát khi chơi ngoài trời không có quạt.
   - **1.20x (Vừa):** Cân bằng giữa độ nét và mức tiêu thụ pin.
   - **1.25x (Chuẩn):** Độ nét tối đa, triệt tiêu răng cưa mà vẫn giữ chuẩn tọa độ cảm ứng.
5. Gõ tìm tên game trong ô tìm kiếm và **chạm vào tên game** để bắt đầu. Khi chơi xong, chỉ cần vuốt đóng game khỏi đa nhiệm, màn hình sẽ tự động trở về bình thường sau 3 giây.

> **💡 Lưu ý cho người dùng OriginOS (iQOO / Vivo):**
> Để tránh việc hệ thống tự đóng băng Shizuku khi đang trong trận, hãy vào *Cài đặt -> Pin -> Quản lý tiêu thụ điện dưới nền*, chuyển **Shizuku** sang chế độ *Cho phép tiêu thụ điện năng cao dưới nền* và bật quyền *Khởi chạy liên kết (Associated startup)*.

---

## 🔨 Tự Build từ Source Code

Dự án sử dụng **GitHub Actions** để tự động biên dịch file APK mỗi khi có thay đổi:
1. Fork repository này về tài khoản của bạn.
2. Chuyển sang tab **Actions** -> Chọn workflow **Build APK** -> Bấm **Run workflow**.
3. Sau khoảng 1-2 phút, file APK mới sẽ tự động được xuất bản tại mục **Releases**.

Hoặc build trực tiếp trên máy tính qua dòng lệnh với lệnh: `./gradlew assembleDebug`

---

## 📜 Giấy phép (License)

Dự án mã nguồn mở phát hành dưới giấy phép MIT License.