# GScan

Skeleton Android Kotlin cho ứng dụng scan tài liệu theo hướng local-first.

## Stack

- Jetpack Compose + Material 3
- MVVM/UDF với `ViewModel`, `StateFlow`
- Feature-first Clean Architecture (`data/domain/presentation`)
- Hilt cho dependency injection
- Room làm nguồn dữ liệu local duy nhất (single source of truth)
- Navigation Compose

## Chạy project

```bash
./gradlew assembleDebug
```

Mục tiêu và feature map sản phẩm: [docs/PROJECT_GOAL.md](docs/PROJECT_GOAL.md).

Kiến trúc và thứ tự dependency kỹ thuật: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
