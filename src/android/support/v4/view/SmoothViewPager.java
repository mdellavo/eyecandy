package android.support.v4.view;

import android.content.Context;
import android.util.AttributeSet;

import org.quuux.eyecandy.Log;

public class SmoothViewPager extends ViewPager {
    private static final String TAG = Log.buildTag(SmoothViewPager.class);

    public SmoothViewPager(final Context context) {
        super(context);
    }

    public SmoothViewPager(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    void smoothScrollTo(final int x, final int y, final int velocity) {
        Log.d(TAG, "smoothScrollTo(x: %s | y: %s | vel: %s", x, y, velocity);
        super.smoothScrollTo(x, y, 1);
    }
}
