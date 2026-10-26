# 标准Gradle构建与Android lint验证

本页说明新增的[Android Gradle检查流程](../.github/workflows/android-gradle.yml)。流程文件已配置，**编写时尚未取得这条流程的远程执行结果**；不能将SDK备用脚本、纯Java检查或流程文件存在写成标准Gradle已经通过。发布后的实际状态应查看对应提交的GitHub检查，而不是推断历史结果覆盖新源码。

## 运行内容

流程在推送、Pull Request或本人手动运行时，使用GitHub托管的Ubuntu24.04环境执行：

```sh
bash ./gradlew --no-daemon :app:assembleDebug :app:lintDebug
```

先准备Android SDK Platform35及Build-Tools34.0.0、35.0.0，再打印Java与Gradle版本。Gradle使用仓库Wrapper8.9及其分发包SHA-256校验，不使用runner预装的其他Gradle，也不为下载失败跳过哈希或证书验证。

[Android官方AGP8.7兼容表](https://developer.android.com/build/releases/agp-8-7-0-release-notes)列明Gradle8.9、最低JDK17、最高API35，默认Build-Tools为34.0.0。项目采用AGP8.7.3；目前未在模块中另定Build-Tools，因此流程安装34和35，不能仅凭安装35就宣称Gradle实际用了35。JDK21沿用现有检查环境，[Gradle官方说明](https://docs.gradle.org/8.5/release-notes.html)确认从8.5起可在Java21上运行。

SDK准备使用官方[sdkmanager命令](https://developer.android.com/tools/sdkmanager)，只安装明确包名，不依赖个人电脑路径。GitHub的[Ubuntu24.04托管镜像清单](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)列有Android命令行工具；流程仍检查工具和目标包是否存在，缺失或安装失败会让检查失败，不会静默换用其他版本。

## 结果如何解释

- `assembleDebug`成功表示这次提交通过标准Android Gradle构建；不代表真机运行、广告跳过、长期后台或原生鸿蒙支持已经验证。
- `lintDebug`按项目默认严重级别执行，没有`continue-on-error`、跳过检查、忽略错误或自动生成基线。若失败，应看具体错误并修正代码或配置；不能只移除lint任务换取绿灯。
- 若失败时存在lint文本报告，流程将其打印到日志，便于审阅。报告可能涉及源码中的设置与资源路径，但不包含手机抓取、用户账号或私人设备记录，因为该流程不连接手机或读取这些数据。
- 没有接入模拟器、Android仪器测试、真广告测试或多机型矩阵。纯Java策略检查仍由独立流程维护，二者的证据不互相替代。

## 权限与产物

流程仅授予`contents: read`，checkout不保留Git凭据，actions沿用仓库已核验的固定提交。没有`pull_request_target`、发行密钥、自动合并、发布或APK上传步骤，也没有修改分支保护或加入新的必需检查要求。

`assembleDebug`遵循Android默认开发构建行为，可能在临时runner中使用其默认调试签名；流程不配置或读取项目发行签名，该临时调试包不能用于覆盖采用维护者证书的现有安装。本流程不将生成的APK作为可下载发行附件。

开发者在自己环境复现时应使用自己的SDK路径，核对Wrapper/AGP/JDK/SDK与命令结果。首次下载需要访问官方分发与依赖仓库；网络失败应记录为环境或下载失败，不把它改写成代码已通过。最终通过记录还应关联准确提交SHA和检查链接。
