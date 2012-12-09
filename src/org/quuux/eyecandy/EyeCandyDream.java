package org.quuux.eyecandy;

import android.service.dreams.DreamService;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

public class EyeCandyDream extends DreamService {

    private static final String TAG = "EyeCandyDream";
    private static final int INTERVAL = 15 * 1000;

    protected EyeCandy eyeCandy;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        setInteractive(false);
        setFullscreen(true);
        setContentView(R.layout.main);

        eyeCandy = new EyeCandy(this, INTERVAL); 
        eyeCandy.attach((TextView)findViewById(R.id.label), 
                        (ImageView)findViewById(R.id.front),
                        (ImageView)findViewById(R.id.back));
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        eyeCandy.startFlipping();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        eyeCandy.stopFlipping();
    }
}
 
