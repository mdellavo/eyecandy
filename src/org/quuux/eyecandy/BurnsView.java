package org.quuux.eyecandy;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

public class BurnsView extends View {

    public static final Log mLog = new Log(BurnsView.class);

    static abstract class ImageHolder {

        final Image image;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);

        final Matrix transformation = new Matrix();

        final float zoomFactor = 2f;

        final Rect orig = new Rect();
        final Rect container = new Rect();
        final Rect zoomed = new Rect();
        final Rect fitted = new Rect();
        final Rect frame = new Rect();

        int age;
        public ImageHolder(final Image image, final View parent, final int width, final int height) {
            this.image = image;

            orig.set(0, 0, width, height);
            container.set(0, 0, parent.getWidth(), parent.getHeight());

            paint.setAlpha(0);

            mLog.d("orig rect = %s (ar = %s)", orig, getAspectRatio(orig));
            mLog.d("container rect = %s (ar = %s)", container, getAspectRatio(container));

            fit(fitted);
            zoom(fitted, zoomed, zoomFactor);

            mLog.d("fitted rect = %s (ar = %s | scale = %.03f, %.03f)", fitted, getAspectRatio(fitted), getScaleX(fitted, orig), getScaleY(fitted, orig));
            mLog.d("zoomed rect = %s (ar = %s | scale = %.03f, %.03f)", zoomed, getAspectRatio(zoomed), getScaleX(zoomed, orig), getScaleY(zoomed, orig));
        }

        double getAspectRatio(final Rect rect) {
            return (double)rect.width() / (double)rect.height();
        }

        void fit(final Rect dest) {
            final double imageAspectRatio = getAspectRatio(orig);
            final double containerAspectRatio = getAspectRatio(container);

            final boolean heightContrained = containerAspectRatio > imageAspectRatio;

            final int scaledWidth = heightContrained ? orig.width() * container.height()/orig.height() : container.width();
            final int scaledHeight = heightContrained ? container.height() : orig.height() * container.width() / orig.width();

            final int top = (container.height() - scaledHeight) / 2;
            final int left = (container.width() - scaledWidth) / 2;

            dest.set(left, top, left + scaledWidth, top + scaledHeight);
        }

        float getTranslateX(final Rect a, final Rect b) {
            return (b.width() - a.width()) / 2.0f;
        }

        float getTranslateY(final Rect a, final Rect b) {
            return (b.height() - a.height()) / 2.0f;
        }

        float getScaleX(final Rect a, final Rect b) {
            return (float)a.width() / (float)b.width();
        }

        float getScaleY(final Rect a, final Rect b) {
            return (float)a.height() / (float)b.height();
        }

        float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        void zoom(final Rect src, final Rect dest, final double scale) {
            final int scaledWidth = (int)Math.round((src.width() * scale) / 2.0f);
            final int scaledHeight = (int)Math.round((src.height() * scale) / 2.0f);
            final int centerX = src.centerX();
            final int centerY = src.centerY();
            dest.set(centerX - scaledWidth, centerY - scaledHeight, centerX + scaledWidth, centerY + scaledHeight);
        }

        void transition(final double transitionProgress) {
            paint.setAlpha(transitionProgress > 0 ? (int)Math.round(255 * transitionProgress) : 0);
        }

        abstract void draw(final Canvas canvas);

        void onDraw(final Canvas canvas, final int elapsed) {
            age += elapsed;

            transformation.reset();
            canvas.save();

            final double animationProgress = (double)age / (double)ANIMATION_TIME;

            if (age < TRANSITION_TIME) {
                final double transitionProgress = (double)age / (double)TRANSITION_TIME;
                //mLog.d("IN >>> progress  - animation = %.03f | transition = %.03f", animationProgress, transitionProgress);
                transition(transitionProgress);
            } else if (age > ANIMATION_TIME - TRANSITION_TIME) {
                final double transitionProgress = (double)(age - (ANIMATION_TIME - TRANSITION_TIME)) / (double)TRANSITION_TIME;
                //mLog.d("OUT <<< progress  - animation = %.03f | transition = %.03f", animationProgress, 1.0f - transitionProgress);
                transition(1.0f  - transitionProgress);
            }

            transformation.preTranslate(
                    lerp(getTranslateX(fitted, container), getTranslateX(zoomed, container), (float)animationProgress),
                    lerp(getTranslateY(fitted, container), getTranslateY(zoomed, container), (float)animationProgress)
            );
            transformation.preScale(
                    lerp(getScaleX(fitted, orig), getScaleX(zoomed, orig), (float)animationProgress),
                    lerp(getScaleY(fitted, orig), getScaleY(zoomed, orig), (float)animationProgress)
            );

            canvas.setMatrix(transformation);

            draw(canvas);

            canvas.restore();
        }

    }

    static class BitmapHolder extends ImageHolder {

        final Bitmap bitmap;

        public BitmapHolder(final Image image, final Bitmap bitmap, final View parent) {
            super(image, parent, bitmap.getWidth(), bitmap.getHeight());
            this.bitmap = bitmap;
        }

        @Override
        void draw(final Canvas canvas) {
            canvas.drawBitmap(bitmap, 0, 0, paint);
        }

    }

    static class MovieHolder extends ImageHolder {

        final Movie movie;

        public MovieHolder(final Image image, final Movie movie, final View parent) {
            super(image, parent, movie.width(), movie.height());
            this.movie = movie;
        }

        @Override
        void draw(final Canvas canvas) {
            movie.draw(canvas, 0, 0, paint);
        }
    }

    static class TextHolder extends ImageHolder {

        String text;

        public TextHolder(final View parent) {
            super(null, parent, parent.getWidth(), parent.getHeight());

            text = parent.getContext().getString(R.string.wait);

            paint.setColor(Color.DKGRAY);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(72);
            paint.setFakeBoldText(true);
            paint.setAlpha(255);
        }

        @Override
        void onDraw(final Canvas canvas, final int elapsed) {
            draw(canvas);
        }

        @Override
        void draw(final Canvas canvas) {
            canvas.drawText(text, container.width() / 2, container.height() / 2, paint);
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
    private ImageHolder mWaitText;
    private boolean loading = false;


    private long mLast;

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
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mMaxBitmapHeight = MAX_BITMAP_HEIGHT;
            mMaxBitmapWidth = MAX_BITMAP_WIDTH;
        }

        invalidate();
    }

    public void setAdapter(ImageAdapter adapter) {
        mAdapter = adapter;
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

                mNext = new BitmapHolder(image, bitmap, BurnsView.this);

                if (mCurrent == null)
                    flipImage();

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

                mPrevious = new BitmapHolder(image, bitmap, BurnsView.this);
            }
        });
    }

    private void flipImage() {
        mPrevious = mCurrent;
        mCurrent = mNext;
        mNext = null;
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


        if (mWaitText == null)
            mWaitText = new TextHolder(this);

        long now = System.currentTimeMillis();
        int elapsed = (int)(now - mLast);
        mLast = now;

        final boolean transitioning = mCurrent != null && mNext != null && (ANIMATION_TIME - mCurrent.age) <= TRANSITION_TIME;

        int textAlpha = 0;

        if (mCurrent != null) {
            mCurrent.onDraw(canvas, elapsed);
        } else {
            mWaitText.onDraw(canvas, elapsed);
        }

        if (transitioning) {
            mNext.onDraw(canvas, elapsed);
        }

        if (mCurrent != null && mNext != null && ANIMATION_TIME - mCurrent.age <= 0) {
            mLog.d("Animation complete, flipping");
            flipImage();
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

}
