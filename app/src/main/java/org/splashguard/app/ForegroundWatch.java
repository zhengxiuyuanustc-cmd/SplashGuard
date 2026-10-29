package org.splashguard.app;

/** Short root-read retries around window transitions, independent of the ad action budget. */
public final class ForegroundWatch {
    public static final long WINDOW_MS=1600, INTERVAL_MS=120;
    public static final int MAX_PROBES=12;
    private long deadline,nextAt;
    private int probes;
    private boolean requested;
    public void request(long now){
        if(delay(now)>=0)return; // Window animations cannot postpone an existing probe window.
        requested=true;probes=0;nextAt=now;
        deadline=now>Long.MAX_VALUE-WINDOW_MS?Long.MAX_VALUE:now+WINDOW_MS;
    }
    public boolean take(long now){
        if(delay(now)!=0)return false;
        probes++;nextAt=now>Long.MAX_VALUE-INTERVAL_MS?Long.MAX_VALUE:now+INTERVAL_MS;
        return true;
    }
    public long delay(long now){
        if(!requested||probes>=MAX_PROBES||now>=deadline||nextAt>=deadline)return -1;
        return Math.max(0,nextAt-now);
    }
    public void clear(){requested=false;deadline=nextAt=0;probes=0;}
}
