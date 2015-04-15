/**
*    Copyright 2015 University of Helsinki
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
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

    private static Context mContext;    
    
    private Intent udpListeningIntent = null;
    private Intent trafficMonitoringIntent = null;
    private Intent wifiScanningIntent = null;
    private Intent downloadIntent = null;
    private Switch sdnSwitch;
    private Switch wifiScanSwitch;
    private ProgressDialog mProgressDialog;
    private boolean isSDNDownloadStarted;
    private boolean isClientDetectionOn = false;

    // some defaults
    private String LOG_TAG = "SDNController";
    private String PREF_KEY_CLT_DETECTION = "pref_client_detection";
    private String SWITCH_WIFI_SCAN = "switchWifiScan";
    private String SWITCH_SDN = "switchSDN";
    private String BUTTON_DOWNLOAD = "buttonDownload";
    
    
    // used for showing download progress bar
    private class DownloadReceiver extends ResultReceiver{
        
        public DownloadReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            if (resultCode == DownloadService.PROGRESS_CODE) {
                int progressRatio = resultData.getInt("progress");
                mProgressDialog.setProgress(progressRatio);
                
                if (progressRatio == -1) {
                    Button downloadButton = (Button)findViewById(R.id.button_download);
                    downloadButton.setText("start");
                    isSDNDownloadStarted = false;
                    mProgressDialog.dismiss();
                    
                    // Log.d("CTX", getContext().toString());
                    CharSequence text = "File is not found, please check the URL!";
                    int duration = Toast.LENGTH_LONG;
                    Toast toast = Toast.makeText(getContext(), text, duration);
                    toast.show();
                }
                
                if (progressRatio == 100) {
                    Button downloadButton = (Button)findViewById(R.id.button_download);
                    downloadButton.setText("start");
                    isSDNDownloadStarted = false;
                    mProgressDialog.dismiss();
                }
            }
        }
    }
    
    public static Context getContext() {
        //  return context
        return mContext;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = getApplication();
        // Log.d("CTX", mContext.toString());
        
        
        sdnSwitch = (Switch) findViewById(R.id.switch_sdn);
        sdnSwitch.setChecked(false);
        wifiScanSwitch = (Switch) findViewById(R.id.switch_wifi_scan);
        wifiScanSwitch.setChecked(false);

        // instantiate it within the onCreate method
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("Downlaoding Files...");
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);
        
        mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", 
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (downloadIntent != null) {
                        stopService(downloadIntent);
                        downloadIntent = null;
                    }

                    Button downloadButton = (Button)findViewById(R.id.button_download);
                    downloadButton.setText("start");
                    isSDNDownloadStarted = false;
                    dialog.dismiss();
                }
            });
        
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
            if (!SDNCommonUtil.isOnline(this)) { // no connection, show warning messages
                CharSequence text = "This device is not connected to any network";
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(this, text, duration);
                toast.show();
            }

            if (sharedPrefs.getBoolean(PREF_KEY_CLT_DETECTION, false)) {
                long startRX = TrafficStats.getTotalRxBytes();
                long startTX = TrafficStats.getTotalTxBytes();
                if (startRX == TrafficStats.UNSUPPORTED || startTX == TrafficStats.UNSUPPORTED) {
                    CharSequence text = "Your device does not support traffic stat monitoring.";
                    int duration = Toast.LENGTH_LONG;
                    Toast toast = Toast.makeText(this, text, duration);
                    toast.show();
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
        
        if (isSDNDownloadStarted) {
            Button downloadButton = (Button)findViewById(R.id.button_download);
            downloadButton.setText("stop");
        }
    }


    @Override
    public void onStop(){
        super.onStop();

        // save current state of the switch
        setPreference(SWITCH_SDN, sdnSwitch.isChecked(), this);
        setPreference(SWITCH_WIFI_SCAN, wifiScanSwitch.isChecked(), this);

        setPreference(BUTTON_DOWNLOAD, isSDNDownloadStarted, this);
    }


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

    
    /**
     * download a specific file via a background service 
     * this func will be called when user clicks the download start/stop button
     *
     * @param view
     */
    @SuppressLint("NewApi")
    public void controlDownload(View view) {
        
        if (!isSDNDownloadStarted) {
            // Is mobile connected to some network?
            if (!SDNCommonUtil.isOnline(this)) { // no connection, show warning messages
                CharSequence text = "This device is not connected to any network";
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(this, text, duration);
                toast.show();
                return;
            }
            
            // fire the downloader
            mProgressDialog.show();
            downloadIntent = new Intent(this, DownloadService.class);
            downloadIntent.putExtra("receiver", new DownloadReceiver(new Handler()));
            Log.d(LOG_TAG, "start downloading tmp file...");
            startService(downloadIntent);
            
            Button downloadButton = (Button)findViewById(R.id.button_download);
            downloadButton.setText("stop");
            isSDNDownloadStarted = true;
            mProgressDialog.show();
        } else {
            if (downloadIntent != null) {
                stopService(downloadIntent);
                downloadIntent = null;
            }            
            isSDNDownloadStarted = false;
            Button downloadButton = (Button)findViewById(R.id.button_download);
            downloadButton.setText("start");
            
            Log.d(LOG_TAG, "cancel downloading file");
        }
    }

}
