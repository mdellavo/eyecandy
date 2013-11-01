package org.quuux.eyecandy;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.app.Fragment;
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
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.quuux.orm.Database;
import org.quuux.orm.Query;
import org.quuux.orm.Session;
import org.quuux.orm.util.QueryAdapter;

public class GalleryFragment extends Fragment implements AbsListView.OnScrollListener, OnBackPressedListener {


    private static final String TAG = Log.buildTag(GalleryFragment.class);

    public static interface Listener {
        void showImage(Image i);
    }

    public static final int THUMB_SIZE = 100;

    private Listener mListener;

    private ViewGroup mContainer;
    private GridView mGridView;
    private ThumbnailAdapter mThumbnailsAdapter;

    private Picasso mPicasso;

    private Animator mCurrentAnimator;
    private int mShortAnimationDuration;
    private ImageView mZoomedImage;
    private boolean mZoomed;

    private TextView mTitle;
    private View mScrim;

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

        final Database db = EyeCandyDatabase.getInstance(context);
        final Session session = db.createSession();
        final Query query = session.query(Image.class);
        mThumbnailsAdapter = new ThumbnailAdapter(context, query);

    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

        final View rv = inflater.inflate(R.layout.gallery, container, false);

        mContainer = container;

        mGridView = (GridView)rv.findViewById(R.id.grid);
        mGridView.setAdapter(mThumbnailsAdapter);
        mGridView.setOnScrollListener(this);

        mZoomedImage = (ImageView)rv.findViewById(R.id.zoomed_image);

        mTitle = (TextView)rv.findViewById(R.id.title);
        mTitle.setAlpha(0);

        mScrim = rv.findViewById(R.id.scrim);

        return rv;
    }

    public static GalleryFragment newInstance() {
        final GalleryFragment rv = new GalleryFragment();

        return rv;
    }

    @Override
    public void onResume() {
        super.onResume();

        final Context context = getActivity();
        if (context != null) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(ScrapeService.ACTION_SCRAPE_COMPLETE);
            context.registerReceiver(mBroadcastReceiver, filter);
        }
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
            final Thumbnailholder tag = (Thumbnailholder) mZoomedImage.getTag();
            if (tag != null)
                mListener.showImage(tag.image);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onScrollStateChanged(final AbsListView absListView, final int i) {
        mThumbnailsAdapter.onScrollStateChanged(absListView, i);
    }

    @Override
    public void onScroll(final AbsListView absListView, final int i, final int i2, final int i3) {
        mThumbnailsAdapter.onScroll(absListView, i, i2, i3);
    }

    private AnimatorSet fade(final View v, final float start, final float end) {
        final AnimatorSet s = new AnimatorSet();

        s.play(ObjectAnimator.ofFloat(v, View.ALPHA, start, end));
        s.setDuration(mShortAnimationDuration);
        s.setInterpolator(new DecelerateInterpolator());

        s.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(final Animator animation) {
                super.onAnimationCancel(animation);
                v.setAlpha(end);

            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                v.setAlpha(end);
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

    private void zoom(final ImageView thumbView) {
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        final Thumbnailholder tag = (Thumbnailholder) thumbView.getTag();

        startZoom(thumbView);

        mTitle.setText(tag.image.getTitle());
        fadeIn(mTitle);
        mScrim.setVisibility(View.VISIBLE);

        final Rect startBounds = new Rect();
        final Rect finalBounds = new Rect();
        final Point globalOffset = new Point();

        thumbView.getGlobalVisibleRect(startBounds);

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


        thumbView.setAlpha(0f);

        mZoomedImage.setTag(tag);

        mZoomedImage.setVisibility(View.VISIBLE);

        mZoomedImage.setPivotX(0f);
        mZoomedImage.setPivotY(0f);

        final int finalWidth = finalBounds.width();
        final int finalHeight = finalBounds.height();

        mPicasso.load(tag.image.getUrl())
                .resize(finalWidth, finalHeight)
                .centerCrop()
                .placeholder(thumbView.getDrawable())
                .into(mZoomedImage);

        AnimatorSet set = new AnimatorSet();
        set
                .play(ObjectAnimator.ofFloat(mZoomedImage, View.X,
                        startBounds.left, finalBounds.left))
                .with(ObjectAnimator.ofFloat(mZoomedImage, View.Y,
                        startBounds.top, finalBounds.top))
                .with(ObjectAnimator.ofFloat(mZoomedImage, View.SCALE_X,
                        startScale, 1f))
                .with(ObjectAnimator.ofFloat(mZoomedImage,
                        View.SCALE_Y, startScale, 1f));
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
                mScrim.setVisibility(View.GONE);

                AnimatorSet set = new AnimatorSet();
                set.play(ObjectAnimator
                        .ofFloat(mZoomedImage, View.X, startBounds.left))
                        .with(ObjectAnimator
                                .ofFloat(mZoomedImage,
                                        View.Y, startBounds.top))
                        .with(ObjectAnimator
                                .ofFloat(mZoomedImage,
                                        View.SCALE_X, startScaleFinal))
                        .with(ObjectAnimator
                                .ofFloat(mZoomedImage,
                                        View.SCALE_Y, startScaleFinal));
                set.setDuration(mShortAnimationDuration);
                set.setInterpolator(new DecelerateInterpolator());
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        endZoom(thumbView);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        endZoom(thumbView);
                    }
                });
                set.start();
                mCurrentAnimator = set;
            }
        });
    }

    @Override
    public boolean onBackPressed() {
        if (mZoomed)
            mZoomedImage.performClick();

        return mZoomed;
    }

    private void startZoom(final View v) {
        mZoomed = true;
        getActivity().invalidateOptionsMenu();
    }

    private void endZoom(final View v) {
        getActivity().invalidateOptionsMenu();
        v.setAlpha(1f);
        mZoomedImage.setVisibility(View.GONE);
        mCurrentAnimator = null;
        mZoomed = false;
    }

    static class Thumbnailholder {
        Image image;
        Target callback;
    }

    class ThumbnailAdapter extends QueryAdapter<Image> {

        public ThumbnailAdapter(final Context context, final Query query) {
            super(context, query);
        }

        @Override
        protected View newView(final Context context, final Image item, final ViewGroup parent) {
            final ImageView v = new ImageView(context);
            v.setImageResource(R.drawable.placeholder);

            final int pix = (int)Utils.dp2pix(THUMB_SIZE, context);
            v.setLayoutParams(new GridView.LayoutParams(pix, pix));
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setPadding(8, 8, 8, 8);

            v.setTag(new Thumbnailholder());

            return v;
        }

        @Override
        protected void bindView(final Context context, final Image item, final View view, final ViewGroup parent) {

            Log.d(TAG, "binding item %s (%s)", item.getUrl(), item.getThumbnailUrl());

            Thumbnailholder tag = (Thumbnailholder) view.getTag();

            tag.image = item;

            final ImageView image = (ImageView)view;

            mPicasso.load(item.getThumbnailUrl())
                    .centerCrop()
                    .placeholder(R.drawable.placeholder)
                    .resize(THUMB_SIZE, THUMB_SIZE)
                    .into(image);

            image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    zoom((ImageView) view);
                }
            });
        }
    }


    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String action = intent.getAction();
            if (ScrapeService.ACTION_SCRAPE_COMPLETE.equals(action)) {
                mThumbnailsAdapter.notifyDataSetChanged();
            }

        }
    };


}
