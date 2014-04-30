/**
*    Copyright 2014 University of Helsinki
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package eit.sdn.sdncontroller;


import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

/**
 * This is the Android client programme for EIT-SDN project
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 **/

public class MainActivity extends Activity {

    private Intent udpListeningIntent = null;
    private Intent trafficMonitoringIntent = null;
    private Intent wifiScanningIntent = null;
    private DownloadListener downloadListener;
    private Switch sdnSwitch;
    private Switch wifiScanSwitch;

    private long downloadID;
    private boolean isSDNDownloadStarted;
    private long startTime;

    private boolean isClientDetectionOn = false;

    // some defaults
    private String LOG_TAG = "SDNController";
    private String PREF_KEY_CLT_DETECTION = "pref_client_detection";
    private String SWITCH_WIFI_SCAN = "switchWifiScan";
    private String SWITCH_SDN = "switchSDN";
    private String BUTTON_DOWNLOAD = "buttonDownload";
    private String DOWNLOAD_ID = "downloadID";
    private String OUT_FILE = "download.txt";

    // FIXME this downloadlistener could not work right (for calculating the
    // time interval) if this activity is stopped, and started again during
    // file downloading. Right now the starttime var is only stored as a class
    // var, not a SharedPreference, so if activity is restarted, the old value
    // will be erased.
    public class DownloadListener extends BroadcastReceiver {

        // defaults
        private String LOG_TAG = "DownloadListener";

        @Override
        public void onReceive(Context c, Intent intent) {
            if (isSDNDownloadStarted) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                if (id == downloadID) {

                    long endTime = System.currentTimeMillis();
                    double interval = (endTime - startTime) / 1000.0;

                    DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    Query query = new Query();
                    query.setFilterById(id);
                    Cursor cursor = manager.query(query);

                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        String bytes = cursor.getString(columnIndex);
                        Log.d(LOG_TAG, "successfully downloaded tmp file...");
                        Log.d(LOG_TAG, bytes + " bytes -- " + interval + "s");

                        String text = bytes + " bytes -- " + interval + "s";
                        SDNCommonUtil.writeToExternalFile(text, LOG_TAG, OUT_FILE);

                        columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME);
                        SDNCommonUtil.removeExternalFile(cursor.getString(columnIndex), LOG_TAG);
                    }

                    Button downloadButton = (Button)findViewById(R.id.button_download);
                    downloadButton.setText("start");
                    isSDNDownloadStarted = false;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sdnSwitch = (Switch) findViewById(R.id.switch_sdn);
        sdnSwitch.setChecked(false);
        wifiScanSwitch = (Switch) findViewById(R.id.switch_wifi_scan);
        wifiScanSwitch.setChecked(false);

        isSDNDownloadStarted = getPreference(BUTTON_DOWNLOAD, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public void onSwitchSDNClicked(View view) throws InterruptedException {
        udpListeningIntent = new Intent(this, UDPListeningService.class);
        trafficMonitoringIntent = new Intent(this, TrafficMonitoringService.class);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Is the switch on?
        boolean on = ((Switch) view).isChecked();

        if (on) {
            Log.d("Main", "SDN switch is checked on");

            // Is mobile connected to some network?
            if (!isOnline()) { // no connection, show warning messages
                CharSequence text = "This device is not connected to any network";
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(this, text, duration);
                toast.show();
            }

            if (sharedPrefs.getBoolean(PREF_KEY_CLT_DETECTION, false)) {
                long startRX = TrafficStats.getTotalRxBytes();
                long startTX = TrafficStats.getTotalTxBytes();
                if (startRX == TrafficStats.UNSUPPORTED || startTX == TrafficStats.UNSUPPORTED) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(this);
                    alert.setTitle("Warning");
                    alert.setMessage("Your device does not support traffic stat monitoring.");
                    alert.show();
                } else {
                    startService(trafficMonitoringIntent);
                    isClientDetectionOn = true;
                }
            }

            startService(udpListeningIntent);
        } else {
            Log.d("Main", "SDN switch is checked off");

            if (isClientDetectionOn) {
                stopService(trafficMonitoringIntent);
                isClientDetectionOn = false;
            }

            stopService(udpListeningIntent);
            udpListeningIntent = null;
            trafficMonitoringIntent = null;
        }
    }

    public void onSwitchWifiScanClicked(View view) {
        wifiScanningIntent = new Intent(this, WifiScanningService.class);

        // Is the switch on?
        boolean on = ((Switch)view).isChecked();

        if (on) {
            if (!SDNCommonUtil.isWifiEnabled(this)) {
                CharSequence text = "wifi is not enabled";
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(this, text, duration);
                toast.show();
            }

            startService(wifiScanningIntent);

        } else {
            stopService(wifiScanningIntent);
            wifiScanningIntent = null;
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_settings:
                openSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStart(){
        super.onStart();

        // get previous state of the switch
        sdnSwitch.setChecked(getPreference(SWITCH_SDN, this));
        wifiScanSwitch.setChecked(getPreference(SWITCH_WIFI_SCAN, this));

        isSDNDownloadStarted = getPreference(BUTTON_DOWNLOAD, this);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        downloadID = preferences.getLong(DOWNLOAD_ID, 0);

        if (isSDNDownloadStarted) {
            Button downloadButton = (Button)findViewById(R.id.button_download);
            downloadButton.setText("stop");
        }

        // register broadcast receiver
        downloadListener = new DownloadListener();
        IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        registerReceiver(downloadListener, intentFilter);
    }


    @Override
    public void onStop(){
        super.onStop();

        // save current state of the switch
        setPreference(SWITCH_SDN, sdnSwitch.isChecked(), this);
        setPreference(SWITCH_WIFI_SCAN, wifiScanSwitch.isChecked(), this);

        setPreference(BUTTON_DOWNLOAD, isSDNDownloadStarted, this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(DOWNLOAD_ID, downloadID);
        editor.commit();

        unregisterReceiver(downloadListener);
    }

//     @Override
//     public void onDestroy() {
//         super.onDestroy();
//
//         // stop the udp listening thread
//         if (udpListeningIntent != null) {
//             stopService(udpListeningIntent);
//         }
//     }



    /**
     * save specific preference, this preference can be used later by calling
     * getPrefernece()
     *
     * @param key name of the preference defined by programmer
     * @param value the boolean value of the preference
     * @param context current running context
     */
    private void setPreference(String key, Boolean value, Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.commit();
    }

    /**
     * get restored preference, this preference is used for recovering/restart
     * this activity
     *
     * @param key name of the preference defined by programmer
     * @param context current running context
     */
    private Boolean getPreference(String key, Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(key, false);
    }

    /**
     * Check whether wifi is enabled and connected
     *
     * @param
     */
    public boolean isOnline() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }


    /** Called when the user clicks the Settings button */
    public void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }


    public class WifiScanUpdateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int num = intent.getIntExtra(WifiScanningService.EXTRA_KEY_UPDATE, 0);
            CharSequence text = "Scan " + num + " started";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }
    }

    public void controlDownload(View view) {

        if (!isDownloadManagerAvailable(this)) {
            CharSequence text = "Can not download the file now, please try it later!";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(this, text, duration);
            toast.show();
            return;
        }


        if (!isSDNDownloadStarted) {
            String url = "http://download.virtualbox.org/virtualbox/4.3.10/virtualbox-4.3_4.3.10-93012~Ubuntu~raring_amd64.deb";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setDescription("file is downloading");
            request.setTitle("sdn-download.tmp");
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "sdn-download.tmp");

            // download existing file with the same name, or there will be an error with broadcast listener
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
                            + "/sdn-download.tmp";
            SDNCommonUtil.removeExternalFile(path, LOG_TAG);

            // get download service and enqueue file
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            downloadID = manager.enqueue(request);
            isSDNDownloadStarted = true;
            startTime = System.currentTimeMillis();
            Log.d(LOG_TAG, "start downloading tmp file...");

            Button downloadButton = (Button)findViewById(R.id.button_download);
            downloadButton.setText("stop");
        } else {
            isSDNDownloadStarted = false;
            Button downloadButton = (Button)findViewById(R.id.button_download);
            downloadButton.setText("start");

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.remove(downloadID);
            Log.d(LOG_TAG, "cancel downloading file");
        }
    }

    /**
     * @param context used to check the device version and DownloadManager information
     * @return true if the download manager is available
     */
    public static boolean isDownloadManagerAvailable(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName("com.android.providers.downloads.ui", "com.android.providers.downloads.ui.DownloadList");
            List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            return list.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

}
