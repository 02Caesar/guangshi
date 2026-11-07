# 光室 0.2.0 构建与验收状态

验证日期：2026-11-07（北京时间）。

## 已完成的当前环境验证

- `./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`：全部成功。
- 29 项测试，0 失败、0 错误、0 跳过。包含实际 Android 图形处理和 Compose 交互，不是只有语法检查。
- Lint：0 错误、0 警告，无 baseline、未关闭检查。
- APK：com.guangshi.app，versionCode=2，versionName=0.2.0，minSdk=29，targetSdk=35。
- APK v2 签名验证通过；签名证书 SHA-256 与此前交付的 0.1.0 一致：`7809d0a2fee8a8401792dcf7630352694f792b3e91ff69008dd995ad71abd20e`。可覆盖升级，勿先卸载。
- APK SHA-256：`4ede86b83fea1fbec1870bfbc480b7a4fcf1ab62172b5b4bfd5f59b81737d47e`。
- 当前环境：JDK 21、Gradle 8.9、SDK 35、Build Tools 35.0.0。CI 使用 JDK 17。

## 关键回归证据

- 8192×6144 原图读取上限 Int.MAX_VALUE 不溢出；极端尺寸边界、无效尺寸有测试。
- 实际解码 JPEG 后以带边框和无边框两种方式导出，校验编码后的尺寸和待发布状态 IS_PENDING=0。
- 模拟 MediaStore 写入失败后删除未完成相册记录，原始文件逐字节不变。测试日志中的“write threw an exception / out of disk space”来自故意注入的写入失败，不是磁盘空间不足。
- EXIF 90° 校正后长宽正确；0.1.0 JSON 底片可读取且保留胶卷和调色。
- 6 种滤镜、曝光/RGB、黑白控制、标准化颗粒，以及模拟光圈保留点击中心和原片不变。
- 焦段选择覆盖超广角、主摄裁切、长焦、越界禁用、24/28/35 和 73/85 循环。
- Compose 测试确认 RGB 滑块改变后大图中心的实际红通道变化；普通窗口快门可见，小窗口可滚动到导出按钮。
- 截图：`docs/screenshots/camera.png`、`darkroom.png`、`compact.png`。它们由 Robolectric 绘制，照片为合成测试图，不是手机实拍。

## 尚未验证的部分

没有连接 OPPO Find X8s+ 或 Android 模拟器，未宣称硬件取景、镜头切换、帧率、手感、拍照方向、生命周期恢复或真实系统相册端到端验收通过。测试相册使用 Robolectric Provider，不等同于 ColorOS 相册。真机清单见 README。

OPPO 官方规格页面访问被云端网络策略拒绝，因此未使用未经核实的固定硬件参数；应用在手机上读取 Camera2 实际开放的镜头、物理光圈和变焦范围。焦段为估算等效值；15mm 是否开放、长焦是否可用需真机确认。模拟光圈是区域虚化，无景深识别。

0.1.0 首次 GitHub Actions 在 SDK 初始化 Action 失败；本次工作流改用 Ubuntu 24.04 自带 SDK。推送后的 CI 结果以 Actions 实际记录为准，不将本地成功等同于 GitHub CI 成功。
