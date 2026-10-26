# 原生鸿蒙可行性与当前边界

核验：2026-10-26。当前工程是Android APK，没有原生HAP工程、原生签名或纯血鸿蒙实机验收。用户明确目前没有可用于验证的纯血鸿蒙手机。本文件不是原生适配已完成的声明。

## 最新明确结论

华为当前[Accessibility Kit简介](https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/accessibilitykit-overview)将支持范围列为无障碍状态查询、无障碍事件发送，并明确不支持通过节点查询获取界面节点及屏幕内容。仅为本应用添加朗读属性、查询读屏状态或发送主动播报事件，不能实现读取其他App开屏按钮并自动关闭广告。

同时，[ExtensionAbility概述](https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/extensionability-overview)的类型表将AccessibilityExtensionAbility列为允许第三方实现。这一通用表格不能覆盖具体Kit当前公布的能力限制，亦不能证明广告跳过用途已获发布许可。

具体[AccessibilityExtensionAbility参考](https://developer.huawei.com/consumer/cn/doc/harmonyos-references/js-apis-application-accessibilityextensionability)的连接、事件等旧回调，以及[AccessibilityExtensionContext参考](https://developer.huawei.com/consumer/cn/doc/harmonyos-references/js-apis-inner-application-accessibilityextensioncontext)中的节点查询、performAction等方法均有API12起废弃标记。injectGesture先在API10废弃，文中提到的同步替代也从API12废弃。不能拿仍可读到的旧例子当作当前商业HarmonyOS三方跨App能力保证，也不能从废弃标记推断每一设备一定会报错。

这次没有找到已核实、适用于本项目用途的当前公开替代接口或受控开放审批流程。结论是**普通公开接口路线尚不足以实现本项目原生自动跳过，原生功能暂不支持**；不是宣称任何第三方永远无法实现，也不是建议使用系统签名、Root或自动点击绕过限制。

## 已收到的HarmonyOS 7用户反馈

2026-10-26，用户补充机型为Mate 80 Pro、系统为7.0.0.109 SP6，并提供截图。截图中本工具显示自动跳过未运行；系统提示“为保障您的隐私安全，应用暂不支持获取系统无障碍权限”。这是用户报告与画面证据，不是维护者在该机上的实测；用户随后确认本工具为0.1.7；安装方式仅描述为“应该是默认的”，具体安装／运行环境仍未知，不将其自动映射为原生安装、卓易通或出境易。

可以确认该次安装／运行环境未向本工具提供所需无障碍权限，不能将其归为普通“忘记授权”，也不能要求用户反复开启旧入口。仅凭截图尚不能确定APK的安装来源、使用了何种兼容环境、限制来自哪一层，以及所有HarmonyOS 7设备是否相同；不可据此宣布全部三方无障碍均被禁止。B站原生版的广告专用设置路径也尚未在此机核对。

解决该个案的当前措施是明确标记自动跳过不可用并保留手动设置指引；待官方确认原生能力与用途开放条件后再做独立原生验证。不能通过ADB强授予、关闭全机安全保护或禁用整个应用的方向权限来冒充修复。不会将私人截图、账号、状态栏或设备唯一标识上传到仓库。

## 实际解决路线

1. 向华为开发者支持确认：原生商业HarmonyOS对普通第三方是否有受支持的前台节点读取与节点动作接口；若属于受控合作能力，具体用途、资格、申请、签名、审核条件是什么。已准备[咨询材料](HARMONY_NATIVE_SUPPORT_REQUEST.md)，用户已通过邮件发送主要内容与对应文件，等待答复；助手未代发。
2. 如得到可用正式路径，再建立独立ArkTS/HAP验证工程：先只验证本人授权、服务连接、受控测试App节点读取与按钮动作、暂停/断开。不要一开始接入全机扫描或真实账号页面；不使用废弃API来冒充已适配。
3. 有DevEco/SDK、合法签名及真机条件后，在同一系统版本验证上述能力；再迁移有限匹配、冲突拒点、12秒/两轮预算及结果归属。Android测试、OpenHarmony样例或模拟器能编译，都不能替代商业HarmonyOS真机证据。
4. 若官方确认该能力不对本用途开放，则保留原生指南／网页指南与目标App内广告专用设置；不能通过改变APK扩展名、装兼容工具或多开几个权限解决原生跨App点击。

本机限定范围的只读检查未发现DevEco/Harmony SDK/hdc工具。没有因此创建不可编译的假原生工程，也没有下载非官方SDK或索要账号密码。当前核心规则与离线夹具可继续独立改进，但不把Java模型叫作原生鸿蒙App。

## 鸿蒙方向权限的官方参考与本项目选择

华为[第三方广告排查](https://consumer.huawei.com/cn/support/content/zh-cn16002151/)列出按应用管理设备方向的入口：4.2/4.3在应用和服务的权限管理中，6.0.0.120及以上在隐私和安全中；5.0至6.0.0.115不涉及该操作。

该说明并未限定为只在广告或开屏期间生效，也没有保证对正常动作功能无影响。因此按用户保留正常方向/体感功能的要求，本项目只列为有版本条件的参考，不默认或自动关闭，不把它等同OriginOS/Flyme的仅开屏选项。优先目标App自己的广告专用开关；原生版目标App是否提供此开关仍需按实际版本核对。

## 一手正文读取与证据

官网正文是动态加载，本次进一步从官网页面本身公开使用的匿名文档内容接口取得正文，没有登录、调用受控能力或绕过授权。私有work缓存仅用于核验，未上传官网完整正文。以下为取得正文的版本/更新时间和内容摘要校验值；这些是文档元数据，不是SDK或设备系统版本。

| 官方文档标识 | 文档修订 | 元数据更新时间 | 正文SHA-256 |
| --- | --- | --- | --- |
| accessibilitykit-overview | V169 | 2026-10-22 09:01:53 | `682984f0cc535f80a7ed5aa4950d8fe249ab6c70f96b2c2f05817ee52316aea7` |
| extensionability-overview | V247 | 2026-10-22 09:02:49 | `c1578db072e8d489cb8a684f7b6db62f93794d01cde045908d212e49292f19a2` |
| js-apis-application-accessibilityextensionability | V246 | 2026-10-22 09:07:49 | `2a2b98f497d38f53c7ee3a6bcfc00bd66ca990b8d46d7a9283c38249201e066a` |
| js-apis-inner-application-accessibilityextensioncontext | V37 | 2026-10-22 09:07:51 | `eb752d932a908aa6e0d6670bcdac7695ad580886e91e52a21cfd5eb4c990f2df` |

已核实的是上述公开支持范围与废弃标记。普通开发者审批、广告跳过用途、原生分发资格与实际设备行为仍需正式确认；不引用普通论坛帖替代厂商答复。
