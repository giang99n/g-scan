# Gộp và trích xuất trang

Điểm vào: Tài liệu → Gộp / trích xuất trang. Một nguồn tạo bản trích xuất; nhiều nguồn tạo bản gộp. Kết quả luôn là tài liệu mới trong thư viện.

## Tiêu chí nghiệm thu trên thiết bị

- Mở bàn phím khi nhập tên hoặc khoảng trang: thanh tạo tài liệu/thông báo lỗi nằm trên bàn phím, danh sách vẫn cuộn được. Đóng bàn phím không để lại khoảng trống kép; kiểm tra cả điều hướng cử chỉ và 3 nút. IME padding áp dụng cho toàn bộ Scaffold, Activity dùng `adjustResize`.
- Chọn hai tài liệu A/B, đổi thứ tự B lên trước A, tạo bản mới: số trang và thứ tự đúng; bản nguồn còn nguyên.
- Chọn một tài liệu 5 trang, nhập `1, 3-5`: bản mới có 4 trang theo thứ tự 1/3/4/5. Nhập `5, 1, 5`: có 2 trang theo thứ tự 5/1.
- Để trống khoảng trang: lấy toàn bộ. Chặn 0, trang vượt số trang nguồn, khoảng ngược, ký tự sai, số vượt giới hạn kiểu số, dấu phẩy thừa.
- Chặn tên trống, tên quá 120 ký tự, không chọn nguồn, tổng quá 100 trang. Cho phép tổng đúng 100.
- Trang xoay 90° và ảnh EXIF: preview và PDF xuất giữ đúng hướng. Xóa bản nguồn sau khi tạo: bản mới vẫn mở và xuất được.
- OCR tài liệu mới: chạy bình thường; nội dung mới nhận dạng tìm được trong thư viện. Không kế thừa kết quả OCR của bản nguồn.
- Hủy trong lúc copy: không tạo tài liệu dở dang; có thể sửa lựa chọn và thử lại. Nếu commit đã hoàn tất khi hủy, mở tài liệu đã tạo.
- Nguồn bị xóa, ảnh thiếu, hết dung lượng: báo lỗi; bản nguồn còn lại không bị ảnh hưởng. Khởi động lại không có metadata trỏ tới ảnh chưa copy xong.
- Xoay màn hình lúc chọn và lúc copy: lựa chọn/job giữ qua ViewModel. Process death trong copy: startup reconciliation dọn staging/orphan; không cam kết tự tiếp tục job.

## Kiểm chứng tự động

- `./gradlew assembleDebug lintDebug` thành công trên bản triển khai cuối.
- `git diff --check` không phát hiện lỗi whitespace.
- Không tạo/chạy unit test theo hướng dẫn dự án.
- Chưa kiểm tra thủ công: ADB không có thiết bị/emulator kết nối tại thời điểm triển khai.
