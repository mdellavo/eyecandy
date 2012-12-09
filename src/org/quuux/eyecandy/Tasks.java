package org.quuux.eyecandy;

import android.content.Context;
import android.net.Uri;

public class Tasks {

    static void scrapeReddit(Context context, String subreddit, ScrapeCompleteListener listener) {
        new ScrapeRedditTask(context, listener).execute(subreddit);
    }

    static void scrapeReddit(Context context, String subreddits[], ScrapeCompleteListener listener) {
        for(String subreddit : subreddits) {
            scrapeReddit(context, subreddit, listener);
        }
    }

    static void fetchImage(Context context, Image image, FetchCompleteListener listener) {
        new FetchImageTask(context, listener).execute(image);
    }

    static void sampleImage(Context context, Image image, int width, int height, SampleCompleteListener listener) {
        new SampleImageTask(context, width, height, listener).execute(image);
    }

    static void nextImage(Context context, NextImageListener listener) {
        new NextImageTask(context, listener).execute();
    }

    static void markImageShown(Context context, Image image) {
        new MarkImageShownTask(context).execute(image);
    }
}
