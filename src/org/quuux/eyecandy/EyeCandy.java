package org.quuux.eyecandy;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.AnimationUtils;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.animation.DecelerateInterpolator;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.AnimatorListenerAdapter;

import java.util.Random;

public class EyeCandy
    implements View.OnTouchListener,
               FetchCompleteListener,
               ScrapeCompleteListener,
               NextImageListener, 
               SampleCompleteListener {
 
    private static final String TAG = "EyeCandyBase";
    private static final String SUBREDDITS[] = { 
        "earthporn",
        "villageporn",
        "cityporn",
         "spaceporn",
         "waterporn",
         "abandonedporn",
         "animalporn",
         "humanporn",
         "botanicalporn",
         "adrenalineporn",
         "destructionporn",
         "movieposterporn",
         "albumartporn",
         "machineporn",
         //"newsporn",
         "geekporn",
         "bookporn",
         //"mapporn",
         "adporn",
         "designporn",
         "roomporn",
         //"militaryporn",
         //"historyporn",
         //"quotesporn",
         "skyporn",
         "fireporn",
         "infrastructureporn",
         "macroporn",
         "instrumentporn",
         "climbingporn",
         "architectureporn",
         "artporn",
         "cemeteryporn",
         //"carporn",
         "fractalporn",
         "exposureporn",
         //"gunporn",
         //"culinaryporn",
         "dessertporn",
         "agricultureporn",
         "boatporn",
        "geologyporn",
        "futureporn",
        "winterporn",
        //"foodporn"
    };

    protected Image current;
    protected TextView mLabel;
    protected ImageView mImageFront, mImageBack;
    protected Handler mHandler;
    protected Context context;
    protected int interval;
    protected int tick = 0;
    protected Animator mCurrentAnimator;

    protected Random mRandom = new Random();

    protected Runnable mImageFlipper = new Runnable() {
            @Override
            public void run() {
                flipImage();
            }
        };

    protected SimpleOnGestureListener mGestureListener = new SimpleOnGestureListener() {

            @Override
            public boolean onDoubleTap(MotionEvent e) { 
                Log.d(TAG, "double tap: " + e);
                openImageSource();
                return super.onDoubleTap(e);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) 
            {
                Log.d(TAG, "fling: e1=" + e1 +", e2=" + e2 + ", velocityX=" + velocityX + "velocityY=" + velocityY);
                flipImage();
                
                return super.onFling(e1, e2, velocityX, velocityY);
            }

            @Override
            public void onLongPress(MotionEvent e) { 
                Log.d(TAG, "on long press : " + e);
                super.onLongPress(e);
            }
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                Log.d(TAG, "tap: " + e);
                showLabel();
                return true;
            }
        };

    GestureDetector mGestureDetector;

    public EyeCandy(Context context, int interval) {
        this.context = context;
        this.interval = interval;

        mGestureDetector = new GestureDetector(this.context, mGestureListener, mHandler);
    }

    public ImageView attach(ImageView image) {
        image.setClickable(true);
        image.setOnTouchListener(this);
        return image;
    }

    public void attach(TextView label, ImageView front, ImageView back) {
        mHandler = new Handler();
                                                
        mLabel = label;
        mImageFront = attach(front);
        mImageBack = attach(back);

        flipImage();
        Tasks.scrapeReddit(this.context, SUBREDDITS, this);
    }

    public boolean onTouch(View view, MotionEvent event)
    {
        return mGestureDetector.onTouchEvent(event);
    }

    public void onFetchComplete(Image image) {

        if (image == null) {
            Log.d(TAG, "Error fetching image");
            flipImage();
            return;
        }

        Log.d(TAG, "fetch complete " + image);
        sampleImage(image);
    }

    public void onSampleComplete(Image image, Bitmap sampled) {
        Log.d(TAG, "sample complete " + image);         
        showImage(image, sampled);
    }

    public void onScrapeComplete(int numScraped) {
        Log.d(TAG, "scrape complete " + numScraped);
    }

    public void nextImage(Image image) {
        if (image == null) {
            Log.d(TAG, "no next image!");
            return;
        }

        Log.d(TAG, "flipping to " + image + ", shown " + image.getTimesShown() + " times");

        if (image.isCached(this.context)) {
            sampleImage(image);
        } else {
            fetchImage(image);
        }
    }
 
    public void startFlipping() {
        mHandler.postDelayed(mImageFlipper, interval);
    }

    public void stopFlipping() {
        mHandler.removeCallbacks(mImageFlipper);
    }

    public void flipImage() {
        Tasks.nextImage(this.context, this);
    }

    public void fetchImage(Image image) {
        Tasks.fetchImage(this.context, image, this);
    }

    public void sampleImage(Image image) {
        int width = mImageFront.getMeasuredWidth() * 2;
        int height = mImageFront.getMeasuredHeight() * 2;
        Tasks.sampleImage(this.context, image, width, height, this);
    }

    public void burnsImage(ImageView image) {
        final Rect startBounds = new Rect();
        final Point globalOffset = new Point();            

        float startScale = randomRange(1f, 2f);
        float finalScale = randomRange(1f, 2f);
 
        Log.d(TAG, "scaling from " + startScale + " -> " + finalScale);

        image.getGlobalVisibleRect(startBounds, globalOffset);
        Log.d(TAG, "start bounds = " + startBounds);
        Log.d(TAG, "global offset = " + globalOffset);

        startBounds.offset(-globalOffset.x, -globalOffset.y);
        Log.d(TAG, "localized start bounds = " + startBounds);
        
        final Rect finalBounds = new Rect(startBounds);
        int finalOffsetX = randomInt(-startBounds.width() / 4, startBounds.width() / 4);
        int finalOffsetY = randomInt(-startBounds.height() / 4, startBounds.height() / 4);

        Log.d(TAG, "offsetting image " + finalOffsetX + ", " + finalOffsetY);
 
        finalBounds.offset(finalOffsetX, finalOffsetY);
        Log.d(TAG, "final bounds = " + finalBounds);

        AnimatorSet set = new AnimatorSet();
        set
            .play(ObjectAnimator.ofFloat(image, View.SCALE_X, startScale, finalScale))
            .with(ObjectAnimator.ofFloat(image, View.SCALE_Y, startScale, finalScale))
            .with(ObjectAnimator.ofFloat(image, View.X,startBounds.left, finalBounds.left))
            .with(ObjectAnimator.ofFloat(image, View.Y, startBounds.top, finalBounds.top));

        set.setDuration(2 * interval);
        //set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                }
                @Override
                public void onAnimationCancel(Animator animation) {
                }
            });

        set.start();    
    }

     public void fadeImage(final ImageView front, final ImageView back) {
        front.bringToFront();
        fade(front, 1.0f, 0.0f, 1000, null).start();
        back.setAlpha(1f);
     }

    public void swapImages(ImageView front, ImageView back) {
        ImageView tmp = front;
        mImageFront = back;
        mImageBack = tmp;
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
                    fade(mLabel, 1.0f, 0.0f, 2000, null).start();
                }
                
                @Override
                public void onAnimationCancel(Animator animation) {
                    fade(mLabel, 1.0f, 0.0f, 2000, null).start();
                }
            }).start();
    }
    
    public void showImage(Image image, Bitmap sampled) {

        // At each tick:
        // if back exists, fade back then show and slide front
        // else show and slide front
        // swap front and back 

        if (image != null && sampled != null) {
            current = image;

            Log.d(TAG, "showing image " + image.getTitle());

            if (tick > 0) {
                mImageBack.setImageBitmap(sampled);
                fadeImage(mImageFront, mImageBack);
                burnsImage(mImageBack);
            } else {
                mImageFront.setImageBitmap(sampled);
                burnsImage(mImageFront);
            }

            swapImages(mImageFront, mImageBack);
 
            mLabel.setText(image.getTitle());
            showLabel();

            tick++;

            Tasks.markImageShown(this.context, image);
        }

        mHandler.removeCallbacks(mImageFlipper);
        mHandler.postDelayed(mImageFlipper, interval);
    }
    
    public void openImageSource() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(current.getSourceUrl()));
        startActivity(i);
    }

    public float randomRange(float min, float max) {
        return min + ((max-min) * mRandom.nextFloat());
    }

    public int randomInt(int min, int max) {
        return min + mRandom.nextInt(max-min);
    }
}
