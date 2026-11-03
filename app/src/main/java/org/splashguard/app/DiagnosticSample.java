package org.splashguard.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * User-requested diagnostic decision input, never a screenshot or raw UI tree.
 * Only enumerated labels and bounded numeric/boolean facts can cross this API.
 * Replaying it explains the recorded conditions; it cannot recreate a screen,
 * authenticate a close button, execute an action, or verify an ad disappeared.
 */
public final class DiagnosticSample {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CANDIDATES = 600;

    public enum Reason {
        NO_MATCH, NOT_VISIBLE, NOT_ENABLED, WRONG_PACKAGE, VERSION_UNKNOWN, VERSION_OUTSIDE,
        TARGET_MISMATCH, ANCESTORS_INCOMPLETE, ANCESTOR_MISMATCH, EXCLUDED, UNSAFE_LABEL,
        NO_AD_EVIDENCE, MATCH_VERIFIED, MATCH_UNVERIFIED, MULTIPLE_CANDIDATES,
        INCOMPLETE_SCAN, TARGET_CHANGED, NODE_ACTION_REJECTED, GESTURE_DISABLED,
        ACTION_SUBMITTED, SERVICE_NOT_READY, WINDOW_EXPIRED, AMBIGUOUS, VERSION_UNVERIFIED,
        CONTEXT_MISMATCH, STOPPED, ATTEMPT_SUBMITTED
    }

    public enum RuleId {
        NONE("none"), GENERIC_SPLASH("generic.splash"), BILI_SPLASH_SKIP("bili.splash.skip");
        public final String id;
        RuleId(String id) { this.id = id; }

        /** Unknown IDs are collapsed, never copied into a diagnostic file. */
        public static RuleId fromKnownRuleId(String value) {
            for (RuleId rule : values()) if (rule.id.equals(value)) return rule;
            return NONE;
        }
    }

    public enum Target { BILIBILI, ZHIHU, OTHER_SELECTED }
    public enum NodeType { NONE, TEXT_VIEW, BUTTON, FRAME_LAYOUT, VIEW_GROUP, WEB_VIEW, OTHER }
    public enum Relation { NONE, VERIFIED_SPLASH_ANCESTOR, CREATIVE_ANCESTOR, UNKNOWN }
    public enum ReplayDecision {
        STOP_INCOMPLETE_SCAN, STOP_MULTIPLE_CANDIDATES, STOP_EXCLUDED, STOP_TARGET_CHANGED,
        STOP_UNVERIFIED_VERSION, STOP_CLOSED_SESSION, STOP_SERVICE_NOT_READY,
        NO_CANDIDATE, STOP_NOT_CLICKABLE, REVIEW_CONTEXT, REVIEW_NODE_ACTION
    }

    private static final Set<String> FIELDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "schemaVersion", "reason", "ruleId", "target", "versionCode", "nodeType", "relation",
            "candidateCount", "scanComplete", "clickable", "sceneMatched", "targetMatched", "excluded")));

    public final Reason reason;
    public final RuleId ruleId;
    public final Target target;
    public final long versionCode;
    public final NodeType nodeType;
    public final Relation relation;
    public final int candidateCount;
    public final boolean scanComplete;
    public final boolean clickable;
    public final boolean sceneMatched;
    public final boolean targetMatched;
    public final boolean excluded;

    public DiagnosticSample(Reason reason, RuleId ruleId, Target target, long versionCode,
                            NodeType nodeType, Relation relation, int candidateCount,
                            boolean scanComplete, boolean clickable, boolean sceneMatched,
                            boolean targetMatched, boolean excluded) {
        if (reason == null || ruleId == null || target == null || nodeType == null || relation == null)
            throw invalid("enum");
        if (versionCode < -1) throw invalid("versionCode");
        if (candidateCount < 0 || candidateCount > MAX_CANDIDATES) throw invalid("candidateCount");
        this.reason = reason;
        this.ruleId = ruleId;
        this.target = target;
        this.versionCode = versionCode;
        this.nodeType = nodeType;
        this.relation = relation;
        this.candidateCount = candidateCount;
        this.scanComplete = scanComplete;
        this.clickable = clickable;
        this.sceneMatched = sceneMatched;
        this.targetMatched = targetMatched;
        this.excluded = excluded;
    }

    /** Values are safe fixed tokens/primitives, not strings obtained from a UI node. */
    public Map<String, Object> toFields() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", SCHEMA_VERSION);
        result.put("reason", reason.name());
        result.put("ruleId", ruleId.name());
        result.put("target", target.name());
        result.put("versionCode", versionCode);
        result.put("nodeType", nodeType.name());
        result.put("relation", relation.name());
        result.put("candidateCount", candidateCount);
        result.put("scanComplete", scanComplete);
        result.put("clickable", clickable);
        result.put("sceneMatched", sceneMatched);
        result.put("targetMatched", targetMatched);
        result.put("excluded", excluded);
        return Collections.unmodifiableMap(result);
    }

    /** Strict SAF/JSON input boundary: unknown fields, coercions and arbitrary text fail closed. */
    public static DiagnosticSample fromFields(Map<String, ?> input) {
        if (input == null || !input.keySet().equals(FIELDS)) throw invalid("fields");
        if (integer(input.get("schemaVersion"), "schemaVersion") != SCHEMA_VERSION) throw invalid("schemaVersion");
        long count = integer(input.get("candidateCount"), "candidateCount");
        if (count < 0 || count > MAX_CANDIDATES) throw invalid("candidateCount");
        return new DiagnosticSample(token(Reason.class, input.get("reason"), "reason"),
                token(RuleId.class, input.get("ruleId"), "ruleId"),
                token(Target.class, input.get("target"), "target"),
                integer(input.get("versionCode"), "versionCode"),
                token(NodeType.class, input.get("nodeType"), "nodeType"),
                token(Relation.class, input.get("relation"), "relation"), (int) count,
                bool(input.get("scanComplete"), "scanComplete"), bool(input.get("clickable"), "clickable"),
                bool(input.get("sceneMatched"), "sceneMatched"), bool(input.get("targetMatched"), "targetMatched"),
                bool(input.get("excluded"), "excluded"));
    }

    /** Deterministic explanation, never permission to perform an Android action. */
    public ReplayDecision replay() {
        if (!scanComplete || reason == Reason.INCOMPLETE_SCAN || reason == Reason.ANCESTORS_INCOMPLETE)
            return ReplayDecision.STOP_INCOMPLETE_SCAN;
        if (candidateCount > 1 || reason == Reason.MULTIPLE_CANDIDATES || reason == Reason.AMBIGUOUS)
            return ReplayDecision.STOP_MULTIPLE_CANDIDATES;
        if (reason == Reason.SERVICE_NOT_READY) return ReplayDecision.STOP_SERVICE_NOT_READY;
        if (reason == Reason.WINDOW_EXPIRED || reason == Reason.STOPPED) return ReplayDecision.STOP_CLOSED_SESSION;
        if (excluded || reason == Reason.EXCLUDED || reason == Reason.UNSAFE_LABEL)
            return ReplayDecision.STOP_EXCLUDED;
        if (reason == Reason.TARGET_CHANGED) return ReplayDecision.STOP_TARGET_CHANGED;
        if (versionCode < 0 || reason == Reason.VERSION_UNKNOWN || reason == Reason.VERSION_OUTSIDE || reason == Reason.VERSION_UNVERIFIED)
            return ReplayDecision.STOP_UNVERIFIED_VERSION;
        if (ruleId == RuleId.NONE || candidateCount == 0 || !targetMatched || reason == Reason.NO_MATCH || reason == Reason.TARGET_MISMATCH
                || reason == Reason.NOT_VISIBLE || reason == Reason.NOT_ENABLED || reason == Reason.WRONG_PACKAGE)
            return ReplayDecision.NO_CANDIDATE;
        if (!clickable || reason == Reason.NODE_ACTION_REJECTED || reason == Reason.GESTURE_DISABLED)
            return ReplayDecision.STOP_NOT_CLICKABLE;
        if (!sceneMatched || reason == Reason.ANCESTOR_MISMATCH || reason == Reason.NO_AD_EVIDENCE || reason == Reason.CONTEXT_MISMATCH)
            return ReplayDecision.REVIEW_CONTEXT;
        return ReplayDecision.REVIEW_NODE_ACTION;
    }

    public static Set<String> expectedFields() { return FIELDS; }

    private static long integer(Object value, String field) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long))
            throw invalid(field);
        return ((Number) value).longValue();
    }

    private static boolean bool(Object value, String field) {
        if (!(value instanceof Boolean)) throw invalid(field);
        return (Boolean) value;
    }

    private static <E extends Enum<E>> E token(Class<E> type, Object value, String field) {
        if (!(value instanceof String)) throw invalid(field);
        try { return Enum.valueOf(type, (String) value); }
        catch (IllegalArgumentException unknown) { throw invalid(field); }
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("Invalid diagnostic decision input: " + field);
    }
}
