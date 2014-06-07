package org.quuux.eyecandy;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.gson.Gson;
import com.squareup.okhttp.OkHttpClient;

import org.quuux.orm.Connection;
import org.quuux.orm.Entity;
import org.quuux.orm.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ScrapeService extends IntentService {

    private static final String TAG = Log.buildTag(ScrapeService.class);

    private static final long SCRAPE_INTERVAL = 1000 * 60 * 5;

    public static String ACTION_SCRAPE_COMPLETE = "org.quuux.eyecandy.intent.action.SCRAPE_COMPLETE";
    public static String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_TASK_STATUS = "task-status";

    private Session mSession;

    public ScrapeService() {
        super(ScrapeService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mSession = getSession();
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final long t1 = System.currentTimeMillis();

        final Subreddit subreddit = (Subreddit) intent.getSerializableExtra(EXTRA_SUBREDDIT);

        if (subreddit != null) {
            final Subreddit result = getSubreddit(subreddit);
            dispatchScrape(result != null ? result : subreddit);
        } else {
            scrapeAllSubreddits();
        }

        final long t2 = System.currentTimeMillis();
        Log.d(TAG, "onHandleIntent complete in %dms", t2-t1);
    }

    private Session getSession() {
        return EyeCandyDatabase.getSession(this);
    }


    private Subreddit getSubreddit(final String name) {
        try {
            final List<Subreddit> results = (List<Subreddit>) mSession.query(Subreddit.class).filter("subreddit=?", name).first(null).get();
            if (results != null && results.size() > 0)
                return results.get(0);
        } catch (Exception e) {
            Log.e(TAG, "error fetching subreddit %s", e, name);
        }

        return null;
    }

    private Subreddit getSubreddit(final Subreddit subreddit) {
        return getSubreddit(subreddit.getSubreddit());
    }

    private void dispatchScrape(final Subreddit subreddit) {

        final long t1 = System.currentTimeMillis();

        Log.d(TAG, "Starting scrape of %s (page=%s / after=%s)...", subreddit.getSubreddit(), subreddit.getPage(), subreddit.getAfter());

        final Runnable[] tasks = new Runnable[] {
                new SubredditScrapeTask(subreddit),
                new ImgurScrapeTask(subreddit)
        };
        final List<Thread> threads = new ArrayList<Thread>();
        for (final Runnable task : tasks) {
            final Thread thread = new Thread(task);
            thread.start();
            threads.add(thread);
        }

        for (final Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "error joining thread", e);
            }
        }

        touchSubreddit(subreddit, System.currentTimeMillis());

        final long t2 = System.currentTimeMillis();
        Log.d(TAG, "Scrape of %s complete (took %sms)", subreddit.getSubreddit(), t2-t1);

        EyeCandyTracker.get(this).sendEvent("scraper", "scrape", subreddit.getSubreddit());
    }

    private List<Subreddit> getExpiredSubreddits(final long age) {
        try {
            return (List<Subreddit>) mSession.query(Subreddit.class).filter("lastScrape < ?", new String[] {String.valueOf(age)}).all(null).get();
        } catch (Exception e) {
            Log.e(TAG, "error getting subreddits", e);
        }

        return null;
    }

    private void touchSubreddit(final Subreddit subreddit, final long ts) {
        final Connection conn = mSession.getConnection();
        conn.beginTransaction();
        conn.exec(
                "UPDATE subreddits SET lastScrape=? WHERE subreddit=?",
                new String[]{
                        String.valueOf(ts),
                        subreddit.getSubreddit()
                }
        );
        conn.commit();
    }

    private void refreshSubreddit(final Subreddit subreddit) {
        final Connection conn = mSession.getConnection();
        conn.beginTransaction();
        conn.exec(
                "UPDATE subreddits SET lastScrape=?,page=?,after=? WHERE subreddit=?",
                new String[] {
                        String.valueOf(System.currentTimeMillis()),
                        String.valueOf(0),
                        null,
                        subreddit.getSubreddit()
                }
        );
        conn.commit();

        try {
            mSession.query(Image.class).filter("subreddit=?", new String[] {subreddit.getSubreddit()}).delete(null).get();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing images from subreddit", e);
        }
    }

    private void scrapeAllSubreddits() {
        final long age = System.currentTimeMillis() - SCRAPE_INTERVAL;

        final List<Subreddit> subreddits = getExpiredSubreddits(age);
        if (subreddits == null || subreddits.size() == 0) {
            Log.e(TAG, "no expired subreddits to scrape!");
            return;
        }


        for (final Subreddit subreddit : subreddits) {

            Log.d(TAG, "Subreddit %s was last scraped %dms ago, scrapping...",
                    subreddit.getSubreddit(), System.currentTimeMillis() - subreddit.getLastScrape());

            refreshSubreddit(subreddit);
            scrapeSubreddit(this, subreddit);
        }

        EyeCandyTracker.get(this).sendEvent("scraper", "scrape all");
    }

    abstract class ScrapeTask<T> implements Runnable {

        private OkHttpClient mHttpClient = new OkHttpClient();

        abstract String getUrl();
        abstract Class<T> getScrapeClass();
        abstract List<Entity> findImages(final T response);
        abstract void updateSubreddit(final T response);
        protected abstract void onScrapeComplete(final boolean status);

        private String get(final URL url) throws IOException {
            final HttpURLConnection connection = mHttpClient.open(url);
            InputStream in = null;
            try {
                // Read the response.
                in = connection.getInputStream();
                byte[] response = readFully(in);
                return new String(response, "UTF-8");
            } finally {
                if (in != null) in.close();
            }
        }

        byte[] readFully(final InputStream in) throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            for (int count; (count = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }

        private <T> T scrape(final Class<T> klass, final String url) {
            String json;
            try {
                json = get(new URL(url));
            } catch (IOException e) {
                Log.e(TAG, "error fetching %s", url);
                return null;
            }

            final Gson gson = new Gson();
            return gson.fromJson(json, klass);
        }


        @Override
        public void run() {
            final long t1 = System.currentTimeMillis();
            final String url = getUrl();
            final T response = scrape(getScrapeClass(), url);
            if (response == null) {
                Log.d(TAG, "error scraping %s", url);
                onScrapeComplete(false);
                return;
            }

            final List<Entity> images = findImages(response);
            if (images == null || images.size() == 0) {
                Log.d(TAG, "no images found %s", url);
                onScrapeComplete(false);
                return;
            }

            final Session session = getSession();

            // FIXME should check exists of image

            session.add(images);
            updateSubreddit(response);

            boolean status;
            try {
                session.commit().get();
                status = true;
                Log.d(TAG, "added %d images", images.size());
            } catch (Exception e) {
                Log.e(TAG, "error committing scrape", e);
                status = false;
            }

            final long t2 = System.currentTimeMillis();

            Log.d(TAG, "Scraped %s -> %s (took %s)", url, status ? "SUCCESS" : "FAILED" , t2-t1);

            onScrapeComplete(status && images.size() > 0);
        }

        public boolean isImage(final String url) {
            final Uri uri = Uri.parse(url);
            final String path = uri.getPath().toLowerCase();
            return ( path.endsWith(".jpg") ||
                    path.endsWith(".jpeg") ||
                    path.endsWith(".gif") ||
                    path.endsWith(".png") );

        }



        void updateSubreddit(final String sql, final String[] args) {
            final Connection conn = getSession().getConnection();

            conn.beginTransaction();
            conn.exec(sql, args);
            conn.commit();
        }


    }

    abstract class BaseSubredditScrapeTask<T> extends ScrapeTask<T> {
        final Subreddit mSubreddit;

        protected BaseSubredditScrapeTask(final Subreddit subreddit) {
            mSubreddit = subreddit;
        }

        @Override
        protected void onScrapeComplete(final boolean status) {
            onScrapeComplete(mSubreddit, status);
        }

        public void onScrapeComplete(final Subreddit subreddit, final boolean status) {
            final Intent intent = new Intent(ACTION_SCRAPE_COMPLETE);
            intent.putExtra(EXTRA_SUBREDDIT, subreddit);
            intent.putExtra(EXTRA_TASK_STATUS, status);
            sendBroadcast(intent);
        }


    }

    class SubredditScrapeTask extends BaseSubredditScrapeTask<RedditPage> {


        protected SubredditScrapeTask(final Subreddit subreddit) {
            super(subreddit);
        }

        @Override
        String getUrl() {
            final String url;
            if (mSubreddit.getAfter() != null)
                url = String.format("http://reddit.com/r/%s.json?after=%s", mSubreddit.getSubreddit(), mSubreddit.getAfter());
            else
                url = String.format("http://reddit.com/r/%s.json", mSubreddit.getSubreddit());
            return url;
        }


        @Override
        List<Entity> findImages(final RedditPage response) {
            final List<Entity> images = new ArrayList<Entity>();

            for(final RedditListItem i : response.data.children) {
                final boolean isImage = isImage(i.data.url);
                final boolean isImgur = i.data.url.contains("imgur.com");
                if (isImage && !isImgur) {
                    final RedditItem item = i.data;

                    final Image img = new Image();
                    img.setSubreddit(mSubreddit.getSubreddit());
                    img.setUrl(item.url);
                    img.setThumbnailUrl(item.thumbnail);
                    img.setTitle(item.title);
                    img.setCreated(item.created);
                    img.setAnimated(false); // FIXME
                    img.setStatus(Image.Status.NOT_FETCHED);

                    images.add(img);
                }
            }
            return images;
        }

        @Override
        void updateSubreddit(final RedditPage response) {
            updateSubreddit(
                    "UPDATE subreddits SET after=? WHERE subreddit=?",
                    new String[] {
                            response.data.after,
                            mSubreddit.getSubreddit()
                    }
            );
        }

        @Override
        Class getScrapeClass() {
            return RedditPage.class;
        }

    }

    class ImgurScrapeTask extends BaseSubredditScrapeTask<ImgurImageList> {


        protected ImgurScrapeTask(final Subreddit subreddit) {
            super(subreddit);
        }

        @Override
        String getUrl() {
            return String.format("http://imgur.com/r/%s/new/day/page/%d/hit.json?scrolled", mSubreddit.getSubreddit(), mSubreddit.getPage());
        }

        @Override
        Class getScrapeClass() {
            return ImgurImageList.class;
        }

        @Override
        List<Entity> findImages(final ImgurImageList response) {
            final List<Entity> images = new ArrayList<Entity>();
            for (final ImgurImage i : response.data) {

                final Image img = new Image();
                img.setSubreddit(mSubreddit.getSubreddit());
                img.setUrl(i.getUrl());
                img.setThumbnailUrl(i.getThumbnail());
                img.setTitle(i.title);
                img.setCreated(i.created);
                img.setAnimated(i.isAnimated());
                img.setStatus(Image.Status.NOT_FETCHED);
                img.setCreated(i.created);

                images.add(img);
            }
            return images;
        }

        @Override
        void updateSubreddit(final ImgurImageList response) {
            updateSubreddit(
                    "UPDATE subreddits SET page=? WHERE subreddit=?",
                    new String[] {
                            String.valueOf(mSubreddit.getPage() + 1),
                            mSubreddit.getSubreddit()
                    }
            );
        }
    }


    public static void scrapeSubreddit(final Context context, final Subreddit subreddit) {
        final Intent i = new Intent(context, ScrapeService.class);
        i.putExtra(ScrapeService.EXTRA_SUBREDDIT, subreddit);
        context.startService(i);
    }


    static class RedditItem {
        String url;
        String title;
        String thumbnail;
        String subreddit;
        long created;
    }

    static class RedditListItem {
        String kind;
        RedditItem data;
    }

    static class RedditList {
        List<RedditListItem> children;
        String modhash;
        String before, after;

    }

    static class RedditPage {
        String type;
        RedditList data;
    }

    static class ImgurImage {
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

    static class ImgurImageList {
        public List<ImgurImage> data = new ArrayList<ImgurImage>();
    }

}
