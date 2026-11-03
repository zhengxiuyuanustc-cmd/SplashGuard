import org.splashguard.app.OfflineRuleStore;
import org.splashguard.app.RuleCatalog;
import org.splashguard.app.RuleJsonCodec;
import org.splashguard.app.RuleUpdateState;
import org.splashguard.app.SignedRulePack;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

/** In-memory ephemeral test keys only. Does not read files, private project keys or Android state. */
public final class RuleUpdateStateTest {
    private static int passed;private static KeyPair pair;
    private static void check(String name,boolean value){if(!value)throw new AssertionError(name);passed++;}
    private static void rejects(String name,Runnable work){try{work.run();throw new AssertionError(name);}catch(IllegalArgumentException expected){passed++;}}
    private static OfflineRuleStore.RuleLimit generic(int window){return new OfflineRuleStore.RuleLimit(RuleCatalog.GENERIC,OfflineRuleStore.SELECTED_APPS,0,Long.MAX_VALUE,window,1,true,false);}
    private static OfflineRuleStore.RuleLimit bili(int window){return new OfflineRuleStore.RuleLimit(RuleCatalog.BILI,RuleCatalog.BILI_PACKAGE,0,Long.MAX_VALUE,window,1,true,false);}
    private static OfflineRuleStore.Pack pack(String id,int version,OfflineRuleStore.RuleLimit...rules){return new OfflineRuleStore.Pack(1,id,version,Arrays.asList(rules));}
    private static String b64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static SignedRulePack.VerifiedPack verified(String publisher,String channel,String kid,OfflineRuleStore.Pack pack)throws Exception{
        String header="{\"alg\":\"RS256\",\"kid\":\""+kid+"\",\"typ\":\""+SignedRulePack.TYPE+"\"}";
        String input=b64(header.getBytes(StandardCharsets.UTF_8))+"."+b64(RuleJsonCodec.encodePack(pack).getBytes(StandardCharsets.UTF_8));
        Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(pair.getPrivate());signer.update(input.getBytes(StandardCharsets.US_ASCII));
        String envelope=input+"."+b64(signer.sign());
        return SignedRulePack.verify(envelope,Arrays.asList(new SignedRulePack.TrustedKey(kid,publisher,channel,Arrays.asList(pack.id),pair.getPublic())));
    }
    private static SignedRulePack.VerifiedPack signed(OfflineRuleStore.Pack pack)throws Exception{return verified("reviewed.publisher","stable","test.key",pack);}
    private static void roundtrip(String name,RuleUpdateState state){String json=state.encode();check(name,json.equals(RuleUpdateState.decode(json).encode()));}
    public static void main(String[] args)throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);pair=generator.generateKeyPair();
        RuleUpdateState empty=RuleUpdateState.empty();String baseline=empty.encode();roundtrip("empty roundtrip",empty);
        check("baseline source",empty.currentSourceDescription().equals("内置规则"));check("baseline no undo",empty.rollback()==empty);check("baseline reset noop",empty.restoreBuiltIns()==empty);
        check("JSON uses nested packs rather than JSON strings",baseline.contains("\"pack\":{}"));
        RuleUpdateState local=empty.applyLocal(pack("local.controls",1,generic(6000)));
        check("input unchanged by local",empty.encode().equals(baseline));check("local narrows",local.store().effective(RuleCatalog.GENERIC).windowMs==6000);
        check("local origin not authenticated",local.currentSourceDescription().contains("未验证"));roundtrip("local roundtrip",local);
        OfflineRuleStore clone=local.store();clone.restoreBuiltIns();check("returned store cannot mutate state",local.store().effective(RuleCatalog.GENERIC).windowMs==6000);
        RuleUpdateState undone=local.rollback();check("undo restores baseline",undone.store().currentPack()==null);check("undo one shot",undone.rollback()==undone);roundtrip("baseline undo roundtrip",undone);
        RuleUpdateState legacy=RuleUpdateState.fromLegacy(local.store().currentPack(),local.store().previousPack(),true);check("legacy marks local",legacy.currentSourceDescription().contains("未验证"));roundtrip("legacy roundtrip",legacy);
        rejects("legacy invalid undo",()->RuleUpdateState.fromLegacy(null,null,true));rejects("null import",()->local.applyLocal(null));rejects("null verified",()->local.previewSigned(null));
        SignedRulePack.VerifiedPack v1=signed(pack("author.stable",1,generic(5000)));
        RuleUpdateState.Plan first=local.previewSigned(v1);RuleUpdateState state=first.next;
        check("signed first not duplicate",!first.alreadyAccepted);check("verified input narrowing",state.store().effective(RuleCatalog.GENERIC).windowMs==5000);check("prior local undo retained",state.rollback().store().effective(RuleCatalog.GENERIC).windowMs==6000);
        check("source limits signature claim",state.currentSourceDescription().contains("仅认证该次输入")&&state.currentSourceDescription().contains("更早"));check("provenance trusted metadata",state.currentSourceDescription().contains("reviewed.publisher / stable"));roundtrip("signed roundtrip",state);
        check("duplicate exact state object",state.previewSigned(v1).next==state&&state.previewSigned(v1).alreadyAccepted);
        check("duplicate preserves undo",state.previewSigned(v1).next.store().canRollback());
        RuleUpdateState localAfter=state.applyLocal(pack("local.next",1,bili(2000)));
        check("same digest noop preserves local effective change",localAfter.previewSigned(v1).next==localAfter&&localAfter.previewSigned(v1).alreadyAccepted);
        RuleUpdateState rollback=state.rollback();check("accepted duplicate after undo stays undone",rollback.previewSigned(v1).next==rollback&&rollback.store().effective(RuleCatalog.GENERIC).windowMs==6000);
        RuleUpdateState reset=state.restoreBuiltIns();check("accepted duplicate after reset stays baseline",reset.previewSigned(v1).next==reset&&reset.store().currentPack()==null);roundtrip("ledger survives reset serialization",reset);
        check("duplicate after reopened reset preserves baseline",RuleUpdateState.decode(reset.encode()).previewSigned(v1).alreadyAccepted);
        SignedRulePack.VerifiedPack conflict=signed(pack("author.stable",1,generic(4000)));
        rejects("same version different digest",()->state.previewSigned(conflict));rejects("same version conflict after reset",()->reset.previewSigned(conflict));
        SignedRulePack.VerifiedPack otherId=signed(pack("author.alternative",1,generic(4000)));
        rejects("pack ID change cannot bypass same-version ledger",()->reset.previewSigned(otherId));
        SignedRulePack.VerifiedPack v2=signed(pack("author.stable",2,generic(4000)));
        RuleUpdateState poisoned=empty.applyLocal(pack("author.stable",999999,generic(5000)));
        RuleUpdateState recovered=poisoned.previewSigned(v1).next;
        check("untrusted same-ID revision cannot poison first trusted revision",recovered.store().currentPack().version==1);
        roundtrip("trusted lower-than-local revision restores",recovered);
        check("undo restores high local snapshot without erasing trusted ledger",recovered.rollback().store().currentPack().version==999999&&recovered.rollback().previewSigned(v1).alreadyAccepted);
        RuleUpdateState poisonedAfter=state.applyLocal(pack("author.stable",999999,bili(1000)));
        RuleUpdateState advancedAfter=poisonedAfter.previewSigned(v2).next;
        check("local revision cannot poison later trusted channel revision",advancedAfter.store().currentPack().version==2&&advancedAfter.store().effective(RuleCatalog.GENERIC).windowMs==4000&&advancedAfter.store().effective(RuleCatalog.BILI).windowMs==1000);
        roundtrip("post-local trusted revision persists",advancedAfter);
        SignedRulePack.VerifiedPack poisonousLoosening=signed(pack("author.stable",2,generic(6000)));
        rejects("revision bypass never bypasses current tightening",()->poisonedAfter.previewSigned(poisonousLoosening));
        RuleUpdateState next=state.previewSigned(v2).next;check("higher version progresses",next.store().effective(RuleCatalog.GENERIC).windowMs==4000);roundtrip("higher version roundtrip",next);
        rejects("lower version blocked",()->next.previewSigned(v1));rejects("lower version remains blocked after reset",()->next.restoreBuiltIns().previewSigned(v1));rejects("lower version remains blocked after undo",()->next.rollback().previewSigned(v1));
        SignedRulePack.VerifiedPack lowerAlias=signed(pack("author.alternative",1,generic(3000)));
        rejects("local ID switch cannot remove ledger",()->next.applyLocal(pack("local.switch",1,bili(1000))).previewSigned(lowerAlias));
        SignedRulePack.VerifiedPack loosen=signed(pack("author.stable",3,generic(8000)));String before=next.encode();
        rejects("higher version cannot loosen",()->next.previewSigned(loosen));check("failed higher input does not mutate",before.equals(next.encode()));
        SignedRulePack.VerifiedPack validSameNext=signed(pack("author.stable",3,generic(3000)));
        check("failed higher import did not advance watermark",!next.previewSigned(validSameNext).alreadyAccepted);
        SignedRulePack.VerifiedPack rotation=verified("reviewed.publisher","stable","rotated.key",pack("author.stable",2,generic(4000)));
        check("same payload alternate trusted key is noop",next.previewSigned(rotation).next==next);
        SignedRulePack.VerifiedPack rotatedHigher=verified("reviewed.publisher","stable","rotated.key",pack("author.stable",3,generic(3000)));
        RuleUpdateState rotated=next.previewSigned(rotatedHigher).next;roundtrip("rotated ledger roundtrip",rotated);check("rotated provenance",rotated.currentSourceDescription().contains("rotated.key"));
        SignedRulePack.VerifiedPack channel=verified("reviewed.publisher","beta","beta.key",pack("author.beta",1,bili(1000)));
        RuleUpdateState two=next.previewSigned(channel).next;check("channels advance independently",two.store().effective(RuleCatalog.BILI).windowMs==1000);roundtrip("two channel roundtrip",two);
        SignedRulePack.VerifiedPack publisher=verified("other.publisher","stable","other.key",pack("author.other",1,bili(1000)));
        roundtrip("publishers independent",next.previewSigned(publisher).next);
        RuleUpdateState many=empty;for(int i=0;i<16;i++)many=many.previewSigned(verified("publisher"+i,"stable","test.key",pack("author"+i,1,generic(5000)))).next;
        roundtrip("maximum ledger roundtrip",many);final RuleUpdateState full=many;
        SignedRulePack.VerifiedPack seventeenth=verified("publisher16","stable","test.key",pack("author16",1,generic(5000)));
        rejects("new channel over cap",()->full.previewSigned(seventeenth));check("ledger preserved at cap after reset",full.restoreBuiltIns().encode().contains("publisher15"));
        SignedRulePack.VerifiedPack existingHigher=verified("publisher0","stable","test.key",pack("author0",2,generic(4000)));
        check("existing channel can advance at cap",!full.previewSigned(existingHigher).alreadyAccepted);
        String encoded=state.encode();
        rejects("unknown root field",()->RuleUpdateState.decode(encoded.replace("\"schemaVersion\":1,","\"extra\":1,\"schemaVersion\":1,")));
        rejects("duplicate root key",()->RuleUpdateState.decode(encoded.replace("\"schemaVersion\":1,","\"schemaVersion\":1,\"schemaVersion\":1,")));
        rejects("unknown schema",()->RuleUpdateState.decode(encoded.replaceFirst("\"schemaVersion\":1","\"schemaVersion\":2")));
        rejects("undo boolean required",()->RuleUpdateState.decode(encoded.replace("\"undo\":true","\"undo\":1")));
        rejects("origin input version consistency",()->RuleUpdateState.decode(encoded.replace("\"inputVersion\":1","\"inputVersion\":2")));
        rejects("origin input ID consistency",()->RuleUpdateState.decode(encoded.replace("\"inputPackId\":\"author.stable\"","\"inputPackId\":\"author.other\"")));
        rejects("origin ledger channel consistency",()->RuleUpdateState.decode(encoded.replaceFirst("\"channel\":\"stable\"","\"channel\":\"different\"")));
        rejects("origin ledger digest consistency",()->RuleUpdateState.decode(encoded.replaceFirst(v1.payloadSha256,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
        rejects("origin ledger key consistency",()->RuleUpdateState.decode(encoded.replaceFirst("\"kid\":\"test.key\"","\"kid\":\"another.key\"")));
        rejects("ledger version cannot be behind origin",()->RuleUpdateState.decode(encoded.replace("\"highestVersion\":1","\"highestVersion\":0")));
        rejects("hash uppercase rejected",()->RuleUpdateState.decode(encoded.replace(v1.payloadSha256,v1.payloadSha256.toUpperCase(java.util.Locale.ROOT))));
        rejects("unsafe publisher syntax",()->RuleUpdateState.decode(encoded.replace("reviewed.publisher","reviewed/publisher")));
        rejects("baseline cannot claim local snapshot",()->RuleUpdateState.decode(local.encode().replace("\"kind\":\"local\"","\"kind\":\"baseline\"")));
        rejects("no undo cannot retain previous",()->RuleUpdateState.decode(state.encode().replace("\"undo\":true","\"undo\":false")));
        rejects("local provenance unknown fields",()->RuleUpdateState.decode(local.encode().replace("\"kind\":\"local\"","\"kind\":\"local\",\"publisher\":\"someone\"")));
        rejects("ledger array required",()->RuleUpdateState.decode(baseline.replace("\"ledger\":[]","\"ledger\":{}")));
        rejects("nested pack object required",()->RuleUpdateState.decode(baseline.replace("\"pack\":{}","\"pack\":\"{}\"")));
        String record=encoded.substring(encoded.indexOf("\"ledger\":[")+10,encoded.length()-2);
        rejects("duplicate channel ledger rejected",()->RuleUpdateState.decode(encoded.substring(0,encoded.length()-2)+","+record+"]}"));
        String fullJson=full.encode();
        rejects("decoded ledger capped at sixteen",()->RuleUpdateState.decode(fullJson.substring(0,fullJson.length()-2)+","+record+"]}"));
        rejects("signed origin requires retained watermark",()->RuleUpdateState.decode(encoded.substring(0,encoded.indexOf("\"ledger\":["))+"\"ledger\":[]}"));
        check("all failures leave state unchanged",state.encode().equals(encoded));
        System.out.println("RuleUpdateStateTest: "+passed+" checks passed");
    }
}
