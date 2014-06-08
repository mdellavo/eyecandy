package org.quuux.eyecandy;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.text.Html;
import android.view.*;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.vending.billing.IInAppBillingService;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.nineoldandroids.view.*;
import com.nineoldandroids.view.ViewPropertyAnimator;

import org.quuux.eyecandy.utils.ViewServer;
import org.quuux.orm.FetchListener;
import org.quuux.orm.Query;
import org.quuux.orm.ScalarListener;
import org.quuux.orm.Session;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity
        extends ActionBarActivity
        implements ActionBar.OnNavigationListener,
                   ViewerFragment.Listener,
                   RandomFragment.Listener,
                   GalleryFragment.Listener,
                   SourcesFragment.Listener,
                   SetupFragment.Listener {

    private static final String TAG = "MainActivity";

    private static final String ACTION_PURCHASE = "org.quuux.eyecandy.action.PURCHASE";

    private static final String FRAG_RANDOM = "random";
    private static final String FRAG_GALLERY = "gallery";
    private static final String FRAG_VIEWER = "viewer";
    private static final String FRAG_SOURCES = "sources";
    private static final String FRAG_FEED = "feed";
    private static final String FRAG_SETUP = "setup";

    public static final int MODE_SLIDE_SHOW = 0;
    public static final int MODE_SOURCES = 1;
    public static final int MODE_GALLERY = 2;
    public static final int MODE_FEED = 3;
    public static final int MODE_SETUP = 4;

    public static final int MODE_BURNS = -1;
    private static final String SKU_UNLOCK = "unlock";
    private static final int FLIP_DELAY = 15 * 1000;
    private static final boolean CAST_ENABLED = false;

    final private Handler mHandler = new Handler();

    private IInAppBillingService mService;

    private AdView mAdView;

    private GestureDetector mGestureDetector;
    private boolean mSquealch;
    private boolean mLeanback;

    private static boolean sNagShown;
    private Set<String> mPurchases = Collections.<String>emptySet();

    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private CastDevice mSelectedDevice;
    private GoogleApiClient mApiClient;
    private RemoteMediaPlayer mRemoteMediaPlayer;
    private boolean mWaitingForReconnect;
    private boolean mCasting;
    private Tracker mTracker;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND"),
                mServiceConn, Context.BIND_AUTO_CREATE);

        if (findViewById(android.R.id.content) == null)
            getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        setContentView(R.layout.base);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar));
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setDisplayShowTitleEnabled(false);

        final ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                getSupportActionBar().getThemedContext(),
                R.array.modes,
                R.layout.support_simple_spinner_dropdown_item
        );
        spinnerAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        actionBar.setListNavigationCallbacks(spinnerAdapter, this);

        int mode;
        if (EyeCandyPreferences.isFirstRun(this)) {
            mode = MODE_SETUP;
            onFirstRun();

        } else {
            mode = MODE_SOURCES;
            checkRefresh();
            // FIXME refresh
        }

        actionBar.setSelectedNavigationItem(mode);

        mAdView = (AdView) findViewById(R.id.ad);
        mAdView.setAdListener(mAdListener);

        mGestureDetector = new GestureDetector(this, mGestureListener);

        setupSystemUi();

        if (BuildConfig.DEBUG)
            ViewServer.get(this).addWindow(this);

        mPurchases = EyeCandyPreferences.getPurchases(this);
        onPurchasesUpdated();

        if (CAST_ENABLED) {
            mMediaRouter = MediaRouter.getInstance(getApplicationContext());
            mMediaRouteSelector = new MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(getString(R.string.cast_application_id)))
                    .build();
        }

    }

    private void checkRefresh() {
        ScrapeService.scrapeSubreddit(this, null);
    }

    private void onFirstRun() {
        sendEvent("ui", "first run");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (BuildConfig.DEBUG)
            ViewServer.get(this).removeWindow(this);

        if (mServiceConn != null) {
            unbindService(mServiceConn);
        }

        mAdView.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BuildConfig.DEBUG)
            ViewServer.get(this).setFocusedWindow(this);

        if(mLeanback)
            startLeanback();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PURCHASE);
        registerReceiver(mBroadcastReceiver, filter);

        mAdView.resume();

        checkPlayServices();

        if (CAST_ENABLED) {
            mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                    MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
        }

    }

    private void checkPlayServices() {
        final int result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            final Dialog dialog = GooglePlayServicesUtil.getErrorDialog(result, this, 0);
            dialog.show();
        }
    }

    @Override
    protected void onPause() {
        if (CAST_ENABLED)
            mMediaRouter.removeCallback(mMediaRouterCallback);
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);
        mAdView.pause();

        if (CAST_ENABLED)
            castTeardown();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onBackPressed() {

        final Fragment frag = getCurrentFragment();
        if (frag != null &&
                frag instanceof OnBackPressedListener &&
                ((OnBackPressedListener) frag).onBackPressed()) {
          return;
        }

        sNagShown = false;

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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001) {
            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

            if (resultCode == RESULT_OK) {
                onPurchaseComplete();
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.cast, menu);

        final MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        final MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        if (mMediaRouteSelector != null)
            mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);

        getMenuInflater().inflate(R.menu.nag, menu);

        getMenuInflater().inflate(R.menu.main, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        boolean rv;
        switch (item.getItemId()) {

            case R.id.settings:
                showSettings();
                rv = true;
                break;

            case R.id.unlock:
                showNag(true);
                rv = true;
                break;

            case R.id.about:
                showAbout();
                rv = true;
                break;

            default:
                rv = super.onOptionsItemSelected(item);
                break;
        }

        return rv;
    }

    private void showSettings() {
        final Intent intent = new Intent(this, EyeCandyPreferences.class);
        startActivity(intent);
    }

    private void onPurchaseComplete() {
        mPurchases.add(SKU_UNLOCK);
        EyeCandyPreferences.setPurchases(this, mPurchases);
        onPurchasesUpdated();
        sendEvent("ui", "purchase complete");
    }

    @Override
    public boolean onLeanbackTouch(final MotionEvent event) {
        // FIXME should just drive this from main with an onleanback listener
        return mGestureDetector.onTouchEvent(event);
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

            case MODE_FEED:
                if (!isFragCurrently(FRAG_FEED))
                    onShowFeed();
                break;

            case MODE_SETUP:
                onShowSetup();
                break;
        }

        EyeCandyPreferences.setLastNavMode(this, position);

        return true;
    }

    private boolean isFragCurrently(final String tag) {
        final Fragment frag = getCurrentFragment();
        return frag != null && frag.getTag().startsWith(tag);
    }

    @TargetApi(11)
    private void setupSystemUi() {
        final View v = getWindow().getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            v.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(final int visibility) {
                    final boolean isAwake = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;

                    Log.d(TAG, "system ui visibility change: isAwake=%s", isAwake);

                    if (isAwake) {
                        getSupportActionBar().show();
                    } else {
                        getSupportActionBar().hide();
                    }

                    slideAd();

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
        sendEvent("ui", "start leanback");
    }

    public void endLeanback() {
        Log.d(TAG, "end leanback");
        mLeanback = false;
        mHandler.removeCallbacks(mLeanbackCallback);
        getSupportActionBar().show();
        showSystemUi();
        sendEvent("ui", "end leanback");
    }

    public void exitLeanback() {
        Log.d(TAG, "exiting leanback");
        endLeanback();
        restoreSystemUi();
        slideAd();
        sendEvent("ui", "exit leanback");
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

    @TargetApi(11)
    private void slideAd() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {

            final int visibility = getWindow().getDecorView().getSystemUiVisibility();
            final boolean isFloating =
                    (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION & visibility) != 0 &&
                            (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION & visibility) == 0;

            final int duration = getResources().getInteger(android.R.integer.config_shortAnimTime);
            final int translationY = (int) ViewHelper.getTranslationY(mAdView);
            final int dy = getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height);

            Log.d(TAG, "slide: leanback=%s | isFloating=%s | translationY=%s | dy=%s", mLeanback, isFloating, translationY, dy);

            final int px;
            if (!isFloating) {
                px = 0;
            } else {
                px = -dy;
            }

            ViewPropertyAnimator.animate(mAdView).translationY(px).setDuration(duration).start();
        }
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.root);
    }

    private void swapFrag(final Fragment fragment, final String tag, final boolean addToBackStack) {
        if (mLeanback)
            exitLeanback();

        Log.d(TAG, "swapFrag(%s, %s, %s)", fragment, tag, addToBackStack);

        final FragmentManager frags = getSupportFragmentManager();
        if (!addToBackStack)
            frags.popBackStack();
        final FragmentTransaction ft = frags.beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_ENTER_MASK);
        //ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        ft.replace(R.id.root, fragment, tag);
        if (addToBackStack)
            ft.addToBackStack(null);
        ft.commit();

        Tracker t = EyeCandyTracker.get(this).getTracker();
        t.setScreenName(tag);
        t.send(new HitBuilders.AppViewBuilder().build());
    }

    private void swapFrag(final Fragment fragment, final String tag) {
        swapFrag(fragment, tag, false);
    }

    private Fragment getFrag(final String tag) {
        final FragmentManager fm = getSupportFragmentManager();
        return fm.findFragmentByTag(tag);
    }

    private void onShowRandom() {
        Fragment frag = getFrag(FRAG_RANDOM);
        if (frag == null)
            frag = RandomFragment.newInstance();
        swapFrag(frag, FRAG_RANDOM, false);
        sendEvent("ui", "show random");
    }

    public void showImage(final Query query, final int position, final Subreddit subreddit,  boolean addToBackStack) {

        final String tag = subreddit != null ? FRAG_VIEWER + "-" + subreddit.getSubreddit() : FRAG_VIEWER;

        Fragment frag = getFrag(tag);
        if (frag == null)
            frag = ViewerFragment.newInstance(query, position, subreddit);

        swapFrag(frag, tag, addToBackStack);
        sendEvent("ui", "show viewer", subreddit != null ? subreddit.getSubreddit() : null);
    }

    private void onShowSetup() {
        Fragment frag = getFrag(FRAG_SETUP);
        if (frag == null)
            frag = SetupFragment.newInstance();
        swapFrag(frag, FRAG_SETUP, false);
        sendEvent("ui", "show setup");
    }

    public void showImage(final Query query, final int position, final Subreddit subreddit) {
        showImage(query, position, subreddit, true);
    }

    public void showImage(final Query query) {
        showImage(query, 0, null);
    }

    @Override
    public void showImage(final Query query, final int position) {
        showImage(query, position, null);
    }

    private void onShowImage() {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).orderBy("created");
        showImage(q, 0, null, false);
    }

    public void showImage(final Subreddit subreddit) {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).filter("subreddit=?", subreddit.getSubreddit()).orderBy("\"id\" ASC");
        showImage(q, 0, subreddit);
    }

    public void showGallery(final Query query, final Subreddit subreddit, final boolean addToBackStack) {
        final String tag = subreddit != null ? FRAG_GALLERY + "-" + subreddit.getSubreddit() : FRAG_GALLERY;

        Fragment frag = getFrag(tag);
        if (frag == null) {
            frag = GalleryFragment.newInstance(query, subreddit);
        }

        swapFrag(frag, tag, addToBackStack);
        sendEvent("ui", "show gallery", subreddit != null ? subreddit.getSubreddit() : null);
    }

    public void showGallery( final Query query, final Subreddit subreddit) {
        showGallery(query, subreddit, true);
    }

    public void showGallery(final Subreddit subreddit) {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).filter("subreddit=?", subreddit.getSubreddit()).orderBy("\"id\" ASC");
        showGallery(q, subreddit);
    }

    private void onShowGallery() {
        showGallery(null, null, false);
    }

    public void showFeed(final Query query, final Subreddit subreddit, final boolean addToBackStack) {
        final String tag = subreddit != null ? FRAG_FEED + "-" + subreddit.getSubreddit() : FRAG_FEED;
        Fragment frag = getFrag(tag);
        if (frag == null) {
            frag = FeedFragment.newInstance(query, subreddit);
        }

        swapFrag(frag, tag, addToBackStack);
        sendEvent("ui", "show feed", subreddit != null ? subreddit.getSubreddit() : null);
    }

    public void showFeed(final Subreddit subreddit) {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).filter("subreddit=?", subreddit.getSubreddit()).orderBy("\"id\" ASC");
        showFeed(q, subreddit, true);
    }

    private void onShowFeed() {
        showFeed(null, null, false);
    }

    private void onShowSources() {
        Fragment frag = getFrag(FRAG_SOURCES);
        if (frag == null)
            frag = SourcesFragment.newInstance();
        swapFrag(frag, FRAG_SOURCES, false);

        sendEvent("ui", "show sources");
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
        sendEvent("ui", "open image");
    }

    private void onPurchaseResult(final Set<String> purchases) {
        if (purchases == null)
            return;

        mPurchases = purchases;
        EyeCandyPreferences.setPurchases(this, purchases);
        onPurchasesUpdated();

    }

    private void onPurchasesUpdated() {
        final boolean unlocked = mPurchases.contains(SKU_UNLOCK);

        if (unlocked) {
            hideAds();
            hideNag();
        } else {
            showNag();
            loadAds();
        }

        supportInvalidateOptionsMenu();
    }

    private void hideNag() {
        final FragmentManager fm = getSupportFragmentManager();
        final NagDialog nag = (NagDialog) fm.findFragmentByTag("nag");
        if (nag != null) {
            nag.dismiss();
        }

    }

    private void hideAds() {
        mAdView.setVisibility(View.GONE);
        mAdView.destroy();
    }

    private void loadAds() {
        AdRequest.Builder builder = new AdRequest.Builder();
        if (BuildConfig.DEBUG) {
            builder.addTestDevice(getString(R.string.test_device_id));
        }

        AdRequest adRequest = builder.build();
        mAdView.loadAd(adRequest);
        mAdView.setVisibility(View.VISIBLE);
    }

    private void showNag(final boolean forced) {
        if (!sNagShown || forced) {
            final FragmentManager fm = getSupportFragmentManager();
            if (fm.findFragmentByTag("nag") == null) {
                final NagDialog dialog = NagDialog.newInstance();
                dialog.show(getSupportFragmentManager(), "nag");
            }

            sendEvent("ui", "show nag");

            sNagShown = true;
        }
    }

    private void showNag() {
        showNag(false);
    }

    private void showAbout() {
        final FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag("about") == null) {
            final WebViewDialog dialog = WebViewDialog.newInstance(R.string.about, "file:///android_asset/about.html");
            dialog.show(getSupportFragmentManager(), "about");
        }

        sendEvent("ui", "show about");
    }

    @Override
    public void onSetupComplete() {
        EyeCandyPreferences.markFirstRun(this);
        onShowSources();
        sendEvent("ui", "setup complete");
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


    public void sendEvent(final String category, final String action, final String label) {
        EyeCandyTracker.get(this).sendEvent(category, action, label);
    }

    public void sendEvent(final String category, final String action) {
        sendEvent(category, action, null);
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

            if (ACTION_PURCHASE.equals(action)) {
                startPurchase();
            }

        }
    };

    private void startPurchase() {

        if (mService == null)
            return;

        final Bundle response;
        try {
            response = mService.getBuyIntent(3, getPackageName(), SKU_UNLOCK, "inapp", null);
        } catch (final RemoteException e) {
            Log.e(TAG, "error starting purchase", e);
            return;
        }

        final int responseCode = response.getInt("RESPONSE_CODE");
        if (responseCode != 0) {
            Log.d(TAG, "error starting purchase: %s", response);
            return;
        }

        final PendingIntent pendingIntent = response.getParcelable("BUY_INTENT");
        try {
            startIntentSenderForResult(pendingIntent.getIntentSender(),
                    1001, new Intent(), Integer.valueOf(0), Integer.valueOf(0),
                    Integer.valueOf(0));
        } catch (final IntentSender.SendIntentException e) {
            Log.e(TAG, "error starting purchase: %s", e);
        }

        sendEvent("ui", "start purchase");
    }

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

    private AdListener mAdListener = new AdListener() {
        @Override
        public void onAdClosed() {
            super.onAdClosed();
            Log.d(TAG, "ad closed");
            sendEvent("ui", "ad closed");
        }

        @Override
        public void onAdFailedToLoad(final int errorCode) {
            super.onAdFailedToLoad(errorCode);
            Log.d(TAG, "ad failed to load: %s", errorCode);
            sendEvent("ui", "ad failed to load");
        }

        @Override
        public void onAdLeftApplication() {
            super.onAdLeftApplication();
            Log.d(TAG, "ad left application");
            sendEvent("ui", "ad left application");
        }

        @Override
        public void onAdOpened() {
            super.onAdOpened();
            Log.d(TAG, "ad opened");
            sendEvent("ui", "ad opened");
        }

        @Override
        public void onAdLoaded() {
            super.onAdLoaded();
            Log.d(TAG, "ad loaded");
            sendEvent("ui", "ad loaded");
        }
    };

    public static class WebViewDialog extends DialogFragment {
        public WebViewDialog() {
            super();
        }

        public static WebViewDialog newInstance(final int titleResId, final String url) {
            final WebViewDialog rv = new WebViewDialog();
            final Bundle args = new Bundle();
            args.putInt("title", titleResId);
            args.putString("url", url);
            rv.setArguments(args);
            return rv;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Bundle args = getArguments();
            final int title = args.getInt("title");
            final String url = args.getString("url");

            final WebView view = new WebView(getActivity());
            view.setBackgroundColor(0x00000000);
            view.loadUrl(url);

            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            final Dialog dialog = builder.setTitle(title)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, null)
                    .setView(view)
                    .create();

            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            return dialog;
        }
    }

    public static class NagDialog extends DialogFragment {

        public NagDialog() {
            super();
        }

        public static NagDialog newInstance() {
            final NagDialog rv = new NagDialog();
            return rv;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Context context = getActivity();
            if (context == null)
                return null;

            final View v = getActivity().getLayoutInflater().inflate(R.layout.nag, null);
            if (v == null)
                return null;

            final TextView nagText = (TextView) v.findViewById(R.id.nag_text);
            nagText.setText(Html.fromHtml(getString(R.string.nag_text)));

            final Button button = (Button) v.findViewById(R.id.purchase_button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    context.sendBroadcast(new Intent(ACTION_PURCHASE));
                }
            });

            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            final Dialog dialog = builder.setTitle(R.string.nag_title)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, null)
                    .setView(v)
                    .create();

            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            return dialog;
        }
    }

    final ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
            initBilling();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

    };

    private void initBilling() {
        new QueryPurchasesTask().execute();
    }

    class QueryPurchasesTask extends AsyncTask<String, Void, Set<String>> {

        @Override
        protected Set<String> doInBackground(final String... params) {
            final Bundle purchases;
            try {
                purchases = mService.getPurchases(3, getPackageName(), "inapp", null);
            } catch (RemoteException e) {
                e.printStackTrace();
                return null;
            }

            final int response = purchases.getInt("RESPONSE_CODE");
            if (response != 0) {
                Log.e(TAG, "Error querying purchases: %s", purchases);
                return null;
            }

            final List<String> purchasedSkus = purchases.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
            final Set<String> rv = new HashSet<String>();
            rv.addAll(purchasedSkus);
            return rv;
        }

        @Override
        protected void onPostExecute(final Set<String> purchases) {
            onPurchaseResult(purchases);
        }
    }

    class CastFlipper {

        final Query mQuery;
        final Session mSession;
        int mDelay;
        boolean mFlipping;
        int mConsumed;
        private long mCount;

        CastFlipper(final Context context, final Query query) {
            mQuery = query;
            mSession = EyeCandyDatabase.getSession(context.getApplicationContext());
        }

        void setDelay(int delay) {
            mDelay = delay;
        }

        void startFlipping() {
            mFlipping = true;

            mQuery.count(new ScalarListener<Long>() {
                @Override
                public void onResult(final Long count) {
                    if (count == 0)
                        return;

                    mCount = count;

                    flipImage();
                }
            });
        }

        void stopFlipping() {
            mFlipping = false;
            mHandler.removeCallbacks(mCallback);
        }

        void flipImage() {
            mQuery.offset((int) (mConsumed % mCount)).limit(1).first(new FetchListener<Image>() {
                @Override
                public void onResult(final Image image) {
                    castImage(image);
                    mConsumed++;

                    if (mFlipping) {
                        mHandler.removeCallbacks(mCallback);
                        mHandler.postDelayed(mCallback, mDelay);
                    }

                }
            });
        }

        final Runnable mCallback = new Runnable() {
            @Override
            public void run() {
                flipImage();
            }
        };
    }

    CastFlipper mCastFlipper;

    public void castStartFlipping(final Query query, final int delay) {

        if (mCastFlipper != null)
            castStopFlipping(false);

        mCastFlipper = new CastFlipper(this, query);
        mCastFlipper.setDelay(delay);
        mCastFlipper.startFlipping();
    }

    public void castStopFlipping(final boolean goIdle) {
        if (mCastFlipper != null) {
            mCastFlipper.stopFlipping();
            mCastFlipper = null;

            if (goIdle)
                castIdle();
        }
    }

    public void castStopFlipping() {
        castStopFlipping(true);
    }

    void castInit() {
        Log.d(TAG, "Cast Init!!!");

        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                .builder(mSelectedDevice, mCastClientListener)
                .setVerboseLoggingEnabled(BuildConfig.DEBUG);

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(mConnectionCallbacks)
                .addOnConnectionFailedListener(mConnectionFailedListener)
                .build();

        mApiClient.connect();
        mRemoteMediaPlayer = new RemoteMediaPlayer();
    }

    void castTeardown() {
        if (mApiClient != null) {
            if (mApiClient.isConnected()) {
                Cast.CastApi.stopApplication(mApiClient);
                mApiClient.disconnect();
            }
            mApiClient = null;
        }
        mSelectedDevice = null;
        mWaitingForReconnect = false;
        mCasting = false;
        mRemoteMediaPlayer = null;
    }

    public void castImage(final Image image) {

        if (image == null)
            return;

        if (!mCasting) {
            Log.d(TAG, "Not casting image %s", image);
            return;
        }

        Log.d(TAG, "casting image %s...", image.getUrl());

        final MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, image.getTitle());
        final MediaInfo mediaInfo = new MediaInfo.Builder(image.getUrl())
                .setContentType(image.getUrl().endsWith(".gif") ? "image/gif" : "image/jpeg")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();

        mRemoteMediaPlayer.load(mApiClient, mediaInfo, true)
                .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        if (result.getStatus().isSuccess()) {
                            Log.d(TAG, "%s loaded successfully", image.getUrl());
                        }
                    }
                });
    }

    void castIdle() {
        final Query q = EyeCandyDatabase.getSession(this).query(Image.class).orderBy("RANDOM()");
        castStartFlipping(q, FLIP_DELAY);
    }

    final GoogleApiClient.ConnectionCallbacks mConnectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {

        @Override
        public void onConnected(final Bundle bundle) {
            Log.d(TAG, "onConnected(bundle=%s)", bundle);

            if (mWaitingForReconnect) {
                mWaitingForReconnect = false;
                castIdle();
            } else {


                try {
                    Cast.CastApi.launchApplication(mApiClient, getString(R.string.cast_application_id), false)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(final Cast.ApplicationConnectionResult result) {

                                            Log.d(TAG, "onApplicationConnectionResult = %s", result);

                                            final Status status = result.getStatus();
                                            if (status.isSuccess()) {

                                                final ApplicationMetadata applicationMetadata =
                                                        result.getApplicationMetadata();
                                                final String sessionId = result.getSessionId();
                                                final String applicationStatus = result.getApplicationStatus();
                                                final boolean wasLaunched = result.getWasLaunched();

                                                mCasting = true;

                                                castIdle();

                                            } else {
                                                castTeardown();
                                            }
                                        }
                                    });

                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch application", e);
                }
            }
        }

        @Override
        public void onConnectionSuspended(final int cause) {
            Log.d(TAG, "onConnectionSuspended(cause=%s)", cause);
            mWaitingForReconnect = true;
        }
    };

    final GoogleApiClient.OnConnectionFailedListener mConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(final ConnectionResult connectionResult) {
            Log.d(TAG, "onConnectionFailed(connectionResult=%s)", connectionResult);
            castTeardown();
        }
    };

    final Cast.Listener mCastClientListener = new Cast.Listener() {
        @Override
        public void onApplicationStatusChanged() {
            super.onApplicationStatusChanged();
            Log.d(TAG, "onApplicationStatusChanged()");
        }

        @Override
        public void onApplicationDisconnected(final int statusCode) {
            super.onApplicationDisconnected(statusCode);
            Log.d(TAG, "onApplicationDisconnected(statusCode=%s)", statusCode);
            mCasting = false;
        }

        @Override
        public void onVolumeChanged() {
            super.onVolumeChanged();
            Log.d(TAG, "onVolumeChanged()");
        }
    };

    final MediaRouter.Callback mMediaRouterCallback = new MediaRouter.Callback() {

        @Override
        public void onRouteSelected(final MediaRouter router, final MediaRouter.RouteInfo info) {
            Log.d(TAG, "onRouteSelected(router=%s, info=%s)", router, info);

            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());
            castInit();
        }

        @Override
        public void onRouteUnselected(final MediaRouter router, final MediaRouter.RouteInfo info) {
            Log.d(TAG, "onRouteUnselected(router=%s, info=%s)", router, info);
            castTeardown();
        }
    };

}
