package org.quuux.eyecandy;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;


public class MainActivity extends Activity
{
    private static final String TAG = "MainActivity";

    private static final String src = "http://www.reddit.com/r/earthporn+villageporn+cityporn+spaceporn+waterporn+abandonedporn+animalporn+humanporn+botanicalporn+adrenalineporn+destructionporn+movieposterporn+albumartporn+machineporn+newsporn+geekporn+bookporn+mapporn+adporn+designporn+roomporn+militaryporn+historyporn+quotesporn+skyporn+fireporn+infrastructureporn+macroporn+instrumentporn+climbingporn+architectureporn+artporn+cemeteryporn+carporn+fractalporn+exposureporn+gunporn+culinaryporn+dessertporn+agricultureporn+boatporn+geologyporn+futureporn+winterporn+foodporn.json?limit=500";

 
    class FetchListener implements  FetchCompleteListener {
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
    
    ImageView mImage;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mImage = (ImageView)findViewById(R.id.image);
        mImage.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    flipImage();
                }
            });

        Tasks.scrapeReddit(this, src, new ScrapeListener());
    }

    public void flipImage() {
        DatabaseHelper db = new DatabaseHelper(this);
        Image image = db.nextImage();
        Log.d(TAG, "flipping to " + image);
        Tasks.fetchImage(this, image, new FetchListener());
    }

    public void showImage(Image image) {
        mImage.setImageURI(image.getCachedImageUri(this));
        Tasks.markImageShown(this, image);
    }
}
