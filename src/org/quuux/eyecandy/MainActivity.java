package org.quuux.eyecandy;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int INTERVAL = 5 * 1000;

    protected EyeCandy eyeCandy;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);

        eyeCandy = new EyeCandy(this, INTERVAL);
        eyeCandy.attach((TextView)findViewById(R.id.label), 
                        (ImageView)findViewById(R.id.front),
                        (ImageView)findViewById(R.id.back));
    }

    @Override
    public void onResume() {
        super.onResume();
        eyeCandy.startFlipping();
    }

    @Override
    public void onPause() {
        super.onPause();
        eyeCandy.stopFlipping();
    }
}
