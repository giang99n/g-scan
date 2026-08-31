# Kiến trúc GScan

## 1. Vì sao chọn kiến trúc này

GScan hướng tới bộ công cụ scan/PDF phong phú nhưng không có backend riêng. Vì scanner, OCR, PDF, storage, biometric và provider cloud đều có thể thay đổi độc lập, UI không được gọi thẳng SDK.

GScan dùng **feature-first + Clean Architecture thực dụng + MVVM/UDF + local-first**:

- Feature-first gom code theo nghiệp vụ để thêm nhiều công cụ mà không tạo một package `utils` khổng lồ.
- Domain giữ business rule độc lập Android/SDK.
- MVVM/UDF tạo luồng UI event → ViewModel → use case → repository → Flow → UI.
- Room và app-owned files là nguồn dữ liệu local; integration bên ngoài chỉ đọc/ghi qua adapter.

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
     └── Compose/UI       │               └── Room / file / ML Kit / PDF / provider
                          │
                     pure Kotlin
```

- `domain` không import Android, Room, ML Kit, OpenCV hay PDF SDK.
- Data adapter map lỗi SDK/storage/provider thành error của app.
- Hilt module tại composition root nối contract với implementation, tương tự override Riverpod provider.
- Không tạo `BaseViewModel`, `BaseRepository`, `BaseUseCase` hoặc multi-module nếu chưa giải quyết vấn đề thật.

## 3. Cấu trúc package mục tiêu

```text
com.example.gscan/
├── app/                         # Navigation, app shell
├── core/
│   ├── database/                # Room, transaction
│   ├── storage/                 # Atomic copy, URI, cleanup, encryption
│   ├── designsystem/            # Theme, shared UI
│   └── common/                  # Error/coroutine abstractions thật sự dùng chung
├── di/                          # Composition root
└── feature/
    ├── scanner/                 # ML Kit scanner / gallery / import adapters
    ├── documents/               # Library, folder, tag, trash, search
    ├── editor/                  # Page operations, annotation, signature, redaction
    ├── ocr/                     # Recognition, index, searchable-text data
    ├── export/                  # PDF/JPEG/TXT, compression, print, share
    ├── security/                # App lock, encryption, protected PDF
    ├── backup/                  # Archive và provider adapters
    └── tools/                   # QR, measure, count, math, assistant
```

Chỉ tạo package khi bắt đầu có code của feature; cây trên là ranh giới định hướng, không phải yêu cầu scaffold trước.

## 4. Pipeline ingest và xử lý

```text
ML Kit Scanner / Gallery / PDF / Sharesheet
   → content URI
   → validate MIME, size, free space
   → copy atomically vào app-owned storage
   → transaction Document + Pages
   → document available
      ├── thumbnail
      ├── OCR/index
      ├── edit operation log/metadata
      ├── export/share/print
      └── backup/provider upload
```

- Không giữ lâu URI tạm do scanner/provider trả về; copy file cần sở hữu trước.
- Với SAF URI mà người dùng muốn app tiếp tục truy cập, chỉ persist permission khi provider hỗ trợ và use case thực sự cần.
- Không để OCR/export/backup lỗi làm document đã lưu không mở được.
- Tách status cho ingest, OCR, export và backup.
- Dùng temp file + atomic rename khi platform/filesystem cho phép; commit database và cleanup theo thứ tự tránh metadata trỏ tới file thiếu.

Triển khai ingest hiện tại lưu source bất biến tại:

```text
files/documents/<document-id>/page-0001.jpg
files/documents/<document-id>/page-0002.png
...
```

Phần mở rộng ưu tiên MIME nhận diện từ header ảnh, fallback về MIME provider và dùng `.img` nếu vẫn không xác định được. Nội dung luôn được kiểm tra bằng header trước khi commit. EXIF rotation/mirror không sửa source bất biến: storage dùng nó để tính kích thước hiển thị, còn image decoder áp dụng transform sau khi downsample. Mỗi trang được ghi vào file `.part`, `fsync`, rồi rename trong thư mục staging. Chỉ sau khi toàn bộ thư mục được rename sang tên document chính thức, repository mới transaction `Document + Pages` vào Room. Nếu transaction lỗi, thư mục document vừa tạo được xóa.

Luồng import ảnh độc lập dùng Android Photo Picker với ordered selection khi implementation hệ thống hỗ trợ. Picker không yêu cầu quyền đọc toàn bộ thư viện; URI được copy ngay trong callback. Vì fallback `ACTION_OPEN_DOCUMENT` trên thiết bị cũ có thể không áp dụng giới hạn của picker, domain luôn kiểm tra lại tối đa 100 trang trước khi ingest.

Ingest, delete và startup reconciliation dùng chung một mutex để không thể đối chiếu/xóa file trong lúc transaction khác đang chạy. Khi coroutine bị cancel, repository kiểm tra document đã commit trong Room hay chưa dưới `NonCancellable`: record đã commit được trả về như kết quả thành công để UI không báo hủy giả, chưa commit thì cleanup. Khi app khởi động, reconciler xóa staging còn sót và thư mục document không có ID tương ứng trong Room; nếu Room không đọc được thì không đụng tới file.

## 5. Scanner và editor

- Bắt đầu với ML Kit Document Scanner để có auto-capture, crop, perspective, rotate và filter bằng model/flow có sẵn.
- App không xin quyền `CAMERA` khi chỉ dùng luồng ML Kit Scanner.
- Có thể thêm CameraX/OpenCV bất cứ lúc nào nếu feature yêu cầu custom viewfinder, realtime controls hoặc thuật toán mà ML Kit không expose. Đây không phải thay đổi bị khóa theo phase.
- Coi JPEG đã copy từ scanner là source page bất biến; mọi edit tạo metadata/operation hoặc derived file.
- Reorder/add/delete/rotate không ghi đè source.
- Redaction phải rasterize/flatten hoặc xóa content gốc tương ứng; overlay màu đen đơn thuần không phải redaction an toàn.

## 6. PDF và OCR

- `PdfDocument` phù hợp tạo PDF từ canvas nhưng không cung cấp merge/split/password/searchable-text/compression đầy đủ. Đánh giá thư viện PDF có license phù hợp cho các năng lực này.
- Kiểm soát dung lượng bằng downsample và JPEG quality trước khi vẽ/trộn vào PDF.
- OCR chạy theo page, idempotent, có version/model metadata để re-index khi engine đổi.
- Search dùng Room FTS khi dữ liệu đủ lớn; không `LIKE %query%` trên toàn bộ OCR text.
- Searchable PDF cần map bounding box OCR vào page coordinate và thêm invisible text layer bằng thư viện hỗ trợ; kiểm tra copy/search text trên các viewer phổ biến.
- Export DOCX/XLSX/PPTX phải định nghĩa rõ mapping; không tuyên bố bảo toàn layout nếu chỉ nhúng ảnh hoặc text.

## 7. Data model định hướng

```text
documents(id, title, state, favorite, folder_id, created_at, updated_at, deleted_at)
pages(id, document_id, position, source_uri, width, height, rotation, created_at)
edit_operations(id, page_id, type, payload, order_index)
ocr_results(page_id, text, language, engine_version, status, error_code, updated_at)
folders(id, parent_id, name, created_at, updated_at)
tags(id, name)
document_tags(document_id, tag_id)
exports(id, document_id, format, preset, output_uri, status, error_code, created_at)
backup_records(id, provider, remote_id, content_hash, status, updated_at)
```

- Chỉ thêm bảng khi feature bắt đầu cần; dùng migration, không destructive migration.
- `pageCount` nên derive từ Pages hoặc cập nhật trong cùng transaction.
- Không tạo `sync_queue` cho cloud riêng vì GScan không vận hành backend.
- File lớn nằm trong storage; database chỉ giữ URI/path, metadata, OCR và edit operations.

Schema hiện tại là version 2. Bảng `pages` có foreign key cascade tới `documents` và unique index `(documentId, position)`. Migration `1 → 2` giữ các document demo cũ nhưng đưa chúng về `DRAFT`, `pageCount = 0` vì version 1 chưa có source page thật.

## 8. Background work và hiệu năng

- Dùng coroutine cho job nhỏ gắn với màn hình.
- Dùng WorkManager khi job phải tiếp tục sau background/process death: OCR batch, export lớn, cleanup hoặc provider upload.
- WorkManager input chỉ chứa ID/URI/path nhỏ; không chứa bitmap hoặc file bytes.
- Đặt unique work theo document + operation, hỗ trợ progress/cancel/retry và kiểm tra idempotency.
- Decode ảnh theo target size, dùng streaming/page-by-page và cache thumbnail có giới hạn. Preview local hiện dùng LRU cache tối đa 1/16 heap, giới hạn trong khoảng 4–32 MB.
- Kiểm tra peak memory, disk space và cancellation với tài liệu hàng trăm trang.

## 9. Security, backup và integration

- Share file app sở hữu qua `FileProvider`/content URI và temporary read permission.
- Dùng Android Keystore, BiometricPrompt và crypto/PDF library chuẩn; không tự thiết kế crypto.
- Không nhúng API secret dùng chung trong APK.
- OAuth mobile dùng authorization code + PKCE theo provider; token lưu bằng cơ chế mã hóa phù hợp.
- Android Auto Backup giữ tắt cho tới khi Room và toàn bộ file có restore nhất quán; ưu tiên explicit encrypted archive backup/restore.
- Cloud riêng, web portal, realtime collaboration, expiring share link và remote logout bị loại vì cần server state/authorization do GScan vận hành.

## 10. Thứ tự theo dependency

Không chia phase. Khi chọn feature tiếp theo, ưu tiên lát cắt mở khóa nhiều năng lực:

```text
scanner/import
  → storage + Document/Page
  → document library + page editor
  → OCR + FTS
  → PDF/editor/sign/security
  → provider integration và tools độc lập
```

Feature độc lập như QR scanner, app lock hoặc print có thể triển khai bất cứ lúc nào. Mỗi lát cắt phải có acceptance criteria, build thành công và ghi rõ phần cần kiểm tra thủ công trên thiết bị thật. Không yêu cầu unit test.
