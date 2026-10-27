package org.splashguard.app.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Test APK only. An independently rendered foreground window for real accessibility actions. */
public final class SyntheticSplashActivity extends Activity {
    private FrameLayout root;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);if(getActionBar()!=null)getActionBar().hide();
        root=new FrameLayout(this);root.setBackgroundColor(Color.WHITE);setContentView(root);
        TextView label=new TextView(this);label.setText("广告（合成测试）");label.setTextSize(20);label.setTextColor(Color.BLACK);
        FrameLayout.LayoutParams labelParams=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);labelParams.leftMargin=dp(16);labelParams.topMargin=dp(200);root.addView(label,labelParams);
        addSkip(48);if("ambiguous".equals(getIntent().getStringExtra("fixtureMode")))addSkip(112);
    }
    private void addSkip(int top){
        Button skip=new Button(this);skip.setText("跳过广告");skip.setTextSize(18);skip.setAllCaps(false);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(148),dp(48),Gravity.TOP|Gravity.RIGHT);params.topMargin=dp(top);params.rightMargin=dp(12);root.addView(skip,params);
        skip.setOnClickListener(view->{root.removeAllViews();TextView content=new TextView(this);content.setText("FIXTURE_CONTENT");content.setTextSize(22);content.setTextColor(Color.BLACK);content.setGravity(Gravity.CENTER);root.addView(content,new FrameLayout.LayoutParams(-1,-1));});
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
