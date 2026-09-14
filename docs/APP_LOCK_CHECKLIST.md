# Checklist App Lock

## Acceptance criteria

- Bật khóa bằng PIN đúng 6 chữ số; PIN xác nhận khác bị từ chối và không bật khóa.
- Sau khi force-stop/mở lại app, nội dung Home/tài liệu không xuất hiện trước màn nhập PIN.
- PIN đúng mở khóa; PIN sai không mở khóa. Sau 5 lần sai, việc thử PIN bị chặn 30 giây và vẫn bị chặn sau khi Activity được tạo lại.
- Có thể đổi PIN sau khi nhập đúng PIN hiện tại; PIN cũ không còn mở khóa được.
- Có thể tắt App Lock sau khi nhập đúng PIN; mở app lần sau không yêu cầu xác thực.
- Các timeout Ngay lập tức, 1 phút, 5 phút và 15 phút khóa lại đúng khi app quay về foreground; xoay màn hình không bị coi là rời app.
- Trên thiết bị đã đăng ký sinh trắc học mạnh, việc bật tùy chọn yêu cầu một lần xác thực; mở khóa dùng BiometricPrompt gắn với khóa Keystore auth-per-use. Hủy/thất bại vẫn giữ màn khóa và cho dùng PIN.
- Khi thêm/xóa enrollment sinh trắc học, khóa biometric cũ bị vô hiệu hóa; ứng dụng yêu cầu PIN và bật lại tùy chọn thay vì chấp nhận enrollment mới.
- Xoay màn hình khi BiometricPrompt đang mở vẫn trả kết quả về Activity mới.
- Trên thiết bị không hỗ trợ/chưa đăng ký sinh trắc học, công tắc bị vô hiệu hóa và PIN vẫn hoạt động.
- Khi App Lock bật, Android không cho chụp màn hình và không hiển thị nội dung trong Recent Apps.
- Nhận PDF qua Sharesheet khi đang khóa chỉ điều hướng tới import sau khi người dùng mở khóa.

## Kiểm tra thủ công còn cần thiết bị/emulator

- Cold start, force-stop, process death, xoay màn hình và từng timeout.
- Sinh trắc học thành công, thất bại, hủy prompt và thay đổi enrollment trong Settings.
- 5 PIN sai, chờ hết delay, đổi PIN và tắt khóa.
- Screenshot/Recent Apps trên các Android version mục tiêu.

## Giới hạn

App Lock chỉ kiểm soát truy cập qua UI. Room, source image và PDF cache chưa được mã hóa. Nếu Android Keystore hoặc verifier không thể đọc, khóa fail-closed; người dùng cần khởi động lại và cuối cùng có thể phải xóa dữ liệu ứng dụng nếu Keystore của thiết bị bị hỏng không phục hồi được.
