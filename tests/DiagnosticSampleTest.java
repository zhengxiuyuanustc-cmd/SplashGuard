import org.splashguard.app.DiagnosticSample;
import org.splashguard.app.DiagnosticSample.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict import/privacy/replay tests; no real device or UI contents are used. */
public final class DiagnosticSampleTest {
    private static int passed;
    private static void check(String name,boolean value){if(!value)throw new AssertionError(name);passed++;}
    private static void rejects(String name,Runnable action){
        try{action.run();throw new AssertionError(name);}catch(IllegalArgumentException expected){passed++;}
    }
    private static DiagnosticSample normal(){
        return new DiagnosticSample(Reason.MATCH_VERIFIED,RuleId.BILI_SPLASH_SKIP,Target.BILIBILI,123,
                NodeType.TEXT_VIEW,Relation.VERIFIED_SPLASH_ANCESTOR,1,true,true,true,true,false);
    }
    private static Map<String,Object> changed(String key,Object value){
        Map<String,Object> m=new LinkedHashMap<>(normal().toFields());m.put(key,value);return m;
    }
    private static ReplayDecision replay(String key,Object value){return DiagnosticSample.fromFields(changed(key,value)).replay();}
    public static void main(String[] args){
        DiagnosticSample original=normal();
        DiagnosticSample parsed=DiagnosticSample.fromFields(original.toFields());
        check("safe facts roundtrip",parsed.toFields().equals(original.toFields()));
        check("review is not an execution action",parsed.replay()==ReplayDecision.REVIEW_NODE_ACTION);
        check("known rule mapping stable",RuleId.fromKnownRuleId("bili.splash.skip")==RuleId.BILI_SPLASH_SKIP
                && RuleId.fromKnownRuleId("generic.splash")==RuleId.GENERIC_SPLASH);
        check("arbitrary rule text is never copied",RuleId.fromKnownRuleId("account-phone-13800138000")==RuleId.NONE);
        check("unknown rule handle does not persist text",RuleId.fromKnownRuleId(null)==RuleId.NONE);
        check("unknown rule cannot replay as a matched rule",DiagnosticSample.fromFields(changed("ruleId","NONE")).replay()==ReplayDecision.NO_CANDIDATE);
        check("only fixed schema keys exported",original.toFields().keySet().equals(DiagnosticSample.expectedFields()));
        check("no raw node or device identifier fields",!original.toFields().containsKey("text")
                && !original.toFields().containsKey("deviceId") && !original.toFields().containsKey("screenshot")
                && !original.toFields().containsKey("versionName"));
        rejects("arbitrary reason string rejected",()->DiagnosticSample.fromFields(changed("reason","my account secret")));
        rejects("arbitrary rule ID rejected",()->DiagnosticSample.fromFields(changed("ruleId","bili.splash.skip")));
        rejects("arbitrary target package rejected",()->DiagnosticSample.fromFields(changed("target","com.bank.private")));
        rejects("arbitrary node text rejected",()->DiagnosticSample.fromFields(changed("nodeType","user message")));
        rejects("arbitrary ancestor value rejected",()->DiagnosticSample.fromFields(changed("relation","Alice's account")));
        rejects("unknown schema rejected",()->DiagnosticSample.fromFields(changed("schemaVersion",2)));
        rejects("schema numeric string not coerced",()->DiagnosticSample.fromFields(changed("schemaVersion","1")));
        rejects("version name text not accepted as version code",()->DiagnosticSample.fromFields(changed("versionCode","9.15.0")));
        rejects("decimal version code not coerced",()->DiagnosticSample.fromFields(changed("versionCode",123.0)));
        rejects("negative version below unknown rejected",()->DiagnosticSample.fromFields(changed("versionCode",-2)));
        rejects("NaN candidate count rejected",()->DiagnosticSample.fromFields(changed("candidateCount",Double.NaN)));
        rejects("overflowing candidate count rejected",()->DiagnosticSample.fromFields(changed("candidateCount",Long.MAX_VALUE)));
        rejects("negative candidate count rejected",()->DiagnosticSample.fromFields(changed("candidateCount",-1)));
        rejects("scan flag string not coerced",()->DiagnosticSample.fromFields(changed("scanComplete","true")));
        rejects("flag integer not coerced",()->DiagnosticSample.fromFields(changed("clickable",1)));
        Map<String,Object> extra=new LinkedHashMap<>(original.toFields());extra.put("rawText","secret message");
        rejects("unknown raw field rejected rather than ignored",()->DiagnosticSample.fromFields(extra));
        Map<String,Object> missing=new LinkedHashMap<>(original.toFields());missing.remove("excluded");
        rejects("missing privacy schema key rejected",()->DiagnosticSample.fromFields(missing));
        rejects("null map rejected",()->DiagnosticSample.fromFields(null));
        rejects("null enum rejected",()->new DiagnosticSample(null,RuleId.NONE,Target.OTHER_SELECTED,-1,
                NodeType.NONE,Relation.NONE,0,true,false,false,false,false));

        check("incomplete scan rejects even if a target exists",replay("scanComplete",false)==ReplayDecision.STOP_INCOMPLETE_SCAN);
        check("multiple targets rejected",replay("candidateCount",2)==ReplayDecision.STOP_MULTIPLE_CANDIDATES);
        check("zero target distinct from a blocked action",replay("candidateCount",0)==ReplayDecision.NO_CANDIDATE);
        check("exclusion takes priority over a matched target",replay("excluded",true)==ReplayDecision.STOP_EXCLUDED);
        check("unknown app version marked for review",replay("versionCode",-1)==ReplayDecision.STOP_UNVERIFIED_VERSION);
        check("nonclickable sample cannot authorize fallback",replay("clickable",false)==ReplayDecision.STOP_NOT_CLICKABLE);
        check("missing context requires manual review",replay("sceneMatched",false)==ReplayDecision.REVIEW_CONTEXT);
        check("target mismatch not treated as success",replay("targetMatched",false)==ReplayDecision.NO_CANDIDATE);
        check("fixed ambiguity reason retained",replay("reason","AMBIGUOUS")==ReplayDecision.STOP_MULTIPLE_CANDIDATES);
        check("fixed incomplete reason retained",replay("reason","INCOMPLETE_SCAN")==ReplayDecision.STOP_INCOMPLETE_SCAN);
        check("fixed unknown-version reason retained",replay("reason","VERSION_UNVERIFIED")==ReplayDecision.STOP_UNVERIFIED_VERSION);
        check("fixed wrong-context reason retained",replay("reason","CONTEXT_MISMATCH")==ReplayDecision.REVIEW_CONTEXT);
        check("stopped session cannot replay as click",replay("reason","STOPPED")==ReplayDecision.STOP_CLOSED_SESSION);
        check("submitted action is still only review input",replay("reason","ATTEMPT_SUBMITTED")==ReplayDecision.REVIEW_NODE_ACTION);
        check("node rejection does not turn into gesture",replay("reason","NODE_ACTION_REJECTED")==ReplayDecision.STOP_NOT_CLICKABLE);
        check("missing service does not become a match",replay("reason","SERVICE_NOT_READY")==ReplayDecision.STOP_SERVICE_NOT_READY);
        check("scanner cap boundary accepted",DiagnosticSample.fromFields(changed("candidateCount",600)).candidateCount==600);
        rejects("scanner cap overflow rejected",()->DiagnosticSample.fromFields(changed("candidateCount",601)));
        try{original.toFields().put("text","private");throw new AssertionError("mutable export");}
        catch(UnsupportedOperationException expected){passed++;}
        System.out.println("DiagnosticSample: "+passed+" checks passed");
    }
}
