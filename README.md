# GScan

Ứng dụng Android Kotlin scan tài liệu theo hướng local-first.

## Stack

- Jetpack Compose + Material 3
- MVVM/UDF với `ViewModel`, `StateFlow`
- Feature-first Clean Architecture (`data/domain/presentation`)
- Hilt cho dependency injection
- Room làm nguồn dữ liệu local duy nhất (single source of truth)
- Navigation Compose
- ML Kit Document Scanner
- App-owned file storage với atomic copy

## Luồng đang hoạt động

```text
ML Kit Scanner → JPEG URI tạm → app-owned storage
    → transaction Room Document + Pages
    → Library/editor nhiều trang → xuất, Save As hoặc share PDF
```

Flow scanner chạy on-device qua Google Play services và không yêu cầu GScan khai báo quyền `CAMERA`. Lần chạy đầu có thể cần mạng để tải module.

PDF được tạo hoàn toàn local, encode tuần tự từng trang theo thứ tự/góc xoay đã lưu để giới hạn RAM. App hỗ trợ ba preset độ phân giải/JPEG quality, hủy khi đang tạo, Save As bằng Storage Access Framework và chia sẻ bằng `FileProvider`.

## Chạy project

```bash
./gradlew assembleDebug
```

Mục tiêu và feature map sản phẩm: [docs/PROJECT_GOAL.md](docs/PROJECT_GOAL.md).

Kiến trúc và thứ tự dependency kỹ thuật: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
