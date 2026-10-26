import org.splashguard.app.DeviceGuidePolicy;
import org.splashguard.app.DeviceGuidePolicy.Family;
import org.splashguard.app.DeviceGuidePolicy.Vendor;
import org.splashguard.app.DeviceGuidePolicy.ServiceState;

/** Device names and manual choices must never be silently mapped to another OS family. */
public final class DeviceGuidePolicyTest {
    private static int passed;
    private static void check(String name,boolean result){if(!result)throw new AssertionError(name);passed++;}
    public static void main(String[] args){
        check("Honor manufacturer",DeviceGuidePolicy.vendor("HONOR","HONOR")==Vendor.HONOR);
        check("old Huawei-made Honor remains Honor",DeviceGuidePolicy.vendor("HUAWEI","HONOR")==Vendor.HONOR);
        check("Chinese Honor brand",DeviceGuidePolicy.vendor("华为","荣耀")==Vendor.HONOR);
        check("Huawei independent",DeviceGuidePolicy.vendor("HUAWEI","HUAWEI")==Vendor.HUAWEI);
        check("null device unknown",DeviceGuidePolicy.vendor(null,null)==Vendor.OTHER);
        check("other brand independent",DeviceGuidePolicy.vendor("Google","google")==Vendor.OTHER);
        check("restore explicit 4 family",DeviceGuidePolicy.restoreFamily("HARMONY_4")==Family.HARMONY_4);
        check("restore explicit 5 family",DeviceGuidePolicy.restoreFamily("HARMONY_5_PLUS")==Family.HARMONY_5_PLUS);
        check("Android17 is not Harmony",DeviceGuidePolicy.restoreFamily("Android 17")==Family.UNKNOWN);
        check("Android API35 is not Harmony",DeviceGuidePolicy.restoreFamily("35")==Family.UNKNOWN);
        check("raw 4.2 string is not an explicit choice",DeviceGuidePolicy.restoreFamily("4.2")==Family.UNKNOWN);
        check("missing family stays unknown",DeviceGuidePolicy.restoreFamily(null)==Family.UNKNOWN);
        String honor=DeviceGuidePolicy.guide(Vendor.HONOR,Family.HARMONY_5_PLUS,"background");
        check("Honor stale family cannot select Huawei5 route",honor.contains("荣耀")&&!honor.contains("系统自动管理"));
        check("Honor does not declare Android17 incompatible",!honor.contains("不支持Android 17")&&honor.contains("不证明不兼容"));
        String old=DeviceGuidePolicy.guide(Vendor.HUAWEI,Family.HARMONY_4,"background");
        String modern=DeviceGuidePolicy.guide(Vendor.HUAWEI,Family.HARMONY_5_PLUS,"background");
        String unknown=DeviceGuidePolicy.guide(Vendor.HUAWEI,Family.UNKNOWN,"accessibility");
        check("4x has scoped manual background settings",old.contains("应用启动管理")&&old.contains("手动管理")&&old.contains("4.3"));
        check("5plus explains removed old switches",modern.contains("系统自动管理")&&modern.contains("不必反复寻找旧入口"));
        check("5plus does not forbid all APKs",modern.contains("部分APK")&&modern.contains("不代表它能读取"));
        check("unknown does not assume 4.2",unknown.contains("不套用4.2")&&unknown.contains("不确定"));
        check("4 official link bounded",DeviceGuidePolicy.officialUrl(Vendor.HUAWEI,Family.HARMONY_4,"background").contains("00410039"));
        check("5 background official link",DeviceGuidePolicy.officialUrl(Vendor.HUAWEI,Family.HARMONY_5_PLUS,"background").contains("16056653"));
        check("5 APK support official link",DeviceGuidePolicy.officialUrl(Vendor.HUAWEI,Family.HARMONY_5_PLUS,"no_skip").contains("16061787"));
        check("unknown Huawei official landing",DeviceGuidePolicy.officialUrl(Vendor.HUAWEI,Family.UNKNOWN,"background").equals("https://consumer.huawei.com/cn/support/"));
        check("Honor never links Huawei guide",DeviceGuidePolicy.officialUrl(Vendor.HONOR,Family.HARMONY_4,"background").startsWith("https://www.honor.com/"));
        check("other vendor has no guessed official URL",DeviceGuidePolicy.officialUrl(Vendor.OTHER,Family.UNKNOWN,"no_skip").isEmpty());
        String permissions=DeviceGuidePolicy.permissionExplanation();
        check("full permissions not required",permissions.contains("不需要")&&permissions.contains("普通权限页")&&!permissions.contains("请开启全部权限"));
        check("BIND is service guard not runtime grant",permissions.contains("保护声明")&&permissions.contains("不是普通权限页"));
        check("no unsafe global sensor guidance",modern.contains("不要关闭全机安全保护")&&modern.contains("整体方向或传感器"));
        check("no ad is not failure",DeviceGuidePolicy.guide(Vendor.HONOR,Family.UNKNOWN,"no_skip").contains("不能算跳过失败"));
        check("unknown topic defaults safely",DeviceGuidePolicy.topic("login").equals("no_skip"));
        check("accessibility topic retained",DeviceGuidePolicy.topic("accessibility").equals("accessibility"));
        check("enabled is a query state",DeviceGuidePolicy.serviceLabel(ServiceState.ON).contains("系统查询"));
        check("query off preserves actual settings caveat",DeviceGuidePolicy.serviceLabel(ServiceState.OFF).contains("实际界面"));
        check("query failure stays unknown",DeviceGuidePolicy.serviceLabel(ServiceState.UNKNOWN).contains("无法确认"));
        System.out.println("DeviceGuidePolicy: "+passed+" checks passed");
    }
}
