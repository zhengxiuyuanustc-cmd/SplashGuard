import org.splashguard.app.OfflineRuleStore;
import org.splashguard.app.RuleCatalog;
import org.splashguard.app.RuleJsonCodec;
import org.splashguard.app.RuleStateStorage;
import org.splashguard.app.RuleUpdateState;
import org.splashguard.app.SignedRulePack;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

/** Fake transactions model memory-before-disk failure and complete old/new restart snapshots. */
public final class RuleStateStorageTest {
    private static int passed;private static KeyPair pair;
    private static void check(String name,boolean value){if(!value)throw new AssertionError(name);passed++;}
    private static void rejects(String name,Runnable action){try{action.run();throw new AssertionError(name);}catch(IllegalStateException expected){check(name+" fixed error",!expected.getMessage().contains("private-provider"));}}
    private static OfflineRuleStore.RuleLimit generic(int window){return new OfflineRuleStore.RuleLimit(RuleCatalog.GENERIC,OfflineRuleStore.SELECTED_APPS,0,Long.MAX_VALUE,window,1,true,false);}
    private static OfflineRuleStore.RuleLimit bili(int window){return new OfflineRuleStore.RuleLimit(RuleCatalog.BILI,RuleCatalog.BILI_PACKAGE,0,Long.MAX_VALUE,window,1,true,false);}
    private static OfflineRuleStore.Pack pack(String id,int version,OfflineRuleStore.RuleLimit...rules){return new OfflineRuleStore.Pack(1,id,version,Arrays.asList(rules));}
    private static RuleStateStorage.Record record(String state){return new RuleStateStorage.Record(state,null,null,false,false,null);}
    private static final class Fake implements RuleStateStorage.Backend {
        RuleStateStorage.Record memory,disk;int commits;boolean fail,throwCommit,readFailure,memoryOnFailure=true,diskOnFailure;
        Fake(RuleStateStorage.Record initial){memory=disk=initial;}
        @Override public RuleStateStorage.Record read(){if(readFailure)throw new IllegalStateException("private-provider-read");return memory;}
        @Override public boolean commit(String state,boolean blocked,String quarantine) {
            commits++;RuleStateStorage.Record candidate=new RuleStateStorage.Record(state,null,null,false,blocked,quarantine);
            if(!fail&&!throwCommit||memoryOnFailure)memory=candidate;
            if(!fail&&!throwCommit||diskOnFailure)disk=candidate;
            if(throwCommit)throw new IllegalStateException("private-provider-write");
            return !fail;
        }
        Fake restart(){return new Fake(disk);}
    }
    private static String b64(byte[] data){return Base64.getUrlEncoder().withoutPadding().encodeToString(data);}
    private static SignedRulePack.VerifiedPack signed(int version,int window)throws Exception {
        OfflineRuleStore.Pack pack=pack("author.stable",version,generic(window));
        String header="{\"alg\":\"RS256\",\"kid\":\"test.key\",\"typ\":\""+SignedRulePack.TYPE+"\"}";
        String input=b64(header.getBytes(StandardCharsets.UTF_8))+"."+b64(RuleJsonCodec.encodePack(pack).getBytes(StandardCharsets.UTF_8));
        Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(pair.getPrivate());signer.update(input.getBytes(StandardCharsets.US_ASCII));
        return SignedRulePack.verify(input+"."+b64(signer.sign()),Arrays.asList(new SignedRulePack.TrustedKey("test.key","reviewed.publisher","stable",Arrays.asList("author.stable"),pair.getPublic())));
    }
    private static void failedReads(RuleStateStorage storage,SignedRulePack.VerifiedPack next) {
        rejects("failed save blocks load",()->storage.load());
        rejects("failed save blocks auth query",()->storage.authBlocked());
        rejects("failed save blocks local",()->storage.applyLocal(pack("local.blocked",1,bili(1000))));
        rejects("failed save blocks undo",()->storage.rollback());
        rejects("failed save blocks preview",()->storage.previewSigned(next));
        rejects("failed save blocks signed apply",()->storage.applySigned(next));
    }
    public static void main(String[] args)throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);pair=generator.generateKeyPair();
        SignedRulePack.VerifiedPack v1=signed(1,6000),v2=signed(2,5000),v3=signed(3,4000);
        try{new RuleStateStorage(null);throw new AssertionError("null backend");}catch(IllegalArgumentException expected){passed++;}
        Fake fresh=new Fake(record(null));RuleStateStorage empty=new RuleStateStorage(fresh);
        check("empty baseline load",empty.load().store().currentPack()==null);check("empty auth allowed",!empty.authBlocked());check("load does not write",fresh.commits==0);
        check("nothing to undo no write",!empty.rollback()&&fresh.commits==0);
        empty.applyLocal(pack("local.first",1,generic(8000)));check("local saved once",fresh.commits==1);
        check("memory and disk commit whole same record",fresh.memory==fresh.disk);check("effective local",empty.load().store().effective(RuleCatalog.GENERIC).windowMs==8000);
        check("new record clears legacy",fresh.disk.legacyCurrent==null&&fresh.disk.legacyPrevious==null&&!fresh.disk.legacyUndo);
        check("local undo available",empty.rollback());check("undo baseline persisted",empty.load().store().currentPack()==null&&!empty.rollback());

        OfflineRuleStore old=new OfflineRuleStore(RuleCatalog.builtInLimits());old.apply(pack("legacy.first",1,generic(9000)));
        String first=RuleJsonCodec.encodePack(old.currentPack());old.apply(pack("legacy.second",1,bili(7000)));
        RuleStateStorage.Record legacy=new RuleStateStorage.Record(null,RuleJsonCodec.encodePack(old.currentPack()),first,true,false,null);
        Fake legacyBackend=new Fake(legacy);RuleStateStorage migrated=new RuleStateStorage(legacyBackend);
        check("legacy current decoded",migrated.load().store().effective(RuleCatalog.BILI).windowMs==7000);check("legacy undo decoded",migrated.load().store().canRollback());
        check("legacy origin local not verified",migrated.load().currentSourceDescription().contains("未验证"));check("read-only migration no disk write",legacyBackend.commits==0&&legacyBackend.disk==legacy);
        check("legacy undo writes new snapshot",migrated.rollback()&&legacyBackend.commits==1&&legacyBackend.disk.state!=null);
        check("legacy keys removed in transaction",legacyBackend.disk.legacyCurrent==null&&legacyBackend.disk.legacyPrevious==null&&!legacyBackend.disk.legacyUndo);
        check("legacy undo target kept",migrated.load().store().effective(RuleCatalog.GENERIC).windowMs==9000&&migrated.load().store().effective(RuleCatalog.BILI).windowMs==12000);

        Fake authBackend=new Fake(record(null));RuleStateStorage auth=new RuleStateStorage(authBackend);
        check("preview first does not mutate",!auth.previewSigned(v1).alreadyAccepted&&authBackend.commits==0&&auth.load().store().currentPack()==null);
        check("signed applies",auth.applySigned(v1)&&authBackend.commits==1);String accepted=authBackend.disk.state;
        check("signed source kept",auth.load().currentSourceDescription().contains("已验证发布者"));
        check("exact replay no commit",!auth.applySigned(v1)&&authBackend.commits==1&&accepted.equals(authBackend.disk.state));
        check("preview next does not advance",!auth.previewSigned(v2).alreadyAccepted&&authBackend.commits==1&&auth.load().store().currentPack().version==1);
        auth.applyLocal(pack("local.tighten",1,bili(2000)));String localState=authBackend.disk.state;int writes=authBackend.commits;
        check("duplicate does not replace local state or undo",!auth.applySigned(v1)&&authBackend.commits==writes&&authBackend.disk.state.equals(localState)&&auth.load().store().canRollback());
        check("undo local restores signed source",auth.rollback()&&auth.load().currentSourceDescription().contains("已验证发布者"));
        check("higher signed applies",auth.applySigned(v2)&&auth.load().store().effective(RuleCatalog.GENERIC).windowMs==5000);
        rejects("lower signed rejected without write",()->auth.applySigned(v1));int afterV2=authBackend.commits;
        check("rejected replay did not write",afterV2==authBackend.commits);
        check("undo signed keeps higher ledger",auth.rollback()&&auth.previewSigned(v2).alreadyAccepted);
        auth.restoreBuiltIns();check("reset baseline",auth.load().store().currentPack()==null);
        check("reset retains duplicate watermark",auth.previewSigned(v2).alreadyAccepted);rejects("reset retains stale rejection",()->auth.previewSigned(v1));
        RuleStateStorage reopened=new RuleStateStorage(authBackend.restart());check("restart retains ledger at baseline",reopened.previewSigned(v2).alreadyAccepted);
        rejects("restart lower rejection",()->reopened.applySigned(v1));
        check("new higher still allowed after reset",reopened.applySigned(v3)&&reopened.load().store().effective(RuleCatalog.GENERIC).windowMs==4000);

        Fake poisonBackend=new Fake(record(null));RuleStateStorage poison=new RuleStateStorage(poisonBackend);
        poison.applyLocal(pack("author.stable",999999,generic(7000)));
        check("unverified same-ID revision not a watermark",poison.applySigned(v1)&&poison.load().store().currentPack().version==1);
        poison.restoreBuiltIns();check("reset after poisoning retains trusted history",poison.previewSigned(v1).alreadyAccepted);

        Fake failBackend=new Fake(record(null));RuleStateStorage failing=new RuleStateStorage(failBackend);failing.applySigned(v1);
        String confirmed=failBackend.disk.state;failBackend.fail=true;
        rejects("false commit is unconfirmed",()->failing.applySigned(v2));
        check("fake models updated memory with unchanged disk",!failBackend.memory.state.equals(confirmed)&&failBackend.disk.state.equals(confirmed));
        failedReads(failing,v3);
        RuleStateStorage restartedOld=new RuleStateStorage(failBackend.restart());
        check("restart sees complete previous disk snapshot",restartedOld.load().store().currentPack().version==1&&!restartedOld.previewSigned(v2).alreadyAccepted);
        rejects("failed explicit recovery remains latched",()->failing.restoreBuiltIns());rejects("still latched after failed recovery",()->failing.load());
        failBackend.fail=false;failing.restoreBuiltIns();
        check("successful explicit recovery clears latch",failing.load().store().currentPack()==null&&!failing.authBlocked());
        check("conservative recovery keeps highest observed complete ledger",failing.previewSigned(v2).alreadyAccepted);
        rejects("recovery never lowers observed ledger",()->failing.applySigned(v1));

        Fake uncertainBackend=new Fake(record(null));RuleStateStorage uncertain=new RuleStateStorage(uncertainBackend);uncertain.applySigned(v1);
        uncertainBackend.fail=true;uncertainBackend.diskOnFailure=true;
        rejects("unconfirmed new disk snapshot latches live process",()->uncertain.applySigned(v2));rejects("new disk still does not unlatch",()->uncertain.load());
        RuleStateStorage restartedNew=new RuleStateStorage(uncertainBackend.restart());
        check("restart new disk is complete state plus ledger",restartedNew.load().store().currentPack().version==2&&restartedNew.previewSigned(v2).alreadyAccepted);
        rejects("new complete disk blocks older version",()->restartedNew.applySigned(v1));

        Fake throwingBackend=new Fake(record(null));RuleStateStorage throwing=new RuleStateStorage(throwingBackend);
        throwingBackend.throwCommit=true;rejects("commit exception fixed and latched",()->throwing.applyLocal(pack("local.throw",1,generic(8000))));
        failedReads(throwing,v1);throwingBackend.throwCommit=false;throwing.restoreBuiltIns();check("exception recovery baseline",throwing.load().store().currentPack()==null);
        Fake readBackend=new Fake(record(null));RuleStateStorage reader=new RuleStateStorage(readBackend);readBackend.readFailure=true;
        rejects("read failure sanitized",()->reader.load());rejects("cannot recover by overwriting unread history",()->reader.restoreBuiltIns());
        check("read failure does not write",readBackend.commits==0);readBackend.readFailure=false;check("transient read failure recoverable without latch",reader.load().store().currentPack()==null);

        String broken="{broken-state-private-provider}";Fake corruptBackend=new Fake(record(broken));RuleStateStorage corrupt=new RuleStateStorage(corruptBackend);
        rejects("corrupt state stops load",()->corrupt.load());check("corrupt state never silently reset",corruptBackend.commits==0&&corruptBackend.disk.state.equals(broken));
        corrupt.restoreBuiltIns();check("corrupt explicit reset permits baseline",corrupt.load().store().currentPack()==null);
        check("corrupt history pauses signed import",corrupt.authBlocked()&&broken.equals(corruptBackend.disk.quarantine));
        rejects("quarantine blocks signed preview",()->corrupt.previewSigned(v1));rejects("quarantine blocks signed apply",()->corrupt.applySigned(v1));
        corrupt.applyLocal(pack("local.quarantined",1,generic(7000)));check("local allowed while auth paused",corrupt.load().store().effective(RuleCatalog.GENERIC).windowMs==7000);
        corrupt.rollback();corrupt.restoreBuiltIns();check("undo reset retain quarantine and block",corrupt.authBlocked()&&broken.equals(corruptBackend.disk.quarantine));
        RuleStateStorage reopenedCorrupt=new RuleStateStorage(corruptBackend.restart());check("quarantine persists restart",reopenedCorrupt.authBlocked());rejects("restart cannot discard corrupt history",()->reopenedCorrupt.applySigned(v1));

        Fake incoherentBackend=new Fake(new RuleStateStorage.Record(RuleUpdateState.empty().previewSigned(v1).next.encode(),null,null,false,false,"prior damaged state"));
        RuleStateStorage incoherent=new RuleStateStorage(incoherentBackend);rejects("quarantine cannot claim auth allowed",()->incoherent.load());
        incoherent.restoreBuiltIns();check("recovery retains quarantine and enforces block",incoherent.authBlocked()&&incoherentBackend.disk.quarantine.equals("prior damaged state"));
        check("repairing block metadata preserves otherwise valid ledger",incoherent.load().encode().contains(v1.payloadSha256));
        Fake longQuarantineBackend=new Fake(new RuleStateStorage.Record(RuleUpdateState.empty().previewSigned(v1).next.encode(),null,null,false,true,new String(new char[65537]).replace('\0','x')));
        RuleStateStorage longQuarantine=new RuleStateStorage(longQuarantineBackend);rejects("oversized quarantine requires explicit recovery",()->longQuarantine.load());
        longQuarantine.restoreBuiltIns();check("quarantine bound repair preserves ledger",longQuarantine.authBlocked()&&longQuarantineBackend.disk.quarantine.length()<256&&longQuarantine.load().encode().contains(v1.payloadSha256));
        Fake missingBackend=new Fake(new RuleStateStorage.Record(null,null,null,false,true,"retained damage"));RuleStateStorage missing=new RuleStateStorage(missingBackend);
        check("missing snapshot retains auth block",missing.authBlocked()&&missing.load().store().currentPack()==null);
        missing.restoreBuiltIns();missing.applyLocal(pack("local.missing",1,generic(7000)));
        check("new snapshots preserve retained damage",missing.authBlocked()&&missingBackend.disk.quarantine.equals("retained damage"));
        Fake oversizedBackend=new Fake(record(new String(new char[65537]).replace('\0','x')));RuleStateStorage oversized=new RuleStateStorage(oversizedBackend);
        rejects("oversized corrupt state stops",()->oversized.load());oversized.restoreBuiltIns();
        check("oversized quarantine bounded and blocked",oversized.authBlocked()&&oversizedBackend.disk.quarantine.length()<256);
        Fake legacyBadBackend=new Fake(new RuleStateStorage.Record(null,"{invalid-legacy}",null,false,false,null));RuleStateStorage legacyBad=new RuleStateStorage(legacyBadBackend);
        rejects("invalid legacy not silently dropped",()->legacyBad.load());legacyBad.restoreBuiltIns();
        check("invalid legacy recovery marked",legacyBad.authBlocked()&&legacyBadBackend.disk.quarantine!=null);

        Fake invalidBackend=new Fake(record(null));RuleStateStorage invalid=new RuleStateStorage(invalidBackend);
        rejects("invalid pack never reaches commit",()->invalid.applyLocal(null));
        check("invalid mutation no write",invalidBackend.commits==0);
        invalid.applyLocal(pack("local.narrow",1,generic(6000)));String beforeInvalid=invalidBackend.disk.state;int beforeWrites=invalidBackend.commits;
        rejects("loosen plan not persisted",()->invalid.applyLocal(pack("local.loosen",1,generic(7000))));
        check("rejected plan preserves state and undo",invalidBackend.commits==beforeWrites&&invalidBackend.disk.state.equals(beforeInvalid)&&invalid.load().store().canRollback());
        System.out.println("RuleStateStorageTest: "+passed+" checks passed");
    }
}