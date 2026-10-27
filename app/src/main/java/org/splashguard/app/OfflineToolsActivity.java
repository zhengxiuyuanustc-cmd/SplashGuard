package org.splashguard.app;

import android.app.Activity;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-controlled, bounded local files. No network, background upload or executable rules. */
public final class OfflineToolsActivity extends Activity {
    private static final int IMPORT_RULES=201,IMPORT_SAMPLE=202,EXPORT=203;
    private static final int INK=Color.rgb(28,46,41),GREEN=Color.rgb(18,104,89),MUTED=Color.rgb(90,107,100);
    private WorkState work;
    private DiagnosticSample previewedSample;
    private Button exportSampleButton;
    private static final class Delivery {
        final String message;final int kind;final OfflineRuleStore.Pack pack;final DiagnosticSample sample;final boolean clearPreview;
        Delivery(String message,int kind,OfflineRuleStore.Pack pack,DiagnosticSample sample,boolean clearPreview){this.message=message;this.kind=kind;this.pack=pack;this.sample=sample;this.clearPreview=clearPreview;}
    }
    /** Retains IO and its result across rotation, without retaining the previous screen. */
    private static final class WorkState {
        final ExecutorService io=Executors.newSingleThreadExecutor();
        final Handler main=new Handler(Looper.getMainLooper());
        OfflineToolsActivity owner;boolean busy,chooserOpen;Delivery pending;DiagnosticSample preview;String export;OfflineRuleStore.Pack pack;
        void complete(Delivery value){main.post(()->{busy=false;pending=value;if(owner!=null)owner.deliverPending();});}
    }
    private LinearLayout content;
    private TextView ruleStatus,notice,rulePreview,samplePreview;
    private Button applyButton,undoButton,restoreButton,pauseGeneric,pauseBili;
    private int restoreStep;
    private String error="";

    public static void open(Context context){
        Intent intent=new Intent(context,OfflineToolsActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if(!(context instanceof Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{context.startActivity(intent);}catch(RuntimeException unavailable){Toast.makeText(context,"暂时无法打开离线工具。",Toast.LENGTH_LONG).show();}
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        Object retained=getLastNonConfigurationInstance();work=retained instanceof WorkState?(WorkState)retained:new WorkState();work.owner=this;previewedSample=work.preview;
        if(saved!=null){
            if(retained==null){
                work.chooserOpen=saved.getBoolean("chooser");
                String preview=saved.getString("pack");if(preview!=null)try{work.pack=RuleJsonCodec.decodePack(preview);}catch(RuntimeException invalid){work.pack=null;}
            }
        }
        render();refresh();
        if(saved!=null&&retained==null&&saved.getBoolean("inFlight"))notice.setText("上次文件或保存操作已中断。当前规则已重新读取，请检查状态后重试；不会自动重放操作。");
        if(work.busy)notice.setText("正在完成上次本地操作，请稍候…");
        if(previewedSample!=null)samplePreview.setText(describe(previewedSample)+"\n\n"+RuleJsonCodec.encodeSample(previewedSample));
        deliverPending();
    }
    @Override public Object onRetainNonConfigurationInstance(){return work;}
    @Override protected void onSaveInstanceState(Bundle out){
        // Diagnostic previews and pending exports stay in retained memory, never in saved-state files.
        out.putBoolean("chooser",work.chooserOpen);out.putBoolean("inFlight",work.busy);
        if(work.pack!=null)out.putString("pack",RuleJsonCodec.encodePack(work.pack));super.onSaveInstanceState(out);
    }
    @Override protected void onPause(){resetRestore();super.onPause();}
    @Override protected void onDestroy(){if(work.owner==this)work.owner=null;if(!isChangingConfigurations())work.io.shutdownNow();super.onDestroy();}
    private void resetRestore(){restoreStep=0;if(restoreButton!=null)restoreButton.setText("恢复内置规则");}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private LinearLayout card(String label){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(shape(Color.WHITE));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);content.addView(c,lp);TextView h=text(label,21,INK);h.setTypeface(null,Typeface.BOLD);c.addView(h);return c;}
    private void body(LinearLayout c,String value){TextView t=text(value,16,MUTED);t.setPadding(0,dp(10),0,dp(6));c.addView(t);}
    private Button button(LinearLayout c,String label,View.OnClickListener action){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(16);b.setTextColor(GREEN);b.setBackground(shape(Color.rgb(230,241,235)));b.setMinHeight(dp(52));b.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);c.addView(b,lp);b.setOnClickListener(v->{if(!work.busy&&!work.chooserOpen){if(v!=restoreButton)resetRestore();action.onClick(v);}});return b;}
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(245,247,245));scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(22),dp(20),dp(24));scroll.addView(content);
        TextView h=text("离线规则与诊断",27,INK);h.setTypeface(null,Typeface.BOLD);content.addView(h);
        body(content,"日常使用无需操作本页。全部由本人选择文件，不会自动上传；恢复规则也不会改变应用选择或系统授权。");
        LinearLayout rules=card("规则限制");ruleStatus=text("",16,INK);rules.addView(ruleStatus);
        body(rules,"文件只能缩短识别时段、减少操作次数、限制版本或停用现有规则；不能新增点击位置、脚本、应用或识别条件。此功能不验证作者签名，请仅导入来源明确的文件。");
        body(rules,"可先单独暂停某组规则，再确认应用。暂停通用规则后，B站专用规则仍可运行；暂停B站处理会限制该App内全部开屏规则。恢复可撤销最近一次修改，或明确恢复全部内置规则。");
        pauseGeneric=button(rules,"暂停通用开屏规则（先预览）",v->previewPause(RuleCatalog.GENERIC));
        pauseBili=button(rules,"暂停B站处理（先预览）",v->previewPause(RuleCatalog.BILI));
        button(rules,"导出当前限制模板",v->exportTemplate());
        button(rules,"选择规则文件，先预览",v->choose(IMPORT_RULES));
        rulePreview=text("尚未选择文件。",15,MUTED);rulePreview.setTextIsSelectable(true);rules.addView(rulePreview);
        applyButton=button(rules,"确认应用预览中的限制",v->applyPreview());
        undoButton=button(rules,"撤销最近规则修改",v->mutate(context->RuleData.rollback(context)?"已撤销最近规则修改。":"没有可撤销的规则修改。"));
        restoreButton=button(rules,"恢复内置规则",v->{
            if(restoreStep==0){restoreStep=1;restoreButton.setText("再次点击，确认恢复内置规则");notice.setText("恢复会移除导入限制及撤销记录；应用选择、功能开关和系统权限保持原样。");}
            else{restoreStep=0;mutate(context->{RuleData.restoreBuiltIns(context);return "已恢复内置规则。";});}
        });
        LinearLayout compatibility=card("触点兼容暂不可用");
        body(compatibility,"当前仅使用按钮自身的点击动作。旧版B站触点方式尚缺可维护的数字版本证据与可靠的遮挡判断，已暂停；旧开关不会生效。今后只有补齐独立验证并重新由本人确认，才考虑恢复，不会自动沿用旧偏好。");
        Switch compat=new Switch(this);compat.setText("坐标触点暂停，保留按钮动作");compat.setTextSize(16);compat.setTextColor(INK);compat.setMinHeight(dp(60));compat.setChecked(false);compat.setEnabled(false);compatibility.addView(compat);
        LinearLayout samples=card("最小诊断样本");
        body(samples,"只暂存本次进程内最近一次判定：固定原因、规则编号、应用类别、版本数字和候选数量等。没有截图、完整页面、原始按钮文字或设备标识；关闭进程后会消失。导出前可在下方检查内容。");
        button(samples,"查看最近判定",v->previewLatest());
        samplePreview=text("尚未读取样本。",15,MUTED);samplePreview.setTextIsSelectable(true);samples.addView(samplePreview);
        exportSampleButton=button(samples,"导出上方已预览的样本",v->{if(previewedSample==null){notice.setText("请先查看最近判定，再决定是否导出。");return;}requestExport(RuleJsonCodec.encodeSample(previewedSample),"SplashGuard-diagnostic.json");});
        button(samples,"选择样本文件，离线复核",v->choose(IMPORT_SAMPLE));
        body(samples,"离线复核只解释文件中的判定条件，不重建原始画面、不执行点击，也不能证明广告实际已关闭。文件保存位置由你选择；如选择云盘，其处理由对应服务负责。");
        LinearLayout replay=card("开发者规则回放");body(replay,"主动记录一次脱敏候选和祖先夹具，再真正运行当前匹配器。与上方诊断结论解释是不同功能；不执行点击。");button(replay,"规则夹具与离线回放",v->RuleReplayActivity.open(this));
        notice=text("",16,GREEN);notice.setPadding(0,dp(18),0,dp(6));content.addView(notice);button(content,"返回",v->finish());
        setContentView(scroll);scroll.requestApplyInsets();
    }
    private void refresh(){
        try{OfflineRuleStore store=RuleData.loadStore(this);OfflineRuleStore.Pack pack=store.currentPack();ruleStatus.setText(pack==null?"当前：内置规则":"当前限制包："+pack.id+" / 版本 "+pack.version);undoButton.setEnabled(store.canRollback()&&!work.busy&&!work.chooserOpen);pauseGeneric.setEnabled(store.effective(RuleCatalog.GENERIC).enabled&&!work.busy&&!work.chooserOpen);pauseBili.setEnabled(store.effective(RuleCatalog.BILI).enabled&&!work.busy&&!work.chooserOpen);ruleStatus.append("\n通用规则："+(store.effective(RuleCatalog.GENERIC).enabled?"启用":"暂停")+"；B站处理："+(store.effective(RuleCatalog.BILI).enabled?"启用":"暂停"));error="";}
        catch(RuntimeException invalid){error="本地规则无法验证，自动处理将停止。可恢复内置规则后再试。";ruleStatus.setText(error);undoButton.setEnabled(false);pauseGeneric.setEnabled(false);pauseBili.setEnabled(false);}
        applyButton.setEnabled(work.pack!=null&&error.isEmpty()&&!work.busy&&!work.chooserOpen);
        restoreButton.setEnabled(!work.busy&&!work.chooserOpen);if(restoreStep==0)restoreButton.setText("恢复内置规则");
        if(work.pack!=null)rulePreview.setText(describe(work.pack));
        exportSampleButton.setEnabled(previewedSample!=null&&!work.busy&&!work.chooserOpen);
    }
    private void previewPause(String ruleId){
        try{OfflineRuleStore store=RuleData.loadStore(this);OfflineRuleStore.RuleLimit rule=store.effective(ruleId);if(rule==null||!rule.enabled)return;
            OfflineRuleStore.Pack current=store.currentPack();int revision=current!=null&&"local.controls".equals(current.id)?current.version+1:1;
            OfflineRuleStore.RuleLimit paused=new OfflineRuleStore.RuleLimit(rule.id,rule.packageName,rule.minVersionCode,rule.maxVersionCode,rule.windowMs,rule.maxActions,false,rule.allowGesture);
            OfflineRuleStore.Pack next=new OfflineRuleStore.Pack(1,"local.controls",revision,java.util.Collections.singletonList(paused));store.apply(next);work.pack=next;refresh();notice.setText("暂停设置尚未生效，请核对预览并确认。撤销最近规则修改可恢复上一状态。");
        }catch(RuntimeException invalid){notice.setText("不能生成暂停预览，请检查当前规则状态；没有自动更改规则。");}
    }
    private void exportTemplate(){
        try{OfflineRuleStore store=RuleData.loadStore(this);OfflineRuleStore.Pack current=store.currentPack();int next=current!=null&&"local.restrictions".equals(current.id)?current.version+1:1;
            if(next>OfflineRuleStore.MAX_PACK_VERSION)throw new IllegalArgumentException();
            requestExport(RuleJsonCodec.encodePack(new OfflineRuleStore.Pack(1,"local.restrictions",next,store.effectiveRules())),"SplashGuard-rule-limits.json");
        }catch(RuntimeException invalid){notice.setText("无法生成限制模板。请检查规则状态；版本号达到上限时需重新命名限制包。");}
    }
    private void applyPreview(){final OfflineRuleStore.Pack pack=work.pack;if(pack==null)return;mutate(context->{RuleData.apply(context,pack);return "限制已应用。已停止当前识别时段，后续重新进入应用时生效。";});}
    private interface Change{String run(Context context);}
    private void mutate(Change change){
        final WorkState task=work;final Context app=getApplicationContext();task.busy=true;refresh();notice.setText("正在保存本地设置…");
        task.io.execute(()->{String result;boolean saved=false;try{result=change.run(app);saved=true;}catch(RuntimeException invalid){result="未能保存或规则不符合限制要求，设置未确认。请重新检查当前状态；不会自动放宽规则。";}
            task.complete(new Delivery(result,0,null,null,saved));});
    }
    private void deliverPending(){
        if(isFinishing()||isDestroyed()||work.owner!=this||work.pending==null)return;
        Delivery result=work.pending;work.pending=null;
        if(result.clearPreview){work.pack=null;rulePreview.setText("尚未选择文件。");}
        if(result.kind==IMPORT_RULES){work.pack=result.pack;if(work.pack==null)rulePreview.setText("没有可应用的预览。");}
        if(result.sample!=null){previewedSample=result.sample;work.preview=previewedSample;samplePreview.setText(describe(previewedSample)+"\n\n"+RuleJsonCodec.encodeSample(previewedSample));}
        refresh();notice.setText(result.message);
    }
    private void choose(int code){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        work.chooserOpen=true;refresh();try{startActivityForResult(intent,code);}catch(RuntimeException unavailable){work.chooserOpen=false;refresh();notice.setText("系统文件选择器不可用，可稍后再试；无需开启额外存储权限。");}
    }
    private void requestExport(String json,String fileName){
        work.export=json;work.chooserOpen=true;refresh();
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,fileName),EXPORT);}
        catch(RuntimeException unavailable){work.chooserOpen=false;work.export=null;refresh();notice.setText("系统文件保存入口不可用，没有导出或上传。");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=IMPORT_RULES&&request!=IMPORT_SAMPLE&&request!=EXPORT)return;
        work.chooserOpen=false;String outgoing=work.export;work.export=null;
        if(result!=RESULT_OK||data==null||data.getData()==null){refresh();notice.setText("已取消文件操作。");return;}
        final Uri uri=data.getData();final WorkState task=work;final Context app=getApplicationContext();final ContentResolver resolver=app.getContentResolver();task.busy=true;refresh();notice.setText("正在处理所选文件…");
        task.io.execute(()->{
            OfflineRuleStore.Pack pack=null;DiagnosticSample value=null;String message;
            try{
                if(request==EXPORT){if(outgoing==null)throw new IllegalStateException();byte[] bytes=outgoing.getBytes(StandardCharsets.UTF_8);OfflineRuleStore.requireInputSize(bytes.length);
                    try(OutputStream stream=resolver.openOutputStream(uri,"wt")){if(stream==null)throw new IllegalStateException();stream.write(bytes);}message="文件已保存到你选择的位置，没有自动上传或发送。";
                }else if(request==IMPORT_RULES){OfflineRuleStore.Pack decoded=RuleJsonCodec.decodePack(read(resolver,uri));OfflineRuleStore preview=RuleData.loadStore(app);preview.apply(decoded);pack=decoded;message="文件符合限制要求，尚未生效。检查预览后再确认。";}
                else{value=RuleJsonCodec.decodeSample(read(resolver,uri));message="离线复核完成，仅解释文件中的条件，没有执行点击。";}
            }catch(Exception invalid){message="文件操作未完成：文件格式、大小或规则限制不符合要求，或系统无法读写。没有自动应用规则。";}
            task.complete(new Delivery(message,request,pack,value,false));
        });
    }
    private static String read(ContentResolver resolver,Uri uri)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
        try(InputStream stream=resolver.openInputStream(uri)){if(stream==null)throw new IllegalStateException();int n;
            while((n=stream.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();if(n==0)throw new java.io.IOException("No progress from provider");if(bytes.size()+n>OfflineRuleStore.MAX_PACK_BYTES)throw new IllegalArgumentException();bytes.write(buffer,0,n);}}
        OfflineRuleStore.requireInputSize(bytes.size());
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
    }
    private void previewLatest(){previewedSample=RuleData.lastSample();work.preview=previewedSample;samplePreview.setText(previewedSample==null?"暂无判定样本。请先在服务已连接时打开所选应用，且确认确实出现广告。":describe(previewedSample)+"\n\n"+RuleJsonCodec.encodeSample(previewedSample));refresh();}
    private String describe(OfflineRuleStore.Pack pack){StringBuilder text=new StringBuilder("待确认：").append(pack.id).append(" / 版本 ").append(pack.version);
        for(OfflineRuleStore.RuleLimit r:pack.rules)text.append("\n\n").append(r.id).append("\n").append(r.packageName).append("\n版本数字：").append(r.minVersionCode).append("—").append(r.maxVersionCode).append("\n识别时段：").append(r.windowMs).append("毫秒；最多").append(r.maxActions).append("轮尝试\n").append(r.enabled?"保留规则":"停用规则").append(r.allowGesture?"；保留旧格式能力字段，当前坐标动作仍暂停":"；禁止触点兼容");return text.toString();}
    private String describe(DiagnosticSample value){return "原因："+value.reason.name()+"\n候选数量："+value.candidateCount+"；扫描"+(value.scanComplete?"完整":"不完整")+"\n复核："+decision(value.replay())+"\n以下仅为最小判定数据，不证明广告已关闭。";}
    private String decision(DiagnosticSample.ReplayDecision value){switch(value){
        case STOP_INCOMPLETE_SCAN:return "扫描不完整，应停止";case STOP_MULTIPLE_CANDIDATES:return "多个候选冲突，应停止";case STOP_EXCLUDED:return "命中排除条件，应停止";
        case STOP_TARGET_CHANGED:return "目标已改变，应停止";case STOP_UNVERIFIED_VERSION:return "版本条件需复核";case STOP_CLOSED_SESSION:return "识别时段已结束";case STOP_SERVICE_NOT_READY:return "服务尚未就绪";
        case NO_CANDIDATE:return "没有可处理的候选";case STOP_NOT_CLICKABLE:return "按钮动作不可用";case REVIEW_CONTEXT:return "需核对广告场景";default:return "需核对按钮动作结果";}}
}
