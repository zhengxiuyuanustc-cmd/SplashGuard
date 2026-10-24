package org.splashguard.app;

import java.util.Locale;

/** Pure decision rules. No broad X/close matching, OCR, or fixed coordinates. */
public final class AdRules {
    private AdRules() {}
    public static String normalize(String s) { return s == null ? "" : s.replaceAll("[\\s\\u00a0\\u200b]+", "").toLowerCase(Locale.ROOT); }
    public static boolean adEvidence(String text) {
        String s = normalize(text);
        return s.contains("广告") || s.contains("摇一摇") || s.contains("扭一扭") || s.contains("扭动手机") || s.equals("advertisement") || s.equals("sponsored");
    }
    public static boolean biliSplashMarker(String id, String className) {
        String key = id == null ? "" : id;
        String cls = className == null ? "" : className;
        return cls.startsWith("tv.danmaku.bili.splash.ad.widget.")
                || key.equals("tv.danmaku.bili:id/splash_interact_view")
                || key.equals("tv.danmaku.bili:id/splash_container")
                || key.equals("tv.danmaku.bili:id/splash_button_container")
                || key.equals("tv.danmaku.bili:id/full_brand_splash");
    }
    /** Bili 9.15 separates the skip label and countdown; the control may be at the bottom. */
    public static boolean biliCandidate(String pkg, String text, String id, boolean splash,
                                         int l, int t, int r, int b, int w, int h) {
        if (!"tv.danmaku.bili".equals(pkg) || !splash || w <= 0 || h <= 0) return false;
        if (l < 0 || t < 0 || r > w || b > h || r <= l || b <= t) return false;
        if ((l+r)/2 < w*.55 || r-l > w*.45 || b-t > h*.12) return false;
        if (!(b < h*.4 || t > h*.72)) return false;
        String label = normalize(text);
        boolean skipLabel = label.matches("(?:[0-9]{1,2}[s秒]?[|·]?)?跳过(?:广告)?(?:[|·]?[0-9]{1,2}[s秒]?)?");
        boolean title = "tv.danmaku.bili:id/countdown_title".equals(id);
        boolean combined = "tv.danmaku.bili:id/count_down".equals(id);
        return (title && skipLabel) || (combined && skipLabel);
    }
    public static boolean safeButtonBounds(int l,int t,int r,int b,int w,int h) {
        return l>=0 && t>=0 && r<=w && b<=h && r>l && b>t && r-l<=w*.45 && b-t<=h*.12;
    }
    public static boolean candidate(String text, String id, boolean evidence,
                                    int left, int top, int right, int bottom, int width, int height) {
        if (width <= 0 || height <= 0 || left < 0 || top < 0 || right > width || bottom > height || right <= left || bottom <= top) return false;
        // Opening ads generally place skip in the top right; prefer missing one over touching content.
        if (left < width * .45 || bottom > height * .35 || right - left > width * .5 || bottom - top > height * .15) return false;
        String s = normalize(text), key = normalize(id);
        if (s.contains("下载") || s.contains("安装") || s.contains("允许") || s.contains("同意") || s.contains("领取") || s.contains("打开")) return false;
        boolean explicit = s.matches("(?:[0-9]{1,2}[s秒]?[:|·]?)?(?:跳过广告|关闭广告|skipad|skipads)(?:[（(]?[0-9]{1,2}[s秒]?[)）]?)?");
        boolean skip = s.matches("(?:[0-9]{1,2}[s秒]?[:|·]?)?(?:跳过|skip)(?:[（(]?[0-9]{1,2}[s秒]?[)）]?)?");
        boolean sdk = key.matches(".*(?:tt_splash_skip_btn|tt_splash_skip|gdt_splash_skip|ksad_splash_skip|splash_skip_view|splash_skip_button|splash_skip_btn|splash_skip).* ".trim());
        boolean countdown = s.matches(".*[0-9]{1,2}.*");
        return explicit || (evidence && ((skip && countdown) || sdk));
    }
}
