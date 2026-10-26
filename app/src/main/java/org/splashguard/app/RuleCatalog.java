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
            .verifiedGestureCompatibility(true).build();
    private static final RuleSpec BILI_NODE=biliBuilder().build();
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
            RuleEvaluator.Result result=RuleEvaluator.evaluate(BILI_VERIFIED,version,node,ancestors,complete,evidence);
            if(result.reason==RuleEvaluator.Reason.VERSION_UNKNOWN || result.reason==RuleEvaluator.Reason.VERSION_OUTSIDE)
                result=RuleEvaluator.evaluate(BILI_NODE,version,node,ancestors,complete,evidence);
            // A known target veto never falls through to generic, including identical visible text.
            boolean hit=biliLegacyHit&&result.matched;
            RuleEvaluator.Reason reason=result.matched&&!biliLegacyHit?RuleEvaluator.Reason.TARGET_MISMATCH:result.reason;
            boolean scene=complete&&hasSplashAncestor(ancestors)&&result.reason!=RuleEvaluator.Reason.EXCLUDED;
            return new Evaluation(hit?result:null,BILI,reason,hit,true,scene,true);
        }
        RuleEvaluator.Result result=RuleEvaluator.genericCandidate(genericLegacyHit,node);
        return new Evaluation(result,GENERIC,result.reason,result.matched,false,evidence,genericLegacyHit);
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
