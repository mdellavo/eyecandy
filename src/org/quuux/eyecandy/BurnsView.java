package org.quuux.eyecandy;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

public class BurnsView extends View {

    public static final Log mLog = new Log(BurnsView.class);

    abstract class ImageHolder {

        final Image image;
        final int width, height, maxWidth, maxHeight;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);

        int age;
        double animationProgress, transitionProgress;

        public ImageHolder(final Image image, final int width, final int height, final int maxWidth, final int maxHeight) {
            this.image = image;
            this.width = width;
            this.height = height;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;

            paint.setAlpha(0);
        }

        double getAspectRatio() {
            return (double)width / (double)height;
        }

        boolean isWidthConstrained() {
            return width > maxWidth;
        }

        boolean isHeightConstrained() {
            return height > maxHeight;
        }

        void animate() {
        }

        void transition() {
            paint.setAlpha((int)Math.round(255 * transitionProgress));
        }

        abstract void draw(final Canvas canvas);

        void onDraw(final Canvas canvas, final int elapsed) {

            age += elapsed;

            if (transitionProgress > 0) {
                transition();
            }

            animate();

            canvas.save();
            draw(canvas);
            canvas.restore();
        }

        void setTransitionProgress(final double progress) {
            transitionProgress = progress;
        }

        void setAnimationProgress(final double progress) {
            animationProgress = progress;
        }
    }

    class BitmapHolder extends ImageHolder {

        final Bitmap bitmap;
        final Rect src = new Rect();
        final Rect dest = new Rect();

        boolean transitioning = false;

        public BitmapHolder(final Image image, final Bitmap bitmap, final int maxWidth, final int maxHeight) {
            super(image, bitmap.getWidth(), bitmap.getHeight(), maxWidth, maxHeight);
            this.bitmap = bitmap;
            mLog.d("bitmap size = %dx%d", width, height);
        }

        @Override
        void animate() {
            super.animate();
//            final double animationProgress = Math.min((double) age / (double) ANIMATION_TIME, 1.0f);

//            if (isWidthConstrained() || isHeightConstrained()) {
//
//
//                final int remainingWidth =  Math.max(width - maxWidth, 0);
//                final int remainingHeight = Math.max(height - maxHeight, 0);
//
//                dest.set(0, 0, maxWidth, maxHeight);
//
//                final int offsetX = Math.max((int)Math.round(remainingWidth * animationProgress), 0);
//                final int offsetY = Math.max((int)Math.round(remainingHeight * animationProgress), 0);
//
//                //mLog.d("animating %f (%d / %d)  - %d,%d -> %d, %d", animationProgress, age, ANIMATION_TIME, offsetX, offsetY, viewWidth + offsetX, viewHeight + offsetY);
//                src.set(offsetX, offsetY, maxWidth + offsetX, maxHeight + offsetY);
//
//            } else {
//                final double scaleWidth = (double)maxWidth / (double)width;
//                final double scaleHeight = (double)maxHeight / (double)height;
//
//                final double scale = Math.min(scaleWidth, scaleHeight);
//                final int scaledWidth = (int)Math.round(width * scale);
//                final int scaledHeight = (int)Math.round(height * scale);
//
//                //mLog.d("scaling %dx%d by %f to %dx%d", width, height, scale, scaledWidth, scaledHeight);
//                final int remainingWidth =  Math.max(maxWidth - scaledWidth, 0);
//                final int remainingHeight = Math.max(maxHeight - scaledHeight, 0);
//
//
//
//                final int offsetX = remainingWidth / 2;
//                final int offsetY = remainingHeight / 2;
//
//                src.set(0, 0, width, height);
//                dest.set(offsetX, offsetY, offsetX + scaledWidth, offsetY + scaledHeight);
//
//            }
        }

        @Override
        void draw(final Canvas canvas) {
            canvas.drawBitmap(bitmap, 0, 0, paint);
        }

    }

    class MovieHolder extends ImageHolder {

        final Movie movie;

        public MovieHolder(final Image image, final Movie movie, final int maxWidth, final int maxHeight) {
            super(image, movie.width(), movie.height(), maxWidth, maxHeight);
            this.movie = movie;
        }

        @Override
        void animate() {
            movie.setTime((int)age % movie.duration());
        }

        @Override
        void draw(final Canvas canvas) {
            movie.draw(canvas, 0, 0, paint);
        }
    }

    class TextHolder extends ImageHolder {

        public TextHolder() {
            super(null, 0, 0, 0, 0);
            paint.setColor(Color.DKGRAY);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(72);
            paint.setFakeBoldText(true);
            paint.setAlpha(255);
        }

        @Override
        void draw(final Canvas canvas) {
            canvas.drawText(getContext().getString(R.string.wait), getWidth()/2, getHeight()/2, paint);
        }

    }

    public static final int MAX_BITMAP_HEIGHT = 2048;
    public static final int MAX_BITMAP_WIDTH = 2048;
    private static final int ANIMATION_TIME = 10 * 1000;
    private static final int TRANSITION_TIME = 2 * 1000;
    public static final int DELAY_MILLIS = 20;

    private static final String TAG = "BurnsView";

    private boolean mRunning;

    private ImageHolder mPrevious;
    private ImageHolder mCurrent;
    private ImageHolder mNext;
    private ImageHolder mWaitText = new TextHolder();
    private boolean loading = false;


    private long mLast;
    private int mAnimationTime;

    private ImageAdapter mAdapter;

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
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mMaxBitmapHeight = MAX_BITMAP_HEIGHT;
            mMaxBitmapWidth = MAX_BITMAP_WIDTH;
        }

        invalidate();
    }

    public void setAdapter(ImageAdapter adapter) {
        mAdapter = adapter;
        mAnimationTime = TRANSITION_TIME;
    }

    public void nextImage() {
        loading = true;

        mAdapter.nextImage(new ImageLoadedListener() {
            @Override
            public void onImageLoaded(final Image image, final Bitmap bitmap) {
                if (image == null || bitmap == null ) {
                    mLog.d("error fetching next image, trying again");
                    nextImage();
                    return;
                }

                Log.d(TAG, "got next image %s", image);

                mNext = new BitmapHolder(image, bitmap, getWidth(), getHeight());

                if (mCurrent == null)
                    mAnimationTime = TRANSITION_TIME;

                loading = false;
            }
        });
    }

    public void previousImage() {
        mAdapter.previousImage(new ImageLoadedListener() {
            @Override
            public void onImageLoaded(Image image, Bitmap bitmap) {
                if (image == null || bitmap == null)
                    return;

                mPrevious = new BitmapHolder(image, bitmap, getWidth(), getHeight());
            }
        });
    }

    private void flipImage() {
        mPrevious = mCurrent;
        mCurrent = mNext;
        mNext = null;

        mCurrent.paint.setAlpha(255);
        mAnimationTime = ANIMATION_TIME;
    }


    @Override
    protected void onDraw(Canvas canvas) {

        final long t1 = System.currentTimeMillis();

        super.onDraw(canvas);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (mMaxBitmapHeight <0 || mMaxBitmapWidth <0) {
                mMaxBitmapWidth = canvas.getMaximumBitmapWidth();
                mMaxBitmapHeight = canvas.getMaximumBitmapHeight();

                if (mAdapter != null)
                    mAdapter.setMaxBitmapSize(mMaxBitmapWidth, mMaxBitmapHeight);
            }
        }

        if (!mRunning)
            return;

        if (mNext == null && !loading)
            nextImage();

        long now = System.currentTimeMillis();
        int elapsed = (int)(now - mLast);
        mLast = now;

        mAnimationTime = Math.max(mAnimationTime - elapsed, 0);

        //mLog.d("mAnimationTime = %s | elapsed = %s", mAnimationTime, elapsed);

        final boolean transitioning = mAnimationTime <= TRANSITION_TIME;

        int textAlpha = 0;

        if (transitioning) {

            if (!loading && mNext == null)
                nextImage();

            final double transitionProgress = 1.0f - ((double)(TRANSITION_TIME - mAnimationTime) / (double)TRANSITION_TIME);

            //mLog.d("transitioning - cur = %.02f / next = %.02f", transitionProgress, 1.0f - transitionProgress);

            if (mCurrent != null)
                mCurrent.setTransitionProgress(transitionProgress);
            else
                mWaitText.setTransitionProgress(transitionProgress);


            if (mNext != null)
                mNext.setTransitionProgress(1.0f - transitionProgress);
            else
                mWaitText.setTransitionProgress(1.0f - transitionProgress);

        }

        final double animationProgress = (double)(ANIMATION_TIME - mAnimationTime) / (double)ANIMATION_TIME;
        //mLog.d("animating - %.02f", animationProgress);

        if (mCurrent != null) {
            mCurrent.setAnimationProgress(animationProgress);
            mCurrent.onDraw(canvas, elapsed);
        } else {
            mWaitText.onDraw(canvas, elapsed);
        }

        if (transitioning) {
            if (mNext != null) {
                //mNext.setAnimationProgress(1.0f - animationProgress);
                mNext.onDraw(canvas, elapsed);
            } else {
                mWaitText.onDraw(canvas, elapsed);
            }
        }


        if (mAnimationTime == 0) {
            if ( mNext != null) {
                mLog.d("Animation complete, flipping");
                flipImage();
            } else if (!loading) {
                nextImage();
            }
        }

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
        invalidate();
    }


    public void stopAnimation() {
        mRunning = false;
        mHandler.removeCallbacks(mAnimator);
    }

    public double getAspectRatio() {
        return (double)getWidth() / (double)getHeight();
    }

}
