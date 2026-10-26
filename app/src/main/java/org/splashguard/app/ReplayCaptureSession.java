package org.splashguard.app;

/** User-armed diagnostic window, independent from and unable to extend the action session. */
public final class ReplayCaptureSession {
    public static final long WAIT_MS=120000,WINDOW_MS=12000;
    public enum State { IDLE,WAITING,CAPTURING,FINISHED }
    private State state=State.IDLE;private long deadline;private String target="";
    public void arm(long now){if(now<0)throw new IllegalArgumentException();state=State.WAITING;deadline=plus(now,WAIT_MS);target="";}
    public State state(long now){if(now<0)throw new IllegalArgumentException();if((state==State.WAITING||state==State.CAPTURING)&&now>=deadline)state=State.FINISHED;return state;}
    public boolean accepts(String pkg,long now){State current=state(now);if(pkg==null||pkg.isEmpty()||current==State.IDLE||current==State.FINISHED)return false;
        if(current==State.WAITING){target=pkg;state=State.CAPTURING;deadline=plus(now,WINDOW_MS);return true;}
        if(!target.equals(pkg)){state=State.FINISHED;return false;}return true;
    }
    public void finish(){if(state!=State.IDLE)state=State.FINISHED;}
    public void clear(){state=State.IDLE;target="";deadline=0;}
    private static long plus(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
}
