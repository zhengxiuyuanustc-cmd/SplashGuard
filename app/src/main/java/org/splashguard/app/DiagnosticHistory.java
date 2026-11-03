package org.splashguard.app;

/** Two in-memory snapshots per selected opening window; never a UI log or action input. */
public final class DiagnosticHistory {
    private DiagnosticSample last,detail;
    private long startedAt=-1;
    public synchronized void beginWindow(){last=null;detail=null;startedAt=-1;}
    public synchronized void beginWindow(long now){if(now<0)throw new IllegalArgumentException("Invalid time");beginWindow();startedAt=now;}
    public synchronized long age(long now){return startedAt<0||now<startedAt?-1:now-startedAt;}
    public synchronized void remember(DiagnosticSample value){
        if(value==null)throw new IllegalArgumentException("Missing sample");
        last=value;
        if(detail==null||specific(value.reason))detail=value;
    }
    private static boolean specific(DiagnosticSample.Reason reason){
        return reason!=DiagnosticSample.Reason.NO_MATCH&&reason!=DiagnosticSample.Reason.WINDOW_EXPIRED;
    }
    public synchronized DiagnosticSample last(){return last;}
    public synchronized DiagnosticSample useful(){return detail==null?last:detail;}
    public synchronized void clear(){beginWindow();}
}
