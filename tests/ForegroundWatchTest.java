import org.splashguard.app.ForegroundWatch;
import org.splashguard.app.SkipSession;
public final class ForegroundWatchTest {
    private static int passed;
    private static void check(String label,boolean condition){if(!condition)throw new AssertionError(label);passed++;}
    private static boolean sample(ForegroundWatch w,SkipSession s,String confirmedRoot,long now){
        return w.take(now)&&s.observe(confirmedRoot,now);
    }
    public static void main(String[] args){
        ForegroundWatch w=new ForegroundWatch();
        check("no periodic polling while idle",w.delay(0)==-1&&!w.take(0));
        w.request(100);check("transition reads immediately",w.delay(100)==0&&w.take(100));
        check("no busy polling",w.delay(101)==119&&!w.take(101));
        w.request(200);check("repeated event does not postpone next sample",w.delay(200)==20);
        check("interval boundary",w.take(220));
        w.clear();check("pause clears pending probes",w.delay(220)==-1&&!w.take(220));
        w.request(0);int reads=0;for(long t=0;t<2000;t+=120)if(w.take(t))reads++;
        check("hard maximum root reads",reads==ForegroundWatch.MAX_PROBES);
        check("probe exhaustion stops",w.delay(2000)==-1&&!w.take(2000));
        w.request(3000);check("a later transition starts a new short watch",w.take(3000));
        check("suspended handler cannot execute after watch expiry",!w.take(5000)&&w.delay(5000)==-1);
        w.request(Long.MAX_VALUE-200);check("deadline saturation stays bounded",w.take(Long.MAX_VALUE-200)&&!w.take(Long.MAX_VALUE));

        SkipSession s=new SkipSession();s.observe("bili",0);SkipSession.Token old=s.begin("skip",1);
        s.accepted(old);s.completed(old);s.review(false);check("initial ad completed",s.done());
        w.clear();w.request(20000);
        check("missing first transition root is unknown",!sample(w,s,null,20000)&&s.done()&&s.deadline()==12000);
        check("empty root cannot renew a stopped session",!sample(w,s,"",20120)&&s.done());
        check("late confirmed unselected app is observed",sample(w,s,"other",20240)&&s.packageName().equals("other"));
        check("return without process restart starts fresh budget",sample(w,s,"bili",20360)&&s.deadline()==32360&&s.attempts()==0);
        SkipSession.Token fresh=s.begin("skip",20361);
        check("prior result cannot affect return session",fresh!=null&&!s.accepted(old)&&!s.recordOnce(old)&&!s.completed(old));
        s.accepted(fresh);s.completed(fresh);s.review(false);
        long deadline=s.deadline(),epoch=s.epoch();
        check("same app window event cannot reopen",!sample(w,s,"bili",20480)&&s.done()&&s.deadline()==deadline&&s.epoch()==epoch);
        w.request(20500);check("animation does not extend watch",w.delay(20500)==100);
        check("late event without departure cannot reopen expired app",!sample(w,s,"bili",22000)&&!s.active(22000));
        w.request(40000);check("new watch alone does not reset ad budget",s.deadline()==deadline&&s.done());
        check("unknown root after long wait still cannot rearm",!sample(w,s,null,40000)&&s.done());
        check("same app after expiry still cannot rearm",!sample(w,s,"bili",40120)&&s.done()&&s.deadline()==deadline);
        s=new SkipSession();s.observe("bili",0);w.clear();w.request(12000);
        check("probe watch does not extend expired ad window",!sample(w,s,"bili",12000)&&!s.active(12000)&&s.deadline()==12000);
        check("late different app creates a new foreground epoch",sample(w,s,"other",12120)&&s.deadline()==24120);
        check("later return gets normal limited window",sample(w,s,"bili",12240)&&s.deadline()==24240);
        System.out.println("PASS "+passed+" foreground-watch cases");
    }
}
