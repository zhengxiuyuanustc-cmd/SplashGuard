package org.splashguard.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Reviewed selectors live in code. Offline packs can only restrict their limits. */
public final class RuleCatalog {
    public static final String GENERIC="generic.splash", BILI="bili.splash.skip", BILI_PACKAGE="tv.danmaku.bili";
    private static final RuleSpec.NodePattern BILI_TARGET=RuleSpec.NodePattern.builder()
            .idIn(BILI_PACKAGE+":id/count_down",BILI_PACKAGE+":id/countdown_title")
            .className("android.widget.TextView")
            .textRegex("(?:[0-9]{1,2}[s秒]?[|·]?)?跳过(?:广告)?(?:[|·]?[0-9]{1,2}[s秒]?)?").build();
    private static RuleSpec.Builder biliBuilder() {
        return RuleSpec.builder(BILI,BILI_PACKAGE).target(BILI_TARGET)
                .container(RuleSpec.NodePattern.biliSplashContainer(),RuleSpec.MAX_ANCESTORS)
                .exclude(RuleSpec.NodePattern.builder().className("android.webkit.WebView").build())
                .requireAdEvidence(false);
    }
    private static final RuleSpec BILI_VERIFIED=biliBuilder().versionNames("9.15.0")
            .verifiedGestureCompatibility(false).build();
    private static final RuleSpec BILI_NODE=biliBuilder().build();
    // Exact native ad-layout path on 9.16.1/code9161100. The path supplies context even when
    // the creative has no accessible ad label; neither parent ID is a global scene marker.
    private static final RuleSpec BILI_WARM=RuleSpec.builder(BILI,BILI_PACKAGE)
            .versions(9161100,9161100).versionNames("9.16.1")
            .target(RuleSpec.NodePattern.builder().idIn(BILI_PACKAGE+":id/count_down")
                    .className("android.widget.TextView").textRegex(BILI_TARGET.textRegex).build())
            .parentPath(RuleSpec.NodePattern.builder().idIn(BILI_PACKAGE+":id/logo_layout").className("android.view.ViewGroup").build(),
                    RuleSpec.NodePattern.builder().idIn(BILI_PACKAGE+":id/root_container").className("android.view.ViewGroup").build())
            .exclude(RuleSpec.NodePattern.builder().className("android.webkit.WebView").build())
            .requireAdEvidence(false).verifiedGestureCompatibility(false).build();
    private RuleCatalog() {}

    public static List<OfflineRuleStore.RuleLimit> builtInLimits() {
        return Collections.unmodifiableList(Arrays.asList(
                new OfflineRuleStore.RuleLimit(GENERIC,OfflineRuleStore.SELECTED_APPS,0,Long.MAX_VALUE,
                        (int)SkipSession.WINDOW_MS,SkipSession.MAX_ATTEMPTS,true,false),
                new OfflineRuleStore.RuleLimit(BILI,BILI_PACKAGE,0,Long.MAX_VALUE,
                        (int)SkipSession.WINDOW_MS,SkipSession.MAX_ATTEMPTS,true,true)));
    }
    public static boolean knownBiliTarget(RuleEvaluator.Node node) {
        return node!=null && BILI_PACKAGE.equals(node.packageName)
                && (node.viewId.equals(BILI_PACKAGE+":id/count_down") || node.viewId.equals(BILI_PACKAGE+":id/countdown_title"));
    }
    private static boolean warmPath(List<RuleEvaluator.Node> parents){
        if(parents==null||parents.size()<2)return false;
        for(int i=0;i<2;i++)if(parents.get(i)==null||!BILI_PACKAGE.equals(parents.get(i).packageName)
                ||!BILI_WARM.parentPath.get(i).matches(parents.get(i)))return false;
        return true;
    }
    /** Shared by live scan, submit-time rescan and replay. The legacy geometry/label guard stays mandatory. */
    public static boolean biliGeometryHit(RuleEvaluator.VersionInfo version,RuleEvaluator.Node node,
            List<RuleEvaluator.Node> parents,boolean complete,boolean evidence,boolean legacySplash,
            int left,int top,int right,int bottom,int width,int height){
        if(node==null)return false;
        boolean warm=node.clickable&&warmPath(parents)
                &&RuleEvaluator.evaluate(BILI_WARM,version,node,parents,complete,evidence).matched;
        boolean scene=legacySplash||warm;
        return AdRules.biliCandidate(node.packageName,node.text,node.viewId,scene,left,top,right,bottom,width,height)
                ||AdRules.biliCandidate(node.packageName,node.description,node.viewId,scene,left,top,right,bottom,width,height);
    }
    public static final class Evaluation {
        public final RuleEvaluator.Result result;
        public final String ruleId;
        public final RuleEvaluator.Reason reason;
        public final boolean matched,knownBiliTarget,sceneMatched,targetMatched;
        private Evaluation(RuleEvaluator.Result result,String id,RuleEvaluator.Reason reason,
                           boolean matched,boolean known,boolean scene,boolean target) {
            this.result=result;ruleId=id;this.reason=reason;this.matched=matched;
            knownBiliTarget=known;sceneMatched=scene;targetMatched=target;
        }
    }
    /** Legacy gates include the existing package, label and geometry checks; none are broadened here. */
    public static Evaluation evaluate(RuleEvaluator.VersionInfo version,RuleEvaluator.Node node,
                                      List<RuleEvaluator.Node> ancestors,boolean complete,boolean evidence,
                                      boolean biliLegacyHit,boolean genericLegacyHit) {
        if(knownBiliTarget(node)) {
            boolean warm=warmPath(ancestors);
            RuleEvaluator.Result result=RuleEvaluator.evaluate(warm?BILI_WARM:BILI_VERIFIED,version,node,ancestors,complete,evidence);
            if(!warm&&(result.reason==RuleEvaluator.Reason.VERSION_UNKNOWN || result.reason==RuleEvaluator.Reason.VERSION_OUTSIDE))
                result=RuleEvaluator.evaluate(BILI_NODE,version,node,ancestors,complete,evidence);
            // A known target veto never falls through to generic, including identical visible text.
            boolean hit=biliLegacyHit&&result.matched&&(!warm||node.clickable);
            RuleEvaluator.Reason reason=result.matched&&!biliLegacyHit?RuleEvaluator.Reason.TARGET_MISMATCH:result.reason;
            boolean scene=complete&&(warm?result.matched:hasSplashAncestor(ancestors))&&result.reason!=RuleEvaluator.Reason.EXCLUDED;
            return new Evaluation(hit?result:null,BILI,reason,hit,true,scene,true);
        }
        RuleEvaluator.Result result=RuleEvaluator.genericCandidate(genericLegacyHit,node);
        if(result.matched) {
            RuleEvaluator.Reason context=genericContext(node,ancestors,complete);
            if(context!=null)return new Evaluation(null,GENERIC,context,false,false,false,genericLegacyHit);
        }
        return new Evaluation(result,GENERIC,result.reason,result.matched,false,evidence,genericLegacyHit);
    }
    /** Unsupported generic context is a veto, not a claim that a WebView or a node is fake. */
    private static RuleEvaluator.Reason genericContext(RuleEvaluator.Node node,List<RuleEvaluator.Node> parents,boolean complete) {
        if(!complete||parents==null||parents.size()>RuleSpec.MAX_ANCESTORS)return RuleEvaluator.Reason.ANCESTORS_INCOMPLETE;
        for(RuleEvaluator.Node parent:parents)
            if(parent==null||!node.packageName.equals(parent.packageName))return RuleEvaluator.Reason.ANCESTOR_MISMATCH;
        if(node.className.equals("android.webkit.WebView"))return RuleEvaluator.Reason.EXCLUDED;
        for(RuleEvaluator.Node parent:parents)
            if(parent.className.equals("android.webkit.WebView"))return RuleEvaluator.Reason.EXCLUDED;
        return null;
    }
    private static boolean hasSplashAncestor(List<RuleEvaluator.Node> parents) {
        if(parents==null)return false;
        boolean found=false;
        for(RuleEvaluator.Node parent:parents) {
            if(parent==null||!BILI_PACKAGE.equals(parent.packageName)||parent.className.equals("android.webkit.WebView"))return false;
            if(AdRules.biliSplashMarker(parent.viewId,parent.className))found=true;
        }
        return found;
    }
}
