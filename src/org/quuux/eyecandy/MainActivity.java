package org.quuux.eyecandy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.Window;
import android.view.WindowManager;
import org.quuux.orm.Database;

public class MainActivity extends Activity {

    static {
        Database.attach(Image.class);
    }

    private static final String TAG = "MainActivity";

    private BurnsView mBurnsView;
    private ImageAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View v = findViewById(android.R.id.content);
        v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        mBurnsView = new BurnsView(this);
        setContentView(mBurnsView);

        mAdapter = new ImageAdapter(this);
        mBurnsView.setAdapter(mAdapter);

        final Intent intent = new Intent(this, ScrapeService.class);
        startService(intent);

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
}
