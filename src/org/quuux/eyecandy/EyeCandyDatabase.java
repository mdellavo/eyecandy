package org.quuux.eyecandy;

import android.content.Context;
import org.quuux.orm.Database;

public class EyeCandyDatabase extends Database {

    private static final String NAME = "eyecandy.db";
    private static final int VERSION = 4;

    private static EyeCandyDatabase instance = null;

    protected EyeCandyDatabase(final Context context, final String name, final int version) {
        super(context, name, version);
    }

    public static EyeCandyDatabase getInstance(final Context context) {
        if (instance == null) {
            instance = new EyeCandyDatabase(context.getApplicationContext(), NAME, VERSION);
        }

        return instance;
    }
}

