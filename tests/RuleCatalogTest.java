import org.splashguard.app.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RuleCatalogTest {
    private static int passed;
    private static final String PKG=RuleCatalog.BILI_PACKAGE;
    private static final RuleEvaluator.VersionInfo VERIFIED=new RuleEvaluator.VersionInfo(-1,"9.15.0");
    private static final RuleEvaluator.Node SKIP=node(PKG,PKG+":id/count_down","android.widget.TextView","跳过 5","");
    private static final RuleEvaluator.Node SPLASH=node(PKG,PKG+":id/splash_container","android.widget.FrameLayout","","");
    private static final List<RuleEvaluator.Node> PARENTS=Arrays.asList(SPLASH);
    private static RuleEvaluator.Node node(String pkg,String id,String cls,String text,String desc) {
        return new RuleEvaluator.Node(pkg,id,cls,text,desc,true,true,true);
    }
    private static void check(String name,boolean result){if(!result)throw new AssertionError(name);passed++;}
    private static RuleCatalog.Evaluation evaluate(RuleEvaluator.VersionInfo v,RuleEvaluator.Node node,
                                                  List<RuleEvaluator.Node> parents,boolean complete,boolean bili,boolean generic) {
        return RuleCatalog.evaluate(v,node,parents,complete,true,bili,generic);
    }
    public static void main(String[] args) {
        RuleCatalog.Evaluation known=evaluate(VERIFIED,SKIP,PARENTS,true,true,true);
        check("named-version match does not authorize coordinates",known.matched&&known.result.versionVerified&&!known.result.gestureAllowed);
        check("known Bili selector is not generic",known.ruleId.equals(RuleCatalog.BILI)&&known.knownBiliTarget);
        check("reviewed actual ancestor is recorded",known.sceneMatched&&known.targetMatched);
        RuleCatalog.Evaluation newer=evaluate(new RuleEvaluator.VersionInfo(100,"9.16.0"),SKIP,PARENTS,true,true,true);
        check("newer version retains same-context native action",newer.matched&&!newer.result.versionVerified&&!newer.result.gestureAllowed);
        check("unverified match has honest reason",newer.reason==RuleEvaluator.Reason.MATCH_UNVERIFIED);
        RuleCatalog.Evaluation unknown=evaluate(new RuleEvaluator.VersionInfo(-1,""),SKIP,PARENTS,true,true,true);
        check("PackageManager failure retains node-only compatibility",unknown.matched&&!unknown.result.gestureAllowed);
        check("no actual ancestor cannot reuse marker elsewhere",!evaluate(VERIFIED,SKIP,Collections.emptyList(),true,true,true).matched);
        check("context veto never routes generic",evaluate(VERIFIED,SKIP,Collections.emptyList(),true,true,true).ruleId.equals(RuleCatalog.BILI));
        check("incomplete ancestry cannot act",evaluate(VERIFIED,SKIP,PARENTS,false,true,true).reason==RuleEvaluator.Reason.ANCESTORS_INCOMPLETE);
        RuleEvaluator.Node web=node(PKG,PKG+":id/creative","android.webkit.WebView","","");
        RuleCatalog.Evaluation fake=evaluate(VERIFIED,SKIP,Arrays.asList(web,SPLASH),true,true,true);
        check("same text and ID under creative is excluded",!fake.matched&&fake.reason==RuleEvaluator.Reason.EXCLUDED);
        check("excluded target has no action result",fake.result==null&&!fake.sceneMatched);
        RuleEvaluator.Node dangerous=node(PKG,SKIP.viewId,SKIP.className,SKIP.text,"立即安装");
        check("dangerous description vetoes native and generic",evaluate(VERIFIED,dangerous,PARENTS,true,true,true).reason==RuleEvaluator.Reason.UNSAFE_LABEL);
        check("legacy location gate remains mandatory",!evaluate(VERIFIED,SKIP,PARENTS,true,false,true).matched);
        check("legacy text gate remains mandatory",!evaluate(VERIFIED,node(PKG,SKIP.viewId,SKIP.className,"跳过课程",""),PARENTS,true,false,true).matched);
        check("wrong class not accepted as Bili target",!evaluate(VERIFIED,node(PKG,SKIP.viewId,"android.webkit.WebView",SKIP.text,""),PARENTS,true,true,true).matched);
        RuleEvaluator.Node title=node(PKG,PKG+":id/countdown_title",SKIP.className,"跳过","");
        check("reviewed separate countdown title shares behavior group",evaluate(VERIFIED,title,PARENTS,true,true,false).matched);
        RuleEvaluator.Node generic=node("com.zhihu.android","com.zhihu.android:id/tt_splash_skip_btn","android.widget.TextView","跳过 5","");
        RuleCatalog.Evaluation genericHit=evaluate(VERIFIED,generic,Collections.emptyList(),true,false,true);
        check("generic clues remain node-only",genericHit.matched&&!genericHit.result.versionVerified&&!genericHit.result.gestureAllowed);
        check("generic clue requires legacy gate",!evaluate(VERIFIED,generic,Collections.emptyList(),true,false,false).matched);
        check("null target is no match",!evaluate(VERIFIED,null,Collections.emptyList(),true,false,true).matched);
        List<OfflineRuleStore.RuleLimit> limits=RuleCatalog.builtInLimits();
        check("only two reviewed built-in groups",limits.size()==2);
        OfflineRuleStore store=new OfflineRuleStore(limits);
        check("generic scope requires selected app",store.effective(RuleCatalog.GENERIC).packageName.equals(OfflineRuleStore.SELECTED_APPS));
        check("generic cannot gain coordinate capability",!store.effective(RuleCatalog.GENERIC).allowGesture);
        check("Bili capability is an upper bound only",store.effective(RuleCatalog.BILI).allowGesture);
        check("hard action limit retained",store.effective(RuleCatalog.BILI).maxActions==2);
        check("hard time limit retained",store.effective(RuleCatalog.BILI).windowMs==12000);
        RuleEvaluator.Node nativeParent=node(generic.packageName,"host:id/splash","android.widget.FrameLayout","","");
        RuleEvaluator.Node genericWeb=node(generic.packageName,"host:id/creative","android.webkit.WebView","","");
        RuleEvaluator.Node foreignParent=node("org.foreign.app","other:id/root","android.widget.FrameLayout","","");
        check("generic complete native ancestry retains node-only match",evaluate(VERIFIED,generic,Arrays.asList(nativeParent),true,false,true).matched);
        RuleCatalog.Evaluation partial=evaluate(VERIFIED,generic,Arrays.asList(nativeParent),false,false,true);
        check("generic incomplete ancestry veto",!partial.matched&&partial.reason==RuleEvaluator.Reason.ANCESTORS_INCOMPLETE);
        check("generic context veto has no actionable result",partial.result==null&&!partial.sceneMatched);
        check("generic missing ancestor list veto",evaluate(VERIFIED,generic,null,true,false,true).reason==RuleEvaluator.Reason.ANCESTORS_INCOMPLETE);
        List<RuleEvaluator.Node> tooDeep=new java.util.ArrayList<>();for(int i=0;i<33;i++)tooDeep.add(nativeParent);
        check("generic oversized ancestry veto",evaluate(VERIFIED,generic,tooDeep,true,false,true).reason==RuleEvaluator.Reason.ANCESTORS_INCOMPLETE);
        check("generic foreign ancestor veto",evaluate(VERIFIED,generic,Arrays.asList(foreignParent),true,false,true).reason==RuleEvaluator.Reason.ANCESTOR_MISMATCH);
        check("generic missing ancestor entry veto",evaluate(VERIFIED,generic,Arrays.asList(nativeParent,null),true,false,true).reason==RuleEvaluator.Reason.ANCESTOR_MISMATCH);
        check("generic WebView ancestor unsupported",evaluate(VERIFIED,generic,Arrays.asList(genericWeb,nativeParent),true,false,true).reason==RuleEvaluator.Reason.EXCLUDED);
        check("generic WebView target unsupported",evaluate(VERIFIED,node(generic.packageName,generic.viewId,"android.webkit.WebView",generic.text,""),Arrays.asList(nativeParent),true,false,true).reason==RuleEvaluator.Reason.EXCLUDED);
        check("noncandidate not upgraded by complete context",!evaluate(VERIFIED,generic,Arrays.asList(nativeParent),true,false,false).matched);
        check("unknown version does not elevate generic action",!evaluate(null,generic,Arrays.asList(nativeParent),true,false,true).result.gestureAllowed);
        System.out.println("RuleCatalogTest: "+passed+" checks passed");
    }
}
