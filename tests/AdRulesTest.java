import org.splashguard.app.AdRules;

public class AdRulesTest {
    private static int passed;
    private static void check(String name, boolean actual, boolean expected) { if(actual!=expected)throw new AssertionError(name); passed++; }
    private static boolean rule(String t,String id,boolean ad) { return AdRules.candidate(t,id,ad,840,120,1080,230,1200,2600); }
    public static void main(String[] args) {
        check("explicit ad skip",rule("跳过广告","",false),true);
        check("countdown",rule("跳过 5s","",true),true);
        check("leading countdown",rule("5 | 跳过","",true),true);
        check("ad close",rule("关闭广告","",false),true);
        check("english",rule("Skip Ad","",false),true);
        check("known sdk",rule("","com.sdk:id/tt_splash_skip_btn",true),true);
        check("no ad context",rule("跳过","",false),false);
        check("onboarding beside an ad label",rule("跳过","",true),false);
        check("onboarding",rule("跳过教程","",true),false);
        check("plain close",rule("关闭","",true),false);
        check("x",rule("×","",true),false);
        check("download",rule("跳过广告并下载","",true),false);
        check("consent",rule("同意并跳过广告","",true),false);
        check("open",rule("打开广告","",true),false);
        check("sdk without evidence",rule("","splash_skip_button",false),false);
        check("bottom button",AdRules.candidate("跳过广告","",true,850,2000,1100,2100,1200,2600),false);
        check("offscreen",AdRules.candidate("跳过广告","",true,850,-10,1100,100,1200,2600),false);
        check("oversized parent",AdRules.candidate("跳过广告","",true,0,0,1200,500,1200,2600),false);
        check("evidence",AdRules.adEvidence("摇一摇了解详情"),true);
        check("not evidence",AdRules.adEvidence("欢迎使用，请完成设置"),false);
        check("bili lower split skip",AdRules.biliCandidate("tv.danmaku.bili","跳过","tv.danmaku.bili:id/countdown_title",true,880,2250,1100,2330,1200,2652),true);
        check("bili combined countdown",AdRules.biliCandidate("tv.danmaku.bili","跳过5","tv.danmaku.bili:id/count_down",true,880,180,1100,280,1200,2652),true);
        check("bili requires splash",AdRules.biliCandidate("tv.danmaku.bili","跳过","tv.danmaku.bili:id/countdown_title",false,880,2250,1100,2330,1200,2652),false);
        check("bili never another package",AdRules.biliCandidate("com.other","跳过","tv.danmaku.bili:id/countdown_title",true,880,2250,1100,2330,1200,2652),false);
        check("bili unrelated skip",AdRules.biliCandidate("tv.danmaku.bili","跳过","tv.danmaku.bili:id/skip",true,880,2250,1100,2330,1200,2652),false);
        check("bili rejects middle content",AdRules.biliCandidate("tv.danmaku.bili","跳过5","tv.danmaku.bili:id/count_down",true,880,1100,1100,1200,1200,2652),false);
        check("bili rejects download",AdRules.biliCandidate("tv.danmaku.bili","跳过并下载","tv.danmaku.bili:id/count_down",true,880,180,1100,280,1200,2652),false);
        check("small skip parent",AdRules.safeButtonBounds(820,2200,1140,2400,1200,2652),true);
        check("do not click fullscreen parent",AdRules.safeButtonBounds(0,0,1200,2652,1200,2652),false);
        check("ad widget is scene marker",AdRules.biliSplashMarker("","tv.danmaku.bili.splash.ad.widget.SplashContainerView"),true);
        check("generic container not marker",AdRules.biliSplashMarker("tv.danmaku.bili:id/root_container","android.widget.FrameLayout"),false);
        check("recorded Bili ViewGroup scene",AdRules.biliSplashMarker("tv.danmaku.bili:id/splash_container","android.view.ViewGroup"),true);
        check("recorded bottom skip within real display",AdRules.biliCandidate("tv.danmaku.bili","跳过 5","tv.danmaku.bili:id/count_down",true,868,2425,1148,2565,1200,2652),true);
        check("recorded skip was rejected by old app-area height",AdRules.biliCandidate("tv.danmaku.bili","跳过 5","tv.danmaku.bili:id/count_down",true,868,2425,1148,2565,1200,2516),false);
        check("recorded bottom label still requires scene",AdRules.biliCandidate("tv.danmaku.bili","跳过 5","tv.danmaku.bili:id/count_down",false,868,2425,1148,2565,1200,2652),false);
        boolean tmallScene=AdRules.biliSplashMarker("tv.danmaku.bili:id/splash_layout","android.widget.FrameLayout");
        check("recorded Tmall FrameLayout scene",tmallScene,true);
        check("recorded Tmall lower skip",AdRules.biliCandidate("tv.danmaku.bili","跳过5","tv.danmaku.bili:id/count_down",tmallScene,868,2425,1148,2565,1200,2652),true);
        check("new scene requires recorded class",AdRules.biliSplashMarker("tv.danmaku.bili:id/splash_layout","android.view.ViewGroup"),false);
        check("new scene exact package",AdRules.biliSplashMarker("com.other:id/splash_layout","android.widget.FrameLayout"),false);
        check("new scene exact id",AdRules.biliSplashMarker("tv.danmaku.bili:id/splash_layout_extra","android.widget.FrameLayout"),false);
        check("logo alone is not a scene",AdRules.biliSplashMarker("tv.danmaku.bili:id/logo_layout","android.view.ViewGroup"),false);
        check("unidentified FrameLayout is not a scene",AdRules.biliSplashMarker("","android.widget.FrameLayout"),false);
        check("Tmall brand alone is not evidence",AdRules.adEvidence("天猫双十一"),false);
        check("new scene rejects unrelated button",AdRules.biliCandidate("tv.danmaku.bili","跳过5","tv.danmaku.bili:id/skip",tmallScene,868,2425,1148,2565,1200,2652),false);
        check("new scene rejects pure countdown",AdRules.biliCandidate("tv.danmaku.bili","5","tv.danmaku.bili:id/count_down",tmallScene,868,2425,1148,2565,1200,2652),false);
        check("new scene rejects ad destination",AdRules.biliCandidate("tv.danmaku.bili","点击进入淘宝app","tv.danmaku.bili:id/count_down",tmallScene,868,2425,1148,2565,1200,2652),false);
        check("new scene rejects center content",AdRules.biliCandidate("tv.danmaku.bili","跳过5","tv.danmaku.bili:id/count_down",tmallScene,868,1200,1148,1340,1200,2652),false);
        check("new scene rejects offscreen control",AdRules.biliCandidate("tv.danmaku.bili","跳过5","tv.danmaku.bili:id/count_down",tmallScene,868,2525,1148,2665,1200,2652),false);
        System.out.println("PASS " + passed + " decision-rule cases");
    }
}
