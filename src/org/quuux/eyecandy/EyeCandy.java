package org.quuux.eyecandy;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.ViewSwitcher;
import android.widget.ImageSwitcher;
import android.view.animation.AnimationUtils;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;

public class EyeCandy

    implements View.OnTouchListener, ViewSwitcher.ViewFactory, FetchCompleteListener, ScrapeCompleteListener, NextImageListener {
 
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

    protected ImageSwitcher mImage;
    protected Handler mHandler;
    protected Context context;
    protected int interval;
    
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
                return super.onDoubleTap(e);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) 
            {
                Log.d(TAG, "fling: e1=" + e1 +", e2=" + e2 + ", velocityX=" + velocityX + "velocityY=" + velocityY);
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
                flipImage();
                return true;
            }
        };

    GestureDetector mGestureDetector;

    public EyeCandy(Context context, int interval) {
        this.context = context;
        this.interval = interval;

        mGestureDetector = new GestureDetector(this.context, mGestureListener, mHandler);
    }
    
    public void attach(ImageSwitcher image) {
        mHandler = new Handler();
        
        mImage = image;
        mImage.setClickable(true);
        mImage.setOnTouchListener(this);
        
        mImage.setFactory(this);
        mImage.setInAnimation(AnimationUtils.loadAnimation(this.context, android.R.anim.fade_in));
        mImage.setOutAnimation(AnimationUtils.loadAnimation(this.context, android.R.anim.fade_out));

        flipImage();
        Tasks.scrapeReddit(this.context, SUBREDDITS, this);
    }

    public boolean onTouch(View view, MotionEvent event)
    {
        return mGestureDetector.onTouchEvent(event);
    }

    @Override
    public View makeView() {
        ImageView view  = new ImageView(this.context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setLayoutParams(new ImageSwitcher.LayoutParams(ImageSwitcher.LayoutParams.FILL_PARENT, ImageSwitcher.LayoutParams.FILL_PARENT));
        return view;
    }

    public void onFetchComplete(Image image) {
        Log.d(TAG, "fetch complete " + image);         
        showImage(image);
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
            showImage(image);
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

    public void showImage(Image image) {
        mImage.setImageURI(image.getCachedImageUri(this.context));
        Tasks.markImageShown(this.context, image);

        mHandler.removeCallbacks(mImageFlipper);
        mHandler.postDelayed(mImageFlipper, interval);
    }
}
