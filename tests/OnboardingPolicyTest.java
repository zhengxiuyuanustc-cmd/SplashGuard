import org.splashguard.app.OnboardingPolicy;

public final class OnboardingPolicyTest {
    private static int passed;
    private static void check(String name,boolean value){if(!value)throw new AssertionError(name);passed++;}
    public static void main(String[] args){
        check("fresh install enters setup",OnboardingPolicy.shouldOpen(false,false,false));
        check("old configured user is not interrupted",!OnboardingPolicy.shouldOpen(false,true,false));
        check("completed user goes home",!OnboardingPolicy.shouldOpen(true,true,false));
        check("later choice stays respected without settings",!OnboardingPolicy.shouldOpen(true,false,false));
        check("unfinished setup resumes after selecting apps",OnboardingPolicy.shouldOpen(false,true,true));
        check("explicit replay remains open",OnboardingPolicy.shouldOpen(true,true,true));
        check("negative corrupt step returns first",OnboardingPolicy.clampStep(-10)==0);
        check("first step preserved",OnboardingPolicy.clampStep(0)==0);
        check("middle step preserved",OnboardingPolicy.clampStep(2)==2);
        check("last step preserved",OnboardingPolicy.clampStep(4)==4);
        check("oversized step stays in flow",OnboardingPolicy.clampStep(100)==4);
        System.out.println("OnboardingPolicy: "+passed+" checks passed");
    }
}
