# 构建交付状态

验证日期：2026-11-05（北京时间）。

- `./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`：成功；可复用安装脚本已执行并验证。
- 单元测试：6 项通过，0 失败、0 跳过。
- Lint：0 错误、1 警告（Android 10/11 fullBackupContent 的兼容配置建议；当前 allowBackup=false）。未设置 lint baseline 或关闭检查。
- APK：app/build/outputs/apk/debug/app-debug.apk，约 26 MB，应用名「光室」，包名 com.guangshi.app，0.1.0，minSdk 29 / targetSdk 35。
- `apksigner verify --verbose`：通过，APK v2 签名，一个签名者。
- SHA-256：ced43347ae5fbd4a772e32b8fe7e0100d4d5e54588275bcc04f1b6c5d5a6af7d。
- 此云环境实际使用 JDK 21、Gradle 8.9、SDK 35、Build Tools 35.0.0；CI 使用 JDK 17。
- 未连接真机或模拟器：不宣称 CameraX 预览、OPPO 相机硬件、手感或 MediaStore 相册端到端验收通过。README 有真机清单。
- 独立本地仓库已建立于 /workspace/guangshi，原 /workspace/qingping 保持不变。GitHub API 被当前网络策略返回 CONNECT 403，远端仓库创建未成功，Actions 尚未运行；没有发布到 GitHub 的 APK 链接。
- 配置草稿已保存 api.github.com 域名要求、install_script 和 start_skill；草稿保存不等于已应用或发布。需在环境设置中审阅保存网络规则后重新尝试创建远端仓库。新任务恢复尚未验证，本地提交不可视为已远端备份。
