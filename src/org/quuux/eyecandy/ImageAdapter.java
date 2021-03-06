package org.quuux.eyecandy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Movie;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;

import org.quuux.eyecandy.utils.GifDecoder;
import org.quuux.eyecandy.utils.GifDecoderRequest;
import org.quuux.orm.QueryListener;
import org.quuux.orm.Session;

import java.util.*;

import pl.droidsonroids.gif.GifDrawable;


public class ImageAdapter {

    private static final String TAG = Log.buildTag(ImageAdapter.class);

    private static final int PRECACHE_SIZE = 3;
    private static final int HISTORY_SIZE = 4;
    private static final int PAGE_SIZE = 10;

    private final Context mContext;
    private final EyeCandyDatabase mDatabase;
    private final RequestQueue mRequestQueue;

    private int mMaxWidth = 1024, mMaxHeight = 1024;
    private boolean mLoading = false;
    private List<Image> mImages = new LinkedList<Image>();
    private Map<Image, Object> mBitmaps = new HashMap<Image, Object>();

    private ImageLoadedListener mListener = null;

    public ImageAdapter(final Context context, final RequestQueue requestQueue) {
        mContext = context;
        mDatabase = EyeCandyDatabase.getInstance(context);
        mRequestQueue = requestQueue;
    }

    public void setMaxBitmapSize(final int width, final int height) {
        mMaxWidth = width;
        mMaxHeight = height;
    }

    public void nextImage(final ImageLoadedListener listener) {

        if (mLoading)
            return;

        mLoading = true;
        mListener = listener;

        Log.d(TAG, "next image...");

        if (mImages.size() > 0) {
            fetchImage();
        } else {
            Log.d(TAG, "no images to load, deferring...");
            fillQueue();
        }
    }

    private void deliverResponse(final Image image, final Object object) {
        Log.d(TAG, "delivering %s : %s", image, object);

        if (mListener != null) {
            mImages.remove(image);
            mBitmaps.remove(image);
            mListener.onImageLoaded(image, object);
            mListener = null;
            mLoading = false;
        }
    }

    // yeah...
    public void previousImage(final ImageLoadedListener listener) {
        listener.onImageLoaded(null, null);
    }

    private Image popImage() {
        return mImages.remove(0);
    }

    public void fillQueue() {
        final Session session = mDatabase.createSession();

        if (mImages.size() > 0)
            return;

        Log.d(TAG, "filling queue");


        // FIXME factor out query so we can subclass for different ordering, etc
        session.query(Image.class).orderBy("timesShown, RANDOM()").limit(PAGE_SIZE).all(new QueryListener<Image>() {
            @Override
            public void onResult(final List<Image> images) {

                if (images == null)
                    return;

                mImages.addAll(images);

                if (mListener != null) {
                    Log.d(TAG, "filled queue, fetching");
                    fetchImage();
                }

            }
        });
    }

    private void dumpState() {
        for (int i=0; i<mImages.size(); i++)
            Log.d(TAG, "%d) %s", i, mImages.get(i));

        for (Image i : mBitmaps.keySet())
            Log.d(TAG, "%s - %s", mBitmaps.get(i) != null ? "cached" : "caching", i);
    }

    private void onResponse(final Image image, final Object obj) {
        if (obj == null)
            return;

        mBitmaps.put(image, obj);

        Log.d(TAG, "fetched %s", obj);

        if (mListener != null) {
            Log.d(TAG, "delivering fetched movie %s to deferred listener", image.getUrl());
            fetchImage();
        }
    }

    private void precache() {

        Log.d(TAG, "precache size = %d", mBitmaps.size());

        int i = 0;
        while (mBitmaps.size() < PRECACHE_SIZE && i < mImages.size()) {

            final Image image = mImages.get(i++);

            if (mBitmaps.containsKey(image))
                continue;

            mBitmaps.put(image, null);

            Log.d(TAG, "precaching image %s", image.getUrl());


            final Response.ErrorListener errorListener = new Response.ErrorListener() {
                @Override
                public void onErrorResponse(final VolleyError error) {
                            Log.e(TAG, "error fetchimg image %s : %s", image.getUrl(), error.getMessage());
                            mImages.remove(image);
                            mBitmaps.remove(image);
                            fetchImage();
                }
            };


            Request request;

            Log.d(TAG, "image %s is animated %s", image, image.isAnimated());

            if (image.isAnimated()) {
                request = new GifDecoderRequest(image.getUrl(), new Response.Listener<GifDrawable>() {
                    @Override
                    public void onResponse(final GifDrawable decoder) {
                        Log.d(TAG, "got movie reponse %s - %s", image, decoder);
                        ImageAdapter.this.onResponse(image, decoder);
                    }
                }, errorListener) {
                    @Override
                    public Priority getPriority() {
                        return Priority.NORMAL;
                    }
                };
            } else {
                request = new ImageRequest(image.getUrl(), new Response.Listener<Bitmap>() {
                    @Override
                    public void onResponse(final Bitmap bitmap) {
                        Log.d(TAG, "got image reponse %s - %s", image, bitmap);
                        ImageAdapter.this.onResponse(image, bitmap);
                    }
                }, mMaxWidth, mMaxHeight, Bitmap.Config.ARGB_8888, errorListener) {
                    @Override
                    public Priority getPriority() {
                        return Priority.NORMAL;
                    }
                };
            }

            Log.d(TAG, "fetching %s - %s", image, request);
            mRequestQueue.add(request);
        }
    }

    private void fetchImage() {
        for (Image image : mBitmaps.keySet()) {
            final Object obj = mBitmaps.get(image);
            if (obj != null) {
                deliverResponse(image, obj);
                break;
            }
        }

        precache();

    }

    public static interface ImageLoadedListener {
        public void onImageLoaded(Image image, Object object);
    }
}
