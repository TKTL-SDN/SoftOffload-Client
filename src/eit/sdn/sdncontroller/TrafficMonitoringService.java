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

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.TrafficStats;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.util.Log;

/**
 * only used for experiments
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class TrafficMonitoringService extends Service {
    private Timer monitoringTimer = new Timer();
    private String logTag = "TrafficMonitoringService";
    // private Context context;

    private String LOG_TAG = "TrafficMonitoringService";

    private class MonitoringTask extends TimerTask {
        private boolean isFirstTimeRunning = true;
        private long startRxBytes;
        private long startTxBytes;

        @Override
        public void run() {
            if (isFirstTimeRunning) {
                startRxBytes = TrafficStats.getTotalRxBytes();
                startTxBytes = TrafficStats.getTotalTxBytes();
                isFirstTimeRunning = false;
            } else {
                long endRxBytes = TrafficStats.getTotalRxBytes();
                long endTxBytes = TrafficStats.getTotalTxBytes();
                long rxBytes = endRxBytes - startRxBytes;
                long txBytes = endTxBytes - startTxBytes;

                startRxBytes = endRxBytes;
                startTxBytes = endTxBytes;
                Log.d(logTag, "mobile recieve rx bytes: " + rxBytes);
                Log.d(logTag, "mobile recieve tx bytes: " + txBytes);
            }
        }
   }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    public void onCreate() {
        super.onCreate();

        // context = this;
        Log.d(logTag, "traffic monitoring service started");
        monitoringTimer.schedule(new MonitoringTask(), 0, 1000);
    }

    public void onDestroy() {
        monitoringTimer.cancel();
        monitoringTimer.purge();
        Log.d(logTag, "traffic monitoring service stopped");
        super.onDestroy();
    }

}
