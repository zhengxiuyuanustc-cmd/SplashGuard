package org.splashguard.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Immutable local rule snapshots and independent accepted-input high-water marks. No IO or keys. */
public final class RuleUpdateState {
    public static final int SCHEMA_VERSION=1, MAX_CHANNELS=16;
    private final OfflineRuleStore.Pack current,previous;
    private final Origin currentOrigin,previousOrigin;
    private final boolean undo;
    private final Map<String,Watermark> ledger;
    private static final class Origin {
        final String kind,publisher,channel,kid,digest,inputId;final int version;
        Origin(String kind,String publisher,String channel,String kid,String digest,String inputId,int version){
            if(!kind.equals("baseline")&&!kind.equals("local")&&!kind.equals("signed-input"))throw invalid();
            this.kind=kind;this.publisher=publisher;this.channel=channel;this.kid=kid;this.digest=digest;this.inputId=inputId;this.version=version;
            if(kind.equals("signed-input")){id(publisher);id(channel);id(kid);hash(digest);id(inputId);version(version);}
        }
        static Origin baseline(){return new Origin("baseline","","","","","",0);}
        static Origin local(){return new Origin("local","","","","","",0);}
        static Origin signed(SignedRulePack.VerifiedPack v){return new Origin("signed-input",v.publisher,v.channel,v.kid,v.payloadSha256,v.pack.id,v.pack.version);}
        Map<String,Object> fields(){Map<String,Object> m=new LinkedHashMap<>();m.put("kind",kind);if(kind.equals("signed-input")){m.put("publisher",publisher);m.put("channel",channel);m.put("kid",kid);m.put("payloadSha256",digest);m.put("inputPackId",inputId);m.put("inputVersion",version);}return m;}
    }
    private static final class Watermark {
        final String publisher,channel,kid,digest;final int version;
        Watermark(String p,String c,String k,String d,int v){publisher=id(p);channel=id(c);kid=id(k);digest=hash(d);version=version(v);}
        String key(){return publisher+"/"+channel;}
        Map<String,Object> fields(){Map<String,Object> m=new LinkedHashMap<>();m.put("publisher",publisher);m.put("channel",channel);m.put("highestVersion",version);m.put("payloadSha256",digest);m.put("kid",kid);return m;}
    }
    private RuleUpdateState(OfflineRuleStore.Pack current,Origin currentOrigin,OfflineRuleStore.Pack previous,Origin previousOrigin,boolean undo,Map<String,Watermark> ledger){
        if(currentOrigin==null||previousOrigin==null||ledger==null||ledger.size()>MAX_CHANNELS)throw invalid();
        OfflineRuleStore check=new OfflineRuleStore(RuleCatalog.builtInLimits());check.restoreValidatedState(current,previous,undo);
        if(!undo&&(previous!=null||!previousOrigin.kind.equals("baseline")))throw invalid();
        this.current=current;this.previous=previous;this.currentOrigin=currentOrigin;this.previousOrigin=previousOrigin;this.undo=undo;
        LinkedHashMap<String,Watermark> copy=new LinkedHashMap<>();for(Map.Entry<String,Watermark> e:ledger.entrySet()){if(e.getValue()==null||!e.getKey().equals(e.getValue().key())||copy.put(e.getKey(),e.getValue())!=null)throw invalid();}
        this.ledger=Collections.unmodifiableMap(copy);validateOrigin(current,currentOrigin);validateOrigin(previous,previousOrigin);
    }
    public static RuleUpdateState empty(){return new RuleUpdateState(null,Origin.baseline(),null,Origin.baseline(),false,Collections.emptyMap());}
    /** Legacy snapshots have no authenticated origin and create no accepted-publisher ledger. */
    public static RuleUpdateState fromLegacy(OfflineRuleStore.Pack current,OfflineRuleStore.Pack previous,boolean undo){return new RuleUpdateState(current,current==null?Origin.baseline():Origin.local(),previous,previous==null?Origin.baseline():Origin.local(),undo,Collections.emptyMap());}
    /** Always return an isolated mutable store; callers cannot mutate this state. */
    public OfflineRuleStore store(){OfflineRuleStore result=new OfflineRuleStore(RuleCatalog.builtInLimits());result.restoreValidatedState(current,previous,undo);return result;}
    public RuleUpdateState applyLocal(OfflineRuleStore.Pack pack){OfflineRuleStore next=store();next.apply(pack);return new RuleUpdateState(next.currentPack(),Origin.local(),current,currentOrigin,true,ledger);}
    public static final class Plan {
        public final RuleUpdateState next;public final boolean alreadyAccepted;
        private Plan(RuleUpdateState next,boolean accepted){this.next=next;alreadyAccepted=accepted;}
    }
    /** Only the verifier can construct VerifiedPack. The ledger tracks its exact input, not merged output. */
    public Plan previewSigned(SignedRulePack.VerifiedPack verified){
        if(verified==null||verified.pack==null)throw invalid();Origin origin=Origin.signed(verified);
        String key=origin.publisher+"/"+origin.channel;Watermark before=ledger.get(key);
        if(before!=null){if(verified.pack.version<before.version)throw invalid();if(verified.pack.version==before.version){if(!origin.digest.equals(before.digest))throw invalid();return new Plan(this,true);}}
        if(before==null&&ledger.size()>=MAX_CHANNELS)throw invalid();
        // Validate tightening on an isolated copy before advancing any watermark.
        OfflineRuleStore next=store();next.applyAuthenticated(verified.pack);Map<String,Watermark> records=new LinkedHashMap<>(ledger);
        records.put(key,new Watermark(origin.publisher,origin.channel,origin.kid,origin.digest,origin.version));
        return new Plan(new RuleUpdateState(next.currentPack(),origin,current,currentOrigin,true,records),false);
    }
    public RuleUpdateState rollback(){if(!undo)return this;return new RuleUpdateState(previous,previousOrigin,null,Origin.baseline(),false,ledger);}
    public RuleUpdateState restoreBuiltIns(){if(current==null&&!undo)return this;return new RuleUpdateState(null,Origin.baseline(),null,Origin.baseline(),false,ledger);}
    /** Describes only the last applied input; a canonical snapshot can combine earlier restrictions. */
    public String currentSourceDescription(){if(currentOrigin.kind.equals("baseline"))return "内置规则";if(currentOrigin.kind.equals("local"))return "本地限制；未验证发布者签名";return "最近输入来自已验证发布者 "+currentOrigin.publisher+" / "+currentOrigin.channel+"（版本 "+currentOrigin.version+"，密钥 "+currentOrigin.kid+"）；签名仅认证该次输入，当前限制可能合并更早的设置";}
    public String encode(){Map<String,Object> m=new LinkedHashMap<>();m.put("schemaVersion",SCHEMA_VERSION);m.put("current",snapshot(current,currentOrigin));m.put("previous",snapshot(previous,previousOrigin));m.put("undo",undo);List<Object> records=new ArrayList<>();for(Watermark w:ledger.values())records.add(w.fields());m.put("ledger",records);return RuleJsonCodec.writeObject(m);}
    public static RuleUpdateState decode(String json){Map<String,Object> m=RuleJsonCodec.readObject(json);exact(m,"schemaVersion","current","previous","undo","ledger");if(integer(m.get("schemaVersion"))!=SCHEMA_VERSION)throw invalid();
        Map<String,Object> c=object(m.get("current")),p=object(m.get("previous"));exact(c,"pack","origin");exact(p,"pack","origin");Map<String,Watermark> ledger=new LinkedHashMap<>();List<?> records=list(m.get("ledger"));if(records.size()>MAX_CHANNELS)throw invalid();
        for(Object record:records){Map<String,Object> r=object(record);exact(r,"publisher","channel","highestVersion","payloadSha256","kid");Watermark w=new Watermark(string(r.get("publisher")),string(r.get("channel")),string(r.get("kid")),string(r.get("payloadSha256")),number(r.get("highestVersion")));if(ledger.put(w.key(),w)!=null)throw invalid();}
        return new RuleUpdateState(pack(c.get("pack")),origin(c.get("origin")),pack(p.get("pack")),origin(p.get("origin")),bool(m.get("undo")),ledger);
    }
    private void validateOrigin(OfflineRuleStore.Pack pack,Origin origin){
        if(pack==null){if(!origin.kind.equals("baseline"))throw invalid();return;}
        if(origin.kind.equals("baseline"))throw invalid();
        if(origin.kind.equals("signed-input")){if(!pack.id.equals(origin.inputId)||pack.version!=origin.version)throw invalid();Watermark w=ledger.get(origin.publisher+"/"+origin.channel);if(w==null||w.version<origin.version)throw invalid();if(w.version==origin.version&&(!w.digest.equals(origin.digest)||!w.kid.equals(origin.kid)))throw invalid();}
    }
    private static Map<String,Object> snapshot(OfflineRuleStore.Pack pack,Origin origin){Map<String,Object> m=new LinkedHashMap<>();m.put("pack",pack==null?new LinkedHashMap<String,Object>():RuleJsonCodec.readObject(RuleJsonCodec.encodePack(pack)));m.put("origin",origin.fields());return m;}
    private static OfflineRuleStore.Pack pack(Object value){Map<String,Object> m=object(value);return m.isEmpty()?null:RuleJsonCodec.decodePack(RuleJsonCodec.writeObject(m));}
    private static Origin origin(Object value){Map<String,Object> m=object(value);String k=string(m.get("kind"));if(k.equals("signed-input")){exact(m,"kind","publisher","channel","kid","payloadSha256","inputPackId","inputVersion");return new Origin(k,string(m.get("publisher")),string(m.get("channel")),string(m.get("kid")),string(m.get("payloadSha256")),string(m.get("inputPackId")),number(m.get("inputVersion")));}exact(m,"kind");if(k.equals("baseline"))return Origin.baseline();if(k.equals("local"))return Origin.local();throw invalid();}
    private static String id(String s){if(s==null||s.length()>80||!s.matches("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*"))throw invalid();return s;}
    private static String hash(String s){if(s==null||!s.matches("[0-9a-f]{64}"))throw invalid();return s;}
    private static int version(int v){if(v<1||v>OfflineRuleStore.MAX_PACK_VERSION)throw invalid();return v;}
    private static void exact(Map<String,Object> m,String... names){if(!m.keySet().equals(new LinkedHashSet<>(Arrays.asList(names))))throw invalid();}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object v){if(!(v instanceof Map))throw invalid();return (Map<String,Object>)v;}
    private static List<?> list(Object v){if(!(v instanceof List))throw invalid();return (List<?>)v;}
    private static String string(Object v){if(!(v instanceof String))throw invalid();return (String)v;}
    private static long integer(Object v){if(!(v instanceof Long))throw invalid();return (Long)v;}
    private static int number(Object v){long n=integer(v);if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw invalid();return (int)n;}
    private static boolean bool(Object v){if(!(v instanceof Boolean))throw invalid();return (Boolean)v;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("离线规则更新状态无法验证；未修改现有规则");}
}
