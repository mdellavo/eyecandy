package org.quuux.eyecandy.utils;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson;

public class GsonRequest<T> extends Request<T> {
    private final Gson mGson = new Gson();
    private final Class<T> mClass;
    private final Response.Listener<T> mListener;

    public GsonRequest(final Class<T> klass,
                       final int method,
                       final String url,
                       final Response.Listener<T> listener,
                       final Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        mClass = klass;
        mListener = listener;
    }

    @Override
    protected Response<T> parseNetworkResponse(final NetworkResponse response) {
        final String data = new String(response.data);
        return Response.success(
                mGson.fromJson(data, mClass),
                HttpHeaderParser.parseCacheHeaders(response)
        );
    }

    @Override
    protected void deliverResponse(T response) {
        mListener.onResponse(response);
    }
}
