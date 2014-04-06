package org.quuux.eyecandy.utils;


import android.content.Context;
import android.content.Intent;

public class IntentBuilder {

    private Intent mIntent;

    public IntentBuilder(final Intent intent) {
        mIntent = intent;
    }

    public IntentBuilder() {
        this(new Intent());
    }

    public IntentBuilder setClass(final Context packageContext, final Class<?> cls) {
        mIntent.setClass(packageContext, cls);
        return this;
    }

    public IntentBuilder setAction(final String action) {
        mIntent.setAction(action);
        return this;
    }



    public IntentBuilder setCategory(final String category) {
        mIntent.addCategory(category);
        return this;
    }

    public void startActivity(final Context context) {
        context.startActivity(mIntent);
    }


}
