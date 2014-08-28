/**
 * 
 */
package eit.sdn.sdncontroller;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * @author yanhe.liu@cs.helsinki.fi
 *
 */
public class DownloadService extends IntentService {
    
    private boolean isCancelled = false;
    
    // default
    private String PREF_KEY_LOCAL_DOWNLOADING = "pref_local_downloading";
    private String LOG_TAG = "DownloadService";
    private int MAX_BUFF = 1024;
    public static final int PROGRESS_CODE = 8344;
    private int CONNECT_TIMEOUT = 15000;
    

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
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String urlToDownload = "http://download.virtualbox.org/virtualbox/4.3.12/virtualbox-4.3_4.3.12-93733~Ubuntu~raring_amd64.deb";

        if (sharedPrefs.getBoolean(PREF_KEY_LOCAL_DOWNLOADING, false)) {
            urlToDownload = "http://192.168.0.1/files/virtualbox_local.deb";
        }
        
        ResultReceiver receiver = (ResultReceiver) intent.getParcelableExtra("receiver");
        try {
            URL url = new URL(urlToDownload);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(CONNECT_TIMEOUT);
            connection.connect();
            // this will be useful so that you can show a typical 0-100% progress bar
            int fileLength = connection.getContentLength();

            // download existing file with the same name
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            .getAbsolutePath() + "/sdn-download.tmp";
            SDNCommonUtil.removeExternalFile(path, LOG_TAG);
            
            // download the file
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new FileOutputStream(path);

            byte data[] = new byte[MAX_BUFF];
            long total = 0;
            int count;
            while (!isCancelled && (count = input.read(data)) != -1) {
                total += count;
                // publishing the progress....
                Bundle resultData = new Bundle();
                resultData.putInt("downloadProgress", (int)(total * 100 / fileLength));
                receiver.send(PROGRESS_CODE, resultData);
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            input.close();
        
        } catch (SocketTimeoutException e) {
            Log.e(LOG_TAG, "download connection timeout");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(LOG_TAG, "fail to read/write file");
            e.printStackTrace();
        } 

        Bundle resultData = new Bundle();
        resultData.putInt("downloadProgress" ,100);
        receiver.send(PROGRESS_CODE, resultData);
    }
    

    @Override
    public void onDestroy() {
        isCancelled = true;
        Log.d(LOG_TAG, "Downlaoding service stopped.");
        super.onDestroy();
    }

}
