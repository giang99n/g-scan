# QR/barcode

Điểm vào: Home → QR & barcode.

## Tiêu chí nghiệm thu

- Quét QR text/URL/Wi-Fi/liên hệ và mã EAN-13/Code128: hiển thị đúng raw text, format, loại nội dung.
- Hủy camera trở lại màn QR, quét lại được; không tạo bản ghi khi hủy hoặc lỗi. Không mở đồng thời hai phiên quét.
- Thiếu/cũ Google Play services hoặc module chưa tải: có thông báo, thử lại sau khi khắc phục.
- Sau lần tải module thành công, thử quét khi offline trên thiết bị có GMS.
- Sao chép, chia sẻ đúng nội dung. Link không tự mở; HTTP(S) phải xác nhận. Các scheme intent/file/javascript/tel và URL có user-info không có nút mở.
- QR có scheme `HTTP://`, `HTTPS://` hoặc pha hoa/thường: chuẩn hóa riêng scheme khi mở, không thay đổi path/query/fragment hay raw text đã lưu.
- Sao chép bằng nút, menu bôi đen → Copy và bàn phím đều gắn cờ sensitive; Android 13+ không hiển thị raw text nhạy cảm trong clipboard preview. Áp dụng cả kết quả và dialog URL.
- Lịch sử mở lại sau khởi động app; chọn mục cuộn lên kết quả đầy đủ. Xóa một mục/toàn bộ có xác nhận, cập nhật cả kết quả đang mở.
- Lỗi lưu: vẫn thấy kết quả, có sao chép/chia sẻ/thử lưu lại. Retry cùng kết quả không tạo bản ghi trùng. Nhắc trước khi rời màn hình nếu chưa lưu.
- Migration 4→5 giữ Document/Page/OCR/mẫu chữ ký cũ; không destructive migration.
- Không thêm quyền CAMERA, không lưu ảnh camera và không log payload.

Giới hạn: camera qua Google Play services, chưa đọc từ gallery; mã nhị phân không có rawValue báo chưa hỗ trợ. Wi-Fi/liên hệ hiển thị nội dung để sao chép, chưa tự kết nối hoặc nhập danh bạ. Kết quả chưa lưu không phục hồi sau process death.

## Kiểm chứng ngày 2026-09-07

- `./gradlew assembleDebug lintDebug` và `git diff --check` đạt; schema Room 5 được xuất.
- Manifest hợp nhất có `barcode_ui`, không có quyền CAMERA.
- Emulator Pixel 5 API 33: vào được màn QR, lịch sử rỗng và thông báo lỗi module hiển thị đúng; bấm quét lại được sau lỗi.
- Database emulator lên v5, `integrity_check = ok`, không có lỗi foreign key, tài liệu cũ vẫn còn; bảng `barcode_history` đúng cột/type/primary key.
- Chưa kiểm chứng luồng quét thành công, sao chép/chia sẻ kết quả và lịch sử với mã thực: Play services trên emulator báo `No successful module downloads for requested features [mlkit.barcode.ui]`, thử lại vẫn chưa mở scanner. Cần thiết bị có GMS/module sẵn sàng để hoàn tất các bước này; không coi trạng thái lỗi đã kiểm tra là bằng chứng nhận dạng mã thành công.
- Không thêm/chạy unit test theo hướng dẫn dự án. Chữ ký giữ pending, không sửa các issue chữ ký trong thay đổi này.

## Sửa sau review

- Chuẩn hóa scheme URL bằng Locale.ROOT trước khi mở; raw text/path/query/fragment không đổi.
- `SensitiveSelectionContainer` bọc LocalClipboard của Compose, gắn cờ sensitive trước khi ghi. Nút Sao chép dùng cùng helper; xử lý lỗi clipboard và giữ nguyên cancellation.
- `assembleDebug lintDebug` và `git diff --check` đạt sau sửa. Chưa xác minh lại clipboard preview trên thiết bị: emulator-5554 không còn kết nối khi thử cập nhật APK.
