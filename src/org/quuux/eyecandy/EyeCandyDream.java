package org.quuux.eyecandy;

import android.service.dreams.DreamService;
import android.util.Log;
import android.widget.ImageSwitcher;

public class EyeCandyDream extends DreamService {

    private static final String TAG = "EyeCandyDream";

    protected EyeCandy eyeCandy;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        setInteractive(false);
        setFullscreen(true);
        setContentView(R.layout.main);

        eyeCandy = new EyeCandy(this);
        eyeCandy.attach((ImageSwitcher)findViewById(R.id.image));
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
 
