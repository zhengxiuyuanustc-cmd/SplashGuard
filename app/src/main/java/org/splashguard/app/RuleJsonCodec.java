package org.splashguard.app;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small, bounded JSON codec for the two local data schemas, not a scripting engine. */
public final class RuleJsonCodec {
    private RuleJsonCodec() {}
    public static final int MAX_DEPTH = 8;
    public static final int MAX_ARRAY_ITEMS = 128;
    public static final int MAX_OBJECT_FIELDS = 32;
    public static final int MAX_STRING_LENGTH = 1024;

    public static String encodePack(OfflineRuleStore.Pack pack) {
        if (pack == null) throw invalid("null pack");
        Map<String,Object> fields = new LinkedHashMap<>();
        fields.put("schemaVersion",pack.schemaVersion);
        fields.put("id",pack.id);
        fields.put("version",pack.version);
        List<Object> rules = new ArrayList<>();
        for (OfflineRuleStore.RuleLimit rule : pack.rules) {
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("id",rule.id);
            item.put("packageName",rule.packageName);
            item.put("minVersionCode",rule.minVersionCode);
            item.put("maxVersionCode",rule.maxVersionCode);
            item.put("windowMs",rule.windowMs);
            item.put("maxActions",rule.maxActions);
            item.put("enabled",rule.enabled);
            item.put("allowGesture",rule.allowGesture);
            rules.add(item);
        }
        fields.put("rules",rules);
        return encode(fields);
    }

    public static OfflineRuleStore.Pack decodePack(String text) {
        Map<String,Object> fields = object(new Parser(text).parse());
        OfflineRuleStore.requireFields(fields.keySet(),OfflineRuleStore.FieldKind.PACK);
        Object value = fields.get("rules");
        if (!(value instanceof List)) throw invalid("rules must be an array");
        List<OfflineRuleStore.RuleLimit> rules = new ArrayList<>();
        for (Object entry : (List<?>)value) {
            Map<String,Object> item = object(entry);
            OfflineRuleStore.requireFields(item.keySet(),OfflineRuleStore.FieldKind.RULE);
            rules.add(new OfflineRuleStore.RuleLimit(string(item.get("id")),string(item.get("packageName")),
                    integer(item.get("minVersionCode")),integer(item.get("maxVersionCode")),
                    intValue(item.get("windowMs")),intValue(item.get("maxActions")),
                    bool(item.get("enabled")),bool(item.get("allowGesture"))));
        }
        return new OfflineRuleStore.Pack(intValue(fields.get("schemaVersion")),string(fields.get("id")),
                intValue(fields.get("version")),rules);
    }

    public static String encodeSample(DiagnosticSample sample) {
        if (sample == null) throw invalid("null sample");
        return encode(sample.toFields());
    }

    public static DiagnosticSample decodeSample(String text) {
        return DiagnosticSample.fromFields(object(new Parser(text).parse()));
    }

    private static String encode(Object value) {
        StringBuilder result = new StringBuilder();
        write(value,result,0);
        String text = result.toString();
        checkInput(text);
        return text;
    }

    private static void write(Object value,StringBuilder result,int depth) {
        if (value instanceof Map) {
            if (depth >= MAX_DEPTH) throw invalid("nesting limit");
            Map<?,?> map=(Map<?,?>)value;
            if (map.size()>MAX_OBJECT_FIELDS) throw invalid("object field limit");
            result.append('{');boolean first=true;
            for (Map.Entry<?,?> entry:map.entrySet()) {
                if (!first) result.append(',');first=false;
                quote(string(entry.getKey()),result);result.append(':');write(entry.getValue(),result,depth+1);
            }
            result.append('}');
        } else if (value instanceof List) {
            if (depth >= MAX_DEPTH) throw invalid("nesting limit");
            List<?> list=(List<?>)value;
            if (list.size()>MAX_ARRAY_ITEMS) throw invalid("array item limit");
            result.append('[');boolean first=true;
            for(Object item:list){if(!first)result.append(',');first=false;write(item,result,depth+1);}
            result.append(']');
        } else if (value instanceof String) quote((String)value,result);
        else if (value instanceof Boolean) result.append(value);
        else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
            result.append(value);
        else throw invalid("unsupported value");
        if(result.length()>OfflineRuleStore.MAX_PACK_BYTES)throw invalid("output byte limit");
    }

    private static void quote(String value,StringBuilder result) {
        checkString(value);result.append('"');
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='"'||c=='\\')result.append('\\');
            result.append(c);
        }
        result.append('"');
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> object(Object value) {
        if(!(value instanceof Map))throw invalid("object required");
        return (Map<String,Object>)value;
    }
    private static String string(Object value) {
        if(!(value instanceof String))throw invalid("string required");
        return (String)value;
    }
    private static long integer(Object value) {
        if(!(value instanceof Long))throw invalid("integer required");
        return (Long)value;
    }
    private static int intValue(Object value) {
        long number=integer(value);
        if(number<Integer.MIN_VALUE||number>Integer.MAX_VALUE)throw invalid("integer range");
        return (int)number;
    }
    private static boolean bool(Object value) {
        if(!(value instanceof Boolean))throw invalid("boolean required");
        return (Boolean)value;
    }

    private static void checkInput(String text) {
        if(text==null||text.isEmpty()||text.length()>OfflineRuleStore.MAX_PACK_BYTES)throw invalid("input size");
        checkUtf16(text);
        OfflineRuleStore.requireInputSize(text.getBytes(StandardCharsets.UTF_8).length);
    }
    private static void checkString(String value) {
        if(value.length()>MAX_STRING_LENGTH)throw invalid("string length");
        checkUtf16(value);
        for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))throw invalid("string control character");
    }
    private static void checkUtf16(String value) {
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)) {
                if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(i+1)))throw invalid("unpaired surrogate");
                i++;
            } else if(Character.isLowSurrogate(c))throw invalid("unpaired surrogate");
        }
    }
    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid offline JSON: "+reason);
    }

    /** Integer-only JSON subset. Bounds are checked before appending entries or decoded characters. */
    private static final class Parser {
        final String text;int at;
        Parser(String text){checkInput(text);this.text=text;}
        Object parse(){Object value=value(0);space();if(at!=text.length())throw invalid("trailing content");return value;}
        Object value(int depth) {
            space();if(at>=text.length())throw invalid("missing value");char c=text.charAt(at);
            if(c=='{')return objectValue(depth+1);
            if(c=='[')return arrayValue(depth+1);
            if(c=='"')return stringValue();
            if(c=='t'){literal("true");return Boolean.TRUE;}
            if(c=='f'){literal("false");return Boolean.FALSE;}
            if(c=='n'){literal("null");return null;}
            if(c=='-'||(c>='0'&&c<='9'))return numberValue();
            throw invalid("unexpected token");
        }
        Map<String,Object> objectValue(int depth) {
            if(depth>MAX_DEPTH)throw invalid("nesting limit");at++;
            Map<String,Object> result=new LinkedHashMap<>();space();if(take('}'))return result;
            while(true) {
                if(result.size()>=MAX_OBJECT_FIELDS)throw invalid("object field limit");
                space();if(at>=text.length()||text.charAt(at)!='"')throw invalid("object key required");
                String key=stringValue();if(result.containsKey(key))throw invalid("duplicate key");
                space();expect(':');result.put(key,value(depth));space();
                if(take('}'))return result;expect(',');
            }
        }
        List<Object> arrayValue(int depth) {
            if(depth>MAX_DEPTH)throw invalid("nesting limit");at++;
            List<Object> result=new ArrayList<>();space();if(take(']'))return result;
            while(true) {
                if(result.size()>=MAX_ARRAY_ITEMS)throw invalid("array item limit");
                result.add(value(depth));space();if(take(']'))return result;expect(',');
            }
        }
        String stringValue() {
            expect('"');StringBuilder result=new StringBuilder();
            while(at<text.length()) {
                char c=text.charAt(at++);
                if(c=='"'){String value=result.toString();checkString(value);return value;}
                if(c=='\\') {
                    if(at>=text.length())throw invalid("incomplete escape");
                    char e=text.charAt(at++);
                    switch(e) {
                        case '"':c='"';break;case '\\':c='\\';break;case '/':c='/';break;
                        case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;
                        case 'r':c='\r';break;case 't':c='\t';break;
                        case 'u':
                            if(at+4>text.length())throw invalid("incomplete unicode escape");
                            int code=0;
                            for(int i=0;i<4;i++) {
                                char h=text.charAt(at++);int digit=(h>='0'&&h<='9')?h-'0':(h>='a'&&h<='f')?h-'a'+10:(h>='A'&&h<='F')?h-'A'+10:-1;
                                if(digit<0)throw invalid("unicode escape");code=code*16+digit;
                            }
                            c=(char)code;break;
                        default:throw invalid("escape");
                    }
                } else if(c<0x20)throw invalid("unescaped control");
                if(result.length()>=MAX_STRING_LENGTH)throw invalid("string length");
                result.append(c);
            }
            throw invalid("unterminated string");
        }
        Long numberValue() {
            int start=at;if(take('-')&&at>=text.length())throw invalid("number");
            if(take('0')) {
                if(at<text.length()&&digit(text.charAt(at)))throw invalid("leading zero");
            } else {
                if(at>=text.length()||text.charAt(at)<'1'||text.charAt(at)>'9')throw invalid("number");
                while(at<text.length()&&digit(text.charAt(at)))at++;
            }
            if(at<text.length()&&(text.charAt(at)=='.'||text.charAt(at)=='e'||text.charAt(at)=='E'))throw invalid("only integers allowed");
            if(at-start>20)throw invalid("integer range");
            try{return Long.valueOf(text.substring(start,at));}
            catch(NumberFormatException bad){throw invalid("integer range");}
        }
        void literal(String value) {
            if(!text.startsWith(value,at))throw invalid("literal");at+=value.length();
        }
        void space(){while(at<text.length()){char c=text.charAt(at);if(c==' '||c=='\t'||c=='\r'||c=='\n')at++;else break;}}
        boolean take(char c){if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;}
        void expect(char c){if(!take(c))throw invalid("separator");}
        boolean digit(char c){return c>='0'&&c<='9';}
    }
}
