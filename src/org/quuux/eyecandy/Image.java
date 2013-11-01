package org.quuux.eyecandy;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.io.File;

import org.json.JSONObject;
import org.quuux.orm.Column;
import org.quuux.orm.Entity;
import org.quuux.orm.Table;

import java.io.Serializable;
import java.security.MessageDigest;

@Table(name="images")
public class Image implements Entity, Serializable {



    public long getId() {
        return id;
    }

    public enum Source {
        REDDIT,
        IMGUR
    };
    
    public enum Status {
        NOT_FETCHED,
        FETCHED,
        FAILED
    };

    public Image() {}

    @Column(primaryKey = true)
    private long id = -1;

    @Column()
    private Source source;

    @Column(unique = true, nullable = false)
    private String url;

    @Column()
    private String title;

    @Column()
    private Status status;

    @Column()
    private int timesShown;

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

    protected Image(final Source source, final String url, final String title, final boolean animated, final Status status, final int timesShown) {
        this.source = source;
        this.url = url;
        this.title = title;
        this.animated = animated;
        this.status = status;
        this.timesShown = timesShown;
    }

    public static Image fromImgur(final String url, final String title, final boolean animated) {
        return new Image(Source.IMGUR, url, title, animated, Status.NOT_FETCHED, 0);
    }


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
    
    public Source getSource() {
        return source;
    }

    public String getUrl() {
        return url;
    }

    public String getThumbnailUrl() {

        if (url.startsWith("http://imgur.com"))
            return url.replace(".jpg", "t.jpg");

        return url;
    }

    public String getTitle() {
        return title;
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


}

