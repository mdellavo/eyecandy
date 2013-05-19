package org.quuux.eyecandy;

import android.content.Context;
import android.os.AsyncTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Tasks {

    static final ExecutorService scrapePool = Executors.newFixedThreadPool(4);

    static void scrapeReddit(Context context, String subreddit, ScrapeCompleteListener listener) {
        new ScrapeRedditTask(context, listener).executeOnExecutor(scrapePool, subreddit);
    }

    static void scrapeReddit(Context context, String subreddits[], ScrapeCompleteListener listener) {
        for(String subreddit : subreddits) {
            scrapeReddit(context, subreddit, listener);
        }
    }

    static void nextImage(Context context, NextImageListener listener) {
        new NextImageTask(context, listener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    static void markImageShown(Context context, Image image) {
        new MarkImageShownTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, image);
    }
}
