import org.splashguard.app.RuleSpec;
import org.splashguard.app.RuleEvaluator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RuleEvaluatorTest {
    private static int passed;
    private static final String PKG="tv.danmaku.bili";
    private static void check(String name,boolean result){if(!result)throw new AssertionError(name);passed++;}
    private static RuleEvaluator.Node node(String id,String cls,String text,String desc){
        return new RuleEvaluator.Node(PKG,id,cls,text,desc,true,true,true);
    }
    private static final RuleEvaluator.Node SKIP=node(PKG+":id/count_down","android.widget.TextView","跳过 5","");
    private static final RuleEvaluator.Node WRAPPER=node(PKG+":id/logo_layout","android.widget.FrameLayout","","");
    private static final RuleEvaluator.Node SPLASH=node(PKG+":id/splash_layout","android.widget.FrameLayout","","");
    private static final RuleEvaluator.Node CREATIVE=node(PKG+":id/creative","android.webkit.WebView","","");
    private static final RuleEvaluator.VersionInfo VERSION=new RuleEvaluator.VersionInfo(-1,"9.15.0");
    private static final List<RuleEvaluator.Node> PARENTS=Arrays.asList(WRAPPER,SPLASH);
    private static RuleSpec.NodePattern target(){return RuleSpec.NodePattern.builder()
            .idIn(PKG+":id/count_down",PKG+":id/countdown_title").className("android.widget.TextView")
            .textRegex("(?:[0-9]{1,2}[s秒]?[|·]?)?跳过(?:广告)?(?:[|·]?[0-9]{1,2}[s秒]?)?").build();}
    private static RuleSpec.Builder builder(){return RuleSpec.builder("bili.splash.skip",PKG)
            .versionNames("9.15.0").target(target()).container(RuleSpec.NodePattern.biliSplashContainer(),4)
            .requireAdEvidence(false);}
    private static RuleEvaluator.Result eval(RuleSpec rule,RuleEvaluator.VersionInfo version,RuleEvaluator.Node n,List<RuleEvaluator.Node> p,boolean complete,boolean ad){
        return RuleEvaluator.evaluate(rule,version,n,p,complete,ad);
    }
    private static boolean rejects(Runnable run){try{run.run();return false;}catch(IllegalArgumentException expected){return true;}}
    public static void main(String[] args){
        RuleSpec verified=builder().verifiedGestureCompatibility(true).build();
        RuleEvaluator.Result hit=eval(verified,VERSION,SKIP,PARENTS,true,false);
        check("recorded name with unknown numeric code can be verified",hit.matched&&hit.versionVerified&&hit.gestureAllowed);
        check("stable rule identifier is retained",hit.ruleId.equals("bili.splash.skip")&&hit.reason==RuleEvaluator.Reason.MATCH_VERIFIED);
        check("same label and ID in creative is not a verified close",eval(verified,VERSION,SKIP,Arrays.asList(CREATIVE),true,true).reason==RuleEvaluator.Reason.ANCESTOR_MISMATCH);
        check("a splash elsewhere in the page is not an ancestor",!eval(verified,VERSION,SKIP,Collections.emptyList(),true,true).matched);
        check("unknown version name is not approved",eval(verified,new RuleEvaluator.VersionInfo(123,""),SKIP,PARENTS,true,true).reason==RuleEvaluator.Reason.VERSION_UNKNOWN);
        check("next application version is not implicitly approved",eval(verified,new RuleEvaluator.VersionInfo(123,"9.16.0"),SKIP,PARENTS,true,true).reason==RuleEvaluator.Reason.VERSION_OUTSIDE);
        check("null version is unknown",eval(verified,null,SKIP,PARENTS,true,true).reason==RuleEvaluator.Reason.VERSION_UNKNOWN);
        check("wrong package cannot reuse target metadata",eval(verified,VERSION,new RuleEvaluator.Node("com.other",SKIP.viewId,SKIP.className,SKIP.text,"",true,true,true),PARENTS,true,true).reason==RuleEvaluator.Reason.WRONG_PACKAGE);
        check("similar id is not exact id",!eval(verified,VERSION,node(PKG+":id/count_down_fake",SKIP.className,SKIP.text,""),PARENTS,true,true).matched);
        check("target class is exact",!eval(verified,VERSION,node(SKIP.viewId,"android.webkit.WebView",SKIP.text,""),PARENTS,true,true).matched);
        check("combined download label is rejected",eval(verified,VERSION,node(SKIP.viewId,SKIP.className,"跳过广告并下载",""),PARENTS,true,true).reason==RuleEvaluator.Reason.UNSAFE_LABEL);
        check("safe visible text cannot override dangerous description",eval(verified,VERSION,node(SKIP.viewId,SKIP.className,SKIP.text,"立即安装"),PARENTS,true,true).reason==RuleEvaluator.Reason.UNSAFE_LABEL);
        check("english dangerous description is rejected",eval(verified,VERSION,node(SKIP.viewId,SKIP.className,SKIP.text,"Install now"),PARENTS,true,true).reason==RuleEvaluator.Reason.UNSAFE_LABEL);
        check("partial ancestor traversal does not prove exclusions absent",eval(verified,VERSION,SKIP,PARENTS,false,true).reason==RuleEvaluator.Reason.ANCESTORS_INCOMPLETE);
        check("missing ancestor list is not complete",eval(verified,VERSION,SKIP,null,true,true).reason==RuleEvaluator.Reason.ANCESTORS_INCOMPLETE);
        List<RuleEvaluator.Node> oversized=new ArrayList<>();for(int i=0;i<33;i++)oversized.add(SPLASH);
        check("ancestor scan cannot exceed the hard depth",eval(verified,VERSION,SKIP,oversized,true,true).reason==RuleEvaluator.Reason.ANCESTORS_INCOMPLETE);
        check("missing parent entry is rejected",eval(verified,VERSION,SKIP,Arrays.asList(WRAPPER,null),true,true).reason==RuleEvaluator.Reason.ANCESTOR_MISMATCH);
        RuleEvaluator.Node foreign=new RuleEvaluator.Node("com.other",SPLASH.viewId,SPLASH.className,"","",true,true,false);
        check("foreign ancestor package is rejected",eval(verified,VERSION,SKIP,Arrays.asList(foreign),true,true).reason==RuleEvaluator.Reason.ANCESTOR_MISMATCH);
        RuleSpec shallow=builder().container(RuleSpec.NodePattern.biliSplashContainer(),1).build();
        check("container depth bound is enforced",!eval(shallow,VERSION,SKIP,PARENTS,true,true).matched);
        RuleSpec path=builder().parentPath(RuleSpec.NodePattern.idIn(WRAPPER.viewId),RuleSpec.NodePattern.idIn(SPLASH.viewId)).build();
        check("direct parent path passes in order",eval(path,VERSION,SKIP,PARENTS,true,true).matched);
        check("same ancestors in a different order do not pass a direct path",!eval(path,VERSION,SKIP,Arrays.asList(SPLASH,WRAPPER),true,true).matched);
        RuleSpec excluded=builder().exclude(RuleSpec.NodePattern.idIn(CREATIVE.viewId)).build();
        check("excluded creative ancestor vetoes an otherwise matching tree",eval(excluded,VERSION,SKIP,Arrays.asList(CREATIVE,SPLASH),true,true).reason==RuleEvaluator.Reason.EXCLUDED);
        RuleSpec excludedTarget=builder().exclude(RuleSpec.NodePattern.idIn(SKIP.viewId)).build();
        check("target-level exclusion vetoes",eval(excludedTarget,VERSION,SKIP,PARENTS,true,true).reason==RuleEvaluator.Reason.EXCLUDED);
        RuleSpec evidence=builder().requireAdEvidence(true).build();
        check("required ad evidence is enforced",eval(evidence,VERSION,SKIP,PARENTS,true,false).reason==RuleEvaluator.Reason.NO_AD_EVIDENCE);
        check("explicit ad evidence permits same rule",eval(evidence,VERSION,SKIP,PARENTS,true,true).matched);
        RuleSpec codes=builder().versions(100,200).build();
        check("numeric lower boundary inclusive",eval(codes,new RuleEvaluator.VersionInfo(100,"9.15.0"),SKIP,PARENTS,true,true).matched);
        check("numeric upper boundary inclusive",eval(codes,new RuleEvaluator.VersionInfo(200,"9.15.0"),SKIP,PARENTS,true,true).matched);
        check("numeric unknown cannot pass a declared numeric range",eval(codes,VERSION,SKIP,PARENTS,true,true).reason==RuleEvaluator.Reason.VERSION_UNKNOWN);
        check("numeric outside fails despite approved name",eval(codes,new RuleEvaluator.VersionInfo(201,"9.15.0"),SKIP,PARENTS,true,true).reason==RuleEvaluator.Reason.VERSION_OUTSIDE);
        check("approved numeric code cannot override wrong name",eval(codes,new RuleEvaluator.VersionInfo(150,"9.16.0"),SKIP,PARENTS,true,true).reason==RuleEvaluator.Reason.VERSION_OUTSIDE);
        RuleSpec unversioned=RuleSpec.builder("generic.test",PKG).target(target()).requireAdEvidence(false).build();
        RuleEvaluator.Result loose=eval(unversioned,null,SKIP,Collections.emptyList(),true,false);
        check("unversioned exact target is only a low confidence match",loose.matched&&!loose.versionVerified&&!loose.gestureAllowed&&loose.reason==RuleEvaluator.Reason.MATCH_UNVERIFIED);
        check("gesture compatibility cannot be declared without versions",rejects(()->RuleSpec.builder("unsafe",PKG).target(target()).container(RuleSpec.NodePattern.biliSplashContainer(),4).verifiedGestureCompatibility(true).build()));
        check("gesture compatibility cannot be declared without context",rejects(()->RuleSpec.builder("unsafe",PKG).target(target()).versionNames("9.15.0").verifiedGestureCompatibility(true).build()));
        check("class-only target is forbidden",rejects(()->RuleSpec.builder("unsafe",PKG).target(RuleSpec.NodePattern.builder().className("android.widget.TextView").build()).build()));
        check("wildcard package forbidden",rejects(()->RuleSpec.builder("unsafe","*").target(target()).build()));
        check("invalid version range rejected",rejects(()->builder().versions(200,100).build()));
        check("unbounded container rejected",rejects(()->builder().container(RuleSpec.NodePattern.biliSplashContainer(),33).build()));
        check("empty node pattern rejected",rejects(()->RuleSpec.NodePattern.builder().build()));
        check("invisible target rejected",eval(verified,VERSION,new RuleEvaluator.Node(PKG,SKIP.viewId,SKIP.className,SKIP.text,"",false,true,true),PARENTS,true,true).reason==RuleEvaluator.Reason.NOT_VISIBLE);
        check("disabled target rejected",eval(verified,VERSION,new RuleEvaluator.Node(PKG,SKIP.viewId,SKIP.className,SKIP.text,"",true,false,true),PARENTS,true,true).reason==RuleEvaluator.Reason.NOT_ENABLED);
        String[] sourceIds={SKIP.viewId};RuleSpec.NodePattern immutable=RuleSpec.NodePattern.idIn(sourceIds);sourceIds[0]="fake";
        check("caller cannot mutate copied id choices",immutable.ids.contains(SKIP.viewId)&&!immutable.ids.contains("fake"));
        boolean immutableList=false;try{immutable.ids.add("fake");}catch(UnsupportedOperationException expected){immutableList=true;}
        check("public id list is read only",immutableList);
        RuleSpec exact=RuleSpec.builder("exact",PKG).target(RuleSpec.NodePattern.builder().textExact("跳过广告").build()).requireAdEvidence(false).build();
        check("exact label may be a normalized description",eval(exact,null,node("","android.widget.TextView",""," 跳 过 广 告 "),Collections.emptyList(),true,false).matched);
        RuleEvaluator.Result generic=RuleEvaluator.genericCandidate(true,SKIP);
        check("legacy candidate remains node only and unverified",generic.matched&&!generic.versionVerified&&!generic.gestureAllowed&&generic.ruleId.equals("generic.splash"));
        check("noncandidate cannot be upgraded by generic path",!RuleEvaluator.genericCandidate(false,SKIP).matched);
        check("generic path rejects dangerous secondary label",!RuleEvaluator.genericCandidate(true,node(SKIP.viewId,SKIP.className,"跳过广告","Open offer")).matched);
        System.out.println("RuleEvaluator: "+passed+" checks passed");
    }
}
