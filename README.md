# 开屏清净 · SplashGuard

Android 本地开屏广告辅助工具：优先引导设置应用自带的防摇一摇开关，再通过无障碍服务尝试点击明确的广告跳过按钮。保留正常旋转、体感和传感器功能。

**[下载 APK · v0.1.4-preview](https://github.com/zhengxiuyuanustc-cmd/SplashGuard/releases/download/v0.1.4-preview/SplashGuard-0.1.4-preview.apk)** · [版本说明与校验文件](https://github.com/zhengxiuyuanustc-cmd/SplashGuard/releases/tag/v0.1.4-preview) · [GitHub 源码](https://github.com/zhengxiuyuanustc-cmd/SplashGuard)

- Android 8.0（API 26）及以上；当前实机覆盖为华为 GOA-AL80 / HarmonyOS 4.2 / Android API 31。
- MIT 开源，无需 root、账户或网络连接；APK 不声明 INTERNET 权限，无广告或第三方运行 SDK。
- 用户选择应用，默认不开启自动点击；可随时暂停或关闭无障碍服务。

**当前下载包是 debuggable、开发签名的预览构建，供了解限制后试用。尚无非 debuggable、正式发布签名的稳定安装包。** 其他品牌、广告样式及长期后台稳定性仍需验证。

## 安装和设置

1. 从上方链接下载并安装 APK，按系统提示允许本次安装。系统可能另行要求本人验证或确认受限制设置，请在手机上完成。
2. 查看“本机手动设置指南”。若目标应用提供独立的广告传感器开关，优先关闭该项；不要关闭整个应用的方向权限或全局传感器。
3. 点“选择需要处理的应用”，仅选择需要跳过开屏广告的应用。初次安装默认不选；常见应用建议仍可自行调整。
4. 阅读无障碍说明，在系统中开启“开屏清净 · 自动跳过”，再打开应用内“启用自动跳过”。返回首页，确认显示“自动跳过已就绪”。
5. 查看“本机后台运行指引”。华为用户在“应用启动管理”中对本工具关闭自动管理，同时允许**自启动、关联启动、后台活动**，保存后再检查服务状态。其他品牌按实际系统页面操作，专用入口尚未验证。
6. 点“关闭首页，保留自动跳过”，再打开选中的应用观察效果。若失效，重新打开本工具检查服务状态；打开过设置页面不代表已经配置成功。

暂停只需关闭应用内开关；彻底停用可在系统无障碍设置中关闭本服务。

## 划掉后台卡片后还能用吗？

在本次测试的华为设备上，完成上述三项后台允许后，分别移除带锁卡片、解除锁定后划掉卡片、点击“关闭首页，保留自动跳过”，服务均继续运行。三种条件各有一例知乎真实开屏广告提前跳过的录屏证据。

因此本机无需一直保留首页卡片，**识别广告的无障碍服务仍须获准在后台运行**。此结果不涵盖一键清理、设备重启、长期省电清理或其他品牌。“强行停止”以及系统关闭无障碍授权会停止功能，需要本人重新开启；本工具不能绕过这些限制。

## B站防摇一摇需单独设置

B站 9.15.0 已核实的路径：

**哔哩哔哩 → 我的 → 设置 → 隐私权限设置 → 关闭“允许广告访问传感器”。**

该项说明为关闭后不展示“摇一摇”“扭一扭”类型广告。设置后结束B站后台再打开；其他版本的入口和名称以实际页面为准。此开关由B站独立保存，不依赖开屏清净后台，也不会关闭整个应用的方向权限。本工具提供指引，不会自动把“看过指引”标记为设置完成。

本机用户已确认该设置有效，并确认B站已观察开屏广告可自动跳过；此前漏过反馈和验证范围保留在 [TESTING.md](TESTING.md)。

## 能力与限制

- 仅处理用户选中的、接近全屏的前台应用，进入前台后最多识别 12 秒；一次进入最多提交一次被系统接受的点击操作。
- 检查可见、可用的控件文字、描述、标识和实际边界。通用规则限定右上方；B站已知开屏场景使用更具体的控件规则，兼容右下方按钮。
- 不匹配普通“×”、一般“关闭”、安装、领取、同意等按钮；不使用预设固定坐标、OCR、远程规则或云端识别。
- 不关闭方向权限或全局传感器，不使用 root、注入或传感器拦截。

**自动跳过不等于系统级阻断摇一摇跳转。** 若广告先于识别触发跳转、没有可访问的跳过按钮或样式不匹配，仍可能无法处理。知乎已验证提前自动跳过，尚未通过物理晃动验证不触发跳转，也未屏蔽其传感器。普通 Android 应用没有可用的通用接口，能只对另一个应用的广告代码暂停加速度计。

当前规则不能保证零误触。建议避开银行、支付、导航和体感游戏，首次使用观察效果，异常时暂停。计数表示已提交的操作，不等于实际广告消失数量；未出现广告不算跳过成功。

## 隐私

界面控件在本机短暂处理，不保存完整页面、截图、输入内容或传感器数据，不上传应用列表。私有设置保存启用状态、所选应用、操作计数、最后一次操作的包名/按钮文字/时间及固定检查原因；系统备份关闭。完整说明见 [PRIVACY.md](PRIVACY.md)。

## 编译和验证

标准工程需要 JDK 17+、Android SDK 35、Gradle 8.9，可由 Android Studio 导入：

```text
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
sh gradlew :app:assembleDebug
```

使用 AGP 8.7.3，附官方 Gradle wrapper 及发行包 SHA-256 校验，首次构建需联网下载构建工具。App 运行无需联网。

**当前已通过的构建路径**：Google 官方 SDK 35 / Build Tools 35.0.0 + JDK 21 本机手工构建，含 35 项规则测试、Java 编译、D8、资源链接、对齐和 v2/v3 验签；安装后回读 APK 哈希与交付包一致。标准 Gradle 路径曾因两个官方发行包下载地址超时而未进入编译，尚无通过记录。

Windows 无 Gradle 构建：

```powershell
.\tools\build-local.ps1 -SdkRoot C:\Android\Sdk -JdkRoot C:\Java\jdk-21 -OutDir C:\build\SplashGuard
```

脚本生成的开发密钥位于指定输出目录，**不要上传或公开密钥**；该脚本当前生成 debuggable 预览包。未来稳定分发应另行使用非 debuggable 构建与妥善保管的正式发布签名，并重新验收。测试方法与实际观察见 [TESTING.md](TESTING.md)，设计边界见 [DESIGN.md](DESIGN.md)。

## 反馈与贡献

欢迎通过 [GitHub Issues](https://github.com/zhengxiuyuanustc-cmd/SplashGuard/issues) 反馈兼容性或漏过问题，请先阅读 [贡献说明](CONTRIBUTING.md)。不要公开账户画面、密码或设备凭据。

[更新记录](CHANGELOG.md) · [MIT 许可证](LICENSE) · [第三方构建组件声明](THIRD_PARTY_NOTICES.md)

## 参考

- [华为：第三方应用广告与设备方向权限](https://consumer.huawei.com/cn/support/content/zh-cn16002151/)
- [华为：无障碍中的第三方应用服务开关自动关闭](https://consumer.huawei.com/cn/support/content/zh-cn00410039/)
- [Android：AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Android：关闭首页并移除最近任务](https://developer.android.com/reference/android/app/Activity#finishAndRemoveTask())
- [AOSP：全局 Sensors off 的影响](https://source.android.com/docs/core/interaction/sensors/sensors-off)
