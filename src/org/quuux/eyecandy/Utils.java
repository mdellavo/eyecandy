package org.quuux.eyecandy;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.util.DisplayMetrics;

public class Utils {

    public static String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index == -1) {
            return null;
        }
            
        return cursor.getString(index);
    }

    public static int getInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index == -1) {
            return -1;
        }
            
        return cursor.getInt(index);
    }

    public static float dp2pix(float dp, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return px;
    }

}
