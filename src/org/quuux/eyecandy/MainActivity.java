package org.quuux.eyecandy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.Window;
import android.view.WindowManager;
import org.quuux.orm.Database;

public class MainActivity extends FragmentActivity implements View.OnTouchListener {

    static {
        Database.attach(Image.class);
    }

    private static final String TAG = "MainActivity";

    private BurnsView mBurnsView;
    private ImageAdapter mAdapter;

    final private Handler mHandler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        View v = findViewById(android.R.id.content);
        v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

        mBurnsView = new BurnsView(this);
        setContentView(mBurnsView);

        getActionBar().setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar));

        mAdapter = new ImageAdapter(this);
        mBurnsView.setAdapter(mAdapter);

        mBurnsView.setOnTouchListener(this);

        final Intent intent = new Intent(this, ScrapeService.class);
        startService(intent);

        summon();
  }

    @Override
    public void onResume() {
        super.onResume();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ScrapeService.ACTION_SCRAPE_COMPLETE);
        registerReceiver(mBroadcastReceiver, filter);

        mBurnsView.startAnimation();
    }

    @Override
    public void onPause() {
        super.onPause();
        mBurnsView.stopAnimation();
        unregisterReceiver(mBroadcastReceiver);
    }

    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String action = intent.getAction();
            if (ScrapeService.ACTION_SCRAPE_COMPLETE.equals(action)) {
                mAdapter.fillQueue();
            }

        }
    };

    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        summon();
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private void dismiss() {
        Log.d(TAG, "dismiss ui");
        getActionBar().hide();
    }

    private void dismissDelayed(long t) {
        mHandler.removeCallbacks(mDismissCallback);
        mHandler.postDelayed(mDismissCallback, t);
    }

    private void summon() {
        Log.d(TAG, "summon ui");

        getActionBar().show();

        dismissDelayed(2500);
    }


    final Runnable mDismissCallback = new Runnable() {
        @Override
        public void run() {
            dismiss();
        }
    };


}
