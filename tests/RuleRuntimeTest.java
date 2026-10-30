import org.splashguard.app.*;
import java.util.Arrays;
import java.util.Collections;

public final class RuleRuntimeTest {
    private static int passed;
    private static final String PKG=RuleCatalog.BILI_PACKAGE;
    private static final RuleEvaluator.Node SKIP=new RuleEvaluator.Node(PKG,PKG+":id/count_down","android.widget.TextView","跳过 5","",true,true,true);
    private static final RuleEvaluator.Node SPLASH=new RuleEvaluator.Node(PKG,PKG+":id/splash_container","android.widget.FrameLayout","","",true,true,false);
    private static final RuleCatalog.Evaluation VERIFIED=bili("9.15.0"),UNVERIFIED=bili("9.16.0");
    private static RuleCatalog.Evaluation bili(String name) {
        return RuleCatalog.evaluate(new RuleEvaluator.VersionInfo(-1,name),SKIP,Arrays.asList(SPLASH),true,true,true,true);
    }
    private static RuleCatalog.Evaluation generic(String pkg) {
        return RuleCatalog.evaluate(new RuleEvaluator.VersionInfo(-1,""),new RuleEvaluator.Node(pkg,"x","android.widget.Button","跳过广告","",true,true,true),
                Collections.emptyList(),true,true,false,true);
    }
    private static void check(String name,boolean result){if(!result)throw new AssertionError(name);passed++;}
    private static OfflineRuleStore baseline(){return new OfflineRuleStore(RuleCatalog.builtInLimits());}
    private static OfflineRuleStore restricted(String id,long min,long max,int window,int actions,boolean enabled,boolean gesture) {
        OfflineRuleStore store=baseline();
        store.apply(new OfflineRuleStore.Pack(1,"local.test",1,Arrays.asList(new OfflineRuleStore.RuleLimit(id,
                RuleCatalog.BILI.equals(id)?PKG:OfflineRuleStore.SELECTED_APPS,min,max,window,actions,enabled,gesture))));
        return store;
    }
    private static RuleRuntime.Gate gate(OfflineRuleStore store,RuleCatalog.Evaluation e,String pkg,long code,long now,int attempts,boolean compat) {
        return RuleRuntime.gate(store,e,pkg,code,now,13000,attempts,compat);
    }
    private static ActionPolicy.Action action(RuleCatalog.Evaluation e,RuleRuntime.Gate g,boolean clickable,boolean rejected,int attempt) {
        return RuleRuntime.action(e,g,clickable,rejected,attempt);
    }
    public static void main(String[] args) {
        OfflineRuleStore store=baseline();
        RuleRuntime.Gate safe=gate(store,VERIFIED,PKG,-1,1100,0,false);
        check("reviewed version defaults to node-only",safe.allowed&&!safe.gestureAllowed);
        check("first safe action uses native node",action(VERIFIED,safe,true,false,1)==ActionPolicy.Action.NODE);
        check("second safe action stays native",action(VERIFIED,safe,true,false,2)==ActionPolicy.Action.NODE);
        check("native rejection has no implicit coordinates",action(VERIFIED,safe,true,true,1)==ActionPolicy.Action.NONE);
        check("nonclickable safe candidate never falls back",action(VERIFIED,safe,false,false,1)==ActionPolicy.Action.NONE);
        RuleRuntime.Gate unknownCode=gate(store,VERIFIED,PKG,-1,1100,0,true);
        check("known version name with unknown code remains node-only",unknownCode.allowed&&!unknownCode.gestureAllowed);
        RuleRuntime.Gate compat=gate(store,VERIFIED,PKG,100,1100,0,true);
        check("a nonnegative code and old opt-in cannot authorize gestures",compat.allowed&&!compat.gestureAllowed);
        check("compatibility first tries native clickable node",action(VERIFIED,compat,true,false,1)==ActionPolicy.Action.NODE);
        check("legacy native rejection cannot dispatch coordinates",action(VERIFIED,compat,true,true,1)==ActionPolicy.Action.NONE);
        check("legacy second round remains native",action(VERIFIED,compat,true,false,2)==ActionPolicy.Action.NODE);
        RuleRuntime.Gate unverified=gate(store,UNVERIFIED,PKG,100,1100,0,true);
        check("newer version cannot use opted-in gesture",unverified.allowed&&!unverified.gestureAllowed);
        check("newer version retry is still native",action(UNVERIFIED,unverified,true,false,2)==ActionPolicy.Action.NODE);
        RuleCatalog.Evaluation generic=generic("com.zhihu.android");
        RuleRuntime.Gate genericGate=gate(store,generic,"com.zhihu.android",-1,1100,0,true);
        check("unknown generic version retains native clue",genericGate.allowed&&!genericGate.gestureAllowed);
        check("generic second round stays native",action(generic,genericGate,true,false,2)==ActionPolicy.Action.NODE);
        check("generic rejection cannot gesture",action(generic,genericGate,true,true,1)==ActionPolicy.Action.NONE);
        check("generic full rejection cannot gesture",action(generic,genericGate,false,true,2)==ActionPolicy.Action.NONE);
        OfflineRuleStore disabled=restricted(RuleCatalog.BILI,0,Long.MAX_VALUE,12000,2,false,true);
        check("disabled Bili cannot match specialized action",!gate(disabled,VERIFIED,PKG,100,1100,0,true).allowed);
        check("disabled Bili cannot bypass with generic label",!gate(disabled,generic(PKG),PKG,100,1100,0,true).allowed);
        check("disabled Bili does not disable other selected apps",gate(disabled,generic,"com.zhihu.android",100,1100,0,false).allowed);
        OfflineRuleStore shortWindow=restricted(RuleCatalog.BILI,0,Long.MAX_VALUE,1000,1,true,true);
        check("tightened window uses original start",gate(shortWindow,VERIFIED,PKG,100,1999,0,true).allowed);
        check("tightened deadline is exclusive",gate(shortWindow,VERIFIED,PKG,100,2000,0,true).reason==DiagnosticSample.Reason.WINDOW_EXPIRED);
        check("tightened action limit rejects round two",!gate(shortWindow,VERIFIED,PKG,100,1100,1,true).allowed);
        check("action helper also respects a one-round limit",action(VERIFIED,gate(shortWindow,VERIFIED,PKG,100,1100,0,true),true,false,2)==ActionPolicy.Action.NONE);
        check("tightened Bili applies to generic same-package candidate",!gate(shortWindow,generic(PKG),PKG,100,2000,0,false).allowed);
        check("original hard deadline rejects exactly at edge",gate(store,VERIFIED,PKG,100,13000,0,true).reason==DiagnosticSample.Reason.WINDOW_EXPIRED);
        check("clock before the observed start cannot permit an action",!gate(store,VERIFIED,PKG,100,999,0,true).allowed);
        check("hard two attempts reject third round",!gate(store,VERIFIED,PKG,100,1100,2,true).allowed);
        check("negative consumed attempts rejected",!gate(store,VERIFIED,PKG,100,1100,-1,true).allowed);
        OfflineRuleStore range=restricted(RuleCatalog.BILI,10,20,12000,2,true,true);
        check("unknown code cannot satisfy narrowed range",gate(range,VERIFIED,PKG,-1,1100,0,true).reason==DiagnosticSample.Reason.VERSION_UNKNOWN);
        check("outside version cannot satisfy range",gate(range,VERIFIED,PKG,21,1100,0,true).reason==DiagnosticSample.Reason.VERSION_OUTSIDE);
        check("inclusive lower version boundary",gate(range,VERIFIED,PKG,10,1100,0,true).allowed);
        check("inclusive upper version boundary",gate(range,VERIFIED,PKG,20,1100,0,true).allowed);
        check("same-package generic cannot bypass narrowed version",!gate(range,generic(PKG),PKG,-1,1100,0,true).allowed);
        OfflineRuleStore noGesture=restricted(RuleCatalog.BILI,0,Long.MAX_VALUE,12000,2,true,false);
        check("offline gesture restriction wins over UI",!gate(noGesture,VERIFIED,PKG,100,1100,0,true).gestureAllowed);
        check("missing validated store fails closed",!gate(null,VERIFIED,PKG,100,1100,0,true).allowed);
        check("wrong scoped package fails closed",!gate(store,VERIFIED,"com.other",100,1100,0,true).allowed);
        check("null rule evaluation fails closed",!gate(store,null,PKG,100,1100,0,true).allowed);
        check("hard invalid attempt not submitted",action(VERIFIED,compat,true,false,3)==ActionPolicy.Action.NONE);
        check("fresh same candidate may submit",RuleRuntime.sameCandidate(VERIFIED,VERIFIED,true,true,true,true,true,true));
        check("fresh incomplete tree cannot submit",!RuleRuntime.sameCandidate(VERIFIED,VERIFIED,false,true,true,true,true,true));
        check("fresh second candidate cannot submit",!RuleRuntime.sameCandidate(VERIFIED,VERIFIED,true,false,true,true,true,true));
        check("lookalike node identity cannot submit",!RuleRuntime.sameCandidate(VERIFIED,VERIFIED,true,true,false,true,true,true));
        check("changed foreground window cannot submit",!RuleRuntime.sameCandidate(VERIFIED,VERIFIED,true,true,true,false,true,true));
        check("moved same-ID candidate cannot submit",!RuleRuntime.sameCandidate(VERIFIED,VERIFIED,true,true,true,true,false,true));
        check("stale node refresh cannot submit",!RuleRuntime.sameCandidate(VERIFIED,VERIFIED,true,true,true,true,true,false));
        check("lost reviewed context cannot submit",!RuleRuntime.sameCandidate(VERIFIED,UNVERIFIED,true,true,true,true,true,true));
        check("same label with changed rule group cannot submit",!RuleRuntime.sameCandidate(VERIFIED,generic(PKG),true,true,true,true,true,true));
        RuleCatalog.Evaluation noContext=RuleCatalog.evaluate(new RuleEvaluator.VersionInfo(100,"9.15.0"),SKIP,
                Collections.emptyList(),true,true,true,true);
        check("fresh context-vetoed target cannot submit",!RuleRuntime.sameCandidate(VERIFIED,noContext,true,true,true,true,true,true));
        SkipSession session=new SkipSession();session.observe(PKG,1000);
        RuleRuntime.Gate before=RuleRuntime.gate(shortWindow,VERIFIED,PKG,100,1100,session.deadline(),session.attempts(),true);
        SkipSession.Token token=session.begin("same",1100);
        check("gate can reserve a permitted first round",before.allowed&&token!=null);
        check("reserved token uses its previous consumed count",RuleRuntime.gate(shortWindow,VERIFIED,PKG,100,1200,session.deadline(),token.attempt-1,true).allowed);
        session.stop();long deadline=session.deadline();session.observe(PKG,1500);
        check("stopping a gate never re-arms same package",!session.active(1500)&&session.deadline()==deadline&&session.attempts()==1);
        check("arbitrary large version code does not become evidence",!gate(store,VERIFIED,PKG,Long.MAX_VALUE,1100,0,true).gestureAllowed);
        check("legacy opt-in cannot act on nonclickable node",action(VERIFIED,compat,false,false,1)==ActionPolicy.Action.NONE);
        System.out.println("RuleRuntimeTest: "+passed+" checks passed");
    }
}
