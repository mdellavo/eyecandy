package org.quuux.eyecandy;

import android.database.Cursor;

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

}
