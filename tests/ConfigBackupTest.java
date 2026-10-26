import org.splashguard.app.ConfigBackup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Untrusted files may restore selected package names, never privileges, executable rules or records. */
public final class ConfigBackupTest {
    private static int passed;
    private static void check(String name,boolean condition){if(!condition)throw new AssertionError(name);passed++;}
    private static void rejects(String name,Runnable action){
        try{action.run();throw new AssertionError(name);}catch(IllegalArgumentException expected){passed++;}
    }
    private static String file(String array){return "{\"schemaVersion\":1,\"selectedApps\":"+array+"}";}
    private static String repeat(char ch,int count){char[] chars=new char[count];Arrays.fill(chars,ch);return new String(chars);}
    public static void main(String[] args){
        ConfigBackup empty=new ConfigBackup(Collections.emptyList());
        check("empty selection is a valid paused restore",ConfigBackup.decode(empty.toJson()).selectedApps.isEmpty());
        ConfigBackup chosen=new ConfigBackup(Arrays.asList("org.example.reader","com.example.music"));
        check("package list canonical sorting",chosen.selectedApps.equals(Arrays.asList("com.example.music","org.example.reader")));
        check("stable bytes independent of input order",chosen.toJson().equals(new ConfigBackup(Arrays.asList("com.example.music","org.example.reader")).toJson()));
        check("roundtrip retains exact selections",ConfigBackup.decode(chosen.toJson()).selectedApps.equals(chosen.selectedApps));
        check("encoder emits only two fields",chosen.toJson().equals("{\"schemaVersion\":1,\"selectedApps\":[\"com.example.music\",\"org.example.reader\"]}"));
        check("known uppercase package names preserved",ConfigBackup.decode(file("[\"com.Qunar\"]")).selectedApps.get(0).equals("com.Qunar"));
        List<String> input=new ArrayList<>();input.add("com.example.reader");ConfigBackup copy=new ConfigBackup(input);input.clear();
        check("constructor copies caller collection",copy.selectedApps.size()==1);
        try{chosen.selectedApps.add("com.other.app");throw new AssertionError("immutable selection");}catch(UnsupportedOperationException expected){passed++;}
        rejects("null selection",()->new ConfigBackup(null));
        rejects("null backup",()->ConfigBackup.encode(null));
        rejects("null item",()->new ConfigBackup(Arrays.asList((String)null)));
        rejects("duplicate constructor package",()->new ConfigBackup(Arrays.asList("com.example.reader","com.example.reader")));
        rejects("duplicate decoded package",()->ConfigBackup.decode(file("[\"com.example.reader\",\"com.example.reader\"]")));
        rejects("escaped duplicate package",()->ConfigBackup.decode(file("[\"com.example.reader\",\"com.example.\\u0072eader\"]")));
        String[] invalidPackages={"", "reader", "com..reader", ".com.reader", "com.reader.", "1com.reader", "com.1reader", "com._reader", "*", "com.example.*", "com/example/reader", "com.example;reader", "com.example-reader", " com.reader", "com.reader ", "com.reader\n", "com.读书", "https://example.com", "com.reader?run=true", "com.reader\u200b"};
        for(String pkg:invalidPackages)rejects("invalid explicit package "+pkg,()->new ConfigBackup(Collections.singletonList(pkg)));
        String edge="com."+repeat('a',ConfigBackup.MAX_PACKAGE_LENGTH-4);
        check("maximum package length accepted",new ConfigBackup(Collections.singletonList(edge)).selectedApps.get(0).length()==ConfigBackup.MAX_PACKAGE_LENGTH);
        rejects("overlong package",()->new ConfigBackup(Collections.singletonList(edge+"a")));
        List<String> maximum=new ArrayList<>();for(int i=0;i<ConfigBackup.MAX_APPS;i++)maximum.add("com.example.app"+i);
        ConfigBackup max=new ConfigBackup(maximum);check("maximum application count roundtrips",ConfigBackup.decode(max.toJson()).selectedApps.size()==ConfigBackup.MAX_APPS);
        maximum.add("com.example.overflow");rejects("too many constructor apps",()->new ConfigBackup(maximum));
        StringBuilder many=new StringBuilder("[");for(int i=0;i<=ConfigBackup.MAX_APPS;i++){if(i>0)many.append(',');many.append('"').append("com.example.app").append(i).append('"');}many.append(']');
        rejects("too many decoded apps",()->ConfigBackup.decode(file(many.toString())));
        String[] badArrays={"null","{}","true","1","\"com.example.reader\"","[null]","[1]","[true]","[{}]","[[]]","[\"com.reader\",false]"};
        for(String array:badArrays)rejects("not a package array "+array,()->ConfigBackup.decode(file(array)));
        String[] extraFields={"enabled","onboarding_seen","gesture_compatibility","rules_current","count","diagnostic","accessibilityGranted","sensorPermission","script","url"};
        for(String field:extraFields)rejects("foreign preference or code field "+field,()->ConfigBackup.decode("{\"schemaVersion\":1,\"selectedApps\":[],\""+field+"\":true}"));
        rejects("missing schema",()->ConfigBackup.decode("{\"selectedApps\":[]}"));
        rejects("missing list",()->ConfigBackup.decode("{\"schemaVersion\":1}"));
        rejects("wrong schema version",()->ConfigBackup.decode("{\"schemaVersion\":2,\"selectedApps\":[]}"));
        rejects("version string not coerced",()->ConfigBackup.decode("{\"schemaVersion\":\"1\",\"selectedApps\":[]}"));
        rejects("version boolean not coerced",()->ConfigBackup.decode("{\"schemaVersion\":true,\"selectedApps\":[]}"));
        rejects("version float not coerced",()->ConfigBackup.decode("{\"schemaVersion\":1.0,\"selectedApps\":[]}"));
        rejects("version overflow",()->ConfigBackup.decode("{\"schemaVersion\":9223372036854775808,\"selectedApps\":[]}"));
        rejects("duplicate key",()->ConfigBackup.decode("{\"schemaVersion\":1,\"schemaVersion\":1,\"selectedApps\":[]}"));
        rejects("escaped duplicate key",()->ConfigBackup.decode("{\"schemaVersion\":1,\"selectedApps\":[],\"selected\\u0041pps\":[]}"));
        rejects("null JSON",()->ConfigBackup.decode(null));
        rejects("empty JSON",()->ConfigBackup.decode(""));
        rejects("root must be object",()->ConfigBackup.decode("[]"));
        rejects("trailing code",()->ConfigBackup.decode(file("[]")+";run()"));
        rejects("unpaired surrogate",()->ConfigBackup.decode(file("[\"com.\\ud800\"]")));
        rejects("oversize byte input",()->ConfigBackup.decode(file("[]")+repeat(' ',ConfigBackup.MAX_BYTES)));
        rejects("nested structure is not a package",()->ConfigBackup.decode(file("[[[[[[[[[[]]]]]]]]]]")));
        check("decoded data has no authority flags",!max.toJson().contains("enabled")&&!max.toJson().contains("gesture")&&!max.toJson().contains("permission"));
        System.out.println("ConfigBackupTest: "+passed+" checks passed");
    }
}
