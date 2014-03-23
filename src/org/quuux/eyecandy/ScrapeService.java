package org.quuux.eyecandy;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

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
import org.quuux.orm.FlushListener;
import org.quuux.orm.FlushTask;
import org.quuux.orm.QueryListener;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ScrapeService extends IntentService {

    private static final String TAG = Log.buildTag(ScrapeService.class);
    private static final long SCRAPE_INTERVAL = 1000 * 60 * 60 * 2;

    public static String ACTION_SCRAPE_COMPLETE = "org.quuux.eyecandy.intent.action.SCRAPE_COMPLETE";
    public static String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_NUM_SCRAPED = "num-scraped";
    public static final String EXTRA_TASK_COUNT = "task-count";
    public static final String EXTRA_TASK_STATUS = "task-status";

    private static int sTaskCount = 0;

    private RequestQueue mRequestQueue;
    private BlockingQueue<Object> mQueue = new LinkedBlockingQueue<Object>();
    private Thread mWriter;

    public ScrapeService() {
        super(ScrapeService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mWriter = new Thread(new Writer(mQueue));
        mWriter.setDaemon(true);
        mWriter.setPriority(Thread.MIN_PRIORITY);
        mWriter.start();
    }

    @Override
    protected void onHandleIntent(final Intent intent) {

        mRequestQueue = EyeCandyVolley.getRequestQueue(this);

        final Subreddit subreddit = (Subreddit) intent.getSerializableExtra(EXTRA_SUBREDDIT);

        if (subreddit != null) {
            dispatchScrape(subreddit);
        } else {
            scrapeAllSubreddits();
        }
    }

    private Session getSession() {
        return EyeCandyDatabase.getSession(this);
    }

    private void dispatchScrape(final Subreddit subreddit) {

        final long timeSinceLastScrape = System.currentTimeMillis() - subreddit.getLastScrape();
        final boolean doScrape = SCRAPE_INTERVAL == 0 || timeSinceLastScrape > SCRAPE_INTERVAL;

        Log.d(TAG, "%s scraped %s seconds ago - %s",
                subreddit.getSubreddit(), timeSinceLastScrape / 1000, doScrape ? "scraping" : "skipping");

        if (!doScrape)
            return;

        scrapeSubreddit(subreddit);
        sTaskCount++;

        for (int i=0; i<4; i++) {
            scrapeImgur(subreddit, i);
            sTaskCount++;
        }
    }

    private void scrapeAllSubreddits() {
        getSession().query(Subreddit.class).all(new QueryListener<Subreddit>() {
            @Override
            public void onResult(final List<Subreddit> subreddits) {

                if (subreddits == null) {
                    Log.e(TAG, "no subreddits to scrape!");
                    return;
                }

                for (final Subreddit subreddit : subreddits) {
                    dispatchScrape(subreddit);
                }

            }
        });

    }

    public void onScrapeComplete(final Subreddit subreddit, final boolean status) {
        sTaskCount--;

        final Intent intent = new Intent(ACTION_SCRAPE_COMPLETE);
        intent.putExtra(EXTRA_SUBREDDIT, subreddit);
        intent.putExtra(EXTRA_TASK_STATUS, status);
        intent.putExtra(EXTRA_TASK_COUNT, sTaskCount);
        sendBroadcast(intent);

        if (sTaskCount == 0) {
            stopSelf();
        }
    }

    private void write(final Image i) {
        try {
            mQueue.put(i);
        } catch (final InterruptedException e) {
            Log.e(TAG, "error putting image on queue", e);
        }
    }


    private void touchSubreddit(final Subreddit subreddit) {
        try {
            mQueue.put(subreddit);
        } catch (InterruptedException e) {
            Log.e(TAG, "error touching subreddit", e);
        }
    }

    private Image buildImage(final Subreddit subreddit, final ImgurImage i) {
        return Image.fromImgur(subreddit, i.getUrl(), i.title, i.isAnimated());
    }

    private Image buildImage(final Subreddit subreddit, final RedditItem i) {
        return Image.fromImgur(subreddit, i.url, i.title, false); // FIXME
    }

    private <T> void doScrape(final Class<T> klass, final Subreddit subreddit, final String url, final Response.Listener<T> listener) {
        final GsonRequest<T> request = new GsonRequest<T>(klass, Request.Method.GET, url, listener, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(final VolleyError error) {
                Log.d(TAG, "Error scraping %s - %s", url, error.getMessage());
                onScrapeComplete(subreddit, false);
            }
        }) {

            @Override
            public Priority getPriority() {
                return Priority.LOW;
            }
        };
        mRequestQueue.add(request);
    }

    public static void scrapeSubreddit(final Context context, final Subreddit subreddit) {
        final Intent i = new Intent(context, ScrapeService.class);
        i.putExtra(ScrapeService.EXTRA_SUBREDDIT, subreddit);
        context.startService(i);
    }

    class RedditItem {
        String url;
        String title;
        String thumbnail;
        String subreddit;
    }

    class RedditListItem {
        String kind;
        RedditItem data;
    }

    class RedditList {
        List<RedditListItem> children;
        String modhash;
        String before, adter;

    }

    class RedditPage {
        String type;
        RedditList data;
    }

    private void scrapeSubreddit(final Subreddit subreddit) {
        final String url = String.format("http://reddit.com/r/%s.json", subreddit.getSubreddit());

        doScrape(RedditPage.class,
                subreddit,
                url,
                new Response.Listener<RedditPage>() {
                    @Override
                    public void onResponse(final RedditPage response) {

                        if (response == null)
                            return;

                        for(final RedditListItem i : response.data.children) {

                            final boolean isImage = isImage(i.data.url);
                            final boolean isImgur = i.data.url.contains("imgur.com");
                            if (isImage && !isImgur) {
                                final Image img = buildImage(subreddit, i.data);
                                write(img);
                            }

                        }

                        touchSubreddit(subreddit);
                    }
                }
        );

    }

    private boolean isImage(final String url) {
        final Uri uri = Uri.parse(url);
        final String path = uri.getPath().toLowerCase();
        return ( path.endsWith(".jpg") ||
                 path.endsWith(".jpeg") ||
                 path.endsWith(".gif") ||
                 path.endsWith(".png") );

    }

    private void scrapeImgur(final Subreddit subreddit, final int page) {
        final String url = String.format("http://imgur.com/r/%s/new/day/page/%d/hit.json", subreddit.getSubreddit(), page);

        doScrape(ImgurImageList.class,
                subreddit,
                url,
                new Response.Listener<ImgurImageList>() {
                    @Override
                    public void onResponse(final ImgurImageList response) {

                        if (response == null)
                            return;

                        for (final ImgurImage i : response.data) {
                            final Image img = buildImage(subreddit, i);
                            write(img);
                        }

                        touchSubreddit(subreddit);
                    }
                }
        );
    }

    private void scrapeImgur(final Subreddit subreddit) {
        scrapeImgur(subreddit, 0);
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

    class Writer implements Runnable {

        private Session mSession = getSession();
        private BlockingQueue<Object> mQueue;

        public Writer(final BlockingQueue<Object> queue) {
            mQueue = queue;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final Object o = mQueue.take();

                    if (o instanceof Subreddit)
                        consume((Subreddit)o);
                    else if (o instanceof Image)
                        consume((Image)o);

                } catch (Exception e) {
                    Log.e(TAG, "error processing image from queue - ", e);
                }
            }
        }

        private void consume(final Subreddit subreddit) throws ExecutionException, InterruptedException {

            Log.d(TAG, "Touching subreddit %s", subreddit);

            subreddit.touch();
            mSession.add(subreddit);
            mSession.commit().get();
            onScrapeComplete(subreddit, true);

        }

        private void consume(final Image image) throws ExecutionException, InterruptedException {
            final Long count = (Long) mSession.query(Image.class).filter("images.url = ?", image.getUrl()).count(null).get();
            if (count.longValue() == 0) {
                insertImage(image);
            }
        }

        private void insertImage(final Image i) {
            mSession.add(i);
            try {
                mSession.commit().get();
            } catch (final Exception e) {
                Log.e(TAG, "error inserting image %s", i);
            }
        }
    }

}
