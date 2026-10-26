# 小米／红米／POCO与三星官方设置核验

核验日期：2026-10-26。本文只记录厂商一级资料及可供本工具参考的入口，没有在相应手机上安装或实测。官方菜单存在不表示开屏清净已完成该机型适配，也不表示调整后台选项必然保活。中文界面名称为对应英文项目的解释性翻译，实际名称按本机为准。

## 小米、红米与POCO

将这些品牌归到小米帮助入口用于人工查阅，不把同品牌当作同一版本。MIUI、澎湃OS、不同区域固件及POCO机型应核对完整版本；下列具体型号或全球文档不能证明全部国内固件采用同一路径。

| 目的 | 官方依据与适用范围 | 可用于本工具的说明 |
| --- | --- | --- |
| 开启无障碍服务 | [小米全球无障碍功能FAQ KA-728047](https://www.mi.com/global/support/faq/details/KA-728047/)：设置进入无障碍；General下的Downloaded apps可启停已下载服务。该页明确菜单随澎湃OS版本、型号变化 | 在设置搜索“无障碍”，找到“下载的应用／已下载服务”后本人选择开屏清净并确认。国内旧入口可能经过“更多设置”，不把TalkBack页面当作本服务 |
| 国内基础入口 | [小米中文无障碍官网](https://accessibility.mi.com/)的搜索可见内容给出“系统设置→更多设置→无障碍→TalkBack”；本次正文为动态脚本，未完整读取 | 仅辅助确认“更多设置→无障碍”这个旧入口；不从TalkBack专用步骤推导第三方服务开关或要求打开TalkBack |
| 自启动机制 | [小米澎湃OS开发者平台：自启动权限管理](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1624)，页面标2024-09-25更新 | 自启动由用户决定；需说明用途，不自动授权。开启自启动不能代替无障碍实际连接，也不代表重启后服务必然恢复 |
| 特定新机菜单示例 | [Xiaomi 14T Pro后台自启动FAQ KA-507611](https://www.mi.com/global/support/faq/details/KA-507611/)为设置→Apps→Permissions→Background autostart | 只为开屏清净检查“后台自启动”；若没有该层级，使用设置搜索，不宣称所有澎湃OS固件都相同 |
| 另一种新菜单与后台限制 | [REDMI Watch 6 NFC连接FAQ KA-700752](https://www.mi.com/global/support/faq/details/KA-700752/)以小米手机上的Mi Fitness为例：Apps→Mi Fitness→Power→No restrictions；Apps→Background autostart；最近任务长按后锁图标 | 本文迁移的仅是系统入口参考：将示例App换成本工具，只针对本工具检查后台活动及最近任务锁定。该FAQ的手表通知、蓝牙及关闭全机省电等步骤不适用于本工具，不照搬 |
| 旧MIUI菜单依据 | [小米官方MIUI 14通用用户手册（英文PDF）](https://alsgp0.fds.api.xiaomi.com/gl123/Generic%20User%20Guide%20for%20MIUI%2014/Generic%20User%20Guide%20for%20MIUI%2014.pdf)搜索索引的权限章节：Settings→Apps→Permissions→Autostart，并可区分本应用启动与由其他应用启动 | 只作为MIUI 14旧版参考；未完整读取大PDF，不据此声称MIUI 13、澎湃OS3和所有POCO沿用完全相同入口 |

无障碍授权能力较大。小米全球FAQ本身也提醒服务能够读取界面并执行操作；不应把它包装成无风险的普通开关。普通权限列表为空、没有相机／通讯录／联网开关，不是“没给全权限”。

## 三星Galaxy／One UI

本轮可直接读取的详细菜单主要来自三星美国/全球开发者官方英文文档，不把海外菜单翻译当成国行精确标签。不同One UI版本、型号及运营商可能改变名称；优先在本机设置搜索对应项目。

| 目的 | 官方依据与适用范围 | 可用于本工具的说明 |
| --- | --- | --- |
| 无障碍服务入口 | [三星官方高级无障碍设置 ANS10001906](https://www.samsung.com/us/support/answer/ANS10001906/)的Installed apps and services部分明确设置→Accessibility→Installed apps，并可逐项开关 | 设置→辅助功能／无障碍→已安装的应用（部分版本称已安装服务），找到开屏清净。不要只打开快捷按钮却把它视为服务已授权 |
| 解除被休眠的本工具 | [三星官方Sleeping apps ANS10003442](https://www.samsung.com/us/support/answer/ANS10003442/)：Battery and device care→Battery→Background usage limits，区分Sleeping、Deep sleeping、Never sleeping列表 | 在“后台使用限制”中查看本工具；若被列入休眠／深度休眠，移出并按需加入“不自动休眠的应用”。仅处理本工具，不要求关闭全机未使用App休眠 |
| 开发者机制与版本边界 | [Samsung Application Management](https://developer.samsung.com/mobile/app-management.html)明确休眠对后台工作有约束，深度休眠App在退出后不活动，提供例外列表；也专门讨论One UI6/Android14前台服务政策 | 后台例外不是无障碍授权，更不能用前台服务政策保证本项目的AccessibilityService永远存活。该页的几天休眠阈值注明可变，不写死为本工具保证 |
| 单App电池选项 | [三星官方音频中断排查](https://www.samsung.com/us/support/troubleshoot/TSG10001337/)给出Settings→Apps→具体App→Battery→Unrestricted，注明型号/软件版本可能不同 | 这是音频App案例，可作单App电池页面参考；仅在本机存在且确需后台时检查本工具。不移植其全局省电、数据节省和移动数据建议：本工具无联网权限 |

三星开发者页还公开后台例外列表的Intent示例，但它使用厂商action/package。此轮建议仍用标准系统设置/本App详情页加人工路径；未实机验证前不把私有入口当成可靠自动设置，更不通过它自动改变授权。

## 受限设置与新Android边界

未查到足够适用范围明确的“小米/三星专用受限设置解锁方案”，不能将社区教程或其他App的授权路径当作通用修复。Android通用[受限设置说明](https://support.google.com/android/answer/12623953?hl=zh-Hans)只在适用版本、可信来源且本机实际提供对应单App操作时参考；没有选项时保留提示并核对系统策略，不承诺绕过。

新增的中文厂商佐证：[小米Android17应用适配指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2297)包含高级保护模式（AAPM）限制非工具型无障碍服务的说明。它说明一种可能的平台限制，不证明当前荣耀“Android17”用户就是这个原因；Android版本、ROM支持及是否启用保护应分开确认。此项目不能虚报自己是残障辅助工具来规避限制，也不要求用户关闭整机安全防护。具体平台API由主文档按Android官方资料核验。

## 摇一摇／广告专用限制：本次没有核实到系统开关

对小米/三星官网进行“摇一摇、传感器、开屏、应用跳转”等检索后，本轮**没有核实到**适用范围明确、只针对广告/开屏的系统传感器禁止开关或普遍适用的广告跳转确认设置。该结论是本轮资料的边界，不等于证明所有型号均无此功能。

小米有[广告审核规范](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1998)，要求关闭标志有效、禁止素材假关闭标志及高灵敏度诱导跳转；[2026年常见问题解读](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2309)也讨论轻微动作触发和关闭障碍。这些是对广告提供者的要求，**不是用户可操作的系统拦截开关**。不能据此写“小米已支持系统级摇一摇拦截”。

因此这两类设备的防摇一摇说明应保留：先找目标App自身“摇一摇广告／允许广告访问传感器”等广告专用选项；没有核实到时，明确暂不能保证关闭该跳转。自动跳过只缩短广告显示机会，不能当作已经关闭传感器。不要关闭全局设备方向、加速度计、自动旋转或正常体感功能来替代，也不要把关闭个性化广告当成广告消失或摇一摇已禁用。

## 可直接交给App品牌帮助的简短文案

**小米／红米／POCO：**先确认MIUI或澎湃OS及完整版本。在设置搜索“无障碍”，找到下载的应用并本人开启本服务。后台可搜索“后台自启动”，只为开屏清净检查；若服务常被清理，再按实际页面检查本工具的电池后台限制和最近任务锁定。菜单来自官方型号/全球资料，具体名称以本机为准。完成后返回检查“服务实际连接”。

**三星Galaxy：**在设置搜索“辅助功能／无障碍”，进入已安装的应用并本人开启本服务。后台检查电池的“后台使用限制”：本工具若在休眠或深度休眠列表，按需移出，并检查“不自动休眠的应用”；不要因此解除所有App的限制。服务授权与后台例外分别检查，仍不保证系统不会回收。

**两类设备的防摇一摇：**本轮未核实广告专用系统开关；优先查目标应用自身广告设置。保留全局方向和体感功能，不照搬其他品牌入口。出现受限/安全提示时，记录提示与完整系统版本，不自动关闭防护。
