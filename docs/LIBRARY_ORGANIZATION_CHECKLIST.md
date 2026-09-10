# Checklist Favorite, folder, tag và batch selection

## Acceptance criteria

- Tài liệu cũ sau migration 6→7 vẫn mở được, mặc định không yêu thích và chưa phân loại.
- Có thể đánh dấu/bỏ đánh dấu từng tài liệu và lọc chỉ các tài liệu yêu thích.
- Có thể tạo, đổi tên và xóa folder một cấp; tên không trống, tối đa 40 ký tự và không trùng không phân biệt hoa/thường.
- Có thể di chuyển một hoặc nhiều tài liệu vào folder hoặc về “Chưa phân loại”. Xóa folder không xóa tài liệu.
- Có thể tạo, đổi tên và xóa tag; một tài liệu có nhiều tag. Xóa tag chỉ gỡ liên kết.
- Search Library tìm theo tên tài liệu, tên tag hoặc nội dung OCR; có thể kết hợp lọc folder, favorite và tag.
- Long press bật selection mode; có chọn tất cả, favorite/bỏ favorite, di chuyển, cập nhật tag và đưa vào thùng rác.
- Batch tag hiển thị checked/unchecked/indeterminate; chỉ tag người dùng đổi mới được thêm hoặc gỡ trên toàn bộ selection.
- Dialog quản lý giữ nguyên input khi lỗi, hiển thị lỗi inline, khóa tương tác lúc ghi và chỉ reset form sau khi Room trả thành công.
- Empty state của search/filter có hành động xóa toàn bộ bộ lọc; hành động phụ của Library/document nằm trong menu có nhãn.
- Batch mutation chỉ thành công khi toàn bộ document còn active và được commit trong một Room transaction.
- Các màn dùng Library để chọn tài liệu cho OCR, chữ ký hoặc gộp không bật organization/batch selection.

## Đã kiểm tra

- `assembleDebug`, `lintDebug` và `git diff --check` đạt.
- Schema Room v7 đã export; migration 6→7 được chạy thủ công trên SQLite với một document cũ, giữ record và không có lỗi foreign key.
- Cài đè APK lên emulator API 33 có dữ liệu legacy: app mở Library thành công, document cũ còn hiển thị.
- Trên emulator: đánh dấu Favorite, bật bộ lọc Favorite, tạo folder và long press để mở batch selection thành công.
- Trên emulator sau UX fix: menu Library/card gọn lại; tạo folder reset form sau thành công; tên folder trùng giữ nguyên input và hiển thị lỗi inline.

## Chưa kiểm tra thủ công

- Đổi tên/xóa folder và tag qua toàn bộ dialog xác nhận.
- Batch di chuyển, đặt tag, bỏ favorite và đưa nhiều document vào thùng rác với nhiều hơn một document thật.
- Process death hoặc thiếu dung lượng trong lúc metadata đang được cập nhật.
