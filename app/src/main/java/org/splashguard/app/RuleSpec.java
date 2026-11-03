package org.splashguard.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Finite, data-only rule definition. Not a script or a claim of button authenticity. */
public final class RuleSpec {
    public static final int MAX_ANCESTORS = 32;
    public final String id, packageName;
    public final long minVersionCode, maxVersionCode;
    public final List<String> versionNames;
    public final NodePattern target, container;
    public final List<NodePattern> parentPath, excludes;
    public final int containerMaxDepth;
    public final boolean requireAdEvidence, verifiedGestureCompatibility;

    private RuleSpec(Builder b) {
        id = token(b.id, "rule id", 64);
        if (!id.matches("[a-z][a-z0-9_.-]*")) throw new IllegalArgumentException("Invalid rule id");
        packageName = token(b.packageName, "package", 200);
        if (!packageName.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+"))
            throw new IllegalArgumentException("Exact package required");
        minVersionCode = b.minVersionCode; maxVersionCode = b.maxVersionCode;
        if ((minVersionCode == -1) != (maxVersionCode == -1) || minVersionCode < -1
                || (minVersionCode != -1 && maxVersionCode < minVersionCode))
            throw new IllegalArgumentException("Invalid version range");
        versionNames = strings(b.versionNames, 16, 128);
        target = b.target;
        if (target == null || !target.hasTargetIdentity()) throw new IllegalArgumentException("Specific target required");
        parentPath = patterns(b.parentPath, MAX_ANCESTORS);
        excludes = patterns(b.excludes, 16);
        container = b.container; containerMaxDepth = b.containerMaxDepth;
        if (container != null && (containerMaxDepth < 1 || containerMaxDepth > MAX_ANCESTORS))
            throw new IllegalArgumentException("Invalid container depth");
        requireAdEvidence = b.requireAdEvidence;
        verifiedGestureCompatibility = b.verifiedGestureCompatibility;
        if (verifiedGestureCompatibility && (!hasVersionConstraint()
                || (container == null && parentPath.isEmpty()) || target.ids.isEmpty()))
            throw new IllegalArgumentException("Gesture compatibility needs version, target id and ancestor evidence");
    }

    public boolean hasVersionConstraint() { return minVersionCode >= 0 || !versionNames.isEmpty(); }
    public boolean hasContextConstraints() { return container != null || !parentPath.isEmpty() || !excludes.isEmpty(); }
    public static Builder builder(String id, String packageName) { return new Builder(id, packageName); }

    public static final class Builder {
        private final String id, packageName;
        private long minVersionCode = -1, maxVersionCode = -1;
        private List<String> versionNames = Collections.emptyList();
        private NodePattern target, container;
        private List<NodePattern> parentPath = Collections.emptyList();
        private final List<NodePattern> excludes = new ArrayList<>();
        private int containerMaxDepth;
        private boolean requireAdEvidence = true, verifiedGestureCompatibility;
        private Builder(String id, String packageName) { this.id=id; this.packageName=packageName; }
        public Builder versions(long min, long max) { minVersionCode=min; maxVersionCode=max; return this; }
        public Builder versionNames(String... names) { versionNames=copy(names); return this; }
        public Builder target(NodePattern value) { target=value; return this; }
        /** Direct parent first; each supplied pattern must match the corresponding depth. */
        public Builder parentPath(NodePattern... path) { parentPath=copy(path); return this; }
        public Builder container(NodePattern value, int maxDepth) { container=value; containerMaxDepth=maxDepth; return this; }
        /** Any match on the target or supplied ancestors vetoes the rule. */
        public Builder exclude(NodePattern... values) { excludes.addAll(copy(values)); return this; }
        public Builder requireAdEvidence(boolean value) { requireAdEvidence=value; return this; }
        /** Only maintained, reviewed built-ins may enable this; imports must not elevate it. */
        public Builder verifiedGestureCompatibility(boolean value) { verifiedGestureCompatibility=value; return this; }
        public RuleSpec build() { return new RuleSpec(this); }
    }

    public static final class NodePattern {
        public final List<String> ids;
        public final String className, exactText;
        public final String textRegex;
        private final Pattern compiled;
        private final boolean biliSplash;
        private NodePattern(PatternBuilder b, boolean biliSplash) {
            ids=strings(b.ids,16,256);
            className=optional(b.className,256);
            exactText=optional(b.exactText,256);
            textRegex=optional(b.textRegex,256);
            compiled=textRegex.isEmpty()?null:Pattern.compile(textRegex);
            this.biliSplash=biliSplash;
            if (!biliSplash && ids.isEmpty() && className.isEmpty() && exactText.isEmpty() && textRegex.isEmpty())
                throw new IllegalArgumentException("Wildcard-only pattern is forbidden");
        }
        public static PatternBuilder builder() { return new PatternBuilder(); }
        public static NodePattern idIn(String... ids) { return builder().idIn(ids).build(); }
        /** Uses the existing exact Bili scene-marker rules on the ancestor itself. */
        public static NodePattern biliSplashContainer() { return new NodePattern(new PatternBuilder(),true); }
        public boolean hasTargetIdentity() { return !ids.isEmpty() || !exactText.isEmpty() || compiled!=null; }
        boolean matches(RuleEvaluator.Node n) {
            if (n == null) return false;
            if (biliSplash && !AdRules.biliSplashMarker(n.viewId,n.className)) return false;
            if (!ids.isEmpty() && !ids.contains(n.viewId)) return false;
            if (!className.isEmpty() && !className.equals(n.className)) return false;
            if (!exactText.isEmpty() && !AdRules.normalize(exactText).equals(AdRules.normalize(n.text))
                    && !AdRules.normalize(exactText).equals(AdRules.normalize(n.description))) return false;
            return compiled==null || compiled.matcher(AdRules.normalize(n.text)).matches()
                    || compiled.matcher(AdRules.normalize(n.description)).matches();
        }
    }

    public static final class PatternBuilder {
        private List<String> ids=Collections.emptyList();
        private String className="", exactText="", textRegex="";
        public PatternBuilder idIn(String... values) { ids=copy(values); return this; }
        public PatternBuilder className(String value) { className=value; return this; }
        public PatternBuilder textExact(String value) { exactText=value; return this; }
        /** Trusted Java-source constants only; never expose arbitrary imported regexes. */
        public PatternBuilder textRegex(String value) { textRegex=value; return this; }
        public NodePattern build() { return new NodePattern(this,false); }
    }

    private static String token(String value,String label,int max) {
        if (value==null || value.isEmpty() || value.length()>max || value.indexOf('\n')>=0
                || value.indexOf('\r')>=0 || value.indexOf('\0')>=0)
            throw new IllegalArgumentException("Invalid "+label);
        return value;
    }
    private static String optional(String value,int max) { return value==null||value.isEmpty()?"":token(value,"pattern",max); }
    private static <T> List<T> copy(T[] items) {
        if (items==null) throw new IllegalArgumentException("Null list");
        return new ArrayList<>(Arrays.asList(items.clone()));
    }
    private static List<String> strings(List<String> items,int maxCount,int maxLength) {
        if (items.size()>maxCount) throw new IllegalArgumentException("Too many values");
        List<String> result=new ArrayList<>();
        for(String item:items) result.add(token(item,"value",maxLength));
        return Collections.unmodifiableList(result);
    }
    private static List<NodePattern> patterns(List<NodePattern> items,int max) {
        if (items.size()>max) throw new IllegalArgumentException("Too many patterns");
        List<NodePattern> result=new ArrayList<>();
        for(NodePattern item:items) { if(item==null)throw new IllegalArgumentException("Null pattern"); result.add(item); }
        return Collections.unmodifiableList(result);
    }
}
