package org.splashguard.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
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
    private final Runnable scanTask = () -> { scanQueued=false; scan(); };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) resetSession();
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
        if (!"apps".equals(key) && !"enabled".equals(key)) return;
        selected = Prefs.selected(this);
        resetSession();
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
        if (!unlocked()) { if (!session.packageName().isEmpty()) resetSession(); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { schedule(); return; }
        try {
            String pkg = String.valueOf(root.getPackageName());
            long now=SystemClock.elapsedRealtime();
            if (session.observe(pkg, now)) reviewAt=0;
            if (selected == null || !selected.contains(pkg) || pkg.equals(getPackageName())) return;
            if (!session.active(now)) { if (!session.done()) diagnose(pkg,"开屏处理时段已结束"); return; }
            // Observe package changes before checking an in-flight gesture, so old callbacks expire.
            if (session.busy() || now < reviewAt) { schedule(); return; }
            Rect window = new Rect(); root.getBoundsInScreen(window);
            Rect screen=screenBounds();
            int width=screen.right,height=screen.bottom;
            // Only act on a nearly full-screen foreground app, never on dialogs or keyboards.
            if (window.width() < screen.width() * .9 || window.height() < screen.height() * .85) { schedule(); return; }
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            try {
                collect(root, nodes, 0);
                boolean evidence = false;
                boolean biliSplash = false;
                AccessibilityNodeInfo candidate=null;
                Rect candidateBounds=null;
                String candidateKey=null;
                boolean candidateBili=false;
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
                    boolean hit = biliHit || AdRules.candidate(text, n.getViewIdResourceName(), evidence, r.left, r.top, r.right, r.bottom, width, height)
                            || AdRules.candidate(desc, n.getViewIdResourceName(), evidence, r.left, r.top, r.right, r.bottom, width, height);
                    if (!hit) continue;
                    String key=str(n.getViewIdResourceName())+"|"+str(n.getClassName())+"|"+r.toShortString();
                    if (session.buttonKey()!=null && !session.buttonKey().equals(key)) continue;
                    candidate=n; candidateBounds=r; candidateKey=key; candidateBili=biliHit; break;
                }
                session.review(candidate!=null);
                if (session.done()) return;
                if (candidate==null) diagnose(pkg,"已检查，未找到符合安全规则的开屏跳过按钮");
                else {
                    SkipSession.Token token=session.begin(candidateKey,SystemClock.elapsedRealtime());
                    if (token!=null) submit(candidate,candidateBounds,candidateBili,pkg,width,height,token);
                }
            } finally { for (AccessibilityNodeInfo n : nodes) n.recycle(); }
            schedule();
        } finally { root.recycle(); }
    }
    private void submit(AccessibilityNodeInfo node,Rect r,boolean bili,String pkg,int width,int height,SkipSession.Token token) {
        String text=str(node.getText()),desc=str(node.getContentDescription());
        final String label=text.isEmpty()?desc:text;
        // On a retained button, retry with a fresh exact tap instead of repeating an ineffective ACTION_CLICK.
        if (token.attempt==1 && ((node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                || (bili && clickSmallParent(node,pkg,width,height)))) {
            session.accepted(token);
            if (session.recordOnce(token)) Prefs.record(this,pkg,label);
            session.completed(token);
            reviewAt=SystemClock.elapsedRealtime()+350;
            return;
        }
        Path path=new Path(); path.moveTo(r.centerX(),r.centerY());
        GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,40)).build();
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { finishGesture(token,pkg,label,true); }
            @Override public void onCancelled(GestureDescription g) { finishGesture(token,pkg,label,false); }
        },handler);
        if (accepted) session.accepted(token);
        else { session.cancelled(token); reviewAt=SystemClock.elapsedRealtime()+350; }
    }
    private void finishGesture(SkipSession.Token token,String pkg,String label,boolean completed) {
        if (!session.current(token)) return;
        if (!connected || !Prefs.enabled(this) || !unlocked()
                || selected==null || !selected.contains(pkg)) { resetSession(); return; }
        // Expiry or a temporarily missing root must not re-arm the same application's window.
        if (!session.active(SystemClock.elapsedRealtime())) { session.cancelled(token); return; }
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
    private boolean clickSmallParent(AccessibilityNodeInfo node,String pkg,int width,int height) {
        AccessibilityNodeInfo parent=node.getParent();
        for (int depth=0;parent!=null && depth<2;depth++) {
            AccessibilityNodeInfo next=null;
            try {
                Rect r=new Rect();parent.getBoundsInScreen(r);
                if (!pkg.equals(String.valueOf(parent.getPackageName())) || !AdRules.safeButtonBounds(r.left,r.top,r.right,r.bottom,width,height)) return false;
                if (parent.isVisibleToUser() && parent.isEnabled() && parent.isClickable() && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                next=parent.getParent();
            } finally { parent.recycle(); }
            parent=next;
        }
        if(parent!=null)parent.recycle();
        return false;
    }
    private void diagnose(String pkg,String reason) {
        if(SystemClock.elapsedRealtime()-lastDiagnostic<2000)return;
        lastDiagnostic=SystemClock.elapsedRealtime();
        Prefs.get(this).edit().putString("diagnostic",pkg+"："+reason).apply();
    }
    private void collect(AccessibilityNodeInfo parent, List<AccessibilityNodeInfo> nodes, int depth) {
        if (depth > 32 || nodes.size() >= 600) return;
        for (int i = 0; i < parent.getChildCount() && nodes.size() < 600; i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child != null) { nodes.add(child); collect(child, nodes, depth + 1); }
        }
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
