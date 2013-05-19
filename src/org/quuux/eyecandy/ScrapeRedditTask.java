package org.quuux.eyecandy;

import android.content.Context;
import android.os.AsyncTask;

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
    private final Log mLog = new Log(ScrapeRedditTask.class);

    private static final String FORMAT = "http://www.reddit.com/r/%s.json?limit=100";

    private static final long SCRAPE_PERIOD = 60 * 60 * 1000;

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
            mLog.e("Error fetching json", e);
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

            mLog.d("%s was last scraped %d seconds ago", url, (ago/1000));

            if (ago < SCRAPE_PERIOD) {
                mLog.d("skipping: %s", uri);
                return rv;
            }
                
            mLog.d("Scraping: %s",url);
           
            URLConnection conn = url.openConnection();
            conn.connect();
        
            JSONObject json = fetch(conn.getInputStream());
            if (json == null) {
                return null;
            }
            
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                mLog.e("json doesnt have data property: %s", json);
                return null;
            }

            JSONArray children = data.optJSONArray("children");
            if (children == null) {
                mLog.e("data doesnt have children property: %s", json);
                return null;
            }
            
            List images = new ArrayList<Image>();

            for(int i=0; i< children.length(); i++) {
                JSONObject image_container = children.optJSONObject(i);
                JSONObject image_json = image_container.optJSONObject("data");

                if (image_json == null) {
                    mLog.e("image container does not have dat property: %s", image_container);
                }

                Image image = Image.fromReddit(image_json);

                if (image != null) {
                    images.add(image);
                }
            }
            
            mLog.d("Found %d images", images.size());
            
            db.syncImages(images);
            db.markScrape(url.toString());

            rv = new Integer(images.size());

        } catch (Exception e) {
            mLog.e("Error scraping %s", e, uri);
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



