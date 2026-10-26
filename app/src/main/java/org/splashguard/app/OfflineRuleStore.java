package org.splashguard.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory, data-only restrictions over an already reviewed built-in catalog.
 * This class performs no IO and does not authenticate a pack's author/signature.
 * Imported fields can only tighten the current effective rule limits. They cannot
 * create a selector, add a package, enable an unreviewed gesture, or change Prefs.
 */
public final class OfflineRuleStore {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_RULES = 128;
    public static final int MAX_PACK_BYTES = 65536;
    public static final int MAX_PACK_VERSION = 1000000;
    /** Existing user-selected generic matcher scope, not a wildcard over installed apps. */
    public static final String SELECTED_APPS = "@selected";

    public enum FieldKind { PACK, RULE }

    private static final Set<String> PACK_FIELDS = fields("schemaVersion", "id", "version", "rules");
    private static final Set<String> RULE_FIELDS = fields("id", "packageName", "minVersionCode", "maxVersionCode",
            "windowMs", "maxActions", "enabled", "allowGesture");

    /** Immutable limits; matching selectors remain exclusively in the built-in catalog. */
    public static final class RuleLimit {
        public final String id;
        public final String packageName;
        public final long minVersionCode;
        public final long maxVersionCode;
        public final int windowMs;
        public final int maxActions;
        public final boolean enabled;
        public final boolean allowGesture;

        public RuleLimit(String id, String packageName, long minVersionCode, long maxVersionCode,
                         int windowMs, int maxActions, boolean enabled, boolean allowGesture) {
            this.id = requireId(id);
            boolean selectedScope = "generic.splash".equals(id) && SELECTED_APPS.equals(packageName);
            if (packageName == null || packageName.length() > 160
                    || (!selectedScope && !packageName.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")))
                throw invalid("packageName");
            if (minVersionCode < 0 || maxVersionCode < minVersionCode) throw invalid("version range");
            if (windowMs < 1 || windowMs > SkipSession.WINDOW_MS) throw invalid("windowMs");
            if (maxActions < 1 || maxActions > SkipSession.MAX_ATTEMPTS) throw invalid("maxActions");
            this.packageName = packageName;
            this.minVersionCode = minVersionCode;
            this.maxVersionCode = maxVersionCode;
            this.windowMs = windowMs;
            this.maxActions = maxActions;
            this.enabled = enabled;
            this.allowGesture = allowGesture;
        }

        /** Unknown version codes never satisfy an imported version range. */
        public boolean allowsVersion(long versionCode) {
            return enabled && versionCode >= 0 && versionCode >= minVersionCode && versionCode <= maxVersionCode;
        }
    }

    /** A pack is untrusted input until apply validates every entry against the catalog/current state. */
    public static final class Pack {
        public final int schemaVersion;
        public final String id;
        public final int version;
        public final List<RuleLimit> rules;

        public Pack(int schemaVersion, String id, int version, List<RuleLimit> rules) {
            if (schemaVersion != SCHEMA_VERSION) throw invalid("schemaVersion");
            this.id = requireId(id);
            if (version < 1 || version > MAX_PACK_VERSION) throw invalid("pack version");
            this.rules = immutableRules(rules);
            this.schemaVersion = schemaVersion;
            this.version = version;
        }
    }

    private static final class State {
        final LinkedHashMap<String, RuleLimit> effective;
        final Pack pack;
        State(Map<String, RuleLimit> rules, Pack pack) {
            effective = new LinkedHashMap<>(rules);
            this.pack = pack;
        }
    }

    private final LinkedHashMap<String, RuleLimit> builtIns;
    private State current;
    private State previous;

    public OfflineRuleStore(List<RuleLimit> reviewedBuiltIns) {
        builtIns = index(immutableRules(reviewedBuiltIns));
        current = new State(builtIns, null);
    }

    /** The parser must call this before accepting a file, regardless of character count. */
    public static void requireInputSize(int byteCount) {
        if (byteCount < 1 || byteCount > MAX_PACK_BYTES) throw invalid("pack byte size");
    }

    /** Rejects unknown fields and missing mandatory fields before JSON values are converted. */
    public static void requireFields(Set<String> actual, FieldKind kind) {
        if (kind == null || actual == null || !actual.equals(kind == FieldKind.PACK ? PACK_FIELDS : RULE_FIELDS))
            throw invalid("fields");
    }

    public static Set<String> expectedFields(FieldKind kind) {
        if (kind == null) throw invalid("field kind");
        return kind == FieldKind.PACK ? PACK_FIELDS : RULE_FIELDS;
    }

    public synchronized RuleLimit effective(String ruleId) { return current.effective.get(ruleId); }

    public synchronized List<RuleLimit> effectiveRules() {
        return Collections.unmodifiableList(new ArrayList<>(current.effective.values()));
    }

    /** Canonical pack contains the full effective restrictions, suitable for local persistence. */
    public synchronized Pack currentPack() { return current.pack; }

    /** Null means the built-in baseline when canRollback is true, otherwise no undo is retained. */
    public synchronized Pack previousPack() { return previous == null ? null : previous.pack; }

    public synchronized boolean canRollback() { return previous != null; }

    /**
     * Restore canonical persisted state without bypassing restrictions. The caller stores
     * currentPack, previousPack and canRollback separately; null previous + true means
     * rollback to the trusted built-in baseline. All validation precedes state changes.
     */
    public synchronized void restoreValidatedState(Pack savedCurrent, Pack savedPrevious, boolean hasPrevious) {
        if ((!hasPrevious && savedPrevious != null) || (hasPrevious && savedCurrent == null))
            throw invalid("persisted undo state");
        requireCanonical(savedCurrent);
        requireCanonical(savedPrevious);
        OfflineRuleStore restored = new OfflineRuleStore(new ArrayList<>(builtIns.values()));
        if (hasPrevious && savedPrevious != null) restored.apply(savedPrevious);
        if (savedCurrent != null) restored.apply(savedCurrent);
        if (!hasPrevious) restored.previous = null;
        current = restored.current;
        previous = restored.previous;
    }

    /** Atomic: no state changes occur if any rule is invalid or would loosen an existing limit. */
    public synchronized void apply(Pack pack) {
        if (pack == null) throw invalid("pack");
        if (current.pack != null && current.pack.id.equals(pack.id) && pack.version <= current.pack.version)
            throw invalid("pack must have a newer version");
        LinkedHashMap<String, RuleLimit> next = new LinkedHashMap<>(current.effective);
        for (RuleLimit limit : pack.rules) {
            RuleLimit baseline = builtIns.get(limit.id);
            RuleLimit active = current.effective.get(limit.id);
            if (baseline == null || active == null) throw invalid("unknown built-in rule");
            requireTightening(limit, baseline);
            requireTightening(limit, active);
            next.put(limit.id, limit);
        }
        Pack canonical = new Pack(pack.schemaVersion, pack.id, pack.version, new ArrayList<>(next.values()));
        State replacement = new State(next, canonical);
        previous = current;
        current = replacement;
    }

    /** One explicit undo; a second rollback cannot silently toggle forward again. */
    public synchronized boolean rollback() {
        if (previous == null) return false;
        current = previous;
        previous = null;
        return true;
    }

    /** Explicit user recovery path. Only rule data is reset; no application settings are accepted here. */
    public synchronized void restoreBuiltIns() {
        current = new State(builtIns, null);
        previous = null;
    }

    private static void requireTightening(RuleLimit value, RuleLimit base) {
        if (!base.id.equals(value.id) || !base.packageName.equals(value.packageName)
                || value.minVersionCode < base.minVersionCode || value.maxVersionCode > base.maxVersionCode
                || value.windowMs > base.windowMs || value.maxActions > base.maxActions
                || (value.enabled && !base.enabled) || (value.allowGesture && !base.allowGesture))
            throw invalid("restriction would broaden a built-in/current rule");
    }

    private void requireCanonical(Pack pack) {
        if (pack == null) return;
        if (pack.rules.size() != builtIns.size()) throw invalid("persisted pack is not a complete snapshot");
        for (RuleLimit rule : pack.rules) if (!builtIns.containsKey(rule.id)) throw invalid("persisted rule ID");
    }

    private static List<RuleLimit> immutableRules(List<RuleLimit> rules) {
        if (rules == null || rules.isEmpty() || rules.size() > MAX_RULES) throw invalid("rule count");
        List<RuleLimit> copy = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (RuleLimit rule : rules) {
            if (rule == null || !ids.add(rule.id)) throw invalid("duplicate/null rule");
            copy.add(rule);
        }
        return Collections.unmodifiableList(copy);
    }

    private static LinkedHashMap<String, RuleLimit> index(List<RuleLimit> rules) {
        LinkedHashMap<String, RuleLimit> result = new LinkedHashMap<>();
        for (RuleLimit rule : rules) result.put(rule.id, rule);
        return result;
    }

    private static String requireId(String value) {
        if (value == null || value.length() > 80 || !value.matches("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*"))
            throw invalid("id");
        return value;
    }

    private static Set<String> fields(String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(names)));
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("Invalid offline rule data: " + field);
    }
}
