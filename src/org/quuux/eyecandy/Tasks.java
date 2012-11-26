package org.quuux.eyecandy;

import android.content.Context;
import android.net.Uri;

public class Tasks {

    static void scrapeReddit(Context context, String subreddits[], ScrapeCompleteListener listener) {
        new ScrapeRedditTask(context, listener).execute(subreddits);
    }

    static void fetchImage(Context context, Image image, FetchCompleteListener listener) {
        new FetchImageTask(context, listener).execute(image);
    }

    static void nextImage(Context context, NextImageListener listener) {
        new NextImageTask(context, listener).execute();
    }
    
    static void markImageShown(Context context, Image image) {
        new MarkImageShownTask(context).execute(image);
    }
}
