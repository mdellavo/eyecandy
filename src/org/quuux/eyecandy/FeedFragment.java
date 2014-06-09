package org.quuux.eyecandy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
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

import org.quuux.eyecandy.utils.GifDecoder;
import org.quuux.eyecandy.utils.GifDecoderRequest;
import org.quuux.eyecandy.utils.ImageUtils;
import org.quuux.orm.FetchListener;
import org.quuux.orm.Func;
import org.quuux.orm.Query;
import org.quuux.orm.Session;
import org.quuux.orm.util.QueryAdapter;

import pl.droidsonroids.gif.GifDrawable;

public class FeedFragment extends GalleryFragment {

    @Override
    public void onResume() {
        super.onResume();
        mListener.setSelectedNavigationItemSilent(MainActivity.MODE_FEED);
    }

    @Override
    public int getLayout() {
        return R.layout.feed;
    }

    @Override
    public int getItemLayout() {
        return R.layout.feed_item;
    }

    @Override
    public void bindItem(final Holder tag) {
        final Transformation transformation = new Transformation() {

            @Override public Bitmap transform(Bitmap source) {
                int targetWidth = tag.thumbnail.getWidth();

                double aspectRatio = (double) source.getHeight() / (double) source.getWidth();
                int targetHeight = (int) (targetWidth * aspectRatio);
                Bitmap result = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, false);
                if (result != source) {
                    // Same bitmap is returned if sizes are the same
                    source.recycle();
                }
                return result;
            }

            @Override public String key() {
                return "transformation" + " desiredWidth";
            }
        };

        final String url = tag.image.isImgur() ? tag.image.getImgurThumbnailUrl(Image.Thumbnail.HUGE) : tag.image.getUrl();

        mPicasso.load(url)
                .placeholder(R.drawable.ic_loading)
                .transform(transformation)
                .into(tag.thumbnail, tag.callback);

        if (tag.summary != null) {
            final String summary = String.format("<em>%s</em>", tag.image.getTitle());
            tag.summary.setText(Html.fromHtml(summary));
        }
    }

    public static FeedFragment newInstance(final Query query, final Subreddit subreddit) {
        final FeedFragment rv = new FeedFragment();
        final Bundle args = new Bundle();
        if (query != null)
            args.putSerializable("query", query);

        if (subreddit != null)
            args.putSerializable("subreddit", subreddit);

        rv.setArguments(args);
        return rv;
    }


}
