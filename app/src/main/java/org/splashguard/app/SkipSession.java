package org.splashguard.app;

/** Bounded opening-ad state. Callers supply monotonic time; no Android objects are retained. */
public final class SkipSession {
    public static final long WINDOW_MS = 12000;
    public static final long RETRY_INTERVAL_MS = 700;
    public static final int MAX_ATTEMPTS = 2;

    /** Identifies one operation in one session, including an asynchronous gesture. */
    public static final class Token {
        public final long epoch;
        public final long operationId;
        public final int attempt;
        private Token(long epoch, long operationId, int attempt) {
            this.epoch = epoch;
            this.operationId = operationId;
            this.attempt = attempt;
        }
    }

    private String packageName = "";
    private String buttonKey;
    private long epoch;
    private long operationSequence;
    private long pendingOperation;
    private long acceptedOperation;
    private long deadline;
    private long lastAttempt;
    private int attempts;
    private boolean pendingAccepted;
    private boolean recorded;
    private boolean done;

    /** A same-package observation never renews the opening window or operation budget. */
    public boolean observe(String pkg, long now) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(packageName)) return false;
        reset();
        packageName = pkg;
        deadline = now > Long.MAX_VALUE - WINDOW_MS ? Long.MAX_VALUE : now + WINDOW_MS;
        return true;
    }

    /** Invalidates outstanding callbacks. The next observed package starts a fresh window. */
    public void reset() {
        epoch++;
        packageName = "";
        buttonKey = null;
        pendingOperation = 0;
        acceptedOperation = 0;
        deadline = 0;
        lastAttempt = 0;
        attempts = 0;
        pendingAccepted = false;
        recorded = false;
        done = false;
    }

    public boolean active(long now) {
        return !packageName.isEmpty() && !done && now < deadline;
    }

    public boolean busy() { return pendingOperation != 0; }
    public boolean done() { return done; }
    public int attempts() { return attempts; }
    public long epoch() { return epoch; }
    public long deadline() { return deadline; }
    public String packageName() { return packageName; }
    public String buttonKey() { return buttonKey; }

    /** Pure guard for a callback before it changes service state or accesses the current window. */
    public boolean current(Token token) { return matchesPending(token); }

    /** No more operations may be attempted after this becomes true. */
    public boolean finished(long now) {
        return !active(now) || (attempts >= MAX_ATTEMPTS && !busy());
    }

    public boolean canAttempt(String key, long now) {
        return key != null && !key.isEmpty() && active(now) && !busy()
                && attempts < MAX_ATTEMPTS
                && (buttonKey == null || buttonKey.equals(key))
                && (attempts == 0 || now - lastAttempt >= RETRY_INTERVAL_MS);
    }

    /** Reserves the attempt before any Android operation is submitted, even if it is rejected. */
    public Token begin(String key, long now) {
        if (!canAttempt(key, now)) return null;
        if (buttonKey == null) buttonKey = key;
        attempts++;
        lastAttempt = now;
        pendingOperation = ++operationSequence;
        pendingAccepted = false;
        return new Token(epoch, pendingOperation, attempts);
    }

    /** Acceptance means an operation was submitted, not that the advertisement disappeared. */
    public boolean accepted(Token token) {
        if (!matchesPending(token) || pendingAccepted) return false;
        pendingAccepted = true;
        acceptedOperation = token.operationId;
        return true;
    }

    public boolean completed(Token token) { return finish(token); }
    public boolean cancelled(Token token) { return finish(token); }

    private boolean finish(Token token) {
        if (!matchesPending(token)) return false;
        pendingOperation = 0;
        pendingAccepted = false;
        return true;
    }

    /** Call after acceptance or its matching completion, before beginning a later operation. */
    public boolean recordOnce(Token token) {
        if (token == null || token.epoch != epoch || token.operationId != operationSequence
                || token.operationId != acceptedOperation || recorded) return false;
        recorded = true;
        return true;
    }

    /** Review fresh nodes only. Disappearance of the original valid button closes this session. */
    public void review(boolean sameKeyStillValid) {
        if (busy() || acceptedOperation == 0 || done) return;
        if (!sameKeyStillValid) done = true;
    }

    private boolean matchesPending(Token token) {
        return token != null && token.epoch == epoch && pendingOperation != 0
                && token.operationId == pendingOperation;
    }
}
