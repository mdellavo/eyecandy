package org.quuux.eyecandy;

import android.app.Application;

import org.quuux.orm.Database;

public class EyeCandyApplication extends Application {
    static {
        Database.attach(Image.class);
        Database.attach(Subreddit.class);
    }

}
