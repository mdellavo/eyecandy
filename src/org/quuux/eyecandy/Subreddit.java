package org.quuux.eyecandy;

import org.quuux.eyecandy.utils.RandomGenerator;
import org.quuux.orm.Column;
import org.quuux.orm.Entity;
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

}
