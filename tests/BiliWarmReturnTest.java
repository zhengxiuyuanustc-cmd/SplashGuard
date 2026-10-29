import org.splashguard.app.*;
import java.util.*;
public final class BiliWarmReturnTest {
    private static int passed;private static final String P=RuleCatalog.BILI_PACKAGE;
    private static final RuleEvaluator.VersionInfo V=new RuleEvaluator.VersionInfo(9161100,"9.16.1");
    private static RuleEvaluator.Node node(String pkg,String id,String cls,String text,String desc,boolean clickable){return new RuleEvaluator.Node(pkg,id,cls,text,desc,true,true,clickable);}
    private static RuleEvaluator.Node parent(String id){return node(P,P+":id/"+id,"android.view.ViewGroup","","",false);}
    private static final RuleEvaluator.Node SKIP=node(P,P+":id/count_down","android.widget.TextView","跳过 3","",true);
    private static final List<RuleEvaluator.Node> PATH=Arrays.asList(parent("logo_layout"),parent("root_container"),node(P,"","android.widget.FrameLayout","","",false));
    private static void check(String name,boolean value){if(!value)throw new AssertionError(name);passed++;}
    private static RuleCatalog.Evaluation run(RuleEvaluator.VersionInfo v,RuleEvaluator.Node n,List<RuleEvaluator.Node> parents,boolean complete,boolean evidence,int l,int t,int r,int b){
        boolean hit=RuleCatalog.biliGeometryHit(v,n,parents,complete,evidence,false,l,t,r,b,1200,2652);
        return RuleCatalog.evaluate(v,n,parents,complete,evidence,hit,true);
    }
    private static boolean matches(RuleEvaluator.VersionInfo v,RuleEvaluator.Node n,List<RuleEvaluator.Node> parents,boolean complete,boolean evidence){return run(v,n,parents,complete,evidence,868,2425,1148,2565).matched;}
    public static void main(String[] args){
        RuleCatalog.Evaluation actual=run(V,SKIP,PATH,true,true,868,2425,1148,2565);
        check("observed exact warm path matches",actual.matched&&actual.knownBiliTarget&&actual.sceneMatched);
        check("exact version only authorizes native nodes",actual.result.versionVerified&&!actual.result.gestureAllowed);
        check("root_container alone is not a global splash marker",!AdRules.biliSplashMarker(P+":id/root_container","android.view.ViewGroup"));
        check("logo_layout alone is not a global splash marker",!AdRules.biliSplashMarker(P+":id/logo_layout","android.view.ViewGroup"));
        check("unknown numeric version rejected",!matches(new RuleEvaluator.VersionInfo(-1,"9.16.1"),SKIP,PATH,true,true));
        check("different numeric version rejected",!matches(new RuleEvaluator.VersionInfo(9161101,"9.16.1"),SKIP,PATH,true,true));
        check("unknown name rejected",!matches(new RuleEvaluator.VersionInfo(9161100,""),SKIP,PATH,true,true));
        check("different name rejected",!matches(new RuleEvaluator.VersionInfo(9161100,"9.16.2"),SKIP,PATH,true,true));
        check("same path requires actual ad evidence",!matches(V,SKIP,PATH,true,false));
        check("readable full ancestry required",!matches(V,SKIP,PATH,false,true));
        check("direct parent cannot be missing",!matches(V,SKIP,PATH.subList(1,3),true,true));
        check("parent order cannot be reversed",!matches(V,SKIP,Arrays.asList(PATH.get(1),PATH.get(0)),true,true));
        check("generic lookalike parent ID rejected",!matches(V,SKIP,Arrays.asList(parent("logo_layout_fake"),PATH.get(1)),true,true));
        check("extra wrapper before logo is unsupported",!matches(V,SKIP,Arrays.asList(parent("wrapper"),PATH.get(0),PATH.get(1)),true,true));
        check("wrong parent class rejected",!matches(V,SKIP,Arrays.asList(node(P,P+":id/logo_layout","android.widget.FrameLayout","","",false),PATH.get(1)),true,true));
        check("foreign ancestors rejected",!matches(V,SKIP,Arrays.asList(PATH.get(0),PATH.get(1),node("other.app","","android.widget.FrameLayout","","",false)),true,true));
        check("WebView anywhere in ancestry rejected",!matches(V,SKIP,Arrays.asList(PATH.get(0),PATH.get(1),node(P,"","android.webkit.WebView","","",false)),true,true));
        check("nonclickable target cannot acquire coordinates",!matches(V,node(P,SKIP.viewId,SKIP.className,SKIP.text,"",false),PATH,true,true));
        check("unsafe description rejected",!matches(V,node(P,SKIP.viewId,SKIP.className,SKIP.text,"立即安装",true),PATH,true,true));
        check("ordinary guide label rejected",!matches(V,node(P,SKIP.viewId,SKIP.className,"跳过课程","",true),PATH,true,true));
        check("unobserved separate countdown is unsupported on warm path",!matches(V,node(P,P+":id/countdown_title",SKIP.className,"跳过","",true),PATH,true,true));
        check("left-side target still rejected",!run(V,SKIP,PATH,true,true,10,2425,280,2565).matched);
        check("oversize target still rejected",!run(V,SKIP,PATH,true,true,100,1800,1190,2565).matched);
        check("outside-screen target still rejected",!run(V,SKIP,PATH,true,true,868,2425,1300,2565).matched);
        RuleReplay.Candidate one=RuleReplay.Candidate.capture(0,SKIP,PATH,true,868,2425,1148,2565);
        RuleReplay.Frame frame=RuleReplay.capture(P,V,1200,2652,true,false,true,Collections.singletonList(one));
        String json=RuleReplay.encode(frame);
        check("replay retains reviewed version and fixed parent IDs",json.contains("BILI9161")&&json.contains("BILI_LOGO_LAYOUT")&&json.contains("BILI_ROOT_CONTAINER"));
        check("live and decoded replay agree",RuleReplay.evaluate(RuleReplay.decode(json)).outcome==RuleReplay.Outcome.UNIQUE_RULE_MATCH);
        String old=json.replace("BILI9161","OTHER").replace("BILI_LOGO_LAYOUT","OTHER").replace("BILI_ROOT_CONTAINER","OTHER");
        check("lossy old export cannot silently gain recognition",RuleReplay.evaluate(RuleReplay.decode(old)).outcome==RuleReplay.Outcome.NO_MATCH);
        RuleReplay.Candidate two=RuleReplay.Candidate.capture(1,SKIP,PATH,true,868,2425,1148,2565);
        check("two apparent buttons still veto whole frame",RuleReplay.evaluate(RuleReplay.capture(P,V,1200,2652,true,false,true,Arrays.asList(one,two))).outcome==RuleReplay.Outcome.MULTIPLE_CANDIDATES);
        check("incomplete whole frame remains vetoed",RuleReplay.evaluate(RuleReplay.capture(P,V,1200,2652,true,false,false,Collections.singletonList(one))).outcome==RuleReplay.Outcome.INCOMPLETE_SCAN);
        System.out.println("BiliWarmReturnTest: "+passed+" checks passed");
    }
}
