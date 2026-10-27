package org.splashguard.app;

/** Intersects offline restrictions with the hard session budget; it never starts or extends a session. */
public final class RuleRuntime {
    private RuleRuntime() {}
    public static final class Gate {
        public final boolean allowed,gestureAllowed;
        public final DiagnosticSample.Reason reason;
        public final int windowMs,maxActions;
        private Gate(boolean allowed,boolean gesture,DiagnosticSample.Reason reason,int window,int actions) {
            this.allowed=allowed;gestureAllowed=gesture;this.reason=reason;windowMs=window;maxActions=actions;
        }
    }
    /** attemptsUsed excludes the operation about to be submitted, including for a reserved Token. */
    public static Gate gate(OfflineRuleStore store,RuleCatalog.Evaluation evaluation,String pkg,long versionCode,
                            long now,long sessionDeadline,int attemptsUsed,boolean userCompatibility) {
        if(evaluation==null||!evaluation.matched||evaluation.result==null)
            return deny(evaluation==null?DiagnosticSample.Reason.NO_MATCH:reason(evaluation.reason),0,0);
        if(store==null)return deny(DiagnosticSample.Reason.SERVICE_NOT_READY,0,0);
        OfflineRuleStore.RuleLimit limit=store.effective(evaluation.ruleId);
        if(!scope(limit,pkg))return deny(DiagnosticSample.Reason.STOPPED,0,0);
        int window=limit.windowMs,actions=limit.maxActions;
        boolean gesture=ActionPolicy.gesturesAvailable()&&limit.allowGesture;
        DiagnosticSample.Reason blocked=restriction(limit,versionCode);
        // A generic label in Bili must not bypass disabled or narrowed Bili restrictions.
        if(RuleCatalog.BILI_PACKAGE.equals(pkg)&&!RuleCatalog.BILI.equals(evaluation.ruleId)) {
            OfflineRuleStore.RuleLimit bili=store.effective(RuleCatalog.BILI);
            if(!scope(bili,pkg))return deny(DiagnosticSample.Reason.STOPPED,0,0);
            window=Math.min(window,bili.windowMs);actions=Math.min(actions,bili.maxActions);
            DiagnosticSample.Reason biliBlocked=restriction(bili,versionCode);
            if(biliBlocked!=null)blocked=biliBlocked;
            gesture=false;
        }
        if(blocked!=null)return deny(blocked,window,actions);
        if(attemptsUsed<0||attemptsUsed>=actions)return deny(DiagnosticSample.Reason.STOPPED,window,actions);
        // SkipSession owns a 12-second deadline; tightened windows share its original start.
        if(sessionDeadline<SkipSession.WINDOW_MS||now<sessionDeadline-SkipSession.WINDOW_MS||now>=sessionDeadline
                ||now>=sessionDeadline-(SkipSession.WINDOW_MS-window))
            return deny(DiagnosticSample.Reason.WINDOW_EXPIRED,window,actions);
        gesture=gesture&&userCompatibility&&versionCode>=0&&evaluation.result.versionVerified&&evaluation.result.gestureAllowed;
        return new Gate(true,gesture,reason(evaluation.reason),window,actions);
    }
    public static ActionPolicy.Action action(RuleCatalog.Evaluation evaluation,Gate gate,boolean clickable,
                                              boolean nodeActionRejected,int attempt) {
        if(gate==null||!gate.allowed||evaluation==null||attempt<1||attempt>gate.maxActions)return ActionPolicy.Action.NONE;
        if(!gate.gestureAllowed) {
            if(nodeActionRejected||!clickable||attempt<1||attempt>gate.maxActions)return ActionPolicy.Action.NONE;
            return ActionPolicy.Action.NODE;
        }
        return ActionPolicy.choose(evaluation.result,clickable,nodeActionRejected,attempt);
    }
    /** Fresh selectors must still describe the same unique native node, window and geometry. */
    public static boolean sameCandidate(RuleCatalog.Evaluation expected,RuleCatalog.Evaluation fresh,
                                         boolean complete,boolean unique,boolean sameNode,boolean sameWindow,
                                         boolean sameBounds,boolean refreshed) {
        return complete&&unique&&sameNode&&sameWindow&&sameBounds&&refreshed
                &&expected!=null&&fresh!=null&&expected.matched&&fresh.matched
                &&expected.result!=null&&fresh.result!=null&&expected.ruleId.equals(fresh.ruleId)
                &&expected.knownBiliTarget==fresh.knownBiliTarget&&expected.sceneMatched==fresh.sceneMatched
                &&expected.result.versionVerified==fresh.result.versionVerified
                &&expected.result.gestureAllowed==fresh.result.gestureAllowed;
    }
    private static boolean scope(OfflineRuleStore.RuleLimit rule,String pkg) {
        return rule!=null&&pkg!=null&&!pkg.isEmpty()
                && (rule.packageName.equals(pkg)||(RuleCatalog.GENERIC.equals(rule.id)&&OfflineRuleStore.SELECTED_APPS.equals(rule.packageName)));
    }
    private static DiagnosticSample.Reason restriction(OfflineRuleStore.RuleLimit limit,long versionCode) {
        if(!limit.enabled)return DiagnosticSample.Reason.STOPPED;
        if(versionCode<0) {
            // Unknown codes may retain node-only baseline compatibility, never satisfy a narrowed range.
            if(limit.minVersionCode!=0||limit.maxVersionCode!=Long.MAX_VALUE)return DiagnosticSample.Reason.VERSION_UNKNOWN;
        } else if(!limit.allowsVersion(versionCode))return DiagnosticSample.Reason.VERSION_OUTSIDE;
        return null;
    }
    private static Gate deny(DiagnosticSample.Reason reason,int window,int actions) {return new Gate(false,false,reason,window,actions);}
    public static DiagnosticSample.Reason reason(RuleEvaluator.Reason reason) {
        return DiagnosticSample.Reason.valueOf(reason.name());
    }
}
