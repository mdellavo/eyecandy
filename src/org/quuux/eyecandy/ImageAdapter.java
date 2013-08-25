package org.quuux.eyecandy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import org.quuux.orm.QueryListener;
import org.quuux.orm.Session;

import java.util.*;


public class ImageAdapter  {

    private static final String TAG = Log.buildTag(ImageAdapter.class);

    private static final int PRECACHE_SIZE = 3;
    private static final int HISTORY_SIZE = 4;
    private static final int PAGE_SIZE = 10;

    class BitmapLruCache extends LruCache<String, Bitmap> implements ImageLoader.ImageCache {

        public BitmapLruCache(int maxSize) {
            super(maxSize);
        }

        @Override
        public Bitmap getBitmap(String url) {
            return get(url);
        }

        @Override
        public void putBitmap(String url, Bitmap bitmap) {
            put(url, bitmap);
        }
    }

    private final Context mContext;
    private final EyeCandyDatabase mDatabase;
    private final RequestQueue mRequestQueue;

    private int mMaxWidth = 2048, mMaxHeight = 2048;
    private ImageLoader mImageLoader;

    private boolean mLoading = false;
    private List<Image> mImages = new LinkedList<Image>();
    private Map<Image, Bitmap> mBitmaps = new HashMap<Image, Bitmap>();

    private ImageLoadedListener mListener = null;

    public ImageAdapter(final Context context) {
        mContext = context;
        mDatabase = EyeCandyDatabase.getInstance(context);
        mRequestQueue = Volley.newRequestQueue(context);
        mImageLoader = new ImageLoader(mRequestQueue, new BitmapLruCache(HISTORY_SIZE));

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ScrapeService.ACTION_SCRAPE_COMPLETE);
        context.registerReceiver(mBroadcastReceiver, filter);
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
            mLoading = false;

            fillQueue();
        }

    }

    private void deliverResponse(final Image image, final Bitmap bitmap) {
        mImages.remove(image);
        mBitmaps.remove(image);
        mListener.onImageLoaded(image, bitmap);
        mListener = null;
        mLoading = false;
    }

    // yeah...
    public void previousImage(final ImageLoadedListener listener) {
        listener.onImageLoaded(null, null);
    }

    private Image popImage() {
        return mImages.remove(0);
    }

    private void fillQueue() {
        final Session session = mDatabase.createSession();

        final int num = PAGE_SIZE - mImages.size();

        if (num < 1 || num > PAGE_SIZE)
            return;

        Log.d(TAG, "filling queue");

        session.query(Image.class).orderBy("timesShown, RANDOM()").limit(num).all(new QueryListener<Image>() {
            @Override
            public void onResult(final List<Image> images) {

                if (images == null)
                    return;

                mImages.addAll(images);

                if (!mLoading && mListener != null) {
                    Log.d(TAG, "delivering image to deferred listener");
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


    private void precache() {


        dumpState();

        Log.d(TAG, "precache size = %d", mBitmaps.size());

        int i = 0;
        while (mBitmaps.size() < PRECACHE_SIZE) {

            if (i >= mImages.size())
                break;

            final Image image = mImages.get(i++);

            if (mBitmaps.containsKey(image))
                continue;

            mBitmaps.put(image, null);

            Log.d(TAG, "precaching image %s", image.getUrl());


            mImageLoader.get(image.getUrl(), new ImageLoader.ImageListener() {
                @Override
                public void onResponse(final ImageLoader.ImageContainer response, final boolean isImmediate) {
                    final Bitmap bitmap = response.getBitmap();

                    if (bitmap == null)
                        return;

                    mBitmaps.put(image, bitmap);

                    Log.d(TAG, "fetched image %s (isImmediate = %s | bitmap = %s) ", image.getUrl(), isImmediate, bitmap);

                    if (mListener != null) {
                        Log.d(TAG, "delivering fetched image %s to deferred listener", image.getUrl());
                        fetchImage();
                    }
                }

                @Override
                public void onErrorResponse(final VolleyError error) {
                    Log.e(TAG, "error fetchimg image %s : %s", image.getUrl(), error.getMessage());
                    mImages.remove(image);
                    mBitmaps.remove(image);
                    precache();
                }
            }, mMaxWidth, mMaxHeight);

        }
    }

    private void fetchImage() {

        final Image image = mImages.get(0);

        Log.d(TAG, "fetching image %s", image.getUrl());

        final Bitmap bitmap = mBitmaps.get(image);
        if (bitmap != null) {
            deliverResponse(image, bitmap);
        }

        precache();

    }

    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String action = intent.getAction();
            if (ScrapeService.ACTION_SCRAPE_COMPLETE.equals(action)) {
                fillQueue();
            }

        }
    };

}
