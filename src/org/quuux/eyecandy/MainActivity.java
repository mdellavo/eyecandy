package org.quuux.eyecandy;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.quuux.eyecandy.utils.ViewServer;
import org.quuux.orm.Query;

public class MainActivity
        extends ActionBarActivity
        implements ActionBar.OnNavigationListener,
                   ViewerFragment.Listener,
                   RandomFragment.Listener,
                   GalleryFragment.Listener,
                   SourcesFragment.Listener, View.OnTouchListener {


    private static final String TAG = "MainActivity";

    private static final String FRAG_RANDOM = "random";
    private static final String FRAG_GALLERY = "gallery-%s";
    private static final String FRAG_VIEWER = "viewer-%s";
    private static final String FRAG_SOURCES = "subreddits";

    public static final int MODE_SLIDE_SHOW = 0;
    public static final int MODE_BURNS = 1;
    public static final int MODE_SOURCES = 2;
    public static final int MODE_GALLERY = 3;

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

        final ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this, R.array.modes, android.R.layout.simple_spinner_dropdown_item);
        actionBar.setListNavigationCallbacks(spinnerAdapter, this);

        actionBar.setSelectedNavigationItem(EyeCandyPreferences.getLastNavMode(this));

        setupSystemUi();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Toast.makeText(MainActivity.this, R.string.starting_scrape, Toast.LENGTH_SHORT).show();
                final Intent intent = new Intent(MainActivity.this, ScrapeService.class);
                startService(intent);
            }
       }, 500);

        ViewServer.get(this).addWindow(this);

        setLeanbackListener(findViewById(R.id.root));
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
    public void setLeanbackListener(final View v) {
        mGestureDetector = new GestureDetector(this, mGestureListener);
        v.setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(final View v, final MotionEvent event) {

        Log.d(TAG, "onTouch(view=%s | event=%s)", v, event);

        mGestureDetector.onTouchEvent(event);

        if (mLeanback) {
            mHandler.removeCallbacks(mLeanbackCallback);
            mHandler.postDelayed(mLeanbackCallback, 2500);
        }

        return false;
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
                    final boolean isAwake = (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0;

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
        final View v = getWindow().getDecorView();

        Log.d(TAG, "hide system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            v.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
            );
        }
    }

    @TargetApi(11)
    private void showSystemUi() {
        final View v = getWindow().getDecorView();

        Log.d(TAG, "show system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            v.setSystemUiVisibility(0);
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

    public void showGallery(final Query query, final boolean addToBackStack) {
        Fragment frag = getFrag(FRAG_GALLERY, query != null ? query.toSql().hashCode() : 0);
        if (frag == null)
            frag = GalleryFragment.newInstance(query);
        swapFrag(frag, FRAG_GALLERY, addToBackStack);
    }

    public void showGallery(final Query query) {
        showGallery(query, true);
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

    final GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        @Override
        public boolean onDown(final MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(final MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(final MotionEvent e) {
            supportInvalidateOptionsMenu();
            toggleLeanback();
            return false;
        }

        @Override
        public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(final MotionEvent e) {

        }

        @Override
        public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
            return false;
        }
    };

}
