import org.splashguard.app.ReplayCaptureSession;
public final class ReplayCaptureSessionTest {
    private static int passed;private static void check(String n,boolean b){if(!b)throw new AssertionError(n);passed++;}
    public static void main(String[] args){ReplayCaptureSession c=new ReplayCaptureSession();
        check("not collecting by default",!c.accepts("app.one",0));c.arm(100);
        check("armed waiting",c.state(100)==ReplayCaptureSession.State.WAITING);
        check("empty package not collected",!c.accepts("",101));
        check("first selected target starts capture",c.accepts("app.one",1000));
        check("same package before edge",c.accepts("app.one",12999));
        check("same-package scans don't renew",!c.accepts("app.one",13000));
        check("finished not restarted",!c.accepts("app.one",13100));c.arm(20000);
        check("wait expires without target",!c.accepts("app.two",140000));c.arm(200000);
        check("new explicit request works",c.accepts("app.one",200001));
        check("another selected package freezes first",!c.accepts("app.two",200002));
        check("return does not rearm",!c.accepts("app.one",200003));c.clear();
        check("clear returns idle",c.state(200003)==ReplayCaptureSession.State.IDLE);c.arm(Long.MAX_VALUE-1);
        check("saturating clock",c.accepts("app",Long.MAX_VALUE-1));
        check("terminal saturated edge",!c.accepts("app",Long.MAX_VALUE));
        ReplayCaptureSession scoped=new ReplayCaptureSession();scoped.arm(1000);
        scoped.observeForeground("com.android.launcher",1100);
        check("desktop does not bind waiting request",scoped.state(1100)==ReplayCaptureSession.State.WAITING);
        scoped.screenOff(1200);
        check("screen-off preserves waiting request",scoped.state(1200)==ReplayCaptureSession.State.WAITING);
        check("first selected target still starts after screen-off",scoped.accepts("app.one",1300));
        scoped.observeForeground("app.one",1400);
        check("same foreground keeps current capture",scoped.state(1400)==ReplayCaptureSession.State.CAPTURING);
        scoped.observeForeground(null,1500);scoped.observeForeground("",1501);
        check("unknown foreground does not invent a departure",scoped.state(1501)==ReplayCaptureSession.State.CAPTURING);
        scoped.observeForeground("org.splashguard.app",1600);
        check("return to this tool finishes active capture",scoped.state(1600)==ReplayCaptureSession.State.FINISHED);
        check("return to old selected target does not restart",!scoped.accepts("app.one",1700));
        scoped.arm(2000);scoped.accepts("app.one",2100);scoped.observeForeground("com.android.launcher",2200);
        check("departure to unselected launcher ends capture",scoped.state(2200)==ReplayCaptureSession.State.FINISHED);
        check("second launch is not recorded without rearm",!scoped.accepts("app.one",2300));
        scoped.arm(3000);scoped.accepts("app.one",3100);scoped.screenOff(3200);
        check("screen-off ends active capture",scoped.state(3200)==ReplayCaptureSession.State.FINISHED);
        check("unlock does not resume old capture",!scoped.accepts("app.one",3300));
        scoped.arm(4000);scoped.observeForeground("app.two",4100);
        check("unrelated waiting observation does not choose target",scoped.state(4100)==ReplayCaptureSession.State.WAITING);
        check("explicit selected target still binds",scoped.accepts("app.one",4200));
        scoped.observeForeground("app.one",16000);
        check("same foreground observation does not extend capture",!scoped.accepts("app.one",16200));
        scoped.arm(50000);scoped.screenOff(170000);
        check("screen-off cannot extend expired waiting window",scoped.state(170000)==ReplayCaptureSession.State.FINISHED);
        System.out.println("ReplayCaptureSessionTest: "+passed+" checks passed");}
}
