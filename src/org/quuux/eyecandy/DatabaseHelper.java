package org.quuux.eyecandy;

import android.content.Context;

import android.database.Cursor;
import android.database.DatabaseUtils.InsertHelper;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final Log mLog = new Log(DatabaseHelper.class);

    private static final String TAG = "DatabaseHelper";

    private static final int DATABASE_VERSION = 6;

    private static final String DATABASE_NAME = "eyecandy.db";

    private static final String IMAGE_TABLE_NAME = "images";

    private static final String IMAGE_TABLE_CREATE = 
        "CREATE TABLE " + IMAGE_TABLE_NAME + " (" + 
        "url TEXT PRIMARY KEY," + 
        "source TEXT," +
        "source_url TEXT," + 
        "title TEXT," + 
        "status TEXT," + 
        "created_on INTEGER," + 
        "times_shown INTEGER" + 
        ");";

    private static final String IMAGE_TABLE_DROP = "DROP TABLE IF EXISTS " + IMAGE_TABLE_NAME + ";";

    private static final String SCRAPE_TABLE_NAME = "scrapes";

    private static final String SCRAPE_TABLE_CREATE = 
        "CREATE TABLE " + SCRAPE_TABLE_NAME + " (" + 
        "url TEXT PRIMARY KEY," + 
        "last_scrape BIGINT" + 
        ");";

    private static final String SCRAPE_TABLE_DROP = "DROP TABLE IF EXISTS " + SCRAPE_TABLE_NAME + ";";

    private static final String HISTORY_TABLE_NAME = "history";

    private static final String HISTORY_TABLE_CREATE = 
        "CREATE TABLE " + HISTORY_TABLE_NAME + " (" + 
        "timestamp INTEGER PRIMARY KEY," + 
        "url TEXT" + 
        ");";

    private static final String HISTORY_TABLE_DROP = "DROP TABLE IF EXISTS " + HISTORY_TABLE_NAME + ";";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(IMAGE_TABLE_CREATE);
        db.execSQL(SCRAPE_TABLE_CREATE);
        db.execSQL(HISTORY_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(IMAGE_TABLE_DROP);
        db.execSQL(SCRAPE_TABLE_DROP);
        db.execSQL(HISTORY_TABLE_DROP);
        onCreate(db);
    }

    public boolean imageExists(SQLiteDatabase db, Image image) {
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + IMAGE_TABLE_NAME + " WHERE url = ?;", new String[] { image.getUrl() });
        boolean rv = cursor.moveToFirst();
        cursor.close();
        return rv;
    }

    synchronized public void syncImages(List<Image> images) {

        SQLiteDatabase db = getWritableDatabase();        
        InsertHelper inserter = new InsertHelper(db, IMAGE_TABLE_NAME);
  
        int source = inserter.getColumnIndex("source");
        int url = inserter.getColumnIndex("url");
        int source_url = inserter.getColumnIndex("source_url");
        int title = inserter.getColumnIndex("title");
        int status = inserter.getColumnIndex("status");
        int created_on = inserter.getColumnIndex("created_on");
        int times_shown = inserter.getColumnIndex("times_shown");
            
        long now = System.currentTimeMillis();

        db.beginTransaction();
        try {
            for(Image image: images) {
                
                if (imageExists(db, image)) {
                    continue;
                }

                inserter.prepareForInsert();

                inserter.bind(source, image.getSource().toString());
                inserter.bind(url, image.getUrl());
                //inserter.bind(source_url, image.getSourceUrl());
                inserter.bind(title, image.getTitle());
                inserter.bind(status, image.getStatus().toString());
                inserter.bind(times_shown, image.getTimesShown());
                inserter.bind(created_on, now);
                
                inserter.execute();
            }

            db.setTransactionSuccessful();
            mLog.d("Synced images");
            
        } catch(Exception e) {
            Log.e(TAG, "Error syncing images", e);
        }

        db.endTransaction();

        inserter.close();            
        db.close();
    }

    synchronized public Image nextImage() {
        SQLiteDatabase db = getReadableDatabase();  
        Cursor cursor = db.rawQuery("SELECT * FROM " + IMAGE_TABLE_NAME + " ORDER BY times_shown, RANDOM();", null);

        if (!cursor.moveToFirst())
            return null;

        //Image rv = Image.from(cursor);

        cursor.close();
        db.close();

        return null;
    }


    synchronized public void markFetched(Image image) {
        SQLiteDatabase db = getWritableDatabase();  
        db.execSQL("UPDATE " + IMAGE_TABLE_NAME + " SET status = ? WHERE url = ?;",
                   new String[] { Image.Status.FETCHED.toString(), image.getUrl() });
        db.close();
    }

    synchronized public void incShown(Image image) {
        SQLiteDatabase db = getWritableDatabase();  
        db.execSQL("UPDATE " + IMAGE_TABLE_NAME + " SET times_shown = times_shown + 1 WHERE url = ?;",
                   new String[] { image.getUrl() });
        db.close();
    }
    
    synchronized public long lastSraped(String url) {
        SQLiteDatabase db = getReadableDatabase();  
        Cursor cursor = db.rawQuery("SELECT last_scrape FROM " + SCRAPE_TABLE_NAME + " WHERE url = ?;", new String[] { url });

        if (!cursor.moveToFirst())
            return 0;

        long last_scraped = cursor.getLong(0);

        cursor.close();
        db.close();

        return last_scraped;    
    }
    
    synchronized public void markScrape(String scrapedUrl) {

        SQLiteDatabase db = getWritableDatabase();        
        InsertHelper inserter = new InsertHelper(db, SCRAPE_TABLE_NAME);
  
        int url = inserter.getColumnIndex("url");
        int last_scrape = inserter.getColumnIndex("last_scrape");

        long now = System.currentTimeMillis();

        db.beginTransaction();
        try {
            inserter.prepareForReplace();

            inserter.bind(url, scrapedUrl);
            inserter.bind(last_scrape, now);
                
            inserter.execute();

            db.setTransactionSuccessful();

        } catch(Exception e) {
            mLog.e("Error marking scrape", e);
        }

        db.endTransaction();

        inserter.close();            
        db.close();
    }
}
