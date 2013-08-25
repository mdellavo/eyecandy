package org.quuux.eyecandy;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.net.Uri;
import android.support.v4.util.LruCache;
import android.view.View;
import android.view.MotionEvent;
import android.widget.TextView;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.graphics.Bitmap;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.AnimatorListenerAdapter;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.*;
import org.quuux.eyecandy.utils.OkHttpStack;
import org.quuux.orm.*;

import java.util.Random;

public class EyeCandy
    implements View.OnTouchListener,
               ScrapeCompleteListener,
               NextImageListener  {

    private static final Log mLog = new Log(EyeCandy.class);

    static {
        Database.attach(Image.class);
    }

    class BitmapLruCache extends LruCache<String, Bitmap> implements ImageLoader.ImageCache {

        public BitmapLruCache(int maxSize) {
            super(maxSize);
        }

        @Override
        public Bitmap getBitmap(String url) {
            return get(url);
        }

        @Override
        public void putBitmap(String url, Bitmap bitmap) {
            put(url, bitmap);
        }
    }

    private static class ImageHolder {
        public long timestamp;
        public Image image;
        public Bitmap bitmap;

        ImageHolder(final long timestamp, final Image image, final Bitmap bitmap) {
            this.timestamp = timestamp;
            this.image = image;
            this.bitmap = bitmap;
        }
    }

    private final EyeCandyDatabase mDatabase;

    private long mLastFlip;
    protected TextView mLabel;
    protected BurnsView mView;
    protected Handler mHandler;
    protected Context mContext;
    protected int mInterval;
    protected int mTick = 0;
    protected Animator mCurrentAnimator;
    protected boolean mLoading = false;
    protected boolean mRunning = false;
    protected Random mRandom = new Random();

    protected ImageHolder mCurrent, mNext;

    private RequestQueue mRequestQueue;
    private ImageLoader mImageLoader;

    protected Runnable mImageFlipper = new Runnable() {
            @Override
            public void run() {
                flipImage();
            }
        };

    protected SimpleOnGestureListener mGestureListener = new SimpleOnGestureListener() {

            @Override
            public boolean onDoubleTap(MotionEvent e) { 
                mLog.d("double tap: %s", e);
                openImageSource();
                return super.onDoubleTap(e);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) 
            {
                mLog.d("fling: e1=%s, e2=%s, velocityX=%f, velocityY=%f", e1, e2, velocityX, velocityY);
                flipImage();
                
                return super.onFling(e1, e2, velocityX, velocityY);
            }

            @Override
            public void onLongPress(MotionEvent e) { 
                mLog.d("on long press : " + e);
                super.onLongPress(e);
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                mLog.d("tap: %s", e);
                showLabel();
                return true;
            }
        };

    GestureDetector mGestureDetector;

    public EyeCandy(Context context, int interval) {
        this.mContext = context;
        this.mInterval = interval;

        mDatabase = EyeCandyDatabase.getInstance(context);

        mRequestQueue = Volley.newRequestQueue(context, new OkHttpStack());
        mImageLoader = new ImageLoader(mRequestQueue, new BitmapLruCache(3));

        mGestureDetector = new GestureDetector(this.mContext, mGestureListener, mHandler);
    }


    public void attach(TextView label, BurnsView view) {
        mHandler = new Handler();

        mLabel = label;

        mView = view;
        mView.setClickable(true);
        mView.setOnTouchListener(this);

        flipImage();
    }

    public boolean onTouch(View view, MotionEvent event)
    {
        return mGestureDetector.onTouchEvent(event);
    }

    public void onSampleComplete(Image image, Bitmap sampled) {
        mLog.d("sample complete: %s", image);
         
        if (sampled == null) {
            mLog.d("Error decoding/sampling image");
            mLoading = false;
            flipImage();
            return;
        }

        showImage(image, sampled);
    }

    public void onScrapeComplete(int numScraped) {
        mLog.d("scrape complete: %d", numScraped);
    }

    public void nextImage(Image image) {
        if (image == null) {
            mLog.d("no next image!");
            return;
        }

        mLog.d("flipping to %s, shown %d times", image, image.getTimesShown());

        fetchImage(image);
    }
 
    public void startFlipping() {
        mRunning = true;

        mHandler.post(mImageFlipper);
    }

    public void stopFlipping() {
        mRunning = false;
        mHandler.removeCallbacks(mImageFlipper);
    }

    public void flipImage() {
        if (mLoading || !mRunning) {
            return;
        }

        mLoading = true;

        final Session session = mDatabase.createSession();
        session.query(Image.class).orderBy("times_shown, RANDOM()").first(new FetchListener<Image>() {
            @Override
            public void onResult(final Image image) {
                nextImage(image);
            }
        });

    }

    public void fetchImage(final Image image) {
        final int width = Math.min(mView.getMeasuredWidth() * 2, 0);
        final int height = Math.min(mView.getMeasuredHeight() * 2, 0);

        mImageLoader.get(image.getUrl(), new ImageLoader.ImageListener() {
            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                mLoading = false;
                showImage(image, response.getBitmap());
            }

            @Override
            public void onErrorResponse(VolleyError error) {
                mLoading = false;
                flipImage();
            }
        }, width, height);
    }

    public Animator fade(View view, float start, float end, long duration, AnimatorListenerAdapter listener) {
        Animator animator = ObjectAnimator.ofFloat(view, "alpha", start, end);
        animator.setDuration(duration);
        if (listener != null) {
            animator.addListener(listener);
        }
        return animator;
    }

    public void showLabel() {
        mLabel.bringToFront();

        fade(mLabel, 0.0f, 1.0f, 1000, new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fade(mLabel, 1.0f, 0.0f, 3000, null).start();
                }
                
                @Override
                public void onAnimationCancel(Animator animation) {
                    fade(mLabel, 1.0f, 0.0f, 1000, null).start();
                }
            }).start();
    }
    
    public void showImage(Image image, Bitmap sampled) {

        // At each mTick:
        // if back exists, fade back then show and slide front
        // else show and slide front
        // swap front and back 

        if (image != null && sampled != null) {



            mCurrent = new ImageHolder(System.currentTimeMillis(), image, sampled);

           //3 mView.swapImage(sampled);

            mLastFlip = System.currentTimeMillis();

            mLabel.setText(image.getTitle());
            showLabel();

            mTick++;

            Tasks.markImageShown(this.mContext, image);
        }

        mHandler.removeCallbacks(mImageFlipper);
        mHandler.postDelayed(mImageFlipper, mInterval);
    }

    public void openImageSource() {

        if (mCurrent == null) {
            mLog.e("current image is null?!");
            return;
        }


        //Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        //mContext.startActivity(i);
    }

    public float randomRange(float min, float max) {
        return min + ((max-min) * mRandom.nextFloat());
    }

    public int randomInt(int min, int max) {
        return min + mRandom.nextInt(max-min);
    }
}
