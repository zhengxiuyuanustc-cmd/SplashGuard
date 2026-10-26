package org.splashguard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.Gravity;
import android.view.View;
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

/** A local, explicit setup flow. Returning from system settings never rebuilds the screen. */
public final class MainActivity extends Activity {
    private static final int GREEN=Color.rgb(18,104,89), INK=Color.rgb(28,46,41), MUTED=Color.rgb(90,107,100);
    private static final int CANVAS=Color.rgb(245,247,245), MINT=Color.rgb(230,241,235), LINE=Color.rgb(222,229,223);
    private static final String WEBSITE="https://zhengxiuyuanustc-cmd.github.io/xiuyuanzheng.github.io/splashguard/";
    private LinearLayout content;
    private ScrollView scroll;
    private TextView stateTitle,stateBody,selectedSummary,permissionSummary,recordsSummary,wizardSummary;
    private Button stateAction;
    private Switch autoSwitch;
    private AlertDialog appPickerDialog;
    private boolean changingSwitch,onboarding;
    private int page,wizardStep;
    private final int[] pageScroll={0,0,0};
    private int wizardScroll;
    private Set<String> expanded=new HashSet<>();
    private String lastStatus="";
    private final Handler statusHandler=new Handler(Looper.getMainLooper());
    private final Runnable statusRefresh=new Runnable(){
        @Override public void run(){refreshStatus();statusHandler.postDelayed(this,1000);}
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        SharedPreferences p=Prefs.get(this);
        boolean legacy=p.contains("enabled")||p.contains("apps")||p.contains("count")||p.contains("last_time");
        if(saved!=null){
            onboarding=saved.getBoolean("onboarding");page=saved.getInt("page",0);
            wizardStep=OnboardingPolicy.clampStep(saved.getInt("wizardStep",0));
            int[] positions=saved.getIntArray("pageScroll");if(positions!=null&&positions.length==3)System.arraycopy(positions,0,pageScroll,0,3);
            wizardScroll=saved.getInt("wizardScroll",0);
            ArrayList<String> open=saved.getStringArrayList("expanded");if(open!=null)expanded=new HashSet<>(open);
        } else {
            onboarding=OnboardingPolicy.shouldOpen(p.getBoolean("onboarding_seen",false),legacy,p.getBoolean("setup_in_progress",false));
            wizardStep=onboarding?OnboardingPolicy.clampStep(p.getInt("setup_step",0)):0;
            if(!onboarding&&!p.getBoolean("onboarding_seen",false))p.edit().putBoolean("onboarding_seen",true).apply();
        }
        if(onboarding)persistWizard();
        render();
    }
    @Override public void onResume(){super.onResume();refreshStatus();statusHandler.removeCallbacks(statusRefresh);statusHandler.post(statusRefresh);}
    @Override public void onPause(){rememberScroll();statusHandler.removeCallbacks(statusRefresh);super.onPause();}
    @Override public void onDestroy(){
        statusHandler.removeCallbacks(statusRefresh);
        AlertDialog dialog=appPickerDialog;appPickerDialog=null;
        if(dialog!=null){dialog.setOnDismissListener(null);dialog.dismiss();}
        super.onDestroy();
    }
    @Override public void onSaveInstanceState(Bundle out){
        rememberScroll();out.putBoolean("onboarding",onboarding);out.putInt("page",page);out.putInt("wizardStep",wizardStep);
        out.putIntArray("pageScroll",pageScroll);out.putInt("wizardScroll",wizardScroll);out.putStringArrayList("expanded",new ArrayList<>(expanded));super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed(){if(onboarding){if(wizardStep>0)moveWizard(-1);else finishWizard();}else if(page!=0)switchPage(0);else super.onBackPressed();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout card(int color){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(20),dp(20),dp(20),dp(20));c.setBackground(shape(color,18));content.addView(c,new LinearLayout.LayoutParams(-1,-2));gap(content,14);return c;}
    private LinearLayout card(){return card(Color.WHITE);}
    private void title(LinearLayout c,String value,int size){TextView t=text(value,size,INK);t.setTypeface(null,Typeface.BOLD);c.addView(t);}
    private void body(LinearLayout c,String value){gap(c,8);c.addView(text(value,16,MUTED));}
    private Button button(LinearLayout c,String label,boolean primary,View.OnClickListener action){
        Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:GREEN);
        b.setBackground(shape(primary?GREEN:MINT,12));b.setMinHeight(dp(52));b.setMinimumHeight(dp(52));b.setPadding(dp(14),dp(10),dp(14),dp(10));b.setElevation(0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);c.addView(b,lp);b.setOnClickListener(action);return b;
    }
    private void disclosureSection(LinearLayout c,String key,String label,String explanation){
        Button header=button(c,label+(expanded.contains(key)?"  −":"  ＋"),false,null);
        TextView details=text(explanation,16,MUTED);gap(c,10);c.addView(details);details.setVisibility(expanded.contains(key)?View.VISIBLE:View.GONE);
        header.setOnClickListener(v->{boolean open=!expanded.contains(key);if(open)expanded.add(key);else expanded.remove(key);details.setVisibility(open?View.VISIBLE:View.GONE);header.setText(label+(open?"  −":"  ＋"));});
    }
    private void sectionLabel(String value){TextView t=text(value,14,MUTED);t.setTypeface(null,Typeface.BOLD);gap(content,8);content.addView(t);gap(content,10);}
    private void rememberScroll(){if(scroll!=null){if(onboarding)wizardScroll=scroll.getScrollY();else pageScroll[page]=scroll.getScrollY();}}
    private void switchPage(int next){rememberScroll();page=next;render();}
    private void render(){
        stateTitle=null;stateBody=null;stateAction=null;autoSwitch=null;selectedSummary=null;permissionSummary=null;recordsSummary=null;wizardSummary=null;lastStatus="";
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CANVAS);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(24),dp(20),dp(24));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(onboarding)renderWizard();else {if(page==0)renderHome();else if(page==1)renderShake();else renderSettings();root.addView(navigation());}
        setContentView(root);root.requestApplyInsets();refreshStatus();
        final int restore=onboarding?wizardScroll:pageScroll[page];scroll.post(()->scroll.scrollTo(0,restore));
    }
    private LinearLayout navigation(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);nav.setPadding(dp(12),dp(8),dp(12),dp(8));nav.setBackgroundColor(Color.WHITE);
        String[] labels={"首页","防摇一摇","设置"};for(int i=0;i<labels.length;i++){final int index=i;Button b=new Button(this);b.setText(labels[i]);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(page==i?GREEN:MUTED);b.setBackground(shape(page==i?MINT:Color.WHITE,12));b.setElevation(0);b.setPadding(dp(4),dp(12),dp(4),dp(12));b.setMinHeight(dp(56));b.setMinimumHeight(dp(56));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);nav.addView(b,lp);b.setOnClickListener(v->{if(page!=index)switchPage(index);});}return nav;
    }
    private void pageHeading(String heading,String subtitle){title(content,heading,30);body(content,subtitle);gap(content,22);}
    private void statusCard(){
        LinearLayout status=card(MINT);stateTitle=text("检查状态…",23,INK);stateTitle.setTypeface(null,Typeface.BOLD);status.addView(stateTitle);gap(status,10);stateBody=text("",16,MUTED);status.addView(stateBody);
        stateAction=button(status,"继续设置",true,v->repairStatus());
    }
    private void addAutoSwitch(LinearLayout c){
        autoSwitch=new Switch(this);autoSwitch.setText("启用自动跳过");autoSwitch.setTextColor(INK);autoSwitch.setTextSize(19);autoSwitch.setSwitchMinWidth(dp(48));autoSwitch.setPadding(0,dp(8),0,dp(8));
        autoSwitch.setMinHeight(dp(56));c.addView(autoSwitch,new LinearLayout.LayoutParams(-1,-2));autoSwitch.setChecked(Prefs.enabled(this));
        autoSwitch.setOnCheckedChangeListener((b,on)->{if(!changingSwitch){Prefs.get(this).edit().putBoolean("enabled",on).apply();refreshStatus();}});
    }
    private void renderHome(){
        pageHeading("开屏清净","少一点广告打扰，多一点使用自主。");statusCard();
        LinearLayout shake=card();title(shake,"防摇一摇广告",24);body(shake,"先关闭应用内的广告专用开关，保留正常旋转和体感功能。");
        button(shake,"查看防摇一摇设置",true,v->switchPage(1));gap(shake,10);shake.addView(text("设置引导 · 不代表已自动拦截全部跳转",14,GREEN));
        LinearLayout auto=card();title(auto,"自动跳过开屏广告",21);addAutoSwitch(auto);
        selectedSummary=text("",16,MUTED);auto.addView(selectedSummary);button(auto,"选择需要处理的应用",false,v->chooseApps());
        permissionSummary=text("",16,MUTED);gap(auto,14);auto.addView(permissionSummary);button(auto,"设置无障碍服务",false,v->disclosure());
        disclosureSection(auto,"auto-principle","如何帮你关闭广告","进入所选应用后的短暂开屏时段内，按本地规则检查控件文字、标识和位置，只尝试点击明确的广告跳过按钮。没有可读取按钮或样式不匹配时可能漏过；操作计数不代表每次都成功。\n\n请只选需要帮助的应用，首次使用时观察效果；可随时关掉上方开关。");
        LinearLayout background=card();title(background,"关闭首页后，继续自动跳过",20);body(background,"系统需允许服务在后台运行。强行停止或系统清理若关闭了服务，需要本人重新开启。");
        button(background,"查看本机后台指引",false,v->backgroundGuide());button(background,"广告未跳过？检查原因",false,v->SetupHelpActivity.open(this,"no_skip"));button(background,"关闭首页，保留自动跳过",true,v->closeHome());
        gap(content,4);content.addView(text("本地处理  ·  无联网权限  ·  随时可停",14,MUTED));
    }
    private void renderShake(){
        pageHeading("防摇一摇广告","只针对广告设置，不关闭手机全局方向感应。");
        LinearLayout local=card(MINT);title(local,"本机防摇一摇选项",22);body(local,"部分OriginOS、Flyme版本提供仅在开屏时限制动作传感器的选项。先查看本机指引；没有专用选项时，不关闭整体方向感应。");button(local,"查看本机广告专用设置",true,v->SetupHelpActivity.open(this,"shake"));
        LinearLayout bili=card();title(bili,"哔哩哔哩广告专用开关",22);body(bili,"已核对：哔哩哔哩 9.15.0。其他版本以实际设置页面为准。");
        gap(bili,14);TextView path=text("我的 → 设置 → 隐私权限设置\n关闭“允许广告访问传感器”",18,GREEN);path.setTypeface(null,Typeface.BOLD);bili.addView(path);button(bili,"查看B站设置步骤",true,v->biliGuide());
        body(bili,"该开关由B站保存，请到B站核对。阅读本页或启用自动跳过，都不代表已经关闭了摇一摇跳转。");
        LinearLayout others=card();title(others,"其他应用与本机设置",21);body(others,Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE);
        button(others,"查看本机手动设置建议",false,v->manualGuide());button(others,"打开系统权限设置",false,v->openSystem());button(others,"查看网页机型指南",false,v->openWebsite("#guides"));
        disclosureSection(others,"shake-principle","防摇一摇的原理与边界","应用提供独立的广告传感器开关时，关闭它可以限制广告使用运动传感器；正常旋转和体感所需的整体传感器权限保留原样。\n\n开屏清净不会全局关闭加速度计或设备方向权限。自动跳过只是尽早结束广告，广告结束前仍可能发生跳转。没有广告专用开关的应用，目前无法保证消除摇一摇跳转。\n\n其他品牌与系统入口尚需实测，建议先看应用自己的隐私/广告设置，不照搬未经验证的路径。");
    }
    private void renderSettings(){
        pageHeading("设置","权限、记录和退出方式，都由你决定。");
        LinearLayout setup=card();title(setup,"设置向导",21);body(setup,"按步骤了解权限、选择应用，并检查当前功能状态。随时可以稍后设置。");button(setup,"重新打开设置向导",true,v->startWizard());
        LinearLayout privacy=card();title(privacy,"隐私与本地记录",21);body(privacy,"App 没有联网权限，不上传界面内容、应用选择或设备信息。");
        recordsSummary=text("",16,GREEN);gap(privacy,12);privacy.addView(recordsSummary);
        disclosureSection(privacy,"privacy","查看权限与保存内容","无障碍权限允许读取界面控件和模拟点击，系统授权范围较大。服务先判断前台应用，再按你的选择与本地规则处理。\n\n不保存完整页面或截图。手机本地保存功能开关、所选应用、操作计数、最近按钮文字/应用/时间及少量诊断记录；高级工具只在进程内暂存固定字段的最小判定样本；本人可预览后导出文件，文件不会因清除记录而自动删除。这些记录不会自动上传。下方可清除操作记录，保留你的应用选择和功能开关。\n\n打开网站、反馈或打赏由系统浏览器处理，需要联网；网站托管方及支付平台会处理各自的访问或付款信息。");
        button(privacy,"清除本地操作记录",false,v->clearRecords());
        LinearLayout control=card();title(control,"随时暂停或停用",21);body(control,"暂时不用：关闭首页“启用自动跳过”。彻底关闭：在系统无障碍设置中关闭本服务；也可直接卸载。");button(control,"打开系统无障碍设置",false,v->openAccessibility());
        disclosureSection(control,"background","后台运行说明","系统电池管理、划掉卡片、一键清理或重启可能关闭第三方无障碍服务。请按本机指引设置后，返回首页确认真实状态。\n\n“关闭首页，保留自动跳过”仅关闭界面；强行停止会停止服务。B站的广告专用开关保存在B站内，不依赖本工具后台维持。");button(control,"查看本机后台指引",false,v->backgroundGuide());
        LinearLayout tools=card();title(tools,"兼容排查与离线工具",21);body(tools,"分别检查权限、实际服务连接和应用选择。高级工具支持规则限制、撤销与本人决定是否导出的最小判定样本。");button(tools,"检查本机设置与未跳过原因",false,v->SetupHelpActivity.open(this,"no_skip"));button(tools,"离线规则与诊断",false,v->OfflineToolsActivity.open(this));
        LinearLayout author=card();title(author,"作者与支持",21);body(author,"免费开源，全部功能无需付费。自愿打赏仅用于支持作者，不解锁功能。");button(author,"作者、网站与自愿支持",false,v->openWebsite("#support"));
        String version;try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){version="预览版";}
        gap(content,4);content.addView(text("SplashGuard "+version+" · MIT 开源预览版",14,MUTED));
    }
    private DeviceGuidePolicy.ServiceState serviceState(){
        try{String raw=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(raw==null)return DeviceGuidePolicy.ServiceState.UNKNOWN;
            ComponentName own=new ComponentName(this,SkipService.class);
            for(String value:raw.split(":"))if(own.equals(ComponentName.unflattenFromString(value)))return DeviceGuidePolicy.ServiceState.ON;
            return DeviceGuidePolicy.ServiceState.OFF;
        }catch(RuntimeException unavailable){return DeviceGuidePolicy.ServiceState.UNKNOWN;}
    }
    private boolean rulesValid(){try{RuleData.loadStore(this);return true;}catch(RuntimeException invalid){return false;}}
    private void refreshStatus(){
        SharedPreferences p=Prefs.get(this);boolean enabled=Prefs.enabled(this),connected=SkipService.isConnected(),valid=rulesValid();
        DeviceGuidePolicy.ServiceState permission=serviceState();int count=Prefs.selected(this).size();
        String signature=enabled+":"+permission+":"+connected+":"+valid+":"+count+":"+p.getInt("count",0)+":"+p.getLong("last_time",0)+":"+p.getString("diagnostic","");if(signature.equals(lastStatus))return;lastStatus=signature;
        if(autoSwitch!=null&&autoSwitch.isChecked()!=enabled){changingSwitch=true;autoSwitch.setChecked(enabled);changingSwitch=false;}
        if(selectedSummary!=null)selectedSummary.setText("已选择 "+count+" 个应用");
        String permissionText=permission==DeviceGuidePolicy.ServiceState.ON?"已开启":permission==DeviceGuidePolicy.ServiceState.OFF?"未开启":"状态暂时无法读取";
        if(permissionSummary!=null)permissionSummary.setText("无障碍授权："+permissionText+"\n服务连接："+(connected?"已连接":"未连接"));
        if(wizardSummary!=null)wizardSummary.setText("已选择 "+count+" 个应用\n无障碍授权："+permissionText+"\n服务连接："+(connected?"已连接":"未连接")+"\n自动跳过："+(enabled?"已开启":"已关闭"));
        if(recordsSummary!=null)recordsSummary.setText("本地操作计数："+p.getInt("count",0)+" 次\n计数代表操作提交，不等于每次广告都消失。");
        if(stateTitle==null)return;
        stateTitle.setTextColor(Color.rgb(145,87,20));stateAction.setVisibility(View.VISIBLE);
        if(!enabled){stateTitle.setText("自动跳过已暂停");stateBody.setText("开启开关后，仍需选择应用并由本人授权无障碍服务。");stateAction.setText("启用自动跳过");}
        else if(!valid){stateTitle.setText("请检查本地规则");stateBody.setText("规则状态无法验证，当前已停止处理。可在离线工具中恢复内置规则。");stateAction.setText("检查规则状态");}
        else if(permission==DeviceGuidePolicy.ServiceState.OFF){stateTitle.setText("还需开启无障碍服务");stateBody.setText("当前查询显示授权未开启，请到系统设置中核对。");stateAction.setText("设置无障碍服务");}
        else if(!connected){stateTitle.setText(permission==DeviceGuidePolicy.ServiceState.UNKNOWN?"请核对服务状态":"等待服务连接");stateBody.setText("服务目前未连接。权限开关、功能开关和应用选择需要分别检查，无需开启所有权限。");stateAction.setText("检查服务与设置");}
        else if(count==0){stateTitle.setText("还需选择应用");stateBody.setText("服务已连接；选择需要处理的应用后才会尝试跳过。");stateAction.setText("选择应用");}
        else{stateTitle.setText("自动跳过已就绪");stateTitle.setTextColor(GREEN);stateBody.setText("服务已连接，将在所选应用的开屏时段尝试处理。不同广告样式可能漏过；操作提交不等于广告成功关闭。");stateAction.setVisibility(View.GONE);}
    }
    private void repairStatus(){
        if(!Prefs.enabled(this)){Prefs.get(this).edit().putBoolean("enabled",true).apply();refreshStatus();}
        else if(!rulesValid())OfflineToolsActivity.open(this);
        else if(serviceState()==DeviceGuidePolicy.ServiceState.OFF||!SkipService.isConnected())SetupHelpActivity.open(this,"accessibility");
        else chooseApps();
    }
    private void closeHome(){if(Prefs.enabled(this)&&serviceState()!=DeviceGuidePolicy.ServiceState.OFF&&SkipService.isConnected()&&rulesValid()&&!Prefs.selected(this).isEmpty()){finishAndRemoveTask();return;}SetupHelpActivity.open(this,"background");}

    private void startWizard(){rememberScroll();onboarding=true;wizardStep=0;wizardScroll=0;persistWizard();render();}
    private void persistWizard(){Prefs.get(this).edit().putBoolean("setup_in_progress",true).putInt("setup_step",wizardStep).apply();}
    private void moveWizard(int delta){wizardStep=OnboardingPolicy.clampStep(wizardStep+delta);wizardScroll=0;persistWizard();render();}
    private void finishWizard(){Prefs.get(this).edit().putBoolean("onboarding_seen",true).putBoolean("setup_in_progress",false).remove("setup_step").apply();onboarding=false;page=0;wizardScroll=0;render();}
    private void renderWizard(){
        TextView step=text("首次设置  "+(wizardStep+1)+" / "+OnboardingPolicy.STEP_COUNT,15,GREEN);step.setTypeface(null,Typeface.BOLD);content.addView(step);gap(content,14);
        String[] headings={"欢迎使用开屏清净","选择需要帮助的应用","由你开启无障碍服务","设置防摇一摇与后台","看清状态，再开始使用"};
        title(content,headings[wizardStep],29);gap(content,20);
        LinearLayout c=card();
        if(wizardStep==0){
            title(c,"两种方式，减少打扰",21);body(c,"① 防摇一摇广告：引导你关闭应用内的广告专用传感器开关。\n② 自动跳过：在本机识别明确的广告跳过按钮并尝试点击。");
            disclosureSection(c,"setup-privacy","了解权限、隐私与使用边界","App 无联网权限，不上传用户或设备信息。无障碍服务有读取界面控件和模拟点击的能力，需你本人到系统设置授权。\n\n本地保留功能设置、应用选择、操作计数和最近操作/诊断记录，可在设置页清除操作记录。不会保存完整页面或截图。\n\n不关闭全局方向传感器；应用正常旋转、体感权限保持原样。广告识别可能漏过或误判，首次启用时请观察，可随时暂停。");
        }else if(wizardStep==1){
            title(c,"只选你需要的",21);body(c,"建议先选择一两个有开屏广告的应用试用，避开银行、支付、导航及体感游戏。可搜索名称，也可稍后再选。");
            selectedSummary=text("",18,GREEN);gap(c,16);c.addView(selectedSummary);button(c,"选择应用",true,v->chooseApps());
        }else if(wizardStep==2){
            title(c,"权限由你决定",21);body(c,"点下方按钮阅读用途，再到系统的无障碍设置中开启“开屏清净 · 自动跳过”。返回后会更新当前状态，继续停留在这一步。");
            wizardSummary=text("",17,GREEN);gap(c,16);c.addView(wizardSummary);button(c,"了解并前往无障碍设置",true,v->disclosure());gap(c,12);addAutoSwitch(c);body(c,"你可稍后设置。未授权、未选应用或未启用时，自动跳过不会运行。");
        }else if(wizardStep==3){
            title(c,"防摇一摇广告",23);body(c,"B站 9.15.0：我的 → 设置 → 隐私权限设置 → 关闭“允许广告访问传感器”。只改广告专用选项，保留正常方向感应。");
            button(c,"查看本机防摇一摇选项",true,v->SetupHelpActivity.open(this,"shake"));button(c,"查看B站防摇一摇步骤",false,v->biliGuide());body(c,"设置是否完成须到B站核对；本工具不能读取或确认该开关。其他应用先查找自己的广告设置。");
            button(c,"查看本机后台运行指引",false,v->backgroundGuide());disclosureSection(c,"setup-background","为什么还要检查后台设置","部分手机会在划掉卡片、清理后台、省电或重启后关闭无障碍服务。按本机指引设置后，返回首页确认已连接，并实际打开所选应用验证。打开指引不等于已经完成配置。");
        }else{
            title(c,"当前设置结果",21);wizardSummary=text("",17,GREEN);gap(c,14);c.addView(wizardSummary);body(c,"完成向导不会自动授权或开启功能。防摇一摇广告的应用内开关仍需本人核对。");statusCard();
        }
        if(wizardStep>0)button(content,"上一步",false,v->moveWizard(-1));
        button(content,wizardStep==OnboardingPolicy.STEP_COUNT-1?"完成，进入首页":wizardStep==0?"开始设置":"下一步",true,v->{if(wizardStep==OnboardingPolicy.STEP_COUNT-1)finishWizard();else moveWizard(1);});
        if(wizardStep!=OnboardingPolicy.STEP_COUNT-1)button(content,"稍后设置，先看首页",false,v->finishWizard());
    }
    private boolean huawei(){return DeviceGuidePolicy.vendor(Build.MANUFACTURER,Build.BRAND)==DeviceGuidePolicy.Vendor.HUAWEI;}
    private void backgroundGuide(){SetupHelpActivity.open(this,"background");}
    private void openAppSettings(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));}catch(RuntimeException ignored){openSettings();}}
    private void biliGuide(){new AlertDialog.Builder(this).setTitle("B站防摇一摇广告")
        .setMessage("已核对版本：哔哩哔哩 9.15.0。\n\n1. 打开B站 → 我的 → 设置。\n2. 打开“隐私权限设置”。\n3. 关闭“允许广告访问传感器”。说明应包含关闭后不展示“摇一摇”“扭一扭”类型广告。\n4. 结束B站后台后重新打开验证。\n\n只关闭广告专用选项；不要关闭系统里的整个设备方向权限。该开关由B站保存，本工具不能把阅读指引当作配置成功。其他版本以实际页面为准。")
        .setNegativeButton("知道了",null).setPositiveButton("打开哔哩哔哩",(d,w)->{try{Intent launch=getPackageManager().getLaunchIntentForPackage("tv.danmaku.bili");if(launch==null)throw new IllegalStateException();startActivity(launch);}catch(RuntimeException unavailable){message("请手动打开B站","未找到可启动的哔哩哔哩。");}}).show();}
    private void manualGuide(){new AlertDialog.Builder(this).setTitle(Build.MANUFACTURER+" "+Build.MODEL)
        .setMessage((huawei()?"本机相关实测仅覆盖华为 GOA-AL80 / HarmonyOS 4.2；没有验证到只拦广告的独立系统跳转权限。\n\n":"此机型的广告专用系统入口尚未实测。\n\n")+"先在目标应用的隐私或广告设置中查找“摇一摇广告”“广告访问传感器”。若系统实际提供“应用跳转提醒”，可了解提示后自行配置。\n\n不要用整体“设备方向”“动作与方向”“身体传感器”或“活动识别”权限替代广告专用设置，它们可能影响正常功能，且未必对应加速度计。\n\n没有独立开关时，自动跳过只能作为补充，不能保证消除晃动跳转。")
        .setPositiveButton("知道了",null).show();}
    private void openSystem(){openAppSettings();}
    private void disclosure(){new AlertDialog.Builder(this).setTitle("了解后，再由你授权")
        .setMessage("无障碍服务允许读取界面控件并模拟点击。开屏清净先判断前台应用，再在你选中的应用开屏时段按本地规则识别明确的跳过按钮。\n\nApp 没有联网权限；不保存完整页面或截图。本地保存设置、应用选择、计数和最近按钮/诊断记录，可清除操作记录。不会上传这些内容。\n\n你可随时暂停、关闭无障碍服务或卸载。未授权也可以查看防摇一摇设置指引。")
        .setNegativeButton("暂不开启",null).setPositiveButton("了解，前往系统设置",(d,w)->openAccessibility()).show();}
    private void openAccessibility(){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(RuntimeException unavailable){openSettings();}}
    private void openSettings(){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(RuntimeException unavailable){message("请手动打开系统设置","找到开屏清净后，按页面说明检查权限。");}}
    private void openWebsite(String anchor){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(WEBSITE+anchor)));}catch(RuntimeException unavailable){message("请用浏览器打开",WEBSITE+anchor);}}
    private void clearRecords(){new AlertDialog.Builder(this).setTitle("清除本地操作记录？").setMessage("清除计数、最近操作和进程内诊断样本，保留规则、应用选择、功能开关与向导状态。本人已导出的文件需自行到保存位置删除。").setNegativeButton("取消",null).setPositiveButton("清除记录",(d,w)->{Prefs.get(this).edit().remove("count").remove("last").remove("last_time").remove("diagnostic").remove("last_diag_time").apply();RuleData.clearSample();refreshStatus();}).show();}
    private void message(String title,String body){new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("知道了",null).show();}
    private static final class App{final String pkg,label;App(String p,String l){pkg=p;label=l;}}
    private void chooseApps(){
        if(isFinishing()||isDestroyed()||appPickerDialog!=null)return;
        // Reserve the single dialog before querying packages or building its views.
        // Queued/re-entrant entry points must not start another picker while it opens.
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择需要处理的应用").create();
        appPickerDialog=dialog;
        dialog.setOnDismissListener(d->{if(appPickerDialog==dialog)appPickerDialog=null;});
        try {
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);List<App> apps=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(ResolveInfo r:getPackageManager().queryIntentActivities(launcher,0)){String p=r.activityInfo.packageName;if(!p.equals(getPackageName())&&!p.equals("com.android.settings")&&!p.equals("com.huawei.systemmanager")&&seen.add(p))apps.add(new App(p,r.loadLabel(getPackageManager()).toString()));}
        Collator order=Collator.getInstance(Locale.CHINA);apps.sort((a,b)->order.compare(a.label,b.label));Set<String> chosen=Prefs.selected(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(4),dp(20),0);box.addView(text("只选需要帮助的应用，可随时调整。建议避开银行、支付、导航及体感游戏。",15,MUTED));
        EditText search=new EditText(this);search.setSingleLine();search.setHint("搜索应用名称或包名");search.setTextSize(17);search.setContentDescription("搜索应用");box.addView(search,new LinearLayout.LayoutParams(-1,dp(56)));
        Button common=new Button(this);common.setText("勾选常见广告应用（可调整）");common.setTextSize(15);common.setAllCaps(false);box.addView(common);
        ScrollView scroller=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroller.addView(list);int listHeight=Math.min(340,Math.max(160,(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density)-390));box.addView(scroller,new LinearLayout.LayoutParams(-1,dp(listHeight)));
        Runnable refill=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);list.removeAllViews();int matched=0;for(App app:apps)if(app.label.toLowerCase(Locale.ROOT).contains(q)||app.pkg.toLowerCase(Locale.ROOT).contains(q)){matched++;CheckBox check=new CheckBox(this);check.setText(app.label);check.setTextSize(17);check.setMinHeight(dp(52));check.setChecked(chosen.contains(app.pkg));check.setOnCheckedChangeListener((b,on)->{if(on)chosen.add(app.pkg);else chosen.remove(app.pkg);});list.addView(check,new LinearLayout.LayoutParams(-1,-2));}if(matched==0)list.addView(text("没有找到匹配应用",16,MUTED));};
        common.setOnClickListener(v->{Set<String> suggestions=new HashSet<>(Arrays.asList("tv.danmaku.bili","com.taobao.taobao","com.jingdong.app.mall","com.xunmeng.pinduoduo","com.sankuai.meituan","com.baidu.netdisk","com.baidu.searchbox","com.baidu.searchbox.lite","com.netease.cloudmusic","com.tencent.qqmusic","com.zhihu.android","com.baidu.tieba","com.cainiao.wireless","com.taobao.idlefish","com.xingin.xhs","ctrip.android.view","com.Qunar"));for(App app:apps)if(suggestions.contains(app.pkg))chosen.add(app.pkg);refill.run();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refill.run();}public void afterTextChanged(Editable e){}});refill.run();
        dialog.setView(box);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,"取消",(d,w)->{});
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,"保存",(d,w)->{Prefs.get(this).edit().putStringSet("apps",new HashSet<>(chosen)).apply();refreshStatus();});
        if(isFinishing()||isDestroyed()){
            appPickerDialog=null;dialog.setOnDismissListener(null);dialog.dismiss();return;
        }
        dialog.show();
        } catch(RuntimeException unavailable) {
            if(appPickerDialog==dialog)appPickerDialog=null;
            dialog.setOnDismissListener(null);dialog.dismiss();
            if(!isFinishing()&&!isDestroyed())message("暂时无法打开应用选择","请稍后再试；你的应用选择尚未更改。");
        }
    }
}
