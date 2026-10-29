package org.splashguard.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, sanitized matcher fixtures. Re-evaluates rules; never executes Android actions. */
public final class RuleReplay {
    private RuleReplay(){}
    public static final int MAX_CANDIDATES=64, MAX_SIZE=8192, SCHEMA_VERSION=2;
    /** Schema 1 SDK_SKIP included substring IDs; its category cannot prove the new exact-ID boundary. */
    public static final class UnsupportedSchemaException extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        private UnsupportedSchemaException(){super("回放格式已更新，请使用新版重新准备样本；不能只修改旧文件的版本号。");}
    }
    /** Bound diagnostic work before normalization; it does not truncate live matching inputs. */
    public static final int MAX_SOURCE_LABEL_CHARS=1024;
    public enum Target { BILI,ZHIHU,OTHER_SELECTED }
    public enum VersionName { BILI9150,BILI9161,OTHER,UNKNOWN }
    public enum Scope { SAME,OTHER }
    public enum Id { NONE,BILI_COUNT_DOWN,BILI_COUNTDOWN_TITLE,BILI_LOGO_LAYOUT,BILI_ROOT_CONTAINER,BILI_INTERACT,BILI_CONTAINER,BILI_LAYOUT,BILI_BUTTON_CONTAINER,BILI_FULL,SDK_SKIP,OTHER }
    public enum Kind { TEXT_VIEW,BUTTON,FRAME_LAYOUT,VIEW_GROUP,WEB_VIEW,BILI_WIDGET,OTHER }
    public enum Outcome { INCOMPLETE_SCAN,MULTIPLE_CANDIDATES,NO_MATCH,UNIQUE_RULE_MATCH }
    private static final String SAMPLE_PACKAGE="org.splashguard.selected",OTHER_PACKAGE="org.splashguard.other";
    private static Set<String> keys(String... names){return new LinkedHashSet<>(Arrays.asList(names));}

    private static final class Value {
        final Scope scope;final Id id;final Kind kind;final String text,description;final boolean visible,enabled,clickable;
        final String sourcePackage;final boolean sourceRepresented;
        Value(Scope scope,Id id,Kind kind,String text,String description,boolean visible,boolean enabled,boolean clickable,String sourcePackage,boolean sourceRepresented){
            if(scope==null||id==null||kind==null||!validLabel(text)||!validLabel(description))throw invalid();
            this.scope=scope;this.id=id;this.kind=kind;this.text=text;this.description=description;this.visible=visible;this.enabled=enabled;this.clickable=clickable;this.sourcePackage=sourcePackage;this.sourceRepresented=sourceRepresented;
        }
        static Value capture(RuleEvaluator.Node node){if(node==null)throw invalid();return new Value(Scope.OTHER,id(node.viewId),kind(node.className),label(node.text),label(node.description),node.visible,node.enabled,node.clickable,node.packageName,boundedLabel(node.text)&&boundedLabel(node.description));}
        Value bind(String pkg){return new Value(sourcePackage==null?scope:pkg.equals(sourcePackage)?Scope.SAME:Scope.OTHER,id,kind,text,description,visible,enabled,clickable,null,sourceRepresented);}
        RuleEvaluator.Node node(String pkg){return new RuleEvaluator.Node(scope==Scope.SAME?pkg:OTHER_PACKAGE,idText(id),classText(kind),labelText(text),labelText(description),visible,enabled,clickable);}
        Map<String,Object> fields(){Map<String,Object> m=new LinkedHashMap<>();m.put("scope",scope.name());m.put("id",id.name());m.put("kind",kind.name());m.put("text",text);m.put("description",description);m.put("visible",visible);m.put("enabled",enabled);m.put("clickable",clickable);return m;}
    }
    public static final class Candidate {
        public final int identity,left,top,right,bottom;public final boolean ancestorsComplete;
        private final Value node;private final List<Value> ancestors;
        private Candidate(int identity,Value node,List<Value> ancestors,boolean complete,int left,int top,int right,int bottom){
            if(identity<0||identity>=MAX_CANDIDATES||node==null||ancestors==null||ancestors.size()>RuleSpec.MAX_ANCESTORS)throw invalid();
            for(int n:new int[]{left,top,right,bottom})if(n< -2*MAX_SIZE||n>2*MAX_SIZE)throw invalid();
            this.identity=identity;this.node=node;this.ancestors=Collections.unmodifiableList(new ArrayList<>(ancestors));this.ancestorsComplete=complete;this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
        public static Candidate capture(int identity,RuleEvaluator.Node node,List<RuleEvaluator.Node> parents,boolean complete,int left,int top,int right,int bottom){
            if(parents==null)throw invalid();List<Value> list=new ArrayList<>();for(RuleEvaluator.Node parent:parents){if(list.size()>=RuleSpec.MAX_ANCESTORS){complete=false;break;}list.add(Value.capture(parent));}
            return new Candidate(identity,Value.capture(node),list,complete,left,top,right,bottom);
        }
        private Candidate bind(String pkg){List<Value> bound=new ArrayList<>();for(Value value:ancestors)bound.add(value.bind(pkg));return new Candidate(identity,node.bind(pkg),bound,ancestorsComplete,left,top,right,bottom);}
        private boolean sourceRepresented(){if(!node.sourceRepresented)return false;for(Value value:ancestors)if(!value.sourceRepresented)return false;return true;}
        private Map<String,Object> fields(){Map<String,Object> m=new LinkedHashMap<>();m.put("identity",identity);m.put("node",node.fields());List<Object> parents=new ArrayList<>();for(Value v:ancestors)parents.add(v.fields());m.put("ancestors",parents);m.put("ancestorsComplete",ancestorsComplete);m.put("bounds",Arrays.asList(left,top,right,bottom));return m;}
    }
    public static final class Frame {
        public final Target packageCategory;public final long versionCode;public final VersionName versionName;
        public final int width,height;public final boolean evidence,biliSplash,scanComplete;public final List<Candidate> candidates;
        private Frame(Target target,long code,VersionName name,int width,int height,boolean evidence,boolean biliSplash,boolean scanComplete,List<Candidate> candidates){
            if(target==null||name==null||code< -1||width<1||width>MAX_SIZE||height<1||height>MAX_SIZE||candidates==null||candidates.size()>MAX_CANDIDATES)throw invalid();
            this.packageCategory=target;versionCode=code;versionName=name;this.width=width;this.height=height;this.evidence=evidence;this.biliSplash=biliSplash;
            boolean complete=scanComplete;List<Candidate> safe=new ArrayList<>();Map<Integer,Map<String,Object>> aliases=new HashMap<>();
            for(Candidate item:candidates){if(item==null)throw invalid();Candidate bound=item.bind(packageText(target));if(!bound.sourceRepresented()||!bound.ancestorsComplete)complete=false;Map<String,Object> fields=bound.fields();Map<String,Object> before=aliases.put(bound.identity,fields);if(before!=null&&!before.equals(fields))throw invalid();safe.add(bound);}
            this.scanComplete=complete;this.candidates=Collections.unmodifiableList(safe);
        }
    }
    public static Frame capture(String pkg,RuleEvaluator.VersionInfo version,int width,int height,boolean evidence,boolean biliSplash,boolean scanComplete,List<Candidate> candidates){
        if(pkg==null||pkg.isEmpty()||version==null)throw invalid();Target target=RuleCatalog.BILI_PACKAGE.equals(pkg)?Target.BILI:"com.zhihu.android".equals(pkg)?Target.ZHIHU:Target.OTHER_SELECTED;
        // Bind against the real package before discarding it and replacing it with the fixed category.
        List<Candidate> bound=new ArrayList<>();if(candidates==null)throw invalid();for(Candidate c:candidates)bound.add(c.bind(pkg));
        return new Frame(target,version.code,version.name.isEmpty()?VersionName.UNKNOWN:"9.15.0".equals(version.name)?VersionName.BILI9150:"9.16.1".equals(version.name)?VersionName.BILI9161:VersionName.OTHER,width,height,evidence,biliSplash,scanComplete,bound);
    }
    public static final class Match {
        public final int identity;public final boolean matched;public final String ruleId;public final RuleEvaluator.Reason reason;
        Match(int identity,boolean matched,String ruleId,RuleEvaluator.Reason reason){this.identity=identity;this.matched=matched;this.ruleId=ruleId;this.reason=reason;}
    }
    public static final class Result {
        public final Outcome outcome;public final int candidateCount,matchedIdentities,uniqueIdentity;public final String ruleId;public final boolean versionVerified;public final List<Match> matches;
        Result(Outcome outcome,int count,int identities,int unique,String ruleId,boolean verified,List<Match> matches){this.outcome=outcome;candidateCount=count;matchedIdentities=identities;uniqueIdentity=unique;this.ruleId=ruleId;versionVerified=verified;this.matches=Collections.unmodifiableList(new ArrayList<>(matches));}
    }
    public static Result evaluate(Frame frame){
        if(frame==null)throw invalid();List<Match> results=new ArrayList<>();if(!frame.scanComplete)return new Result(Outcome.INCOMPLETE_SCAN,frame.candidates.size(),0,-1,"none",false,results);
        String pkg=packageText(frame.packageCategory);RuleEvaluator.VersionInfo version=new RuleEvaluator.VersionInfo(frame.versionCode,frame.versionName==VersionName.BILI9150?"9.15.0":frame.versionName==VersionName.BILI9161?"9.16.1":frame.versionName==VersionName.OTHER?"unverified":"");
        Set<Integer> identities=new LinkedHashSet<>();CandidateSelection<Integer> selection=new CandidateSelection<>();RuleCatalog.Evaluation unique=null;
        for(Candidate c:frame.candidates){
            RuleEvaluator.Node node=c.node.node(pkg);
            if(c.node.scope!=Scope.SAME){results.add(new Match(c.identity,false,"none",RuleEvaluator.Reason.WRONG_PACKAGE));continue;}
            List<RuleEvaluator.Node> parents=new ArrayList<>();for(Value v:c.ancestors)parents.add(v.node(pkg));
            boolean bili=RuleCatalog.biliGeometryHit(version,node,parents,c.ancestorsComplete,frame.evidence,frame.biliSplash,c.left,c.top,c.right,c.bottom,frame.width,frame.height);
            boolean generic=AdRules.candidate(node.text,node.viewId,frame.evidence,c.left,c.top,c.right,c.bottom,frame.width,frame.height)||AdRules.candidate(node.description,node.viewId,frame.evidence,c.left,c.top,c.right,c.bottom,frame.width,frame.height);
            RuleCatalog.Evaluation evaluation=RuleCatalog.evaluate(version,node,parents,c.ancestorsComplete,frame.evidence,bili,generic);
            results.add(new Match(c.identity,evaluation.matched,evaluation.ruleId,evaluation.reason));
            if(evaluation.matched){selection.offer(c.identity);identities.add(c.identity);if(unique==null)unique=evaluation;}
        }
        if(selection.ambiguous())return new Result(Outcome.MULTIPLE_CANDIDATES,frame.candidates.size(),identities.size(),-1,"none",false,results);
        Integer identity=selection.unique();return new Result(identity==null?Outcome.NO_MATCH:Outcome.UNIQUE_RULE_MATCH,frame.candidates.size(),identities.size(),identity==null?-1:identity,identity==null?"none":unique.ruleId,identity!=null&&unique.result.versionVerified,results);
    }
    public static String encode(Frame frame){if(frame==null)throw invalid();Map<String,Object> m=new LinkedHashMap<>();m.put("schemaVersion",SCHEMA_VERSION);m.put("packageCategory",frame.packageCategory.name());m.put("versionCode",frame.versionCode);m.put("versionName",frame.versionName.name());m.put("width",frame.width);m.put("height",frame.height);m.put("evidence",frame.evidence);m.put("biliSplash",frame.biliSplash);m.put("scanComplete",frame.scanComplete);List<Object> items=new ArrayList<>();for(Candidate c:frame.candidates)items.add(c.fields());m.put("candidates",items);return RuleJsonCodec.writeObject(m);}
    public static Frame decode(String json){Map<String,Object> m=RuleJsonCodec.readObject(json);exact(m,"schemaVersion","packageCategory","versionCode","versionName","width","height","evidence","biliSplash","scanComplete","candidates");if(integer(m.get("schemaVersion"))!=SCHEMA_VERSION)throw new UnsupportedSchemaException();List<Candidate> candidates=new ArrayList<>();
        for(Object item:list(m.get("candidates"))){Map<String,Object> c=object(item);exact(c,"identity","node","ancestors","ancestorsComplete","bounds");List<Value> parents=new ArrayList<>();for(Object p:list(c.get("ancestors")))parents.add(value(object(p)));List<?> b=list(c.get("bounds"));if(b.size()!=4)throw invalid();candidates.add(new Candidate(number(c.get("identity")),value(object(c.get("node"))),parents,bool(c.get("ancestorsComplete")),number(b.get(0)),number(b.get(1)),number(b.get(2)),number(b.get(3))));}
        return new Frame(token(Target.class,m.get("packageCategory")),integer(m.get("versionCode")),token(VersionName.class,m.get("versionName")),number(m.get("width")),number(m.get("height")),bool(m.get("evidence")),bool(m.get("biliSplash")),bool(m.get("scanComplete")),candidates);
    }
    private static Value value(Map<String,Object> m){exact(m,"scope","id","kind","text","description","visible","enabled","clickable");return new Value(token(Scope.class,m.get("scope")),token(Id.class,m.get("id")),token(Kind.class,m.get("kind")),string(m.get("text")),string(m.get("description")),bool(m.get("visible")),bool(m.get("enabled")),bool(m.get("clickable")),null,true);}
    private static boolean knownLabel(String s){return s.length()<=32&&(AdRules.candidate(s,"",true,700,20,950,80,1000,2000)||AdRules.biliCandidate(RuleCatalog.BILI_PACKAGE,s,RuleCatalog.BILI_PACKAGE+":id/count_down",true,700,20,950,80,1000,2000));}
    private static boolean validLabel(String s){return s!=null&&(s.equals("@none")||s.equals("@other")||s.equals("@unsafe")||(s.equals(AdRules.normalize(s))&&knownLabel(s)));}
    private static boolean boundedLabel(String raw){return raw==null||raw.length()<=MAX_SOURCE_LABEL_CHARS;}
    private static String label(String raw){if(!boundedLabel(raw))return "@unsafe";String s=AdRules.normalize(raw);if(s.isEmpty())return "@none";RuleEvaluator.Node test=new RuleEvaluator.Node(SAMPLE_PACKAGE,"","",s,"",true,true,true);if(RuleEvaluator.genericCandidate(true,test).reason==RuleEvaluator.Reason.UNSAFE_LABEL)return "@unsafe";return knownLabel(s)?s:"@other";}
    private static String labelText(String token){return token.equals("@none")?"":token.equals("@unsafe")?"安装":token.equals("@other")?"other":token;}
    private static Id id(String raw){String s=raw==null?"":raw;if(s.isEmpty())return Id.NONE;for(Id id:Id.values())if(id!=Id.SDK_SKIP&&id!=Id.OTHER&&id!=Id.NONE&&s.equals(idText(id)))return id;return AdRules.candidate("",s,true,700,20,950,80,1000,2000)?Id.SDK_SKIP:Id.OTHER;}
    private static String idText(Id id){switch(id){case BILI_COUNT_DOWN:return RuleCatalog.BILI_PACKAGE+":id/count_down";case BILI_COUNTDOWN_TITLE:return RuleCatalog.BILI_PACKAGE+":id/countdown_title";case BILI_LOGO_LAYOUT:return RuleCatalog.BILI_PACKAGE+":id/logo_layout";case BILI_ROOT_CONTAINER:return RuleCatalog.BILI_PACKAGE+":id/root_container";case BILI_INTERACT:return RuleCatalog.BILI_PACKAGE+":id/splash_interact_view";case BILI_CONTAINER:return RuleCatalog.BILI_PACKAGE+":id/splash_container";case BILI_LAYOUT:return RuleCatalog.BILI_PACKAGE+":id/splash_layout";case BILI_BUTTON_CONTAINER:return RuleCatalog.BILI_PACKAGE+":id/splash_button_container";case BILI_FULL:return RuleCatalog.BILI_PACKAGE+":id/full_brand_splash";case SDK_SKIP:return "sdk:id/tt_splash_skip_btn";case OTHER:return "other";default:return "";}}
    private static Kind kind(String raw){if(raw==null)return Kind.OTHER;for(Kind k:Kind.values())if(k!=Kind.OTHER&&raw.equals(classText(k)))return k;return raw.startsWith("tv.danmaku.bili.splash.ad.widget.")?Kind.BILI_WIDGET:Kind.OTHER;}
    private static String classText(Kind k){switch(k){case TEXT_VIEW:return "android.widget.TextView";case BUTTON:return "android.widget.Button";case FRAME_LAYOUT:return "android.widget.FrameLayout";case VIEW_GROUP:return "android.view.ViewGroup";case WEB_VIEW:return "android.webkit.WebView";case BILI_WIDGET:return "tv.danmaku.bili.splash.ad.widget.Replay";default:return "android.view.View";}}
    private static String packageText(Target target){return target==Target.BILI?RuleCatalog.BILI_PACKAGE:target==Target.ZHIHU?"com.zhihu.android":SAMPLE_PACKAGE;}
    private static void exact(Map<String,Object> map,String... names){if(!map.keySet().equals(keys(names)))throw invalid();}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){if(!(value instanceof Map))throw invalid();return (Map<String,Object>)value;}
    private static List<?> list(Object value){if(!(value instanceof List))throw invalid();return (List<?>)value;}
    private static String string(Object value){if(!(value instanceof String))throw invalid();return (String)value;}
    private static long integer(Object value){if(!(value instanceof Long))throw invalid();return (Long)value;}
    private static int number(Object value){long n=integer(value);if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw invalid();return (int)n;}
    private static boolean bool(Object value){if(!(value instanceof Boolean))throw invalid();return (Boolean)value;}
    private static <T extends Enum<T>> T token(Class<T> type,Object value){try{return Enum.valueOf(type,string(value));}catch(RuntimeException invalid){throw invalid();}}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid sanitized rule replay fixture");}
}
