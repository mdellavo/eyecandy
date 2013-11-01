
package org.quuux.eyecandy;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class BurnsView extends View {

    public static final Log mLog = new Log(BurnsView.class);

    private static final int MAX_BITMAP_HEIGHT = 2048;
    private static final int MAX_BITMAP_WIDTH = 2048;
    private static final int ANIMATION_TIME = 10 * 1000;
    private static final int TRANSITION_TIME = 2 * 1000;
    private static final int DELAY_MILLIS = 20;
    private static final int TITLE_TEXT_TIME = 5 * 1000;

    private static final String TAG = "BurnsView";

    private boolean mRunning;

    private ImageHolder mPrevious;
    private ImageHolder mCurrent;
    private ImageHolder mNext;
    private TextHolder mWaitText;
    private TextHolder mTitleText;
    private boolean loading = false;
    private int sequenceNumber;
    private long mLast;

    private int mMaxBitmapWidth = -1, mMaxBitmapHeight = -1;

    private ImageAdapter mAdapter;

    private GestureDetector mDetector;

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

        mDetector = new GestureDetector(getContext(), mListener);

        invalidate();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        boolean result = mDetector.onTouchEvent(event);

        if (!result)
            result = super.onTouchEvent(event);

        return result;
    }

    public void setAdapter(ImageAdapter adapter) {
        mAdapter = adapter;
    }

    public void nextImage() {
        if (loading)
            return;

        loading = true;

        mAdapter.nextImage(new ImageAdapter.ImageLoadedListener() {
            @Override
            public void onImageLoaded(final Image image, final Object object) {
                if (image == null || object == null) {
                    mLog.d("error fetching next image, trying again");
                    nextImage();
                    loading = false;
                    return;
                }

                Log.d(TAG, "got next image %s", image);

                sequenceNumber++;

                if (object instanceof Bitmap)
                    mNext = new BitmapHolder(sequenceNumber, image, (Bitmap)object, BurnsView.this);
                else if (object instanceof Movie)
                    mNext = new MovieHolder(sequenceNumber, image, (Movie)object, BurnsView.this);

                if (mCurrent == null)
                    flipImage();

                loading = false;
            }
        });
    }

    public void previousImage() {
        mAdapter.previousImage(new ImageAdapter.ImageLoadedListener() {
            @Override
            public void onImageLoaded(final Image image, final Object object) {
                if (image == null || object == null)
                    return;

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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mMaxBitmapHeight = MAX_BITMAP_HEIGHT;
            mMaxBitmapWidth = MAX_BITMAP_WIDTH;
        }

        if (!mRunning)
            return;

        if (mNext == null && !loading)
            nextImage();


        if (mWaitText == null)
            mWaitText = new TextHolder(this);

        if (mTitleText == null)
            mTitleText = new TextHolder(this);

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

        if (mCurrent != null && ANIMATION_TIME - mCurrent.age <= 0) {
            if (mNext != null) {
                mLog.d("Animation complete, flipping");
                flipImage();
            } else {
                mLog.d("animation expired and nothing to do ! (loading = %s, current = %s, next = %s)", loading, mCurrent, mNext);
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

    public void showTitle() {
        if (mCurrent == null)
            return;

        mTitleText.setText(mCurrent.image.getTitle());
    }

    private GestureDetector.SimpleOnGestureListener mListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(final MotionEvent e) {
            showTitle();
            return true;
        }
    };

    interface Drawable {
        void onDraw(final Canvas canvas, final int elapsed);
    }

    static abstract class ImageHolder implements Drawable {

        final Image image;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);

        final Matrix transformation = new Matrix();

        final int sequenceNumber;
        final float zoomFactor = 1.5f;

        final Rect orig = new Rect();
        final Rect container = new Rect();
        final Rect zoomed = new Rect();
        final Rect fitted = new Rect();
        final Rect frame = new Rect();

        int age;

        public ImageHolder(final int sequenceNumber, final Image image, final View parent, final int width, final int height) {
            this.sequenceNumber = sequenceNumber;
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

        @Override
        public void onDraw(final Canvas canvas, final int elapsed) {
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
                transition(1.0f - transitionProgress);
            }

            final boolean alt = sequenceNumber % 2 == 0;

            final Rect src = alt ? fitted : zoomed;
            final Rect dest = alt ? zoomed : fitted;

            transformation.preTranslate(
                    lerp(getTranslateX(src, container), getTranslateX(dest, container), (float)animationProgress),
                    lerp(getTranslateY(src, container), getTranslateY(dest, container), (float)animationProgress)
            );
            transformation.preScale(
                    lerp(getScaleX(src, orig), getScaleX(dest, orig), (float)animationProgress),
                    lerp(getScaleY(src, orig), getScaleY(dest, orig), (float)animationProgress)
            );

            canvas.setMatrix(transformation);

            draw(canvas);

            canvas.restore();
        }

    }

    static class BitmapHolder extends ImageHolder {

        final Bitmap bitmap;

        public BitmapHolder(final int sequenceNumber, final Image image, final Bitmap bitmap, final View parent) {
            super(sequenceNumber, image, parent, bitmap.getWidth(), bitmap.getHeight());
            this.bitmap = bitmap;
        }

        @Override
        void draw(final Canvas canvas) {
            canvas.drawBitmap(bitmap, 0, 0, paint);
        }
    }

    static class MovieHolder extends ImageHolder {

        final Movie movie;
        final Canvas canvas;
        final Bitmap bitmap;

        public MovieHolder(final int sequenceNumber, final Image image, final Movie movie, final View parent) {
            super(sequenceNumber, image, parent, movie.width(), movie.height());
            this.movie = movie;
            bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
        }

        @Override
        void draw(final Canvas canvas) {
            // NB drawing to bitmap backed canvas and drawing the backing bitmap to view canvas preserves hw accel
            if (movie.duration() > 0)
                movie.setTime(age % movie.duration());
            movie.draw(this.canvas, 0, 0, paint);
            canvas.drawBitmap(bitmap, 0, 0, paint);
        }
    }

    static class TextHolder implements Drawable {

        String text;
        Paint paint = new Paint();
        int x,y;

        public TextHolder(final View parent) {
            paint.setColor(Color.DKGRAY);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(72);
            paint.setFakeBoldText(true);
            paint.setAlpha(255);

            setText(parent.getContext().getString(R.string.wait));

            x = parent.getWidth() / 2;
            y = parent.getHeight() / 2;
        }

        public void setText(final String text) {
            this.text = text;
        }

        @Override
        public void onDraw(final Canvas canvas, final int elapsed) {
            canvas.drawText(text, x, y, paint);
        }
    }
}
