package org.quuux.eyecandy;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.service.dreams.DreamService;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class EyeCandyViewerDream extends DreamService {

    @Override
    public void onCreate() {
        super.onCreate();
        setFullscreen(true);
        setInteractive(true);
        final Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        setContentView(R.layout.viewer_dream);
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
    }
}
