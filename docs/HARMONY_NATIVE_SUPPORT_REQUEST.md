# 原生无障碍能力咨询草稿

供维护者本人通过华为开发者支持提交。尚未发送，不需要在公开材料中提供密码、PIN、设备序列号、账号界面或私人日志。

主题：商业HarmonyOS三方应用的前台节点读取／节点动作能力及用途开放条件

我们维护免费开源的本地广告辅助工具，当前是Android应用，计划评估原生HarmonyOS版本。用途是用户明确选择App并本人授权后，仅在短暂开屏时段尝试点击明确的广告跳过节点；多候选或场景不明即停止。没有联网权限、截图上传、任意远程指令、全局方向禁用或支付／授权自动化。

当前官方Accessibility Kit简介说明仅开放状态查询与事件发送，不支持查询界面节点及屏幕内容。与此同时ExtensionAbility类型表列出三方可实现AccessibilityExtensionAbility；旧节点/动作接口及连接回调标为API12起废弃。希望确认：

1. 当前商业HarmonyOS手机面向普通三方是否存在受支持的跨App前台节点读取、原生节点动作接口？如有，请提供当前接口及SDK/系统版本、文档，不以OpenHarmony旧样例代替。
2. 若属于受控合作能力，以上自愿、有限广告辅助用途是否可申请？主体资格、用途范围、ACL／签名、能力申请及市场审核分别需要什么？
3. 旧接口废弃后有何正式迁移路径？仅有服务类型可注册，是否仍不能查询节点或执行动作？
4. 用户授权、断开、后台生命周期、暂停与卸载有哪些规定？如何在合法开发环境先对独立离线测试App验证，避免访问真实账号页面？
5. 若此用途不支持，请明确能力限制；我们将保留设置指引并准确标注原生自动跳过未支持，不虚报已适配。

附官方出处与工程验证范围：HARMONY_NATIVE_FEASIBILITY.md、TESTING.md。用户报告的个案为Mate 80 Pro / 7.0.0.109 SP6，开屏清净0.1.7，系统提示本应用暂不支持获取系统无障碍权限。我们没有读取该设备，用户仅表示“应该是默认的”安装方式，具体APK安装／兼容环境未知；请确认此限制与原生受支持能力的关系及正式解决途径。该个案不是本项目的原生实测；维护者目前没有纯血鸿蒙验证机，DevEco/SDK版本待建立合法验证环境后补充。

## 官方联系入口（2026-10-26核查，尚未发送）

首选[在线提单](https://developer.huawei.com/consumer/cn/support/feedback/)，这是[官方支持页](https://developer.huawei.com/consumer/cn/support/)“开始提单”的实际目标。如直达页未显示，返回支持页再点“在线提单”；登录等后续要求以页面实际提示为准。本次未登录查看工单表单，不能保证具体分类名称、账号资质要求或接入审批条件。描述中注明HarmonyOS / Accessibility Kit，请转相关技术团队。

官方支持页标注在线提单通常1—2个工作日应答；不是本项目对解决时间的承诺。备用为官网公布邮箱devConnect@huawei.com，见[官方联系页](https://developer.huawei.com/consumer/cn/support/contactUs/)和[2026-09-28文档中心公告](https://developer.huawei.com/consumer/cn/notice/detail/?id=9002212768432948003)。这是开发者联盟联系邮箱，不声明为Accessibility Kit专属邮箱。也可在[官方社区](https://developer.huawei.com/consumer/cn/forum/)选择“我要提问题”；社区是公开交流渠道。

## 可直接提交的短版

标题：HarmonyOS 7原生跨应用无障碍能力咨询及APK授权不支持提示

您好，我们维护免费开源工具开屏清净（SplashGuard）。当前Android版只在用户选定App的短暂开屏时段识别明确的跳过按钮，冲突拒点，可随时暂停，无联网权限。现计划评估原生HarmonyOS版本。

用户报告Mate 80 Pro / HarmonyOS 7.0.0.109 SP6，运行开屏清净0.1.7时提示“为保障您的隐私安全，应用暂不支持获取系统无障碍权限”。安装／运行方式未知，仅称使用默认安装；我们未连接该机，未完成原生验证。

请协助确认：
1. 该提示对应怎样的能力限制？是否存在面向该APK运行环境的正式支持方式？
2. 普通第三方原生HarmonyOS应用，是否有受支持的跨App前台节点读取和节点点击接口？请提供当前SDK及系统范围。
3. 若能力受控开放，用户自愿授权的有限广告辅助用途能否申请，具体资格、申请入口、签名及上架条件是什么？若不支持，也请明确说明。
4. 当前Accessibility Kit简介不支持节点查询，而旧AccessibilityExtensionContext读取／动作接口标记废弃；此用途是否存在正式迁移方案？

项目：https://github.com/zhengxiuyuanustc-cmd/SplashGuard
感谢，请转HarmonyOS / Accessibility Kit相关技术团队。
