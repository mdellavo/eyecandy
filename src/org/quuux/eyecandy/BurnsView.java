package org.quuux.eyecandy;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

public class BurnsView extends View {

    public static final Log mLog = new Log(BurnsView.class);
    public static final int MAX_BITMAP_HEIGHT = 2048;
    public static final int MAX_BITMAP_WIDTH = 2048;
    private Paint mTextPaint = new Paint();

    private final class BitmapHolder {

        public Bitmap bitmap;
        public int width, height;
        public Rect src = new Rect();
        public Rect dest = new Rect();
        public Paint paint = new Paint();
        public int age;

        public BitmapHolder(Bitmap bitmap) {
            this.bitmap = bitmap;

            width = bitmap.getWidth();
            height = bitmap.getHeight();

            mLog.d("bitmap size = %dx%d", width, height);

            paint.setAlpha(0);
            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            paint.setDither(true);
        }

        public void update(int elapsed) {
            age += elapsed;

            final double animationProgress = Math.min((double)age / (double)ANIMATION_TIME, 1.0f);

            final int viewWidth = BurnsView.this.getWidth();
            final int viewHeight = BurnsView.this.getHeight();

            final int remainingWidth =  Math.max(width - viewWidth, 0);
            final int remainingHeight = Math.max(height - viewHeight, 0);


            if (isWidthConstrained() || isHeightConstrained()) {
                dest.set(0, 0, viewWidth, viewHeight);

                final int offsetX = Math.max((int)Math.round(remainingWidth * animationProgress), 0);
                final int offsetY = Math.max((int)Math.round(remainingHeight * animationProgress), 0);

                //mLog.d("animating %f (%d / %d)  - %d,%d -> %d, %d", animationProgress, age, ANIMATION_TIME, offsetX, offsetY, viewWidth + offsetX, viewHeight + offsetY);
                src.set(offsetX, offsetY, viewWidth + offsetX, viewHeight + offsetY);

            } else {

                final double scaleWidth = (double)viewWidth / (double)width;
                final double scaleHeight = (double)viewHeight / (double)height;

                final double scale = Math.min(scaleWidth, scaleHeight);
                final int scaledWidth = (int)Math.round(width * scale);
                final int scaledHeight = (int)Math.round(height * scale);

                //mLog.d("scaling %dx%d by %f to %dx%d", width, height, scale, scaledWidth, scaledHeight);

                final int offsetX = remainingWidth / 2;
                final int offsetY = remainingHeight / 2;

                src.set(0, 0, width, height);
                dest.set(offsetX, offsetY, offsetX + scaledWidth, offsetY + scaledHeight);

            }
        }

        public double getAspectRatio() {
            return (double)width / (double)height;
        }

        public boolean isWidthConstrained() {
            return width > BurnsView.this.getWidth();
        }

        public boolean isHeightConstrained() {
            return height > BurnsView.this.getHeight();
        }
    }

    private static final int ANIMATION_TIME = 5 * 1000;
    private static final int TRANSITION_TIME = 2 * 1000;
    public static final int DELAY_MILLIS = 20;
    private static final String TAG = "BurnsView";

    private boolean mRunning;
    private long mLast;
    private BitmapHolder mCurrent;
    private BitmapHolder mNext;
    private int mAnimationTime;
    private int mTransitionTime;

    private int mMaxBitmapWidth = -1, mMaxBitmapHeight = -1;

    private final Handler mHandler = new Handler();

    private final Runnable mAnimator = new Runnable() {
        @Override
        public void run() {
            invalidate();
            mHandler.removeCallbacks(this);
            postDelayed(this, DELAY_MILLIS);
        }
    };

    public BurnsView(Context context) {
        super(context);
        init();
    }

    public BurnsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BurnsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }


    private void init() {
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mMaxBitmapHeight = MAX_BITMAP_HEIGHT;
            mMaxBitmapWidth = MAX_BITMAP_WIDTH;
        }

        mTextPaint.setColor(Color.DKGRAY);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(72);
        mTextPaint.setFakeBoldText(true);
        mTextPaint.setAlpha(255);

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (mMaxBitmapHeight <0 || mMaxBitmapWidth <0) {
                mMaxBitmapWidth = canvas.getMaximumBitmapWidth();
                mMaxBitmapHeight = canvas.getMaximumBitmapHeight();
            }
        }

        final long t1 = System.currentTimeMillis();

        super.onDraw(canvas);

        long now = System.currentTimeMillis();
        int elapsed = (int)(now - mLast);

        if (mTransitionTime > 0) {
            mTransitionTime = Math.max(mTransitionTime - elapsed, 0);

            final double transitionProgress = 1 - ((float)mTransitionTime / (float)TRANSITION_TIME);

            mLog.d(String.format("Transitioning %f (%d / %d)", transitionProgress, mTransitionTime, TRANSITION_TIME));

            if (mTransitionTime == 0 && mNext != null) {
                mLog.d("Transition complete");

                if (mCurrent != null)
                    mCurrent.bitmap.recycle();

                mCurrent = mNext;
                mNext = null;

                mCurrent.paint.setAlpha(255);

            } else {

                int currentAlpha = (int)Math.round(255 * (1- transitionProgress));
                int nextAlpha = (int)Math.round(255 * transitionProgress);

                if (mNext != null) {
                    mNext.paint.setAlpha(nextAlpha);
                }

                if (mCurrent != null) {
                    mCurrent.paint.setAlpha(currentAlpha);
                }

                mTextPaint.setAlpha(currentAlpha);

                mLog.d("currentAlpha = %d | nextAlpha = %d", currentAlpha, nextAlpha);

            }

        }

        if (mNext != null) {
            mNext.update(elapsed);
            canvas.drawBitmap(mNext.bitmap, mNext.src, mNext.dest, mNext.paint);
        }

        if (mCurrent != null) {
            mCurrent.update(elapsed);
            canvas.drawBitmap(mCurrent.bitmap, mCurrent.src, mCurrent.dest, mCurrent.paint);
        } else {
            canvas.drawText("conjuring", getWidth()/2, getHeight()/2, mTextPaint);
        }

        mLast = now;

        final long t2 = System.currentTimeMillis();
        final long renderTime = t2 - t1;
        if (renderTime > 10) {
            mLog.d("onDraw took %d ms", (int) renderTime);
        }
    }

    public void startAnimation() {
        mRunning = true;
        mLast = System.currentTimeMillis();
        mHandler.removeCallbacks(mAnimator);
        mHandler.post(mAnimator);
    }


    public void stopAnimation() {
        mRunning = false;
        mHandler.removeCallbacks(mAnimator);
    }

    protected void swapImage(Bitmap bitmap) {
        mLog.d("swap Image - %s", bitmap);

        mNext = new BitmapHolder(bitmap);
        mTransitionTime = TRANSITION_TIME;

        if (!mRunning)
            startAnimation();
    }

    public double getAspectRatio() {
        return (double)getWidth() / (double)getHeight();
    }

    public int getMaxImageWidth() {
        return mMaxBitmapWidth;
    }

    public int getMaxImageHeight() {
        return mMaxBitmapHeight;
    }

}
