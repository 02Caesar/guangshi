# 光室 0.4.0 构建与验收状态

验证日期：2026-11-08（北京时间）。

## 本环境已验证

- `./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`：全部成功。
- 39 项测试通过，0 失败、0 错误、0 跳过；Lint：0 错误、0 警告。
- Android 15 原生图形引擎直接编译生产 AGSL 资源；通过 RenderNode 硬件录制及离屏读回，比较 6 种滤镜、曝光/RGB/反差/饱和度参数与 CPU 导出。抽样各通道误差不超过 3/255。
- 验证颗粒改变图像、模拟光圈保留点击中心并模糊外围，以及无新相机帧时更新 RenderEffect、预览宿主重挂载时更新效果。
- 刻度整数化测试验证 0.1 步进、正反调节不累积漂移和范围边界；Compose 验证顶部控制位置、浮层展开前后取景区域尺寸不变、红圈提示不存在、每次 ＋ 为 0.1。
- 保留暗房 RGB 实际像素变化、小窗口导出可达、原图尺寸溢出、EXIF 方向、两种边框导出、失败回滚、旧底片读取及焦段决策回归。
- 新增多选删除、确认取消、回收站多选恢复；存储测试覆盖批量删除后重新读取、原片字节保留、参数保留、重复操作及延迟保存不复活底片。Compose 测试覆盖删除全部、空底片盒与回收站恢复流程。
- UI 截图位于 docs/screenshots，使用合成测试图和未授权相机界面，不是 OPPO 实拍。
- APK：com.guangshi.app，versionCode=4，versionName=0.4.0，minSdk=29，targetSdk=35。
- APK SHA-256：`5f59a004c2e6422669a49471e861a9a2716083c04c6eeb4f35596c56d5310bd4`。
- 调试签名证书 SHA-256 与此前交付版本一致：`7809d0a2fee8a8401792dcf7630352694f792b3e91ff69008dd995ad71abd20e`。直接覆盖安装，勿先卸载。

## 实现和测试边界

Android 13+ 优先使用 CameraX Preview + PreviewView.COMPATIBLE（TextureView）+ RuntimeShader / RenderEffect，目标 30 fps。未继续沿用这条路径的逐帧 Bitmap 回读、CPU 处理和 Compose 图片状态推送。低版本或着色器不可用时保留 CPU 路径。

GPU 颗粒为稳定浮点噪声，预览虚化为快速多点近似；高分辨率导出保留 CPU 处理。使用同一组参数与色调曲线，不承诺颗粒图案和虚化结果逐像素一致。

尚未连接 OPPO 或模拟器，**没有实测手机预览帧率、移动跟手延迟、TextureView 与相机的端到端显示、镜头切换、锁屏恢复或 ColorOS 相册行为**。离屏着色器测试不等于相机硬件测试；真机验收清单见 README。

Robolectric Android 13 测试配置使用旧 Android 12 图形库，因此 GPU 测试使用 Android 15 原生运行时，没有改写生产 shader 或关闭断言。相册失败回滚测试日志中的 write error 来自故意注入异常，不是磁盘故障。

GitHub CI 状态以 Actions 实际记录为准；本报告中的成功指当前开发环境，不代替推送后的 CI 结果。
