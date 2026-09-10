# Chữ ký viết tay

Điểm vào: Home → Chữ ký để quản lý mẫu/chọn tài liệu; hoặc mở tài liệu → Ký.

## Tiêu chí nghiệm thu

- Vẽ nhiều nét; bỏ nét cuối, vẽ lại; chặn mẫu rỗng/tên trống, giới hạn tên 60 ký tự, 100 nét/8000 điểm.
- Lưu mẫu local, mở lại dùng được; xóa mẫu có xác nhận và không xóa chữ ký đã đặt trên tài liệu.
- Chọn trang, dùng mẫu hoặc nét vừa vẽ, kéo di chuyển và đổi kích thước trong biên trang.
- Lưu/hoàn tác/xóa chữ ký, nhắc khi rời màn hình có thay đổi chưa lưu; chặn đổi trang khi còn thay đổi chưa lưu.
- Xoay trang 0/90/180/270 độ: chữ ký đi cùng nội dung. Preview, thumbnail và PDF không lệch tọa độ.
- Gộp/trích xuất giữ chữ ký của các trang; nguồn ảnh vẫn bất biến.
- PDF xuất ghép chữ ký vào ảnh, không tạo annotation có thể gỡ; OCR vẫn đọc ảnh nguồn.
- Migration 3→4 giữ nguyên tài liệu/trang/OCR cũ; không dùng destructive migration.
- Lỗi database/hết dung lượng phải báo lỗi và không mất bản nguồn.
- Bàn phím không che nút lưu; bản nháp giữ khi đổi cấu hình màn hình. Chưa khôi phục bản nháp sau process death.

## Đã kiểm chứng ngày 2026-09-06

- `assembleDebug`, `lintDebug`, `git diff --check` đạt.
- Emulator Pixel 5 API 33 nâng database 2→3→4, giữ tài liệu cũ; `integrity_check = ok`, không có lỗi foreign key trên bản database sao chép cả WAL.
- Nhập ảnh thử, vẽ hai nét, lưu mẫu, đặt lên trang, phóng to và lưu thành công.
- Xoay trang 90°, xuất PDF thành công; render bằng macOS `sips` (không có Poppler), kiểm tra trực quan thấy chữ ký cùng hướng trang.
- Cài lại APK giữ dữ liệu, mở lại tài liệu đã ký, kéo chữ ký đưa state về chưa lưu.
- Chưa kiểm tra thiết bị thật, áp lực dung lượng, process death khi ghi, tất cả góc EXIF và tài liệu 100 trang có chữ ký.

Giới hạn hiện tại: một chữ ký trên mỗi trang, nét đen; không nhập ảnh chữ ký, không ký số bằng chứng thư. Chữ ký/mẫu đã lưu tồn tại local; bản nháp chưa lưu chỉ nằm trong ViewModel.
