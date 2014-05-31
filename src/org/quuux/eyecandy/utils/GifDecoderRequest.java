package org.quuux.eyecandy.utils;

import android.graphics.Movie;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.quuux.eyecandy.Log;

import java.io.ByteArrayInputStream;

import pl.droidsonroids.gif.GifDrawable;

public class GifDecoderRequest extends Request<GifDrawable> {

    private static final String TAG = Log.buildTag(GifDecoderRequest.class);
    final Response.Listener<GifDrawable> mListener;

    private static final Object sLock = new Object();

    public GifDecoderRequest(final String url,
                             final Response.Listener<GifDrawable> listener,
                             final Response.ErrorListener errorListener) {
        super(Method.GET, url, errorListener);
        mListener = listener;
    }



    @Override
    protected Response parseNetworkResponse(final NetworkResponse response) {
        Response rv = null;
        final GifDecoder decoder;
        final long t1, t2;
        synchronized (sLock) {
            Log.d(TAG, "decoding gif...");
            t1 = SystemClock.currentThreadTimeMillis();

            try {
                Drawable drawable = new GifDrawable(response.data);
                rv = Response.success(drawable, HttpHeaderParser.parseCacheHeaders(response));
            } catch (Throwable e) {
                Log.e(TAG, "error decoding gif", e);
                rv = Response.error(new VolleyError(e));
            }

            t2 = SystemClock.currentThreadTimeMillis();

        }
        Log.d(TAG, "Movie loaded in %s ms", t2-t1);
        return rv;
    }

    @Override
    protected void deliverResponse(final GifDrawable response) {
        mListener.onResponse(response);
    }
}
