import org.splashguard.app.RuleReplay;
import org.splashguard.app.RuleCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.DirectoryStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.HashSet;
import java.util.Set;

/** Executable, reviewed data cases. It does not simulate Android clicks, overlays or user intent. */
public final class RuleReplayCorpusTest {
    private static int passed;
    private static void check(String label,boolean value){if(!value)throw new AssertionError(label);passed++;}
    private static String read(Path path,int limit)throws Exception{
        long size=Files.size(path);if(size<=0||size>limit)throw new AssertionError("Fixture file size: "+path.getFileName());
        byte[] bytes=Files.readAllBytes(path);if(bytes.length>limit)throw new AssertionError("Fixture grew");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(System.getProperty("splashguard.fixtureRoot","tests/fixtures/rule-replay")).toRealPath();
        String index=read(root.resolve("index.tsv"),16384);Set<String> names=new HashSet<>(),outcomes=new HashSet<>();int cases=0;
        for(String line:index.split(String.valueOf((char)10))){
            if(line.endsWith(String.valueOf((char)13)))line=line.substring(0,line.length()-1);
            if(line.isEmpty()||line.startsWith("#"))continue;
            String[] f=line.split(String.valueOf((char)9),-1);check("five manifest columns",f.length==5);
            String name=f[0];check("bounded local fixture filename",name.length()<=100&&name.matches("case-[0-9]{2}-[a-z0-9-]+[.]json"));
            check("unique registration "+name,names.add(name));if(++cases>128)throw new AssertionError("Too many fixture cases");
            String expected=f[1];if(!expected.equals("INVALID"))RuleReplay.Outcome.valueOf(expected);outcomes.add(expected);
            int identity=Integer.parseInt(f[2]);check("identity contract "+name,expected.equals("UNIQUE_RULE_MATCH")?(identity>=0&&identity<64):identity==-1);
            check("rule contract "+name,f[3].equals("none")||f[3].equals(RuleCatalog.BILI)||f[3].equals(RuleCatalog.GENERIC));
            check("verified flag explicit "+name,f[4].equals("true")||f[4].equals("false"));boolean verified=Boolean.parseBoolean(f[4]);
            Path file=root.resolve(name).normalize();check("no links or paths escape "+name,file.getParent().equals(root)&&!Files.isSymbolicLink(file)&&file.toRealPath().getParent().equals(root));
            String json=read(file,65536);
            if(expected.equals("INVALID")){
                try{RuleReplay.decode(json);throw new AssertionError("Invalid fixture accepted: "+name);}catch(IllegalArgumentException required){passed++;}
                continue;
            }
            RuleReplay.Frame frame=RuleReplay.decode(json);RuleReplay.Result first=RuleReplay.evaluate(frame);
            check("outcome "+name,first.outcome.name().equals(expected));check("identity "+name,first.uniqueIdentity==identity);check("rule "+name,first.ruleId.equals(f[3]));check("version scope "+name,first.versionVerified==verified);
            String encoded=RuleReplay.encode(frame);RuleReplay.Frame restored=RuleReplay.decode(encoded);check("canonical roundtrip "+name,encoded.equals(RuleReplay.encode(restored)));
            RuleReplay.Result second=RuleReplay.evaluate(restored);check("same verdict after serialization "+name,first.outcome==second.outcome&&first.uniqueIdentity==second.uniqueIdentity&&first.ruleId.equals(second.ruleId)&&first.versionVerified==second.versionVerified&&first.matchedIdentities==second.matchedIdentities);
        }
        Set<String> files=new HashSet<>();try(DirectoryStream<Path> stream=Files.newDirectoryStream(root,"*.json")){for(Path file:stream)files.add(file.getFileName().toString());}
        check("every fixture registered and present",files.equals(names));
        check("all decision classes and malformed input represented",outcomes.contains("INVALID")&&outcomes.contains("UNIQUE_RULE_MATCH")&&outcomes.contains("NO_MATCH")&&outcomes.contains("MULTIPLE_CANDIDATES")&&outcomes.contains("INCOMPLETE_SCAN"));
        System.out.println("RuleReplayCorpusTest: "+passed+" checks passed across "+cases+" registered fixtures");
    }
}
