package org.splashguard.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A user-reviewed metadata snapshot. It is not an action, OS detector or proof that an ad closed. */
public final class CompatibilityReport {
    public enum RuntimeEnvironment { UNKNOWN, DEFAULT_INSTALL_UNKNOWN, DIRECT_ANDROID_CONFIRMED, COMPATIBILITY_CONFIRMED, CLONE_OR_PROFILE_CONFIRMED }
    public enum AppState { INSTALLED, NOT_FOUND, UNKNOWN }
    public enum Category { GUIDE_ONLY, PAUSED, RULES_UNAVAILABLE, SERVICE_NOT_ENABLED, SERVICE_NOT_CONNECTED, NO_APPS_SELECTED, READY_TO_OBSERVE }
    private static final List<String> PACKAGES=Collections.unmodifiableList(Arrays.asList("tv.danmaku.bili","com.baidu.searchbox","com.baidu.searchbox.lite","com.netease.cloudmusic"));
    public static List<String> knownPackages(){return PACKAGES;}
    public static String appLabel(String pkg){int i=PACKAGES.indexOf(pkg);if(i<0)throw new IllegalArgumentException("Unknown report app");return new String[]{"哔哩哔哩","百度","百度极速版","网易云音乐"}[i];}
    public static RuntimeEnvironment restoreRuntime(String token){try{return RuntimeEnvironment.valueOf(token);}catch(RuntimeException unknown){return RuntimeEnvironment.UNKNOWN;}}
    public static String runtimeLabel(RuntimeEnvironment value){
        if(value==null)return "不清楚";
        switch(value){case DEFAULT_INSTALL_UNKNOWN:return "默认安装，具体环境不清楚";case DIRECT_ANDROID_CONFIRMED:return "本人已确认直接在Android环境安装";case COMPATIBILITY_CONFIRMED:return "本人已确认通过兼容工具运行";case CLONE_OR_PROFILE_CONFIRMED:return "本人已确认在分身或工作资料内运行";default:return "不清楚";}
    }
    /** Bound and sanitize metadata before rendering or encoding; never accept arbitrary report fields. */
    private static String metadata(String raw){
        if(raw==null||raw.isEmpty())return "未知";
        StringBuilder b=new StringBuilder();int end=Math.min(raw.length(),256);
        for(int i=0;i<end&&b.length()<100;){int cp=raw.codePointAt(i);i+=Character.charCount(cp);int type=Character.getType(cp);
            if(Character.isISOControl(cp)||type==Character.FORMAT||type==Character.SURROGATE||type==Character.LINE_SEPARATOR||type==Character.PARAGRAPH_SEPARATOR){b.append(' ');continue;}
            if(b.length()+Character.charCount(cp)>100)break;b.appendCodePoint(cp);}
        String value=b.toString().trim();return value.isEmpty()?"未知":value;
    }
    public static final class App {
        public final String packageName,versionName;public final long versionCode;public final AppState state;public final boolean selected;
        public App(String pkg,AppState state,String name,long code,boolean selected){
            if(!PACKAGES.contains(pkg)||state==null||code< -1)throw new IllegalArgumentException("Invalid report app");
            this.packageName=pkg;this.state=state;this.versionName=state==AppState.INSTALLED?metadata(name):"未知";this.versionCode=state==AppState.INSTALLED?code:-1;this.selected=selected;
        }
        private Map<String,Object> fields(){Map<String,Object> m=new LinkedHashMap<>();m.put("packageName",packageName);m.put("state",state.name());m.put("versionName",versionName);m.put("versionCode",versionCode);m.put("selected",selected);return m;}
    }
    public final String toolVersion,manufacturer,brand,model,androidRelease;
    public final int androidApi,selectedCount;public final boolean guideOnly,enabled,connected,rulesValid;
    public final DeviceGuidePolicy.ServiceState permissionQuery;public final RuntimeEnvironment runtime;
    public final Category category;public final List<App> apps;
    public CompatibilityReport(String toolVersion,String manufacturer,String brand,String model,String release,int api,
            boolean guideOnly,boolean enabled,DeviceGuidePolicy.ServiceState query,boolean connected,boolean rulesValid,int count,RuntimeEnvironment runtime,List<App> apps){
        if(api<1||count<0||apps==null||apps.size()>PACKAGES.size())throw new IllegalArgumentException("Invalid report snapshot");
        this.toolVersion=metadata(toolVersion);this.manufacturer=metadata(manufacturer);this.brand=metadata(brand);this.model=metadata(model);this.androidRelease=metadata(release);androidApi=api;
        this.guideOnly=guideOnly;this.enabled=DeviceGuidePolicy.effectiveEnabled(enabled,guideOnly);this.permissionQuery=query==null?DeviceGuidePolicy.ServiceState.UNKNOWN:query;
        this.connected=connected;this.rulesValid=rulesValid;selectedCount=count;this.runtime=runtime==null?RuntimeEnvironment.UNKNOWN:runtime;
        List<App> copy=new ArrayList<>();for(String pkg:PACKAGES)for(App app:apps){if(app==null)throw new IllegalArgumentException("Null report app");if(pkg.equals(app.packageName)){if(copy.stream().anyMatch(a->a.packageName.equals(pkg)))throw new IllegalArgumentException("Duplicate report app");copy.add(app);}}
        this.apps=Collections.unmodifiableList(copy);category=category(guideOnly,this.enabled,rulesValid,permissionQuery,connected,count);
    }
    public static Category category(boolean guideOnly,boolean enabled,boolean valid,DeviceGuidePolicy.ServiceState query,boolean connected,int count){
        if(count<0)throw new IllegalArgumentException("Negative app count");
        if(guideOnly)return Category.GUIDE_ONLY;if(!enabled)return Category.PAUSED;if(!valid)return Category.RULES_UNAVAILABLE;
        if(!connected)return query==DeviceGuidePolicy.ServiceState.OFF?Category.SERVICE_NOT_ENABLED:Category.SERVICE_NOT_CONNECTED;
        if(count==0)return Category.NO_APPS_SELECTED;return Category.READY_TO_OBSERVE;
    }
    public static String title(Category category){switch(category){case GUIDE_ONLY:return "当前设为仅查看指引";case PAUSED:return "自动跳过已暂停";case RULES_UNAVAILABLE:return "请检查本地规则";case SERVICE_NOT_ENABLED:return "还需核对无障碍设置";case SERVICE_NOT_CONNECTED:return "服务尚未连接";case NO_APPS_SELECTED:return "还需选择应用";default:return "服务已连接，待观察广告效果";}}
    public static String guidance(Category category,DeviceGuidePolicy.ServiceState query){switch(category){
        case GUIDE_ONLY:return "这是本人选择的本地模式，自动跳过已暂停；没有修改系统授权或确认具体操作系统。可查看广告专用设置，退出模式后仍保持暂停。";
        case PAUSED:return "可在首页由本人启用；还需核对应用选择、当前服务连接和系统实际支持情况。";
        case RULES_UNAVAILABLE:return "本地规则状态无法验证，当前停止处理。请在离线工具中核对或恢复内置规则。";
        case SERVICE_NOT_ENABLED:return "本次系统查询未列为启用，服务也未连接。按本机实际设置核对；若系统明确提示不支持，可选择仅查看指引，不必反复尝试。";
        case SERVICE_NOT_CONNECTED:return "系统查询不能代替实际连接。请按本机帮助核对服务与后台状态，无需全开权限；尚不能据此判断是广告规则问题。";
        case NO_APPS_SELECTED:return "当前服务已连接；选择需要帮助的应用后才会尝试处理，不会自动加入其他应用。";
        default:return "将在所选应用的短暂开屏时段尝试处理。未出现广告不能验证效果，操作计数不证明广告关闭。"+(query==DeviceGuidePolicy.ServiceState.OFF?" 系统启用列表与连接状态存在差异，请保留本报告，无需仅因列表差异反复授权。":"");}}
    public String toJson(){
        Map<String,Object> m=new LinkedHashMap<>();m.put("schemaVersion",1);m.put("toolVersion",toolVersion);
        Map<String,Object> device=new LinkedHashMap<>();device.put("manufacturer",manufacturer);device.put("brand",brand);device.put("model",model);device.put("androidRelease",androidRelease);device.put("androidApi",androidApi);m.put("device",device);
        m.put("runtimeReportedByUser",runtime.name());m.put("guideOnly",guideOnly);m.put("enabled",enabled);m.put("permissionQuery",permissionQuery.name());m.put("serviceConnected",connected);m.put("rulesValid",rulesValid);m.put("selectedAppCount",selectedCount);m.put("category",category.name());
        List<Object> rows=new ArrayList<>();for(App app:apps)rows.add(app.fields());m.put("knownApps",rows);
        String json=RuleJsonCodec.writeObject(m);OfflineRuleStore.requireInputSize(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);return json;
    }
    public String toText(){
        StringBuilder b=new StringBuilder("开屏清净兼容报告（本人预览后决定是否分享）\n本工具：").append(toolVersion).append("\n品牌／型号：").append(manufacturer).append(" / ").append(brand).append(" / ").append(model);
        b.append("\nAndroid运行环境：").append(androidRelease).append(" / API ").append(androidApi).append("\n完整厂商系统版本：尚未自动确认，分享时可本人补充");
        b.append("\n运行方式（本人选择）：").append(runtimeLabel(runtime)).append("\n模式：").append(guideOnly?"仅查看指引":"自动跳过设置").append("\n自动跳过：").append(enabled?"已开":"暂停");
        b.append("\n").append(DeviceGuidePolicy.serviceLabel(permissionQuery)).append("\n实际服务连接：").append(connected?"已连接（当前Android环境）":"未连接").append("\n本地规则：").append(rulesValid?"可读取":"无法验证").append("\n所选应用数量：").append(selectedCount);
        b.append("\n\n当前判断：").append(title(category)).append("\n").append(guidance(category,permissionQuery));
        for(App a:apps)b.append("\n\n").append(appLabel(a.packageName)).append("（").append(a.packageName).append("）\n查询：").append(a.state==AppState.INSTALLED?"本环境已查到":a.state==AppState.NOT_FOUND?"本环境未查到，不等于全机未安装":"暂时未知").append("\n版本：").append(a.versionName).append(" / code ").append(a.versionCode).append("\n本工具选择：").append(a.selected?"已选":"未选");
        return b.toString();
    }
}
