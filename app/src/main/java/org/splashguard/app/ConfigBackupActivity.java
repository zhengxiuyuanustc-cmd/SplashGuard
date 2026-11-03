package org.splashguard.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A reviewed application-list restore. Never a dump/restore of system permissions or all preferences. */
public final class ConfigBackupActivity extends Activity {
    private static final int IMPORT=301,EXPORT=302;
    private State state;private TextView preview,notice,currentPreview;private Button confirm;private ConfigBackup displayedCurrent;
    private static final class Preview {
        final ConfigBackup backup;final Set<String> available;final String description;
        Preview(ConfigBackup backup,Set<String> available,String description){this.backup=backup;this.available=available;this.description=description;}
    }
    private static final class Result {
        final String message;final Preview preview;final boolean clear;
        Result(String message,Preview preview,boolean clear){this.message=message;this.preview=preview;this.clear=clear;}
    }
    private static final class State {
        final ExecutorService io=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());
        ConfigBackupActivity owner;boolean busy,chooser;String export;Preview preview;Result pending;
        void done(Result result){main.post(()->{busy=false;pending=result;if(owner!=null)owner.deliver();});}
    }
    public static void open(Context context){Intent intent=new Intent(context,ConfigBackupActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);if(!(context instanceof Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);Object retained=getLastNonConfigurationInstance();state=retained instanceof State?(State)retained:new State();state.owner=this;
        if(saved!=null&&retained==null)state.chooser=saved.getBoolean("chooser");render();refresh();deliver();
        if(state.busy)notice.setText("正在完成上次本地操作，请稍候…");
        if(saved!=null&&retained==null&&saved.getBoolean("busy"))notice.setText("上次文件操作已中断，请检查当前选择后重新操作；不会自动恢复或开启功能。");
    }
    @Override protected void onResume(){super.onResume();if(preview!=null)refresh();}
    @Override public Object onRetainNonConfigurationInstance(){return state;}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("chooser",state.chooser);out.putBoolean("busy",state.busy);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){if(state.owner==this)state.owner=null;if(!isChangingConfigurations())state.io.shutdownNow();super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(28,46,41));t.setPadding(0,dp(12),0,dp(8));parent.addView(t);return t;}
    private Button button(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(17);b.setAllCaps(false);b.setMinHeight(dp(52));parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->{if(!state.busy&&!state.chooser)action.run();});return b;}
    private void render(){ScrollView scroller=new ScrollView(this);scroller.setBackgroundColor(Color.rgb(245,247,245));scroller.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(22),dp(22),dp(24));scroller.addView(box);
        text(box,"备份应用选择",27);text(box,"只保存你在本工具中选择的应用包名，不是完整应用清单。文件由本人选择位置和是否分享；如选云盘，同步由该服务处理。",16);
        currentPreview=text(box,"",15);currentPreview.setTextIsSelectable(true);
        button(box,"导出上方当前应用选择",()->{try{if(displayedCurrent==null)throw new IllegalStateException();state.export=ConfigBackup.encode(displayedCurrent);choose(EXPORT,new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"SplashGuard-selected-apps.json"));}catch(RuntimeException invalid){notice.setText("应用选择无法导出，备份最多支持128项；没有自动保存或上传。");}});
        button(box,"选择备份文件，先预览",()->choose(IMPORT,new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")));
        preview=text(box,"尚未选择备份。",16);preview.setTextIsSelectable(true);
        text(box,"确认恢复会替换本工具当前的应用选择，并暂停自动跳过。只恢复当前环境可启动的应用；规则、兼容开关、操作记录、系统授权和广告传感器设置均不从文件恢复。请在首页核对后再本人启用。",16);
        confirm=button(box,"确认恢复列表并暂停自动跳过",this::restore);
        notice=text(box,"",16);button(box,"返回",this::finish);setContentView(scroller);scroller.requestApplyInsets();
    }
    private void refresh(){try{displayedCurrent=new ConfigBackup(Prefs.selected(this));currentPreview.setText("当前所选包名（仅这些内容会导出）：\n"+ConfigBackup.encode(displayedCurrent));}catch(RuntimeException invalid){displayedCurrent=null;currentPreview.setText("当前选择无法生成备份，最多支持128项。");}preview.setText(state.preview==null?"尚未选择可恢复的备份。":state.preview.description);confirm.setEnabled(state.preview!=null&&!state.busy&&!state.chooser);}
    private void choose(int request,Intent intent){state.chooser=true;refresh();try{startActivityForResult(intent.addCategory(Intent.CATEGORY_OPENABLE),request);}catch(RuntimeException unavailable){state.chooser=false;state.export=null;refresh();notice.setText("系统文件入口不可用，没有更改当前设置。");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=IMPORT&&request!=EXPORT)return;
        state.chooser=false;String outgoing=state.export;state.export=null;
        if(result!=RESULT_OK||data==null||data.getData()==null){refresh();notice.setText("已取消文件操作，当前设置没有更改。");return;}
        Uri uri=data.getData();final State job=state;final Context app=getApplicationContext();job.busy=true;refresh();notice.setText("正在读取或保存所选文件…");
        job.io.execute(()->{try{if(request==EXPORT){LocalJsonFiles.write(app.getContentResolver(),uri,outgoing);job.done(new Result("已保存应用选择，没有自动上传。",null,false));}
            else{ConfigBackup backup=ConfigBackup.decode(LocalJsonFiles.read(app.getContentResolver(),uri));job.done(new Result("请先核对预览；尚未恢复。",prepare(app,backup),false));}}
            catch(Exception invalid){job.done(new Result("文件操作未完成：格式、大小或读写不符合要求。没有恢复设置。",null,request==IMPORT));}});
    }
    private void restore(){final Preview reviewed=state.preview;if(reviewed==null)return;final State job=state;final Context app=getApplicationContext();job.busy=true;refresh();notice.setText("正在重新核对应用并保存…");
        job.io.execute(()->{try{Preview fresh=prepare(app,reviewed.backup);
            if(!fresh.available.equals(reviewed.available)){job.done(new Result("可恢复的应用已变化，请核对新的预览后再次确认。",fresh,false));return;}
            boolean saved=Prefs.get(app).edit().putStringSet("apps",new HashSet<>(fresh.available)).putBoolean("enabled",false).commit();
            job.done(new Result(saved?"应用选择已恢复，自动跳过已暂停。请返回首页检查后再本人开启。":"保存未能确认，请返回首页检查实际状态。",null,true));
        }catch(RuntimeException invalid){job.done(new Result("无法恢复应用选择，请重新检查；未自动授予任何系统权限。",null,false));}});
    }
    private void deliver(){if(isFinishing()||isDestroyed()||state.owner!=this||state.pending==null)return;Result result=state.pending;state.pending=null;if(result.clear)state.preview=null;if(result.preview!=null)state.preview=result.preview;refresh();notice.setText(result.message);}
    private static Preview prepare(Context context,ConfigBackup backup){Set<String> available=new LinkedHashSet<>();StringBuilder rows=new StringBuilder();PackageManager manager=context.getPackageManager();
        for(String pkg:backup.selectedApps){boolean safe=!pkg.equals(context.getPackageName())&&!pkg.equals("com.android.settings")&&!pkg.equals("com.huawei.systemmanager")&&!pkg.equals("com.honor.systemmanager");boolean launchable=false;
            if(safe)try{launchable=manager.getLaunchIntentForPackage(pkg)!=null;}catch(RuntimeException unknown){launchable=false;}
            if(launchable)available.add(pkg);rows.append(launchable?"将恢复：":"不恢复（未查到、不可启动或系统项）：").append(pkg).append('\n');}
        String summary="文件包含 "+backup.selectedApps.size()+" 项；本机可恢复 "+available.size()+" 项。\n确认后将替换原列表，并暂停自动跳过。"+(available.isEmpty()?"\n可恢复列表为空，确认后会清空当前选择。":"")+"\n\n"+rows;
        return new Preview(backup,available,summary);
    }
}
