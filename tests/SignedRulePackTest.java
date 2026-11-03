import org.splashguard.app.OfflineRuleStore;
import org.splashguard.app.RuleJsonCodec;
import org.splashguard.app.SignedRulePack;
import org.splashguard.app.SignedRulePack.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Generated throwaway test keys only. No owner key, files, network, or Android. */
public final class SignedRulePackTest {
    private static int passed;
    private static final String HEADER="{\"alg\":\"RS256\",\"kid\":\"test.rsa\",\"typ\":\"splashguard-rule-limits+jws\"}";
    private static void check(String name,boolean condition){if(!condition)throw new AssertionError(name);passed++;}
    private static void rejects(String name,Reason reason,Runnable action){
        try{action.run();throw new AssertionError(name+": accepted");}
        catch(Failure failure){
            if(failure.reason!=reason)throw new AssertionError(name+": "+failure.reason+" instead of "+reason);
            if(!failure.getMessage().equals("Rule authentication failed: "+reason.name())||failure.getCause()!=null)
                throw new AssertionError(name+": variable error contents");
            passed++;
        }
    }
    private static String b64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String b64(String text){return b64(text.getBytes(StandardCharsets.UTF_8));}
    private static String sign(String header,byte[] payload,PrivateKey key)throws Exception{
        String input=b64(header)+"."+b64(payload);Signature signature=Signature.getInstance("SHA256withRSA");
        signature.initSign(key);signature.update(input.getBytes(StandardCharsets.US_ASCII));return input+"."+b64(signature.sign());
    }
    private static String sign(String header,String payload,PrivateKey key)throws Exception{return sign(header,payload.getBytes(StandardCharsets.UTF_8),key);}
    private static String[] segments(String compact){return compact.split("[.]",-1);}
    private static String replace(String compact,int index,String value){String[] parts=segments(compact);parts[index]=value;return String.join(".",parts);}
    private static String nonCanonical(String segment){
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int index=alphabet.indexOf(segment.charAt(segment.length()-1));
        return segment.substring(0,segment.length()-1)+alphabet.charAt(index+1);
    }
    private static String repeat(char c,int count){char[] chars=new char[count];Arrays.fill(chars,c);return new String(chars);}
    private static String pack(){
        return RuleJsonCodec.encodePack(new OfflineRuleStore.Pack(1,"author.limits",1,
                Collections.singletonList(new OfflineRuleStore.RuleLimit("generic.splash","@selected",0,Long.MAX_VALUE,8000,1,true,false))));
    }
    private static TrustedKey trust(PublicKey key){return new TrustedKey("test.rsa","test.author","stable",Collections.singletonList("author.limits"),key);}
    private static final class MutableRsa implements RSAPublicKey {
        private static final long serialVersionUID=1L;
        BigInteger n,e;
        MutableRsa(BigInteger n,BigInteger e){this.n=n;this.e=e;}
        public BigInteger getModulus(){return n;}public BigInteger getPublicExponent(){return e;}
        public String getAlgorithm(){return "RSA";}public String getFormat(){return "X.509";}public byte[] getEncoded(){return null;}
    }
    private static void verifyReject(String name,Reason reason,String compact,List<TrustedKey> trust){rejects(name,reason,()->SignedRulePack.verify(compact,trust));}
    public static void main(String[] args)throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        KeyPair pair=generator.generateKeyPair(),other=generator.generateKeyPair();
        TrustedKey trusted=trust(pair.getPublic());List<TrustedKey> keys=Collections.singletonList(trusted);
        String json=pack(),compact=sign(HEADER,json,pair.getPrivate());VerifiedPack verified=SignedRulePack.verify(compact,keys);
        check("verified pack decoded",verified.pack.id.equals("author.limits")&&verified.pack.version==1&&verified.pack.rules.get(0).windowMs==8000);
        check("source is fixed trust metadata",verified.kid.equals("test.rsa")&&verified.publisher.equals("test.author")&&verified.channel.equals("stable"));
        check("original immutable envelope retained",verified.originalEnvelope.equals(compact));
        String digest=b64(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        check("digest is exact original payload",b64(hexBytes(verified.payloadSha256)).equals(digest)&&verified.payloadSha256.matches("[0-9a-f]{64}"));
        check("limits are data only",verified.pack.rules.size()==1&&!verified.pack.rules.get(0).allowGesture);
        try{trusted.allowedPackIds.add("other.pack");throw new AssertionError("mutable scope");}catch(UnsupportedOperationException expected){passed++;}
        List<String> mutableIds=new ArrayList<>(Collections.singletonList("author.limits"));
        TrustedKey copyScope=new TrustedKey("test.rsa","test.author","stable",mutableIds,pair.getPublic());mutableIds.set(0,"other.pack");
        check("caller list changes cannot change trust scope",SignedRulePack.verify(compact,Collections.singletonList(copyScope)).pack.id.equals("author.limits"));
        RSAPublicKey rsa=(RSAPublicKey)pair.getPublic();MutableRsa mutableKey=new MutableRsa(rsa.getModulus(),rsa.getPublicExponent());
        TrustedKey snapshot=trust(mutableKey);mutableKey.n=BigInteger.ONE;mutableKey.e=BigInteger.valueOf(3);
        check("caller key changes cannot alter pinned provider copy",SignedRulePack.verify(compact,Collections.singletonList(snapshot)).kid.equals("test.rsa"));
        check("empty trust list never falls back",emptyRejects(compact));
        verifyReject("null input",Reason.INPUT_FORMAT,null,keys);verifyReject("empty input",Reason.INPUT_FORMAT,"",keys);
        verifyReject("total envelope cap",Reason.INPUT_FORMAT,repeat('A',SignedRulePack.MAX_ENVELOPE_BYTES+1),keys);
        for(String invalid:Arrays.asList(" "+compact,compact+" ",compact+"\n","\uFEFF"+compact,compact+"\u00E9",compact+"\uD800"))
            verifyReject("strict envelope ASCII",Reason.INPUT_FORMAT,invalid,keys);
        for(String invalid:Arrays.asList("", "a", ".a.b", "a..b", "a.b.", "a.b.c.d", ".", "..", "a.b"))
            verifyReject("exactly three nonempty segments",Reason.INPUT_FORMAT,invalid,keys);
        verifyReject("header base64 padding",Reason.INPUT_FORMAT,replace(compact,0,segments(compact)[0]+"="),keys);
        verifyReject("payload base64 plus",Reason.INPUT_FORMAT,replace(compact,1,"+abc"),keys);
        verifyReject("signature base64 slash",Reason.INPUT_FORMAT,replace(compact,2,"/abc"),keys);
        verifyReject("impossible base64 length",Reason.INPUT_FORMAT,replace(compact,0,"A"),keys);
        verifyReject("oversized header before JSON parse",Reason.INPUT_FORMAT,replace(compact,0,b64(repeat(' ',257))),keys);
        verifyReject("oversized payload before verify",Reason.INPUT_FORMAT,replace(compact,1,b64(repeat(' ',48001))),keys);
        verifyReject("noncanonical signature bits",Reason.INPUT_FORMAT,replace(compact,2,nonCanonical(segments(compact)[2])),keys);
        String paddedJson=json;while(paddedJson.getBytes(StandardCharsets.UTF_8).length%3==0)paddedJson+=" ";
        String noncanon=sign(HEADER,paddedJson,pair.getPrivate());
        verifyReject("noncanonical payload bits",Reason.INPUT_FORMAT,replace(noncanon,1,nonCanonical(segments(noncanon)[1])),keys);
        String paddedHeader=HEADER;while(paddedHeader.getBytes(StandardCharsets.UTF_8).length%3==0)paddedHeader+=" ";
        String headerNoncanon=sign(paddedHeader,json,pair.getPrivate());
        verifyReject("noncanonical header bits",Reason.INPUT_FORMAT,replace(headerNoncanon,0,nonCanonical(segments(headerNoncanon)[0])),keys);
        List<String> badHeaders=Arrays.asList("[]","{}","null",HEADER.replace("RS256","none"),HEADER.replace("RS256","HS256"),
                HEADER.replace("RS256","PS256"),HEADER.replace("\"RS256\"","1"),HEADER.replace("\"test.rsa\"","null"),
                HEADER.replace(TYPE(),"unrelated"),HEADER.replace(",\"typ\":\""+TYPE()+"\"",""),
                HEADER.replace("\"alg\":\"RS256\"","\"alg\":\"RS256\",\"alg\":\"RS256\""));
        for(String header:badHeaders)verifyReject("invalid fixed header schema",Reason.HEADER,sign(header,json,pair.getPrivate()),keys);
        for(String field:Arrays.asList("jku","jwk","x5u","x5c","crit","publisher","channel","payload")){
            String header=HEADER.substring(0,HEADER.length()-1)+",\""+field+"\":\"https://untrusted.invalid/key\"}";
            verifyReject("header cannot introduce trust or unknown fields",Reason.HEADER,sign(header,json,pair.getPrivate()),keys);
        }
        byte[] invalidUtf8={(byte)0xC3,0x28};
        verifyReject("header strict UTF8",Reason.HEADER,replace(compact,0,b64(invalidUtf8)),keys);
        verifyReject("unknown kid",Reason.UNTRUSTED_KEY,sign(HEADER.replace("test.rsa","other.rsa"),json,pair.getPrivate()),keys);
        verifyReject("modified payload bytes",Reason.SIGNATURE,replace(compact,1,b64(json.replace("8000","7000"))),keys);
        verifyReject("semantically identical reserialized header not signed",Reason.SIGNATURE,replace(compact,0,b64(" "+HEADER)),keys);
        verifyReject("semantically identical reserialized payload not signed",Reason.SIGNATURE,replace(compact,1,b64(" "+json)),keys);
        String reordered="{\"typ\":\""+TYPE()+"\",\"kid\":\"test.rsa\",\"alg\":\"RS256\"}";
        check("valid differently ordered header uses exact original bytes",SignedRulePack.verify(sign(reordered,json,pair.getPrivate()),keys).pack.version==1);
        String spaced=sign(HEADER," "+json+"\n",pair.getPrivate());
        VerifiedPack differentBytes=SignedRulePack.verify(spaced,keys);
        check("signed legal payload whitespace preserved in digest",differentBytes.pack.version==1&&!verified.payloadSha256.equals(differentBytes.payloadSha256));
        verifyReject("wrong private key",Reason.SIGNATURE,sign(HEADER,json,other.getPrivate()),keys);
        byte[] changedSignature=Base64.getUrlDecoder().decode(segments(compact)[2]);changedSignature[20]^=1;
        verifyReject("changed signature",Reason.SIGNATURE,replace(compact,2,b64(changedSignature)),keys);
        verifyReject("short signature",Reason.SIGNATURE,replace(compact,2,b64(new byte[255])),keys);
        verifyReject("too long signature",Reason.INPUT_FORMAT,replace(compact,2,b64(new byte[257])),keys);
        verifyReject("signed payload invalid UTF8",Reason.PAYLOAD,sign(HEADER,invalidUtf8,pair.getPrivate()),keys);
        for(String payload:Arrays.asList("[]","{}","\uFEFF"+json,json+"{}",json.replace("\"version\":1","\"version\":1.1"),
                json.replace("\"id\":\"author.limits\"","\"id\":\"author.limits\",\"url\":\"https://untrusted.invalid\""),
                json.replace("\"version\":1","\"version\":1,\"version\":2"),json.replace("\"maxActions\":1","\"maxActions\":0")))
            verifyReject("valid signature cannot bypass Pack parser",Reason.PAYLOAD,sign(HEADER,payload,pair.getPrivate()),keys);
        verifyReject("trusted key cannot sign another pack ID",Reason.PACK_SCOPE,sign(HEADER,json.replace("author.limits","foreign.limits"),pair.getPrivate()),keys);
        String unknownRule=json.replace("generic.splash","unknown.rule").replace("@selected","org.example.app");
        check("authentication does not claim catalog validation",SignedRulePack.verify(sign(HEADER,unknownRule,pair.getPrivate()),keys).pack.rules.get(0).id.equals("unknown.rule"));
        rejects("null trust config",Reason.TRUST_CONFIGURATION,()->SignedRulePack.verify(compact,null));
        rejects("null trusted entry",Reason.TRUST_CONFIGURATION,()->SignedRulePack.verify(compact,Arrays.asList(trusted,null)));
        rejects("duplicate kid",Reason.TRUST_CONFIGURATION,()->SignedRulePack.verify(compact,Arrays.asList(trusted,trusted)));
        rejects("too many trusted keys",Reason.TRUST_CONFIGURATION,()->SignedRulePack.verify(compact,Collections.nCopies(SignedRulePack.MAX_TRUSTED_KEYS+1,trusted)));
        for(String invalid:Arrays.asList("","Test","a/other","a..b","-a","a_","a b",repeat('a',81),"a\n")){
            rejects("unsafe kid",Reason.TRUST_CONFIGURATION,()->new TrustedKey(invalid,"author","stable",Collections.singletonList("author.limits"),pair.getPublic()));
            rejects("unsafe publisher",Reason.TRUST_CONFIGURATION,()->new TrustedKey("key",invalid,"stable",Collections.singletonList("author.limits"),pair.getPublic()));
            rejects("unsafe channel",Reason.TRUST_CONFIGURATION,()->new TrustedKey("key","author",invalid,Collections.singletonList("author.limits"),pair.getPublic()));
            rejects("unsafe allowed pack ID",Reason.TRUST_CONFIGURATION,()->new TrustedKey("key","author","stable",Collections.singletonList(invalid),pair.getPublic()));
        }
        rejects("null identifier",Reason.TRUST_CONFIGURATION,()->new TrustedKey(null,"author","stable",Collections.singletonList("author.limits"),pair.getPublic()));
        rejects("no allowed packs",Reason.TRUST_CONFIGURATION,()->new TrustedKey("key","author","stable",Collections.emptyList(),pair.getPublic()));
        rejects("null scope",Reason.TRUST_CONFIGURATION,()->new TrustedKey("key","author","stable",null,pair.getPublic()));
        rejects("duplicate scope",Reason.TRUST_CONFIGURATION,()->new TrustedKey("key","author","stable",Arrays.asList("pack","pack"),pair.getPublic()));
        rejects("too much scope",Reason.TRUST_CONFIGURATION,()->new TrustedKey("key","author","stable",Collections.nCopies(SignedRulePack.MAX_ALLOWED_PACK_IDS+1,"pack"),pair.getPublic()));
        rejects("null key",Reason.TRUST_CONFIGURATION,()->trust(null));
        PublicKey nonRsa=new PublicKey(){private static final long serialVersionUID=1L;public String getAlgorithm(){return "EC";}public String getFormat(){return "X.509";}public byte[] getEncoded(){return new byte[0];}};
        rejects("non RSA key",Reason.TRUST_CONFIGURATION,()->trust(nonRsa));
        for(MutableRsa invalid:Arrays.asList(new MutableRsa(BigInteger.ONE.shiftLeft(1023).add(BigInteger.ONE),rsa.getPublicExponent()),
                new MutableRsa(BigInteger.ONE.shiftLeft(4096).add(BigInteger.ONE),rsa.getPublicExponent()),
                new MutableRsa(rsa.getModulus(),BigInteger.valueOf(3)),new MutableRsa(rsa.getModulus().clearBit(0),rsa.getPublicExponent()),
                new MutableRsa(rsa.getModulus().negate(),rsa.getPublicExponent()),new MutableRsa(null,rsa.getPublicExponent()),new MutableRsa(rsa.getModulus(),null)))
            rejects("RSA size exponent and modulus boundary",Reason.TRUST_CONFIGURATION,()->trust(invalid));
        String maxHeader=HEADER+repeat(' ',SignedRulePack.MAX_HEADER_BYTES-HEADER.getBytes(StandardCharsets.UTF_8).length);
        check("exact header byte limit accepted",SignedRulePack.verify(sign(maxHeader,json,pair.getPrivate()),keys).pack.version==1);
        String maxPayload=json+repeat(' ',SignedRulePack.MAX_PAYLOAD_BYTES-json.getBytes(StandardCharsets.UTF_8).length);
        check("exact payload byte limit accepted",SignedRulePack.verify(sign(HEADER,maxPayload,pair.getPrivate()),keys).pack.version==1);
        List<String> maxScope=new ArrayList<>();maxScope.add("author.limits");for(int i=1;i<SignedRulePack.MAX_ALLOWED_PACK_IDS;i++)maxScope.add("pack"+i);
        check("exact scope count accepted",new TrustedKey("key","author","stable",maxScope,pair.getPublic()).allowedPackIds.size()==SignedRulePack.MAX_ALLOWED_PACK_IDS);
        check("exact identifier limit accepted",new TrustedKey(repeat('a',80),"author","stable",Collections.singletonList("author.limits"),pair.getPublic()).kid.length()==80);
        generator.initialize(4096);KeyPair large=generator.generateKeyPair();
        check("4096 RSA supported",SignedRulePack.verify(sign(HEADER,json,large.getPrivate()),Collections.singletonList(trust(large.getPublic()))).pack.version==1);
        check("constants bound all file allocations",SignedRulePack.MAX_ENVELOPE_BYTES==65536&&SignedRulePack.MAX_PAYLOAD_BYTES==48000&&SignedRulePack.MAX_HEADER_BYTES==256);
        System.out.println("SignedRulePackTest: "+passed+" checks passed");
    }
    private static boolean emptyRejects(String compact){try{SignedRulePack.verify(compact,Collections.emptyList());return false;}catch(Failure failure){return failure.reason==Reason.UNTRUSTED_KEY;}}
    private static String TYPE(){return SignedRulePack.TYPE;}
    private static byte[] hexBytes(String text){byte[] result=new byte[text.length()/2];for(int i=0;i<result.length;i++)result[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return result;}
}
