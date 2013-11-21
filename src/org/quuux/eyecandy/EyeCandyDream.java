package org.quuux.eyecandy;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.dreams.DreamService;
import android.widget.ImageView;
import android.widget.TextView;
import org.quuux.orm.Database;

@TargetApi(17)
public class EyeCandyDream extends DreamService {

    static {
        Database.attach(Image.class);
    }

    private static final String TAG = Log.buildTag(EyeCandyDream.class);

    private BurnsView mBurnsView;
    private ImageAdapter mAdapter;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        setInteractive(true);
        setFullscreen(true);

        mBurnsView = new BurnsView(this);
        mAdapter = new ImageAdapter(this, EyeCandyVolley.getRequestQueue(this));
        mBurnsView.setAdapter(mAdapter);

        setContentView(mBurnsView);

        final Intent intent = new Intent(this, ScrapeService.class);
        startService(intent);

    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ScrapeService.ACTION_SCRAPE_COMPLETE);
        registerReceiver(mBroadcastReceiver, filter);

        mBurnsView.startAnimation();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();

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
 
