package org.splashguard.app;

import org.splashguard.app.DeviceGuidePolicy.Vendor;

/** Offline summaries of official sources, not a device certification or a permission bypass. */
public final class BrandGuides {
    private BrandGuides() {}
    public static final String RESTRICTED_URL="https://support.google.com/android/answer/12623953?hl=zh-Hans";
    public static final String PROTECTION_URL="https://support.google.com/android/answer/16339980?hl=zh-Hans";
    public static final String UNUSED_URL="https://support.google.com/android/answer/13627979?hl=zh-Hans";
    private static final String VIVO_SHAKE="https://bbs.vivo.com.cn/newbbs/thread/38069132";
    private static final String MEIZU_SHAKE="https://flyme.com/firmwarelist-174.html";

    public static boolean handles(Vendor vendor){
        return vendor!=null&&vendor!=Vendor.HUAWEI&&vendor!=Vendor.HONOR&&vendor!=Vendor.OTHER;
    }
    public static String guide(Vendor vendor,String topic){
        String access,background,scope;
        if(vendor==null)return "本品牌具体入口尚未核实，请在系统设置搜索无障碍，核对本服务。";
        switch(vendor){
            case XIAOMI:
                scope="小米／REDMI／POCO：先核对MIUI或澎湃OS的完整版本和地区。下述入口来自小米全球FAQ与特定型号示例，不代表所有国内固件一致。";
                access="设置搜索“无障碍”，查找“下载的应用／已下载服务”，选择开屏清净并由本人授权。部分旧版本上级入口为“更多设置”。不要开启TalkBack代替本服务。";
                background="在设置搜索“后台自启动”，只检查开屏清净。Xiaomi 14T Pro官方示例：设置 → 应用 → 权限 → 后台自启动；其他版本可能直接在应用中。若经常被清理，再在本应用信息的电池／耗电页面检查后台限制，必要时仅为本工具选择无限制；部分版本可在最近任务长按后加锁。上述后台入口不保证长期存活。";break;
            case SAMSUNG:
                scope="三星Galaxy：路径主要来自三星美国／全球官方说明，国行名称、One UI版本及型号可能不同；可优先用设置搜索。";
                access="设置 → 辅助功能／无障碍 → 已安装的应用（部分版本称已安装服务），找到开屏清净，由本人开启。快捷按钮不是服务实际连接的证明。";
                background="设置 → 电池和设备维护／设备维护 → 电池 → 后台使用限制。若开屏清净在休眠或深度休眠列表中，按需移出，再检查“不自动休眠的应用”。只处理本工具，不解除所有应用的休眠限制；返回后确认服务连接。";break;
            case OPPO:
                scope="OPPO：先核对ColorOS完整版本。官方海外资料的旧示例与较新版本入口有变化，不能把某一条路径套用于全部手机。";
                access="在设置搜索“无障碍”，再找开屏清净服务；上级入口可能在“其他设置”“系统设置”或“辅助功能与便捷”，以本机为准。不要启用不相关的辅助功能代替本服务。";
                background="在设置搜索“自启动”，只检查开屏清净；ColorOS 12起的官方示例是设置 → 应用 → 自启动。较早系统可能在手机管家 → 隐私权限 → 启动管理。官方说明较新省电模式可能限制后台并取消卡片加锁，因此加锁不等于永久后台；不要求关闭全机省电。";break;
            case REALME:
                scope="realme真我：先核对realme UI及地区。以下为官方版本示例，其他版本先用设置搜索。";
                access="设置搜索“无障碍”。中文官方示例：UI6.0从“辅助功能”进入，UI4／5从“其他设置”进入，UI3从“系统设置”进入。到无障碍页面后再按实际列表找到开屏清净；不要求打开教程中的内容放大。";
                background="长按开屏清净图标 → 应用信息 → 电池使用，按本机选项检查允许后台活动和自启动。不要把“启动其他应用”等额外能力当作必需权限。深度清理可能停止服务，之后应回App重新检查实际连接；卡片加锁不保证不会被清理。";break;
            case ONEPLUS:
                scope="一加：先区分国行ColorOS与海外OxygenOS。品牌名不能确认所用系统，不将海外教程当作国行通用路径。";
                access="先在设置搜索“无障碍”，找到开屏清净。历史国行OnePlus 9／ColorOS11手册将上级入口放在“其他设置”；若本人确认是海外OxygenOS14，官方手册也有其他设置中的无障碍示例。新版本以实际页面为准。";
                background="设置搜索“自启动”，只核对开屏清净；本人确认是OxygenOS14时，可参考设置 → 应用 → 自启动。电池后台项以当前系统为准，不能照搬其他地区或旧手册，返回检查实际服务连接。";break;
            case VIVO:case IQOO:
                scope="vivo／iQOO：先核对OriginOS或Funtouch OS及地区。官方客服后台示例与特定机型手册只作为入口参考，不证明所有版本相同。";
                access="设置搜索“无障碍”，查找并本人开启开屏清净。vivo Y36s对应官方手册示例在“快捷与辅助”中；其他版本以实际服务列表为准，不开启TalkBack代替本服务。";
                background="设置搜索“自启动”“后台耗电管理”。官方客服示例：设置 → 应用与权限 → 权限管理 → 权限 → 自启动；设置 → 电池 → 后台耗电管理。只针对开屏清净按需允许后台；部分版本可在多任务界面下滑卡片加锁。仍可能被系统停止，不需要照搬消息通知排障的联网、通知或全局设置。";break;
            case MEIZU:
                scope="魅族Flyme：本轮尚未核实当前机型的完整无障碍及后台菜单，保持手动搜索，不套用旧版多级路径。";
                access="在系统设置搜索“无障碍”，找到开屏清净并由本人检查授权。若找不到已下载服务列表，请保留完整Flyme版本及提示，按官网支持核对。";
                background="在设置搜索“后台”“应用启动管理”，只检查开屏清净；具体选项以本机为准。官方旧入口实时页面未完整取回，不将旧手机管家菜单写成全系固定路径。回App确认服务实际连接。";break;
            case GOOGLE:
                scope="Google／Pixel及接近原生Android：以下为Android官方通用说明；设置名称随系统变化，不代表具体手机已实测。";
                access="在设置搜索“无障碍”，按本机服务列表选择开屏清净并由本人授权。若显示受限或高级保护提示，查看下方系统限制说明；不以普通权限页为空判断未开全权限。";
                background="若长时间不用后服务停止，可在设置 → 应用 → 开屏清净中查看“未使用的应用／未使用时暂停活动”。Android官方说明这类优化可停止后台及撤销权限；如确需持续运行，仅为本工具按实际选项调整。返回重新检查服务，不要求为所有应用关闭优化。";break;
            default:return "本品牌具体入口尚未核实，请在系统设置搜索无障碍及本应用的后台设置。";
        }
        String body="accessibility".equals(topic)?access:"background".equals(topic)?background:access+"\n\n后台："+background;
        return scope+"\n\n"+body+"\n\n这些是设置入口参考，不是广告布局已适配或长期保活承诺。授权已开仍需核对服务实际连接；已连接但广告未跳过，应检查应用选择和规则诊断。";
    }

    public static String officialUrl(Vendor vendor,String topic){
        if(vendor==null)return "";boolean access="accessibility".equals(topic);
        switch(vendor){
            case XIAOMI:return access?"https://www.mi.com/global/support/faq/details/KA-728047/":"https://www.mi.com/global/support/faq/details/KA-507611/";
            case SAMSUNG:return access?"https://www.samsung.com/us/support/answer/ANS10001906/":"https://www.samsung.com/us/support/answer/ANS10003442/";
            case OPPO:return access?"https://support.oppo.com/lk/answer/?aid=neu12906":"https://support.oppo.com/co/answer/?aid=neu119";
            case REALME:return access?"https://www.realme.com/cn/support/kw/doc/2075629":"https://www.realme.com/in/support/kw/doc/2083586";
            case ONEPLUS:return "https://service.oneplus.com/cn";
            case VIVO:case IQOO:return access?"https://www.vivo.com.cn/instructions/index?androidVersion=13.0&funtouchVersion=13.5&modelName=PD2318&modelNameExt=vivo+Y36s&pageId=233966":"https://kefu.vivo.com.cn/robot/imgmsgData/3dfcdb616d0542b4baa4e9ead6d75c5e/index_1.html";
            case MEIZU:return "https://www.meizu.com/";
            case GOOGLE:return access?RESTRICTED_URL:UNUSED_URL;
            default:return "";
        }
    }
    public static String shakeGuide(Vendor vendor){
        String common="\n\n如果没有明确的开屏／广告专用选项，请保留整体设备方向、加速度与正常体感权限，优先查看目标App内的广告专用开关。B站9.15.0的已核对示例：我的 → 设置 → 隐私权限设置 → 关闭“允许广告访问传感器”，其他版本按实际页面确认。关闭个性化广告不等于关闭摇一摇。自动跳过也不代表已禁用传感器。";
        if(vendor==Vendor.VIVO||vendor==Vendor.IQOO){
            return "部分OriginOS版本有开屏专用的动作权限选项。已核实官方升级公告中的X100 Ultra／15.1.12.1.W10.V000L1示例：\n\n设置 → 隐私 → 权限管理 → 权限 → 访问设备动作与方向。\n\n只检查有摇一摇广告的目标应用，只有实际提供“仅开屏时禁止”等明确开屏专用选项时，才由本人选择。不要选长期禁止方向／动作访问；其他vivo、iQOO、OriginOS或海外Funtouch版本是否有该项需到机上核对。\n\n本工具未在本机实测，不会读取或自动修改该系统选项；设置后请验证广告以及正常旋转、体感功能。"+common;
        }
        if(vendor==Vendor.MEIZU){
            return "部分Flyme版本提供开屏专用的加速度传感器限制。已核实16th／Flyme8.1.9.0A官方更新记录，不能据此确认所有新机型支持。\n\n可在实际手机管家／权限设置中查找加速度传感器管理；该公告未给完整菜单路径。只有实际看到“仅在应用开屏时拒绝”这类专用选项，才针对有广告的目标App使用。缺失时不要改为长期禁用加速度计。\n\n本工具未在本机实测，也不会自动读取或修改此系统选项。"+common;
        }
        return "本品牌本轮尚未核实适用范围明确、仅限开屏广告的系统动作传感器开关；不代表所有机型永远没有。请先找目标应用自身“摇一摇广告”“广告访问传感器”等广告专用设置，不照搬vivo或Flyme的步骤。"+common;
    }
    public static String shakeUrl(Vendor vendor){
        if(vendor==Vendor.VIVO||vendor==Vendor.IQOO)return VIVO_SHAKE;
        if(vendor==Vendor.MEIZU)return MEIZU_SHAKE;
        return "";
    }
    public static String platformHelp(int sdk){
        String text="系统授权、后台运行、当前服务连接和广告规则是不同问题，不能靠开启无关权限互相替代。";
        if(sdk>=33)text+="\n\nAndroid13及以上部分侧载应用可能遇到“受限制的设置”。仅在确认下载来源可信且系统实际为本应用提供该入口时，可查看应用信息 → 更多 → 允许受限设置，再由本人按提示认证并检查无障碍。菜单缺失时不要照搬其他手机路径，也不要关闭全机安全防护。";
        text+="\n\n若系统明确提示“高级保护／设备保护”限制无障碍：Android官方说明该模式可只允许经验证的辅助工具。本App没有读取该模式状态，不能判断本机是否受此影响；这与普通受限设置不相同，不能承诺同一入口可解决。可保留系统保护，继续使用应用内防摇一摇设置及手动跳过，不反复扩大权限。";
        if(sdk>=34)text+="\n\nAndroid14及以上的敏感界面可限制非辅助工具的读取和操作，Android16及以上还可限制相应模拟触点。本工具不会伪报为残障辅助工具来绕过；已授权也可能无法处理某些页面。";
        return text+"\n\n长期未使用应用的优化可能停止后台；仅针对开屏清净检查，不要求调整所有应用。打开官网由系统浏览器处理，需要网络；本页文字已离线内置，App本身无联网权限。";
    }
}
