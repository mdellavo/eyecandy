package org.quuux.eyecandy.utils;

import android.content.Context;

import com.android.volley.toolbox.HurlStack;
import com.squareup.okhttp.HttpResponseCache;
import com.squareup.okhttp.OkHttpClient;
import org.quuux.eyecandy.Log;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * An {@link com.android.volley.toolbox.HttpStack HttpStack} implementation which
 * uses OkHttp as its transport.
 */
public class OkHttpStack extends HurlStack {
    private static final String TAG = Log.buildTag(OkHttpStack.class);
    private static final long CACHE_SIZE = 50 * 1024 * 1024;

    private final OkHttpClient client;

    public OkHttpStack(final Context context) {
        this(context, new OkHttpClient());
    }

    public OkHttpStack(final Context context, final OkHttpClient client) {
        if (client == null) {
            throw new NullPointerException("Client must not be null.");
        }
        this.client = client;
    }

    @Override protected HttpURLConnection createConnection(URL url) throws IOException {
        return client.open(url);
    }
}