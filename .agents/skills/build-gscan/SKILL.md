---
name: build-gscan
description: Phát triển, sửa lỗi, review và chọn tính năng cho GScan — ứng dụng Android Kotlin/Compose hướng tới triển khai nhiều nhất có thể các chức năng kiểu iScanner mà không vận hành backend riêng và không tự huấn luyện model AI. Dùng khi làm scanner/import, quản lý tài liệu, OCR/search, PDF/image editor, annotation, signature, merge/split/compress, security, backup/provider integration, QR/measure/count/math/AI tools, Room, local storage, ViewModel hoặc kiến trúc GScan.
---

# Build GScan

## Đọc nguồn sự thật

1. Đọc `docs/PROJECT_GOAL.md` để biết feature map, ràng buộc backend/model và Definition of Done.
2. Đọc `docs/ARCHITECTURE.md` trước khi đổi data flow, storage, database, dependency hoặc package.
3. Kiểm tra code, cấu hình build và trạng thái Git; bảo toàn thay đổi không liên quan của người dùng.

Nếu tài liệu mâu thuẫn, ưu tiên yêu cầu mới nhất được người dùng nói rõ và cập nhật tài liệu trong cùng thay đổi.

## Tối đa hóa tính năng trong hai ràng buộc

- Không dùng phase, POC hay MVP để từ chối hoặc trì hoãn một tính năng.
- Triển khai bất kỳ tính năng kiểu iScanner nào có thể chạy local/on-device hoặc qua Android, SDK/model pre-trained, provider/BaaS do bên thứ ba vận hành mà không cần GScan vận hành backend.
- Không tự thu thập data, train, fine-tune, serve hoặc maintain model AI riêng.
- Không nhúng API secret dùng chung trong APK. Với provider cloud, dùng flow mobile an toàn như OAuth + PKCE và tài khoản người dùng.
- Không xây cloud riêng, web portal, realtime collaboration, expiring/revocable link hoặc remote logout vì chúng cần server state/authorization của GScan.
- Không sao chép thương hiệu, giao diện hoặc tài sản độc quyền của iScanner.

Khi nhận một feature, phân loại thành:

1. Local/on-device — triển khai đầy đủ.
2. Client-only qua platform/provider — triển khai nếu credential, privacy và license an toàn.
3. Cần backend riêng hoặc model riêng — không triển khai theo cách đó; tìm giải pháp local/pre-trained trước, rồi nêu giới hạn nếu không có giải pháp phù hợp.

## Chọn lát cắt

- Viết acceptance criteria kiểm tra được.
- Ưu tiên feature có giá trị cao hoặc mở khóa nhiều feature khác, không dựa vào nhãn giai đoạn.
- Bám vào vertical slice hoàn chỉnh; không scaffold package/bảng/abstraction chỉ để chuẩn bị.
- Theo dependency mặc định: scan/import → app-owned storage + Document/Page → library/editor → OCR/search → PDF/sign/security → integration/tools.
- Cho phép làm feature độc lập như QR, print, app lock bất cứ lúc nào.

## Giữ ranh giới code

- Đặt Compose, `UiState`, event và ViewModel trong `presentation`.
- Đặt model nghiệp vụ, repository contract và use case trong `domain`; không import Android, Room, ML Kit, OpenCV hoặc PDF SDK vào domain.
- Đặt Room, storage, scanner/OCR, PDF và provider adapters trong `data` hoặc `core` phù hợp.
- Nối implementation qua Hilt tại composition root.
- Giữ Room làm single source of truth cho metadata/index local; giữ file lớn trong app-owned storage.

```text
UI event → ViewModel → use case → repository → Room/file/SDK/provider → Flow/UiState
```

## Bảo vệ dữ liệu và hiệu năng

- Copy result URI cần sở hữu vào app storage; không giả định URI tạm tồn tại lâu dài.
- Lưu Document + Pages trước; OCR, export, backup và integration chạy độc lập, có status/error riêng.
- Dùng transaction cho thay đổi Document + Pages và có rollback/cleanup cho file operation.
- Coi source page đã copy là bất biến; edit bằng metadata/operation hoặc derived output.
- Không truyền bitmap/file bytes qua navigation, Bundle hoặc WorkManager input.
- Không chạy I/O, OCR, PDF hoặc xử lý ảnh nặng trên main thread.
- Dùng coroutine cho job nhỏ gắn với màn hình; WorkManager cho job phải sống qua background/process death.
- Decode theo target size, stream page-by-page và kiểm tra thủ công với tài liệu lớn.
- Không coi `PdfDocument` là công cụ merge/split/password/searchable PDF/compression đầy đủ; chọn thư viện có năng lực và license phù hợp.
- Redaction phải flatten/xóa content nhạy cảm; overlay có thể gỡ không được xem là redaction.

## Security và integration

- Chia sẻ bằng content URI, `FileProvider` và quyền đọc tạm thời; không lộ raw path.
- Dùng Android Keystore, BiometricPrompt và crypto/PDF library chuẩn; không tự thiết kế crypto.
- Giữ Auto Backup tắt tới khi Room và files restore nhất quán; ưu tiên explicit backup archive.
- Dùng OAuth mobile flow phù hợp và secure token storage cho provider.
- Đánh giá privacy, license, app-size, device support và offline behavior trước khi thêm SDK/model pre-trained.

## State và lỗi

- Dùng immutable `UiState` cho mỗi screen và effect riêng cho navigation/snackbar.
- Biểu diễn loading, content/empty, cancellation, unavailable/unsupported, storage full và recoverable error khi liên quan.
- Map exception SDK/storage/provider thành lỗi app có ý nghĩa.
- Không để lỗi OCR/export/backup làm document đã lưu trở thành không mở được.

## Kiểm chứng và bàn giao

1. Không tạo hoặc chạy unit test trừ khi người dùng yêu cầu rõ trong task cụ thể.
2. Chạy formatter/analyzer nếu project có và ít nhất `assembleDebug` để xác nhận compile/package.
3. Khi đổi Room, tạo migration an toàn và xác minh schema/build; không dùng destructive migration.
4. Kiểm tra thủ công happy path, lỗi quan trọng và ghi rõ phần cần thiết bị thật.
5. Đối chiếu `docs/PROJECT_GOAL.md`: feature không cần backend GScan hoặc model GScan tự train.
6. Cập nhật goal khi capability/ràng buộc thay đổi; cập nhật architecture khi data flow/schema/dependency đổi.
7. Bàn giao phần đã làm, build/check đã chạy, giới hạn provider/device/license và việc chưa xác minh.
