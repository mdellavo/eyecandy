package org.quuux.eyecandy;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;

import org.json.JSONObject;
import org.quuux.orm.Column;
import org.quuux.orm.Entity;
import org.quuux.orm.Table;

import java.io.Serializable;
import java.security.MessageDigest;
import java.util.regex.Pattern;

@Table(name="images")
public class Image implements Entity, Serializable {

    public enum Status {
        NOT_FETCHED,
        FETCHED,
        FAILED
    };

    public enum Thumbnail {
        SMALL,
        MEDIUM,
        LARGE,
        HUGE
    };

    public Image() {}

    @Column(primaryKey = true)
    private long id = -1;

    @Column(unique = true, nullable = false)
    private String url;

    @Column()
    private long created;

    @Column()
    private String title;

    @Column()
    private Status status;

    @Column()
    private int timesShown;

    @Column()
    private long lastShown;

    @Column()
    private int width;

    @Column()
    private int height;

    @Column()
    private int size;

    @Column()
    private String mimeType;

    @Column()
    private boolean animated;

    @Column
    private String thumbnailUrl;

    @Column()
    private String subreddit;

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        return (o != null && o instanceof Image && ((Image) o).getUrl().equals(url));
    }

    @Override
    public String toString() {
        return "Image(" + url + ")";
    }

    public long getId() {
        return id;
    }

    public String getSubreddit() {
        return subreddit;
    }

    public String getUrl() {
        return url.replace("http://imgur.com", "http://i.imgur.com");
    }

    public String getThumbnailUrl() {
        return !TextUtils.isEmpty(thumbnailUrl) ? thumbnailUrl : null;
    }

    public boolean isImgur() {
        return getUrl().startsWith("http://i.imgur.com");
    }

    public String getImgurThumbnailUrl(Thumbnail thumbnail) {

        if (!isImgur())
            throw new IllegalArgumentException("not an imgur url");

        String ext = null;

        switch (thumbnail) {
            case SMALL:
                ext = "t";
                break;

            case MEDIUM:
                ext = "m";
                break;

            case LARGE:
                ext = "l";
                break;

            case HUGE:
                ext = "h";
                break;

            default:
                throw new IllegalArgumentException("unknown thumbnail: " + thumbnail);
        }

        return getThumbnailUrl().replace("t.", ext + ".");
    }

    public String getTitle() {
        return title.replaceAll("\\[[^\\]]+\\]", "");
    }

    public Status getStatus() {
        return status;
    }

    public int getTimesShown() {
        return timesShown;
    }

    public boolean isAnimated() {
        return animated;
    }

    public void stamp() {
        lastShown = System.currentTimeMillis();
        timesShown++;
    }

    private long getCreated() {
        return created;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public void setCreated(final long created) {
        this.created = created;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    public void setTimesShown(final int timesShown) {
        this.timesShown = timesShown;
    }

    public void setLastShown(final long lastShown) {
        this.lastShown = lastShown;
    }

    public void setWidth(final int width) {
        this.width = width;
    }

    public void setHeight(final int height) {
        this.height = height;
    }

    public void setSize(final int size) {
        this.size = size;
    }

    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }

    public void setAnimated(final boolean animated) {
        this.animated = animated;
    }

    public void setThumbnailUrl(final String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public void setSubreddit(final String subreddit) {
        this.subreddit = subreddit;
    }
}

