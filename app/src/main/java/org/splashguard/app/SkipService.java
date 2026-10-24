package org.splashguard.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
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
    private String activePackage = "";
    private long deadline;
    private long lastAttempt;
    private boolean done;
    private boolean busy;
    private Set<String> selected;
    private long lastDiagnostic;
    private boolean scanQueued;
    private final Runnable scanTask = () -> { scanQueued=false; scan(); };

    @Override public void onServiceConnected() {
        selected = Prefs.selected(this);
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        connected = true;
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (!"apps".equals(key) && !"enabled".equals(key)) return;
        selected = Prefs.selected(this);
        handler.removeCallbacks(scanTask);
        scanQueued=false;
        activePackage = ""; deadline = 0; done = false;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!Prefs.enabled(this)) return;
        // Do not postpone an already queued scan when animations keep emitting events.
        if (!scanQueued) { scanQueued=true; handler.postDelayed(scanTask, 40); }
    }
    private void schedule() {
        if (!scanQueued && !done && SystemClock.elapsedRealtime() < deadline && Prefs.enabled(this)) { scanQueued=true; handler.postDelayed(scanTask, 180); }
    }
    private void scan() {
        if (!Prefs.enabled(this) || busy) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { schedule(); return; }
        try {
            String pkg = String.valueOf(root.getPackageName());
            if (!pkg.equals(activePackage)) {
                activePackage = pkg;
                deadline = SystemClock.elapsedRealtime() + 12000;
                done = false;
            }
            if (selected == null || !selected.contains(pkg) || pkg.equals(getPackageName()) || done) return;
            if (SystemClock.elapsedRealtime() > deadline) { diagnose(pkg,"开屏处理时段已结束"); return; }
            Rect window = new Rect(); root.getBoundsInScreen(window);
            Rect screen=screenBounds();
            int width=screen.right,height=screen.bottom;
            // Only act on a nearly full-screen foreground app, never on dialogs or keyboards.
            if (window.width() < screen.width() * .9 || window.height() < screen.height() * .85) return;
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            try {
                collect(root, nodes, 0);
                boolean evidence = false;
                boolean biliSplash = false;
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
                    if (!hit || SystemClock.elapsedRealtime() - lastAttempt < 700) continue;
                    lastAttempt = SystemClock.elapsedRealtime();
                    if (n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        done = true; Prefs.record(this, pkg, text.isEmpty() ? desc : text); break;
                    }
                    if (biliHit && clickSmallParent(n,pkg,width,height)) {
                        done = true; Prefs.record(this,pkg,text.isEmpty()?desc:text); break;
                    }
                    // Gesture only on the exact identified button, not on its potentially large parent.
                    Path path = new Path(); path.moveTo(r.centerX(), r.centerY());
                    GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, 40)).build();
                    final String label = text.isEmpty() ? desc : text;
                    busy = true;
                    boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                        @Override public void onCompleted(GestureDescription g) { busy = false; done = true; Prefs.record(SkipService.this, pkg, label); }
                        @Override public void onCancelled(GestureDescription g) { busy = false; schedule(); }
                    }, handler);
                    if (!accepted) { busy = false; }
                    break;
                }
                if (!done && !busy) diagnose(pkg,"已检查，未找到符合安全规则的开屏跳过按钮");
            } finally { for (AccessibilityNodeInfo n : nodes) n.recycle(); }
            schedule();
        } finally { root.recycle(); }
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
    @Override public void onInterrupt() { handler.removeCallbacks(scanTask); scanQueued=false; }
    @Override public boolean onUnbind(Intent intent) {
        connected = false;
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        connected = false;
        handler.removeCallbacksAndMessages(null);
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
}
