package org.quuux.eyecandy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ViewSwitcher;
import android.widget.ImageSwitcher;
import android.view.animation.AnimationUtils;

public class MainActivity
    extends Activity
    implements View.OnClickListener, ViewSwitcher.ViewFactory, FetchCompleteListener, ScrapeCompleteListener, NextImageListener {

    private static final String TAG = "MainActivity";

    private static final String src = "http://www.reddit.com/r/earthporn+villageporn+cityporn+spaceporn+waterporn+abandonedporn+animalporn+humanporn+botanicalporn+adrenalineporn+destructionporn+movieposterporn+albumartporn+machineporn+newsporn+geekporn+bookporn+mapporn+adporn+designporn+roomporn+militaryporn+historyporn+quotesporn+skyporn+fireporn+infrastructureporn+macroporn+instrumentporn+climbingporn+architectureporn+artporn+cemeteryporn+carporn+fractalporn+exposureporn+gunporn+culinaryporn+dessertporn+agricultureporn+boatporn+geologyporn+futureporn+winterporn+foodporn.json?limit=500";

    private static final int INTERVAL = 5 * 1000;

    protected ImageSwitcher mImage;
    protected Handler mImageSwitcher;

    Runnable mImageFlipper = new Runnable() {
            @Override
            public void run() {
                flipImage();
            }
        };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mImageSwitcher = new Handler();

        mImage = (ImageSwitcher)findViewById(R.id.image);
        mImage.setFactory(this);
        mImage.setOnClickListener(this);
        mImage.setInAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        mImage.setOutAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));

        Tasks.scrapeReddit(this, src, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        startFlipping();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopFlipping();
    }
    
    public void onClick(View v) {
        flipImage();
    }

    @Override
    public View makeView() {
        ImageView view  = new ImageView(this);
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
        flipImage();
    }
    public void nextImage(Image image) {
        Log.d(TAG, "flipping to " + image + ", shown " + image.getTimesShown() + " times");
            
        if (image.isCached(this)) {
            showImage(image);
        } else {
            fetchImage(image);
        }
    }
 
    public void startFlipping() {
        mImageSwitcher.postDelayed(mImageFlipper, INTERVAL);
    }

    public void stopFlipping() {
        mImageSwitcher.removeCallbacks(mImageFlipper);
    }

    public void flipImage() {
        Tasks.nextImage(this, this);
    }

    public void fetchImage(Image image) {
        Tasks.fetchImage(this, image, this);
    }

    public void showImage(Image image) {
        mImage.setImageURI(image.getCachedImageUri(this));
        Tasks.markImageShown(this, image);

        mImageSwitcher.removeCallbacks(mImageFlipper);
        mImageSwitcher.postDelayed(mImageFlipper, INTERVAL);
    }
}
