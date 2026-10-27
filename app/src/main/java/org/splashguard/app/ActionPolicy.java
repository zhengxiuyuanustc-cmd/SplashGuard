package org.splashguard.app;

/** Explicit action type. No implicit coordinate fallback for generic/unverified matches. */
public final class ActionPolicy {
    private ActionPolicy() {}
    public enum Action { NONE, NODE, GESTURE }
    /** Coordinate dispatch is paused: no maintained numeric-version and reliable occlusion evidence exists. */
    public static boolean gesturesAvailable() { return false; }
    public static Action choose(RuleEvaluator.Result result,boolean clickable,boolean nodeActionRejected,int attempt) {
        if(result==null||!result.matched||attempt<1||attempt>SkipSession.MAX_ATTEMPTS)return Action.NONE;
        boolean compat=gesturesAvailable()&&result.versionVerified&&result.gestureAllowed;
        if(nodeActionRejected)return compat?Action.GESTURE:Action.NONE;
        if(attempt>1&&compat)return Action.GESTURE;
        if(clickable)return Action.NODE;
        return compat?Action.GESTURE:Action.NONE;
    }
}
