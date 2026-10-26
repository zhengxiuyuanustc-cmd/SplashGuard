import org.splashguard.app.SkipSession;

/** State regressions run with javac/java, without an Android device or clock. */
public final class SkipSessionTest {
    private static int passed;
    private static void check(String name, boolean actual) {
        if (!actual) throw new AssertionError(name);
        passed++;
    }
    private static SkipSession start(long now) {
        SkipSession s = new SkipSession();
        s.observe("tv.danmaku.bili", now);
        return s;
    }
    private static String snapshot(SkipSession s) {
        return s.epoch()+"/"+s.packageName()+"/"+s.deadline()+"/"+s.attempts()+"/"
                +s.buttonKey()+"/"+s.busy()+"/"+s.done();
    }
    public static void main(String[] args) {
        SkipSession s = start(100);
        SkipSession.Token first = s.begin("count_down", 200);
        check("reserve before submission", first != null && s.busy() && s.attempts() == 1);
        check("current token guard is pure", s.current(first) && s.current(first) && s.busy());
        check("accepted is not disappearance", s.accepted(first) && !s.done());
        check("completion remains reviewable", s.completed(first) && !s.busy() && !s.done());
        check("count first accepted once", s.recordOnce(first) && !s.recordOnce(first));
        s.review(true);
        check("no early retry", s.begin("count_down", 899) == null);
        check("only original precise button", s.begin("another_skip", 900) == null);
        SkipSession.Token retry = s.begin("count_down", 900);
        check("one retry at interval boundary", retry != null && retry.attempt == 2);
        String before = snapshot(s);
        check("previous token is no longer current", !s.current(first) && s.current(retry));
        check("old completion cannot release new operation", !s.completed(first) && before.equals(snapshot(s)));
        check("old cancellation cannot release new operation", !s.cancelled(first) && before.equals(snapshot(s)));
        check("retry accepted", s.accepted(retry));
        check("retry not counted again", !s.recordOnce(retry));
        check("retry completes", s.completed(retry));
        check("no third attempt", s.begin("count_down", 1600) == null && s.finished(1600));

        s = start(0);
        first = s.begin("count_down", 0);
        s.accepted(first);
        s.review(false);
        check("do not review during an in-flight operation", !s.done());
        s.completed(first);
        s.review(false);
        check("disappeared button closes session", s.done() && s.begin("count_down", 700) == null);
        check("other button cannot reopen completed session", s.begin("countdown_title", 700) == null);

        s = start(0);
        long originalEpoch = s.epoch();
        long originalDeadline = s.deadline();
        check("same-package bright screen does not start again", !s.observe("tv.danmaku.bili", 8000));
        check("same-package repeated present event preserves budget", !s.observe("tv.danmaku.bili", 11999)
                && s.epoch() == originalEpoch && s.deadline() == originalDeadline);
        check("deadline is exclusive", s.begin("count_down", 12000) == null && s.finished(12000));
        check("same package after expiry does not rearm", !s.observe("tv.danmaku.bili", 14000)
                && s.begin("count_down", 14000) == null);

        s = start(0);
        first = s.begin("count_down", 11900);
        s.accepted(first);
        s.completed(first);
        check("accepted attempt does not extend deadline", s.deadline() == 12000
                && s.begin("count_down", 12600) == null);

        s = start(0);
        first = s.begin("count_down", 100);
        s.accepted(first);
        s.observe("com.zhihu.android", 300);
        retry = s.begin("zhihu_skip", 300);
        before = snapshot(s);
        check("package switch isolates completion", !s.completed(first) && before.equals(snapshot(s)));
        check("package switch isolates cancellation", !s.cancelled(first) && before.equals(snapshot(s)));
        check("package switch isolates acceptance and counter", !s.accepted(first) && !s.recordOnce(first)
                && before.equals(snapshot(s)));
        check("new package operation still active", retry != null && s.busy());

        s = start(0);
        first = s.begin("count_down", 100);
        s.accepted(first);
        s.reset(); // screen off, pause, or application-selection change
        before = snapshot(s);
        check("reset invalidates all old callback types", !s.completed(first) && !s.cancelled(first)
                && !s.accepted(first) && !s.recordOnce(first) && before.equals(snapshot(s)));
        check("reset invalidates current-token guard", !s.current(first));
        check("reset has no active budget", !s.active(500) && s.begin("count_down", 500) == null);
        check("same app after explicit reset starts a fresh window", s.observe("tv.danmaku.bili", 1000)
                && s.deadline() == 13000 && s.attempts() == 0);
        retry = s.begin("count_down", 1000);
        before = snapshot(s);
        check("old gesture cannot release new post-reset gesture", !s.completed(first)
                && !s.cancelled(first) && before.equals(snapshot(s)) && s.busy());
        check("fresh operation may count in new session", s.accepted(retry) && s.recordOnce(retry));

        s = start(0);
        first = s.begin("count_down", 100);
        check("rejected reservation still consumes one attempt", s.cancelled(first) && s.attempts() == 1);
        check("rejected operation is not counted", !s.recordOnce(first));
        check("rejected operation preserves cooldown", s.begin("count_down", 799) == null);
        retry = s.begin("count_down", 800);
        check("rejected operation allows only bounded retry", retry != null && s.cancelled(retry)
                && s.begin("count_down", 1500) == null);

        s = new SkipSession();
        check("null package does not arm", !s.observe(null, 0) && !s.active(0));
        check("empty package does not arm", !s.observe("", 0) && !s.active(0));
        s.observe("tv.danmaku.bili", 0);
        check("empty button cannot reserve attempt", s.begin(null, 0) == null && s.begin("", 0) == null);

        s = start(100);
        originalDeadline = s.deadline();
        s.stop();
        check("ambiguous targets stop before the first click", s.done() && s.begin("real_skip", 200) == null);
        check("stop preserves foreground package", "tv.danmaku.bili".equals(s.packageName()));
        check("stop preserves original deadline and attempt count", s.deadline() == originalDeadline && s.attempts() == 0);
        check("same package does not renew a stopped window", !s.observe("tv.danmaku.bili", 500)
                && s.done() && s.deadline() == originalDeadline);
        s.review(true);
        check("a later unique candidate cannot unlock a stopped window", s.done() && s.begin("real_skip", 700) == null);

        s = start(0);
        first = s.begin("original", 100);
        s.accepted(first);
        s.stop();
        before = snapshot(s);
        check("stop cancels pending operation state", !s.busy() && !s.current(first));
        check("old completion cannot unlock a blocked session", !s.completed(first) && before.equals(snapshot(s)));
        check("old cancellation cannot unlock a blocked session", !s.cancelled(first) && before.equals(snapshot(s)));
        check("old acceptance cannot unlock a blocked session", !s.accepted(first) && before.equals(snapshot(s)));
        check("old result cannot count after a block", !s.recordOnce(first) && before.equals(snapshot(s)));
        check("stop retains target and consumed attempt", "original".equals(s.buttonKey()) && s.attempts() == 1);
        check("a newly appearing button cannot receive a retry", s.begin("other", 900) == null);
        check("blocked package stays stopped beyond deadline", !s.observe("tv.danmaku.bili", 14000) && !s.active(14000));
        check("another package can start its own session", s.observe("com.zhihu.android", 15000)
                && s.active(15000) && !s.done() && s.attempts() == 0);
        s.stop();
        s.reset();
        check("an explicit screen reset allows a new bounded session", s.observe("com.zhihu.android", 20000)
                && s.deadline() == 32000 && s.begin("known", 20001) != null);
        System.out.println("PASS " + passed + " session-state cases");
    }
}
