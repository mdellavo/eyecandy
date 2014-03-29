package org.quuux.eyecandy;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.squareup.okhttp.OkHttpClient;

import org.quuux.eyecandy.utils.GsonRequest;
import org.quuux.eyecandy.utils.OkHttpStack;
import org.quuux.orm.Connection;
import org.quuux.orm.Database;
import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.FlushListener;
import org.quuux.orm.FlushTask;
import org.quuux.orm.QueryListener;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;

import java.net.URL;
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
    private BlockingQueue<Image> mQueue = new LinkedBlockingQueue<Image>();
    private Thread mWriter;

    public Set<Subreddit> mScraping = new HashSet<Subreddit>();

    public ScrapeService() {
        super(ScrapeService.class.getName());
    }

    @Override
    protected void onHandleIntent(final Intent intent) {

        mRequestQueue = EyeCandyVolley.getRequestQueue(this);

        final Subreddit subreddit = (Subreddit) intent.getSerializableExtra(EXTRA_SUBREDDIT);

        if (subreddit != null) {
            mScraping.add(subreddit);

            getSession().query(Subreddit.class).filter("subreddit=?", subreddit.getSubreddit()).first(new FetchListener<Subreddit>() {
                @Override
                public void onResult(final Subreddit result) {
                    dispatchScrape(result != null ? result : subreddit);
                }
            });
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

        if (mScraping.contains(subreddit)) {
            Log.d(TAG, "already scraping %s, aborting!", subreddit.getSubreddit());
            onScrapeComplete(subreddit, false);
            return;
        }

        Log.d(TAG, "%s scraped %s seconds ago - %s",
                subreddit.getSubreddit(), timeSinceLastScrape / 1000, doScrape ? "scraping" : "skipping");

//        if (!doScrape)
//            return;


        scrapeSubreddit(subreddit);
        sTaskCount++;

        scrapeImgur(subreddit);
        sTaskCount++;
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

        mScraping.remove(subreddit);
    }


    private Image buildImage(final Subreddit subreddit, final ImgurImage i) {
        return Image.fromImgur(subreddit, i.getUrl(), i.getThumbnail(), i.title, i.created, i.isAnimated());
    }

    private Image buildImage(final Subreddit subreddit, final RedditItem i) {
        return Image.fromImgur(subreddit, i.url, i.thumbnail, i.title, i.created, false); // FIXME
    }

    private <T> void doScrape(final Class<T> klass, final Subreddit subreddit, final String url, final Response.Listener<T> listener) {

        final GsonRequest<T> request = new GsonRequest<T>(klass, Request.Method.GET, url, listener, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(final VolleyError error) {
                Log.d(TAG, "Error scraping %s - %s", url, error.getMessage());
                onScrapeComplete(subreddit, false);
            }
        });

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
        long created;
    }

    class RedditListItem {
        String kind;
        RedditItem data;
    }

    class RedditList {
        List<RedditListItem> children;
        String modhash;
        String before, after;

    }

    class RedditPage {
        String type;
        RedditList data;
    }

    private void scrapeSubreddit(final Subreddit subreddit) {

        final String url;
        if (subreddit.getAfter() != null)
            url = String.format("http://reddit.com/r/%s.json?after=%s", subreddit.getSubreddit(), subreddit.getAfter());
        else
            url = String.format("http://reddit.com/r/%s.json", subreddit.getSubreddit());

        Log.d(TAG, "scraping %s", url);

        doScrape(RedditPage.class,
                subreddit,
                url,
                new Response.Listener<RedditPage>() {
                    @Override
                    public void onResponse(final RedditPage response) {

                        if (response == null || response.data.children.size() == 0) {
                            Log.d(TAG, "no images found on reddit!");
                            onScrapeComplete(subreddit, false);
                            return;
                        }

                        subreddit.setAfter(response.data.after);

                        final Connection connection = getSession().getConnection();
                        connection.beginTransaction();
                        connection.exec(
                                "UPDATE subreddits SET after=? WHERE subreddit=?",
                                new String[]{subreddit.getAfter(), subreddit.getSubreddit()}
                        );
                        connection.commit();

                        final Session session = getSession();
                        for(final RedditListItem i : response.data.children) {

                            final boolean isImage = isImage(i.data.url);
                            final boolean isImgur = i.data.url.contains("imgur.com");
                            if (isImage && !isImgur) {
                                final Image img = buildImage(subreddit, i.data);
                                session.add(img);
                            }

                        }
                        session.commit(new FlushListener() {
                            @Override
                            public void onFlushed() {
                                Log.d(TAG, "added %d images from reddit(%s)", response.data.children.size(), subreddit.getSubreddit());

                                onScrapeComplete(subreddit, true);
                            }
                        });
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

    private void scrapeImgur(final Subreddit subreddit) {
        final String url = String.format("http://imgur.com/r/%s/new/day/page/%d/hit.json?scrolled", subreddit.getSubreddit(), subreddit.getPage());

        Log.d(TAG, "scraping %s", url);

        doScrape(ImgurImageList.class,
                subreddit,
                url,
                new Response.Listener<ImgurImageList>() {
                    @Override
                    public void onResponse(final ImgurImageList response) {

                        if (response == null || response.data.size() == 0) {
                            Log.d(TAG, "no images found on imgur!");
                            onScrapeComplete(subreddit, false);
                            return;
                        }

                        subreddit.setPage(subreddit.getPage() + 1);

                        final Connection connection = getSession().getConnection();
                        connection.beginTransaction();
                        connection.exec("UPDATE subreddits SET page=? WHERE subreddit=?", new String[]{String.valueOf(subreddit.getPage()), subreddit.getSubreddit()});
                        connection.commit();

                        final Session session = getSession();

                        for (final ImgurImage i : response.data) {
                            final Image img = buildImage(subreddit, i);
                            session.add(img);
                        }

                        session.commit(new FlushListener() {
                            @Override
                            public void onFlushed() {
                                Log.d(TAG, "added %d images from imgur(%s)", response.data.size(), subreddit.getSubreddit());
                                onScrapeComplete(subreddit, true);
                            }
                        });

                    }
                }
        );
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
        long created;

        public String getUrl() {
            return String.format("http://i.imgur.com/%s%s", hash, ext);
        }
        public String getThumbnail() {return String.format("http://i.imgur.com/%st%s", hash, ext);}

        public boolean isAnimated() {
            return "1".equals(animated);
        }
    }

    private static class ImgurImageList {
        public List<ImgurImage> data = new ArrayList<ImgurImage>();
    }

}
