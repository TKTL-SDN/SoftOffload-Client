/**
 * 
 */
package eit.sdn.sdncontroller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * @author yanhe.liu@cs.helsinki.fi
 *
 * service for downloading a specific file
 * mainly used for testing downloading average speed
 */
public class DownloadService extends IntentService {
    
    private boolean isCancelled = false;
    private boolean isFinished = false;
    private long startTime; // used for calculating download time 
    
    // default
    private String PREF_KEY_LOCAL_DOWNLOADING = "pref_local_downloading";
    private String LOG_TAG = "DownloadService";
    private int MAX_BUFF = 10240;
    public static final int PROGRESS_CODE = 8344;
    private long DELAY_TIME_MS = 2000;
    private int DELAY_TIMES = 50;
    private String LOG_FILE = "download-log.txt";
    

    /**
     * A required constructor for this service
     *
     */
    public DownloadService() {
        super("DownloadService");
    }
    
    /**
     * main logic function of this service
     *
     */
    @SuppressLint("DefaultLocale")
    @Override
    protected void onHandleIntent(Intent intent) {
        // download file url
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String urlToDownload = "http://download.virtualbox.org/virtualbox/4.3.12/virtualbox-4.3_4.3.12-93733~Ubuntu~raring_amd64.deb";

        // this is the local url
        if (sharedPrefs.getBoolean(PREF_KEY_LOCAL_DOWNLOADING, false)) {
            urlToDownload = "http://192.168.0.1/files/virtualbox_local.deb";
        }
        
        // download existing file with the same name
        // remove the old one every time when this service starts
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .getAbsolutePath() + "/sdn-download.tmp";
        SDNCommonUtil.removeExternalFile(path, LOG_TAG);
        
        // used to transmit download progress percentage back to main activiy
        ResultReceiver receiver = (ResultReceiver) intent.getParcelableExtra("receiver");
        
        // start download 
        startTime = System.currentTimeMillis();
        fileDownload(urlToDownload, path, receiver);
    }
    

    @Override
    public void onDestroy() {
        isCancelled = true;
        Log.d(LOG_TAG, "DownlaodingService stopped.");
        super.onDestroy();
    }
    
    
    // Here I use a try/catch recursion
    private void fileDownload(String urlToDownload, String path, ResultReceiver receiver) {
        
        long total = 0;
        InputStream input = null;
        OutputStream output = null;
        
        try {
            URL url = new URL(urlToDownload);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            
            File file = new File(path);
            if (file.exists()) { // resume downloading
                 total = file.length();
                 connection.setRequestProperty("Range", "bytes=" + total + "-");
                 Log.i(LOG_TAG, "resume downloading from bytes " + total);
            }
            
            connection.connect();
            // this will be to show a 0-100% progress bar
            int fileLength = connection.getContentLength();
            
            // download the file
            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(file, true);
            
            byte data[] = new byte[MAX_BUFF];
            int count;
            while (!isCancelled && (count = input.read(data)) != -1) {
                output.write(data, 0, count);
                total += count;
                // publishing the progress....
                Bundle resultData = new Bundle();
                resultData.putInt("progress", (int)(total * 100 / fileLength));
                receiver.send(PROGRESS_CODE, resultData);
            }
    
            output.flush();
            output.close();
            input.close();
            isFinished = true;
            
            // write to download statistics
            Log.d(LOG_TAG, "finish file downloading with size " + total);
            if (!isCancelled) {
                // timestamp
                long endTime = System.currentTimeMillis();
                double interval = (endTime - startTime) / 1000.0;
                
                // wifi signal level
                WifiManager wifiManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
                int level = wifiManager.getConnectionInfo().getRssi();
                
                String text = total + " bytes -- " + interval + "s -- " + level;
                SDNCommonUtil.writeToExternalFile(text, LOG_TAG, LOG_FILE);
                SDNCommonUtil.removeExternalFile(path, LOG_TAG);
            }

        } catch (SocketException e) { // lost connection
            Log.e(LOG_TAG, "socket error, try to resume downloading");
            if (output != null) { // force OS to flush cached bytes to file
                try {
                    output.close();
                } catch (IOException ex) {
                    Log.e(LOG_TAG, "fail to write file");
                    isFinished = true; // skip downloading in this case
                }
            }
            
            int i = 0;
            while (i < DELAY_TIMES && !isFinished) {
                i++;
                SystemClock.sleep(DELAY_TIME_MS);
                if (SDNCommonUtil.isOnline(this)) { // recursion
                    fileDownload(urlToDownload, path, receiver);
                }
            }
        } catch (SocketTimeoutException e) {
            Log.e(LOG_TAG, "download connection timeout");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(LOG_TAG, "fail to read/write file");
            e.printStackTrace();
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {}
        }
        
        // publish final signal to main activity
        Bundle resultData = new Bundle();
        resultData.putInt("downloadProgress" ,100);
        receiver.send(PROGRESS_CODE, resultData);
    }

}
