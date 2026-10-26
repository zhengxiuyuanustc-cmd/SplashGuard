package org.splashguard.app;

import java.util.Locale;

/** Pure, conservative setup routing. Android API numbers never identify HarmonyOS. */
public final class DeviceGuidePolicy {
    private DeviceGuidePolicy() {}
    public enum Vendor { HUAWEI, HONOR, XIAOMI, OPPO, REALME, ONEPLUS, VIVO, IQOO, SAMSUNG, MEIZU, GOOGLE, OTHER }
    public enum Family { UNKNOWN, HARMONY_4, HARMONY_5_PLUS }
    public enum ServiceState { UNKNOWN, ON, OFF }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public static Vendor vendor(String manufacturer, String brand) {
        String names=lower(manufacturer)+" "+lower(brand);
        // Some older Honor devices identify their manufacturer as Huawei.
        if (names.contains("honor") || names.contains("荣耀")) return Vendor.HONOR;
        if (names.contains("huawei") || names.contains("华为")) return Vendor.HUAWEI;
        if (names.contains("realme") || names.contains("真我")) return Vendor.REALME;
        if (names.contains("oneplus") || names.contains("一加")) return Vendor.ONEPLUS;
        if (names.contains("iqoo")) return Vendor.IQOO;
        if (names.contains("xiaomi") || names.contains("redmi") || names.contains("poco") || names.contains("小米") || names.contains("红米")) return Vendor.XIAOMI;
        if (names.contains("oppo")) return Vendor.OPPO;
        if (names.contains("vivo")) return Vendor.VIVO;
        if (names.contains("samsung") || names.contains("三星")) return Vendor.SAMSUNG;
        if (names.contains("meizu") || names.contains("魅族")) return Vendor.MEIZU;
        if (names.contains("google") || names.contains("pixel")) return Vendor.GOOGLE;
        return Vendor.OTHER;
    }

    public static String vendorLabel(Vendor value) {
        if(value==null)return "其他／不确定";
        switch(value){
            case HUAWEI:return "华为";case HONOR:return "荣耀";case XIAOMI:return "小米／REDMI／POCO";
            case OPPO:return "OPPO";case REALME:return "realme 真我";case ONEPLUS:return "一加";
            case VIVO:return "vivo";case IQOO:return "iQOO";case SAMSUNG:return "三星";case MEIZU:return "魅族";case GOOGLE:return "Google／Pixel";
            default:return "其他／不确定";
        }
    }
    /** Null means automatic routing. A manual guide choice never asserts the real OS or changes it. */
    public static Vendor restoreGuideVendor(String token){
        if(token==null||"auto".equals(token))return null;
        try{return Vendor.valueOf(token);}catch(IllegalArgumentException unknown){return null;}
    }

    /** Restores our own UI token, not a guessed platform or Android version string. */
    public static Family restoreFamily(String token) {
        if ("HARMONY_4".equals(token)) return Family.HARMONY_4;
        if ("HARMONY_5_PLUS".equals(token)) return Family.HARMONY_5_PLUS;
        return Family.UNKNOWN;
    }

    public static String topic(String value) {
        return "accessibility".equals(value) || "background".equals(value) || "shake".equals(value) ? value : "no_skip";
    }

    public static String title(String value) {
        String t=topic(value);
        if ("shake".equals(t)) return "防摇一摇：本机专用设置";
        if ("accessibility".equals(t)) return "无障碍与授权帮助";
        if ("background".equals(t)) return "后台与系统兼容帮助";
        return "没有自动跳过？逐项检查";
    }

    public static String familyLabel(Family family) {
        if (family==Family.HARMONY_4) return "HarmonyOS 4.x（含4.2、4.3）";
        if (family==Family.HARMONY_5_PLUS) return "HarmonyOS 5/NEXT/6及以上";
        return "不确定／尚未核对";
    }

    public static String permissionExplanation() {
        return "不需要给本应用“全权限”。普通权限页没有相机、联系人等开关是正常的。App不申请联网、相机、联系人、悬浮窗或设备管理员。\n\n"
                +"自动跳过需要你本人开启无障碍服务。BIND_ACCESSIBILITY_SERVICE是系统绑定服务的保护声明，不是普通权限页里的“全部允许”。"
                +"无障碍授权、服务实际连接、后台运行和目标应用的广告专用开关，需要分别核对。打开过设置页面不表示已经设置成功。";
    }

    public static String serviceLabel(ServiceState state) {
        if (state==ServiceState.ON) return "系统查询：已列为启用";
        if (state==ServiceState.OFF) return "系统查询：未列为启用（若设置显示已开，请以实际界面和服务连接状态核对）";
        return "系统查询：暂时无法确认，请本人核对设置";
    }

    public static String guide(Vendor vendor, Family family, String topic) {
        String common="\n\n若出现“受限制”“风险”或灰色开关，请保留提示原文，只按系统为本应用实际提供的选项处理。"
                +"没有可用入口时，请核对下载来源并查看官方说明；不要关闭全机安全保护，也不要用关闭整体方向或传感器权限代替广告专用设置。";
        String text;
        if ("shake".equals(topic(topic))) {
            if(vendor==Vendor.HUAWEI)return "优先检查目标应用自己的广告专用开关。华为官方另列：HarmonyOS4.2／4.3可在应用和服务 → 权限管理 → 应用获取设备方向中按应用管理；HarmonyOS6.0.0.120及以上对应隐私和安全 → 应用获取设备方向；5.0至6.0.0.115不涉及此操作。\n\n该系统开关并未在官方说明中限定为只影响广告或只限开屏，可能涉及应用正常动作功能。按本工具保留正常方向／体感的原则，不作为默认关闭方案，不自动修改；不能把它当作OriginOS／Flyme的仅开屏选项。\n\n当前APK仍不是原生鸿蒙自动跳过版。"+common;
            return BrandGuides.shakeGuide(vendor)+common;
        }
        if (BrandGuides.handles(vendor)) {
            text=BrandGuides.guide(vendor,topic(topic));
        } else if (vendor==Vendor.HONOR) {
            text="本机按荣耀 MagicOS／Android 独立处理，不套用华为鸿蒙步骤。请在关于本机核对完整MagicOS与Android版本；Android版本较新本身不证明不兼容。\n\n"
                    +"无障碍：在设置搜索“无障碍”，检查本服务；部分版本入口为“辅助功能 → 无障碍 → 下载服务”，以实际页面为准。"
                    +"权限已开时，先看下方“服务实际连接”，无需重复要求全开权限。\n\n"
                    +"后台：在设置搜索“应用启动管理”，只针对开屏清净检查允许后台活动；必要时按本机选项配置自启动、关联启动与后台活动。"
                    +"也可在多任务界面下拉本应用卡片加锁。荣耀官方提醒资源不足时加锁后仍可能被清理，不能保证永久后台。"
                    +"电池优化如需调整，也仅针对本应用，不要求关闭全机省电或省流量。";
        } else if (vendor==Vendor.HUAWEI && family==Family.HARMONY_4) {
            text="你选择了HarmonyOS 4.x；这是手动选择，App没有自动确认系统版本。\n\n"
                    +"华为官方4.x指引：设置搜索“无障碍” → 已安装的服务／下载服务 → 本人开启开屏清净。"
                    +"清理后台或重启后，服务可能停止，需要重新检查。\n\n"
                    +"后台：设置搜索“应用启动管理”，将本应用改为手动管理，按需允许自启动、关联启动、后台活动；必要时给多任务卡片加锁。"
                    +"返回本页检查服务实际连接。官方资料涵盖4.3，不表示本工具已在该机型实测；既有实机仅覆盖GOA-AL80／HarmonyOS 4.2。";
        } else if (vendor==Vendor.HUAWEI && family==Family.HARMONY_5_PLUS) {
            text="你选择了HarmonyOS 5/NEXT/6及以上。当前是Android APK，并非原生鸿蒙自动跳过版；兼容环境与原生应用的能力不能混同。华为当前Accessibility Kit简介明确不支持通过无障碍节点查询界面及屏幕内容，旧读取／动作接口已标废弃；原生自动跳过暂不支持。\n\n"
                    +"华为官方说明：5及以上的部分原应用启动管理能力已由系统自动管理。找不到旧版三个开关不等于权限被拒，不必反复寻找旧入口。\n\n"
                    +"官方说明部分APK可以由卓易通／出境易提供服务，是否支持以实际环境为准。能安装或打开Android APK，不代表它能读取、点击原生鸿蒙应用。"
                    +"请确认本工具和目标应用是否处于同一运行环境；本页显示的是当前Android环境内的服务状态，不是跨环境能力证明。";
        } else if (vendor==Vendor.HUAWEI) {
            text="尚未核对本机系统，请先在上方选择你实际看到的系统家族。HUAWEI品牌可能对应HarmonyOS或EMUI，Android API数字不能替代完整鸿蒙版本。\n\n"
                    +"如果不确定，保持“不确定”，在设置搜索“无障碍”并核对实际选项；不套用4.2的旧后台路径。"
                    +"找不到入口或开关灰色时，记录完整系统版本与具体提示，再按对应官方说明处理。";
        } else {
            text="此品牌的完整系统与后台路径尚未在本工具验证。可在系统设置搜索“无障碍”，由本人检查本服务。"
                    +"后台选项名称可能是“自启动”“后台活动”或“电池优化”，仅按本机实际页面为本应用设置。\n\n"
                    +"系统授权已开仍不能替代服务实际连接；请返回本页重新检查，不因打开了设置就标为完成。";
        }
        if ("no_skip".equals(topic(topic))) {
            text += "\n\n没有自动跳过时，先确认本次确实出现了广告、目标应用已选中、服务已连接。没有广告只记为“未出现广告”，不能算跳过失败。"
                    +"多候选冲突、扫描不完整或规则不支持时，本工具可能保守拒点；不要连续乱点。";
        }
        return text+common;
    }

    public static String officialUrl(Vendor vendor, Family family, String topic) {
        if("shake".equals(topic(topic)))return vendor==Vendor.HUAWEI?"https://consumer.huawei.com/cn/support/content/zh-cn16002151/":BrandGuides.shakeUrl(vendor);
        if(BrandGuides.handles(vendor))return BrandGuides.officialUrl(vendor,topic(topic));
        if (vendor==Vendor.HONOR) return "accessibility".equals(topic(topic))
                ? "https://www.honor.com/cn/support/content/zh-cn15869276/"
                : "https://www.honor.com/cn/support/content/zh-cn00428704/";
        if (vendor!=Vendor.HUAWEI) return "";
        if (family==Family.HARMONY_4) return "https://consumer.huawei.com/cn/support/content/zh-cn00410039/";
        if (family==Family.HARMONY_5_PLUS) return "background".equals(topic(topic))
                ? "https://consumer.huawei.com/cn/support/content/zh-cn16056653/"
                : "https://consumer.huawei.com/cn/support/content/zh-cn16061787/";
        return "https://consumer.huawei.com/cn/support/";
    }
}
