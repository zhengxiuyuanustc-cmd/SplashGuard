# 多品牌设置指引与官方来源

更新：2026-10-26，对应本地0.1.10 / versionCode 11候选。公开网站安装包仍为0.1.7。本轮新增的是**设置指引覆盖**：帮助本人找到适用入口并检查状态，没有新增某品牌广告的识别规则，也没有完成全部机型、系统版本或广告样式的实机验收。

## 自动识别和手动查看

帮助页按本机制造商及品牌字段选择初始指引。旧荣耀可能仍带Huawei制造商字段，荣耀优先；真我／一加、iQOO等品牌分别处理。识别仅用于显示文字，不确认完整ROM、地区或版本。

界面提供“自动识别”及12个指引选项。本人可手动查看其他品牌，适合帮家人设置或纠正品牌字段；**选择指引不改变设备设置，不会自动授权，也不把所选品牌当作实测成功**。自检状态始终来自当前Android环境，华为系统家族还须按“关于本机”手选。

## 12个入口的来源与用途

以下中文路径是帮助定位的简要说明；海外文章、特定型号手册与历史公告均注明边界。完整核验见[华为／荣耀](HUAWEI_COMPATIBILITY.md)、[小米／三星](OFFICIAL_XIAOMI_SAMSUNG.md)、[OPPO／真我／一加](OFFICIAL_OPPO_FAMILY.md)、[vivo／iQOO／魅族](OFFICIAL_VIVO_MEIZU.md)。

| 指引入口 | 官方依据与可采用方向 | 适用边界 |
| --- | --- | --- |
| 华为 | [无障碍服务自动关闭](https://consumer.huawei.com/cn/support/content/zh-cn00410039/)、[5及以上后台管理变化](https://consumer.huawei.com/cn/support/content/zh-cn16056653/)、[APK运行环境](https://consumer.huawei.com/cn/support/content/zh-cn16061787/) | 4.x后台旧三开关不套给5/NEXT/6；能打开APK不证明可控制原生鸿蒙应用。4.3官方覆盖不等于本工具实测 |
| 荣耀 | [官方无障碍介绍](https://www.honor.com/cn/support/content/zh-cn15869276/)、[后台检查](https://www.honor.com/cn/support/content/zh-cn00428704/) | 与华为鸿蒙分开；加锁和启动管理不保证长期保活，也不能证明用户所报Android17已适配 |
| 小米／REDMI／POCO | [无障碍与下载的服务](https://www.mi.com/global/support/faq/details/KA-728047/)、[14T Pro后台自启动示例](https://www.mi.com/global/support/faq/details/KA-507611/) | 来自全球FAQ与特定型号，MIUI／澎湃OS及地区可不同；先搜索无障碍、后台自启动，只为本工具检查 |
| OPPO | [无障碍上级入口示例](https://support.oppo.com/lk/answer/?aid=neu12906)、[自启动管理](https://support.oppo.com/co/answer/?aid=neu119)、[中文省电说明](https://support.oppo.com/cn/answer/?aid=SI2105920) | 旧版与较新ColorOS入口有差异；省电优化可能限制后台及取消卡片加锁，不把旧菜单称为全系通用 |
| realme真我 | [中文无障碍上级入口](https://www.realme.com/cn/support/kw/doc/2075629)、[应用信息与电池使用](https://www.realme.com/in/support/kw/doc/2083586) | UI6、UI4/5、UI3分别有辅助功能／其他设置／系统设置示例；不要求开启教程里的内容放大，不要求额外的启动其他App能力 |
| 一加 | [国行服务入口](https://service.oneplus.com/cn)、[国行OnePlus9历史中文手册](https://service.oneplus.com/content/dam/support/user-manuals/cn/OnePlus%209%20User%20Manual_CN.pdf)、[海外OxygenOS14手册](https://service.oneplus.com/content/dam/support/user-manuals/common/OxygenOS_14.0_User_Manual.pdf) | 先确认国行ColorOS还是海外OxygenOS；品牌名不确定ROM，不用海外或历史菜单覆盖新机 |
| vivo | [Y36s对应官方手册](https://www.vivo.com.cn/instructions/index?androidVersion=13.0&funtouchVersion=13.5&modelName=PD2318&modelNameExt=vivo+Y36s&pageId=233966)、[官方后台入口](https://kefu.vivo.com.cn/robot/imgmsgData/3dfcdb616d0542b4baa4e9ead6d75c5e/index_1.html) | 快捷与辅助、自启动、后台耗电管理只作入口参考；不移植消息通知排障中的联网、通知或还原设置要求 |
| iQOO | 与vivo共用已核实的[官方后台入口参考](https://kefu.vivo.com.cn/robot/imgmsgData/3dfcdb616d0542b4baa4e9ead6d75c5e/index_1.html) | 仍须确认OriginOS／Funtouch OS、地区及本机实际列表；共用资料不证明每台iQOO具备同样菜单或开屏专用动作权限 |
| 三星Galaxy | [已安装无障碍应用](https://www.samsung.com/us/support/answer/ANS10001906/)、[休眠／深度休眠与例外](https://www.samsung.com/us/support/answer/ANS10003442/) | 主要为美国／全球说明，One UI、运营商和国行名称可变；只检查本工具所在列表，不解除所有App限制 |
| 魅族 | [魅族官网](https://www.meizu.com/)、[16th官方固件记录](https://flyme.com/firmwarelist-174.html) | 新版后台／无障碍完整路径未核实，保持搜索优先。固件公告证明特定历史功能存在，不能推广为所有新机都支持 |
| Google／Pixel | [Android受限设置](https://support.google.com/android/answer/12623953?hl=zh-Hans)、[闲置应用管理](https://support.google.com/android/answer/13627979?hl=zh-Hans) | Android通用资料，非Pixel各型号认证；只按本人手机的实际单App选项处理 |
| 其他／不确定 | 保留未知，参考[Android通用受限设置说明](https://support.google.com/android/answer/12623953?hl=zh-Hans)并搜索本机设置 | 没有已核实的品牌专属路径，不自动猜测系统或套用其他厂商菜单 |

所有入口只提供文字和本人主动打开的标准系统设置／官网链接。App未新增联网权限；内置文字可离线阅读，点击官网交给系统浏览器，需要浏览器联网。

## 只使用开屏／广告专用的动作限制

vivo官方账号的[OriginOS升级公告](https://bbs.vivo.com.cn/newbbs/thread/38069132)在**X100 Ultra / 15.1.12.1.W10.V000L1**的具体记录中给出动作与方向的开屏专用选择。Flyme的[16th / 8.1.9.0A更新记录](https://flyme.com/firmwarelist-174.html)也有仅在应用开屏时拒绝加速度调用的历史依据。它们证明部分系统有更细的控制，不是所有vivo、iQOO或魅族机型的支持清单。

帮助页仅建议本人在实际手机上看到明确“仅开屏时禁止／拒绝”等选项后，针对有广告的目标App设置；缺少专用项时保留正常方向、加速度和体感权限。工具不会读取、修改或确认该开关，更不会把打开指南当作设置完成。请实际验证广告与正常使用。

其他品牌本轮没有核实到同样适用范围明确的系统专用选项，不代表所有机型永远没有。优先使用目标App内部广告专用开关；B站9.15.0的既有例子仍在防摇一摇页。关闭个性化广告、自动旋转或整体动作权限都不能等同于只关闭摇一摇广告。

## 把系统限制与漏识别分开

| 情况 | 官方依据 | 应如何解释 |
| --- | --- | --- |
| 部分侧载应用显示受限制设置 | [Android受限设置说明](https://support.google.com/android/answer/12623953?hl=zh-Hans) | 部分步骤适用于Android13及以上。仅在本人确认来源可信且本机提供对应单App入口时，按系统提示操作；没有入口不承诺绕过 |
| 系统明确提示高级保护／设备保护限制无障碍 | [Android高级保护说明](https://support.google.com/android/answer/16339980?hl=zh-Hans) | 该模式可限制为经验证的无障碍工具。本项目未读取本机该模式状态，不把API版本或普通权限页推断为此原因，也不要求关闭全机保护 |
| 无障碍已连接，但某些敏感页面不可见或不可操作 | [Android防点按劫持说明](https://developer.android.com/privacy-and-security/risks/tapjacking)、[Android官方安全说明](https://developer.android.com/blog/posts/enhancing-android-security-stop-malware-from-snooping-on-your-app-data) | Android14起敏感标记可约束读取与动作；Android16起还能约束相关模拟触点。不是扩大普通权限或改点坐标就能解决，项目不虚报为残障辅助工具绕过 |
| 长期不用后失去后台工作能力 | [Android闲置应用管理](https://support.google.com/android/answer/13627979?hl=zh-Hans) | 系统可能撤销权限或停止后台；如需持续使用，仅针对本工具检查实际选项，再返回确认服务连接 |
| 已连接且出现广告仍未跳过 | 项目的[离线规则与诊断说明](OFFLINE_RULES.md) | 可能是未选目标App、未知布局、不可访问控件或保守拒点，应看固定诊断原因；不能继续无条件追加权限 |

“Android17”本身不足以确定上述任何一种原因。荣耀用户的具体型号、MagicOS、目标App版本和失败控件尚未齐备，因此不能宣布该个案已解决，亦不能直接判不支持。

## 验证与后续收集

本轮630项纯Java检查已通过，用于验证品牌优先级、手动选择恢复、各主题文案和官网来源、开屏专用限制边界等策略。它不证明Android界面、大字体、实际系统入口或真实广告成功率。本候选SDK35整包构建、对齐及v2/v3验签亦通过，实际uses-permissions为空；APK校验值见[TESTING.md](../TESTING.md)。尚未实机验收；此处记录本地结果，发布后的远程检查以[PR #4](https://github.com/zhengxiuyuanustc-cmd/SplashGuard/pull/4)为准。当前公开安装包仍是0.1.7。

贡献新路径时请提供品牌／型号、完整系统及地区、准确菜单名称、官方来源与测试现象，并明确是授权、后台断连、未出现广告、漏跳过还是误跳转。无需提供PIN、IMEI、序列号、账户界面或完整系统日志。官网路径与真实用户样本应分别记录；没有广告不记为失败，不能把其他机型一次成功推广成全系支持。
