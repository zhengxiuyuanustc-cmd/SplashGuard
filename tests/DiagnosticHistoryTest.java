import org.splashguard.app.*;
import java.util.*;
public final class DiagnosticHistoryTest {
    private static int checks;
    private static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);checks++;}
    private static DiagnosticSample sample(DiagnosticSample.Reason reason){return new DiagnosticSample(reason,
        DiagnosticSample.RuleId.BILI_SPLASH_SKIP,DiagnosticSample.Target.BILIBILI,9161100,
        DiagnosticSample.NodeType.TEXT_VIEW,DiagnosticSample.Relation.NONE,0,true,true,false,true,false);}
    public static void main(String[]args){
        DiagnosticHistory history=new DiagnosticHistory();
        check("empty latest",history.last()==null);check("empty useful",history.useful()==null);
        DiagnosticSample no=sample(DiagnosticSample.Reason.NO_MATCH),expired=sample(DiagnosticSample.Reason.WINDOW_EXPIRED);
        history.remember(no);history.remember(expired);
        check("final expiry remains explicit",history.last()==expired);check("empty matches don't erase first scan",history.useful()==no);
        history.beginWindow();check("new window clears final",history.last()==null);check("new window clears old detail",history.useful()==null);
        DiagnosticSample reject=sample(DiagnosticSample.Reason.NO_AD_EVIDENCE);history.remember(no);history.remember(reject);
        for(int i=0;i<200;i++)history.remember(no);
        history.remember(expired);check("specific rejection survives later empty scans and expiry",history.useful()==reject);
        check("latest expiry is separately visible",history.last()==expired);
        DiagnosticSample action=sample(DiagnosticSample.Reason.ACTION_SUBMITTED);history.remember(action);history.remember(expired);
        check("later real action supersedes earlier rejection",history.useful()==action);
        history.beginWindow();history.remember(expired);check("new expired window cannot inherit prior action",history.useful()==expired);
        for(DiagnosticSample.Reason reason:DiagnosticSample.Reason.values()){
            if(reason==DiagnosticSample.Reason.NO_MATCH||reason==DiagnosticSample.Reason.WINDOW_EXPIRED)continue;
            history.beginWindow();DiagnosticSample specific=sample(reason);history.remember(specific);history.remember(no);history.remember(expired);
            check("specific reason retained: "+reason,history.useful()==specific&&history.last()==expired);
        }
        history.beginWindow(1000);check("new window starts at zero age",history.age(1000)==0);
        check("age exposes an old unrenewed session",history.age(35000)==34000);
        check("earlier clock stays unknown",history.age(999)==-1);
        try{history.beginWindow(-1);throw new AssertionError("negative time accepted");}catch(IllegalArgumentException expected){check("invalid start does not reset age",history.age(35000)==34000);}
        history.clear();check("clear forgets timing",history.age(40000)==-1);
        check("explicit clear removes both",history.last()==null&&history.useful()==null);
        try{history.remember(null);throw new AssertionError("null accepted");}catch(IllegalArgumentException expected){check("null leaves state unchanged",history.last()==null&&history.useful()==null);}
        String pkg=RuleCatalog.BILI_PACKAGE;
        RuleEvaluator.Node event=new RuleEvaluator.Node(pkg,pkg+":id/skip","android.widget.TextView","跳过 5","",true,true,true);
        RuleEvaluator.Node parent=new RuleEvaluator.Node(pkg,pkg+":id/root","android.view.ViewGroup","","",true,true,false);
        check("fixed extra target is diagnostic only",RuleReplay.additionalDiagnosticCandidate(event)&&!RuleCatalog.knownBiliTarget(event));
        RuleReplay.Candidate candidate=RuleReplay.Candidate.capture(0,event,Collections.singletonList(parent),true,868,2425,1148,2565);
        RuleReplay.Frame frame=RuleReplay.capture(pkg,new RuleEvaluator.VersionInfo(9161100,"9.16.1"),1200,2652,false,false,true,Collections.singletonList(candidate));
        String json=RuleReplay.encode(frame);check("two fixed resource names survive diagnostics",json.contains("BILI_EVENT_SKIP")&&json.contains("BILI_EVENT_ROOT"));
        check("roundtrip does not grant an action selector",RuleReplay.evaluate(RuleReplay.decode(json)).outcome==RuleReplay.Outcome.NO_MATCH);
        check("unknown old categories do not gain resource identity",!RuleReplay.encode(RuleReplay.decode(json.replace("BILI_EVENT_SKIP","OTHER").replace("BILI_EVENT_ROOT","OTHER"))).contains("BILI_EVENT_SKIP"));
        check("foreign package is not an extra diagnostic target",!RuleReplay.additionalDiagnosticCandidate(new RuleEvaluator.Node("other.app",event.viewId,event.className,event.text,"",true,true,true)));
        check("lookalike ID rejected",!RuleReplay.additionalDiagnosticCandidate(new RuleEvaluator.Node(pkg,pkg+":id/skip_fake",event.className,event.text,"",true,true,true)));
        check("unrelated label rejected",!RuleReplay.additionalDiagnosticCandidate(new RuleEvaluator.Node(pkg,event.viewId,event.className,"跳过课程","",true,true,true)));
        check("other kind rejected",!RuleReplay.additionalDiagnosticCandidate(new RuleEvaluator.Node(pkg,event.viewId,"android.widget.Button",event.text,"",true,true,true)));
        check("null is not a diagnostic target",!RuleReplay.additionalDiagnosticCandidate(null));
        System.out.println("DiagnosticHistoryTest: "+checks+" checks passed");
    }
}
