package org.quuux.eyecandy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.effect.EffectUpdateListener;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.view.ViewHelper;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import org.quuux.eyecandy.utils.GifDecoderRequest;
import org.quuux.eyecandy.utils.ImageUtils;
import org.quuux.orm.FetchListener;
import org.quuux.orm.Func;
import org.quuux.orm.Query;
import org.quuux.orm.Session;
import org.quuux.orm.util.QueryAdapter;

import pl.droidsonroids.gif.GifDrawable;

public class GalleryFragment
        extends Fragment
        implements AbsListView.OnScrollListener,
                   OnBackPressedListener,
                   View.OnLongClickListener {


    private static final String TAG = Log.buildTag(GalleryFragment.class);
    private static final int FLIP_DELAY = 15 * 1000;

    public static interface Listener {
        void showImage(Query query, int position);
        void openImage(Image image);
        void castImage(Image image);
        void castStartFlipping(Query query, int delay);
        void castStopFlipping();
        void setSelectedNavigationItemSilent(int modeGallery);

        void sendEvent(String category, String action);

        void sendEvent(String category, String action, String label);
    }

    public static final int THUMB_SIZE = 100;

    Listener mListener;

    private Query mQuery;

    private ViewGroup mContainer;
    private GridView mGridView;
    private Adapter mThumbnailsAdapter;

    Picasso mPicasso;

    private Animator mCurrentAnimator;
    private int mShortAnimationDuration;
    private ImageView mZoomedImage;
    private boolean mZoomed;

    private TextView mTitle;
    private View mScrim;

    private ProgressBar mProgressBar;

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

        final Context context = getActivity();

        setHasOptionsMenu(true);

        mPicasso = EyeCandyPicasso.getInstance(context);

        mShortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        final Session session = EyeCandyDatabase.getSession(context);

        final Bundle args = getArguments();
        if (savedInstanceState != null && savedInstanceState.containsKey("query"))
            mQuery = session.bind((Query) savedInstanceState.getSerializable("query"));
        else if (args != null && args.containsKey("query"))
            mQuery = session.bind((Query) args.getSerializable("query"));
        else
            mQuery = session.query(Image.class).orderBy("id DESC");

        mThumbnailsAdapter = new Adapter(context, mQuery);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("query", mQuery);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

        final View rv = inflater.inflate(getLayout(), container, false);

        mContainer = container;

        mGridView = (GridView)rv.findViewById(R.id.grid);
        mGridView.setAdapter(mThumbnailsAdapter);
        mGridView.setOnScrollListener(this);

        mZoomedImage = (ImageView)rv.findViewById(R.id.zoomed_image);

        mTitle = (TextView)rv.findViewById(R.id.title);
        ViewHelper.setAlpha(mTitle, 0);

        mScrim = rv.findViewById(R.id.scrim);
        mScrim.setVisibility(View.VISIBLE);
        ViewHelper.setAlpha(mScrim, 0);

        final Session session = EyeCandyDatabase.getSession(getActivity());
        mQuery.orderBy(Func.RANDOM).limit(1).first(new FetchListener<Image>() {
            @Override
            public void onResult(final Image image) {
                if (image != null)
                    setBackground((ImageView) rv.findViewById(R.id.backing), image);

                mListener.castImage(image);
                
                rv.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mListener.castStartFlipping(mQuery, FLIP_DELAY);
                    }
                }, FLIP_DELAY);
            }
        });

        mProgressBar = (ProgressBar)rv.findViewById(R.id.progress_bar);

        return rv;
    }


    @Override
    public void onResume() {
        super.onResume();

        final Activity act = getActivity();
        if (act == null)
            return;

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ScrapeService.ACTION_SCRAPE_COMPLETE);
        act.registerReceiver(mBroadcastReceiver, filter);
        mListener.setSelectedNavigationItemSilent(MainActivity.MODE_GALLERY);

    }

    @Override
    public void onPause() {
        super.onPause();

        final Context context = getActivity();
        if (context != null) {
            context.unregisterReceiver(mBroadcastReceiver);
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (mZoomed)
            inflater.inflate(R.menu.gallery, menu);

    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        if (item.getItemId() == R.id.select) {
            final Holder tag = (Holder) mZoomedImage.getTag();
            if (tag != null) {
                endZoom(mZoomedImage);
                mListener.showImage(mQuery, mThumbnailsAdapter.getPositionForItem(tag.image));
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    public boolean onLongClick(final View v) {
        final Holder tag = (Holder) mZoomedImage.getTag();
        if (tag == null)
            return false;

        mListener.openImage(tag.image);
        return true;
    }

    public int getLayout() {
        return R.layout.gallery;
    }

    public int getItemLayout() {
        return R.layout.thumbnail;
    }

    public void bindItem(Holder tag) {
        mPicasso.load(tag.image.getThumbnailUrl())
                .fit()
                .placeholder(R.drawable.ic_loading)
                .into(tag.thumbnail, tag.callback);
    }

    // FIXME factor this out nicely and reuse , maybe base fragment
    private void setBackground(final ImageView v, final Image image) {
        final Activity act = getActivity();
        if (act == null)
            return;

        final Display display = getActivity().getWindowManager().getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();

        final Response.Listener<Bitmap> listener = new Response.Listener<Bitmap>() {
            @Override
            public void onResponse(final Bitmap src) {
                ImageUtils.blur(act, src, 25, new ImageUtils.Listener() {
                    @Override
                    public void complete(final Bitmap bitmap) {

                        if (bitmap != null) {
                            v.setImageBitmap(bitmap);
                            ViewHelper.setAlpha(v, .4f);
                        }
                    }
                });
            }
        };

        final Response.ErrorListener errorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(final VolleyError error) {
                Log.e(TAG, "error loading image for backing - %s", error, image);
            }
        };

        final ImageRequest request = new ImageRequest(
                image.getUrl(),
                listener,
                width,
                height,
                Bitmap.Config.ARGB_8888,
                errorListener
        );

        final RequestQueue requestQueue = EyeCandyVolley.getRequestQueue(act);
        requestQueue.add(request);

    }

    @Override
    public void onScrollStateChanged(final AbsListView absListView, final int i) {
        //Log.d(TAG, "onScrollStateChanged(state=%s)", i);

        mThumbnailsAdapter.onScrollStateChanged(absListView, i);
    }

    @Override
    public void onScroll(final AbsListView view, final int firstVisibleItem, final int visibleItemCount, final int totalItemCount) {
        //Log.d(TAG, "onScroll(firstVisibleItem=%s, visibleItemCount=%s, totalItemCount=%s)", firstVisibleItem, visibleItemCount, totalItemCount);
        mThumbnailsAdapter.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
    }

    public static GalleryFragment newInstance(final Query query, final Subreddit subreddit) {
        final GalleryFragment rv = new GalleryFragment();
        final Bundle args = new Bundle();
        if (query != null)
            args.putSerializable("query", query);

        if (subreddit != null)
            args.putSerializable("subreddit", subreddit);

        rv.setArguments(args);
        return rv;
    }

    private AnimatorSet fade(final View v, final float start, final float end) {
        final AnimatorSet s = new AnimatorSet();

        s.play(ObjectAnimator.ofFloat(v, "alpha", start, end));
        s.setDuration(mShortAnimationDuration * 3);
        s.setInterpolator(new DecelerateInterpolator());

        s.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(final Animator animation) {
                super.onAnimationCancel(animation);
                ViewHelper.setAlpha(v, end);
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                ViewHelper.setAlpha(v, end);
            }
        });
        s.start();

        return s;
    }

    private void fadeOut(final View v) {
        fade(v, 1f, 0);
    }

    private void fadeIn(final View v) {
        fade(v, 0, 1f);
    }

    // FIXME load backing?
    private void onZoomedImageLoadComplete() {
        fadeOut(mProgressBar);

    }

    private void loadZoomedImage(final Holder tag, final Rect bounds) {
        if (tag.image.isAnimated()) {

            final Context context = getActivity();
            if (context == null)
                return;

            Log.d(TAG, "loading movie %s", tag.image.getUrl());

            final RequestQueue queue = EyeCandyVolley.getRequestQueue(context);

            final GifDecoderRequest request = new GifDecoderRequest(
                    tag.image.getUrl(),
                    new Response.Listener<GifDrawable>() {
                        @Override
                        public void onResponse(final GifDrawable response) {
                            if (response != null) {
                                mZoomedImage.setImageDrawable(response);
                                response.setVisible(true, true);
                            }

                            onZoomedImageLoadComplete();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(final VolleyError error) {
                            Log.e(TAG, "error loading movie", error);
                            onZoomedImageLoadComplete();
                        }
                    }
            );

            queue.add(request);

        } else {

            mPicasso.load(tag.image.getUrl())
                    .resize(bounds.width(), bounds.height())
                    .centerInside()
                    .into(mZoomedImage, new Callback() {
                        @Override
                        public void onSuccess() {
                            onZoomedImageLoadComplete();
                        }

                        @Override
                        public void onError() {
                            onZoomedImageLoadComplete();
                        }
                    });
        }

    }

    void zoom(final Holder tag) {
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        startZoom(tag.thumbnail);

        mTitle.setText(tag.image.getTitle());
        fadeIn(mTitle);

        fadeIn(mScrim);

        final Rect startBounds = new Rect();
        final Rect finalBounds = new Rect();
        final Point globalOffset = new Point();

        tag.thumbnail.getGlobalVisibleRect(startBounds);

        mContainer.getGlobalVisibleRect(finalBounds, globalOffset);
        startBounds.offset(-globalOffset.x, -globalOffset.y);
        finalBounds.offset(-globalOffset.x, -globalOffset.y);

        float startScale;

        if ((float) finalBounds.width() / finalBounds.height()
                > (float) startBounds.width() / startBounds.height()) {
            startScale = (float) startBounds.height() / finalBounds.height();
            float startWidth = startScale * finalBounds.width();
            float deltaWidth = (startWidth - startBounds.width()) / 2;
            startBounds.left -= deltaWidth;
            startBounds.right += deltaWidth;
        } else {
            startScale = (float) startBounds.width() / finalBounds.width();
            float startHeight = startScale * finalBounds.height();
            float deltaHeight = (startHeight - startBounds.height()) / 2;
            startBounds.top -= deltaHeight;
            startBounds.bottom += deltaHeight;
        }

        ViewHelper.setAlpha(tag.thumbnail, 0f);

        mZoomedImage.setTag(tag);

        mZoomedImage.setVisibility(View.VISIBLE);
        mZoomedImage.setImageDrawable(tag.thumbnail.getDrawable());
        mZoomedImage.setOnLongClickListener(this);

        ViewHelper.setPivotX(mZoomedImage, 0);
        ViewHelper.setPivotY(mZoomedImage, 0);

        loadZoomedImage(tag, finalBounds);

        AnimatorSet set = new AnimatorSet();
        set
                .play(ObjectAnimator.ofFloat(mZoomedImage, "x",
                        startBounds.left, finalBounds.left))
                .with(ObjectAnimator.ofFloat(mZoomedImage, "y",
                        startBounds.top, finalBounds.top))
                .with(ObjectAnimator.ofFloat(mZoomedImage, "scaleX",
                        startScale, 1f))
                .with(ObjectAnimator.ofFloat(mZoomedImage,
                        "scaleY", startScale, 1f));
        set.setDuration(mShortAnimationDuration);
        set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCurrentAnimator = null;
            }
        });
        set.start();
        mCurrentAnimator = set;

        final float startScaleFinal = startScale;
        mZoomedImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCurrentAnimator != null) {
                    mCurrentAnimator.cancel();
                }

                fadeOut(mTitle);
                fadeOut(mScrim);

                AnimatorSet set = new AnimatorSet();
                set.play(ObjectAnimator
                        .ofFloat(mZoomedImage, "x", startBounds.left))
                        .with(ObjectAnimator
                                .ofFloat(mZoomedImage,
                                        "y", startBounds.top))
                        .with(ObjectAnimator
                                .ofFloat(mZoomedImage,
                                        "scaleX", startScaleFinal))
                        .with(ObjectAnimator
                                .ofFloat(mZoomedImage,
                                        "scaleY", startScaleFinal));
                set.setDuration(mShortAnimationDuration);
                set.setInterpolator(new DecelerateInterpolator());
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        endZoom(tag.thumbnail);
                        mListener.castStartFlipping(mQuery, FLIP_DELAY);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        endZoom(tag.thumbnail);
                    }
                });
                set.start();
                mCurrentAnimator = set;
            }
        });

        mListener.sendEvent("ui", "zoom");
    }

    @Override
    public boolean onBackPressed() {
        if (mZoomed)
            mZoomedImage.performClick();

        Log.d(TAG, "on back pressed - zoomed = %s", mZoomed);

        return mZoomed;
    }

    private void startZoom(final View v) {
        mZoomed = true;
        getActivity().supportInvalidateOptionsMenu();

        ViewHelper.setAlpha(mProgressBar, 0);
        mProgressBar.setVisibility(View.VISIBLE);
        fadeIn(mProgressBar);
    }

    private void endZoom(final View v) {
        if (v != null)
            ViewHelper.setAlpha(v, 1);

        mZoomedImage.setVisibility(View.GONE);
        mCurrentAnimator = null;
        mZoomed = false;
        getActivity().supportInvalidateOptionsMenu();
    }

    public static class Holder {
        ImageView thumbnail;
        View animated;
        Image image;
        Callback callback;
    }

    class Adapter extends QueryAdapter<Image> {

        final int mPadding;
        final int mSize;
        private final LayoutInflater mInflater;

        public Adapter(final Context context, final Query query) {
            super(context, query);
            mSize = context.getResources().getDimensionPixelSize(R.dimen.source_thumbnail);
            mPadding = context.getResources().getDimensionPixelSize(R.dimen.thumbnail_padding);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        protected View newView(final Context context, final Image item, final ViewGroup parent) {

            final View v = mInflater.inflate(getItemLayout(), parent, false);

            final Holder holder = new Holder();
            holder.thumbnail = (ImageView) v.findViewById(R.id.thumbnail);
            holder.animated = v.findViewById(R.id.animated);
            v.setTag(holder);

            return v;
        }

        @Override
        protected void bindView(final Context context, final Image item, final View view, final ViewGroup parent) {

            final Holder tag = (Holder) view.getTag();
            tag.image = item;
            tag.animated.setVisibility(View.GONE);
            tag.callback = new Callback() {
                @Override
                public void onSuccess() {
                    tag.animated.setVisibility(item.isAnimated() ? View.VISIBLE : View.GONE);
                }

                @Override
                public void onError() {
                }
            };

            bindItem(tag);

            tag.thumbnail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    zoom(tag);
                    mListener.castImage(tag.image);
                }
            });

        }

        @Override
        protected void onLoadComplete() {
            super.onLoadComplete();

            Log.d(TAG, "onLoadComplete(total=%s | count=%s | hasMode=%s)", getTotal(), getCount(), hasMore());

            if (!hasMore()) {
                final Bundle args = getArguments();
                if (args != null && args.containsKey("subreddit")) {
                    final Subreddit subreddit = (Subreddit) args.getSerializable("subreddit");
                    Log.d(TAG, "requesting more - %s", subreddit.getSubreddit());
                    ViewHelper.setAlpha(mProgressBar, 0);
                    mProgressBar.setVisibility(View.VISIBLE);
                    fadeIn(mProgressBar);
                    ScrapeService.scrapeSubreddit(getContext(), subreddit);
                    mListener.sendEvent("ui", "scrape more", subreddit.getSubreddit());
                }
            }

        }
    }

    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String action = intent.getAction();
            if (ScrapeService.ACTION_SCRAPE_COMPLETE.equals(action)) {
                fadeOut(mProgressBar);
                Log.d(TAG, "continue loading...");
                mThumbnailsAdapter.loadPage();
            }
        }
    };


}
