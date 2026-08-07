# Kiến trúc GScan

## 1. Vì sao chọn kiến trúc này

App kiểu iScanner không chỉ có camera. Nó còn có crop/filter ảnh, OCR, quản lý nhiều trang, tạo PDF, chia sẻ, đồng bộ cloud và các job có thể chạy lâu. Nếu UI gọi thẳng CameraX, Room hoặc OCR SDK thì sau vài feature code sẽ khó test và khó thay SDK.

GScan dùng **feature-first + Clean Architecture thực dụng + MVVM/UDF**:

- **Feature-first** gom code theo nghiệp vụ (`documents`, `scanner`, `editor`, `ocr`, `export`) để một thay đổi ít chạm sang feature khác.
- **Clean Architecture** giữ nghiệp vụ không phụ thuộc framework. `domain` chỉ biết model, repository interface và use case.
- **MVVM/UDF** tạo một chiều dữ liệu: UI gửi event → ViewModel gọi use case → repository cập nhật Room → `Flow` phát state mới → UI render lại.
- **Local-first** giúp tài liệu vẫn dùng được khi offline; Room là single source of truth, cloud chỉ đồng bộ với local.

Liên hệ Flutter:

| Android/Kotlin | Flutter tương đương |
|---|---|
| Composable | Widget |
| ViewModel + StateFlow | Riverpod Notifier/Bloc + state |
| Hilt | Provider/Riverpod container |
| Use case | Service/interactor |
| Room DAO | Drift/Isar datasource |
| Repository interface | Repository pattern trong Dart |
| Navigation Compose | go_router |

## 2. Dependency rule

```text
presentation  ───────► domain ◄─────── data
     │                    ▲               │
     └── Android UI       │               └── Room / Camera / OCR / API
                          │
                    pure Kotlin
```

`domain` không import Android, Room, CameraX hay ML Kit. Hilt module ở composition root nối `DocumentRepository` với `OfflineDocumentRepository`, tương tự việc override một Riverpod provider bằng implementation thật.

## 3. Cấu trúc hiện tại

```text
com.example.gscan/
├── app/                         # Navigation graph, app shell
├── core/
│   ├── database/                # Room database, DAO, entity
│   └── designsystem/            # Theme và component dùng chung
├── di/                          # Composition root của Hilt
└── feature/
    ├── documents/
    │   ├── data/                # Mapper + repository implementation
    │   ├── domain/              # Model, repository contract, use case
    │   └── presentation/        # Screen, UiState, ViewModel
    └── scanner/
        └── presentation/        # Điểm vào cho camera pipeline
```

Hiện tại project giữ **một Gradle module `:app`** nhưng ranh giới package đã rõ. Đây là điểm cân bằng tốt cho giai đoạn đầu: build nhanh, refactor ít, không có nhiều boilerplate. Khi team lớn hoặc build chậm, có thể tách lần lượt thành `:core:database`, `:core:designsystem`, `:feature:scanner`, `:feature:documents` mà không phải đổi business API.

Không tạo class `BaseViewModel`, `BaseRepository` hay `BaseUseCase` nếu chúng chưa mang hành vi thật; abstraction chỉ được thêm khi có ít nhất hai use case rõ ràng.

## 4. Các feature nên triển khai

1. **scanner**: CameraX preview/capture, phát hiện biên, perspective correction. Camera chỉ tạo file ảnh tạm và trả về `CapturedPage`; không tự ghi database.
2. **editor**: crop, rotate, reorder, filter ảnh. Lưu thao tác chỉnh sửa dưới dạng metadata để có thể render lại, tránh ghi đè ảnh gốc.
3. **documents**: quản lý document/page, tag, search, trash. Đây là feature local-first và là nguồn state cho màn hình home.
4. **ocr**: nhận dạng text theo từng page, lưu text và ngôn ngữ. SDK OCR nằm ở `data`, domain chỉ thấy `TextRecognizer` interface.
5. **export**: tạo PDF/JPEG, quality/compression, watermark và share qua `FileProvider`.
6. **sync**: upload file + metadata, conflict resolution, retry. Chỉ thêm khi backend/cloud thực sự xuất hiện.
7. **subscription/settings**: entitlement, giới hạn export/OCR, cấu hình chất lượng.

## 5. Pipeline scan đề xuất

```text
CameraX capture
   → lưu ảnh gốc trong app storage
   → detect 4 góc / perspective correction
   → tạo preview nhẹ cho UI
   → user crop, filter, reorder
   → transaction lưu Document + Pages vào Room
   → enqueue OCR/PDF bằng WorkManager
   → cập nhật trạng thái DRAFT → PROCESSING → READY/FAILED
```

Việc nặng, cần tiếp tục khi app ra background (OCR hàng loạt, export, upload) dùng **WorkManager**. Tác vụ chỉ cần sống cùng màn hình dùng coroutine trong `viewModelScope`. Không dùng WorkManager cho camera preview hoặc filter realtime.

Mỗi job phải có unique work name theo document để tránh enqueue trùng, lưu progress để UI quan sát, và chỉ lưu URI/path trong input data; không nhét bitmap vào WorkManager/Bundle.

## 6. Data model mục tiêu

```text
documents(id, title, status, created_at, updated_at, deleted_at)
pages(id, document_id, position, original_uri, processed_uri, crop_points, filter)
ocr_results(page_id, text, language, updated_at)
exports(id, document_id, type, output_uri, status, created_at)
sync_queue(id, aggregate_id, operation, retry_count, state)
```

File ảnh/PDF nằm trong app storage; database chỉ giữ URI/path và metadata. Một document xóa mềm trước, sau đó cleanup worker mới xóa file để có thể phục hồi và tránh orphan file khi transaction lỗi.

## 7. Quy ước state và lỗi

- Mỗi screen có một immutable `UiState`; UI không giữ business state bằng `remember`.
- Event một lần như snackbar/navigation đi qua event channel riêng, không nhét vào state lâu dài.
- Data layer map exception của SDK/network thành lỗi của domain; UI không kiểm tra `SQLiteException` hay exception của ML Kit.
- Repository trả `Flow` cho dữ liệu quan sát liên tục và `suspend` cho command.
- Mọi write liên quan Document + Pages dùng Room transaction.

## 8. Khi nào tách multi-module

Tách module khi có một trong các dấu hiệu: nhiều developer cùng sửa một feature, build incremental chậm rõ rệt, cần enforce dependency bằng Gradle, hoặc feature có thể tái sử dụng. Thứ tự tách an toàn:

1. `:core:model`, `:core:database`, `:core:designsystem`
2. `:feature:documents`
3. Các feature nặng như `:feature:scanner`, `:feature:ocr`, `:feature:export`
4. `:app` chỉ còn navigation và wiring

Không dùng dynamic feature module cho scanner ở giai đoạn đầu; camera là luồng chính và người dùng cần mở ngay.

## 9. Lộ trình kỹ thuật

- **M1:** Documents + Pages schema, CameraX capture, permission, file storage.
- **M2:** Crop/perspective/filter và editor nhiều trang.
- **M3:** WorkManager pipeline cho OCR + PDF, progress/retry.
- **M4:** Search OCR, share/export, trash/cleanup.
- **M5:** Auth/subscription/cloud sync nếu product cần.

Mỗi milestone nên có unit test cho use case/mapper, Room migration test và ít nhất một Compose UI test cho happy path.
