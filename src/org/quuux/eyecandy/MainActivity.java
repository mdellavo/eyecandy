package org.quuux.eyecandy;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.quuux.eyecandy.utils.ViewServer;
import org.quuux.orm.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity
        extends ActionBarActivity
        implements ActionBar.OnNavigationListener,
                   ViewerFragment.Listener,
                   RandomFragment.Listener,
                   GalleryFragment.Listener,
                   SourcesFragment.Listener {

    private static final String TAG = "MainActivity";

    private static final String FRAG_RANDOM = "random";
    private static final String FRAG_GALLERY = "gallery-%s";
    private static final String FRAG_VIEWER = "viewer-%s";
    private static final String FRAG_SOURCES = "subreddits";

    public static final int MODE_SLIDE_SHOW = 0;
    public static final int MODE_SOURCES = 1;
    public static final int MODE_GALLERY = 2;

    public static final int MODE_BURNS = -1;

    final private Handler mHandler = new Handler();

    private GestureDetector mGestureDetector;
    private boolean mSquealch;
    private boolean mLeanback;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        setContentView(R.layout.base);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar));
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setDisplayShowTitleEnabled(false);

        final ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.modes,
                android.R.layout.simple_spinner_dropdown_item
        );

        actionBar.setListNavigationCallbacks(spinnerAdapter, this);

        actionBar.setSelectedNavigationItem(EyeCandyPreferences.getLastNavMode(this));

        mGestureDetector = new GestureDetector(this, mGestureListener);

        setupSystemUi();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final Intent intent = new Intent(MainActivity.this, ScrapeService.class);
                startService(intent);
            }
       }, 500);

        ViewServer.get(this).addWindow(this);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ViewServer.get(this).removeWindow(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ViewServer.get(this).setFocusedWindow(this);

        if(mLeanback)
            startLeanback();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ScrapeService.ACTION_SCRAPE_COMPLETE);
        registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void onBackPressed() {

        final Fragment frag = getCurrentFragment();
        if (frag != null &&
                frag instanceof OnBackPressedListener &&
                ((OnBackPressedListener) frag).onBackPressed()) {
          return;
        }

        super.onBackPressed();
    }

    private final Runnable mLeanbackCallback = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "resuming leanback...");
            startLeanback();
        }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (mLeanback)
                startLeanback();
            else
                exitLeanback();
        }
    }

    @Override
    public void onLeanbackTouch(final MotionEvent event) {
        // FIXME should just drive this from main with an onleanback listener
        mGestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onNavigationItemSelected(final int position, final long id) {

        if (mSquealch) {
            Log.d(TAG, "squealching nav item selection");
            mSquealch = false;
            return true;
        }

        switch (position) {
            case MODE_SLIDE_SHOW:
                if (!isFragCurrently(FRAG_VIEWER))
                    onShowImage();
                break;

            case MODE_BURNS:
                if (!isFragCurrently(FRAG_RANDOM))
                    onShowRandom();
                break;

            case MODE_SOURCES:
                if (!isFragCurrently(FRAG_SOURCES))
                    onShowSources();
                break;

            case MODE_GALLERY:
                if (!isFragCurrently(FRAG_GALLERY))
                    onShowGallery();
                break;
        }

        EyeCandyPreferences.setLastNavMode(this, position);

        return true;
    }

    private boolean isFragCurrently(final String tag) {
        final Fragment frag = getCurrentFragment();
        return frag != null && tag.equals(frag.getTag());
    }

    @TargetApi(11)
    private void setupSystemUi() {
        final View v = getWindow().getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            v.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(final int visibility) {
                    final boolean isAwake = (visibility & View.SYSTEM_UI_FLAG_IMMERSIVE) == 0;

                    Log.d(TAG, "system ui visibility change: isAwake=%s", isAwake);

                    if (isAwake) {
                        getSupportActionBar().show();
                        mLeanback = false;
                    } else {
                        getSupportActionBar().hide();
                    }


                }
            });
        }
    }

    @TargetApi(11)
    private void hideSystemUi() {
        Log.d(TAG, "hide system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
            );
        }
    }

    @TargetApi(11)
    private void showSystemUi() {
        Log.d(TAG, "show system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    @TargetApi(11)
    private void restoreSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    public void startLeanback() {
        Log.d(TAG, "start leanback");
        mLeanback = true;
        getSupportActionBar().hide();
        hideSystemUi();
    }

    public void endLeanback() {
        Log.d(TAG, "end leanback");
        mLeanback = false;
        mHandler.removeCallbacks(mLeanbackCallback);
        getSupportActionBar().show();
        showSystemUi();
    }

    public void exitLeanback() {
        Log.d(TAG, "exiting leanback");
        endLeanback();
        restoreSystemUi();
    }

    public boolean isLeanback() {
        return mLeanback;
    }

    public void toggleLeanback() {
        if (mLeanback)
            endLeanback();
        else
            startLeanback();
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.root);
    }

    private void swapFrag(final Fragment fragment, final String tag, final boolean addToBackStack) {
        restoreSystemUi();
        final FragmentManager frags = getSupportFragmentManager();
        final FragmentTransaction ft = frags.beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_ENTER_MASK);
        //ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        ft.replace(R.id.root, fragment, tag);
        if (addToBackStack)
            ft.addToBackStack(null);
        ft.commit();
    }

    private void swapFrag(final Fragment fragment, final String tag) {
        swapFrag(fragment, tag, false);
    }

    private Fragment getFrag(final String tag, Object... args) {
        final FragmentManager fm = getSupportFragmentManager();
        return fm.findFragmentByTag(String.format(tag, args));
    }

    private void onShowRandom() {
        Fragment frag = getFrag(FRAG_RANDOM);
        if (frag == null)
            frag = RandomFragment.newInstance();
        swapFrag(frag, FRAG_RANDOM, false);
    }


    public void showImage(final Query query, final int position, final boolean addToBackStack) {
        Fragment frag = getFrag(FRAG_VIEWER, query.toSql().hashCode());
        if (frag == null)
            frag = ViewerFragment.newInstance(query, position);
        swapFrag(frag, FRAG_VIEWER, addToBackStack);
    }


    public void showImage(final Query query, final int position) {
        showImage(query, position, true);
    }

    public void showImage(final Query query) {
        showImage(query, 0);
    }

    private void onShowImage() {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).orderBy("timesShown, RANDOM()");
        showImage(q, 0, false);
    }

    public void showImage(final Subreddit subreddit) {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).filter("subreddit=?", subreddit.getSubreddit()).orderBy("id DESC");
        showImage(q, 0);
    }


    public void showGallery(final Query query, final boolean addToBackStack) {
        Fragment frag = getFrag(FRAG_GALLERY, query != null ? query.toSql().hashCode() : 0);
        if (frag == null)
            frag = GalleryFragment.newInstance(query);
        swapFrag(frag, FRAG_GALLERY, addToBackStack);
    }

    public void showGallery(final Query query) {
        showGallery(query, true);
    }

    public void showGallery(final Subreddit subreddit) {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).filter("subreddit=?", subreddit.getSubreddit()).orderBy("id DESC");
        showGallery(q);
    }

    private void onShowGallery() {
        showGallery(null, false);
    }

    private void onShowSources() {
        Fragment frag = getFrag(FRAG_SOURCES);
        if (frag == null)
            frag = SourcesFragment.newInstance();
        swapFrag(frag, FRAG_SOURCES, false);
    }

    public void setSelectedNavigationItemSilent(final int pos) {
        final ActionBar ab = getSupportActionBar();
        if (ab == null)
            return;

        ab.setSelectedNavigationItem(pos);
    }

    public void openImage(final Image image) {
        final OpenImageDialog dialog = OpenImageDialog.newInstance(image);
        dialog.show(getSupportFragmentManager(), String.format("open-image-%s", image.getId()));
    }

    static class ImageOpenerAdapter extends ArrayAdapter<ResolveInfo> {

        public ImageOpenerAdapter(final Context context, final Image image) {
            super(context, 0);
            init(context, image);
        }

        private void init(final Context context, final Image image) {
            final PackageManager manager = context.getPackageManager();

            final Intent viewIntent = buildViewIntent(image);
            final List<ResolveInfo> viewActivites = queryActvities(manager, viewIntent);
            for (final ResolveInfo i : viewActivites) {
                Log.d(TAG, "adding match - %s", i);
                add(i);
            }

            final Intent shareIntent = buildShareIntent(image);
            final List<ResolveInfo> shareActivites = queryActvities(manager, shareIntent);
            for (final ResolveInfo i : shareActivites) {
                Log.d(TAG, "adding match - %s", i);
                add(i);
            }

        }

        private Intent buildShareIntent(final Image image) {
            final Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            final String text = String.format("%s - %s", image.getTitle(), image.getUrl());
            intent.putExtra(Intent.EXTRA_TEXT, text);
            intent.setType("text/plain");
            return intent;
        }

        private Intent buildViewIntent(final Image image) {
            final Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(image.getUrl()));
            return intent;
        }


        private List<ResolveInfo> queryActvities(final PackageManager manager, final Intent intent) {
            return manager.queryIntentActivities(
                    intent,
                    PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER
            );
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {

            final PackageManager pm = getContext().getPackageManager();
            final ResolveInfo info = getItem(position);

            View v = convertView;
            if (v == null) {
                final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.open_image_item, parent, false);
            }

            final TextView header = (TextView)v.findViewById(R.id.header);

            final ResolveInfo prev = (position > 1 && position <= getCount() - 1) ? getItem(position - 1) : null;

            if (position == 0) {
                header.setVisibility(View.VISIBLE);
                header.setText(R.string.open_with);
            } else if (prev != null && prev.filter.hasAction(Intent.ACTION_VIEW) && info.filter.hasAction(Intent.ACTION_SEND)) {
                header.setVisibility(View.VISIBLE);
                header.setText(R.string.share_with);
            } else {
                header.setVisibility(View.GONE);
            }

            final ImageView icon = (ImageView) v.findViewById(R.id.icon);
            icon.setImageDrawable(info.loadIcon(pm));

            final TextView label = (TextView) v.findViewById(R.id.label);
            label.setText(info.loadLabel(pm));

            return v;
        }
    }

    public static class OpenImageDialog extends DialogFragment {
        public OpenImageDialog() {
            super();
        }

        public static OpenImageDialog newInstance(final Image image) {
            final OpenImageDialog rv = new OpenImageDialog();
            final Bundle args = new Bundle();
            args.putSerializable("image", image);
            rv.setArguments(args);
            return rv;
        }

        private Image getImage() {
            return (Image) getArguments().getSerializable("image");
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {

            final Activity act = getActivity();

            final AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(act, android.R.style.Theme_Holo_Light));
            builder.setTitle(R.string.open_dialog_title);

            final ImageOpenerAdapter adapter = new ImageOpenerAdapter(act, getImage());

            builder.setAdapter(
                    adapter,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            final ResolveInfo info = adapter.getItem(which);

                            final Intent i;
                            if (info.filter.hasAction(Intent.ACTION_VIEW)) {
                                i = adapter.buildViewIntent(getImage());
                            } else if (info.filter.hasAction(Intent.ACTION_SEND)) {
                                i = adapter.buildShareIntent(getImage());
                            } else {
                                i = null;
                                return;
                            }

                            i.setClassName(info.activityInfo.applicationInfo.packageName, info.activityInfo.name);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                            startActivity(i);
                        }
                    });

            return builder.create();
        }
    }

    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String action = intent.getAction();

            if (ScrapeService.ACTION_SCRAPE_COMPLETE.equals(action)) {
                final int taskCount = intent.getIntExtra(ScrapeService.EXTRA_TASK_COUNT, -1);
                Log.d(TAG, "scrape complete, task count = %s", taskCount);
            }

        }
    };

    final Runnable mTapCallback = new Runnable() {
        @Override
        public void run() {

        }
    };

    final GestureDetector.OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onSingleTapUp(final MotionEvent e) {
            supportInvalidateOptionsMenu();
            toggleLeanback();

            if (mLeanback) {
                mHandler.removeCallbacks(mLeanbackCallback);
                mHandler.postDelayed(mLeanbackCallback, 2500);
            }

            return false;
        }

    };

}
