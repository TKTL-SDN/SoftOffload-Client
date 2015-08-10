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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

/**
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class WifiScanningService extends IntentService {

    private boolean isEnabled = false;
    private boolean isScanningFinished = false;
    private int scanNum = 0;
    private int scanInterval;
    private long connectTimeout;
    private boolean isConnectingTestEnabled = false;
    private WifiScanReceiver wifiScanReceiver; // used for scan wifi ap
    private Map<String, String> apMap = new ConcurrentHashMap<String, String>();
    private Map<String, Integer> testedAPMap = new ConcurrentHashMap<String, Integer>();

    // defaults
    private String LOG_TAG = SDNCommonUtil.LOG_TAG;
    private String LOG_FILE = "log.txt";
    private String WIFI_LIST_FILE = "wifi.txt";
    private String FREE_WIFI_FILE = "open-wifi.txt";
    private String AVAILBLE_WIFI_FILE = "available-wifi.txt";
    private String WIFI_TRIED_FILE = "tried-wifi.txt";
    private String PATH = "/SDNController/";
    private String PREF_SCAN_INTERVAL = "pref_wifi_scan_interval";
    private String PREF_CONNECTING_TEST = "pref_connecting_test";
    private String PREF_CONNECTING_TEST_TIMEOUT = "pref_connecting_test_timeout";
    private String DEFAULT_SCAN_INTERVAL = "10";
    private String DEFAULT_DELAY_TIME_MS = "2000";
    private int DELAY_TIMES = 3;

    public static final String ACTION_SCAN_UPDATE = "eit.sdn.sdncontroller.ACTION_SCAN_UPDATE";
    public static final String EXTRA_KEY_UPDATE = "SCAN_NUM";

    /**
     * receive wifi scan result broadcast and then trigger our own functions
     *
     */
    private class WifiScanReceiver extends BroadcastReceiver {
        private boolean isServiceAsked = false;

        public void onReceive(Context c, Intent intent) {

            if (isServiceAsked) {
                Log.d(LOG_TAG, "wifi scan result is available...");
                WifiManager wifiManager = (WifiManager)c.getSystemService(Context.WIFI_SERVICE);
                List<ScanResult> scanResultList = wifiManager.getScanResults();

                for (ScanResult r: scanResultList) {

                    Date d = new Date();
                    CharSequence s  = DateFormat.format("hh:mm:ss, MMMM d, yyyy", d.getTime());
                    String timestamp = "[" + s.toString() + "] ";
                    String logStr = timestamp + r.SSID + " | " + r.BSSID + " | " + r.level;
                    SDNCommonUtil.writeToExternalFile(logStr, LOG_TAG, LOG_FILE);
                    // Log.d(LOG_TAG, r.SSID + " " + Integer.toString(r.level));

                    if (!apMap.containsKey(r.BSSID)) {
                        String ap = timestamp + r.SSID + " | " + r.BSSID;
                        apMap.put(r.BSSID, ap);
                        SDNCommonUtil.writeToExternalFile(ap, LOG_TAG, WIFI_LIST_FILE);

                        if (r.capabilities.equals("[ESS]")) {
                            SDNCommonUtil.writeToExternalFile(ap, LOG_TAG, FREE_WIFI_FILE);
                            if (isConnectingTestEnabled) {
                                if ((!testedAPMap.containsKey(r.BSSID) && r.level > -98)
                                        || (testedAPMap.containsKey(r.BSSID) && r.level > testedAPMap.get(r.BSSID))) {
                                    try {
                                        testedAPMap.put(r.BSSID, r.level);
                                        String msg = ap + " | " + r.level;
                                        SDNCommonUtil.writeToExternalFile(msg, LOG_TAG, WIFI_TRIED_FILE);
                                        connectToOpenNetwork(r.SSID, r.BSSID, r.level, LOG_TAG);
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "failed to start connection test");
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }

                // Log.d("test", testedAPMap.toString());

                String tmpStr = "Scan " + scanNum + " finished";
                CharSequence text = tmpStr;
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(c, text, duration);
                toast.show();

                Log.d(LOG_TAG, "wrote wifi scanning result on external storage");
                isServiceAsked = false;
                isScanningFinished = true;
            }
        }

        public void setFlag(boolean b) {
            isServiceAsked = b;
        }
    }

    /**
     * A required constructor for this service
     *
     */
    public WifiScanningService() {
        super("WifiScanningService");
    }


    @Override
    protected void onHandleIntent(Intent arg0) {
        isEnabled = true;
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        scanInterval = Integer.parseInt(sharedPrefs.getString(PREF_SCAN_INTERVAL, DEFAULT_SCAN_INTERVAL));
        connectTimeout = Long.parseLong(sharedPrefs.getString(PREF_CONNECTING_TEST_TIMEOUT, DEFAULT_DELAY_TIME_MS));
        isConnectingTestEnabled = sharedPrefs.getBoolean(PREF_CONNECTING_TEST, false);

        wifiScanReceiver = new WifiScanReceiver();
        registerReceiver(wifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        File root = Environment.getExternalStorageDirectory();
        String path1 = root.getAbsolutePath() + PATH + WIFI_LIST_FILE;
        String path2 = root.getAbsolutePath() + PATH + FREE_WIFI_FILE;
        String path3 = root.getAbsolutePath() + PATH + AVAILBLE_WIFI_FILE;
        SDNCommonUtil.removeExternalFile(path1, LOG_TAG);
        SDNCommonUtil.removeExternalFile(path2, LOG_TAG);
        SDNCommonUtil.removeExternalFile(path3, LOG_TAG);

        WifiManager wifiManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
        WifiLock lock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, LOG_TAG);
        lock.acquire();

        while(isEnabled) {
            isScanningFinished = false;
            wifiScanReceiver.setFlag(true);
            wifiManager.startScan();
            scanNum++;

            // send broadcast to main activity
            Intent intentUpdate = new Intent();
            intentUpdate.setAction(ACTION_SCAN_UPDATE);
            intentUpdate.addCategory(Intent.CATEGORY_DEFAULT);
            intentUpdate.putExtra(EXTRA_KEY_UPDATE, scanNum);
            sendBroadcast(intentUpdate);

            Log.d(LOG_TAG, "try to scan available wifi points");

            while(isConnectingTestEnabled && !isScanningFinished) {
                SystemClock.sleep(1000);
            }

            Log.d(LOG_TAG, "scan " + scanNum + " finished");
            SystemClock.sleep(scanInterval * 1000);
        }
        lock.release();
    }

    public void terminateService() {
        isEnabled = false;
        isScanningFinished = true;
    }

    @Override
    public void onDestroy() {
        terminateService();
        unregisterReceiver(wifiScanReceiver);
        Log.d(LOG_TAG, "wifi scanning service stopped");
        super.onDestroy();
    }

    private void connectToOpenNetwork(String ssid, String bssid, int level, String logTag) {
        WifiManager wifiMgr = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connMgr = (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        NetworkInfo netInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        // WifiConfiguration wifiConfig = null;
        int netId = -1;

        if (netInfo.isConnected() && wifiInfo.getBSSID() != null
                && wifiInfo.getBSSID().equals(bssid)) {

                Log.d(logTag, "same ssid to current one");
                TestConnectionTask task = new TestConnectionTask(this);
                task.execute(wifiInfo.getSSID(), wifiInfo.getBSSID(), level);
                try {
                    task.get(2500, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    Log.e(logTag, "failed to proceed connection testing");
                    e.printStackTrace();
                }
        } else {
            Log.d(logTag, "try to find network config");
            // find corresponding config
            List<WifiConfiguration> list = wifiMgr.getConfiguredNetworks();
            for(WifiConfiguration i : list) {
                // wifiMgr.disableNetwork(i.networkId);
                if(i.BSSID != null && i.BSSID.equals(bssid)) {
                    Log.d(logTag, "find existing config for bssid: " + bssid);
                    netId = i.networkId;
                    break;
                }
            }

            if (netId == -1) {
                // not find existing config for the new ssid
                Log.d(logTag, "create new config for ssid: " + ssid);
                WifiConfiguration conf = new WifiConfiguration();
                conf.SSID = "\"" + ssid + "\"";
                conf.BSSID = bssid;
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                netId = wifiMgr.addNetwork(conf);
                // wifiConfig = conf;
            }

            Log.d(logTag, "trying to connect to network " + ssid + " - " + bssid + "...");

            wifiMgr.disconnect();
            wifiMgr.enableNetwork(netId, true);
            // wifiMgr.reconnect();

            for (int i = 0; i < DELAY_TIMES; i++) {
                SystemClock.sleep(connectTimeout);
                wifiInfo = wifiMgr.getConnectionInfo();
                netInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                if (netInfo.isConnected()) {
                    Log.d("debug", wifiInfo.getBSSID() + " " + bssid);
                }

                if (netInfo.isConnected()
                        && wifiInfo.getBSSID() != null
                        && wifiInfo.getBSSID().equals(bssid)) {
                    Log.d(LOG_TAG, "succeeded to connect to network " + ssid + " - " + bssid);
                    TestConnectionTask task = new TestConnectionTask(this);
                    task.execute(wifiInfo.getSSID(), wifiInfo.getBSSID(), level);
                    try {
                        task.get(2500, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        Log.e(logTag, "failed to proceed connection testing");
                        e.printStackTrace();
                    }
                    return;
                }
            }

            Log.d(LOG_TAG, "failed to connect to network " + ssid);
            testedAPMap.put(bssid, level);
            wifiMgr.disableNetwork(netId);
            // wifiMgr.removeNetwork(wifiConfig.networkId);
        }
    }

    /**
     * AsyncTask class for testing network connection
     *
     * Android requires to execute networking operations in a different
     * AsyncTask thread like this
     *
     */
    private class TestConnectionTask extends AsyncTask<Object, Void, Void> {
        private String logTag = "testConnectionTask";
        private Context context;

        public TestConnectionTask (Context c){
            context = c;
       }

        @Override
        protected Void doInBackground(Object... params) {
            String ssid = (String)params[0];
            String bssid = (String)params[1];
            int level = (Integer)params[2];
            WifiManager wifiMgr = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);

            try {
                Log.d(logTag, "try to connect to www.google.com");
                InetAddress addr = InetAddress.getByName("www.google.com");
                if(addr.isReachable(2000)) {
                    Date d = new Date();
                    CharSequence s  = DateFormat.format("hh:mm:ss, MMMM d, yyyy", d.getTime());
                    String timestamp = "[" + s.toString() + "] ";
                    String data = timestamp + ssid + " - " + bssid;
                    Log.d(logTag, "succeeded to connect to www.google.com");
                    testedAPMap.put(bssid, 0);
                    SDNCommonUtil.writeToExternalFile(data, logTag, AVAILBLE_WIFI_FILE);
                } else {
                    testedAPMap.put(bssid, level);
                    Log.d(logTag, "failed to connect to www.google.com");
                }
                wifiMgr.disconnect();

            } catch (UnknownHostException e) {
                Log.d(logTag, "can not resolve DNS record of www.google.com");
            } catch (IOException e) {
                Log.e(logTag, "can not use 'www.google.com' to test wifi connection");
                e.printStackTrace();
            } catch (Exception e) {
                Log.e(logTag, "unknown exception when trying to connection www.google.com");
                e.printStackTrace();
            }
            return null;
        }

    }

}
