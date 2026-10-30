package org.splashguard.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-armed sanitized capture and real offline matcher replay; never a click executor. */
public final class RuleReplayActivity extends Activity {
    private static final int IMPORT=401,EXPORT=402;
    private State state;private TextView status,preview,detail,notice;private Button export;
    private static final class Result {final RuleReplay.Frame frame;final String message;Result(RuleReplay.Frame frame,String message){this.frame=frame;this.message=message;}}
    private static final class State {
        final ExecutorService io=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());
        RuleReplayActivity owner;boolean busy,chooser;String outgoing;RuleReplay.Frame preview;Result pending;
        void done(Result r){main.post(()->{busy=false;pending=r;if(owner!=null)owner.deliver();});}
    }
    public static void open(Context context){Intent intent=new Intent(context,RuleReplayActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);if(!(context instanceof Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);Object retained=getLastNonConfigurationInstance();state=retained instanceof State?(State)retained:new State();state.owner=this;
        if(saved!=null&&retained==null)state.chooser=saved.getBoolean("chooser");render();refresh();deliver();
        if(state.busy)notice.setText("正在完成上次本地操作，请稍候…");
        if(saved!=null&&retained==null&&saved.getBoolean("busy"))notice.setText("上次文件操作已中断，夹具和导出内容未自动恢复，请重新选择。");}
    @Override protected void onResume(){super.onResume();if(status!=null)status.setText(RuleData.replayStatus());}
    @Override public Object onRetainNonConfigurationInstance(){return state;}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("chooser",state.chooser);out.putBoolean("busy",state.busy);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){if(state.owner==this)state.owner=null;if(!isChangingConfigurations())state.io.shutdownNow();super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout p,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(28,46,41));t.setPadding(0,dp(12),0,dp(8));p.addView(t);return t;}
    private Button button(LinearLayout p,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(17);b.setMinHeight(dp(52));p.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->{if(!state.busy&&!state.chooser)action.run();});return b;}
    private void render(){ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(245,247,245));scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(22),dp(22),dp(24));scroll.addView(box);
        text(box,"规则夹具与离线回放",27);text(box,"这是高级排查工具。与诊断结论复核不同，本页会用当前内置识别规则重新计算匹配和候选冲突。它不执行点击，不重建完整画面，也不证明广告已关闭。",16);
        status=text(box,"",16);
        button(box,"准备记录下一次所选应用的开屏",()->{if(!Prefs.enabled(this)||!SkipService.isConnected()||Prefs.selected(this).isEmpty()){notice.setText("请先本人启用功能、选择应用并连接服务；本按钮不会自动授权或启用。");return;}RuleData.requestReplayCapture();state.preview=null;state.outgoing=null;refresh();notice.setText("准备后请返回并打开需要排查的所选应用。最多等待10分钟，只记录该应用本次约12秒内的匹配线索；即使点击时段已结束仍可只读记录，不延长自动点击时间。");});
        text(box,"记录须本人先准备，默认不生成夹具。仅进程内保留有限候选和祖先：已知跳过语法／危险标记、固定ID／类名类别、临时同帧身份、版本码、屏幕尺寸和按钮边界。其他文本、包名与类名会折叠；不含截图、完整页面、账号或设备唯一标识。",16);
        button(box,"查看并重新匹配最近夹具",()->{RuleReplay.Frame frame=RuleData.lastReplay();if(frame==null){state.preview=null;state.outgoing=null;refresh();notice.setText("暂无夹具。先准备一次，再打开所选应用；没有广告或没有可访问控件时可能没有有效候选。");return;}state.preview=frame;refresh();notice.setText("已固定本次查看的夹具，导出将使用上方这份内容。");});
        button(box,"选择夹具文件，重新运行匹配",()->choose(IMPORT,new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")));
        preview=text(box,"尚未查看夹具。",16);preview.setTextIsSelectable(true);
        detail=text(box,"",14);detail.setTextIsSelectable(true);detail.setVisibility(View.GONE);
        button(box,"展开／收起脱敏文件内容",()->{if(state.preview==null)return;detail.setVisibility(detail.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);});
        export=button(box,"导出上方已查看的夹具",()->{if(state.preview==null)return;state.outgoing=RuleReplay.encode(state.preview);choose(EXPORT,new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"SplashGuard-rule-replay.json"));});
        button(box,"取消准备并清除此处夹具",()->{RuleData.clearReplay();state.preview=null;state.outgoing=null;refresh();notice.setText("已取消准备并清除进程内夹具。本人已经导出的文件需到保存位置删除。");});
        text(box,"文件中标记与几何信息不能认证按钮真假。回放不覆盖实际服务授权、用户应用选择、离线限制、12秒预算或提交前界面重验。未知布局仍可能无法复现；导出和分享完全由本人决定。",16);
        notice=text(box,"",16);button(box,"返回",this::finish);setContentView(scroll);scroll.requestApplyInsets();
    }
    private void refresh(){status.setText(RuleData.replayStatus());export.setEnabled(state.preview!=null&&!state.busy&&!state.chooser);
        if(state.preview==null){preview.setText("尚未查看夹具。");detail.setText("");detail.setVisibility(View.GONE);}else{RuleReplay.Result result=RuleReplay.evaluate(state.preview);StringBuilder text=new StringBuilder("重新匹配：").append(outcome(result.outcome)).append("\n输入候选：").append(result.candidateCount).append("；匹配身份：").append(result.matchedIdentities).append("\n规则：").append(result.ruleId).append("\n版本名称条件：").append(result.versionVerified?"命中（不等于手势许可）":"未验证／不适用");
            for(RuleReplay.Match match:result.matches)text.append("\n候选").append(match.identity).append("：").append(match.reason.name());preview.setText(text.toString());detail.setText(RuleReplay.encode(state.preview));}}
    private String outcome(RuleReplay.Outcome value){switch(value){case INCOMPLETE_SCAN:return "输入扫描不完整，应停止";case MULTIPLE_CANDIDATES:return "多个匹配身份冲突，应停止";case NO_MATCH:return "没有符合规则的候选";default:return "匹配到唯一候选，仍不代表真实点击安全或广告关闭";}}
    private void choose(int request,Intent intent){state.chooser=true;refresh();try{startActivityForResult(intent.addCategory(Intent.CATEGORY_OPENABLE),request);}catch(RuntimeException unavailable){state.chooser=false;state.outgoing=null;refresh();notice.setText("系统文件入口不可用，没有导出或改变规则。");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=IMPORT&&request!=EXPORT)return;state.chooser=false;String outgoing=state.outgoing;state.outgoing=null;
        if(result!=RESULT_OK||data==null||data.getData()==null){refresh();notice.setText("已取消文件操作。");return;}final Uri uri=data.getData();final State job=state;final Context app=getApplicationContext();job.busy=true;refresh();notice.setText("正在处理所选文件…");
        job.io.execute(()->{try{if(request==EXPORT){LocalJsonFiles.write(app.getContentResolver(),uri,outgoing);job.done(new Result(null,"夹具已保存到所选位置，没有自动上传。"));}else{RuleReplay.Frame frame=RuleReplay.decode(LocalJsonFiles.read(app.getContentResolver(),uri));RuleReplay.evaluate(frame);job.done(new Result(frame,"已用当前内置规则重新匹配，没有执行Android动作。"));}}
            catch(Exception invalid){job.done(new Result(null,invalid instanceof RuleReplay.UnsupportedSchemaException?"回放格式已更新，请用新版重新准备样本。旧文件的ID类别不能证明精确标识，不能只改版本号。":"文件操作未完成，格式／大小或读写不符合要求，没有改变规则或执行点击。"));}});
    }
    private void deliver(){if(isFinishing()||isDestroyed()||state.owner!=this||state.pending==null)return;Result result=state.pending;state.pending=null;if(result.frame!=null)state.preview=result.frame;refresh();notice.setText(result.message);}
}
