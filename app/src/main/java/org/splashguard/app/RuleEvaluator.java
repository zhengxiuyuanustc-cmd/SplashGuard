package org.splashguard.app;

import java.util.List;

/** Evaluates finite attributes; callers still enforce selection, bounds and session budgets. */
public final class RuleEvaluator {
    private RuleEvaluator() {}
    public enum Reason {
        NO_MATCH, NOT_VISIBLE, NOT_ENABLED, WRONG_PACKAGE, VERSION_UNKNOWN, VERSION_OUTSIDE,
        TARGET_MISMATCH, ANCESTORS_INCOMPLETE, ANCESTOR_MISMATCH, EXCLUDED, UNSAFE_LABEL,
        NO_AD_EVIDENCE, MATCH_VERIFIED, MATCH_UNVERIFIED
    }
    public static final class VersionInfo {
        public final long code;
        public final String name;
        public VersionInfo(long code,String name) {
            if(code < -1)throw new IllegalArgumentException("Unknown version code is -1");
            this.code=code; this.name=safe(name);
        }
    }
    public static final class Node {
        public final String packageName,viewId,className,text,description;
        public final boolean visible,enabled,clickable;
        public Node(String pkg,String id,String cls,String text,String description,boolean visible,boolean enabled,boolean clickable) {
            packageName=safe(pkg);viewId=safe(id);className=safe(cls);this.text=safe(text);this.description=safe(description);
            this.visible=visible;this.enabled=enabled;this.clickable=clickable;
        }
    }
    public static final class Result {
        public final String ruleId;
        public final Reason reason;
        public final boolean matched,versionVerified,gestureAllowed;
        private Result(String id,Reason reason,boolean matched,boolean verified,boolean gesture) {
            ruleId=id;this.reason=reason;this.matched=matched;versionVerified=verified;gestureAllowed=gesture;
        }
    }
    private static Result no(String id,Reason reason) { return new Result(id,reason,false,false,false); }

    /** Ancestors must be immediate-parent first and never exceed 32 entries. */
    public static Result evaluate(RuleSpec rule,VersionInfo version,Node node,List<Node> ancestors,
                                  boolean ancestorsComplete,boolean adEvidence) {
        if(rule==null)throw new IllegalArgumentException("Rule required");
        String id=rule.id;
        if(node==null)return no(id,Reason.NO_MATCH);
        if(!node.visible)return no(id,Reason.NOT_VISIBLE);
        if(!node.enabled)return no(id,Reason.NOT_ENABLED);
        if(!rule.packageName.equals(node.packageName))return no(id,Reason.WRONG_PACKAGE);
        if(unsafe(node))return no(id,Reason.UNSAFE_LABEL);
        if(version==null)version=new VersionInfo(-1,"");
        if(rule.minVersionCode>=0) {
            if(version.code<0)return no(id,Reason.VERSION_UNKNOWN);
            if(version.code<rule.minVersionCode||version.code>rule.maxVersionCode)return no(id,Reason.VERSION_OUTSIDE);
        }
        if(!rule.versionNames.isEmpty()) {
            if(version.name.isEmpty())return no(id,Reason.VERSION_UNKNOWN);
            if(!rule.versionNames.contains(version.name))return no(id,Reason.VERSION_OUTSIDE);
        }
        if(!rule.target.matches(node))return no(id,Reason.TARGET_MISMATCH);
        if(ancestors==null || ancestors.size()>RuleSpec.MAX_ANCESTORS)
            return no(id,Reason.ANCESTORS_INCOMPLETE);
        if(rule.hasContextConstraints()&&!ancestorsComplete)return no(id,Reason.ANCESTORS_INCOMPLETE);
        for(Node a:ancestors)if(a==null||!rule.packageName.equals(a.packageName))return no(id,Reason.ANCESTOR_MISMATCH);
        for(RuleSpec.NodePattern exclusion:rule.excludes) {
            if(exclusion.matches(node))return no(id,Reason.EXCLUDED);
            for(Node a:ancestors)if(exclusion.matches(a))return no(id,Reason.EXCLUDED);
        }
        if(ancestors.size()<rule.parentPath.size())return no(id,Reason.ANCESTOR_MISMATCH);
        for(int i=0;i<rule.parentPath.size();i++)
            if(!rule.parentPath.get(i).matches(ancestors.get(i)))return no(id,Reason.ANCESTOR_MISMATCH);
        if(rule.container!=null) {
            boolean found=false;
            for(int i=0;i<Math.min(ancestors.size(),rule.containerMaxDepth);i++)
                if(rule.container.matches(ancestors.get(i))) { found=true;break; }
            if(!found)return no(id,Reason.ANCESTOR_MISMATCH);
        }
        if(rule.requireAdEvidence&&!adEvidence)return no(id,Reason.NO_AD_EVIDENCE);
        boolean verified=rule.hasVersionConstraint();
        return new Result(id,verified?Reason.MATCH_VERIFIED:Reason.MATCH_UNVERIFIED,true,verified,
                verified&&rule.verifiedGestureCompatibility);
    }

    /** Existing AdRules hit remains a low-confidence, node-only hint, never gesture authority. */
    public static Result genericCandidate(boolean legacyCandidate,Node node) {
        String id="generic.splash";
        if(!legacyCandidate||node==null)return no(id,Reason.NO_MATCH);
        if(!node.visible)return no(id,Reason.NOT_VISIBLE);
        if(!node.enabled)return no(id,Reason.NOT_ENABLED);
        if(node.packageName.isEmpty())return no(id,Reason.WRONG_PACKAGE);
        if(unsafe(node))return no(id,Reason.UNSAFE_LABEL);
        return new Result(id,Reason.MATCH_UNVERIFIED,true,false,false);
    }
    private static boolean unsafe(Node node) {
        String s=AdRules.normalize(node.text)+"|"+AdRules.normalize(node.description);
        String[] terms={"下载","安装","允许","同意","领取","打开","授权","支付","登录","关注",
                "download","install","allow","accept","open","authorize","payment","login","signin","follow"};
        for(String term:terms)if(s.contains(term))return true;
        return false;
    }
    private static String safe(String value) { return value==null?"":value; }
}
