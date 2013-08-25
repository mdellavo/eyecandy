package org.quuux.eyecandy;

import android.app.Activity;
import android.content.Intent;
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

        View v = findViewById(android.R.id.content);
        v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

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
        mBurnsView.startAnimation();
    }

    @Override
    public void onPause() {
        super.onPause();
        mBurnsView.stopAnimation();
    }
}
