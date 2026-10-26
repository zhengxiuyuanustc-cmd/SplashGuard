package org.splashguard.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import java.util.List;

/** Query only our entry. An enabled-list result never substitutes for an actual connection. */
final class ServiceStatus {
    private ServiceStatus(){}
    static DeviceGuidePolicy.ServiceState query(Context context){
        ComponentName own=new ComponentName(context,SkipService.class);
        try{
            AccessibilityManager manager=(AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> list=manager==null?null:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if(list!=null){for(AccessibilityServiceInfo item:list){if(item==null)continue;ResolveInfo resolve=item.getResolveInfo();ServiceInfo info=resolve==null?null:resolve.serviceInfo;
                if(info!=null&&own.equals(new ComponentName(info.packageName,info.name)))return DeviceGuidePolicy.ServiceState.ON;}
                return DeviceGuidePolicy.ServiceState.OFF;}
        }catch(RuntimeException unknown){/* A restricted or unavailable query stays unknown unless the fallback succeeds. */}
        try{String raw=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(raw==null)return DeviceGuidePolicy.ServiceState.UNKNOWN;
            for(String token:raw.split(":"))if(own.equals(ComponentName.unflattenFromString(token)))return DeviceGuidePolicy.ServiceState.ON;
            return DeviceGuidePolicy.ServiceState.OFF;
        }catch(RuntimeException unknown){return DeviceGuidePolicy.ServiceState.UNKNOWN;}
    }
}
