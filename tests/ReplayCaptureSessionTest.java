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
        System.out.println("ReplayCaptureSessionTest: "+passed+" checks passed");}
}
