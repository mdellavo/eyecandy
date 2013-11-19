package org.quuux.eyecandy.utils;

import android.graphics.Movie;
import android.os.SystemClock;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import org.quuux.eyecandy.Log;

public class MovieRequest extends Request<Movie> {

    private static final String TAG = Log.buildTag(MovieRequest.class);
    final Response.Listener<Movie> mListener;

    private static final Object sLock = new Object();

    public MovieRequest(final String url,
                        final Response.Listener<Movie> listener,
                        final Response.ErrorListener errorListener) {
        super(Method.GET, url, errorListener);
        mListener = listener;
    }

    @Override
    protected Response parseNetworkResponse(final NetworkResponse response) {
        final Movie movie;
        final long t1, t2;
        synchronized (sLock) {
            Log.d(TAG, "decoding movie...");
            t1 = SystemClock.currentThreadTimeMillis();
            movie = Movie.decodeByteArray(response.data, 0, response.data.length);
            t2 = SystemClock.currentThreadTimeMillis();
        }
        Log.d(TAG, "Movie loaded in %s ms", t2-t1);
        return Response.success(movie, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    protected void deliverResponse(final Movie response) {
        mListener.onResponse(response);
    }
}
