package org.quuux.eyecandy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;

import org.quuux.eyecandy.utils.GifDecoder;

// FIXME should probably cap max bitmap size for hw surface, see EyeCandyView
public class AnimatedImageDrawable extends Drawable implements Drawable.Callback, Runnable, Animatable {

    private static final String TAG = Log.buildTag(AnimatedImageDrawable.class);

    private final GifDecoder mDecoder;
    private final Rect mDest;
    private boolean mRunning;
    private long mStartTime;

    private final Handler mHandler;

    private int mFrame;

    private final Runnable mCallback = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
        }
    };

    public AnimatedImageDrawable(final Context context, final GifDecoder decoder, final Rect dest) {
        super();

        mDecoder = decoder;
        mDest = dest;

        mHandler = new Handler(context.getMainLooper());

        final int width = mDecoder.getWidth() > 0 ? mDecoder.getWidth() : dest.width();
        final int height = mDecoder.getHeight() > 0 ? mDecoder.getHeight() : dest.height();

        setCallback(this);

        mFrame = 0;

    }

    @Override
    public int getIntrinsicHeight() {
        return mDecoder.getHeight();
    }

    @Override
    public int getIntrinsicWidth() {
        return mDecoder.getWidth();
    }

    @Override
    public void draw(final Canvas canvas) {
        final Bitmap bitmap = mDecoder.getFrame(mFrame);
        canvas.drawBitmap(bitmap, 0, 0, null);
        mHandler.postDelayed(mCallback, mDecoder.getDelay(mFrame));
        mFrame = (mFrame + 1) % mDecoder.getFrameCount();
    }

    @Override
    public void setAlpha(final int i) {

    }

    @Override
    public void setColorFilter(final ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
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

    public Bitmap getFrame(final int i) {
        return mDecoder.getFrame(i);
    }
}
