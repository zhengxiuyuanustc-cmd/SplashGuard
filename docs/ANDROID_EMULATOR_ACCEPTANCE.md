# Android隔离模拟器验收

本套检查补充纯Java规则测试，针对Android系统文件选择器、URI读权限、Activity重建、SharedPreferences保存和写入失败恢复。当前先提交检查入口，实际结果以对应源码的工作流为准；不能用脚本存在或历史构建成功代替执行结果。

检查在新建的API 26和35 Google APIs x86_64模拟器运行，覆盖最低Android版本和当前targetSdk。它不代表华为、荣耀等厂商系统或原生鸿蒙，也不验证真实广告、长期后台、进程被杀或所有磁盘故障。

## 隔离边界

测试会清空空白模拟器内本工具的偏好，并临时使其私有偏好目录只读以制造实际写入失败。开始前必须同时提供`isolatedEmulator=true`和确认系统`ro.kernel.qemu=1`；任一不满足会在清空数据前失败。不要对个人手机运行这套测试。工作流明确指向新建的`emulator-5554`，不连接维护者手机。

测试DocumentsProvider只编译进androidTest APK，展示公开签名示例及改动签名的副本。系统DocumentsUI真正选择文档并给目标App授予URI读取能力；测试不直接伪造onActivityResult。测试依赖、公钥示例和Provider均不进入正式APK，工作流另外检查发行Manifest。

## 检查范围

1. 旧限制通过真实SharedPreferences迁移，签名历史在撤销和恢复内置后保留。
2. 系统文件选择、预览不保存、Activity重建、本人确认后才保存。
3. 修改签名的文件不能提交或生成有效限制。
4. 恢复内置后再次选择同一签名文件仍无操作，不改变撤销或重新生效。
5. 取消系统文件选择不修改规则。
6. 真实应用私有目录写权限失败导致提交未确认，规则处理暂停；恢复写权限并明确恢复内置后才恢复，保留已接收历史。

本地可在专用空白模拟器上运行：

```sh
bash ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.isolatedEmulator=true
```

测试使用固定版本AndroidX Test/UiAutomator，参见[官方测试运行器](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner)和[DocumentsProvider说明](https://developer.android.com/guide/topics/providers/create-document-provider)。模拟器启动参数参考[Android官方命令行说明](https://developer.android.com/studio/run/emulator-commandline)。

失败时仅保留新建模拟器的合成页面截图、界面树、环境和测试报告，作为工作流附件保留7天；不采集用户手机、账号或广告现场。完整验证状态见[TESTING](../TESTING.md)。
