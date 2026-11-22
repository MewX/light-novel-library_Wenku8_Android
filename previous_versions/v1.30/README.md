# LightNovelLibrary `v1.30`（`1.x` 系列最终归档）

本目录为 `master` 分支上 **`1.x`（Material Design 1）系列** 最终版本 `v1.30` 的完整独立 Android Studio 工程快照（复制自 `master` 分支的 `studio-android/LightNovelLibrary/`）。

## 版本信息

- **应用 ID**：`org.mewx.wenku8`
- **版本号**：`versionName = 1.30`（`versionCode = 73`）
- **目标 SDK**：`minSdkVersion 21` / `targetSdkVersion 37`
- **构建配置**：Android Gradle Plugin `9.3.1` + Java（经典 View / Fragment / Material Design 1 架构）

## 目录与 `api/` 子模块说明

- `app/`：`v1.30` 主应用模块源码与单元测试 / 仪器测试。
- `api/`：**Git Submodule**（指向 `MewX/wenku8-api-core`，精确锁定至 `master` 分支 `v1.30` 所对应的提交 `b12993b`），用于保持与 `v1.30` 完全一致的原生签名协议接口。
- `api-stub/`：面向开源构建的兼容桩模块。当未拉取私有 `api/` 子模块（或设置 `-DforceApiStub=1` / `WENKU8_FORCE_API_STUB=1`）时，`settings.gradle` 会自动回退至 `api-stub/`，可直接编译与运行测试：
  ```bash
  ./gradlew :app:assembleAlphaDebug
  ./gradlew :app:testAlphaDebugUnitTest
  ```
- 历史 `1.x` 视觉设计稿与实机截图分别归档于 [`../../docs/designs/v1/`](../../docs/designs/v1/) 与 [`../../screenshots/v1/`](../../screenshots/v1/)。
