import org.splashguard.app.ActionPolicy;
import org.splashguard.app.RuleSpec;
import org.splashguard.app.RuleEvaluator;
import java.util.Arrays;

public final class ActionPolicyTest {
    private static int passed;
    private static void check(String name,boolean actual){if(!actual)throw new AssertionError(name);passed++;}
    private static RuleEvaluator.Result matched(boolean compat){
        String pkg="tv.danmaku.bili";
        RuleSpec rule=RuleSpec.builder("bili.splash.skip",pkg).versionNames("9.15.0")
                .target(RuleSpec.NodePattern.idIn(pkg+":id/count_down"))
                .container(RuleSpec.NodePattern.biliSplashContainer(),4).requireAdEvidence(false)
                .verifiedGestureCompatibility(compat).build();
        RuleEvaluator.Node target=new RuleEvaluator.Node(pkg,pkg+":id/count_down","android.widget.TextView","跳过5","",true,true,true);
        RuleEvaluator.Node parent=new RuleEvaluator.Node(pkg,pkg+":id/splash_layout","android.widget.FrameLayout","","",true,true,false);
        return RuleEvaluator.evaluate(rule,new RuleEvaluator.VersionInfo(-1,"9.15.0"),target,Arrays.asList(parent),true,false);
    }
    private static ActionPolicy.Action choose(RuleEvaluator.Result r,boolean click,boolean reject,int attempt){return ActionPolicy.choose(r,click,reject,attempt);}
    public static void main(String[] args){
        RuleEvaluator.Result normal=matched(false),compat=matched(true);
        RuleEvaluator.Node label=new RuleEvaluator.Node("com.example","","android.widget.TextView","跳过广告","",true,true,true);
        RuleEvaluator.Result generic=RuleEvaluator.genericCandidate(true,label);
        check("ordinary first action uses native node",choose(normal,true,false,1)==ActionPolicy.Action.NODE);
        check("ordinary retry remains native node",choose(normal,true,false,2)==ActionPolicy.Action.NODE);
        check("ordinary rejected action never upgrades to coordinates",choose(normal,true,true,1)==ActionPolicy.Action.NONE);
        check("ordinary rejected retry never upgrades to coordinates",choose(normal,true,true,2)==ActionPolicy.Action.NONE);
        check("ordinary nonclickable target has no coordinate fallback",choose(normal,false,false,1)==ActionPolicy.Action.NONE);
        check("generic first action only uses node",choose(generic,true,false,1)==ActionPolicy.Action.NODE);
        check("generic retry only uses node",choose(generic,true,false,2)==ActionPolicy.Action.NODE);
        check("generic rejected node stops",choose(generic,true,true,1)==ActionPolicy.Action.NONE);
        check("generic nonclickable target stops",choose(generic,false,false,1)==ActionPolicy.Action.NONE);
        check("verified compatibility still starts with a node",choose(compat,true,false,1)==ActionPolicy.Action.NODE);
        check("legacy metadata cannot enable rejected-node coordinates",choose(compat,true,true,1)==ActionPolicy.Action.NONE);
        check("legacy compatibility retry stays native",choose(compat,true,false,2)==ActionPolicy.Action.NODE);
        check("legacy nonclickable target cannot request coordinates",choose(compat,false,false,1)==ActionPolicy.Action.NONE);
        check("zero attempt is invalid",choose(compat,true,false,0)==ActionPolicy.Action.NONE);
        check("negative attempt is invalid",choose(compat,true,false,-1)==ActionPolicy.Action.NONE);
        check("third attempt cannot bypass external budget",choose(compat,true,false,3)==ActionPolicy.Action.NONE);
        check("null match cannot authorize any action",choose(null,true,false,1)==ActionPolicy.Action.NONE);
        check("a failed match cannot authorize a gesture",choose(RuleEvaluator.genericCandidate(false,label),true,true,1)==ActionPolicy.Action.NONE);
        check("coordinate implementation not available",!ActionPolicy.gesturesAvailable());
        check("legacy second-round rejection still stops",choose(compat,true,true,2)==ActionPolicy.Action.NONE);
        check("legacy second-round nonclickable stops",choose(compat,false,false,2)==ActionPolicy.Action.NONE);
        System.out.println("ActionPolicy: "+passed+" checks passed");
    }
}
