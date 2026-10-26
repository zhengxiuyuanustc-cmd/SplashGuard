package org.splashguard.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A user-reviewed list of this tool's selected applications, never a Preferences dump.
 * Decoding performs no Android actions. The caller must preview installed targets and
 * restore with automatic skipping paused; no permission or compatibility flag is accepted.
 */
public final class ConfigBackup {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_APPS = 128;
    public static final int MAX_PACKAGE_LENGTH = 160;
    public static final int MAX_BYTES = OfflineRuleStore.MAX_PACK_BYTES;
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final Set<String> FIELDS = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList("schemaVersion", "selectedApps")));

    /** Sorted, immutable, exact package names. Labels, installed-app inventories and records are absent. */
    public final List<String> selectedApps;

    public ConfigBackup(Collection<String> selectedApps) {
        if (selectedApps == null || selectedApps.size() > MAX_APPS) throw invalid("application count");
        List<String> copy = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String pkg : selectedApps) {
            if (copy.size() >= MAX_APPS) throw invalid("application count");
            if (pkg == null || pkg.length() > MAX_PACKAGE_LENGTH || !PACKAGE.matcher(pkg).matches())
                throw invalid("exact package name required");
            if (!seen.add(pkg)) throw invalid("duplicate application");
            copy.add(pkg);
        }
        Collections.sort(copy);
        this.selectedApps = Collections.unmodifiableList(copy);
    }

    public String toJson() { return encode(this); }

    public static String encode(ConfigBackup backup) {
        if (backup == null) throw invalid("backup required");
        Map<String,Object> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", SCHEMA_VERSION);
        fields.put("selectedApps", backup.selectedApps);
        return RuleJsonCodec.writeObject(fields);
    }

    public static ConfigBackup decode(String json) {
        Map<String,Object> fields = RuleJsonCodec.readObject(json);
        if (!fields.keySet().equals(FIELDS)) throw invalid("fields");
        Object version = fields.get("schemaVersion");
        if (!(version instanceof Long) || ((Long) version).longValue() != SCHEMA_VERSION)
            throw invalid("schemaVersion");
        Object input = fields.get("selectedApps");
        if (!(input instanceof List)) throw invalid("selectedApps must be an array");
        List<?> array = (List<?>) input;
        if (array.size() > MAX_APPS) throw invalid("application count");
        List<String> apps = new ArrayList<>();
        for (Object entry : array) {
            if (!(entry instanceof String)) throw invalid("package must be a string");
            apps.add((String) entry);
        }
        return new ConfigBackup(apps);
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("Invalid application-selection backup: " + field);
    }
}
