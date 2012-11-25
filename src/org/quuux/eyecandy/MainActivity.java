package org.quuux.eyecandy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;


public class MainActivity extends Activity implements View.OnClickListener
{
    private static final String TAG = "MainActivity";

    private static final String src = "http://www.reddit.com/r/earthporn+villageporn+cityporn+spaceporn+waterporn+abandonedporn+animalporn+humanporn+botanicalporn+adrenalineporn+destructionporn+movieposterporn+albumartporn+machineporn+newsporn+geekporn+bookporn+mapporn+adporn+designporn+roomporn+militaryporn+historyporn+quotesporn+skyporn+fireporn+infrastructureporn+macroporn+instrumentporn+climbingporn+architectureporn+artporn+cemeteryporn+carporn+fractalporn+exposureporn+gunporn+culinaryporn+dessertporn+agricultureporn+boatporn+geologyporn+futureporn+winterporn+foodporn.json?limit=500";

    private static final int INTERVAL = 5 * 1000;

    class FetchListener implements FetchCompleteListener {
        public void onFetchComplete(Image image) {
            Log.d(TAG, "fetch complete " + image);         
            showImage(image);
        }
    }

    class ScrapeListener implements ScrapeCompleteListener {
        public void onScrapeComplete(int numScraped) {
            Log.d(TAG, "scrape complete " + numScraped);
            flipImage();
        }
    }
    
    class NextListener implements NextImageListener {
        public void nextImage(Image image) {
            Log.d(TAG, "flipping to " + image);
            fetchImage(image);
        }
    }

    protected ImageView mImage;
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

        mImage = (ImageView)findViewById(R.id.image);
        mImage.setOnClickListener(this);

        Tasks.scrapeReddit(this, src, new ScrapeListener());
    }

    public void onClick(View v) {
        flipImage();
    }

    public void flipImage() {
        Tasks.nextImage(this, new NextListener());
    }

    public void fetchImage(Image image) {
        Tasks.fetchImage(this, image, new FetchListener());
    }

    public void showImage(Image image) {
        mImage.setImageURI(image.getCachedImageUri(this));
        Tasks.markImageShown(this, image);
        mImageSwitcher.removeCallbacks(mImageFlipper);
        mImageSwitcher.postDelayed(mImageFlipper, INTERVAL);
    }
}
