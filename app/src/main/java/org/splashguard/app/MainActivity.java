package org.splashguard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(18,104,89), INK = Color.rgb(29,48,42), MUTED = Color.rgb(91,107,99);
    private LinearLayout content;
    private TextView stateTitle, stateBody;
    private Button stateAction;
    private String lastStatus = "";
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRefresh = new Runnable() {
        @Override public void run() { refreshStatus(); statusHandler.postDelayed(this,1000); }
    };
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onCreate(Bundle b) { super.onCreate(b); }
    @Override public void onResume() { super.onResume(); render(); statusHandler.removeCallbacks(statusRefresh); statusHandler.post(statusRefresh); }
    @Override public void onPause() { statusHandler.removeCallbacks(statusRefresh); super.onPause(); }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(3), 1); return t;
    }
    private void gap(LinearLayout parent, int h) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }
    private GradientDrawable shape(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(22)); return d; }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(22),dp(22),dp(22),dp(22)); c.setBackground(shape(Color.WHITE));
        content.addView(c, new LinearLayout.LayoutParams(-1,-2)); gap(content,14); return c;
    }
    private void heading(LinearLayout c, String title, String body) { TextView t = text(title,21,INK); t.setTypeface(null,Typeface.BOLD); c.addView(t); gap(c,10); c.addView(text(body,14,MUTED)); }
    private Button button(LinearLayout c, String label, View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setTextSize(15); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(54)); p.topMargin=dp(14); c.addView(b,p); b.setOnClickListener(action); return b;
    }
    private boolean huawei() { return Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("huawei"); }
    private boolean serviceEnabled() {
        String raw = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (raw == null) return false;
        ComponentName own = new ComponentName(this, SkipService.class);
        for (String value : raw.split(":")) if (own.equals(ComponentName.unflattenFromString(value))) return true;
        return false;
    }
    private void refreshStatus() {
        if (stateTitle == null) return;
        boolean enabled=Prefs.enabled(this), permission=serviceEnabled(), connected=SkipService.isConnected();
        int count=Prefs.selected(this).size();
        String signature=enabled+":"+permission+":"+connected+":"+count;
        if (signature.equals(lastStatus)) return;
        lastStatus=signature;
        stateTitle.setTextColor(Color.rgb(153,83,15));
        stateAction.setVisibility(View.VISIBLE);
        if (!enabled) {
            stateTitle.setText("自动跳过已暂停");
            stateBody.setText("当前不会自动点击广告。开启后仍需无障碍服务和应用选择。\nB站广告专用开关需在B站内单独核对。");
            stateAction.setText("启用自动跳过");
        } else if (!permission) {
            stateTitle.setText("自动跳过未运行");
            stateBody.setText("系统无障碍服务未开启。即使下方开关已打开，也无法自动跳过。请重新开启“开屏清净 · 自动跳过”。");
            stateAction.setText("恢复无障碍服务");
        } else if (!connected) {
            stateTitle.setText("无障碍服务尚未就绪");
            stateBody.setText("系统开关已开启，服务尚未连接。稍候若仍未恢复，请进入无障碍设置检查本服务。");
            stateAction.setText("检查无障碍服务");
        } else if (count==0) {
            stateTitle.setText("尚未选择需要处理的应用");
            stateBody.setText("服务已连接。选择有开屏广告的应用后，才会尝试识别跳过按钮。");
            stateAction.setText("选择应用");
        } else {
            stateTitle.setText("自动跳过已就绪"); stateTitle.setTextColor(GREEN);
            stateBody.setText("服务已连接，已选择 "+count+" 个应用。可通过下方按钮关闭首页；自动跳过仍需系统允许无障碍服务在后台运行。\nB站防摇一摇仍需下方的广告专用设置。");
            stateAction.setVisibility(View.GONE);
        }
    }
    private void repairStatus() {
        if (!Prefs.enabled(this)) { Prefs.get(this).edit().putBoolean("enabled",true).apply(); render(); }
        else if (!serviceEnabled() || !SkipService.isConnected()) disclosure();
        else chooseApps();
    }
    private void closeHome() {
        if (Prefs.enabled(this) && serviceEnabled() && SkipService.isConnected() && !Prefs.selected(this).isEmpty()) {
            finishAndRemoveTask();
            return;
        }
        new AlertDialog.Builder(this).setTitle("请先恢复自动跳过")
                .setMessage("自动跳过需要同时开启应用内开关和系统无障碍服务，等待服务连接，并选择需要处理的应用。当前尚未就绪，关闭首页后也无法自动跳过。\n\n请先按首页提示完成设置，再使用“关闭首页，保留自动跳过”。")
                .setNegativeButton("留在首页",null).setPositiveButton("继续设置",(d,w)->repairStatus()).show();
    }
    private void render() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(243,245,241));
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20),dp(24),dp(20),dp(26)); scroll.addView(content); setContentView(scroll);
        TextView brand = text("开屏清净",32,INK); brand.setTypeface(null,Typeface.BOLD); content.addView(brand); gap(content,8);
        content.addView(text("让打开应用这件事，安静一点。",15,MUTED)); gap(content,24);
        LinearLayout status=card();
        stateTitle=text("检查自动跳过状态…",21,INK);stateTitle.setTypeface(null,Typeface.BOLD);status.addView(stateTitle);gap(status,10);
        stateBody=text("",14,MUTED);status.addView(stateBody);
        stateAction=button(status,"检查无障碍服务",v -> repairStatus());
        button(status,"关闭首页，保留自动跳过",v -> closeHome());
        lastStatus="";
        refreshStatus();
        LinearLayout bili=card();
        heading(bili,"01  应用内的防摇一摇开关","哔哩哔哩 9.15.0 已核实有广告专用开关。关闭它可限制广告的传感器访问，无需关闭整个应用的方向权限。");
        gap(bili,10);bili.addView(text("哔哩哔哩 → 我的 → 设置\n→ 隐私权限设置 → 允许广告访问传感器：关闭",14,GREEN));
        button(bili,"查看B站设置指引",v -> biliGuide());
        LinearLayout system = card();
        heading(system,"02  检查系统跳转限制","优先查找手机自带的“应用跳转提醒”或应用内“摇一摇广告”开关。开屏清净不会关闭方向传感器，也不会自动修改系统权限。");
        gap(system,12); system.addView(text("已识别："+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" · "+Build.DISPLAY,12,GREEN));
        button(system,"查看本机手动设置指南",v -> manualGuide());
        button(system,"打开系统权限管理",v -> openSystem());
        gap(system,10); system.addView(text("方向权限会持续影响应用的旋转或体感功能，不能作为通用默认方案。若本机没有独立的广告跳转限制，请使用下方自动跳过。",12,MUTED));
        LinearLayout auto = card();
        heading(auto,"03  自动跳过开屏广告","仅在所选应用进入前台后的 12 秒内，尝试点击明确的广告跳过按钮。防摇一摇需要先配置上方的广告专用开关；打开自动跳过不代表已禁止晃动跳转。");
        gap(auto,16);
        Switch toggle = new Switch(this); toggle.setText("启用自动跳过"); toggle.setTextSize(17); toggle.setTextColor(INK); toggle.setChecked(Prefs.enabled(this)); auto.addView(toggle,new LinearLayout.LayoutParams(-1,dp(48)));
        toggle.setOnCheckedChangeListener((b,on)-> { Prefs.get(this).edit().putBoolean("enabled",on).apply(); render(); });
        gap(auto,8); auto.addView(text("已选择 " + Prefs.selected(this).size() + " 个应用  ·  无障碍" + (serviceEnabled()?"已开启":"未开启"),13,MUTED));
        button(auto,"选择需要处理的应用",v -> chooseApps());
        button(auto,"设置无障碍服务",v -> disclosure());
        gap(auto,10); auto.addView(text("若清理后台后自动跳过失效，请查看下方后台运行指引。无需开启悬浮窗。",12,MUTED));
        LinearLayout background=card();
        heading(background,"04  关闭首页后继续使用","不用一直保留首页卡片。请使用顶部“关闭首页，保留自动跳过”；自动跳过仍需系统允许无障碍服务在后台运行。强行停止应用或系统清理若关闭了服务，功能就会停止。\n\nB站的广告传感器开关保存在B站内，不由本工具后台维持，请在B站设置中单独核对。");
        button(background,"查看本机后台运行指引",v -> backgroundGuide());
        button(background,"打开本应用系统设置",v -> openAppSettings());
        gap(background,10);background.addView(text("打开设置或阅读指引不代表已经设置成功。返回首页后请确认“自动跳过已就绪”，并实际打开所选应用检查。",12,MUTED));
        LinearLayout privacy = card(); heading(privacy,"本地运行，随时暂停","无网络权限，不上传或保存页面内容。无障碍权限允许读取屏幕控件并模拟点击；启用后仅处理所选应用，普通“×”、支付和授权按钮不在识别规则中。");
        gap(privacy,12); privacy.addView(text("已发送跳过操作：" + Prefs.get(this).getInt("count",0) + " 次\n计数表示操作已提交，不等于广告已成功消失。",13,GREEN));
        gap(privacy,10);privacy.addView(text("最近检查："+Prefs.get(this).getString("diagnostic","暂无检查记录"),12,MUTED));
        gap(privacy,10); privacy.addView(text("暂停：关闭上方开关。彻底停用：在系统无障碍设置中关闭本服务。系统方向权限需在系统页单独恢复。",12,MUTED));
        gap(content,8); content.addView(text("SplashGuard 0.1.4 · MIT 开源预览版\n广告专用设置优先，谨慎点击兜底。",12,MUTED));
    }
    private void backgroundGuide() {
        String guide;
        if (huawei()) {
            guide="华为后台运行指引\n\n1. 在设置中搜索“应用启动管理”，或进入手机管家 → 应用启动管理。找到“开屏清净”。\n2. 关闭本应用的“自动管理”，在弹窗中同时打开“允许自启动”“允许关联启动”“允许后台活动”，并确认保存。\n3. 返回本工具，按首页提示重新开启“开屏清净 · 自动跳过”无障碍服务，等待显示已就绪。\n4. 使用“关闭首页，保留自动跳过”，再打开所选应用验证。\n\n华为清理后台或重启后可能关闭第三方无障碍服务。单独划掉卡片与一键清理都可能触发系统清理，不能保证等同于本工具主动关闭首页。若仍受影响，可按华为官方建议在最近任务中下拉卡片加锁，再进行验证。\n\n手动管理不能保证绕过“强行停止”。无障碍服务被关闭后，需要本人再次开启。入口名称可能随系统版本变化。\n\n依据：华为官网《华为手机/平板无障碍中的第三方应用的服务开关自动关闭》（zh-cn00410039）。";
        } else {
            guide="此机型的后台运行设置尚未验证。\n\n1. 打开本应用系统设置，查找“自启动”“后台运行”“后台活动”或“电池优化”等选项；也可在系统设置中搜索这些名称。仅对“开屏清净”按实际页面允许所需的后台运行。\n2. 返回本工具，确认无障碍服务已开启、已连接，并显示“自动跳过已就绪”。\n3. 使用“关闭首页，保留自动跳过”，再打开所选应用验证。\n\n不同品牌的单独划卡、一键清理和省电策略可能不同，本工具不会把打开设置当作配置完成。强行停止或系统清理若关闭了无障碍服务，自动跳过就无法继续，需要重新打开应用并按首页提示恢复。";
        }
        new AlertDialog.Builder(this).setTitle(Build.MANUFACTURER+" "+Build.MODEL)
                .setMessage(guide).setNegativeButton("知道了",null).setPositiveButton("打开本应用设置",(d,w)->openAppSettings()).show();
    }
    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));
        } catch (RuntimeException ignored) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (RuntimeException unavailable) {
                new AlertDialog.Builder(this).setMessage("未能打开系统设置，请手动进入设置并搜索“开屏清净”。").setPositiveButton("知道了",null).show();
            }
        }
    }
    private void biliGuide() {
        new AlertDialog.Builder(this).setTitle("哔哩哔哩防摇一摇")
                .setMessage("已核实版本：9.15.0。\n\n1. 打开哔哩哔哩，点底部“我的”。\n2. 向下找到“设置”。\n3. 在设置靠下方打开“隐私权限设置”。\n4. 关闭“允许广告访问传感器”。说明应为：关闭将不展示“摇一摇”“扭一扭”类型广告。\n5. 结束B站后台后重新启动。\n\n只关闭这一项，不要关闭系统“应用获取设备方向”，也不要误关同页其他权限。\n\n该开关状态需在B站设置页核对，本工具不把“已打开指引”当作“已配置成功”。其他版本若名称不同，以实际页面为准。")
                .setNegativeButton("知道了",null).setPositiveButton("打开哔哩哔哩",(d,w)->{
                    Intent launch=getPackageManager().getLaunchIntentForPackage("tv.danmaku.bili");
                    if(launch!=null)startActivity(launch);else new AlertDialog.Builder(this).setMessage("未找到可启动的哔哩哔哩，请手动打开。").setPositiveButton("知道了",null).show();
                }).show();
    }
    private void openSystem() {
        List<Intent> choices = new ArrayList<>();
        if (huawei()) choices.add(new Intent("huawei.intent.action.HSM_PERMISSION_MANAGER"));
        choices.add(new Intent("android.intent.action.MANAGE_PERMISSIONS")); choices.add(new Intent(Settings.ACTION_PRIVACY_SETTINGS)); choices.add(new Intent(Settings.ACTION_SETTINGS));
        for (Intent i : choices) { try { startActivity(i); return; } catch (RuntimeException ignored) {} }
    }
    private void manualGuide() {
        String brand=Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String guide;
        if (huawei()) guide="华为 HarmonyOS 4.2 / 4.3\n设置 → 应用和服务 → 权限管理。英文系统：Apps & services → Permission manager。\n\n“应用获取设备方向 / Device orientation”会持续限制应用的传感器访问，并影响自动旋转和体感控制；本工具不会替你关闭。需要这些功能的应用请保留允许。\n\n这台已测试的 HarmonyOS 4.2 未验证有只拦广告的独立跳转权限。建议先查看目标应用内部是否有“摇一摇广告”开关，没有则使用自动跳过。新版本系统的选项以实际页面为准。";
        else if (brand.contains("honor")) guide="荣耀：在设置的搜索框查找“应用自动跳转提醒”。若系统提供该选项，可开启并在跳转前拒绝。若只看到“动作与方向”权限，它会影响正常体感功能，请保留。具体入口随系统版本变化，请以实际页面为准。";
        else guide="在系统设置中搜索“应用跳转”“自动跳转提醒”；若存在，可开启跳转确认。\n\n若没有独立选项，请在目标应用内搜索“摇一摇广告”，或启用本工具的自动跳过。不要将“身体传感器”或“活动识别”权限当作加速度计开关。\n\n本工具未验证此型号的专用入口；不会猜测权限编号或自动修改系统。";
        new AlertDialog.Builder(this).setTitle(Build.MANUFACTURER+" "+Build.MODEL).setMessage(guide).setPositiveButton("知道了",null).show();
    }
    private void disclosure() {
        new AlertDialog.Builder(this).setTitle("启用前请了解")
                .setMessage("无障碍服务拥有读取界面和模拟点击的能力。开屏清净仅在你选中的应用进入前台后 12 秒内，读取控件文字、位置和标识，在本机识别广告跳过按钮。页面内容不会保存或上传。\n\n没有无障碍服务也能使用系统权限防护。你可随时关闭服务或暂停自动跳过。")
                .setNegativeButton("暂不开启",null).setPositiveButton("了解，前往设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }
    private static final class App { final String pkg,label; App(String p,String l){pkg=p;label=l;} }
    private void chooseApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> apps = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (ResolveInfo r : getPackageManager().queryIntentActivities(launcher,0)) {
            String p = r.activityInfo.packageName;
            if (!p.equals(getPackageName()) && !p.equals("com.android.settings") && !p.equals("com.huawei.systemmanager") && seen.add(p)) apps.add(new App(p,r.loadLabel(getPackageManager()).toString()));
        }
        Collator sort = Collator.getInstance(Locale.CHINA); apps.sort((a,b)->sort.compare(a.label,b.label));
        Set<String> chosen = Prefs.selected(this);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(16),0,dp(16),0);
        TextView note = text("只选择需要自动跳过广告的应用；建议避开银行、支付、导航及体感游戏。",13,MUTED); layout.addView(note);
        EditText search = new EditText(this); search.setSingleLine(); search.setHint("搜索应用名称"); layout.addView(search);
        Button common = new Button(this); common.setText("勾选常见广告应用（可再调整）"); common.setTextSize(12); layout.addView(common);
        ScrollView scroller = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroller.addView(list); layout.addView(scroller,new LinearLayout.LayoutParams(-1,dp(340)));
        Runnable refill = () -> {
            String q=search.getText().toString().toLowerCase(Locale.ROOT); list.removeAllViews();
            for (App app : apps) if (app.label.toLowerCase(Locale.ROOT).contains(q) || app.pkg.contains(q)) {
                CheckBox check = new CheckBox(this); check.setText(app.label); check.setTextSize(15); check.setChecked(chosen.contains(app.pkg));
                check.setOnCheckedChangeListener((b,on)->{if(on)chosen.add(app.pkg);else chosen.remove(app.pkg);}); list.addView(check,new LinearLayout.LayoutParams(-1,dp(48)));
            }
        };
        common.setOnClickListener(v->{ Set<String> suggestions=new HashSet<>(Arrays.asList("tv.danmaku.bili","com.taobao.taobao","com.jingdong.app.mall","com.xunmeng.pinduoduo","com.sankuai.meituan","com.baidu.netdisk","com.netease.cloudmusic","com.tencent.qqmusic","com.zhihu.android","com.baidu.tieba","com.cainiao.wireless","com.taobao.idlefish","com.xingin.xhs","ctrip.android.view","com.Qunar")); for(App app:apps)if(suggestions.contains(app.pkg))chosen.add(app.pkg); refill.run(); });
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refill.run();}public void afterTextChanged(Editable e){}});
        refill.run();
        new AlertDialog.Builder(this).setTitle("选择应用").setView(layout).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{Prefs.get(this).edit().putStringSet("apps",new HashSet<>(chosen)).apply();render();}).show();
    }
}
