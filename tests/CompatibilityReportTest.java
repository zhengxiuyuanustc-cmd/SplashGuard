import org.splashguard.app.CompatibilityReport;
import org.splashguard.app.CompatibilityReport.App;
import org.splashguard.app.CompatibilityReport.AppState;
import org.splashguard.app.CompatibilityReport.Category;
import org.splashguard.app.CompatibilityReport.RuntimeEnvironment;
import org.splashguard.app.DeviceGuidePolicy.ServiceState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Cross-device evidence is observational: never infer native support or successful ad closure. */
public final class CompatibilityReportTest {
    private static int passed;
    private static void check(String n,boolean result){if(!result)throw new AssertionError(n);passed++;}
    private static void rejects(String n,Runnable action){try{action.run();}catch(IllegalArgumentException expected){passed++;return;}throw new AssertionError(n);}
    private static Category category(boolean guide,boolean enabled,boolean valid,ServiceState query,boolean connected,int count){return CompatibilityReport.category(guide,enabled,valid,query,connected,count);}
    private static CompatibilityReport report(String name,RuntimeEnvironment runtime,List<App> apps){return new CompatibilityReport("0.1.12","HUAWEI","HUAWEI",name,"12",31,false,true,ServiceState.OFF,true,true,2,runtime,apps);}
    public static void main(String[] args){
        check("listed enabled without connection needs connection",category(false,true,true,ServiceState.ON,false,1)==Category.SERVICE_NOT_CONNECTED);
        check("actual connection outranks off list",category(false,true,true,ServiceState.OFF,true,1)==Category.READY_TO_OBSERVE);
        check("connected empty selection needs apps rather than repeated permission",category(false,true,true,ServiceState.OFF,true,0)==Category.NO_APPS_SELECTED);
        check("off and disconnected needs settings",category(false,true,true,ServiceState.OFF,false,1)==Category.SERVICE_NOT_ENABLED);
        check("unknown permission remains uncertain disconnected",category(false,true,true,ServiceState.UNKNOWN,false,1)==Category.SERVICE_NOT_CONNECTED);
        check("null permission is not off",category(false,true,true,null,false,1)==Category.SERVICE_NOT_CONNECTED);
        check("guide mode wins over stale true",category(true,true,true,ServiceState.ON,true,1)==Category.GUIDE_ONLY);
        check("guide mode does not rely on service failure",category(true,false,true,ServiceState.ON,true,1)==Category.GUIDE_ONLY);
        check("pause retains user choice even connected",category(false,false,true,ServiceState.ON,true,1)==Category.PAUSED);
        check("bad rules stop handling before permission",category(false,true,false,ServiceState.ON,true,1)==Category.RULES_UNAVAILABLE);
        check("pause does not become ready with bad rules",category(false,false,false,ServiceState.ON,true,1)==Category.PAUSED);
        rejects("negative count rejected",()->category(false,true,true,ServiceState.ON,true,-1));
        check("ready title is observational",CompatibilityReport.title(Category.READY_TO_OBSERVE).contains("待观察"));
        check("ready body rejects count as proof",CompatibilityReport.guidance(Category.READY_TO_OBSERVE,ServiceState.ON).contains("不证明广告关闭"));
        check("query discrepancy does not demand repeated authorizing",CompatibilityReport.guidance(Category.READY_TO_OBSERVE,ServiceState.OFF).contains("无需"));
        check("unknown query with real connection stays observational",category(false,true,true,ServiceState.UNKNOWN,true,1)==Category.READY_TO_OBSERVE);
        check("arbitrary runtime tokens never assert OS",CompatibilityReport.restoreRuntime("HarmonyOS 7")==RuntimeEnvironment.UNKNOWN);
        check("raw API number never identifies runtime",CompatibilityReport.restoreRuntime("37")==RuntimeEnvironment.UNKNOWN);
        check("null runtime restored safely",CompatibilityReport.restoreRuntime(null)==RuntimeEnvironment.UNKNOWN);
        check("explicit enum restore",CompatibilityReport.restoreRuntime("DEFAULT_INSTALL_UNKNOWN")==RuntimeEnvironment.DEFAULT_INSTALL_UNKNOWN);
        check("default install retains unknown",CompatibilityReport.runtimeLabel(RuntimeEnvironment.DEFAULT_INSTALL_UNKNOWN).contains("不清楚"));
        List<App> rows=new ArrayList<>();rows.add(new App("com.netease.cloudmusic",AppState.NOT_FOUND,"discarded-name",999,true));rows.add(new App("tv.danmaku.bili",AppState.INSTALLED,"9.15.0",915000,true));
        CompatibilityReport r=report("MODEL",RuntimeEnvironment.DEFAULT_INSTALL_UNKNOWN,rows);String before=r.toJson();rows.clear();
        check("report copies caller collection",r.apps.size()==2&&r.toJson().equals(before));
        check("report order deterministic",r.apps.get(0).packageName.equals("tv.danmaku.bili"));
        try{r.apps.clear();throw new AssertionError("mutable apps");}catch(UnsupportedOperationException expected){passed++;}
        try{CompatibilityReport.knownPackages().clear();throw new AssertionError("mutable whitelist");}catch(UnsupportedOperationException expected){passed++;}
        check("not found metadata not promoted to installed",!before.contains("discarded-name")&&r.apps.get(1).versionCode==-1);
        check("stale selection kept separate from installed status",r.apps.get(1).selected&&r.apps.get(1).state==AppState.NOT_FOUND);
        check("not found not equated to globally absent",r.toText().contains("不等于全机未安装"));
        check("Android release not full Harmony version",r.toText().contains("完整厂商系统版本：尚未自动确认"));
        check("runtime self report labeled",r.toJson().contains("runtimeReportedByUser"));
        check("JSON has schema and bounded whitelist",before.contains("\"schemaVersion\":1")&&before.contains("\"knownApps\""));
        check("no hidden selected package list",!before.contains("selectedApps")&&!before.contains("rawTree")&&!before.contains("serial"));
        check("metadata with quotes escaped by common codec",report("a\"b\\c",null,Collections.emptyList()).toJson().contains("a\\\"b\\\\c"));
        CompatibilityReport clean=report("a\n\r\u0000\u202e\u2028\u2029b",null,Collections.emptyList());
        check("ASCII control removed from model",!clean.model.contains("\n")&&!clean.model.contains("\r")&&!clean.model.contains("\u0000"));
        check("bidi control removed from model",!clean.model.contains("\u202e"));
        check("unicode line separator removed",!clean.model.contains("\u2028"));
        check("unicode paragraph separator removed",!clean.model.contains("\u2029"));
        StringBuilder huge=new StringBuilder();for(int i=0;i<10000;i++)huge.append("甲");
        CompatibilityReport bounded=report(huge.toString(),null,Collections.emptyList());
        check("metadata bounded before normalization",bounded.model.length()<=100);
        check("report stays far inside file limit",bounded.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<65536);
        CompatibilityReport absent=report(null,null,Collections.emptyList());check("unknown metadata explicit",absent.model.equals("未知"));check("unknown runtime explicit",absent.runtime==RuntimeEnvironment.UNKNOWN);
        CompatibilityReport guide=new CompatibilityReport(null,null,null,null,null,26,true,true,null,true,true,0,null,Collections.emptyList());
        check("guide mode effective flag false",!guide.enabled&&guide.category==Category.GUIDE_ONLY);
        check("guide mode preserves actual connection",guide.connected&&guide.toText().contains("已连接"));
        check("null query represented unknown",guide.permissionQuery==ServiceState.UNKNOWN);
        rejects("arbitrary app disallowed",()->new App("private.account.app",AppState.INSTALLED,"1",1,true));
        rejects("null app status rejected",()->new App("tv.danmaku.bili",null,"1",1,true));
        rejects("invalid version code rejected",()->new App("tv.danmaku.bili",AppState.INSTALLED,"1",-2,true));
        App one=new App("tv.danmaku.bili",AppState.INSTALLED,"1",1,true);
        rejects("duplicates rejected",()->report("m",null,Arrays.asList(one,one)));
        rejects("null candidate not silently omitted",()->report("m",null,Arrays.asList((App)null)));
        rejects("oversize app array rejected",()->report("m",null,Arrays.asList(one,one,one,one,one)));
        rejects("missing app array rejected",()->report("m",null,null));
        rejects("invalid API rejected",()->new CompatibilityReport("1","a","b","m","r",0,false,true,ServiceState.ON,true,true,0,null,Collections.emptyList()));
        check("known empty rows remain empty",report("m",null,Collections.emptyList()).apps.isEmpty());
        System.out.println("CompatibilityReportTest: "+passed+" checks passed");
    }
}
