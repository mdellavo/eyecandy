package org.quuux.eyecandy;

import android.content.Intent;
import android.net.Uri;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.MuzeiArtSource;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;

import org.quuux.orm.Entity;
import org.quuux.orm.FetchListener;
import org.quuux.orm.Session;

public class EyeCandyArtSource extends MuzeiArtSource {

    private static final long UPDATE_PERIOD = 1000 * 60 * 5;

    public EyeCandyArtSource() {
        super(EyeCandyArtSource.class.getName());
    }

    @Override
    protected void onUpdate(final int reason) {

        final Session session = EyeCandyDatabase.getSession(this);
        session.query(Image.class).filter("animated=0").orderBy("RANDOM()").first(new FetchListener<Image>() {
            @Override
            public void onResult(final Image image) {

                final Artwork artwork = new Artwork.Builder()
                        .imageUri(Uri.parse(image.getUrl()))
                        .title(image.getTitle())
                        .byline(image.getSubreddit())
                        .viewIntent(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(image.getUrl())))
                        .build();

                publishArtwork(artwork);
                scheduleUpdate(UPDATE_PERIOD);
            }
        });


    }
}