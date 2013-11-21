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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScrapeService extends IntentService {

    private static final String TAG = Log.buildTag(ScrapeService.class);
    private static final long SCRAPE_INTERVAL = 1000 * 60 * 60 * 2;

    public static String ACTION_SCRAPE_COMPLETE = "org.quuux.eyecandy.intent.action.SCRAPE_COMPLETE";
    public static String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_NUM_SCRAPED = "num-scraped";
    public static final String EXTRA_TASK_COUNT = "task-count";
    public static final String EXTRA_TASK_STATUS = "task-status";

    private RequestQueue mRequestQueue;

    private static int sTaskCount = 0;

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
        final Session session = EyeCandyDatabase.getSession(this);
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

    public void onScrapeComplete(final Subreddit subreddit, final boolean status, final int numScraped) {
        sTaskCount--;

        final Intent intent = new Intent(ACTION_SCRAPE_COMPLETE);
        intent.putExtra(EXTRA_SUBREDDIT, subreddit);
        intent.putExtra(EXTRA_NUM_SCRAPED, numScraped);
        intent.putExtra(EXTRA_TASK_STATUS, status);
        intent.putExtra(EXTRA_TASK_COUNT, sTaskCount);
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

        final Set<String> knownUrls = new HashSet<String>();

        final GsonRequest<ImgurImageList> request = new GsonRequest<ImgurImageList>(
                ImgurImageList.class,
                Request.Method.GET,
                url,
                new Response.Listener<ImgurImageList>() {
                    @Override
                    public void onResponse(final ImgurImageList response) {

                        if (response == null)
                            return;

                        int count = 0;

                        final Session session = EyeCandyDatabase.getSession(ScrapeService.this);

                        for(final ImgurImage i : response.data) {

                            final Image img =  Image.fromImgur(subreddit, i.getUrl(), i.title, i.isAnimated());
                            if (knownUrls.contains(img.getUrl())) {
                                Log.d(TAG, "skipping %s...", img.getUrl());
                                continue;
                            }

                            session.add(img);
                            count++;
                        }

                        subreddit.touch();
                        session.add(subreddit);

                        // Prevent dog piling
                        synchronized (sLock) {
                            session.commit();
                        }

                        onScrapeComplete(subreddit, true, count);

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(final VolleyError error) {
                        Log.d(TAG, "Error scraping %s - %s", url, error.getMessage());
                        onScrapeComplete(subreddit, false, 0);
                    }
                }
        );


        final Session session = EyeCandyDatabase.getSession(this);
        session.query(Image.class).filter("subreddit=?", subreddit.getSubreddit()).all(new QueryListener<Image>() {
            @Override
            public void onResult(final List<Image> images) {

                if (images != null)
                    for(Image i : images)
                        knownUrls.add(i.getUrl());


                mRequestQueue.add(request);
            }
        });
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
