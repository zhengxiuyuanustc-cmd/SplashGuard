package org.splashguard.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit metadata preview and SAF export. No report is uploaded or imported. */
public final class CompatibilityReportActivity extends Activity {
    private static final int EXPORT=411;
    private static final class State {
        final ExecutorService io=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());
        CompatibilityReportActivity owner;CompatibilityReport report;CompatibilityReport.RuntimeEnvironment runtime=CompatibilityReport.RuntimeEnvironment.UNKNOWN;
        boolean busy,chooser;String outgoing,pending;
        void done(String text){main.post(()->{busy=false;pending=text;if(owner!=null)owner.deliver();});}
    }
    private State state;private TextView preview,json,notice;private Spinner runtime;private Button export,copy,check,details;private boolean expanded;
    public static void open(Context context){Intent i=new Intent(context,CompatibilityReportActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);if(!(context instanceof Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);Object retained=getLastNonConfigurationInstance();state=retained instanceof State?(State)retained:new State();state.owner=this;
        if(saved!=null){expanded=saved.getBoolean("expanded");if(retained==null){state.runtime=CompatibilityReport.restoreRuntime(saved.getString("runtime"));state.chooser=saved.getBoolean("chooser");}}
        render();if(saved==null&&state.report==null)inspect();else refresh();deliver();
        if(saved!=null&&retained==null)notice.setText("原预览已随进程结束而丢失，请重新检查；没有自动保存或恢复导出。若文件窗口未返回，可返回后重新进入此页。");
        if(state.busy)notice.setText("正在保存本人选择的文件，请稍候…");
    }
    @Override public Object onRetainNonConfigurationInstance(){return state;}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("expanded",expanded);out.putBoolean("chooser",state.chooser);out.putString("runtime",state.runtime.name());super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){if(state.owner==this)state.owner=null;if(!isChangingConfigurations())state.io.shutdownNow();super.onDestroy();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout p,String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(Color.rgb(28,46,41));v.setPadding(0,dp(10),0,dp(10));v.setLineSpacing(dp(3),1);p.addView(v);return v;}
    private Button button(LinearLayout p,String label,Runnable click){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(17);b.setMinHeight(dp(52));p.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->click.run());return b;}
    private void render(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,247,245));scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(22),dp(22),dp(24));scroll.addView(box);
        text(box,"兼容报告",27);text(box,"帮助其他手机定位授权、连接和广告漏过问题。只检查本工具状态和下列四个常见应用；不读取账号页面、设备唯一标识、截图或完整应用列表。报告不会自动保存或发送。",16);
        text(box,"安装／运行方式（可选，不清楚就保持默认）",17);
        final CompatibilityReport.RuntimeEnvironment[] options=CompatibilityReport.RuntimeEnvironment.values();String[] labels=new String[options.length];for(int i=0;i<labels.length;i++)labels[i]=CompatibilityReport.runtimeLabel(options[i]);
        runtime=new Spinner(this);runtime.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));runtime.setMinimumHeight(dp(52));runtime.setSelection(state.runtime.ordinal());box.addView(runtime);
        runtime.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!state.busy&&!state.chooser&&state.runtime!=options[position]){state.runtime=options[position];inspect();}}});
        check=button(box,"重新检查并更新预览",this::inspect);preview=text(box,"",16);preview.setTextIsSelectable(true);
        text(box,"以下预览是本次快照，不是持续监控。Android运行环境不能替代完整鸿蒙／MagicOS等系统版本；手选运行方式只是本人报告，不证明跨环境权限或广告已关闭。",15);
        details=button(box,"展开／收起导出字段",()->{expanded=!expanded;json.setVisibility(expanded?View.VISIBLE:View.GONE);});json=text(box,"",14);json.setTextIsSelectable(true);json.setVisibility(expanded?View.VISIBLE:View.GONE);
        copy=button(box,"复制上方报告文字",this::copy);export=button(box,"保存上方报告为文件",this::export);
        text(box,"保存需由本人选择位置。如选择云盘，同步由该服务处理；已导出或复制的内容需要本人管理。分享前请核对预览，再自行决定发给谁。",15);
        notice=text(box,"",16);button(box,"返回",this::finish);setContentView(scroll);scroll.requestApplyInsets();
    }
    private void inspect(){if(state.busy||state.chooser)return;try{state.report=snapshot();notice.setText("已更新本次预览，没有自动保存或发送。");}catch(RuntimeException failed){state.report=null;notice.setText("本次检查未完成，请稍后重新检查；没有自动导出。");}refresh();}
    private CompatibilityReport snapshot(){
        PackageManager pm=getPackageManager();String own="未知";try{own=pm.getPackageInfo(getPackageName(),0).versionName;}catch(Exception unknown){/* Explicit unknown metadata. */}
        Set<String> selected=Prefs.selected(this);List<CompatibilityReport.App> apps=new ArrayList<>();
        for(String pkg:CompatibilityReport.knownPackages()){
            CompatibilityReport.AppState status=CompatibilityReport.AppState.UNKNOWN;String name=null;long code=-1;
            try{PackageInfo p=pm.getPackageInfo(pkg,0);status=CompatibilityReport.AppState.INSTALLED;name=p.versionName;code=Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
            catch(PackageManager.NameNotFoundException missing){status=CompatibilityReport.AppState.NOT_FOUND;}catch(RuntimeException unknown){/* Do not equate a restricted query with an absent application. */}
            apps.add(new CompatibilityReport.App(pkg,status,name,code,selected.contains(pkg)));
        }
        boolean valid;try{RuleData.loadStore(this);valid=true;}catch(RuntimeException bad){valid=false;}
        return new CompatibilityReport(own,Build.MANUFACTURER,Build.BRAND,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT,Prefs.guideOnly(this),Prefs.enabled(this),ServiceStatus.query(this),SkipService.isConnected(),valid,selected.size(),state.runtime,apps);
    }
    private void refresh(){boolean idle=!state.busy&&!state.chooser,has=state.report!=null;runtime.setEnabled(idle);check.setEnabled(idle);copy.setEnabled(idle&&has);export.setEnabled(idle&&has);details.setEnabled(has);
        preview.setText(has?state.report.toText():"尚无可用预览，请先重新检查。");json.setText(has?state.report.toJson():"");}
    private void copy(){if(state.busy||state.chooser||state.report==null)return;try{ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(c==null)throw new IllegalStateException();c.setPrimaryClip(ClipData.newPlainText("开屏清净兼容报告",state.report.toText()));notice.setText("已复制到系统剪贴板，没有自动发送。请自行决定是否分享。");}catch(RuntimeException failed){notice.setText("无法复制，报告尚未发送。");}}
    private void export(){if(state.busy||state.chooser||state.report==null)return;
        state.outgoing=state.report.toJson();state.chooser=true;refresh();
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"SplashGuard-compatibility-report.json"),EXPORT);}
        catch(RuntimeException unavailable){state.chooser=false;state.outgoing=null;refresh();notice.setText("系统文件入口不可用，没有保存报告。");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=EXPORT)return;state.chooser=false;final String reviewed=state.outgoing;state.outgoing=null;
        if(result!=RESULT_OK||data==null||data.getData()==null){refresh();notice.setText("已取消，没有保存或发送报告。");return;}
        if(reviewed==null){refresh();notice.setText("原预览已丢失，请重新检查再导出；没有向所选文件写入内容。");return;}
        final State job=state;final Context app=getApplicationContext();final Uri uri=data.getData();job.busy=true;refresh();notice.setText("正在保存已预览的固定报告…");
        job.io.execute(()->{try{LocalJsonFiles.write(app.getContentResolver(),uri,reviewed);job.done("已保存报告，没有自动发送。已导出的文件需要本人管理。");}catch(Exception failed){job.done("报告未能确认保存，请检查本人选择的位置；没有自动重试或发送。");}});
    }
    private void deliver(){if(state.owner!=this||isFinishing()||isDestroyed()||state.pending==null)return;String message=state.pending;state.pending=null;refresh();notice.setText(message);}
}
