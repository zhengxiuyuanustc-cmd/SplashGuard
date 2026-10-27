import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.splashguard.app.*;

public final class RuleReplayTest {
    private static int passed;
    private static final String BILI="tv.danmaku.bili";
    private static final RuleEvaluator.VersionInfo OLD=new RuleEvaluator.VersionInfo(100,"9.15.0");
    private static void check(String label,boolean result){if(!result)throw new AssertionError(label);passed++;}
    private static void rejects(String label,Runnable code){try{code.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){passed++;}}
    private static RuleEvaluator.Node node(String pkg,String id,String cls,String text,String desc){return new RuleEvaluator.Node(pkg,id,cls,text,desc,true,true,true);}
    private static RuleEvaluator.Node parent(){return node(BILI,BILI+":id/splash_container","android.widget.FrameLayout","","");}
    private static RuleReplay.Candidate c(int identity,String text,String desc,List<RuleEvaluator.Node> parents){return RuleReplay.Candidate.capture(identity,node(BILI,BILI+":id/countdown_title","android.widget.TextView",text,desc),parents,true,700,20,950,80);}
    private static RuleReplay.Frame f(RuleReplay.Candidate... candidates){return RuleReplay.capture(BILI,OLD,1000,2000,true,true,true,Arrays.asList(candidates));}
    public static void main(String[] args){
        RuleReplay.Candidate real=c(0,"跳过 5","",Collections.singletonList(parent()));
        RuleReplay.Frame good=f(real);RuleReplay.Result hit=RuleReplay.evaluate(good);
        check("runs real catalog matching",hit.outcome==RuleReplay.Outcome.UNIQUE_RULE_MATCH&&hit.ruleId.equals(RuleCatalog.BILI)&&hit.versionVerified);
        check("result counts identities rather than labels",hit.matchedIdentities==1&&hit.uniqueIdentity==0);
        String json=RuleReplay.encode(good);
        check("roundtrip deterministic",json.equals(RuleReplay.encode(RuleReplay.decode(json))));
        check("roundtrip reruns rule evaluator",RuleReplay.evaluate(RuleReplay.decode(json)).outcome==hit.outcome);
        check("no original resource ID in output",!json.contains(":id/countdown_title"));
        check("no exact package in output",!json.contains(BILI));
        check("not supplied precomputed verdict",!json.contains("UNIQUE_RULE_MATCH")&&!json.contains("reason"));
        RuleReplay.Candidate sameMeta=c(1,"跳过 5","",Collections.singletonList(parent()));
        check("two lookalikes are ambiguous",RuleReplay.evaluate(f(real,sameMeta)).outcome==RuleReplay.Outcome.MULTIPLE_CANDIDATES);
        check("order reversal still ambiguous",RuleReplay.evaluate(f(sameMeta,real)).outcome==RuleReplay.Outcome.MULTIPLE_CANDIDATES);
        check("equivalent identity alias is one candidate",RuleReplay.evaluate(f(real,real)).matchedIdentities==1);
        rejects("inconsistent aliases rejected",()->f(real,c(0,"跳过 4","",Collections.singletonList(parent()))));
        check("wrong container cannot fall back generic",RuleReplay.evaluate(f(c(0,"跳过 5","",Collections.emptyList()))).outcome==RuleReplay.Outcome.NO_MATCH);
        check("unsafe opposite description vetoed",RuleReplay.evaluate(f(c(0,"跳过 5","点击安装好礼",Collections.singletonList(parent())))).outcome==RuleReplay.Outcome.NO_MATCH);
        check("unsafe source labels reduced to token",!RuleReplay.encode(f(c(0,"跳过5","点击安装秘密账号",Collections.singletonList(parent())))).contains("秘密账号"));
        RuleEvaluator.Node web=node(BILI,"account.private:id/web","android.webkit.WebView","用户账号","");
        check("WebView exclusion preserved",RuleReplay.evaluate(f(c(0,"跳过5","",Arrays.asList(parent(),web)))).outcome==RuleReplay.Outcome.NO_MATCH);
        RuleEvaluator.Node otherParent=node("org.other.app",BILI+":id/splash_container","android.widget.FrameLayout","","");
        check("other-package ancestor veto preserved",RuleReplay.evaluate(f(c(0,"跳过5","",Collections.singletonList(otherParent)))).outcome==RuleReplay.Outcome.NO_MATCH);
        RuleReplay.Frame newVersion=RuleReplay.capture(BILI,new RuleEvaluator.VersionInfo(101,"9.16.0"),1000,2000,true,true,true,Collections.singletonList(real));
        check("new version can only be unverified match",RuleReplay.evaluate(newVersion).outcome==RuleReplay.Outcome.UNIQUE_RULE_MATCH&&!RuleReplay.evaluate(newVersion).versionVerified);
        check("raw version names not exported",!RuleReplay.encode(newVersion).contains("9.16.0"));
        RuleReplay.Frame incomplete=RuleReplay.capture(BILI,OLD,1000,2000,true,true,false,Arrays.asList(real,sameMeta));
        check("partial scan stops before matching",RuleReplay.evaluate(incomplete).outcome==RuleReplay.Outcome.INCOMPLETE_SCAN);
        RuleReplay.Candidate invalidBounds=RuleReplay.Candidate.capture(0,node(BILI,BILI+":id/countdown_title","android.widget.TextView","跳过5",""),Collections.singletonList(parent()),true,-10,0,100,80);
        check("offscreen bounds cannot match",RuleReplay.evaluate(f(invalidBounds)).outcome==RuleReplay.Outcome.NO_MATCH);
        RuleReplay.Candidate wrongClass=RuleReplay.Candidate.capture(0,node(BILI,BILI+":id/countdown_title","private.account.Custom","跳过5",""),Collections.singletonList(parent()),true,700,20,950,80);
        check("unknown class not promoted to TextView",RuleReplay.evaluate(f(wrongClass)).outcome==RuleReplay.Outcome.NO_MATCH);
        List<RuleEvaluator.Node> deep=new ArrayList<>();for(int i=0;i<33;i++)deep.add(parent());
        RuleReplay.Candidate truncated=RuleReplay.Candidate.capture(0,node(BILI,BILI+":id/countdown_title","android.widget.TextView","跳过5",""),deep,true,700,20,950,80);
        check("deep ancestor list becomes incomplete",!truncated.ancestorsComplete&&RuleReplay.evaluate(f(truncated)).outcome==RuleReplay.Outcome.INCOMPLETE_SCAN);
        String privatePkg="private.user.selected";RuleReplay.Candidate sdk=RuleReplay.Candidate.capture(0,node(privatePkg,"private:id/tt_splash_skip_btn","private.user.Widget","会员账号13800000000",""),Collections.emptyList(),true,700,20,950,80);
        RuleReplay.Frame redacted=RuleReplay.capture(privatePkg,new RuleEvaluator.VersionInfo(-1,"private-version"),1000,2000,true,false,true,Collections.singletonList(sdk));
        String sanitized=RuleReplay.encode(redacted);
        check("unknown selected package collapsed",!sanitized.contains(privatePkg)&&sanitized.contains("OTHER_SELECTED"));
        check("unknown labels and classes removed",!sanitized.contains("13800000000")&&!sanitized.contains("private.user.Widget")&&!sanitized.contains("private-version"));
        check("SDK identity semantics preserved",RuleReplay.evaluate(redacted).outcome==RuleReplay.Outcome.UNIQUE_RULE_MATCH);
        check("no raw ID in sanitized generic frame",!sanitized.contains("tt_splash_skip_btn"));
        RuleReplay.Candidate wrongPackage=RuleReplay.Candidate.capture(0,node("org.other.app",BILI+":id/countdown_title","android.widget.TextView","跳过5",""),Collections.singletonList(parent()),true,700,20,950,80);
        check("cross-package target cannot match",RuleReplay.evaluate(f(wrongPackage)).outcome==RuleReplay.Outcome.NO_MATCH);
        rejects("unknown root field",()->RuleReplay.decode(json.replace("\"schemaVersion\":2","\"schemaVersion\":2,\"upload\":true")));
        rejects("precomputed verdict rejected",()->RuleReplay.decode(json.replace("\"schemaVersion\":2","\"schemaVersion\":2,\"reason\":\"MATCH_VERIFIED\"")));
        rejects("arbitrary raw label rejected",()->RuleReplay.decode(json.replace("跳过5","秘密账户信息")));
        rejects("unknown kind rejected",()->RuleReplay.decode(json.replace("TEXT_VIEW","PrivateClass")));
        rejects("fractional coordinate rejected",()->RuleReplay.decode(json.replace("[700,20,950,80]","[700.1,20,950,80]")));
        rejects("oversized frame rejected",()->RuleReplay.capture(BILI,OLD,9000,2000,true,true,true,Collections.singletonList(real)));
        List<RuleReplay.Candidate> tooMany=new ArrayList<>();for(int i=0;i<65;i++)tooMany.add(real);
        rejects("more than64 inputs rejected",()->RuleReplay.capture(BILI,OLD,1000,2000,true,true,true,tooMany));
        rejects("invalid native-like identity rejected",()->c(64000,"跳过5","",Collections.singletonList(parent())));
        rejects("future schema rejected",()->RuleReplay.decode(json.replace("\"schemaVersion\":2","\"schemaVersion\":3")));
        rejects("wrong type boolean rejected",()->RuleReplay.decode(json.replace("\"evidence\":true","\"evidence\":\"true\"")));
        check("no candidate is not success",RuleReplay.evaluate(f()).outcome==RuleReplay.Outcome.NO_MATCH);
        char[] labelChars=new char[RuleReplay.MAX_SOURCE_LABEL_CHARS];Arrays.fill(labelChars,'x');String bounded=new String(labelChars);
        RuleEvaluator.Node boundaryNode=node(privatePkg,"private:id/tt_splash_skip_btn","android.widget.Button",bounded,"");
        RuleReplay.Candidate boundary=RuleReplay.Candidate.capture(0,boundaryNode,Collections.emptyList(),true,700,20,950,80);
        RuleReplay.Frame boundaryFrame=RuleReplay.capture(privatePkg,OLD,1000,2000,true,false,true,Collections.singletonList(boundary));
        check("source-label boundary remains representable",boundaryFrame.scanComplete);
        check("bounded unknown SDK label keeps existing matching semantics",RuleReplay.evaluate(boundaryFrame).outcome==RuleReplay.Outcome.UNIQUE_RULE_MATCH);
        RuleEvaluator.Node oversizedNode=node(privatePkg,"private:id/tt_splash_skip_btn","android.widget.Button",bounded+"private-account","");
        RuleReplay.Candidate oversized=RuleReplay.Candidate.capture(0,oversizedNode,Collections.emptyList(),true,700,20,950,80);
        RuleReplay.Frame oversizedFrame=RuleReplay.capture(privatePkg,OLD,1000,2000,true,false,true,Collections.singletonList(oversized));
        check("overlong source label marks whole frame incomplete",!oversizedFrame.scanComplete);
        check("overlong SDK label cannot claim unique replay",RuleReplay.evaluate(oversizedFrame).outcome==RuleReplay.Outcome.INCOMPLETE_SCAN);
        String oversizedJson=RuleReplay.encode(oversizedFrame);
        check("overlong raw account content never retained",!oversizedJson.contains("private-account")&&!oversizedJson.contains(bounded));
        check("incomplete source survives file roundtrip",!RuleReplay.decode(oversizedJson).scanComplete);
        RuleReplay.Candidate longDescription=c(0,"跳过5",bounded+"description",Collections.singletonList(parent()));
        check("opposite overlong description marks incomplete",!f(longDescription).scanComplete);
        RuleEvaluator.Node longParent=node(BILI,BILI+":id/splash_container","android.widget.FrameLayout",bounded+"ancestor","");
        RuleReplay.Frame longAncestry=f(c(0,"跳过5","",Collections.singletonList(longParent)));
        check("overlong ancestor labels mark incomplete",!longAncestry.scanComplete&&RuleReplay.evaluate(longAncestry).outcome==RuleReplay.Outcome.INCOMPLETE_SCAN);
        RuleEvaluator.Node originalSkip=node(BILI,BILI+":id/countdown_title","android.widget.TextView","跳过5","");
        check("diagnostic cap does not change live rule matching",RuleCatalog.evaluate(OLD,originalSkip,Collections.singletonList(longParent),true,true,true,true).matched);
        check("lossy candidate does not silently select another identity",RuleReplay.evaluate(f(longDescription,sameMeta)).outcome==RuleReplay.Outcome.INCOMPLETE_SCAN);
        char[] spaces=new char[RuleReplay.MAX_SOURCE_LABEL_CHARS+1];Arrays.fill(spaces,' ');
        check("capture bounds input before whitespace normalization",!f(c(0,new String(spaces)+"跳过5","",Collections.singletonList(parent()))).scanComplete);
        RuleEvaluator.Node nativeHost=node(privatePkg,"host:id/root","android.widget.FrameLayout","","");
        RuleEvaluator.Node sdkNode=node(privatePkg,"private:id/tt_splash_skip_btn","android.widget.Button","","");
        RuleReplay.Candidate nativeSdk=RuleReplay.Candidate.capture(0,sdkNode,Collections.singletonList(nativeHost),true,700,20,950,80);
        RuleReplay.Frame nativeSdkFrame=RuleReplay.capture(privatePkg,OLD,1000,2000,true,false,true,Collections.singletonList(nativeSdk));
        check("generic native context survives real replay",RuleReplay.evaluate(nativeSdkFrame).outcome==RuleReplay.Outcome.UNIQUE_RULE_MATCH);
        check("generic native context survives file roundtrip",RuleReplay.evaluate(RuleReplay.decode(RuleReplay.encode(nativeSdkFrame))).outcome==RuleReplay.Outcome.UNIQUE_RULE_MATCH);
        RuleEvaluator.Node webHost=node(privatePkg,"host:id/web","android.webkit.WebView","","");
        RuleReplay.Candidate webSdk=RuleReplay.Candidate.capture(0,sdkNode,Arrays.asList(webHost,nativeHost),true,700,20,950,80);
        RuleReplay.Frame webSdkFrame=RuleReplay.capture(privatePkg,OLD,1000,2000,true,false,true,Collections.singletonList(webSdk));
        check("generic WebView context rejected by real replay",RuleReplay.evaluate(webSdkFrame).outcome==RuleReplay.Outcome.NO_MATCH);
        check("generic WebView rejection survives file roundtrip",RuleReplay.evaluate(RuleReplay.decode(RuleReplay.encode(webSdkFrame))).outcome==RuleReplay.Outcome.NO_MATCH);
        RuleReplay.Candidate partialSdk=RuleReplay.Candidate.capture(0,sdkNode,Collections.singletonList(nativeHost),false,700,20,950,80);
        check("generic partial ancestry stops whole frame",RuleReplay.evaluate(RuleReplay.capture(privatePkg,OLD,1000,2000,true,false,true,Collections.singletonList(partialSdk))).outcome==RuleReplay.Outcome.INCOMPLETE_SCAN);
        RuleEvaluator.Node alien=node("other.source", "other:id/root","android.widget.FrameLayout","","");
        RuleReplay.Candidate foreignSdk=RuleReplay.Candidate.capture(0,sdkNode,Collections.singletonList(alien),true,700,20,950,80);
        check("generic foreign ancestry scope preserved",RuleReplay.evaluate(RuleReplay.capture(privatePkg,OLD,1000,2000,true,false,true,Collections.singletonList(foreignSdk))).outcome==RuleReplay.Outcome.NO_MATCH);
        RuleReplay.Candidate deceptiveId=RuleReplay.Candidate.capture(0,node(privatePkg,"host:id/not_really_splash_skip_offer","android.widget.Button","",""),Collections.singletonList(nativeHost),true,700,20,950,80);
        RuleReplay.Frame deceptiveFrame=RuleReplay.capture(privatePkg,OLD,1000,2000,true,false,true,Collections.singletonList(deceptiveId));
        check("false SDK substring not promoted in capture",RuleReplay.evaluate(deceptiveFrame).outcome==RuleReplay.Outcome.NO_MATCH);
        check("false SDK substring remains rejected after roundtrip",RuleReplay.evaluate(RuleReplay.decode(RuleReplay.encode(deceptiveFrame))).outcome==RuleReplay.Outcome.NO_MATCH);
        RuleReplay.Candidate secondNative=RuleReplay.Candidate.capture(1,sdkNode,Collections.singletonList(nativeHost),true,700,20,950,80);
        check("two supported generic native identities remain ambiguous",RuleReplay.evaluate(RuleReplay.capture(privatePkg,OLD,1000,2000,true,false,true,Arrays.asList(nativeSdk,secondNative))).outcome==RuleReplay.Outcome.MULTIPLE_CANDIDATES);
        RuleReplay.Candidate unknownContext=RuleReplay.Candidate.capture(1,node(BILI,BILI+":id/count_down","android.widget.TextView","跳过5",""),Collections.singletonList(parent()),false,700,20,950,80);
        check("uncertain later candidate blocks otherwise unique first",RuleReplay.evaluate(f(real,unknownContext)).outcome==RuleReplay.Outcome.INCOMPLETE_SCAN);
        check("uncertain first candidate cannot leave a later unique one",RuleReplay.evaluate(f(unknownContext,real)).outcome==RuleReplay.Outcome.INCOMPLETE_SCAN);
        check("uncertain context sets whole-frame completeness false",!f(real,unknownContext).scanComplete);
        check("replay encoder identifies exact-ID semantics version",json.contains("\"schemaVersion\":2"));
        try{RuleReplay.decode(json.replace("\"schemaVersion\":2","\"schemaVersion\":1"));throw new AssertionError("legacy alias schema accepted");}
        catch(RuleReplay.UnsupportedSchemaException expected){passed++;}
        System.out.println("RuleReplayTest: "+passed+" checks passed");
    }
}
