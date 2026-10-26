package org.splashguard.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SkipService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile boolean connected;
    public static boolean isConnected() { return connected; }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SkipSession session = new SkipSession();
    private long reviewAt;
    private boolean receiverRegistered;
    private Set<String> selected;
    private long lastDiagnostic;
    private boolean scanQueued;
    private String versionPackage="";
    private RuleEvaluator.VersionInfo version=new RuleEvaluator.VersionInfo(-1,"");
    private final Runnable scanTask = () -> { scanQueued=false; scan(); };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                RuleData.replayScreenOff(SystemClock.elapsedRealtime());resetSession();
            }
            else queueScan(40); // Waking/unlocking requests a scan; it never renews a session.
        }
    };

    @Override public void onServiceConnected() {
        selected = Prefs.selected(this);
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        connected = true;
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, filter);
            receiverRegistered = true;
        }
        queueScan(40);
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (!"apps".equals(key) && !"enabled".equals(key) && !RuleData.affectsRuntime(key)) return;
        selected = Prefs.selected(this);
        if("apps".equals(key)||"enabled".equals(key))resetSession();
        else {
            // Rule changes invalidate callbacks without renewing this app's time/action budget.
            handler.removeCallbacks(scanTask);scanQueued=false;session.stop();reviewAt=0;
        }
        queueScan(40);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Do not postpone an already queued scan when animations keep emitting events.
        queueScan(40);
    }
    private void queueScan(long delay) {
        if (connected && Prefs.enabled(this) && !scanQueued) {
            scanQueued=true;
            handler.postDelayed(scanTask, delay);
        }
    }
    private void resetSession() {
        handler.removeCallbacks(scanTask);
        scanQueued=false;
        session.reset();
        reviewAt=0;
        versionPackage="";
        version=new RuleEvaluator.VersionInfo(-1,"");
    }
    private boolean unlocked() {
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return power!=null && power.isInteractive() && keyguard!=null && !keyguard.isKeyguardLocked();
    }
    private void schedule() {
        long now=SystemClock.elapsedRealtime();
        if (!session.finished(now) && selected!=null && selected.contains(session.packageName()))
            queueScan(Math.max(180, reviewAt-now));
    }
    private void scan() {
        if (!Prefs.enabled(this)) return;
        if (!unlocked()) { RuleData.replayScreenOff(SystemClock.elapsedRealtime());if (!session.packageName().isEmpty()) resetSession(); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { schedule(); return; }
        try {
            String pkg = String.valueOf(root.getPackageName());
            long now=SystemClock.elapsedRealtime();
            // End active diagnostics on any confirmed departure, before filtering selected applications.
            RuleData.observeReplayForeground(str(root.getPackageName()),now);
            if (session.observe(pkg, now)) { reviewAt=0; versionPackage=""; }
            if (selected == null || !selected.contains(pkg) || pkg.equals(getPackageName())) return;
            RuleEvaluator.VersionInfo appVersion=versionFor(pkg);
            if (!session.active(now)) {
                if (!session.done()) {
                    remember(pkg,appVersion,null,null,DiagnosticSample.Reason.WINDOW_EXPIRED,0,true);
                    diagnose(pkg,"开屏处理时段已结束");
                }
                return;
            }
            // Observe package changes before checking an in-flight gesture, so old callbacks expire.
            if (session.busy() || now < reviewAt) { schedule(); return; }
            OfflineRuleStore store=loadStoreOrStop(pkg,appVersion);
            if(store==null)return;
            Rect window = new Rect(); root.getBoundsInScreen(window);
            Rect screen=screenBounds();
            int width=screen.right,height=screen.bottom;
            // Only act on a nearly full-screen foreground app, never on dialogs or keyboards.
            if (window.width() < screen.width() * .9 || window.height() < screen.height() * .85) { schedule(); return; }
            boolean captureReplay=RuleData.wantsReplayCapture(pkg,now);
            List<AccessibilityNodeInfo> replayIdentities=new ArrayList<>();
            List<RuleReplay.Candidate> replayCandidates=new ArrayList<>();
            boolean replayComplete=true;
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            try {
                if (!collect(root, nodes, 0)) {
                    session.stop();
                    if(captureReplay)rememberReplayFrame(pkg,appVersion,width,height,false,false,false,replayCandidates);
                    remember(pkg,appVersion,null,null,DiagnosticSample.Reason.INCOMPLETE_SCAN,0,false);
                    diagnose(pkg,"界面扫描不完整，已停止本次自动点击");
                    return;
                }
                boolean evidence = false;
                boolean biliSplash = false;
                AccessibilityNodeInfo candidate=null;
                Rect candidateBounds=null;
                String candidateKey=null;
                RuleCatalog.Evaluation candidateRule=null, rejectedRule=null;
                RuleEvaluator.Node candidateSnapshot=null,rejectedSnapshot=null;
                int candidateCount=0;
                CandidateSelection<AccessibilityNodeInfo> candidates=new CandidateSelection<>();
                for (AccessibilityNodeInfo n : nodes) {
                    if (!n.isVisibleToUser() || !pkg.equals(String.valueOf(n.getPackageName()))) continue;
                    if (AdRules.adEvidence(str(n.getText())) || AdRules.adEvidence(str(n.getContentDescription()))) evidence = true;
                    if (AdRules.biliSplashMarker(n.getViewIdResourceName(),str(n.getClassName()))) biliSplash = true;
                }
                if (AdRules.biliSplashMarker(root.getViewIdResourceName(),str(root.getClassName()))) biliSplash = true;
                for (AccessibilityNodeInfo n : nodes) {
                    if (!n.isVisibleToUser() || !n.isEnabled() || !pkg.equals(String.valueOf(n.getPackageName()))) continue;
                    Rect r = new Rect(); n.getBoundsInScreen(r);
                    if (!screen.contains(r) || !window.contains(r)) continue;
                    String text = str(n.getText()), desc = str(n.getContentDescription());
                    boolean biliHit = AdRules.biliCandidate(pkg,text,n.getViewIdResourceName(),biliSplash,r.left,r.top,r.right,r.bottom,width,height)
                            || AdRules.biliCandidate(pkg,desc,n.getViewIdResourceName(),biliSplash,r.left,r.top,r.right,r.bottom,width,height);
                    boolean genericHit = AdRules.candidate(text, n.getViewIdResourceName(), evidence, r.left, r.top, r.right, r.bottom, width, height)
                            || AdRules.candidate(desc, n.getViewIdResourceName(), evidence, r.left, r.top, r.right, r.bottom, width, height);
                    RuleEvaluator.Node snapshot=snapshot(n);
                    if(!biliHit&&!genericHit&&!RuleCatalog.knownBiliTarget(snapshot))continue;
                    Ancestry ancestry=ancestors(n);
                    if(captureReplay){
                        if(replayCandidates.size()>=RuleReplay.MAX_CANDIDATES)replayComplete=false;
                        else try{int identity=replayIdentities.indexOf(n);if(identity<0){identity=replayIdentities.size();replayIdentities.add(n);}
                            replayCandidates.add(RuleReplay.Candidate.capture(identity,snapshot,ancestry.nodes,ancestry.complete,r.left,r.top,r.right,r.bottom));
                        }catch(RuntimeException unrepresentable){replayComplete=false;}
                    }
                    RuleCatalog.Evaluation evaluation=RuleCatalog.evaluate(appVersion,snapshot,ancestry.nodes,
                            ancestry.complete,evidence,biliHit,genericHit);
                    if(!evaluation.matched) {
                        // Keep the exact known-target veto; it cannot take the generic route.
                        if(rejectedRule==null||evaluation.knownBiliTarget) {rejectedRule=evaluation;rejectedSnapshot=snapshot;}
                        continue;
                    }
                    // Native node identity, never resource ID/text/bounds, defines a duplicate.
                    candidates.offer(n);
                    candidateCount++;
                    String key=str(n.getViewIdResourceName())+"|"+str(n.getClassName())+"|"+r.toShortString();
                    if (candidate==null) {
                        candidate=n; candidateBounds=r; candidateKey=key; candidateRule=evaluation;candidateSnapshot=snapshot;
                    }
                }
                if(captureReplay)rememberReplayFrame(pkg,appVersion,width,height,evidence,biliSplash,replayComplete,replayCandidates);
                // Inspect all matching nodes before retry-key filtering or any click/gesture.
                candidateCount=candidates.ambiguous()?Math.max(2,candidateCount):(candidate==null?0:1);
                if (candidates.ambiguous()) {
                    session.stop();
                    remember(pkg,appVersion,candidateRule,candidateSnapshot,DiagnosticSample.Reason.MULTIPLE_CANDIDATES,candidateCount,true);
                    diagnose(pkg,"发现多个不同的跳过候选，已停止本次自动点击");
                    return;
                }
                candidate=candidates.unique();
                if (candidate!=null && session.buttonKey()!=null && !session.buttonKey().equals(candidateKey)) {
                    session.stop();
                    remember(pkg,appVersion,candidateRule,candidateSnapshot,DiagnosticSample.Reason.TARGET_CHANGED,candidateCount,true);
                    diagnose(pkg,"跳过候选已变化，已停止本次自动点击");
                    return;
                }
                session.review(candidate!=null);
                if (session.done()) return;
                if (candidate==null) {
                    remember(pkg,appVersion,rejectedRule,rejectedSnapshot,
                            rejectedRule==null?DiagnosticSample.Reason.NO_MATCH:RuleRuntime.reason(rejectedRule.reason),0,true);
                    diagnose(pkg,"已检查，未找到符合安全规则的开屏跳过按钮");
                }
                else {
                    long submitAt=SystemClock.elapsedRealtime();
                    RuleRuntime.Gate gate=RuleRuntime.gate(store,candidateRule,pkg,appVersion.code,submitAt,
                            session.deadline(),session.attempts(),RuleData.gestureCompatibilityEnabled(this));
                    if(!gate.allowed) {
                        session.stop();
                        remember(pkg,appVersion,candidateRule,candidateSnapshot,gate.reason,candidateCount,true);
                        diagnose(pkg,"当前离线规则或处理预算不允许点击");
                        return;
                    }
                    SkipSession.Token token=session.begin(candidateKey,submitAt);
                    if (token!=null) submit(candidate,candidateBounds,candidateRule,candidateSnapshot,pkg,appVersion,token);
                }
            } catch(RuntimeException staleTree) {
                session.stop();remember(pkg,appVersion,null,null,DiagnosticSample.Reason.INCOMPLETE_SCAN,0,false);
                diagnose(pkg,"界面读取或操作状态已变化，已停止本次自动点击");
            } finally { for (AccessibilityNodeInfo n : nodes) n.recycle(); }
            schedule();
        } finally { root.recycle(); }
    }
    private void submit(AccessibilityNodeInfo node,Rect r,RuleCatalog.Evaluation evaluation,
                        RuleEvaluator.Node snapshot,String pkg,RuleEvaluator.VersionInfo appVersion,SkipSession.Token token) {
        FreshAction fresh=revalidateAction(node,r,evaluation,snapshot,pkg,appVersion,token);
        if(fresh==null)return;
        try {
        String text=fresh.snapshot.text,desc=fresh.snapshot.description;
        final String label=text.isEmpty()?desc:text;
        ActionPolicy.Action action=RuleRuntime.action(fresh.evaluation,fresh.gate,fresh.node.isClickable(),false,token.attempt);
        if(action==ActionPolicy.Action.NODE) {
            if(fresh.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                session.accepted(token);
                if (session.recordOnce(token)) Prefs.record(this,pkg,label);
                session.completed(token);
                remember(pkg,fresh.version,fresh.evaluation,fresh.snapshot,DiagnosticSample.Reason.ACTION_SUBMITTED,1,true);
                reviewAt=SystemClock.elapsedRealtime()+350;
                return;
            }
            // A rejected native action may have changed the foreground or layout. No same-round tap.
            session.cancelled(token);
            remember(pkg,fresh.version,fresh.evaluation,fresh.snapshot,DiagnosticSample.Reason.NODE_ACTION_REJECTED,1,true);
            reviewAt=SystemClock.elapsedRealtime()+350;
            return;
        }
        if(action!=ActionPolicy.Action.GESTURE) {
            session.cancelled(token);
            remember(pkg,fresh.version,fresh.evaluation,fresh.snapshot,fresh.node.isClickable()?DiagnosticSample.Reason.NODE_ACTION_REJECTED:
                    DiagnosticSample.Reason.GESTURE_DISABLED,1,true);
            reviewAt=SystemClock.elapsedRealtime()+350;
            return;
        }
        // Only a version-verified, explicitly enabled compatibility rule may submit coordinates.
        Path path=new Path(); path.moveTo(r.centerX(),r.centerY());
        GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,40)).build();
        final RuleCatalog.Evaluation submittedRule=fresh.evaluation;
        final RuleEvaluator.VersionInfo submittedVersion=fresh.version;
        final RuleEvaluator.Node submittedSnapshot=fresh.snapshot;
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { finishGesture(token,pkg,label,submittedRule,submittedVersion,submittedSnapshot,true); }
            @Override public void onCancelled(GestureDescription g) { finishGesture(token,pkg,label,submittedRule,submittedVersion,submittedSnapshot,false); }
        },handler);
        if (accepted) {
            session.accepted(token);
            remember(pkg,fresh.version,fresh.evaluation,fresh.snapshot,DiagnosticSample.Reason.ACTION_SUBMITTED,1,true);
        } else {
            session.cancelled(token); reviewAt=SystemClock.elapsedRealtime()+350;
            remember(pkg,fresh.version,fresh.evaluation,fresh.snapshot,DiagnosticSample.Reason.NODE_ACTION_REJECTED,1,true);
        }
        } finally {fresh.node.recycle();}
    }
    private static final class FreshAction {
        final AccessibilityNodeInfo node;
        final RuleCatalog.Evaluation evaluation;
        final RuleRuntime.Gate gate;
        final RuleEvaluator.Node snapshot;
        final RuleEvaluator.VersionInfo version;
        FreshAction(AccessibilityNodeInfo node,RuleCatalog.Evaluation evaluation,RuleRuntime.Gate gate,
                    RuleEvaluator.Node snapshot,RuleEvaluator.VersionInfo version) {
            this.node=node;this.evaluation=evaluation;this.gate=gate;this.snapshot=snapshot;this.version=version;
        }
    }
    /** Never submit against the earlier scan: cross-process reads and ACTION_CLICK may change a layout. */
    private FreshAction revalidateAction(AccessibilityNodeInfo original,Rect expectedBounds,RuleCatalog.Evaluation expected,
                                         RuleEvaluator.Node oldSnapshot,String pkg,RuleEvaluator.VersionInfo expectedVersion,SkipSession.Token token) {
        if(!session.current(token)||!unlocked()||!Prefs.enabled(this)||!Prefs.selected(this).contains(pkg))
            return rejectFresh(pkg,expectedVersion,expected,oldSnapshot,DiagnosticSample.Reason.SERVICE_NOT_READY,false);
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return rejectFresh(pkg,expectedVersion,expected,oldSnapshot,DiagnosticSample.Reason.TARGET_CHANGED,false);
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        try {
            Rect screen=screenBounds(),window=new Rect();root.getBoundsInScreen(window);
            boolean sameWindow=pkg.equals(str(root.getPackageName()))&&root.getWindowId()==original.getWindowId();
            if(!sameWindow||window.width()<screen.width()*.9||window.height()<screen.height()*.85)
                return rejectFresh(pkg,expectedVersion,expected,oldSnapshot,DiagnosticSample.Reason.TARGET_CHANGED,false);
            if(!collect(root,nodes,0))return rejectFresh(pkg,expectedVersion,expected,oldSnapshot,DiagnosticSample.Reason.INCOMPLETE_SCAN,false);
            boolean evidence=false,biliSplash=AdRules.biliSplashMarker(root.getViewIdResourceName(),str(root.getClassName()));
            for(AccessibilityNodeInfo node:nodes) {
                if(!node.isVisibleToUser()||!pkg.equals(str(node.getPackageName())))continue;
                evidence|=AdRules.adEvidence(str(node.getText()))||AdRules.adEvidence(str(node.getContentDescription()));
                biliSplash|=AdRules.biliSplashMarker(node.getViewIdResourceName(),str(node.getClassName()));
            }
            RuleEvaluator.VersionInfo freshVersion=readVersion(pkg);
            if(freshVersion.code!=expectedVersion.code||!freshVersion.name.equals(expectedVersion.name))
                return rejectFresh(pkg,freshVersion,expected,oldSnapshot,DiagnosticSample.Reason.TARGET_CHANGED,true);
            CandidateSelection<AccessibilityNodeInfo> candidates=new CandidateSelection<>();
            AccessibilityNodeInfo candidate=null;
            RuleCatalog.Evaluation candidateRule=null;
            for(AccessibilityNodeInfo node:nodes) {
                RuleCatalog.Evaluation rule=evaluateFresh(node,pkg,freshVersion,screen,window,evidence,biliSplash);
                if(rule==null||!rule.matched)continue;
                candidates.offer(node);
                if(candidate==null){candidate=node;candidateRule=rule;}
            }
            if(candidates.ambiguous())return rejectFresh(pkg,freshVersion,expected,oldSnapshot,DiagnosticSample.Reason.MULTIPLE_CANDIDATES,true);
            candidate=candidates.unique();
            if(candidate==null)return rejectFresh(pkg,freshVersion,expected,oldSnapshot,DiagnosticSample.Reason.TARGET_CHANGED,true);
            boolean refreshed=candidate.refresh();
            Rect freshBounds=new Rect();candidate.getBoundsInScreen(freshBounds);
            candidateRule=evaluateFresh(candidate,pkg,freshVersion,screen,window,evidence,biliSplash);
            if(!RuleRuntime.sameCandidate(expected,candidateRule,true,true,original.equals(candidate),
                    sameWindow&&candidate.getWindowId()==original.getWindowId(),expectedBounds.equals(freshBounds),refreshed))
                return rejectFresh(pkg,freshVersion,candidateRule,oldSnapshot,DiagnosticSample.Reason.TARGET_CHANGED,true);
            // Re-read current configuration and monotonic deadline after the fresh scan and refresh.
            OfflineRuleStore store=loadStoreOrStop(pkg,freshVersion);
            if(store==null)return null;
            if(!session.current(token)||!unlocked()||!Prefs.enabled(this)||!Prefs.selected(this).contains(pkg)
                    ||!foregroundStillSame(root,pkg,screen,window))
                return rejectFresh(pkg,freshVersion,candidateRule,snapshot(candidate),DiagnosticSample.Reason.TARGET_CHANGED,true);
            RuleRuntime.Gate gate=RuleRuntime.gate(store,candidateRule,pkg,freshVersion.code,SystemClock.elapsedRealtime(),
                    session.deadline(),token.attempt-1,RuleData.gestureCompatibilityEnabled(this));
            if(!gate.allowed)return rejectFresh(pkg,freshVersion,candidateRule,snapshot(candidate),gate.reason,true);
            return new FreshAction(AccessibilityNodeInfo.obtain(candidate),candidateRule,gate,snapshot(candidate),freshVersion);
        } catch(RuntimeException stale) {
            return rejectFresh(pkg,expectedVersion,expected,oldSnapshot,DiagnosticSample.Reason.INCOMPLETE_SCAN,false);
        } finally {
            for(AccessibilityNodeInfo node:nodes)node.recycle();
            root.recycle();
        }
    }
    private boolean foregroundStillSame(AccessibilityNodeInfo expected,String pkg,Rect screen,Rect window) {
        AccessibilityNodeInfo current=getRootInActiveWindow();
        if(current==null)return false;
        try {
            Rect bounds=new Rect();current.getBoundsInScreen(bounds);
            return current.equals(expected)&&current.getWindowId()==expected.getWindowId()
                    &&pkg.equals(str(current.getPackageName()))&&window.equals(bounds)
                    &&bounds.width()>=screen.width()*.9&&bounds.height()>=screen.height()*.85;
        } finally {current.recycle();}
    }
    private RuleCatalog.Evaluation evaluateFresh(AccessibilityNodeInfo node,String pkg,RuleEvaluator.VersionInfo appVersion,
                                                  Rect screen,Rect window,boolean evidence,boolean biliSplash) {
        if(!node.isVisibleToUser()||!node.isEnabled()||!pkg.equals(str(node.getPackageName())))return null;
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        if(!screen.contains(bounds)||!window.contains(bounds))return null;
        String text=str(node.getText()),desc=str(node.getContentDescription()),id=node.getViewIdResourceName();
        boolean bili=AdRules.biliCandidate(pkg,text,id,biliSplash,bounds.left,bounds.top,bounds.right,bounds.bottom,screen.right,screen.bottom)
                ||AdRules.biliCandidate(pkg,desc,id,biliSplash,bounds.left,bounds.top,bounds.right,bounds.bottom,screen.right,screen.bottom);
        boolean generic=AdRules.candidate(text,id,evidence,bounds.left,bounds.top,bounds.right,bounds.bottom,screen.right,screen.bottom)
                ||AdRules.candidate(desc,id,evidence,bounds.left,bounds.top,bounds.right,bounds.bottom,screen.right,screen.bottom);
        RuleEvaluator.Node snapshot=snapshot(node);
        if(!bili&&!generic&&!RuleCatalog.knownBiliTarget(snapshot))return null;
        Ancestry ancestry=ancestors(node);
        return RuleCatalog.evaluate(appVersion,snapshot,ancestry.nodes,ancestry.complete,evidence,bili,generic);
    }
    private FreshAction rejectFresh(String pkg,RuleEvaluator.VersionInfo appVersion,RuleCatalog.Evaluation evaluation,
                                     RuleEvaluator.Node node,DiagnosticSample.Reason reason,boolean complete) {
        session.stop();
        remember(pkg,appVersion,evaluation,node,reason,reason==DiagnosticSample.Reason.MULTIPLE_CANDIDATES?2:0,complete);
        diagnose(pkg,"提交前复核未通过，已停止本次自动点击");
        return null;
    }
    private void finishGesture(SkipSession.Token token,String pkg,String label,RuleCatalog.Evaluation evaluation,
                               RuleEvaluator.VersionInfo appVersion,RuleEvaluator.Node snapshot,boolean completed) {
        if (!session.current(token)) return;
        if (!connected || !Prefs.enabled(this) || !unlocked()
                || selected==null || !selected.contains(pkg)) { resetSession(); return; }
        // Expiry or a temporarily missing root must not re-arm the same application's window.
        if (!session.active(SystemClock.elapsedRealtime())) { session.cancelled(token); return; }
        OfflineRuleStore store=loadStoreOrStop(pkg,appVersion);
        if(store==null)return;
        RuleRuntime.Gate gate=RuleRuntime.gate(store,evaluation,pkg,appVersion.code,SystemClock.elapsedRealtime(),
                session.deadline(),token.attempt-1,RuleData.gestureCompatibilityEnabled(this));
        if(!gate.allowed||!gate.gestureAllowed) {
            session.stop();remember(pkg,appVersion,evaluation,snapshot,gate.allowed?DiagnosticSample.Reason.GESTURE_DISABLED:gate.reason,1,true);
            return;
        }
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if (root==null) {
            session.cancelled(token);
            reviewAt=SystemClock.elapsedRealtime()+350;
            schedule();
            return;
        }
        try {
            String current=String.valueOf(root.getPackageName());
            if (!pkg.equals(current)) { session.observe(current,SystemClock.elapsedRealtime()); reviewAt=0; queueScan(40); return; }
        } finally { root.recycle(); }
        if (completed) {
            if (session.recordOnce(token)) Prefs.record(this,pkg,label);
            session.completed(token);
        } else session.cancelled(token);
        reviewAt=SystemClock.elapsedRealtime()+350;
        schedule();
    }
    private Rect screenBounds() {
        WindowManager manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(Build.VERSION.SDK_INT>=30)return manager.getMaximumWindowMetrics().getBounds();
        DisplayMetrics metrics=new DisplayMetrics();manager.getDefaultDisplay().getRealMetrics(metrics);
        return new Rect(0,0,metrics.widthPixels,metrics.heightPixels);
    }
    private RuleEvaluator.VersionInfo versionFor(String pkg) {
        if(pkg.equals(versionPackage))return version;
        versionPackage=pkg;
        version=readVersion(pkg);
        return version;
    }
    private RuleEvaluator.VersionInfo readVersion(String pkg) {
        try {
            PackageInfo info=getPackageManager().getPackageInfo(pkg,0);
            return new RuleEvaluator.VersionInfo(Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode,info.versionName);
        } catch(Exception unavailable) {return new RuleEvaluator.VersionInfo(-1,"");}
    }
    private OfflineRuleStore loadStoreOrStop(String pkg,RuleEvaluator.VersionInfo appVersion) {
        try {return RuleData.loadStore(this);}
        catch(IllegalStateException invalid) {
            session.stop();remember(pkg,appVersion,null,null,DiagnosticSample.Reason.SERVICE_NOT_READY,0,true);
            diagnose(pkg,"本地规则无法验证，请在离线工具中恢复内置规则");return null;
        }
    }
    private void rememberReplayFrame(String pkg,RuleEvaluator.VersionInfo version,int width,int height,boolean evidence,boolean biliSplash,boolean complete,List<RuleReplay.Candidate> candidates){
        // Diagnostics must never change matching, budgets or Android actions if their limits are exceeded.
        try{RuleReplay.Frame frame=RuleReplay.capture(pkg,version,width,height,evidence,biliSplash,complete,candidates);
            RuleReplay.encode(frame);RuleData.rememberReplay(pkg,frame,SystemClock.elapsedRealtime());
        }catch(RuntimeException tooLarge){
            try{RuleData.rememberReplay(pkg,RuleReplay.capture(pkg,version,width,height,evidence,biliSplash,false,java.util.Collections.emptyList()),SystemClock.elapsedRealtime());}catch(RuntimeException ignored){/* no sample for unsupported dimensions */}
        }
    }
    private static RuleEvaluator.Node snapshot(AccessibilityNodeInfo node) {
        return new RuleEvaluator.Node(str(node.getPackageName()),node.getViewIdResourceName(),str(node.getClassName()),
                str(node.getText()),str(node.getContentDescription()),node.isVisibleToUser(),node.isEnabled(),node.isClickable());
    }
    private static final class Ancestry {
        final List<RuleEvaluator.Node> nodes;final boolean complete;
        Ancestry(List<RuleEvaluator.Node> nodes,boolean complete){this.nodes=nodes;this.complete=complete;}
    }
    private static Ancestry ancestors(AccessibilityNodeInfo node) {
        List<RuleEvaluator.Node> result=new ArrayList<>();
        AccessibilityNodeInfo parent=null;
        try {
            parent=node.getParent();
            while(parent!=null&&result.size()<RuleSpec.MAX_ANCESTORS) {
                AccessibilityNodeInfo current=parent;parent=null;
                try {result.add(snapshot(current));parent=current.getParent();}
                finally {current.recycle();}
            }
            return new Ancestry(result,parent==null);
        } catch(RuntimeException incomplete) {return new Ancestry(result,false);}
        finally {if(parent!=null)parent.recycle();}
    }
    private void remember(String pkg,RuleEvaluator.VersionInfo appVersion,RuleCatalog.Evaluation evaluation,
                          RuleEvaluator.Node node,DiagnosticSample.Reason reason,int count,boolean complete) {
        DiagnosticSample.Target target=RuleCatalog.BILI_PACKAGE.equals(pkg)?DiagnosticSample.Target.BILIBILI:
                "com.zhihu.android".equals(pkg)?DiagnosticSample.Target.ZHIHU:DiagnosticSample.Target.OTHER_SELECTED;
        DiagnosticSample.NodeType type=DiagnosticSample.NodeType.NONE;
        if(node!=null) {
            if(node.className.equals("android.widget.TextView"))type=DiagnosticSample.NodeType.TEXT_VIEW;
            else if(node.className.equals("android.widget.Button"))type=DiagnosticSample.NodeType.BUTTON;
            else if(node.className.equals("android.widget.FrameLayout"))type=DiagnosticSample.NodeType.FRAME_LAYOUT;
            else if(node.className.equals("android.view.ViewGroup"))type=DiagnosticSample.NodeType.VIEW_GROUP;
            else if(node.className.equals("android.webkit.WebView"))type=DiagnosticSample.NodeType.WEB_VIEW;
            else type=DiagnosticSample.NodeType.OTHER;
        }
        DiagnosticSample.Relation relation=evaluation!=null&&evaluation.knownBiliTarget&&evaluation.sceneMatched?
                DiagnosticSample.Relation.VERIFIED_SPLASH_ANCESTOR:DiagnosticSample.Relation.NONE;
        boolean excluded=reason==DiagnosticSample.Reason.EXCLUDED||reason==DiagnosticSample.Reason.UNSAFE_LABEL;
        RuleData.rememberSample(new DiagnosticSample(reason,DiagnosticSample.RuleId.fromKnownRuleId(evaluation==null?null:evaluation.ruleId),
                target,appVersion.code,type,relation,Math.min(DiagnosticSample.MAX_CANDIDATES,count),complete,
                node!=null&&node.clickable,evaluation!=null&&evaluation.sceneMatched,evaluation!=null&&evaluation.targetMatched,excluded));
    }
    private void diagnose(String pkg,String reason) {
        if(SystemClock.elapsedRealtime()-lastDiagnostic<2000)return;
        lastDiagnostic=SystemClock.elapsedRealtime();
        Prefs.get(this).edit().putString("diagnostic",pkg+"："+reason).apply();
    }
    private boolean collect(AccessibilityNodeInfo parent, List<AccessibilityNodeInfo> nodes, int depth) {
        int count=parent.getChildCount();
        if (count==0) return true;
        if (depth>=32) return false;
        for (int i = 0; i < count; i++) {
            if (nodes.size()>=600) return false;
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child==null) return false;
            nodes.add(child);
            if (!collect(child, nodes, depth + 1)) return false;
        }
        return true;
    }
    private static String str(CharSequence s) { return s == null ? "" : s.toString(); }
    @Override public void onInterrupt() { resetSession(); }
    @Override public boolean onUnbind(Intent intent) {
        connected = false;
        resetSession();
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        connected = false;
        resetSession();
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered=false; }
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
}
