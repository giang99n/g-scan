# Mục tiêu dự án GScan

## 1. Mục tiêu duy nhất

Xây dựng ứng dụng Android GScan có nhiều nhất có thể các chức năng hữu ích của một ứng dụng scan tài liệu hiện đại như iScanner, nhưng:

- Không xây dựng, triển khai hoặc vận hành backend riêng.
- Không tự thu thập dữ liệu để huấn luyện, fine-tune hoặc duy trì model AI riêng.
- Được phép dùng Android framework, Google Play services, SDK/thư viện bên thứ ba, model pre-trained và dịch vụ do nhà cung cấp khác vận hành nếu phù hợp về chi phí, quyền riêng tư và license.
- Ưu tiên xử lý local/on-device và lưu file trên thiết bị.
- Không sao chép thương hiệu, giao diện, nội dung hoặc tài sản độc quyền của iScanner.

Không chia phạm vi thành Phase, POC, MVP hay Commercial. Một tính năng không bị trì hoãn chỉ vì nó “nâng cao”; thứ tự làm dựa trên giá trị người dùng, dependency kỹ thuật, độ ổn định và công sức.

## 2. Stack và nguyên tắc nền tảng

- Kotlin + Jetpack Compose.
- Feature-first, MVVM/UDF, Hilt.
- Room làm single source of truth cho metadata và chỉ mục local.
- App-owned storage cho ảnh, PDF, thumbnail và file export.
- ML Kit Document Scanner cho luồng scanner sẵn có.
- ML Kit Text Recognition hoặc model OCR pre-trained phù hợp.
- `PdfDocument` hoặc thư viện PDF có license phù hợp cho tạo/chỉnh sửa/bảo vệ PDF.
- Coroutines cho job gắn với màn hình; WorkManager cho job cần sống qua background/process death.

ML Kit Document Scanner xử lý trên thiết bị nhưng UI/model/logic được tải động qua Google Play services. Lần dùng đầu có thể cần mạng; thiết bị dưới 1,7 GB RAM có thể trả `UNSUPPORTED`. Khi chỉ dùng scanner này, app không cần khai báo hoặc xin quyền `CAMERA`.

## 3. Feature map mục tiêu

### 3.1 Scan và nhập tài liệu

- Scan một hoặc nhiều trang.
- Tự phát hiện biên, auto-capture, crop, rotate, sửa phối cảnh và filter bằng ML Kit Scanner.
- Import ảnh từ gallery/photo picker.
- Import PDF và ảnh từ Android Sharesheet hoặc Storage Access Framework.
- Chế độ document, receipt, ID card/passport và book scan nếu SDK/UX cho phép.
- Custom CameraX/OpenCV chỉ khi cần UX hoặc xử lý mà ML Kit Scanner không cung cấp; OpenCV là thuật toán/thư viện có sẵn, không phải model AI tự train.

### 3.2 Quản lý tài liệu local

- Home, recent, danh sách/grid, thumbnail và sort.
- Đổi tên, duplicate, favorite, folder, tag, move và batch select.
- Reorder, rotate, add, replace, duplicate và delete page.
- Merge nhiều document, split theo page/range.
- Trash, restore và cleanup file an toàn.
- Search theo tên, tag và nội dung OCR.
- Xử lý tài liệu hàng trăm trang theo kiểu streaming/lazy, không giữ toàn bộ bitmap trong RAM.

### 3.3 OCR và dữ liệu trích xuất

- OCR theo từng trang, copy/edit text và lưu chỉ mục tìm kiếm.
- Hỗ trợ tiếng Việt/tiếng Anh trước; mở rộng script/ngôn ngữ bằng model pre-trained khi cần.
- Searchable PDF bằng invisible text layer nếu thư viện PDF được chọn hỗ trợ đúng tọa độ text.
- Export text sang TXT; DOCX/XLSX/PPTX chỉ triển khai khi có mapping nội dung rõ ràng, không quảng bá giữ nguyên layout nếu thực tế chỉ xuất text/ảnh.
- Trích xuất QR/barcode bằng ML Kit Barcode Scanning hoặc Google Code Scanner.

### 3.4 PDF và image editor

- Preview PDF/JPEG.
- Crop, rotate, reorder, filter và điều chỉnh brightness/contrast.
- Annotation: freehand, highlight, text, shape, stamp và image overlay.
- Redaction/cover/blur phải được flatten vào output; không chỉ vẽ một lớp có thể gỡ để lộ dữ liệu.
- Watermark, page number, header/footer.
- E-signature bằng vẽ tay, ảnh chữ ký hoặc signature template lưu local.
- Form filling cơ bản bằng text/image overlay.
- Merge, split, extract page, delete page và export page range.
- Preset PDF theo chất lượng/dung lượng; giảm dung lượng bằng downsample/re-encode ảnh trước khi tạo PDF, không giả định `PdfDocument` tự nén.
- Print qua Android Print Framework.

### 3.5 Bảo mật và riêng tư

- App lock/folder lock bằng PIN và Android BiometricPrompt.
- Mã hóa file local bằng Android Keystore và thư viện crypto chuẩn; không tự thiết kế thuật toán mã hóa.
- Password-protected PDF nếu thư viện PDF hỗ trợ encryption chuẩn và license phù hợp.
- Redaction an toàn, xóa file tạm và tránh lộ raw file path.
- Backup/restore thủ công thành archive được kiểm tra toàn vẹn; chỉ bật Android Auto Backup khi Room và toàn bộ file có chiến lược restore nhất quán.

### 3.6 Chia sẻ, lưu và tích hợp không cần backend riêng

- Save As qua Storage Access Framework.
- Share PDF/JPEG/TXT bằng content URI, `FileProvider` và quyền đọc tạm thời.
- Mở/import từ Gmail, Drive và app khác qua Android intents/SAF.
- Đồng bộ hoặc backup vào Google Drive/Dropbox/OneDrive bằng API chính thức và tài khoản của người dùng nếu có thể triển khai an toàn hoàn toàn từ mobile client.
- In-app purchase/subscription qua Google Play Billing nếu cần thương mại hóa; không nhúng server secret vào app. Nếu một entitlement bắt buộc xác minh server-side để chống gian lận thì ghi rõ giới hạn thay vì dựng backend trái mục tiêu.
- Crash reporting/analytics qua SDK managed có consent và cấu hình privacy phù hợp; GScan không vận hành server analytics riêng.

### 3.7 Công cụ mở rộng

- QR/barcode scanner.
- Đo chiều dài/diện tích bằng ARCore hoặc API pre-trained có sẵn nếu thiết bị hỗ trợ.
- Đếm vật thể tương tự bằng model pre-trained/on-device hoặc SDK bên thứ ba; không tự train model. Nếu model chung không đủ chính xác, giữ tính năng experimental hoặc không phát hành.
- Giải toán bằng OCR pre-trained + parser/CAS có sẵn; không tự xây model nhận dạng phương trình.
- AI chat/summarize/translate tài liệu chỉ dùng on-device model/platform API hoặc dịch vụ bên thứ ba có cơ chế credential an toàn. Không nhúng API secret dùng chung trong APK. Nếu không thỏa điều kiện này, không triển khai.

## 4. Ma trận khả thi

| Nhóm | Cách thực hiện | Quyết định |
|---|---|---|
| Scan, OCR, PDF, editor, signature, search, local security | Android/ML Kit/thư viện/model pre-trained chạy local | Ưu tiên triển khai đầy đủ |
| Save/share/print/import | Android intents, SAF, FileProvider, Print Framework | Triển khai |
| Backup cloud vào tài khoản người dùng | API provider + OAuth/PKCE, không có server GScan | Triển khai khi credential flow an toàn |
| Billing, crash, analytics | Dịch vụ managed | Có thể triển khai, ghi rõ giới hạn client-only |
| Count, math, measure, AI assistant | SDK/model pre-trained hoặc on-device platform API | Thử nghiệm và chỉ phát hành khi chất lượng đạt tiêu chí |
| Cloud riêng đa nền tảng, web portal, realtime sync qua tài khoản GScan | Cần backend do GScan vận hành | Không triển khai |
| Share link có expiry/revoke, remote logout/collaboration | Cần server giữ state và authorization | Không triển khai |
| Model AI riêng | Cần dữ liệu/training/serving hoặc maintenance riêng | Không triển khai |

Backend-as-a-Service cũng được xem là dependency cloud managed, không phải backend tự vận hành. Chỉ dùng nếu người dùng chấp nhận dependency đó; không tự thêm BaaS chỉ để mô phỏng kiến trúc cloud của iScanner.

## 5. Cách chọn việc tiếp theo

Không dùng phase để chặn scope. Chấm mỗi feature theo:

1. Giá trị trực tiếp cho người dùng scan tài liệu.
2. Số feature khác được nó mở khóa.
3. Có thể làm local/client-only mà không giảm an toàn hay không.
4. Rủi ro mất dữ liệu, bảo mật, hiệu năng và license.
5. Công sức triển khai và khả năng kiểm tra trên thiết bị thật.

Thứ tự mặc định theo dependency, không phải phase:

```text
scan/import
  → app-owned file storage + Document/Page schema
  → library + editor nhiều trang
  → OCR/search
  → PDF editor/export/sign/security
  → integrations và công cụ mở rộng độc lập
```

Có thể làm song song hoặc đổi thứ tự khi một feature không phụ thuộc chuỗi trên.

## 6. Pipeline dữ liệu chuẩn

```text
Scanner / Gallery / PDF import
    → content URI
    → copy atomically vào app-owned storage
    → transaction lưu Document + Pages trong Room
    → tài liệu mở được ngay
        ├── OCR/index
        ├── thumbnail/preview
        ├── edit operations
        └── export/share/print/backup
```

- Không để OCR/export trở thành điều kiện để lưu document.
- Tách document state khỏi OCR/export/backup state.
- Không truyền bitmap lớn qua navigation, Bundle hoặc WorkManager input.
- Không chạy I/O, OCR, PDF hoặc xử lý ảnh nặng trên main thread.
- Mọi thao tác Document + Pages liên quan nhau dùng transaction và file operation có rollback/cleanup rõ ràng.

## 7. Definition of Done

Một tính năng chỉ hoàn thành khi:

- Không cần backend do GScan vận hành và không cần model do GScan tự train.
- Có acceptance criteria kiểm tra được.
- Có loading/content/empty/error và cancellation phù hợp.
- Không làm mất document đã lưu khi OCR, export, backup hoặc integration lỗi.
- Không để file tạm/orphan trong các lỗi phổ biến đã biết.
- Không xử lý nặng trên main thread và không giữ bitmap lớn quá lâu.
- Không yêu cầu viết hoặc chạy unit test; tính năng phải compile/package thành công và scanner, share, biometric, print, storage/provider integration được kiểm tra thủ công trên thiết bị thật khi liên quan.
- Dependency/SDK có license, privacy và security phù hợp.

## 8. Chỉ số chất lượng

- Tỷ lệ hoàn tất scan → lưu → mở lại → share/export.
- Thời gian hiển thị trang đầu và peak memory với tài liệu nhiều trang.
- Chất lượng crop/filter/OCR trên bộ tài liệu tiếng Việt và tiếng Anh.
- Dung lượng và độ rõ giữa các preset PDF.
- Tỷ lệ scanner/module/feature pre-trained không khả dụng theo thiết bị.
- Tỷ lệ job OCR/export/backup retry hoặc thất bại.
- Tỷ lệ crash/ANR và số file orphan sau các kịch bản lỗi kiểm tra thủ công.

## 9. Tài liệu tham chiếu

- [Danh sách tính năng iScanner](https://iscanner.com/homepage/)
- [ML Kit Document Scanner cho Android](https://developers.google.com/ml-kit/vision/doc-scanner/android)
- [ML Kit Text Recognition v2 cho Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [Android `PdfDocument`](https://developer.android.com/reference/android/graphics/pdf/PdfDocument)
- [Chia sẻ file an toàn bằng content URI](https://developer.android.com/training/secure-file-sharing)
