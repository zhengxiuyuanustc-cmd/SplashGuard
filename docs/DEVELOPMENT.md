# 从第一次构建开始

开屏清净是一个原生 Java Android 应用。欢迎贡献应用界面、无障碍规则、脱敏样本、机型指南和文档；无需先购买设备或给项目打赏。先阅读[贡献流程](../CONTRIBUTING.md)和[规则边界](RULES.md)，再选择一个小问题开始。

## 环境与源码

当前项目配置：JDK 21、Gradle Wrapper 8.9、Android Gradle Plugin 8.7.3、Android SDK Platform 35；最低 Android 8.0 / API 26。Java 源码按 Java 8 语法编译。请通过 Android Studio 的 SDK Manager 安装 Android SDK Platform 35、对应 Android SDK Build-Tools 和 Platform-Tools；不要将 SDK 放进仓库。

```text
git clone https://github.com/zhengxiuyuanustc-cmd/SplashGuard.git
cd SplashGuard
git fetch origin
git switch --detach origin/feature/simple-ui-onboarding
git switch -c feature/short-description
```

0.1.7 的工作基线是 `origin/feature/simple-ui-onboarding`，当前 `main` 仍为旧版本。先显式检出工作基线，再创建你自己的贡献分支；不要把默认克隆得到的 `main` 当作0.1.7。如果该工作分支尚未出现在远端，说明维护者还未推送此次候选，请等待分支公开后再按上述命令继续。公开安装包与网页上传状态以实际发行记录为准。

Android Studio 可直接打开项目根目录。命令行构建前，将 `JAVA_HOME` 指向你的 JDK 21，配置自己的 Android SDK 路径。可使用本机环境变量或被 Git 忽略的 `local.properties`；不要提交个人电脑路径。

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux：

```sh
./gradlew :app:assembleDebug
```

输出通常位于 `app/build/outputs/apk/debug/`。首次运行会下载 Wrapper 与构建依赖。Wrapper 已配置分发包 SHA-256；不要为绕过下载错误关闭证书或哈希验证。维护过程曾遇到 Gradle 分发包下载超时，已有验证记录不代表标准 Gradle 构建已成功；本轮 CI 也只运行下述纯 Java 检查。请如实报告自己的构建命令、版本和结果。

## 先运行不依赖手机的检查

以下三个检查类分别覆盖规则、有限会话及首次引导迁移。当前源码共有 48 + 40 + 11 = 99 项检查；测试数量会随贡献变化。

```text
mkdir build
javac -encoding UTF-8 -d build app/src/main/java/org/splashguard/app/AdRules.java app/src/main/java/org/splashguard/app/SkipSession.java app/src/main/java/org/splashguard/app/OnboardingPolicy.java tests/AdRulesTest.java tests/SkipSessionTest.java tests/OnboardingPolicyTest.java
java -cp build AdRulesTest
java -cp build SkipSessionTest
java -cp build OnboardingPolicyTest
```

项目的 PR CI 使用 JDK 21 执行同样的三个检查，不需要 Android SDK、手机、账号页面或签名密钥。它不能证明 Android 界面已经通过、服务可以长期存活，或真实广告一定跳过。

## Windows SDK 备用构建

当标准 Gradle 路线暂时受下载环境影响时，仓库提供显式的 SDK 工具链脚本。它依次调用 `aapt2`、`javac`、D8、`zipalign`、`apksigner` 并运行纯 Java 检查：

```powershell
.\tools\build-local.ps1 -SdkRoot "C:\Android\Sdk" -JdkRoot "C:\Program Files\Java\jdk-21" -OutDir ".\build\local"
```

请将路径改为自己的实际安装位置。脚本按目录名选择可用的 SDK Platform 和 Build-Tools；重现某次结果时要记录并核对实际选中的版本，建议使用仅包含所需版本的专用 SDK 目录。

这是开发预览构建路径，不是正式发行签名流程。脚本会启用调试并在输出目录创建或复用本地开发密钥；产生的 APK 不能因此称为稳定正式版，也不能把脚本成功写成标准 Gradle 路线已通过。

## 签名与可复现性

- 记录 Git commit、JDK / SDK / Build-Tools / Gradle 版本、构建命令、APK SHA-256、签名证书指纹及测试范围，才能让别人按相同来源复现过程。
- 同一源码不代表 APK 必然逐字节相同：构建环境、归档时间和签名密钥都可能产生差异。未做双机比对时，只能称“构建步骤可重现”，不能声称二进制完全可复现。
- 新贡献者使用自己的开发密钥，在模拟器或独立测试环境验证；它通常无法覆盖安装采用另一证书的已有版本。不要为了测试随意清除日常使用手机的数据。
- 发布者维护升级所需签名，开发密钥和正式密钥均不得进入 Git、Issue、PR、构建日志或公开附件。仓库忽略密钥与本地输出目录，但提交前仍需检查文件清单。
- 正式发布需要维护者单独确定非调试构建、签名保管和更新路线；本项目的纯测试 CI 不签名、不上传 APK，也不接收发行密钥。

## 实机验证要记录什么

优先使用模拟器或测试设备。首次安装时通过向导理解权限，再由本人开启无障碍、选择少量目标应用；不要自动修改用户的全局传感器权限。

开屏验证记录应包括：工具与目标应用版本、品牌/型号/系统、操作步骤、服务是否已连接、应用是否已选、是否实际出现广告、点击是否仅提交、广告是否提前消失、是否还发生晃动跳转。结束目标应用后台、熄屏再解锁可能改变广告投放，但并不保证每次有广告；“未出现广告”单独记录。

界面改动还要验证：旧用户升级不重置 `apps` / `enabled`；首次安装的五步引导可稍后完成；从系统设置返回不跳步骤或重置滚动；大字体不裁字；暂停、撤权、清除最近记录和关闭首页的区别清楚。涉及判断逻辑的变化应加入正例和反例，详见[架构](ARCHITECTURE.md)和[规则指南](RULES.md)。

## 目录速查

| 目录 | 内容 |
| --- | --- |
| `app/src/main/java/org/splashguard/app/` | 服务、规则、会话、界面、偏好与引导策略 |
| `app/src/main/res/` | 主题、图标、无障碍服务配置 |
| `tests/` | 纯 Java 检查与脱敏样本 |
| `tools/` | 本地开发构建脚本 |
| `docs/` | 开发、架构、规则和路线图 |
| `.github/` | Issue / PR 模板与只读权限的测试 CI |

网站位于单独的 [GitHub Pages 仓库](https://github.com/zhengxiuyuanustc-cmd/xiuyuanzheng.github.io/tree/main/splashguard)。修改网页时也要核对实际下载版本、签名说明及隐私表述，不把源码功能写成已公开安装包已经具备。
