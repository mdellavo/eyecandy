package org.quuux.eyecandy;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.lang.ref.WeakReference;

import java.net.URL;
import java.net.URLConnection;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.File;

import java.util.List;
import java.util.ArrayList;

import org.json.JSONObject;
import org.json.JSONArray;

class ScrapeRedditTask extends AsyncTask<String, Integer, Integer> {
    private static final String TAG = "ScrapeRedditTask";

    private static final String FORMAT = "http://www.reddit.com/r/%s.json?limit=100";    

    private static final long SCRAPE_PERIOD = 24 * 60 * 60 * 1000;

    protected WeakReference mContext;
    protected ScrapeCompleteListener mListener;

    public ScrapeRedditTask(Context context, ScrapeCompleteListener listener) {
        mContext = new WeakReference(context);
        mListener = listener;
    }

    protected JSONObject fetch(InputStream in) {
        JSONObject rv = null;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder response = new StringBuilder(); 

            String line;
            while ((line = reader.readLine()) != null)
                response.append(line);
            
            rv = new JSONObject(response.toString());
        } catch(Exception e) {
            Log.e(TAG, "Error fetching json", e);
        }
        
        return rv;
    }

    @Override
    protected Integer doInBackground(String... uris) {
        String uri = uris[0];
        Integer rv = new Integer(-1);
        try { 
            DatabaseHelper db = new DatabaseHelper((Context)mContext.get());

            URL url = new URL(String.format(FORMAT, uri));
            
            long last_scrape = db.lastSraped(url.toString());
            long now = System.currentTimeMillis();
            long ago = now - last_scrape;

            Log.d(TAG, "now = " + now + " | last = " + last_scrape);

            Log.d(TAG, url + " was last scraped " + (ago/1000) + " seconds ago");

            if (ago < SCRAPE_PERIOD) {
                Log.d(TAG, "skipping " + uri);
                return rv;
            }
                
            Log.d(TAG, "Scraping " + url);
           
            URLConnection conn = url.openConnection();
            conn.connect();
        
            JSONObject json = fetch(conn.getInputStream());
            if (json == null) {
                return null;
            }
            
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                Log.e(TAG, "json doesnt have data property: " + json);
                return null;
            }

            JSONArray children = data.optJSONArray("children");
            if (children == null) {
                Log.e(TAG, "data doesnt have children property: " + json);
                return null;
            }
            
            List images = new ArrayList<Image>();

            for(int i=0; i< children.length(); i++) {
                JSONObject image_container = children.optJSONObject(i);
                JSONObject image_json = image_container.optJSONObject("data");

                if (image_json == null) {
                    Log.e(TAG, "image container does not have dat property: " + image_container);
                }

                Image image = Image.fromReddit(image_json);

                if (image != null) {
                    Log.d(TAG, "scraped image " + image.getUrl());
                    images.add(image);
                }
            }
            
            Log.d(TAG, "Found " + images.size() + " images");
            
            db.syncImages(images);
            db.markScrape(url.toString());

            rv = new Integer(images.size());

        } catch (Exception e) {
            Log.e(TAG, "Error scraping " + uri, e);
        }

        return rv;
    }
    
    @Override
    protected void onProgressUpdate(Integer... progress) {
    }

    @Override
    protected void onPostExecute(Integer numScraped) {
        if (mListener != null) {
            mListener.onScrapeComplete(numScraped.intValue());
        }
    }
    
}



