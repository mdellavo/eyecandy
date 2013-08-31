package org.quuux.eyecandy.utils;

import android.graphics.Movie;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

public class MovieRequest extends Request<Movie> {

    final Response.Listener<Movie> mListener;

    public MovieRequest(final String url,
                        final Response.Listener<Movie> listener,
                        final Response.ErrorListener errorListener) {
        super(Method.GET, url, errorListener);
        mListener = listener;
    }

    @Override
    protected Response parseNetworkResponse(final NetworkResponse response) {
        final Movie movie = Movie.decodeByteArray(response.data, 0, response.data.length);
        return Response.success(movie, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    protected void deliverResponse(final Movie response) {
        mListener.onResponse(response);
    }
}
