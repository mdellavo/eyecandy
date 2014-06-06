package org.quuux.eyecandy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Display;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.view.ViewHelper;
import com.nineoldandroids.view.ViewPropertyAnimator;
import com.squareup.picasso.Picasso;

import org.quuux.eyecandy.utils.GifDecoder;
import org.quuux.eyecandy.utils.GifDecoderRequest;
import org.quuux.eyecandy.utils.ImageUtils;
import org.quuux.orm.Database;
import org.quuux.orm.FetchListener;
import org.quuux.orm.Query;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import pl.droidsonroids.gif.GifDrawable;

public class ViewerFragment
        extends Fragment
        implements ViewPager.PageTransformer,
                   ViewPager.OnPageChangeListener,
                   View.OnTouchListener, View.OnLongClickListener {

    private static final int LOOKAHEAD = 10;

    public interface Listener {
        void startLeanback();
        void endLeanback();
        void exitLeanback();
        boolean isLeanback();
        void setSelectedNavigationItemSilent(int pos);
        boolean onLeanbackTouch(MotionEvent ev);
        void openImage(Image image);
        void castImage(Image image);
    }

    private static final String TAG = Log.buildTag(ViewerFragment.class);
    private static final long FLIP_DELAY = 15 * 1000;
    private Adapter mAdapter;
    private ViewPager mPager;
    private boolean mFlipping;
    private Listener mListener;
    private GestureDetector mGestureDetector;
    private Handler mHandler = new Handler();
    private int mShortAnimationDuration;

    public static ViewerFragment newInstance(final Query query, final int position, final Subreddit subreddit) {
        final ViewerFragment rv = new ViewerFragment();
        final Bundle args = new Bundle();
        args.putSerializable("query", query);
        args.putInt("position", position);
        args.putSerializable("subreddit", subreddit);
        rv.setArguments(args);
        return rv;
    }

    public static ViewerFragment newInstance(final Query query) {
        return newInstance(query, 0, null);
    }


    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        if (!(activity instanceof Listener)) {
            throw new IllegalArgumentException("Activity must implement Listener");
        }

        mListener = (Listener) activity;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mShortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        setHasOptionsMenu(true);

        mFlipping = EyeCandyPreferences.isFlipping(getActivity());

        mGestureDetector = new GestureDetector(getActivity(), mGestureListener);

        final Display display = getActivity().getWindowManager().getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();

        final Bundle args = getArguments();

        Query q = null;
        if (savedInstanceState == null) {
            q = (Query) getArguments().getSerializable("query");
        } else {
            q = (Query) savedInstanceState.getSerializable("query");
        }

        final Database db = EyeCandyDatabase.getInstance(getActivity());
        final Session session = db.createSession();
        mAdapter = new Adapter(this, session.bind(q), new Point(width, height));

    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

        final View rv = inflater.inflate(R.layout.viewer, container, false);

        mPager = (ViewPager) rv.findViewById(R.id.pager);
        mPager.setPageTransformer(true, this);

        mPager.setAdapter(mAdapter);
        mPager.setOnPageChangeListener(this);

        mPager.setOnLongClickListener(this);

        return rv;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final int position;
        if (savedInstanceState != null && savedInstanceState.containsKey("position"))
            position = savedInstanceState.getInt("position");
        else
            position = getArguments().getInt("position");

        // WHY?!
        mPager.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "setting position - %s", position);
                mPager.setCurrentItem(position);
            }
        }, 100);

    }

    @Override
    public void onResume() {
        super.onResume();

        if (mFlipping)
            startFlipping();

        final Activity act = getActivity();
        if (act == null)
            return;

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ScrapeService.ACTION_SCRAPE_COMPLETE);
        act.registerReceiver(mBroadcastReceiver, filter);

        mListener.setSelectedNavigationItemSilent(MainActivity.MODE_SLIDE_SHOW);
        mListener.startLeanback();

        mPager.postDelayed(new Runnable() {
            @Override
            public void run() {
                mListener.castImage(getImage(0));
            }
        }, 100);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mFlipping)
            stopFlipping();

        final Activity act = getActivity();
        if (act == null)
            return;

        act.unregisterReceiver(mBroadcastReceiver);
        mListener.exitLeanback();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAdapter != null)
            outState.putSerializable("query", mAdapter.getQuery());

        if (mPager != null)
            outState.putInt("position", mPager.getCurrentItem());
    }

    private void setMenuVisible(final Menu menu, final int id, final boolean visible) {
        final MenuItem item = menu.findItem(id);
        if (item != null)
            item.setVisible(visible);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.viewer, menu);
        setMenuVisible(menu, R.id.play, !mFlipping);
        setMenuVisible(menu, R.id.pause, mFlipping);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        final boolean rv;

        switch (item.getItemId()) {

            case R.id.play:
            case R.id.pause:
                toggleFlipping();
                rv = true;
                break;

            default:
                rv = super.onOptionsItemSelected(item);
                break;
        }

        return rv;
    }

    @Override
    public void transformPage(final View view, final float position) {
        int pageWidth = view.getWidth();

        ViewHelper.setTranslationX(view, pageWidth * -position);

        if (position < -1) { // [-Infinity,-1)
            // This page is way off-screen to the left.
            ViewHelper.setAlpha(view, 0);
        } else if (position < 0) { // [-1,0]
            // Use the default slide transition when moving to the left page
            ViewHelper.setAlpha(view, 1-Math.abs(position));
            //view.setTranslationX(pageWidth * position);
            //view.setScaleX(1);
            //view.setScaleY(1);
        } else if (position == 0) {
            ViewHelper.setAlpha(view, 1);
            ViewHelper.setTranslationX(view, 0);
        } else if (position < 1) { // (0,1]
            // Fade the page out.
            ViewHelper.setAlpha(view, 1 - position);

            // Counteract the default slide transition

            // Scale the page down (between MIN_SCALE and 1)
            //float scaleFactor = MIN_SCALE
            //        + (1 - MIN_SCALE) * (1 - Math.abs(position));
            //view.setScaleX(scaleFactor);
            //view.setScaleY(scaleFactor);

        } else { // (1,+Infinity]
            // This page is way off-screen to the right.
            ViewHelper.setAlpha(view, 0);
        }

    }

    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        boolean rv = false;
        rv |= mListener.onLeanbackTouch(event);
        rv |= mGestureDetector.onTouchEvent(event);
        return rv;
    }

    @Override
    public boolean onLongClick(final View v) {

        final View cur = mPager.findViewWithTag(mPager.getCurrentItem());
        if (cur == null)
            return false;


        final Image image = (Image) cur.getTag(R.id.image);
        if (image == null)
            return false;

        mListener.openImage(image);

        return true;
    }

    @Override
    public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
    }

    private Image getImage(final int i) {
        final View v = mPager.findViewWithTag(i);

        if (v == null)
            return null;

        final Holder holder = (Holder) v.getTag(R.id.holder);
        if (holder == null)
            return null;

        return (Image) v.getTag(R.id.image);

    }

    @Override
    public void onPageSelected(final int i) {
        Log.d(TAG, "page selected %d", i);
        final View v = mPager.findViewWithTag(i);

        if (v == null)
            return;

        final Holder holder = (Holder) v.getTag(R.id.holder);
        if (holder == null)
            return;

        if (holder == null)
            return;

        final Image image =  (Image) v.getTag(R.id.image);

        if (holder.imageFailed) {
            Log.d(TAG, "selected a failed image %s, skipping...",
                    image != null ? image.getUrl() : "(null)");

            flipImage();
            return;
        } else if (mFlipping && holder.imageLoaded) {
            Log.d(TAG, "image loaded and selected, scheduling flip");
            startFlipping();
        }

        if (holder.movie != null) {
            Log.d(TAG, "starting movie");
            holder.movie.reset();
            holder.movie.start();
        }

        holder.summon(true);

        mAdapter.getQuery().count(new ScalarListener<Long>() {
            @Override
            public void onResult(final Long count) {

                Log.d(TAG, "viewing position %s of %s (remaining=%s / lookahead=%s)", i, count, count-i, LOOKAHEAD);

                if (count - i < LOOKAHEAD) {
                    final Subreddit subreddit = getSubreddit();
                    if (subreddit != null) {
                        Log.d(TAG, "scraping more from %s", subreddit.getSubreddit());
                        ScrapeService.scrapeSubreddit(getActivity(), subreddit);
                    }
                }
            }
        });
    }

    private Subreddit getSubreddit() {
        final Bundle args = getArguments();
        return (Subreddit) args.getSerializable("subreddit");
    }

    @Override
    public void onPageScrollStateChanged(final int state) {

    }


    private void toggleTitle() {
        final View cur = mPager.findViewWithTag(mPager.getCurrentItem());
        if (cur == null)
            return;

        final Holder holder = (Holder) cur.getTag(R.id.holder);
        if (holder == null)
            return;

        if (mListener.isLeanback())
            holder.dismiss();
        else
            holder.summon();
    }

    private void toggleFlipping() {
        mFlipping = !mFlipping;
        if (mFlipping)
            startFlipping();
        else
            stopFlipping();

        final FragmentActivity activity = getActivity();
        if (activity != null)
            activity.supportInvalidateOptionsMenu();

        EyeCandyPreferences.setFlipping(activity, mFlipping);
    }

    private void startFlipping() {
        Log.d(TAG, "scheduling flip");
        mHandler.removeCallbacks(mFlipCallback);
        mHandler.postDelayed(mFlipCallback, FLIP_DELAY);
    }
    
    private void stopFlipping() {
        Log.d(TAG, "stopping flip");
        mHandler.removeCallbacks(mFlipCallback);
    }

    private void flipImage() {
        Log.d(TAG, "flip image");

        final int pos = mPager.getCurrentItem() + 1;
        final Image image = getImage(pos);
        mListener.castImage(image);
        mPager.setCurrentItem(pos, true);
    }

    private Runnable mFlipCallback = new Runnable() {
        @Override
        public void run() {
            flipImage();
        }
    };

    private void onImageLoaded(final Holder holder) {

        // The on screen page loaded, set callback
        if (mFlipping && mPager.getCurrentItem() == holder.position) {
            Log.d(TAG, "currently selected view loaded, scheduling flip");
            startFlipping();
        }

        if (holder.movie != null && mPager.getCurrentItem() == holder.position) {
            holder.movie.reset();
            holder.movie.start();

        }
    }

    private void onImageError(final Holder holder, final VolleyError error) {
        final Context context = getActivity();
        if (context == null)
            return;

        if (holder.position == mPager.getCurrentItem()) {
            Log.d(TAG, "image failed on currently selected view, skipping...");
            Toast.makeText(context, R.string.error_loading_image, Toast.LENGTH_LONG).show();
            flipImage();
        }

    }

    static class Holder {
        int duration;
        boolean imageFailed;
        boolean imageLoaded;
        int position;
        Bitmap backingBitmap;
        Bitmap bitmap;

        GifDrawable movie;
        ImageView image;
        ImageView backing;
        ProgressBar spinner;
        TextView title;

        private Runnable mDismissCallback = new Runnable() {
            @Override
            public void run() {
                dismiss();
            }
        };

        void summon(final boolean autoDismiss) {
            ViewPropertyAnimator
                    .animate(title)
                    .setDuration(Math.round(duration))
                    .alpha(1)
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(final Animator animation) {
                        }
                        @Override
                        public void onAnimationEnd(final Animator animation) {
                            if (autoDismiss)
                                dismiss();
                        }

                        @Override
                        public void onAnimationCancel(final Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(final Animator animation) {

                        }
                    });
        }

        void summon() {
            summon(false);
        }

        void dismiss() {
            ViewPropertyAnimator
                    .animate(title)
                    .setDuration(Math.round(ViewHelper.getAlpha(title) * 4 * duration))
                    .alpha(0)
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(final Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(final Animator animation) {
                        }

                        @Override
                        public void onAnimationCancel(final Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(final Animator animation) {

                        }
                    });
        }

    }

    public static class Adapter extends PagerAdapter {
        private final Query mQuery;
        private final Point mSize;
        private final WeakReference<ViewerFragment> mFrag;
        private final Picasso mPicasso;
        private long mOffset;
        private final Map<Integer, Image> mImages = new HashMap<Integer, Image>();
        private int mCount;

        public Adapter(final ViewerFragment frag, final Query query, final Point size) {
            final Context context = frag.getActivity();

            mQuery = query;
            mFrag = new WeakReference<ViewerFragment>(frag);
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

        private ViewerFragment getFrag() {
            return mFrag.get();
        }

        private Context getContext() {
            final ViewerFragment frag = getFrag();
            if (mFrag == null)
                return null;

            final Context context = frag.getActivity();
            if (context == null)
                return null;

            return context;
        }

        public Query getQuery() {
            return mQuery;
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

            final Context context = getContext();
            if (context == null)
                return null;

            final LayoutInflater inflater =
                    (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            final View rv = inflater.inflate(R.layout.viewer_page, null);

            container.addView(rv);

            final Holder holder = new Holder();
            holder.duration = context.getResources().getInteger(android.R.integer.config_shortAnimTime) * 4;
            holder.position = position;
            holder.backing = (ImageView) rv.findViewById(R.id.backing);
            holder.image = (ImageView) rv.findViewById(R.id.image);
            holder.spinner = (ProgressBar) rv.findViewById(R.id.spinner);
            holder.title = (TextView)rv.findViewById(R.id.title);

            rv.setTag(position);
            rv.setTag(R.id.holder, holder);

            rv.setOnTouchListener(getFrag());
            rv.setOnLongClickListener(getFrag());

            loadItem(position, new FetchListener<Image>() {
                @Override
                public void onResult(final Image image) {

                    if (image == null)
                        return;

                    rv.setTag(R.id.image, image);

                    holder.title.setText(image.getTitle());
                    holder.title.setVisibility(View.VISIBLE);
                    ViewHelper.setAlpha(holder.title, 0);

                    Log.d(TAG, "item @ pos %s = %s", position, image);
                    if (image.isAnimated()) {
                        loadMovie(holder, image);
                    } else {
                        loadImage(holder, image);
                    }

                    final EyeCandyDatabase db = EyeCandyDatabase.getInstance(context);
                    final Session session = db.createSession();
                    image.stamp();
                    session.add(image);
                    session.commit();

                }
            });

            return rv;
        }


        private void send(final Context context, final Request<?> r) {
            final RequestQueue requestQueue = EyeCandyVolley.getRequestQueue(context);
            requestQueue.add(r);
        }

        private void loadImage(final Holder holder, final Image image) {
            Log.d(TAG, "loading image %s", image.getUrl());

            final Context context = getContext();
            if (context == null)
                return;

            final ImageRequest request = new ImageRequest(image.getUrl(), new Response.Listener<Bitmap>() {
                @Override
                public void onResponse(final Bitmap bitmap) {
                    Log.d(TAG, "got image reponse %s - %s (%s x %s) ",
                            image, bitmap, bitmap.getWidth(), bitmap.getHeight());
                    holder.image.setImageBitmap(bitmap);
                    holder.bitmap = bitmap;
                    setBacking(holder, bitmap);
                    onImageLoaded(holder);
                }
            }, mSize.x, mSize.y, Bitmap.Config.ARGB_8888, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(final VolleyError error) {
                    Log.e(TAG, "error loading image %s", error, image);
                    onImageError(holder, error);
                }
            }) {
                @Override
                public Priority getPriority() {
                    return Priority.IMMEDIATE;
                }
            };


            send(context, request);
        }

        private void loadMovie(final Holder holder, final Image image) {
            final long t1 = SystemClock.uptimeMillis();
            Log.d(TAG, "loading movie %s", image.getUrl());
            final Context context = getContext();
            if (context == null)
                return;

            final GifDecoderRequest request = new GifDecoderRequest(image.getUrl(), new Response.Listener<GifDrawable>() {
                @Override
                public void onResponse(final GifDrawable drawable) {

                    final Context context = getContext();
                    if (context == null)
                        return;

                    if (drawable == null || drawable.getNumberOfFrames() == 0) {
                        Log.d(TAG, "error loading gif %s", image);
                        return;
                    }

                    holder.image.setImageDrawable(drawable);
                    drawable.setVisible(true, true);

                    holder.movie = drawable;

                    if (drawable.canSeekForward())
                        drawable.seekTo(drawable.getDuration() / 2);

                    setBacking(holder, drawable);

                    if (drawable.canSeekBackward())
                        drawable.reset();

                    final long t2 = SystemClock.uptimeMillis();
                    Log.d(TAG, "loaded gif - %s (%s @ %s frames) in %d ms", image, drawable.getBounds(), drawable.getNumberOfFrames(), t2-t1);

                    onImageLoaded(holder);

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(final VolleyError error) {
                    Log.e(TAG, "error loading movie - " + image, error);
                    onImageError(holder, error);
                }
            }) {
                @Override
                public Priority getPriority() {
                    return Priority.IMMEDIATE;
                }
            };

            send(context, request);
        }

        @Override
        public void destroyItem(final ViewGroup container, final int position, final Object object) {
            final View view = (View)object;
            final Holder holder = (Holder) view.getTag(R.id.holder);

            if (holder.bitmap != null) {
                Log.d(TAG, "recycling bitmap");
                holder.bitmap.recycle();
                holder.bitmap = null;
            }

            if (holder.backingBitmap != null) {
                Log.d(TAG, "recycling backing bitmap");
                holder.backingBitmap.recycle();
                holder.backingBitmap = null;
            }

            if (holder.movie != null) {
                holder.movie.recycle();
                holder.movie = null;
            }

            container.removeView(view);
        }

        private void setBacking(final Holder holder, final Bitmap bitmap) {

            final Context context = getContext();
            if (context == null)
                return;

            final long t1 = SystemClock.uptimeMillis();

            ImageUtils.blur(context, bitmap, 25, new ImageUtils.Listener() {
                @Override
                public void complete(final Bitmap backing) {

                    if (backing == null)
                        return;

                    holder.backing.setVisibility(View.VISIBLE);
                    final long t2 = SystemClock.uptimeMillis();

                    Log.d(TAG, "generated backing (%s x %s) in %s ms",
                            backing.getWidth(), backing.getHeight(), t2 - t1);

                    holder.backing.setImageBitmap(backing);
                    holder.backingBitmap = backing;

                    ViewHelper.setAlpha(holder.backing, 0);
                    ViewPropertyAnimator.animate(holder.backing).alpha(.4f).setDuration(holder.duration).start();
                }
            });
        }

        private void setBacking(final Holder holder, final Drawable drawable) {
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            setBacking(holder, bitmap);
        }

        private void onImageLoaded(final Holder holder) {
            holder.image.setVisibility(View.VISIBLE);
            ViewHelper.setAlpha(holder.image, 0);
            ViewPropertyAnimator.animate(holder.image).alpha(1).setDuration(holder.duration).start();

            holder.spinner.setVisibility(View.GONE);

            holder.imageLoaded = true;

            final ViewerFragment frag = getFrag();
            if (frag == null)
                return;


            frag.onImageLoaded(holder);
        }


        private void onImageError(final Holder holder, final VolleyError error) {
            final ViewerFragment frag = getFrag();
            if (frag == null)
                return;

            holder.imageFailed = true;
            holder.spinner.setVisibility(View.GONE);
            holder.image.setBackgroundResource(R.drawable.ic_action_alerts_and_states_error);

            frag.onImageError(holder, error);
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


    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String action = intent.getAction();
            if (ScrapeService.ACTION_SCRAPE_COMPLETE.equals(action)) {
                if (mAdapter.getCount() == 0) {
                    mAdapter.notifyDataSetChanged();
                    mPager.setAdapter(mAdapter);
                }
            }

        }
    };

    final GestureDetector.OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(final MotionEvent e) {
            Log.d(TAG, "tap!");
            toggleTitle();
            return false;
        }
    };

}
