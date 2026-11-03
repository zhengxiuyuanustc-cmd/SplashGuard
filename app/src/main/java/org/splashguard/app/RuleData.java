package org.splashguard.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Local persistence boundary. Invalid saved restrictions stop processing until explicit recovery. */
final class RuleData {
    private static final String CURRENT="rules_current", PREVIOUS="rules_previous", UNDO="rules_undo", COMPAT="gesture_compatibility";
    private static final DiagnosticHistory diagnosticHistory=new DiagnosticHistory();
    private static RuleReplay.Frame replay;
    private static final ReplayCaptureSession captureSession=new ReplayCaptureSession();
    private RuleData() {}

    private static final String STATE="rules_state_v2", AUTH_BLOCKED="rules_auth_blocked", QUARANTINE="rules_auth_quarantine";
    private static RuleStateStorage persistence;
    private static synchronized RuleStateStorage storage(Context context){
        if(persistence==null){final Context app=context.getApplicationContext();
            persistence=new RuleStateStorage(new RuleStateStorage.Backend(){
                @Override public RuleStateStorage.Record read(){
                    SharedPreferences prefs=Prefs.get(app);
                    try{return new RuleStateStorage.Record(prefs.getString(STATE,null),prefs.getString(CURRENT,null),
                            prefs.getString(PREVIOUS,null),prefs.getBoolean(UNDO,false),prefs.getBoolean(AUTH_BLOCKED,false),prefs.getString(QUARANTINE,null));}
                    catch(RuntimeException invalid){return new RuleStateStorage.Record("!invalid-local-state",null,null,false,true,"local-state-invalid");}
                }
                @Override public boolean commit(String state,boolean blocked,String quarantine){
                    SharedPreferences.Editor edit=Prefs.get(app).edit().putString(STATE,state).putBoolean(AUTH_BLOCKED,blocked)
                            .remove(CURRENT).remove(PREVIOUS).remove(UNDO);
                    if(quarantine==null)edit.remove(QUARANTINE);else edit.putString(QUARANTINE,quarantine);
                    return edit.commit();
                }
            });
        }
        return persistence;
    }
    static synchronized OfflineRuleStore loadStore(Context context){return storage(context).load().store();}
    static synchronized String sourceDescription(Context context){
        RuleStateStorage local=storage(context);
        return local.load().currentSourceDescription()+(local.authBlocked()?"\n认证导入已暂停：历史状态曾损坏，恢复内置不会清除该保护。":"");
    }
    static synchronized RuleUpdateState.Plan previewSigned(Context context,SignedRulePack.VerifiedPack verified){return storage(context).previewSigned(verified);}
    static synchronized boolean applySigned(Context context,String originalEnvelope){
        // Never trust a preview's parsed Pack. Re-authenticate the exact original bytes, then read current state.
        return storage(context).applySigned(RuleTrust.verify(originalEnvelope));
    }
    static synchronized void apply(Context context,OfflineRuleStore.Pack pack){storage(context).applyLocal(pack);}
    static synchronized boolean rollback(Context context){return storage(context).rollback();}
    static synchronized void restoreBuiltIns(Context context){storage(context).restoreBuiltIns();}
    static boolean gestureCompatibilityEnabled(Context context) {
        // Legacy opt-in is never sufficient for a future coordinate implementation.
        return false;
    }
    static void setGestureCompatibility(Context context,boolean enabled) {
        if(enabled)throw new IllegalStateException("触点兼容暂不可用，请使用按钮动作。若将来恢复，需重新确认。");
        if(!Prefs.get(context).edit().putBoolean(COMPAT,enabled).commit())throw new IllegalStateException("兼容设置保存失败，请重新检查。");
    }
    static boolean affectsRuntime(String key) {
        return key==null||STATE.equals(key)||AUTH_BLOCKED.equals(key)||QUARANTINE.equals(key)||CURRENT.equals(key)||PREVIOUS.equals(key)||UNDO.equals(key)||COMPAT.equals(key);
    }
    static void beginDiagnosticWindow(){diagnosticHistory.beginWindow(android.os.SystemClock.elapsedRealtime());}
    static long diagnosticAgeMillis(){return diagnosticHistory.age(android.os.SystemClock.elapsedRealtime());}
    static void rememberSample(DiagnosticSample value){diagnosticHistory.remember(value);}
    static DiagnosticSample lastSample(){return diagnosticHistory.last();}
    static DiagnosticSample usefulSample(){return diagnosticHistory.useful();}
    static synchronized void clearSample(){diagnosticHistory.clear();replay=null;captureSession.clear();}
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
        if(state==ReplayCaptureSession.State.WAITING)return "已准备：最多等待10分钟，在下一所选应用的短暂开屏时段记录。";
        if(state==ReplayCaptureSession.State.CAPTURING)return "正在记录该应用本次开屏的匹配线索；不会延长自动点击时段。";
        return replay==null?"暂无规则夹具；未主动准备时不会生成。":"已取得进程内规则夹具，可本人查看后决定是否导出。";
    }
}
