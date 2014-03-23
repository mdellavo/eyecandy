package org.quuux.eyecandy;

import android.content.Context;

import org.quuux.eyecandy.utils.RandomGenerator;
import org.quuux.orm.Column;
import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.FetchTask;
import org.quuux.orm.FlushListener;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;
import org.quuux.orm.Table;

import java.io.Serializable;


@Table(name="subreddits")
public class Subreddit implements Entity, Serializable {

    @Column(primaryKey = true)
    private long id = -1;

    @Column(nullable=false)
    private long lastScrape;

    @Column(unique = true, nullable = false)
    private String subreddit;

    public Subreddit() {}

    public Subreddit(final String subreddit) {
        this.subreddit = subreddit;
    }

    public String getSubreddit() {
        return subreddit;
    }

    public long getLastScrape() {
        return lastScrape;
    }

    public void setLastScrape(final long ts) {
        lastScrape = ts;
    }

    public void touch() {
        setLastScrape(System.currentTimeMillis() + RandomGenerator.get().randomInt(0, 1000 * 60 * 5));
    }

    public static void add(final Context context, final String subreddit, final FetchListener<Subreddit> listener) {
        final Session session = EyeCandyDatabase.getSession(context);

        session.query(Subreddit.class).filter("subreddit=?", subreddit).first(new FetchListener<Subreddit>() {
            @Override
            public void onResult(Subreddit result) {

                final Subreddit s;
                if (result == null) {
                    s = new Subreddit(subreddit);
                    session.add(s);
                } else {
                    s = result;
                }

                s.setLastScrape(0);

                session.commit(new FlushListener() {
                    @Override
                    public void onFlushed() {
                        refresh(context, s);

                        if (listener != null)
                            listener.onResult(s);
                    }
                });
            }
        });

    }

    public static void remove(final Context context, final String subreddit, final FlushListener listener) {
        final Session session = EyeCandyDatabase.getSession(context);
        session.query(Subreddit.class).filter("subreddit=?", subreddit).delete(null);
        session.query(Image.class).filter("subreddit=?", subreddit).delete(null);
        session.commit(new FlushListener() {
            @Override
            public void onFlushed() {
                if (listener != null)
                    listener.onFlushed();
            }
        });
    }


    public static void refresh(final Context context, final Subreddit subreddit) {
        subreddit.setLastScrape(0);
        ScrapeService.scrapeSubreddit(context, subreddit);

    }
}
