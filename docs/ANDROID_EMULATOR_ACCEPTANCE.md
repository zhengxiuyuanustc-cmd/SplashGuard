# Android隔离模拟器验收

## 当前结果（2026-10-29）

准确源码`d2140cb786142d829cd3141ebf010fd0c882b6e0`的[三系统验收](https://github.com/zhengxiuyuanustc-cmd/SplashGuard/actions/runs/40867780298)已通过：Android8/API26、Android15/API35、Android17/API37各16项，原始JUnit无失败/错误/跳过，附件SHA256及生产Manifest核对通过。较原8项增加保留任务切回、同应用对话框不续期和六项[服务控制与小窗口场景](SERVICE_CONTROL_ACCEPTANCE.md)。结果见[永久摘要](validation/android-emulator-20261029-service-controls.json)。本次只增测试，未修改生产代码或重发0.1.16安装包。不能替代厂商真机、长期后台或真实广告验证。

## 历史基础（0.1.14）

源码`9fd308b1798394c95904066ace295084849fcca5`的[不可调试目标模拟器验收](https://github.com/zhengxiuyuanustc-cmd/SplashGuard/actions/runs/40526038212)已通过：Android8/API26、Android15/API35和Android17/API37各8项，JUnit报告均为8 tests / 0 failures / 0 errors / 0 skipped，另核对正式APK不含测试组件或联网权限。 它补充纯Java规则测试，并实际覆盖系统文件选择器、URI读权限、Activity重建、SharedPreferences保存/写入失败恢复、系统服务绑定及独立合成页面的单按钮操作和双候选拒点。首轮2caf657的API26/35各6项为历史基础检查。

检查在新建的API 26、35和37 Google APIs x86_64模拟器运行，覆盖最低Android版本和当前targetSdk和Android17系统。API37的官方包名为android-37.0，采用至少4GiB内存，见[官方模拟器版本说明](https://developer.android.com/studio/releases/emulator)。它不代表华为、荣耀等厂商系统或原生鸿蒙，也不验证真实广告、长期后台、进程被杀或所有磁盘故障。

## 隔离边界

测试会清空空白模拟器内本工具的偏好，并临时使其私有偏好目录只读以制造实际写入失败。开始前必须同时提供`isolatedEmulator=true`和确认系统`ro.kernel.qemu=1`；任一不满足会在清空数据前失败。不要对个人手机运行这套测试。工作流明确指向新建的`emulator-5554`，不连接维护者手机。

测试DocumentsProvider与合成广告Activity只编译进androidTest APK，展示公开签名示例及改动签名的副本。系统DocumentsUI真正选择文档并给目标App授予URI读取能力；测试不直接伪造onActivityResult。测试依赖、公钥示例和Provider均不进入正式APK，工作流另外检查发行Manifest。

## 检查范围

1. 旧限制通过真实SharedPreferences迁移，签名历史在撤销和恢复内置后保留。
2. 系统文件选择、预览不保存、Activity重建、本人确认后才保存。
3. 修改签名的文件不能提交或生成有效限制。
4. 恢复内置后再次选择同一签名文件仍无操作，不改变撤销或重新生效。
5. 取消系统文件选择不修改规则。
6. 真实应用私有目录写权限失败导致提交未确认，规则处理暂停；恢复写权限并明确恢复内置后才恢复，保留已接收历史。
7. 系统实际绑定SkipService后，在独立测试APK的合成广告窗中执行原有节点点击，确认内容页出现且只记录一次操作。
8. 两个同样符合规则的合成按钮让实际服务报告冲突并拒绝点击。

目标使用与release相同的不可调试acceptance变体，以runner临时测试证书签名；不使用维护者APK私钥。测试还核对实际ApplicationInfo不可调试。仅在隔离模拟器通过shell配置服务及必要AppOp，并设置UiAutomation不压制其他无障碍服务；这不验证用户或厂商的授权界面，也不代表真实广告。正式release依旧未配置runner签名，测试组件不得进入正式包。

本地可在专用空白模拟器上运行：

```sh
bash ./gradlew --no-daemon :app:connectedAcceptanceAndroidTest -Pandroid.testInstrumentationRunnerArguments.isolatedEmulator=true
```

测试使用固定版本AndroidX Test/UiAutomator，参见[官方测试运行器](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner)和[DocumentsProvider说明](https://developer.android.com/guide/topics/providers/create-document-provider)。模拟器启动参数参考[Android官方命令行说明](https://developer.android.com/studio/run/emulator-commandline)。

失败时仅保留新建模拟器的合成页面截图、界面树、环境和测试报告，作为工作流附件保留7天；不采集用户手机、账号或广告现场。完整验证状态见[TESTING](../TESTING.md)。

## 测试环境修复记录

最初扩展中，API26/35的单按钮动作已通过，双按钮测试却复用了先前测试Activity的任务。测试现在显式用NEW_TASK|CLEAR_TASK创建新页面，未修改广告匹配范围。API37先因旧设备默认数据分区耗尽导致系统服务错误，扩大为6GiB并检查至少1GiB可用空间；随后发现旧命令行工具不能正确生成37.0镜像目标，且图形服务发生hasReadColorBufferDma断言崩溃。现固定官方cmdline-tools22.0、核对目标版本，使用当前软件渲染并在改写偏好前确认模拟器用户已解锁。此前失败保留，不计入通过数量。

模拟器数据分区配置见[Google工具源定义](https://android.googlesource.com/platform/prebuilts/android-emulator/+/master/linux-x86_64/lib/hardware-properties.ini)，工具安装见[官方sdkmanager说明](https://developer.android.com/tools/sdkmanager)，图形模式见[官方加速说明](https://developer.android.com/studio/run/emulator-acceleration)。所有设置仅在新建CI模拟器，不操作用户手机或绕过厂商授权。App主代码、生产权限和既有签名0.1.14包均未因此改变。

Activity.recreate不等于进程被杀；只读私有目录不是全部磁盘故障。服务由隔离模拟器shell配置，不等于各厂商授权向导通过。单一合成按钮通过不证明能辨认全部伪装按钮，两个候选拒点也不证明覆盖层或真实广告已经验收。
