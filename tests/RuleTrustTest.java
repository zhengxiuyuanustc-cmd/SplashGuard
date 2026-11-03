import org.splashguard.app.RuleTrust;
import org.splashguard.app.SignedRulePack;
import org.splashguard.app.RuleUpdateState;
import org.splashguard.app.RuleCatalog;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;

/** End-to-end public example: production pinned key, exact payload, and accepted-history planning. */
public final class RuleTrustTest {
    private static int passed;
    private static void check(String label,boolean value){if(!value)throw new AssertionError(label);passed++;}
    private static String read(Path path)throws Exception{
        long size=Files.size(path);if(size<1||size>65536)throw new AssertionError("Example bounds");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
    }
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(System.getProperty("splashguard.authorityRoot","examples/offline-rules"));
        String envelope=read(root.resolve("community-v1.jws")),payload=read(root.resolve("community-v1.payload.json"));
        SignedRulePack.VerifiedPack verified=RuleTrust.verify(envelope);
        check("compiled trusted publisher",verified.publisher.equals(RuleTrust.PUBLISHER));
        check("compiled trusted channel",verified.channel.equals(RuleTrust.CHANNEL));
        check("compiled key id",verified.kid.equals(RuleTrust.KEY_ID));
        check("reviewed pack scope",verified.pack.id.equals(RuleTrust.PACK_ID)&&verified.pack.version==1);
        check("original envelope retained only as exact input",verified.originalEnvelope.equals(envelope));
        check("digest of exact author payload",verified.payloadSha256.equals(hex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)))));
        check("public sample is a partial existing-slot restriction",verified.pack.rules.size()==1&&verified.pack.rules.get(0).id.equals(RuleCatalog.GENERIC));
        check("public sample cannot enable coordinates",!verified.pack.rules.get(0).allowGesture);
        RuleUpdateState initial=RuleUpdateState.empty();RuleUpdateState.Plan plan=initial.previewSigned(verified);
        check("preview does not mutate current state",initial.store().currentPack()==null);
        check("new source produces plan",!plan.alreadyAccepted);
        check("normalized effective result includes original other slot",plan.next.store().currentPack().rules.size()==2);
        check("explicit source is input not signed aggregate",plan.next.currentSourceDescription().contains("本次输入")||plan.next.currentSourceDescription().contains("该次输入"));
        RuleUpdateState reopened=RuleUpdateState.decode(plan.next.encode());
        check("accepted input no-op after restart",reopened.previewSigned(RuleTrust.verify(envelope)).alreadyAccepted);
        RuleUpdateState baseline=reopened.restoreBuiltIns();
        check("restore baseline really restores rules",baseline.store().currentPack()==null);
        check("accepted input still no-op after baseline restore",baseline.previewSigned(verified).alreadyAccepted);
        check("no-op does not reapply demonstration input",baseline.previewSigned(verified).next.store().currentPack()==null);

        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair attacker=generator.generateKeyPair();
        String[] segments=envelope.split("\\.");String input=segments[0]+"."+segments[1];
        Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(attacker.getPrivate());signature.update(input.getBytes(StandardCharsets.US_ASCII));
        String impostor=input+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        try{RuleTrust.verify(impostor);throw new AssertionError("File cannot replace pinned key");}
        catch(SignedRulePack.Failure expected){check("same kid with other private key rejected",expected.reason==SignedRulePack.Reason.SIGNATURE);}
        System.out.println("RuleTrustTest: "+passed+" checks passed with reviewed public signed example");
    }
}
