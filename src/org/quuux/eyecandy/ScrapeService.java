package org.quuux.eyecandy;

import android.app.IntentService;
import android.content.Intent;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import org.quuux.eyecandy.utils.GsonRequest;
import org.quuux.eyecandy.utils.OkHttpStack;
import org.quuux.orm.Database;
import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.QueryListener;
import org.quuux.orm.Session;

import java.util.ArrayList;
import java.util.List;

public class ScrapeService extends IntentService {

    private static final String TAG = Log.buildTag(ScrapeService.class);
    private static final long SCRAPE_INTERVAL =  1000 * 60 * 60 * 2;

    public static String ACTION_SCRAPE_COMPLETE = "org.quuux.eyecandy.intent.action.SCRAPE_COMPLETE";
    public static String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_NUM_SCRAPED = "num-scraped";

    private RequestQueue mRequestQueue;

    private static int sTaskCount = 0;
    private EyeCandyDatabase mDatabase;

    private static final Object sLock = new Object();

    public ScrapeService() {
        super(ScrapeService.class.getName());
    }

    @Override
    protected void onHandleIntent(final Intent intent) {

        mRequestQueue = EyeCandyVolley.getRequestQueue(this);

        final Subreddit subreddit = (Subreddit) intent.getSerializableExtra(EXTRA_SUBREDDIT);
        if (subreddit != null) {
            scrapeImgur(subreddit);
        } else {
            scrapeAllSubreddits();
        }
    }

    private void scrapeAllSubreddits() {
        mDatabase = EyeCandyDatabase.getInstance(this);

        final Session session = mDatabase.createSession();
        session.query(Subreddit.class).all(new QueryListener<Subreddit>() {
            @Override
            public void onResult(final List<Subreddit> subreddits) {

                if (subreddits == null) {
                    Log.e(TAG, "no subreddits to scrape!");
                    return;
                }

                for (Subreddit subreddit : subreddits) {
                    scrapeImgur(subreddit);
                }

            }
        });

    }

    public void onScrapeComplete(final Subreddit subreddit, final int numScraped) {
        sTaskCount--;

        //Log.d(TAG, "scrape complete: %d", numScraped);

        final Intent intent = new Intent(ACTION_SCRAPE_COMPLETE);
        intent.putExtra(EXTRA_SUBREDDIT, subreddit);
        intent.putExtra(EXTRA_NUM_SCRAPED, numScraped);
        sendBroadcast(intent);

        if (sTaskCount == 0) {
            stopSelf();
        }
    }


    private void scrapeImgur(final Subreddit subreddit) {

        final long timeSinceLastScrape = System.currentTimeMillis() - subreddit.getLastScrape();
        final boolean doScrape = timeSinceLastScrape > SCRAPE_INTERVAL;

        Log.d(TAG, "%s scraped %s seconds ago - %s", subreddit, timeSinceLastScrape / 1000, doScrape ? "scraping" : "skipping");

        if (!doScrape)
            return;

        sTaskCount++;

        final String url = String.format("http://imgur.com/r/%s.json", subreddit.getSubreddit());

        final GsonRequest<ImgurImageList> request = new GsonRequest<ImgurImageList>(
                ImgurImageList.class,
                Request.Method.GET,
                url,
                new Response.Listener<ImgurImageList>() {
                    @Override
                    public void onResponse(final ImgurImageList response) {

                        synchronized (sLock) {
                            final Session session = mDatabase.createSession();

                            for(final ImgurImage i : response.data) {
                                final Image img =  Image.fromImgur(subreddit, i.getUrl(), i.title, i.isAnimated());
                                session.add(img);
                            }

                            subreddit.touch();
                            session.add(subreddit);

                            session.commit();
                        }

                        onScrapeComplete(subreddit, response.data.size());

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(final VolleyError error) {
                        Log.d(TAG, "Error scraping %s - %s", url, error.getMessage());
                    }
                }
        ) {
            @Override
            public Priority getPriority() {
                return Priority.LOW;
            }
        };
        mRequestQueue.add(request);
    }

    private static class ImgurImage {
        String hash;
        String title;
        String mimetype;
        String ext;
        int width;
        int height;
        int size;
        String animated;

        public String getUrl() {
            return String.format("http://imgur.com/%s%s", hash, ext);
        }


        public boolean isAnimated() {
            return "1".equals(animated);
        }
    }

    private static class ImgurImageList {
        public List<ImgurImage> data = new ArrayList<ImgurImage>();
    }
}
