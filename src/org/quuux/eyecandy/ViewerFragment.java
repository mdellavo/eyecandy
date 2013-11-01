package org.quuux.eyecandy;

import android.content.Context;
import android.graphics.Bitmap;
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

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

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
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Display display = getActivity().getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);
        mAdapter = new Adapter(getActivity(), size);

        final Bundle args = getArguments();
        final Image image = (Image) args.getSerializable("image");

        if (image != null) {
            mLoading = true;
            final Database db = EyeCandyDatabase.getInstance(getActivity());
            final Session session = db.createSession();
            session.query(Image.class).filter("images.id < ?", image.getId()).project("COUNT(1)").scalar(new ScalarListener() {
                @Override
                public void onResult(final Object obj) {
                    Log.d(TAG, "offset = %s", obj);

                    mAdapter.setOffset((Long)obj);
                    if (mPager != null)
                        mPager.setAdapter(mAdapter);
                }
            });
        }

    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        mPager = new ViewPager(getActivity());
        mPager.setPageTransformer(true, this);

        if (!mLoading)
            mPager.setAdapter(mAdapter);

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

        private final Point mSize;
        private WeakReference<Context> mContext;
        private Picasso mPicasso;
        private long mOffset;
        private Map<Integer, Image> mImages = new HashMap<Integer, Image>();

        public Adapter(final Context context, final Point size) {
            mContext = new WeakReference<Context>(context);
            mPicasso = EyeCandyPicasso.getInstance(context);
            mSize = size;

        }

        private Query getQuery(final int position) {
            Query rv = null;

            final Context context = mContext.get();
            if (context != null) {
                final Database db = EyeCandyDatabase.getInstance(context);
                final Session session = db.createSession();
                rv = session.query(Image.class).offset((int) (position + mOffset)).limit(1);
            }

            return rv;
        }

        @Override
        public boolean isViewFromObject(final View view, final Object o) {
            return view.equals(o);
        }

        @Override
        public int getCount() {
            return 100000;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {

            Log.d(TAG, "instantiateItem(position=%s)", position);

            final Context context = mContext.get();
            if (context == null)
                return null;

            final ImageView rv = new ImageView(context);

            final ViewPager.LayoutParams params = new ViewPager.LayoutParams();
            params.height = ViewPager.LayoutParams.MATCH_PARENT;
            params.width = ViewPager.LayoutParams.MATCH_PARENT;
            rv.setLayoutParams(params);
            rv.setAlpha(0);



            container.addView(rv);

            final Image i = mImages.get(position);
            if (i == null)
                loadItem(rv, position);
            else
                loadImage(rv, i);

            return rv;
        }

        @Override
        public void destroyItem(final ViewGroup container, final int position, final Object object) {
            container.removeView((ImageView)object);
        }

        private void loadItem(final ImageView v, final int position) {

            Log.d(TAG, "loading item at position %s", position);

            final Query query = getQuery(position);
            query.first(new FetchListener<Image>() {
                @Override
                public void onResult(final Image result) {
                    mImages.put(position, result);

                    Log.d(TAG, "item @ pos %s = %s", position, result);
                    loadImage(v, result);
                }
            });
        }

        private void loadImage(final ImageView v, final Image image) {
            Log.d(TAG, "loading image %s", image.getUrl());
            mPicasso.load(image.getUrl()).resize(mSize.x, mSize.y).centerCrop().into(v, new Callback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "loaded image");
                }

                @Override
                public void onError() {
                    Log.d(TAG, "error loading image!");
                }
            });
        }

        public void setOffset(final long offset) {
            mOffset = offset;
        }
    }

}
