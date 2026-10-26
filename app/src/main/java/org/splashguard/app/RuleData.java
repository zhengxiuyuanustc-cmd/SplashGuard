package org.splashguard.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Local persistence boundary. Invalid saved restrictions stop processing until explicit recovery. */
final class RuleData {
    private static final String CURRENT="rules_current", PREVIOUS="rules_previous", UNDO="rules_undo", COMPAT="gesture_compatibility";
    private static volatile DiagnosticSample sample;
    private static RuleReplay.Frame replay;
    private static final ReplayCaptureSession captureSession=new ReplayCaptureSession();
    private RuleData() {}

    static synchronized OfflineRuleStore loadStore(Context context) {
        SharedPreferences prefs=Prefs.get(context);
        try {
            String current=prefs.getString(CURRENT,null), previous=prefs.getString(PREVIOUS,null);
            boolean undo=prefs.getBoolean(UNDO,false);
            OfflineRuleStore store=new OfflineRuleStore(RuleCatalog.builtInLimits());
            store.restoreValidatedState(current==null?null:RuleJsonCodec.decodePack(current),
                    previous==null?null:RuleJsonCodec.decodePack(previous),undo);
            return store;
        } catch(RuntimeException invalid) { throw new IllegalStateException("本地规则状态无法验证，请在离线工具中恢复内置规则。",invalid); }
    }
    static synchronized void apply(Context context,OfflineRuleStore.Pack pack) {
        OfflineRuleStore store=loadStore(context);store.apply(pack);save(context,store);
    }
    static synchronized boolean rollback(Context context) {
        OfflineRuleStore store=loadStore(context);
        if(!store.rollback())return false;
        save(context,store);return true;
    }
    static synchronized void restoreBuiltIns(Context context) {
        // This explicit recovery works even if the previous data cannot be decoded.
        boolean saved=Prefs.get(context).edit().remove(CURRENT).remove(PREVIOUS).remove(UNDO).commit();
        if(!saved)throw new IllegalStateException("规则保存失败，请重试并重新检查状态。");
    }
    private static void save(Context context,OfflineRuleStore store) {
        SharedPreferences.Editor edit=Prefs.get(context).edit();
        putPack(edit,CURRENT,store.currentPack());putPack(edit,PREVIOUS,store.previousPack());
        if(!edit.putBoolean(UNDO,store.canRollback()).commit())throw new IllegalStateException("规则保存失败，请重试并重新检查状态。");
    }
    private static void putPack(SharedPreferences.Editor edit,String key,OfflineRuleStore.Pack pack) {
        if(pack==null)edit.remove(key);else edit.putString(key,RuleJsonCodec.encodePack(pack));
    }
    static boolean gestureCompatibilityEnabled(Context context) {
        try{return Prefs.get(context).getBoolean(COMPAT,false);}catch(RuntimeException invalid){return false;}
    }
    static void setGestureCompatibility(Context context,boolean enabled) {
        if(!Prefs.get(context).edit().putBoolean(COMPAT,enabled).commit())throw new IllegalStateException("兼容设置保存失败，请重新检查。");
    }
    static boolean affectsRuntime(String key) {
        return key==null||CURRENT.equals(key)||PREVIOUS.equals(key)||UNDO.equals(key)||COMPAT.equals(key);
    }
    static void rememberSample(DiagnosticSample value){sample=value;}
    static DiagnosticSample lastSample(){return sample;}
    static synchronized void clearSample(){sample=null;replay=null;captureSession.clear();}
    static synchronized void requestReplayCapture(){replay=null;captureSession.arm(android.os.SystemClock.elapsedRealtime());}
    static synchronized void observeReplayForeground(String pkg,long now){captureSession.observeForeground(pkg,now);}
    static synchronized void replayScreenOff(long now){captureSession.screenOff(now);}
    static synchronized boolean wantsReplayCapture(String pkg,long now){return captureSession.accepts(pkg,now);}
    static synchronized void rememberReplay(String pkg,RuleReplay.Frame value,long now){
        if(!captureSession.accepts(pkg,now))return;
        if(replay==null||!value.scanComplete||!value.candidates.isEmpty())replay=value;
    }
    static synchronized RuleReplay.Frame lastReplay(){captureSession.state(android.os.SystemClock.elapsedRealtime());return replay;}
    static synchronized void clearReplay(){replay=null;captureSession.clear();}
    static synchronized String replayStatus(){
        ReplayCaptureSession.State state=captureSession.state(android.os.SystemClock.elapsedRealtime());
        if(state==ReplayCaptureSession.State.WAITING)return "已准备：最多等待2分钟，在下一所选应用的短暂开屏时段记录。";
        if(state==ReplayCaptureSession.State.CAPTURING)return "正在记录该应用本次开屏的匹配线索；不会延长自动点击时段。";
        return replay==null?"暂无规则夹具；未主动准备时不会生成。":"已取得进程内规则夹具，可本人查看后决定是否导出。";
    }
}
