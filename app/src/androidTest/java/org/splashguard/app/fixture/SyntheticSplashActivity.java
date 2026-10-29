package org.splashguard.app.fixture;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Test APK only: independent ad window, including a retained task returning from background. */
public final class SyntheticSplashActivity extends Activity {
    private FrameLayout root;private String mode,instance;private int resumes;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);if(getActionBar()!=null)getActionBar().hide();
        root=new FrameLayout(this);root.setBackgroundColor(Color.WHITE);setContentView(root);
        mode=getIntent().getStringExtra("fixtureMode");
        instance="INSTANCE_"+android.os.Process.myPid()+"_"+android.os.SystemClock.elapsedRealtime();
        if("warm".equals(mode)||"late_dialog".equals(mode)){
            content("FIXTURE_WARM_CONTENT");
            if("late_dialog".equals(mode)){
                Button show=new Button(this);show.setText("显示同应用对话框");
                FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);params.bottomMargin=dp(80);root.addView(show,params);
                show.setOnClickListener(view->new AlertDialog.Builder(this).setMessage("FIXTURE_DIALOG")
                        .setPositiveButton("显示测试广告",(dialog,which)->ad()).show());
            }
        }else if("controlled".equals(mode)){
            content("FIXTURE_WAITING");
            addControl("显示测试广告",80,this::ad);
            addControl("显示小窗口测试广告",144,this::smallDialog);
        }else ad();
    }
    @Override protected void onResume(){super.onResume();resumes++;if("warm".equals(mode)&&resumes>1)ad();}
    private void ad(){
        root.removeAllViews();
        TextView label=new TextView(this);label.setText("广告（合成测试）");label.setTextSize(20);label.setTextColor(Color.BLACK);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);params.leftMargin=dp(16);params.topMargin=dp(200);root.addView(label,params);
        addSkip(48);if("ambiguous".equals(mode))addSkip(112);
    }
    private void content(String value){
        root.removeAllViews();TextView text=new TextView(this);text.setText(value);text.setContentDescription(instance);
        text.setTextSize(22);text.setTextColor(Color.BLACK);text.setGravity(Gravity.CENTER);root.addView(text,new FrameLayout.LayoutParams(-1,-1));
    }
    private void addControl(String label,int bottom,Runnable action){
        Button button=new Button(this);button.setText(label);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        params.bottomMargin=dp(bottom);root.addView(button,params);button.setOnClickListener(view->action.run());
    }
    private void smallDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.RIGHT);
        TextView evidence=new TextView(this);evidence.setText("广告（合成小窗口）");box.addView(evidence);
        Button skip=new Button(this);skip.setText("跳过广告");skip.setContentDescription("FIXTURE_DIALOG_SKIP");
        box.addView(skip,new LinearLayout.LayoutParams(dp(148),dp(48)));
        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).create();
        skip.setOnClickListener(view->{dialog.dismiss();content("FIXTURE_DIALOG_ACTION");});
        dialog.show();WindowManager.LayoutParams params=dialog.getWindow().getAttributes();
        params.width=dp(300);params.height=dp(200);params.gravity=Gravity.TOP|Gravity.RIGHT;params.y=dp(32);
        dialog.getWindow().setAttributes(params);
    }
    private void addSkip(int top){
        Button skip=new Button(this);skip.setText("跳过广告");skip.setTextSize(18);skip.setAllCaps(false);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(148),dp(48),Gravity.TOP|Gravity.RIGHT);params.topMargin=dp(top);params.rightMargin=dp(12);root.addView(skip,params);
        skip.setOnClickListener(view->content("FIXTURE_CONTENT"));
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
