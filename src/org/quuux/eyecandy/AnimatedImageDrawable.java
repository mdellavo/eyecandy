package org.quuux.eyecandy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;


public class AnimatedImageDrawable extends Drawable implements Drawable.Callback, Runnable, Animatable {

    private static final String TAG = Log.buildTag(AnimatedImageDrawable.class);

    private final Bitmap mBitmap;
    private final Movie mMovie;
    private final Canvas mCanvas;
    private boolean mRunning;
    private long mStartTime;

    private final Handler mHandler;

    public AnimatedImageDrawable(final Context context, final Movie movie) {
        super();

        mHandler = new Handler(context.getMainLooper());

        mMovie = movie;

        mBitmap = Bitmap.createBitmap(mMovie.width(), mMovie.height(),
                Bitmap.Config.ARGB_8888);

        mCanvas = new Canvas(mBitmap);
        setCallback(this);
    }


    @Override
    public int getIntrinsicHeight() {
        return mMovie.height();
    }

    @Override
    public int getIntrinsicWidth() {
        return mMovie.width();
    }

    @Override
    public void draw(final Canvas canvas) {
        if (mMovie == null || mMovie.duration() == 0)
            return;
        final int relTime = (int) ((SystemClock.uptimeMillis() - mStartTime) % mMovie.duration());
        mMovie.setTime(relTime);
        mMovie.draw(mCanvas, 0, 0);
        canvas.drawBitmap(mBitmap, 0, 0, null);
        invalidateSelf();
    }

    @Override
    public void setAlpha(final int i) {

    }

    @Override
    public void setColorFilter(final ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }


    @Override
    public void invalidateDrawable(final Drawable drawable) {
        Log.d(TAG, "invalidate");
    }

    @Override
    public void scheduleDrawable(final Drawable drawable, final Runnable runnable, final long l) {
        Log.d(TAG, "scheduleDrawable");
        mHandler.postAtTime(runnable, drawable, l);
    }

    @Override
    public void unscheduleDrawable(final Drawable drawable, final Runnable runnable) {
        Log.d(TAG, "scheduleDrawable");
        mHandler.removeCallbacks(runnable, drawable);
    }

    @Override
    public void start() {
        if (!isRunning()) {
            mRunning = true;
            run();
        }
    }

    @Override
    public void stop() {
        mRunning = false;
        unscheduleSelf(this);
    }

    @Override
    public boolean isRunning() {
        return mRunning;
    }

    @Override
    public void run() {
        mStartTime = SystemClock.uptimeMillis();
        unscheduleSelf(this);
        scheduleSelf(this, SystemClock.uptimeMillis() + 16);
    }
}
