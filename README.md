# 光室 · 原生 Android 胶片相机

**[点此直接下载 Android 调试 APK（约 26 MB）](https://raw.githubusercontent.com/02Caesar/guangshi/main/downloads/guangshi-debug.apk)**

下载后在手机中打开 APK 安装。此包已在开发环境构建并校验签名，仍需 OPPO 真机验收。

Kotlin + Jetpack Compose + CameraX；优先针对 OPPO Find X8s+ / ColorOS 的普通竖屏设计。Android 10（API 29）及以上。不请求网络或相册读写权限，无登录、无云端处理。

## 首版流程

1. 首次打开允许相机权限。拒绝后可重试，永久拒绝可进入应用系统设置。
2. 拖动圆形转盘或点按右侧胶卷档位，选择暖调日光 / 冷调夜景 / 高反差黑白。400 / 800 / 100 仅为风格编号，不修改传感器 ISO。
3. 点击快门，原始 JPEG 保存在应用私有目录。取景为未经胶片处理的原始画面，FIT_CENTER 展示完整 4:3 目标画面，不拉伸、不铺满裁边；实际尺寸由设备 CameraX 协商。
4. 最近照片入口或「暗房」打开底片盒；上方大图、下方横滑底片。曝光、颗粒、青 / 品红 / 黄可调；黑白禁用彩色控制。按住看原片；重置恢复胶卷默认值。
5. 选择是否附带黑色胶片边框，点击「导出到系统相册」，等待“已保存”提示。通过 MediaStore 写入 Pictures/光室，保留原始像素尺寸与经过 EXIF 校正的方向。

原片和每张照片的胶卷、调色参数保存在 files/negatives（JSON 原子替换）。重启后恢复；卸载/清除数据会删除应用内底片，已导出的相册照片保留。预览最长边 1000px，缩略图 160px，滑块预览合并约 65ms，后台协程可取消；原图导出与预览使用 PixelEngine 的同一色调/曝光/颗粒算法，颗粒按标准化坐标确定。同一时刻仅允许一次拍摄或导出。导出耗时取决于照片尺寸，内存不足会提示重试。滑块松手保存参数；导出使用点击时参数快照。

## 构建与安装

需要 JDK 17 或 21、Android SDK Platform 35、Build Tools 35.0.0；Gradle Wrapper 8.9（固定 SHA-256）、AGP 8.7.3、Kotlin 2.0.21。安装 SDK 后设置 ANDROID_HOME，或在不提交的 local.properties 中设置 sdk.dir。

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.guangshi.app/.MainActivity
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。将 APK 传到手机打开，按 ColorOS 提示允许该文件来源安装应用。这是已签名的调试包；不同构建机器可能使用不同调试签名，签名冲突需先备份/导出底片再卸载旧版本。

## GitHub Actions 下载

工程内附 `.github/workflows/android.yml`，支持推送 main 与手动 `workflow_dispatch`。远端仓库发布后，在 **Actions → 光室 Debug APK → Run workflow → main → Run workflow** 运行。成功后打开运行记录的 **Artifacts → guangshi-debug-apk**（登录 GitHub），下载 ZIP，解压得到 APK。Artifact 保留 14 天。首次 GitHub Actions 运行失败，未产生可下载的 Artifact；请使用本页顶部已经构建好的 APK 下载链接。

## 验收状态

当前云端构建、测试和 Lint 的结果见 BUILD_STATUS.md。纯像素测试覆盖黑白通道、曝光、胶卷差异、黑白忽略调色、颗粒一致性和青色控制。它们不代替相机/相册运行测试。

仍需 OPPO Find X8s+ 真机逐项验证：

- 首次允许 / 拒绝 / 永久拒绝相机授权；从系统设置授权后返回。
- 连续拍摄至少 10 张；快速点快门不会重复保存或卡死；拍摄失败提示后可重试。
- 切后台、锁屏后返回，预览和快门恢复；相机被其他应用占用后可重新连接。
- 竖拍、左横拍、右横拍与倒置照片方向正确；对照画面四角确认取景与成片裁切。
- 三种胶卷明显不同；拖动/点按转盘有刻度、吸附动画与轻微触感（受系统触感设置影响）。
- 暗房滑动底片、调色、重置、按住对比流畅；黑白只显示曝光和颗粒。
- 强制停止再打开，底片及松手保存的参数仍在。
- 带边框和无边框各导出一张，在系统相册中可见；方向和调色一致；拒绝联网不影响流程。
- 检查前摄开孔、系统手势区、大字体与小窗口布局；长时间导出和低内存行为。

界面使用系统安全区域和实际可用空间；暗房控件可纵向滚动，不依赖折叠屏。没有真机或模拟器运行证据时，不宣称 OPPO 适配验收通过。
