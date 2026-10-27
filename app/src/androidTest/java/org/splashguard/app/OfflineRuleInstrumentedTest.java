package org.splashguard.app;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.UiAutomation;
import android.content.pm.ApplicationInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

/** Destructive fixture tests: refuse to run before clearing anything unless this is an isolated emulator. */
@RunWith(AndroidJUnit4.class)
public final class OfflineRuleInstrumentedTest {
    private static final String PICK_SIGNED="选择作者签名文件（先预览）",CONFIRM="确认应用预览中的限制";
    private Context app;private UiDevice device;private ActivityScenario<OfflineToolsActivity> screen;private boolean isolated,serviceTouched;
    @Rule public final TestWatcher evidence=new TestWatcher(){
        @Override protected void failed(Throwable failure,Description description){
            if(!isolated||device==null)return;
            try{File dir=new File(app.getExternalFilesDir(null),"instrumentation");dir.mkdirs();
                device.dumpWindowHierarchy(new File(dir,description.getMethodName()+".xml"));
                device.takeScreenshot(new File(dir,description.getMethodName()+".png"));
            }catch(Exception ignored){/* The failing assertion remains the test result. */}
        }
    };
    @Before public void prepare()throws Exception{
        assertEquals("Tests must be explicitly opted into an isolated emulator","true",InstrumentationRegistry.getArguments().getString("isolatedEmulator"));
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertEquals("Refusing to alter a physical phone","1",device.executeShellCommand("getprop ro.kernel.qemu").trim());
        app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.os.UserManager users=app.getSystemService(android.os.UserManager.class);
        long unlockDeadline=SystemClock.elapsedRealtime()+20000;
        while(!users.isUserUnlocked()&&SystemClock.elapsedRealtime()<unlockDeadline)SystemClock.sleep(100);
        assertTrue("The disposable emulator user must be unlocked before preference tests",users.isUserUnlocked());
        isolated=true;
        assertEquals("Acceptance target must be non-debuggable",0,app.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE);
        assertTrue("Reset only the empty emulator fixture preferences",Prefs.get(app).edit().clear().commit());
        RuleData.clearSample();
        device.pressHome();
    }
    @After public void clean()throws Exception{
        if(!isolated)return;
        device.pressHome();
        assertTrue(Prefs.get(app).edit().putBoolean("enabled",false).commit());
        if(serviceTouched){device.executeShellCommand("settings put secure enabled_accessibility_services ''");device.executeShellCommand("settings put secure accessibility_enabled 0");}
        File directory=new File(app.getApplicationInfo().dataDir,"shared_prefs");directory.setWritable(true,true);
        if(screen!=null){screen.close();screen=null;}
        RuleData.restoreBuiltIns(app);assertTrue(Prefs.get(app).edit().clear().commit());
        device.setOrientationNatural();
    }
    private void launch(){screen=ActivityScenario.launch(new Intent(app,OfflineToolsActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    private interface Condition{boolean check(Activity activity);}
    private void await(String label,Condition condition){
        long deadline=SystemClock.elapsedRealtime()+12000;
        do{AtomicBoolean value=new AtomicBoolean();screen.onActivity(activity->value.set(condition.check(activity)));if(value.get())return;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<deadline);
        fail(label);
    }
    private static TextView text(View root,String value,boolean exact){
        if(root instanceof TextView){String found=((TextView)root).getText().toString();if(exact?found.equals(value):found.contains(value))return (TextView)root;}
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),value,exact);if(found!=null)return found;}}
        return null;
    }
    private static Button button(Activity activity,String label){TextView found=text(activity.getWindow().getDecorView(),label,true);assertTrue("Expected button: "+label,found instanceof Button);return (Button)found;}
    private void click(String label){screen.onActivity(activity->{Button found=button(activity,label);assertTrue("Button enabled: "+label,found.isEnabled());assertTrue(found.performClick());});}
    private void chooseSigned(String file)throws Exception{
        click(PICK_SIGNED);
        UiObject2 document=device.wait(Until.findObject(By.text(file)),1500);
        if(document==null){
            UiObject2 root=device.wait(Until.findObject(By.text("SplashGuard Test Files")),1500);
            if(root==null){UiObject2 drawer=device.wait(Until.findObject(By.desc("Show roots")),5000);
                assertNotNull("The system file picker navigation is available",drawer);drawer.click();
                root=device.wait(Until.findObject(By.text("SplashGuard Test Files")),5000);}
            assertNotNull("Real DocumentsUI lists the test-only provider",root);root.click();
            document=device.wait(Until.findObject(By.text(file)),5000);
        }
        assertNotNull("Public fixture document visible",document);document.click();
        assertTrue("The document picker returns to the target app",device.wait(Until.hasObject(By.pkg(app.getPackageName())),8000));
    }
    private SignedRulePack.VerifiedPack publicSample()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream stream=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("community-v1.jws")){
            byte[] buffer=new byte[2048];int n;while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);
        }
        return RuleTrust.verify(new String(bytes.toByteArray(),StandardCharsets.US_ASCII));
    }
    private interface BackgroundCondition{boolean check();}
    private void awaitBackground(String label,BackgroundCondition condition){
        long until=SystemClock.elapsedRealtime()+12000;
        while(SystemClock.elapsedRealtime()<until){if(condition.check())return;SystemClock.sleep(100);}captureActiveFailure(label);fail(label);
    }
    private void captureActiveFailure(String label){
        try{File directory=new File(app.getExternalFilesDir(null),"instrumentation");directory.mkdirs();
            String name=label.replaceAll("[^A-Za-z0-9]+","_");
            device.dumpWindowHierarchy(new File(directory,name+"-active.xml"));device.takeScreenshot(new File(directory,name+"-active.png"));
            DiagnosticSample value=RuleData.lastSample();String details="foreground="+device.getCurrentPackageName()+"\nserviceConnected="+SkipService.isConnected()+"\ncount="+Prefs.get(app).getInt("count",0)+"\nsample="+(value==null?"none":value.reason.name())+"\ndiagnostic="+Prefs.get(app).getString("diagnostic","");
            try(java.io.FileOutputStream output=new java.io.FileOutputStream(new File(directory,name+"-state.txt"))){output.write(details.getBytes(StandardCharsets.UTF_8));}
        }catch(Exception unavailable){/* Preserve the original assertion. Only synthetic emulator data is read. */}
    }
    private void enableActualService()throws Exception{
        String fixture=InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
        assertTrue(Prefs.get(app).edit().putStringSet("apps",Collections.singleton(fixture)).putBoolean("enabled",true).commit());
        serviceTouched=true;
        if(android.os.Build.VERSION.SDK_INT>=33)device.executeShellCommand("appops set org.splashguard.app ACCESS_RESTRICTED_SETTINGS allow");
        device.executeShellCommand("settings put secure enabled_accessibility_services org.splashguard.app/org.splashguard.app.SkipService");
        device.executeShellCommand("settings put secure accessibility_enabled 1");
        awaitBackground("System must actually connect the service",SkipService::isConnected);
    }
    private void startSyntheticSplash(String mode)throws Exception{
        device.executeShellCommand("am start -W -f 0x10008000 -n org.splashguard.app.test/org.splashguard.app.fixture.SyntheticSplashActivity --es fixtureMode "+mode);
    }
    @Test public void actualServiceClicksOneExplicitSyntheticSkip()throws Exception{
        enableActualService();startSyntheticSplash("unique");
        assertTrue("Real accessibility node action exits the synthetic ad",device.wait(Until.hasObject(By.text("FIXTURE_CONTENT")),10000));
        awaitBackground("One accepted operation recorded",()->Prefs.get(app).getInt("count",0)==1);
    }
    @Test public void actualServiceStopsOnTwoSyntheticSkipCandidates()throws Exception{
        enableActualService();startSyntheticSplash("ambiguous");
        awaitBackground("Actual service observes the conflict",()->RuleData.lastSample()!=null&&RuleData.lastSample().reason==DiagnosticSample.Reason.MULTIPLE_CANDIDATES);
        assertEquals(2,device.findObjects(By.text("跳过广告")).size());assertEquals(0,Prefs.get(app).getInt("count",0));
        assertFalse(device.hasObject(By.text("FIXTURE_CONTENT")));
    }
    @Test public void realPreferencesMigrateAndKeepAcceptedHistory()throws Exception{
        OfflineRuleStore store=new OfflineRuleStore(RuleCatalog.builtInLimits());
        OfflineRuleStore.Pack legacy=new OfflineRuleStore.Pack(1,"local.legacy",1,store.effectiveRules());store.apply(legacy);
        assertTrue(Prefs.get(app).edit().putString("rules_current",RuleJsonCodec.encodePack(store.currentPack())).putBoolean("rules_undo",false).commit());
        assertNotNull(RuleData.loadStore(app).currentPack());assertFalse(Prefs.get(app).contains("rules_state_v2"));
        SignedRulePack.VerifiedPack sample=publicSample();assertFalse(RuleData.previewSigned(app,sample).alreadyAccepted);
        assertTrue(RuleData.applySigned(app,sample.originalEnvelope));assertTrue(Prefs.get(app).contains("rules_state_v2"));assertFalse(Prefs.get(app).contains("rules_current"));
        String before=Prefs.get(app).getString("rules_state_v2",null);assertFalse(RuleData.applySigned(app,sample.originalEnvelope));assertEquals(before,Prefs.get(app).getString("rules_state_v2",null));
        assertTrue(RuleData.rollback(app));assertTrue(RuleData.previewSigned(app,sample).alreadyAccepted);
        RuleData.restoreBuiltIns(app);assertNull(RuleData.loadStore(app).currentPack());assertTrue(RuleData.previewSigned(app,sample).alreadyAccepted);
    }
    @Test public void documentsUiPreviewNeedsConfirmationAndSurvivesRecreation()throws Exception{
        launch();chooseSigned("SG-valid.jws");await("Verified preview loaded",activity->button(activity,CONFIRM).isEnabled());
        assertFalse("Preview has no preference mutation",Prefs.get(app).contains("rules_state_v2"));
        final String original=publicSample().originalEnvelope;
        screen.onActivity(activity->{Bundle saved=new Bundle();InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity,saved);
            assertFalse(saved.containsKey("pack"));for(String key:saved.keySet()){Object value=saved.get(key);assertFalse("No original signed envelope in saved state",value instanceof String&&value.equals(original));}});
        screen.recreate();await("Original in-memory preview remains usable",activity->button(activity,CONFIRM).isEnabled());
        click(CONFIRM);await("Commit confirmed",activity->text(activity.getWindow().getDecorView(),"限制已应用。",false)!=null);
        assertEquals("splashguard.community",RuleData.loadStore(app).currentPack().id);
        assertTrue(RuleData.previewSigned(app,publicSample()).alreadyAccepted);
    }
    @Test public void tamperedDocumentCannotOfferApplyOrSave()throws Exception{
        launch();chooseSigned("SG-tampered.jws");await("Tampered signature rejected",activity->text(activity.getWindow().getDecorView(),"签名验证失败",false)!=null);
        screen.onActivity(activity->assertFalse(button(activity,CONFIRM).isEnabled()));assertFalse(Prefs.get(app).contains("rules_state_v2"));assertNull(RuleData.loadStore(app).currentPack());
    }
    @Test public void resettingRulesDoesNotReapplySameSignedInput()throws Exception{
        launch();chooseSigned("SG-valid.jws");await("Ready to confirm",activity->button(activity,CONFIRM).isEnabled());click(CONFIRM);
        await("First import saved",activity->text(activity.getWindow().getDecorView(),"限制已应用。",false)!=null);
        click("恢复内置规则");click("再次点击，确认恢复内置规则");await("Baseline restored",activity->text(activity.getWindow().getDecorView(),"已恢复内置规则。",true)!=null);
        assertNull(RuleData.loadStore(app).currentPack());String baseline=Prefs.get(app).getString("rules_state_v2",null);
        chooseSigned("SG-valid.jws");await("Already accepted notice",activity->text(activity.getWindow().getDecorView(),"此版本与内容已接收过",false)!=null);
        screen.onActivity(activity->assertFalse(button(activity,CONFIRM).isEnabled()));assertEquals(baseline,Prefs.get(app).getString("rules_state_v2",null));assertNull(RuleData.loadStore(app).currentPack());
    }
    @Test public void cancelingSystemPickerLeavesRulesUnchanged()throws Exception{
        launch();click(PICK_SIGNED);SystemClock.sleep(500);device.pressBack();
        if(!device.wait(Until.hasObject(By.pkg(app.getPackageName())),1500))device.pressBack();
        assertTrue(device.wait(Until.hasObject(By.pkg(app.getPackageName())),5000));await("Cancel notice",activity->text(activity.getWindow().getDecorView(),"已取消文件操作。",true)!=null);
        screen.onActivity(activity->assertFalse(button(activity,CONFIRM).isEnabled()));assertFalse(Prefs.get(app).contains("rules_state_v2"));
    }
    @Test public void realPrivateDirectoryWriteFailureStopsUntilExplicitRecovery()throws Exception{
        assertTrue(RuleData.applySigned(app,publicSample().originalEnvelope));
        File directory=new File(app.getApplicationInfo().dataDir,"shared_prefs");assertTrue(directory.isDirectory());
        assertTrue("Make only this empty emulator app directory read-only",directory.setWritable(false,true));
        try{
            OfflineRuleStore.RuleLimit active=RuleData.loadStore(app).effective(RuleCatalog.GENERIC);
            OfflineRuleStore.RuleLimit paused=new OfflineRuleStore.RuleLimit(active.id,active.packageName,active.minVersionCode,active.maxVersionCode,active.windowMs,active.maxActions,false,active.allowGesture);
            try{RuleData.apply(app,new OfflineRuleStore.Pack(1,"local.io.failure",1,Collections.singletonList(paused)));fail("A real disk write failure must not be reported as confirmed");}catch(IllegalStateException expected){/* save is unconfirmed */}
            try{RuleData.loadStore(app);fail("Processing must be latched off");}catch(IllegalStateException expected){/* failed-write latch */}
        }finally{assertTrue(directory.setWritable(true,true));RuleData.restoreBuiltIns(app);}
        assertNull(RuleData.loadStore(app).currentPack());assertTrue("Accepted signed history survives explicit recovery",RuleData.previewSigned(app,publicSample()).alreadyAccepted);
    }
}
