package org.splashguard.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit, local setup help; settings launches are never counted as authorization. */
public final class SetupHelpActivity extends Activity {
    private static final String EXTRA_TOPIC="topic";
    private static final int INK=Color.rgb(28,46,41), GREEN=Color.rgb(18,104,89), MUTED=Color.rgb(90,107,100);
    private static final String[] PACKAGES={"tv.danmaku.bili","com.baidu.searchbox","com.baidu.searchbox.lite","com.netease.cloudmusic"};
    private static final String[] LABELS={"哔哩哔哩","百度（不是百度网盘）","百度极速版","网易云音乐"};
    private static final int FAMILY_UNKNOWN=11001,FAMILY_4=11002,FAMILY_5=11003;
    private String topic="no_skip";
    private DeviceGuidePolicy.Vendor vendor,detectedVendor;
    private boolean manualVendor;
    private LinearLayout familyContainer;
    private DeviceGuidePolicy.Family family=DeviceGuidePolicy.Family.UNKNOWN;
    private DeviceGuidePolicy.ServiceState serviceState=DeviceGuidePolicy.ServiceState.UNKNOWN;
    private ScrollView scroll;
    private TextView heading,status,guide,appChecks,notice,modeSummary;
    private Button official,modeAction,modeCancel,accessibilityAction;
    private int modeConfirm;
    private int savedScroll;
    private String ownVersion="未知";
    private List<AppCheck> commonApps=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable delayedRefresh=()->refresh();

    public static void open(Context context,String topic) {
        Intent intent=new Intent(context,SetupHelpActivity.class).putExtra(EXTRA_TOPIC,DeviceGuidePolicy.topic(topic));
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); }
        catch(RuntimeException unavailable) { Toast.makeText(context,"帮助入口暂不可用，请在系统设置中搜索无障碍并核对实际状态。",Toast.LENGTH_LONG).show(); }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        topic=DeviceGuidePolicy.topic(saved==null?getIntent().getStringExtra(EXTRA_TOPIC):saved.getString(EXTRA_TOPIC));
        detectedVendor=DeviceGuidePolicy.vendor(Build.MANUFACTURER,Build.BRAND);vendor=detectedVendor;
        if(saved!=null){DeviceGuidePolicy.Vendor chosen=DeviceGuidePolicy.restoreGuideVendor(saved.getString("guideVendor"));if(chosen!=null){vendor=chosen;manualVendor=true;}}
        if(saved!=null){family=DeviceGuidePolicy.restoreFamily(saved.getString("family"));savedScroll=saved.getInt("scroll",0);}
        render();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);setIntent(intent);topic=DeviceGuidePolicy.topic(intent.getStringExtra(EXTRA_TOPIC));
        resetModeConfirm();heading.setText(DeviceGuidePolicy.title(topic));refreshGuide();refresh();
    }
    @Override protected void onResume(){super.onResume();refresh();handler.removeCallbacks(delayedRefresh);handler.postDelayed(delayedRefresh,600);}
    @Override protected void onPause(){handler.removeCallbacks(delayedRefresh);resetModeConfirm();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(delayedRefresh);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString(EXTRA_TOPIC,topic);out.putString("family",family.name());out.putString("guideVendor",manualVendor?vendor.name():"auto");out.putInt("scroll",scroll.getScrollY());super.onSaveInstanceState(out);}

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private void gap(LinearLayout box,int size){box.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout card(LinearLayout parent,String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(shape(Color.WHITE,16));parent.addView(c,new LinearLayout.LayoutParams(-1,-2));TextView h=text(title,20,INK);h.setTypeface(null,Typeface.BOLD);c.addView(h);gap(c,10);gap(parent,14);return c;}
    private Button button(LinearLayout parent,String title,View.OnClickListener action){Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(GREEN);b.setBackground(shape(Color.rgb(230,241,235),12));b.setMinHeight(dp(52));b.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);parent.addView(b,lp);b.setOnClickListener(v->{if(v!=modeAction)resetModeConfirm();action.onClick(v);});return b;}

    private void render() {
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,247,245));
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(22),dp(20),dp(24));scroll.addView(content);
        heading=text(DeviceGuidePolicy.title(topic),26,INK);heading.setTypeface(null,Typeface.BOLD);content.addView(heading);gap(content,14);
        LinearLayout mode=card(content,"系统已明确提示不支持？");
        modeSummary=text("",16,INK);mode.addView(modeSummary);
        gap(mode,8);mode.addView(text("若本机实际显示“为保障您的隐私安全，应用暂不支持获取系统无障碍权限”这类提示，不必反复尝试授权。可由你确认后只查看设置指引。本工具不会读取系统提示，也不会仅凭型号判断；切换模式不修改系统授权。",16,MUTED));
        modeAction=button(mode,"",v->changeGuideMode());modeCancel=button(mode,"取消本次模式更改",v->resetModeConfirm());
        LinearLayout device=card(content,"本机与运行环境");
        device.addView(text(safe(Build.MANUFACTURER)+" / "+safe(Build.BRAND)+" / "+safe(Build.MODEL)+"\nAndroid运行环境："+safe(Build.VERSION.RELEASE)+"（API "+Build.VERSION.SDK_INT+"）",16,MUTED));
        gap(device,8);device.addView(text("完整系统名称与版本请在“关于本机”核对。Android数字不能替代厂商系统版本，也不证明跨兼容环境能力。",16,MUTED));
        gap(device,12);device.addView(text("识别的品牌："+DeviceGuidePolicy.vendorLabel(detectedVendor)+"。下方可手动查看其他品牌指引；这不会改变手机设置，也不确认系统版本。",16,MUTED));
        final DeviceGuidePolicy.Vendor[] brands=DeviceGuidePolicy.Vendor.values();
        String[] labels=new String[brands.length+1];labels[0]="自动识别（"+DeviceGuidePolicy.vendorLabel(detectedVendor)+"）";
        for(int i=0;i<brands.length;i++)labels[i+1]="查看："+DeviceGuidePolicy.vendorLabel(brands[i]);
        Spinner chooseBrand=new Spinner(this);chooseBrand.setContentDescription("选择要查看的品牌指引");
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);chooseBrand.setAdapter(adapter);chooseBrand.setMinimumHeight(dp(52));
        chooseBrand.setSelection(manualVendor?vendor.ordinal()+1:0);device.addView(chooseBrand,new LinearLayout.LayoutParams(-1,-2));
        familyContainer=new LinearLayout(this);familyContainer.setOrientation(LinearLayout.VERTICAL);device.addView(familyContainer);
        gap(familyContainer,10);familyContainer.addView(text("华为系统家族，请按关于本机实际显示手动选择：",16,INK));
        RadioGroup choices=new RadioGroup(this);choices.setOrientation(RadioGroup.VERTICAL);
        addFamily(choices,FAMILY_UNKNOWN,DeviceGuidePolicy.Family.UNKNOWN);addFamily(choices,FAMILY_4,DeviceGuidePolicy.Family.HARMONY_4);addFamily(choices,FAMILY_5,DeviceGuidePolicy.Family.HARMONY_5_PLUS);
        choices.check(family==DeviceGuidePolicy.Family.HARMONY_4?FAMILY_4:family==DeviceGuidePolicy.Family.HARMONY_5_PLUS?FAMILY_5:FAMILY_UNKNOWN);
        choices.setOnCheckedChangeListener((group,id)->{resetModeConfirm();family=id==FAMILY_4?DeviceGuidePolicy.Family.HARMONY_4:id==FAMILY_5?DeviceGuidePolicy.Family.HARMONY_5_PLUS:DeviceGuidePolicy.Family.UNKNOWN;refreshGuide();});familyContainer.addView(choices);
        chooseBrand.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> parent){}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){resetModeConfirm();manualVendor=position>0;vendor=manualVendor?brands[position-1]:detectedVendor;refreshGuide();}
        });
        LinearLayout ready=card(content,"实际状态自检");status=text("",16,INK);ready.addView(status);button(ready,"重新检查",v->refresh());
        gap(ready,8);ready.addView(text("授权开关和服务实际连接是不同状态；这里只显示本次检查结果。离开本页后系统仍可能停止服务。",15,MUTED));
        LinearLayout permissions=card(content,"无需“全权限”");permissions.addView(text(DeviceGuidePolicy.permissionExplanation(),16,MUTED));
        accessibilityAction=button(permissions,"打开系统无障碍设置",v->openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),"设置中搜索“无障碍”，由本人核对开屏清净服务。"));
        button(permissions,"打开本应用设置",v->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)),"设置中搜索“应用管理”，找到开屏清净；不要把普通权限页为空当作缺少全权限。"));
        LinearLayout steps=card(content,"按本机情况处理");guide=text("",16,MUTED);steps.addView(guide);official=button(steps,"用浏览器查看对应官方说明",v->openOfficial());
        LinearLayout protections=card(content,"受限授权与系统保护（按需查看）");
        TextView protectionText=text(BrandGuides.platformHelp(Build.VERSION.SDK_INT),16,MUTED);protectionText.setVisibility(View.GONE);
        button(protections,"展开／收起系统限制说明",v->protectionText.setVisibility(protectionText.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));protections.addView(protectionText);
        button(protections,"查看Android官方受限设置说明",v->openUrl(BrandGuides.RESTRICTED_URL));
        button(protections,"查看官方高级保护说明",v->openUrl(BrandGuides.PROTECTION_URL));
        LinearLayout apps=card(content,"常见应用检查");appChecks=text("",16,INK);apps.addView(appChecks);
        gap(apps,10);apps.addView(text("百度与百度网盘是不同应用。分身、工作资料或兼容环境中的应用可能不在当前列表；请确认本工具与目标应用处在同一环境。未出现广告不算跳过失败。",16,MUTED));
        button(apps,"将上述已安装应用加入处理",v->addCommonApps());
        LinearLayout share=card(content,"复制最小自检摘要");share.addView(text("只复制品牌型号、Android运行环境、本工具版本、功能/服务状态和上述应用的版本与选择。不会包含账号界面、设备唯一标识或完整应用列表，也不会自动上传或发送。分享前请本人检查。",16,MUTED));
        button(share,"复制自检摘要",v->copySummary());
        notice=text("",15,MUTED);content.addView(notice);button(content,"返回",v->finish());
        setContentView(scroll);scroll.requestApplyInsets();refreshGuide();scroll.post(()->scroll.scrollTo(0,savedScroll));
    }

    private void resetModeConfirm(){modeConfirm=0;refreshMode();}
    private void refreshMode(){
        if(modeSummary==null||modeAction==null||modeCancel==null)return;
        boolean guideOnly=Prefs.guideOnly(this);
        modeSummary.setText(guideOnly?"当前：仅查看设置指引。自动跳过保持暂停；系统授权和真实服务连接仍在下方单独显示。你可随时重新检查并退出此模式，退出后也不会自动启用。":"当前未选择仅指引模式。只有本人确认下方系统提示，才会切换并暂停自动跳过；手选品牌或鸿蒙家族不会切换此模式。");
        modeAction.setText(modeConfirm==1?"确认：仅查看指引并暂停":modeConfirm==2?"确认退出（自动跳过仍暂停）":guideOnly?"退出仅指引模式，保持暂停":"我已看到不支持提示：仅查看指引");
        modeCancel.setVisibility(modeConfirm==0?View.GONE:View.VISIBLE);
        if(accessibilityAction!=null)accessibilityAction.setText(guideOnly?"手动检查系统无障碍（可选）":"打开系统无障碍设置");
    }
    private void changeGuideMode(){
        boolean currentlyGuide=Prefs.guideOnly(this);int expected=currentlyGuide?2:1;
        if(modeConfirm!=expected){modeConfirm=expected;refreshMode();notice.setText(currentlyGuide?"确认退出后仍会暂停自动跳过，请到首页核对后再本人启用。":"确认后仅查看设置指引，并暂停自动跳过；不会修改系统授权或判断具体限制原因。");return;}
        boolean saved=Prefs.setGuideOnly(this,!currentlyGuide);modeConfirm=0;refresh();
        notice.setText(!saved?"保存未能确认，请重新检查当前模式；不会自动开启功能。":currentlyGuide?"已退出仅指引模式。自动跳过仍暂停，请核对实际支持情况后再本人启用。":"已设为仅查看设置指引，自动跳过已暂停，无需反复尝试授权。系统授权未被修改。");
    }
    private void addFamily(RadioGroup group,int id,DeviceGuidePolicy.Family value){RadioButton b=new RadioButton(this);b.setId(id);b.setText(DeviceGuidePolicy.familyLabel(value));b.setTextSize(16);b.setTextColor(INK);b.setMinHeight(dp(48));group.addView(b);}
    private void refreshGuide(){if(familyContainer!=null)familyContainer.setVisibility(vendor==DeviceGuidePolicy.Vendor.HUAWEI?View.VISIBLE:View.GONE);if(guide==null)return;guide.setText("指引品牌："+DeviceGuidePolicy.vendorLabel(vendor)+(manualVendor?"（手动查看）":"（自动识别）")+"\n路径参考官方资料，本机广告效果仍需实测。\n\n"+DeviceGuidePolicy.guide(vendor,family,topic));official.setVisibility(DeviceGuidePolicy.officialUrl(vendor,family,topic).isEmpty()?View.GONE:View.VISIBLE);official.setText(vendor==DeviceGuidePolicy.Vendor.GOOGLE&&"accessibility".equals(topic)?"受限设置说明（仅提示受限时参考）":(vendor==DeviceGuidePolicy.Vendor.MEIZU||vendor==DeviceGuidePolicy.Vendor.ONEPLUS)&&!"shake".equals(topic)?"查看官网支持入口（菜单需核对）":"用浏览器查看官方路径参考");}
    private void refresh() {
        if(status==null)return;
        refreshMode();serviceState=queryEnabledService();
        Set<String> selected=Prefs.selected(this);
        ownVersion=packageVersion(getPackageName());
        status.setText("本工具版本："+ownVersion+"\n使用模式："+(Prefs.guideOnly(this)?"仅查看设置指引（本人选择）":"自动跳过设置")+"\n自动跳过开关："+(Prefs.enabled(this)?"已开启":"未开启")
                +"\n已选择应用："+selected.size()+"个\n"+DeviceGuidePolicy.serviceLabel(serviceState)
                +"\n服务实际连接："+(SkipService.isConnected()?"已连接（当前Android环境）":"未连接"));
        commonApps=new ArrayList<>();StringBuilder rows=new StringBuilder();
        for(int i=0;i<PACKAGES.length;i++){
            AppCheck app=checkApp(PACKAGES[i],LABELS[i],selected.contains(PACKAGES[i]));commonApps.add(app);
            if(rows.length()>0)rows.append("\n\n");rows.append(app.describe());
        }
        appChecks.setText(rows.toString());
    }
    private DeviceGuidePolicy.ServiceState queryEnabledService(){
        try{
            AccessibilityManager manager=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
            if(manager==null)return DeviceGuidePolicy.ServiceState.UNKNOWN;
            List<AccessibilityServiceInfo> enabled=manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if(enabled==null)return DeviceGuidePolicy.ServiceState.UNKNOWN;
            ComponentName wanted=new ComponentName(this,SkipService.class);
            for(AccessibilityServiceInfo item:enabled){ResolveInfo resolve=item.getResolveInfo();ServiceInfo info=resolve==null?null:resolve.serviceInfo;
                if(info!=null&&wanted.equals(new ComponentName(info.packageName,info.name)))return DeviceGuidePolicy.ServiceState.ON;}
            return DeviceGuidePolicy.ServiceState.OFF;
        }catch(RuntimeException unavailable){return DeviceGuidePolicy.ServiceState.UNKNOWN;}
    }
    private String packageVersion(String pkg){try{PackageInfo p=getPackageManager().getPackageInfo(pkg,0);return safe(p.versionName)+" / code "+(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode);}catch(Exception unavailable){return "暂时无法读取";}}
    private AppCheck checkApp(String pkg,String label,boolean selected){
        try{PackageInfo p=getPackageManager().getPackageInfo(pkg,0);return new AppCheck(pkg,label,true,safe(p.versionName)+" / code "+(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode),selected);}
        catch(android.content.pm.PackageManager.NameNotFoundException absent){return new AppCheck(pkg,label,false,"当前环境未查到，可能未安装或位于其他分身/环境",selected);}
        catch(RuntimeException unavailable){return new AppCheck(pkg,label,false,"读取受限或暂时未知",selected);}
    }
    private void addCommonApps(){
        refresh();Set<String> selected=Prefs.selected(this);int added=0;
        for(AppCheck app:commonApps)if(app.installed&&selected.add(app.pkg))added++;
        if(added>0)Prefs.get(this).edit().putStringSet("apps",new HashSet<>(selected)).apply();
        refresh();notice.setText(added>0?"已加入"+added+"个应用；没有自动授权或开启自动跳过，请再次核对状态。":"没有新增可加入的应用；已有选择保持原样。");
    }
    private void copySummary(){
        refresh();StringBuilder value=new StringBuilder("开屏清净最小自检摘要（本人决定是否分享）\n");
        value.append("品牌/型号：").append(safe(Build.MANUFACTURER)).append(" / ").append(safe(Build.BRAND)).append(" / ").append(safe(Build.MODEL));
        value.append("\nAndroid运行环境：").append(safe(Build.VERSION.RELEASE)).append(" / API ").append(Build.VERSION.SDK_INT);
        value.append("\n系统完整版本：请本人从关于本机补充");
        value.append("\n当前查看指引：").append(DeviceGuidePolicy.vendorLabel(vendor)).append(manualVendor?"（手动，仅为指引选择）":"（自动）");
        if(vendor==DeviceGuidePolicy.Vendor.HUAWEI)value.append("\n手动家族选择：").append(DeviceGuidePolicy.familyLabel(family));
        value.append("\n本工具使用模式：").append(Prefs.guideOnly(this)?"仅查看设置指引（本人选择）":"自动跳过设置");
        value.append("\n本工具版本：").append(ownVersion).append("\n自动跳过：").append(Prefs.enabled(this)?"已开":"未开");
        value.append("\n").append(DeviceGuidePolicy.serviceLabel(serviceState)).append("\n服务实际连接：").append(SkipService.isConnected()?"已连接":"未连接");
        for(AppCheck app:commonApps)value.append("\n\n").append(app.describe());
        try{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard==null)throw new IllegalStateException();clipboard.setPrimaryClip(ClipData.newPlainText("开屏清净自检摘要",value.toString()));notice.setText("已复制到系统剪贴板，没有自动上传或发送。请本人检查后再决定是否分享。");}
        catch(RuntimeException unavailable){notice.setText("无法写入剪贴板；可根据上方内容手动记录，不影响设置。");}
    }
    private void openSettings(Intent intent,String fallback){try{startActivity(intent);}catch(RuntimeException unavailable){String message="无法打开该系统入口。请手动在"+fallback;notice.setText(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();}}
    private void openOfficial(){String url=DeviceGuidePolicy.officialUrl(vendor,family,topic);if(!url.isEmpty())openUrl(url);}
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(RuntimeException unavailable){notice.setText("没有可用浏览器，请手动打开："+url);notice.setTextIsSelectable(true);}}
    private static String safe(String value){if(value==null||value.isEmpty())return "未知";String line=value.replaceAll("[\\r\\n\\t\\p{Cntrl}]"," ");return line.length()>100?line.substring(0,100):line;}
    private static final class AppCheck {
        final String pkg,label,version;final boolean installed,selected;
        AppCheck(String pkg,String label,boolean installed,String version,boolean selected){this.pkg=pkg;this.label=label;this.installed=installed;this.version=version;this.selected=selected;}
        String describe(){return label+"\n"+pkg+"\n"+version+"\n本工具选择："+(selected?"已选择":"未选择");}
    }
}
