import org.splashguard.app.DiagnosticSample;
import org.splashguard.app.DiagnosticSample.*;
import org.splashguard.app.OfflineRuleStore;
import org.splashguard.app.OfflineRuleStore.Pack;
import org.splashguard.app.OfflineRuleStore.RuleLimit;
import org.splashguard.app.RuleJsonCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded untrusted JSON regressions; no Android, files, network, or real UI data. */
public final class RuleJsonCodecTest {
    private static int passed;
    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
        passed++;
    }
    private static void rejects(String name, Runnable action) {
        try { action.run(); throw new AssertionError(name); }
        catch (IllegalArgumentException expected) { passed++; }
    }
    private static void rejectsWith(String name, String fragment, Runnable action) {
        try { action.run(); throw new AssertionError(name); }
        catch (IllegalArgumentException expected) {
            if (expected.getMessage() == null || !expected.getMessage().contains(fragment))
                throw new AssertionError(name + ": unexpected rejection " + expected.getMessage());
            passed++;
        }
    }
    private static RuleLimit generic(int window) {
        return new RuleLimit("generic.splash", OfflineRuleStore.SELECTED_APPS, 0,
                Long.MAX_VALUE, window, 2, true, false);
    }
    private static RuleLimit bili() {
        return new RuleLimit("bili.splash.skip", "tv.danmaku.bili", 0,
                Long.MAX_VALUE, 12000, 2, true, true);
    }
    private static Pack normalPack() {
        return new Pack(1, "local.pack", 1, Arrays.asList(generic(12000), bili()));
    }
    private static DiagnosticSample normalSample() {
        return new DiagnosticSample(Reason.MATCH_VERIFIED, RuleId.BILI_SPLASH_SKIP,
                Target.BILIBILI, 123, NodeType.TEXT_VIEW, Relation.VERIFIED_SPLASH_ANCESTOR,
                1, true, true, true, true, false);
    }
    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
    private static String array(int count) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < count; i++) { if (i > 0) result.append(','); result.append('0'); }
        return result.append(']').toString();
    }
    private static String object(int count) {
        StringBuilder result = new StringBuilder("{");
        for (int i = 0; i < count; i++) {
            if (i > 0) result.append(',');
            result.append('"').append('k').append(i).append("\":0");
        }
        return result.append('}').toString();
    }
    private static void packRejects(String name, String json) {
        rejects(name, () -> RuleJsonCodec.decodePack(json));
    }
    private static void sampleRejects(String name, String json) {
        rejects(name, () -> RuleJsonCodec.decodeSample(json));
    }
    public static void main(String[] args) {
        String pack = RuleJsonCodec.encodePack(normalPack());
        String sample = RuleJsonCodec.encodeSample(normalSample());
        check("pack roundtrip is deterministic", pack.equals(RuleJsonCodec.encodePack(RuleJsonCodec.decodePack(pack))));
        check("sample roundtrip preserves only controlled facts", normalSample().toFields().equals(RuleJsonCodec.decodeSample(sample).toFields()));
        check("sample encoding is deterministic", sample.equals(RuleJsonCodec.encodeSample(RuleJsonCodec.decodeSample(sample))));
        check("pack has the exact stable root schema", pack.startsWith("{\"schemaVersion\":1,\"id\":\"local.pack\",\"version\":1,\"rules\":["));
        check("maximum long is exact", RuleJsonCodec.decodePack(pack).rules.get(0).maxVersionCode == Long.MAX_VALUE);
        check("selected scope is preserved as a fixed token", OfflineRuleStore.SELECTED_APPS.equals(RuleJsonCodec.decodePack(pack).rules.get(0).packageName));
        check("structural JSON whitespace is legal", pack.equals(RuleJsonCodec.encodePack(RuleJsonCodec.decodePack(" \t\r\n" + pack + "\n\r\t "))));
        check("field order does not affect semantics", pack.equals(RuleJsonCodec.encodePack(RuleJsonCodec.decodePack(pack.replace(
                "\"schemaVersion\":1,\"id\":\"local.pack\"", "\"id\":\"local.pack\",\"schemaVersion\":1")))));
        String slash = "\\";
        check("unicode escapes in known keys decode", pack.equals(RuleJsonCodec.encodePack(RuleJsonCodec.decodePack(pack.replace(
                "schemaVersion", slash + "u0073chemaVersion")))));
        check("unicode escapes in controlled values decode", pack.equals(RuleJsonCodec.encodePack(RuleJsonCodec.decodePack(pack.replace(
                "local.pack", "local" + slash + "u002epack")))));
        check("escaped enum token decodes without arbitrary text", sample.equals(RuleJsonCodec.encodeSample(RuleJsonCodec.decodeSample(sample.replace(
                "MATCH_VERIFIED", "MATCH" + slash + "u005fVERIFIED")))));
        check("negative zero is an integer zero", RuleJsonCodec.decodePack(pack.replace("\"minVersionCode\":0", "\"minVersionCode\":-0")).rules.get(0).minVersionCode == 0);
        DiagnosticSample boundary = new DiagnosticSample(Reason.NO_MATCH, RuleId.NONE, Target.OTHER_SELECTED,
                -1, NodeType.NONE, Relation.NONE, 600, false, false, false, false, true);
        check("sample unknown version and candidate upper bound roundtrip", boundary.toFields().equals(
                RuleJsonCodec.decodeSample(RuleJsonCodec.encodeSample(boundary)).toFields()));
        check("sample maximum version code roundtrips", RuleJsonCodec.decodeSample(sample.replace("\"versionCode\":123", "\"versionCode\":" + Long.MAX_VALUE)).versionCode == Long.MAX_VALUE);
        check("minimum positive limits and pack version upper bound decode", RuleJsonCodec.decodePack(pack.replace(
                "\"version\":1", "\"version\":1000000").replace("\"windowMs\":12000", "\"windowMs\":1").replace(
                "\"maxActions\":2", "\"maxActions\":1")).version == 1000000);

        rejects("null pack cannot encode", () -> RuleJsonCodec.encodePack(null));
        rejects("null sample cannot encode", () -> RuleJsonCodec.encodeSample(null));
        packRejects("null input", null);
        packRejects("empty input", "");
        packRejects("whitespace input", " \r\n\t");
        for (String root : Arrays.asList("null", "true", "0", "[]", "\"text\"")) {
            packRejects("pack root object required: " + root, root);
            sampleRejects("sample root object required: " + root, root);
        }

        packRejects("duplicate root key", pack.replace("\"id\":\"local.pack\"", "\"id\":\"local.pack\",\"id\":\"local.other\""));
        rejectsWith("escaped duplicate root key", "duplicate key", () -> RuleJsonCodec.decodePack(pack.replace(
                "\"id\":\"local.pack\"", "\"id\":\"local.pack\",\"" + slash + "u0069d\":\"local.other\"")));
        packRejects("duplicate nested rule key", pack.replace("\"windowMs\":12000", "\"windowMs\":12000,\"windowMs\":1"));
        sampleRejects("duplicate sample key", sample.replace("\"clickable\":true", "\"clickable\":true,\"clickable\":false"));
        packRejects("root action is not an accepted extension", pack.replace("\"rules\":[", "\"action\":\"tap\",\"rules\":["));
        packRejects("nested selector cannot inject executable rules", pack.replace("\"maxActions\":2", "\"selectors\":[\"*\"],\"maxActions\":2"));
        packRejects("missing pack field", pack.replace("\"version\":1,", ""));
        packRejects("missing rule field", pack.replace("\"allowGesture\":false,", "").replace(
                ",\"allowGesture\":false", ""));
        packRejects("persistence wrapper is not an import schema", "{\"current\":" + pack + ",\"previous\":null}");
        packRejects("partial current state is rejected", "{\"schemaVersion\":1,\"id\":\"local.pack\",\"version\":1}");
        rejectsWith("null rules is not defaulted to an empty list", "rules must be an array", () -> RuleJsonCodec.decodePack(
                "{\"schemaVersion\":1,\"id\":\"local.pack\",\"version\":1,\"rules\":null}"));
        rejectsWith("rules object cannot replace array", "rules must be an array", () -> RuleJsonCodec.decodePack(
                "{\"schemaVersion\":1,\"id\":\"local.pack\",\"version\":1,\"rules\":{}}"));
        packRejects("empty rules", "{\"schemaVersion\":1,\"id\":\"local.pack\",\"version\":1,\"rules\":[]}");
        packRejects("null rule item", "{\"schemaVersion\":1,\"id\":\"local.pack\",\"version\":1,\"rules\":[null]}");
        packRejects("duplicate rule IDs are not accepted", pack.replace("bili.splash.skip", "generic.splash"));
        sampleRejects("sample cannot export raw node text", sample.replace("\"reason\":", "\"text\":\"private\",\"reason\":"));
        sampleRejects("sample cannot export device identifier", sample.replace("\"reason\":", "\"deviceId\":\"private\",\"reason\":"));
        sampleRejects("missing sample field", sample.replace(",\"excluded\":false", ""));
        sampleRejects("unknown diagnostic reason", sample.replace("MATCH_VERIFIED", "CUSTOM_REASON"));
        sampleRejects("UI string cannot replace a controlled enum", sample.replace("TEXT_VIEW", "account-private"));
        sampleRejects("package string cannot replace target token", sample.replace("BILIBILI", "tv.danmaku.bili"));

        for (String number : Arrays.asList("\"1\"", "true", "null", "1.0", "1e0", "1E0", "2147483648", "9223372036854775808", "-9223372036854775809", "99999999999999999999999999"))
            packRejects("pack integer coercion/range: " + number, pack.replace("\"version\":1", "\"version\":" + number));
        for (String number : Arrays.asList("01", "-01", "+1", "-", "0x1", "1.", "1e", "1e+1", "NaN", "Infinity", "\u0661"))
            packRejects("non-JSON integer: " + number, pack.replace("\"version\":1", "\"version\":" + number));
        rejectsWith("Long.MIN_VALUE parses exactly before domain rejection", "version range", () -> RuleJsonCodec.decodePack(pack.replace(
                "\"minVersionCode\":0", "\"minVersionCode\":-9223372036854775808")));
        packRejects("boolean strings cannot be coerced", pack.replace("\"enabled\":true", "\"enabled\":\"true\""));
        packRejects("boolean numbers cannot be coerced", pack.replace("\"enabled\":true", "\"enabled\":1"));
        packRejects("null boolean cannot be defaulted", pack.replace("\"enabled\":true", "\"enabled\":null"));
        packRejects("schema string cannot be coerced", pack.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""));
        packRejects("pack schema is bounded", pack.replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
        packRejects("pack version is bounded", pack.replace("\"version\":1", "\"version\":1000001"));
        packRejects("budget cannot exceed hard time limit", pack.replace("\"windowMs\":12000", "\"windowMs\":12001"));
        packRejects("budget cannot exceed hard action limit", pack.replace("\"maxActions\":2", "\"maxActions\":3"));
        packRejects("selected token cannot scope an unrelated rule", pack.replace("tv.danmaku.bili", "@selected"));
        packRejects("path text is not a package", pack.replace("tv.danmaku.bili", "../private"));
        packRejects("URL text is not a package", pack.replace("tv.danmaku.bili", "https://example.com"));
        sampleRejects("sample numeric string cannot be coerced", sample.replace("\"candidateCount\":1", "\"candidateCount\":\"1\""));
        sampleRejects("sample schema string cannot be coerced", sample.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""));
        sampleRejects("sample floating point cannot be coerced", sample.replace("\"versionCode\":123", "\"versionCode\":123.0"));
        sampleRejects("sample large count cannot wrap to int", sample.replace("\"candidateCount\":1", "\"candidateCount\":4294967297"));
        sampleRejects("sample negative version cannot be coerced", sample.replace("\"versionCode\":123", "\"versionCode\":-2"));
        sampleRejects("sample null flag is not false", sample.replace("\"scanComplete\":true", "\"scanComplete\":null"));

        for (String malformed : Arrays.asList(pack + " {}", pack + "//comment", pack.substring(0, pack.length() - 1),
                pack.replace("\"version\":1", "'version':1"), pack.replace("\"version\":1,", "\"version\":1,,"),
                pack.substring(0, pack.length() - 1) + ",}", "{unquoted:1}", "[1,]", "{\"x\":truefalse}"))
            packRejects("syntax is strict: " + malformed.length(), malformed);
        for (String malformed : Arrays.asList("\"" + slash + "x\"", "\"" + slash + "u12\"", "\"" + slash + "u00xz\"", "\"unterminated", "\"tail" + slash))
            packRejects("malformed string escape: " + malformed.length(), malformed);
        rejectsWith("valid quote and backslash escapes parse before schema", "object required", () -> RuleJsonCodec.decodePack("\"" + slash + "\"" + slash + slash + slash + "/\""));
        rejectsWith("valid escaped surrogate pair parses before schema", "object required", () -> RuleJsonCodec.decodePack("\"" + slash + "uD83D" + slash + "uDE00\""));
        rejectsWith("valid raw surrogate pair parses before schema", "object required", () -> RuleJsonCodec.decodePack("\"" + new String(Character.toChars(0x1F600)) + "\""));
        for (String badUnicode : Arrays.asList("\"" + (char)0xD800 + "\"", "\"" + (char)0xDC00 + "\"",
                "\"" + slash + "uD800\"", "\"" + slash + "uDC00\"", "\"" + slash + "uD800a\"",
                "\"" + slash + "uD800" + slash + "uD800\""))
            rejectsWith("unpaired UTF16 cannot be normalized: " + badUnicode.length(), "unpaired surrogate", () -> RuleJsonCodec.decodePack(badUnicode));
        for (String control : Arrays.asList("\"\u0000\"", "\"\t\"", "\"\u0085\"", "\"" + slash + "n\"", "\"" + slash + "u0000\"", "\"" + slash + "u007f\""))
            packRejects("control characters cannot become model text", control);
        packRejects("BOM is not accepted before schema", "\uFEFF" + pack);
        packRejects("non-JSON whitespace cannot hide payload", "\u00A0" + pack);

        rejectsWith("eight nested containers can parse", "object required", () -> RuleJsonCodec.decodePack(repeat("[", 8) + "0" + repeat("]", 8)));
        rejectsWith("ninth container is rejected by depth bound", "nesting limit", () -> RuleJsonCodec.decodePack(repeat("[", 9) + "0" + repeat("]", 9)));
        rejectsWith("eight mixed containers parse before exact schema", "fields", () -> RuleJsonCodec.decodePack("{\"x\":" + repeat("[", 7) + "0" + repeat("]", 7) + "}"));
        rejectsWith("ninth mixed container also hits depth bound", "nesting limit", () -> RuleJsonCodec.decodePack("{\"x\":" + repeat("[", 8) + "0" + repeat("]", 8) + "}"));
        rejectsWith("array at 128 parses before schema", "object required", () -> RuleJsonCodec.decodePack(array(128)));
        rejectsWith("129th array item is rejected before schema", "array item limit", () -> RuleJsonCodec.decodePack(array(129)));
        rejectsWith("32 object fields parse before exact schema", "fields", () -> RuleJsonCodec.decodePack(object(32)));
        rejectsWith("33rd object field is rejected before schema", "object field limit", () -> RuleJsonCodec.decodePack(object(33)));
        rejectsWith("1024-character string parses before schema", "object required", () -> RuleJsonCodec.decodePack("\"" + repeat("a", 1024) + "\""));
        rejectsWith("1024 escaped characters parse before schema", "object required", () -> RuleJsonCodec.decodePack("\"" + repeat(slash + "u0061", 1024) + "\""));
        rejectsWith("1025th decoded character is rejected", "string length", () -> RuleJsonCodec.decodePack("\"" + repeat("a", 1025) + "\""));
        rejectsWith("escaped decoded string uses the same bound", "string length", () -> RuleJsonCodec.decodePack("\"" + repeat(slash + "u0061", 1025) + "\""));
        String atByteLimit = pack + repeat(" ", OfflineRuleStore.MAX_PACK_BYTES - pack.length());
        check("exact UTF8 byte limit is accepted", pack.equals(RuleJsonCodec.encodePack(RuleJsonCodec.decodePack(atByteLimit))));
        rejectsWith("ASCII byte limit cannot be exceeded", "input size", () -> RuleJsonCodec.decodePack(atByteLimit + " "));
        rejectsWith("multibyte text uses byte rather than char count", "pack byte size", () -> RuleJsonCodec.decodePack(pack + repeat("\u3000", 22000)));

        List<RuleLimit> maximumRules = new ArrayList<>();
        for (int i = 0; i < 128; i++) maximumRules.add(new RuleLimit("reviewed.rule" + i,
                "com.example.app", 0, Long.MAX_VALUE, 12000, 2, true, false));
        String maximumPack = RuleJsonCodec.encodePack(new Pack(1, "local.maximum", 1, maximumRules));
        check("128 distinct data rules roundtrip within both bounds", RuleJsonCodec.decodePack(maximumPack).rules.size() == 128
                && maximumPack.equals(RuleJsonCodec.encodePack(RuleJsonCodec.decodePack(maximumPack))));
        Pack longFields = new Pack(1, repeat("a", 80), 1, Arrays.asList(new RuleLimit(repeat("b", 80),
                "com." + repeat("c", 156), Long.MAX_VALUE, Long.MAX_VALUE, 1, 1, false, false)));
        check("maximum accepted identifiers and equal version endpoints roundtrip", RuleJsonCodec.encodePack(longFields).equals(
                RuleJsonCodec.encodePack(RuleJsonCodec.decodePack(RuleJsonCodec.encodePack(longFields)))));
        packRejects("81-character pack id is rejected by model", pack.replace("local.pack", repeat("a", 81)));
        packRejects("161-character package name is rejected by model", pack.replace("tv.danmaku.bili", "com." + repeat("a", 157)));
        for (Reason reason : Reason.values()) {
            DiagnosticSample finite = new DiagnosticSample(reason, RuleId.GENERIC_SPLASH, Target.ZHIHU,
                    0, NodeType.BUTTON, Relation.CREATIVE_ANCESTOR, 0, true, false, false, false, false);
            check("controlled reason roundtrip: " + reason, finite.toFields().equals(
                    RuleJsonCodec.decodeSample(RuleJsonCodec.encodeSample(finite)).toFields()));
        }
        OfflineRuleStore store = new OfflineRuleStore(Arrays.asList(generic(12000), bili()));
        Pack knownRestriction = new Pack(1, "local.tight", 1, Arrays.asList(generic(6000)));
        store.apply(RuleJsonCodec.decodePack(RuleJsonCodec.encodePack(knownRestriction)));
        Pack before = store.currentPack();
        rejects("unknown well-formed rule is rejected by reviewed catalog", () -> store.apply(RuleJsonCodec.decodePack(maximumPack)));
        check("catalog rejection preserves current and undo", store.currentPack() == before && store.canRollback());
        rejects("malformed decode cannot partially apply a pack", () -> store.apply(RuleJsonCodec.decodePack(pack.replace(
                "\"enabled\":true", "\"enabled\":\"true\""))));
        check("decode failure preserves all runtime state", store.currentPack() == before && store.effective("generic.splash").windowMs == 6000 && store.canRollback());
        check("roundtripped canonical snapshot restores single-step undo", restoreAndRollback(store));
        System.out.println("RuleJsonCodecTest: " + passed + " passed");
    }
    private static boolean restoreAndRollback(OfflineRuleStore source) {
        OfflineRuleStore restored = new OfflineRuleStore(Arrays.asList(generic(12000), bili()));
        restored.restoreValidatedState(RuleJsonCodec.decodePack(RuleJsonCodec.encodePack(source.currentPack())),
                source.previousPack() == null ? null : RuleJsonCodec.decodePack(RuleJsonCodec.encodePack(source.previousPack())), source.canRollback());
        return restored.effective("generic.splash").windowMs == 6000 && restored.rollback()
                && restored.effective("generic.splash").windowMs == 12000 && !restored.rollback();
    }
}
