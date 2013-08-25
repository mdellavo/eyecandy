package org.quuux.eyecandy;

import android.app.Activity;
import android.graphics.Movie;
import android.os.Bundle;

import java.io.InputStream;

public class AnimationTestActivity extends Activity {
    private AnimatedImageView mView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mView = new AnimatedImageView(this);
        final InputStream in = getResources().openRawResource(R.raw.test_animation);
        final Movie movie = Movie.decodeStream(in);
        mView.setMovie(movie);
        setContentView(mView);
    }
}
