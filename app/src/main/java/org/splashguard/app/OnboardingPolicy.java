package org.splashguard.app;

/** Migration and resume decisions, isolated from Android UI and permissions. */
public final class OnboardingPolicy {
    public static final int STEP_COUNT=5;
    private OnboardingPolicy(){}
    public static boolean shouldOpen(boolean alreadySeen,boolean legacySettings,boolean inProgress){
        return inProgress||(!alreadySeen&&!legacySettings);
    }
    public static int clampStep(int step){return Math.max(0,Math.min(STEP_COUNT-1,step));}
}
