package org.splashguard.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;

/** Separate offline test package, never included in the shipped app. */
public class FixtureActivity extends Activity {
    private final Handler handler = new Handler();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode=getIntent().getStringExtra("mode");
        if(mode==null)mode="positive";
        final String chosen=mode;
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(0xffe7f0eb); setContentView(root);
        TextView title=new TextView(this); title.setTextSize(26);title.setText(mode.equals("negative")?"欢迎使用，请完成设置":"广告 · 本地测试页面");
        FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);root.addView(title,center);
        Runnable show=()->{
            Button skip=new Button(this);skip.setText(chosen.equals("negative")?"跳过":"跳过广告");
            FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(330,150,Gravity.TOP|Gravity.RIGHT);pos.topMargin=50;pos.rightMargin=40;root.addView(skip,pos);
            skip.setOnClickListener(v->{title.setText("SKIPPED");root.removeView(skip);});
        };
        if(mode.equals("late"))handler.postDelayed(show,14000);else show.run();
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
