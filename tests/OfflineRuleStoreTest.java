import org.splashguard.app.OfflineRuleStore;
import org.splashguard.app.OfflineRuleStore.Pack;
import org.splashguard.app.OfflineRuleStore.RuleLimit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Untrusted-import regressions; no Android, files, network, or signing key needed. */
public final class OfflineRuleStoreTest {
    private static int passed;
    private static void check(String name, boolean value) { if (!value) throw new AssertionError(name); passed++; }
    private static void rejects(String name, Runnable action) {
        try { action.run(); throw new AssertionError(name); }
        catch (IllegalArgumentException expected) { passed++; }
    }
    private static RuleLimit limit(String id, String pkg, long low, long high, int time, int actions, boolean on, boolean gesture) {
        return new RuleLimit(id,pkg,low,high,time,actions,on,gesture);
    }
    private static RuleLimit generic(long low,long high,int time,int actions,boolean on,boolean gesture) {
        return limit("generic.splash","com.example.selected",low,high,time,actions,on,gesture);
    }
    private static RuleLimit bili(long low,long high,int time,int actions,boolean on,boolean gesture) {
        return limit("bili.splash.skip","tv.danmaku.bili",low,high,time,actions,on,gesture);
    }
    private static List<RuleLimit> catalog() {
        return Arrays.asList(generic(0,Long.MAX_VALUE,12000,2,true,false),bili(0,Long.MAX_VALUE,12000,2,true,true));
    }
    private static Pack pack(String id,int version,RuleLimit... rules) { return new Pack(1,id,version,Arrays.asList(rules)); }
    public static void main(String[] args) {
        OfflineRuleStore store=new OfflineRuleStore(catalog());
        check("initial catalog available",store.effectiveRules().size()==2 && store.currentPack()==null);
        check("initial rollback unavailable",!store.rollback() && !store.canRollback());
        check("unknown rule lookup is absent",store.effective("unreviewed")==null);
        check("unknown version never satisfies limits",!store.effective("generic.splash").allowsVersion(-1));

        store.apply(pack("local.first",1,generic(100,200,6000,1,true,false)));
        check("import narrows version",store.effective("generic.splash").allowsVersion(100)
                && !store.effective("generic.splash").allowsVersion(99) && !store.effective("generic.splash").allowsVersion(201));
        check("import narrows budget",store.effective("generic.splash").windowMs==6000 && store.effective("generic.splash").maxActions==1);
        check("omitted built-in remains available",store.effective("bili.splash.skip").allowGesture);
        check("canonical persistence includes full effective set",store.currentPack().rules.size()==2);
        check("first import can undo to baseline without a previous pack",store.canRollback() && store.previousPack()==null);
        Pack first=store.currentPack();
        rejects("unknown rule cannot add new automation",()->store.apply(pack("local.bad",1,
                limit("other.rule","com.other.app",0,100,1000,1,true,false))));
        check("bad import leaves prior pack and rollback unchanged",store.currentPack()==first && store.canRollback());
        rejects("same ID cannot target another app",()->store.apply(pack("local.bad",1,
                limit("generic.splash","com.other.app",100,200,5000,1,true,false))));
        rejects("new pack cannot widen lower version",()->store.apply(pack("local.bad",1,generic(99,200,5000,1,true,false))));
        rejects("new pack cannot widen upper version",()->store.apply(pack("local.bad",1,generic(100,201,5000,1,true,false))));
        rejects("new pack cannot regain time previously removed",()->store.apply(pack("local.bad",1,generic(100,200,7000,1,true,false))));
        rejects("new pack cannot regain attempts previously removed",()->store.apply(pack("local.bad",1,generic(100,200,5000,2,true,false))));
        rejects("generic import cannot enable gestures",()->store.apply(pack("local.bad",1,generic(100,200,5000,1,true,true))));
        rejects("equal pack version is not a fresh import",()->store.apply(pack("local.first",1,generic(100,200,5000,1,true,false))));

        store.apply(pack("local.second",1,bili(10,20,3000,1,true,false)));
        check("later omitted rule keeps earlier restriction",store.effective("generic.splash").windowMs==6000);
        check("reviewed gesture capability can be removed",!store.effective("bili.splash.skip").allowGesture);
        Pack second=store.currentPack();
        OfflineRuleStore reopened=new OfflineRuleStore(catalog());
        reopened.restoreValidatedState(second,store.previousPack(),store.canRollback());
        check("reopened store retains current restrictions",reopened.effective("bili.splash.skip").windowMs==3000
                && reopened.effective("generic.splash").windowMs==6000 && reopened.canRollback());
        check("cross-process rollback restores previous canonical state",reopened.rollback()
                && reopened.effective("bili.splash.skip").allowGesture && reopened.effective("generic.splash").windowMs==6000);
        check("cross-process rollback remains single use",!reopened.rollback());
        reopened.restoreValidatedState(first,null,true);
        check("persisted baseline undo flag works",reopened.canRollback() && reopened.rollback()
                && reopened.currentPack()==null && reopened.effective("generic.splash").windowMs==12000);
        reopened.restoreValidatedState(first,null,false);
        check("saved no-undo state does not invent an undo",!reopened.canRollback() && reopened.effective("generic.splash").windowMs==6000);
        Pack beforeBadRestore=reopened.currentPack();
        Pack tampered=pack("local.tampered",1,generic(0,Long.MAX_VALUE,12000,2,true,false),bili(0,Long.MAX_VALUE,12000,2,true,true));
        rejects("persisted current cannot broaden previous",()->reopened.restoreValidatedState(tampered,first,true));
        check("bad restore leaves live state intact",reopened.currentPack()==beforeBadRestore && !reopened.canRollback());
        rejects("partial persisted current rejected",()->reopened.restoreValidatedState(pack("local.partial",1,generic(100,200,6000,1,true,false)),null,false));
        rejects("previous metadata needs an undo flag",()->reopened.restoreValidatedState(second,first,false));
        rejects("baseline cannot pretend to have imported undo",()->reopened.restoreValidatedState(null,first,true));
        rejects("gesture cannot be restored by another import",()->store.apply(pack("local.third",1,bili(10,20,2000,1,true,true))));
        check("failed widening does not consume rollback",store.currentPack()==second && store.rollback());
        check("rollback restores immediately preceding effective state",store.currentPack()==first
                && store.effective("bili.splash.skip").allowGesture && store.effective("generic.splash").windowMs==6000);
        check("rollback is single use, not a toggle",!store.rollback());

        store.apply(pack("local.first",2,generic(120,180,4000,1,false,false)));
        check("rule can be disabled",!store.effective("generic.splash").enabled);
        rejects("disabled rule cannot be silently reenabled by import",()->store.apply(pack("local.first",3,generic(120,180,3000,1,true,false))));
        rejects("same pack cannot downgrade version",()->store.apply(pack("local.first",1,generic(120,180,3000,1,false,false))));
        check("explicit undo can restore prior enabled rule",store.rollback() && store.effective("generic.splash").enabled);

        RuleLimit before=store.effective("generic.splash");
        rejects("batch validation is atomic",()->store.apply(pack("local.atomic",1,generic(120,180,2000,1,false,false),
                limit("unknown.rule","com.unknown.app",0,100,1000,1,true,false))));
        check("valid first entry did not partially apply",store.effective("generic.splash")==before);

        OfflineRuleStore restored=new OfflineRuleStore(catalog());
        restored.apply(store.currentPack());
        check("canonical data restores restrictions on a fresh store",restored.effective("generic.splash").windowMs==6000
                && restored.effective("generic.splash").minVersionCode==100);
        restored.restoreBuiltIns();
        check("explicit restore uses original trusted catalog",restored.currentPack()==null && !restored.canRollback()
                && restored.effective("generic.splash").windowMs==12000);

        rejects("duplicate pack rule IDs rejected",()->pack("local.dup",1,generic(0,10,1000,1,true,false),generic(0,10,1000,1,true,false)));
        rejects("duplicate built-in IDs rejected",()->new OfflineRuleStore(Arrays.asList(generic(0,10,1000,1,true,false),generic(0,20,2000,1,true,false))));
        rejects("unsupported schema rejected",()->new Pack(2,"local.new",1,catalog()));
        rejects("zero version rejected",()->new Pack(1,"local.zero",0,catalog()));
        rejects("oversized pack version rejected",()->new Pack(1,"local.large",OfflineRuleStore.MAX_PACK_VERSION+1,catalog()));
        rejects("URL is not a pack identifier",()->pack("https://remote.rules",1,generic(0,10,1000,1,true,false)));
        rejects("newline cannot be smuggled in ID",()->pack("local.id\nsecret",1,generic(0,10,1000,1,true,false)));
        rejects("empty pack rejected",()->new Pack(1,"local.empty",1,new ArrayList<RuleLimit>()));
        rejects("window cannot exceed hard limit",()->generic(0,10,12001,1,true,false));
        rejects("attempt count cannot exceed hard limit",()->generic(0,10,1000,3,true,false));
        rejects("negative version rejected",()->generic(-1,10,1000,1,true,false));
        rejects("inverted versions rejected",()->generic(10,9,1000,1,true,false));
        rejects("package wildcard rejected",()->limit("generic.splash","com.*",0,10,1000,1,true,false));
        RuleLimit selected=limit("generic.splash",OfflineRuleStore.SELECTED_APPS,0,Long.MAX_VALUE,12000,2,true,false);
        OfflineRuleStore selectedStore=new OfflineRuleStore(Arrays.asList(selected));
        selectedStore.apply(pack("local.selected",1,limit("generic.splash",OfflineRuleStore.SELECTED_APPS,10,20,1000,1,true,false)));
        check("selected-app scope only restricts existing generic matcher",selectedStore.effective("generic.splash").windowMs==1000);
        rejects("selected scope cannot create another rule type",()->limit("other.rule",OfflineRuleStore.SELECTED_APPS,0,10,1000,1,true,false));
        rejects("selected scope cannot turn into another package",()->selectedStore.apply(pack("local.selected",2,
                limit("generic.splash","com.other.app",10,20,1000,1,true,false))));

        OfflineRuleStore.requireInputSize(OfflineRuleStore.MAX_PACK_BYTES);
        passed++;
        rejects("empty file rejected",()->OfflineRuleStore.requireInputSize(0));
        rejects("byte size cap enforced",()->OfflineRuleStore.requireInputSize(OfflineRuleStore.MAX_PACK_BYTES+1));
        HashSet<String> fields=new HashSet<>(OfflineRuleStore.expectedFields(OfflineRuleStore.FieldKind.PACK));
        OfflineRuleStore.requireFields(fields,OfflineRuleStore.FieldKind.PACK);
        passed++;
        fields.add("script");
        rejects("unknown script field rejected",()->OfflineRuleStore.requireFields(fields,OfflineRuleStore.FieldKind.PACK));
        fields.remove("script");fields.remove("id");
        rejects("missing required field rejected",()->OfflineRuleStore.requireFields(fields,OfflineRuleStore.FieldKind.PACK));
        HashSet<String> ruleFields=new HashSet<>(OfflineRuleStore.expectedFields(OfflineRuleStore.FieldKind.RULE));
        ruleFields.add("position");
        rejects("coordinate instruction field rejected",()->OfflineRuleStore.requireFields(ruleFields,OfflineRuleStore.FieldKind.RULE));

        List<RuleLimit> max=new ArrayList<>();
        for(int i=0;i<OfflineRuleStore.MAX_RULES;i++)max.add(limit("rule.r"+i,"com.example.app",0,10,1000,1,true,false));
        check("catalog cap boundary accepted",new OfflineRuleStore(max).effectiveRules().size()==128);
        max.add(limit("rule.extra","com.example.app",0,10,1000,1,true,false));
        rejects("catalog over cap rejected",()->new OfflineRuleStore(max));
        try { store.effectiveRules().clear(); throw new AssertionError("effective list mutable"); }
        catch(UnsupportedOperationException expected) { passed++; }
        try { first.rules.clear(); throw new AssertionError("pack list mutable"); }
        catch(UnsupportedOperationException expected) { passed++; }
        System.out.println("OfflineRuleStore: "+passed+" checks passed");
    }
}
