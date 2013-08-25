package org.quuux.eyecandy;

import android.*;
import android.R;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class AnimatedImageView extends View {

    private static final Log mLog = new Log(AnimatedImageView.class);
    private Movie mMovie;

    private long last = -1;
    private Paint mPaint;

    public AnimatedImageView(final Context context) {
        super(context);
        initialize();
    }

    public AnimatedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public AnimatedImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    private void initialize() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final long now = System.currentTimeMillis();

        if (last < 0)
            last = System.currentTimeMillis();

        final long t = (now - last) % mMovie.duration();

        final float scale = Math.min(
                getWidth() / (float)mMovie.width(),
                getHeight() / (float)mMovie.height()
        );

        final int dx = Math.max((getWidth() - mMovie.width()) / 2, 0);
        final int dy = Math.max((getHeight() - mMovie.height()) / 2, 0);

        //mLog.d("t = %d | scale = %.04f | mMovie.width = %d | mMovie.height = %d | width = %d | height = %d",
        //        t, scale, mMovie.width(), mMovie.height(), getWidth(), getHeight());

        canvas.save();

        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        mMovie.setTime((int)t);
        mMovie.draw(canvas, 0, 0, mPaint);

        canvas.restore();

        invalidate();
    }

    public void setMovie(final Movie movie) {
        mMovie = movie;
    }
}
