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
import java.util.Date;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
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
    private long fileLength;
    private boolean isFirstConnection = true;
    
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
        // String urlToDownload = "http://download.virtualbox.org/virtualbox/4.3.12/virtualbox-4.3_4.3.12-93733~Ubuntu~raring_amd64.deb";
        // String urlToDownload = "http://download.angrybirds.com/games/BadPiggies/BadPiggiesInstaller_1.5.1.exe";
        // String urlToDownload = "http://download.virtualbox.org/virtualbox/4.3.18/Oracle_VM_VirtualBox_Extension_Pack-4.3.18-96516.vbox-extpack";
        String urlToDownload = "http://www.cs.helsinki.fi/group/eit-sdn/files/tiny.tmp";
        
        // this is the local url
        if (sharedPrefs.getBoolean(PREF_KEY_LOCAL_DOWNLOADING, false)) {
            // urlToDownload = "http://192.168.1.21/files/small.tmp";
            urlToDownload = "http://192.168.0.1/files/medium.tmp";
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
    @SuppressLint("NewApi")
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
            if (isFirstConnection) {
                fileLength = connection.getContentLength();
            }
            
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
                // date and time
                Date d = new Date();
                CharSequence s  = DateFormat.format("hh:mm:ss, MMMM d, yyyy", d.getTime());
                String timestamp = "[" + s.toString() + "] ";
                
                // download duration
                long endTime = System.currentTimeMillis();
                double interval = (endTime - startTime) / 1000.0;
                String text = timestamp + total + " bytes -- " + interval + "s";
                
                // get signal level
                ConnectivityManager cManager = (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);
                int type = cManager.getActiveNetworkInfo().getType();
                if (type == ConnectivityManager.TYPE_MOBILE) { // cellular
                    // FIXME this part is not working right now
                    // get lte signal level
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        // current only for 4.2 and newer versions
                        TelephonyManager tManager = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
                        try {
                            for (CellInfo info: tManager.getAllCellInfo()) {
                                if (info instanceof CellInfoLte) {
                                    CellSignalStrengthLte lte = ((CellInfoLte) info).getCellSignalStrength();
                                    int level = lte.getDbm();
                                    text += " -- " + level;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Unable to obtain cell signal information");
                        }
                   }
                } else if (type == ConnectivityManager.TYPE_WIFI) { // wifi
                    // wifi signal level
                    WifiManager wifiManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
                    int level = wifiManager.getConnectionInfo().getRssi();
                    text += " -- " + level;
                }
                
                SDNCommonUtil.writeToExternalFile(text, LOG_TAG, LOG_FILE);
                SDNCommonUtil.removeExternalFile(path, LOG_TAG);
            }

        } catch (SocketException e) { // lost connection
            Log.e(LOG_TAG, "socket error, try to resume downloading");
            isFirstConnection = false;
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
