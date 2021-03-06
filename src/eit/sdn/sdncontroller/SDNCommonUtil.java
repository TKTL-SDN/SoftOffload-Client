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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.util.Log;

/**
 * Here are some common functions which are always used in different
 * activities and services
 * 
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class SDNCommonUtil {

    public static String LOG_TAG = "eitsdncontroller";
    
    
    /**
     * Returns the 32bit dotted format of the provided long ip.
     *
     * @param ip the int ip in little-endian
     * @return the 32bit dotted format of <code>ip</code>
     * @throws IllegalArgumentException if <code>ip</code> is invalid
     */
    public static String littleEndianIntToIpAddress(int ip) {
        // if ip is smaller than 0.0.0.0
        if (ip < 0) {
            throw new IllegalArgumentException("invalid ip");
        }
        StringBuilder ipAddress = new StringBuilder();
        for (int i = 0; i <= 3; i++) {
            int shift = i * 8;
            ipAddress.append((ip & (0xff << shift)) >> shift);
            if (i < 3) {
                ipAddress.append(".");
            }
        }
        return ipAddress.toString();
    }

    /* Checks if external storage is available for read and write */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public static void writeToExternalFile(String data, String logTag, String fileName) {

        if (!isExternalStorageWritable()) {
            Log.e(logTag, "failed to find external storage");
        } else {
            File path = Environment.getExternalStorageDirectory();
            File dir = new File(path.getAbsolutePath() + "/SDNController");
            if(!dir.isDirectory()) {
                if (!dir.mkdirs()) {
                    Log.e(logTag, "sdn directory can not be created");
                    return;
                }
            }

            File file = new File(dir, fileName);
            try {
                FileOutputStream f = new FileOutputStream(file, true);
                PrintWriter pw = new PrintWriter(f);
                pw.println(data);
                pw.flush();
                pw.close();
                f.close();
            } catch (FileNotFoundException e) {
                Log.e(logTag, "can not find indicated file");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(logTag, "failed to write SDNController/result.txt");
                e.printStackTrace();
            }
        }
    }

    public static void removeExternalFile(String path, String logTag) {
        File file = new File(path);
        if (file.exists()) {
            boolean isDeleted = file.delete();
            if (!isDeleted) {
                Log.e(logTag, "failed to delete " + path);
            }
        }
    }


    /**
     * Check whether wifi is enabled
     *
     * @param activity context
     */
    public static boolean isWifiEnabled(Context c) {
        WifiManager wifiManager = (WifiManager)c.getSystemService(Context.WIFI_SERVICE);

        return (wifiManager.isWifiEnabled());
    }

    
    /**
     * Check whether wifi is enabled and connected
     *
     * @param activity context
     */
    public static boolean isOnline(Context c) {
        ConnectivityManager connMgr = (ConnectivityManager)
                c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }
}
