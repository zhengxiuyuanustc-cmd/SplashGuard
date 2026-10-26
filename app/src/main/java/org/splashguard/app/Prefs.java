package org.splashguard.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("splashguard", Context.MODE_PRIVATE); }
    static Set<String> selected(Context c) { return new HashSet<>(get(c).getStringSet("apps", new HashSet<String>())); }
    static boolean guideOnly(Context c) { return get(c).getBoolean("guide_only", false); }
    static boolean enabled(Context c) { return DeviceGuidePolicy.effectiveEnabled(get(c).getBoolean("enabled", false),guideOnly(c)); }
    static boolean setGuideOnly(Context c,boolean guideOnly) {
        DeviceGuidePolicy.GuideModeChange choice=DeviceGuidePolicy.confirmedGuideMode(guideOnly);
        return get(c).edit().putBoolean("guide_only",choice.guideOnly).putBoolean("enabled",choice.enabled).commit();
    }
    static void record(Context c, String pkg, String button) {
        SharedPreferences p = get(c);
        p.edit().putInt("count", p.getInt("count", 0) + 1).putString("last", pkg + " · " + button).putLong("last_time", System.currentTimeMillis()).apply();
    }
}
