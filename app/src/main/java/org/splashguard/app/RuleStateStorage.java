package org.splashguard.app;

/**
 * IO-independent save boundary. A backend commits the whole snapshot and flags together.
 * A failed commit may already have changed backend memory, so normal processing stays
 * stopped until an explicit, successful recovery. No method performs Android actions.
 */
public final class RuleStateStorage {
    private static final String INVALID="本地规则状态无法验证或保存未确认；处理已暂停，请检查后恢复内置规则。";
    private static final String AUTH_PAUSED="规则签名导入已暂停；本地限制和内置规则仍可使用，历史记录不会自动清除。";
    private static final int MAX_QUARANTINE_CHARS=65536;
    private static final String OVERSIZED="损坏规则快照超出保留上限；签名导入保持暂停。";
    private static final String LEGACY_INVALID="旧规则快照无法验证；签名导入保持暂停。";

    public interface Backend {
        Record read();
        /** One transaction also removes legacy keys; false is never treated as confirmed. */
        boolean commit(String encodedState,boolean authBlocked,String quarantine);
    }
    public static final class Record {
        public final String state,legacyCurrent,legacyPrevious,quarantine;
        public final boolean legacyUndo,authBlocked;
        public Record(String state,String legacyCurrent,String legacyPrevious,boolean legacyUndo,
                      boolean authBlocked,String quarantine) {
            this.state=state;this.legacyCurrent=legacyCurrent;this.legacyPrevious=legacyPrevious;
            this.legacyUndo=legacyUndo;this.authBlocked=authBlocked;this.quarantine=quarantine;
        }
    }
    private static final class Loaded {
        final Record record;final RuleUpdateState state;
        Loaded(Record record,RuleUpdateState state){this.record=record;this.state=state;}
    }
    private final Backend backend;
    private boolean failed;

    public RuleStateStorage(Backend backend) {
        if(backend==null)throw new IllegalArgumentException(INVALID);
        this.backend=backend;
    }
    public synchronized RuleUpdateState load(){return ready().state;}
    public synchronized boolean authBlocked(){return ready().record.authBlocked;}

    public synchronized void applyLocal(OfflineRuleStore.Pack pack) {
        Loaded before=ready();
        final RuleUpdateState next;
        try{next=before.state.applyLocal(pack);}catch(RuntimeException invalid){throw invalid();}
        persist(next,before.record.authBlocked,before.record.quarantine);
    }
    public synchronized RuleUpdateState.Plan previewSigned(SignedRulePack.VerifiedPack verified) {
        Loaded before=ready();requireAuth(before.record);
        try{return before.state.previewSigned(verified);}catch(RuntimeException invalid){throw invalid();}
    }
    /** Returns false for an already accepted exact input; it performs no write or undo change. */
    public synchronized boolean applySigned(SignedRulePack.VerifiedPack verified) {
        Loaded before=ready();requireAuth(before.record);
        final RuleUpdateState.Plan plan;
        try{plan=before.state.previewSigned(verified);}catch(RuntimeException invalid){throw invalid();}
        if(plan.alreadyAccepted)return false;
        persist(plan.next,before.record.authBlocked,before.record.quarantine);return true;
    }
    public synchronized boolean rollback() {
        Loaded before=ready();RuleUpdateState next=before.state.rollback();
        if(next==before.state)return false;
        persist(next,before.record.authBlocked,before.record.quarantine);return true;
    }
    /**
     * Only explicit recovery bypasses a failed-write latch. Valid observed ledgers survive.
     * A corrupt record keeps a quarantine marker and keeps signed imports paused;
     * it is never silently replaced with empty trusted history.
     */
    public synchronized void restoreBuiltIns() {
        Record before=read();
        RuleUpdateState next;boolean blocked=before.authBlocked;String quarantine=before.quarantine;
        try{next=decodeSnapshot(before).restoreBuiltIns();}
        catch(RuntimeException invalid) {
            next=RuleUpdateState.empty();blocked=true;
            if(quarantine==null)quarantine=before.state==null?LEGACY_INVALID:boundedQuarantine(before.state);
        }
        if(quarantine!=null){blocked=true;quarantine=boundedQuarantine(quarantine);}
        persist(next,blocked,quarantine);
    }
    private Loaded ready() {
        if(failed)throw invalid();
        Record record=read();
        try{return new Loaded(record,decode(record));}
        catch(RuntimeException invalid){throw invalid();}
    }
    private Record read() {
        final Record record;
        try{record=backend.read();}catch(RuntimeException invalid){throw invalid();}
        if(record==null)throw invalid();
        return record;
    }
    private static RuleUpdateState decode(Record record) {
        if(record.quarantine!=null&&(!record.authBlocked||record.quarantine.length()>MAX_QUARANTINE_CHARS))throw invalid();
        return decodeSnapshot(record);
    }
    private static RuleUpdateState decodeSnapshot(Record record) {
        if(record.state!=null)return RuleUpdateState.decode(record.state);
        return RuleUpdateState.fromLegacy(
                record.legacyCurrent==null?null:RuleJsonCodec.decodePack(record.legacyCurrent),
                record.legacyPrevious==null?null:RuleJsonCodec.decodePack(record.legacyPrevious),
                record.legacyUndo);
    }
    private void persist(RuleUpdateState next,boolean blocked,String quarantine) {
        final String encoded;
        try{encoded=next.encode();}catch(RuntimeException invalid){throw invalid();}
        final boolean confirmed;
        try{confirmed=backend.commit(encoded,blocked,quarantine);}
        catch(RuntimeException invalid){failed=true;throw invalid();}
        if(!confirmed){failed=true;throw invalid();}
        failed=false;
    }
    private static String boundedQuarantine(String value) {
        return value.length()>MAX_QUARANTINE_CHARS?OVERSIZED:value;
    }
    private static void requireAuth(Record record) {
        if(record.authBlocked)throw new IllegalStateException(AUTH_PAUSED);
    }
    private static IllegalStateException invalid(){return new IllegalStateException(INVALID);}
}