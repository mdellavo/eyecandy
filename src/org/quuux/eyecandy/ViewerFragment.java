package org.quuux.eyecandy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Movie;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.quuux.eyecandy.utils.MovieRequest;
import org.quuux.orm.CountListener;
import org.quuux.orm.Database;
import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.Query;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public class ViewerFragment extends Fragment implements ViewPager.PageTransformer {

    private static final String TAG = Log.buildTag(ViewerFragment.class);
    private Adapter mAdapter;
    private ViewPager mPager;
    private boolean mLoading;

    public static ViewerFragment newInstance(final Image image) {
        final ViewerFragment rv = new ViewerFragment();
        final Bundle args = new Bundle();
        args.putSerializable("image", image);
        rv.setArguments(args);
        return rv;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

        Display display = getActivity().getWindowManager().getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();

        mPager = new ViewPager(getActivity());
        mPager.setPageTransformer(true, this);

        final Database db = EyeCandyDatabase.getInstance(getActivity());
        final Session session = db.createSession();

        Query q = session.query(Image.class).filter("animated=?", 1);

        mAdapter = new Adapter(getActivity(), q, new Point(width, height));

        final Bundle args = getArguments();
        final Image image = (Image) args.getSerializable("image");

        if (image != null) {
            q = q.filter("images.id < ?", image.getId());

            q.project("COUNT(1)").scalar(Long.class, new ScalarListener<Long>() {
                @Override
                public void onResult(final Long obj) {
                    Log.d(TAG, "offset = %s", obj);
                    mAdapter.setOffset((Long)obj);
                    mPager.setAdapter(mAdapter);
                }
            });
        } else {
            mPager.setAdapter(mAdapter);
        }

        return mPager;
    }

    @Override
    public void transformPage(final View view, final float position) {
        int pageWidth = view.getWidth();
        view.setTranslationX(pageWidth * -position);

        if (position < -1) { // [-Infinity,-1)
            // This page is way off-screen to the left.
            view.setAlpha(0);
        } else if (position < 0) { // [-1,0]
            // Use the default slide transition when moving to the left page
            view.setAlpha(1-Math.abs(position));
            //view.setTranslationX(pageWidth * position);
            //view.setScaleX(1);
            //view.setScaleY(1);
        } else if (position == 0) {
            view.setAlpha(1);
            view.setTranslationX(0);
        } else if (position < 1) { // (0,1]
            // Fade the page out.
            view.setAlpha(1 - position);

            // Counteract the default slide transition

            // Scale the page down (between MIN_SCALE and 1)
            //float scaleFactor = MIN_SCALE
            //        + (1 - MIN_SCALE) * (1 - Math.abs(position));
            //view.setScaleX(scaleFactor);
            //view.setScaleY(scaleFactor);

        } else { // (1,+Infinity]
            // This page is way off-screen to the right.
            view.setAlpha(0);
        }

    }

    public static class Adapter extends PagerAdapter {

        private final Query mQuery;
        private final Point mSize;
        private final WeakReference<Context> mContext;
        private final Picasso mPicasso;
        private long mOffset;
        private final Map<Integer, Image> mImages = new HashMap<Integer, Image>();
        private int mCount;

        public Adapter(final Context context, final Query query, final Point size) {
            mQuery = query;
            mContext = new WeakReference<Context>(context);
            mPicasso = EyeCandyPicasso.getInstance(context);
            mSize = size;

            final Database db = EyeCandyDatabase.getInstance(context);
            final Session session = db.createSession();

            mQuery.count(new ScalarListener<Long>() {
                @Override
                public void onResult(final Long count) {
                    Log.d(TAG, "count = %s", count);
                    mCount = count.intValue();
                    notifyDataSetChanged();
                }
            });

        }

        private Query getQuery(final int position) {
            return mQuery.offset((int) (position + mOffset)).limit(1);
        }

        @Override
        public boolean isViewFromObject(final View view, final Object o) {
            return view.equals(o);
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {

            Log.d(TAG, "instantiateItem(position=%s)", position);

            final Context context = mContext.get();
            if (context == null)
                return null;

            final ImageView rv = new ImageView(context);
            rv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            final ViewPager.LayoutParams params = new ViewPager.LayoutParams();
            params.height = ViewPager.LayoutParams.MATCH_PARENT;
            params.width = ViewPager.LayoutParams.MATCH_PARENT;
            rv.setLayoutParams(params);

            container.addView(rv);

            loadItem(position, new FetchListener<Image>() {
                @Override
                public void onResult(final Image image) {
                    Log.d(TAG, "item @ pos %s = %s", position, image);
                    if (image.isAnimated()) {
                        loadMovie(rv, image);
                    } else {
                        loadImage(rv, image);
                    }
                }
            });

            //loadItem(position + 1, new FetchListener<Image>() {
            //
            //
            //   @Override
            //    public void on˚Result(final Image next) {
            //        Log.d(TAG, "prefetching item @ pos = %s - %s", position + 1, next);
            //        mPicasso.load(next.getUrl()).fetch();
            //    }
            //});

            return rv;
        }


        private void loadImage(final ImageView v, final Image image) {
            Log.d(TAG, "loading image %s", image.getUrl());
            mPicasso.load(image.getUrl()).resize(mSize.x, mSize.y).noFade().skipMemoryCache().centerCrop().into(v, new Callback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "loaded image");
                }  @Override
                public void onError() {
                    Log.d(TAG, "error loading image!");
                }
            });
        }


        private void loadMovie(final ImageView view, final Image image) {

            Log.d(TAG, "loading movie %s", image.getUrl());
            final Context context = mContext.get();
            if (context != null) {
                final RequestQueue requestQueue = EyeCandyVolley.getRequestQueue(context);
                requestQueue.add(new MovieRequest(image.getUrl(), new Response.Listener<Movie>() {
                    @Override
                    public void onResponse(final Movie movie) {
                        Log.d(TAG, "loaded movie - %s", image);
                        final Context context = mContext.get();
                        if (context != null) {
                            final AnimatedImageDrawable drawable = new AnimatedImageDrawable(context, movie);
                            view.setBackground(drawable);
                            drawable.setVisible(true, true);

                            Log.d(TAG, "movie is %s x %s @ %s ms", movie.width(), movie.height(), movie.duration());

                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(final VolleyError error) {
                        Log.e(TAG, "error loading movie - " + image, error);
                    }
                }));
            }
        }

        @Override
        public void destroyItem(final ViewGroup container, final int position, final Object object) {
            container.removeView((ImageView)object);
            // FIXME recycle drawable
        }

        private void loadItem(final int position, final FetchListener<Image> listener) {

            Log.d(TAG, "loading item at position %s", position);

            final Image i = mImages.get(position);
            if (i != null) {
                listener.onResult(i);
                return;
            }

            getQuery(position).first(new FetchListener<Image>() {
                @Override
                public void onResult(final Image result) {
                    mImages.put(position, result);
                    listener.onResult(result);
                }
            });
        }


        public void setOffset(final long offset) {
            mOffset = offset;
        }
    }

}
