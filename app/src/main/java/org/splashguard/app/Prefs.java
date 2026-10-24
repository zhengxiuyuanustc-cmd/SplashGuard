package org.splashguard.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("splashguard", Context.MODE_PRIVATE); }
    static Set<String> selected(Context c) { return new HashSet<>(get(c).getStringSet("apps", new HashSet<String>())); }
    static boolean enabled(Context c) { return get(c).getBoolean("enabled", false); }
    static void record(Context c, String pkg, String button) {
        SharedPreferences p = get(c);
        p.edit().putInt("count", p.getInt("count", 0) + 1).putString("last", pkg + " · " + button).putLong("last_time", System.currentTimeMillis()).apply();
    }
}
