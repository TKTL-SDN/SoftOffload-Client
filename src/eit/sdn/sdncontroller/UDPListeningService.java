/**
*    Copyright 2013 University of Helsinki
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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

/**
 * This class is used for mobile deveice to listen to the UDP messages from
 * SDN AP agent, and react to them
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 **/

public class UDPListeningService extends IntentService {

    private boolean isEnabled = true;
    private DatagramSocket socket = null;
    private String logTag = "UDPListeningService";
    private int preNetId;
    private ConnectivityChangeReceiver connChangeReceiver; // used for debug

    // some defaults
    private int MAX_BUF_LEN = 1024;
    private int UDP_SERVER_PORT = 7755;
    private String TOKEN = "\\|";
    private long DELAY_TIME_MS = 12000;
    private int DELAY_TIMES = 2;
    // Message types
    private final String MSG_SHOW = "show";
    private final String MSG_SWITCH = "switch";

    // broadcast receiver for network connection info
    private class ConnectivityChangeReceiver extends BroadcastReceiver {
        private String logTag = "UDPListeningService";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(logTag, "action: " + intent.getAction());
            Log.d(logTag, "component: " + intent.getComponent());

            Bundle extras = intent.getExtras();
            if (extras != null) {
                for (String key: extras.keySet()) {
                    Log.d(logTag, "key [" + key + "]: " + extras.get(key));
                }
            } else {
                Log.d(logTag, "no extras");
            }
        }
    }



    /**
     * A required constructor
     */
    public UDPListeningService() {
        super("UDPListeningService");
    }

    @SuppressLint("DefaultLocale")
    @Override
    protected void onHandleIntent(Intent arg0) {
        String message;
        byte[] recvBuf = new byte[MAX_BUF_LEN];

        isEnabled = true;
        // connChangeReceiver = new ConnectivityChangeReceiver();
        // registerReceiver(connChangeReceiver,
                // new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));


        try {
            socket = new DatagramSocket(UDP_SERVER_PORT);
            Log.i("UDPListeningService", "UDP receiver started");

            while (isEnabled) {
                DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(packet);

                // get packet data with specific length from recvBuf
                message = new String(packet.getData(), packet.getOffset(),
                                    packet.getLength()).trim();
                Log.d("UDPListeningService", "received packet: " + message);

                String[] fields = message.split(TOKEN);
                String msg_type = fields[0].toLowerCase();

                if (msg_type.equals(MSG_SWITCH)) { // switch to another access point
                    wifiSwitch(fields);

                } else if (msg_type.equals(MSG_SHOW)) { // using for debug
                    Log.d("UDPListeningService", "list wifi");
                }

            }

            socket.close();

        // FIXME current thread termination may cause socket exception
        // now we just ignore it
        } catch (SocketException e) {
            Log.w("UDPListeningService", e.toString());
            // e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    public void stopListening() {
        isEnabled = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    @Override
    public void onDestroy() {
        stopListening();
        // unregisterReceiver(connChangeReceiver);
        Log.d("UDPListeningService", "UDP receiver successfully stopped.");
        super.onDestroy();
    }

    /**
     * switch wifi connection to a specific one defined in the received message
     * The management pkt should be like this:
     * switch|ssid|auth_alg|passwd|wep_options_to_be_added...
     *
     * TODO Now WEP configuration is still missing
     *
     * @param fields the splitted udp message
     */
    private void wifiSwitch(String[] fields) {

        if (!fields[1].equals("")) {

            WifiManager wifiManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            preNetId = wifiInfo.getNetworkId();

            if (wifiInfo.getSSID().equals(fields[1])) {
                Log.i("UDPListeningService", "same ssid to current one, ignore the request");
            } else {
                // find corresponding config
                List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                for( WifiConfiguration i : list ) {
                    // Log.d("test", i.SSID);
                    if(i.SSID != null && i.SSID.equals(fields[1])) {
                        Log.d("UDPListeningService", "find existing config for ssid: " + fields[1]);
                        connectWifiNetwork(wifiManager, i);
                        return;
                    }
                }


                // TODO this part of logic is not complete at all
                // not find existing config for the new ssid
                Log.d("UDPListeningService", "create new config for ssid: " + fields[1]);
                WifiConfiguration conf = new WifiConfiguration();
                conf.SSID = "\"" + fields[1] + "\"";
                if (fields[2].equals("open")) {
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                } else if (fields[2].equals("wep")) {
                    // TODO add WEP condition
                } else if (fields[2].equals("wpa") && !fields[3].equals("")) {
                    conf.preSharedKey = "\""+ fields[3] +"\"";
                } else {
                    Log.w("UDPListeningService", "illegal mgt packet, ignore it");
                    return;
                }


                wifiManager.addNetwork(conf);
                Log.d("test", "created new config successfully");
                list = wifiManager.getConfiguredNetworks();
                for( WifiConfiguration i : list ) {
                    // Log.d("test", i.SSID);
                    if(i.SSID != null && i.SSID.equals("\"" + fields[1] + "\"")) {
                        connectWifiNetwork(wifiManager, i);
                        break;
                    }
                }
            }
        } else {
            Log.w("UDPListeningService", "Invalid SSID, ignore it");
        }
    }

    private void connectWifiNetwork(WifiManager wifiManager, WifiConfiguration config) {
        ConnectivityManager connManager = (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);

        wifiManager.disconnect();
        wifiManager.enableNetwork(config.networkId, true);
        wifiManager.reconnect();

        for (int i = 0; i < DELAY_TIMES; i++) {
            SystemClock.sleep(DELAY_TIME_MS);
            NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (networkInfo != null && networkInfo.isConnected()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo.getSSID().equals(config.SSID)) {
                    Log.i(logTag, "connected to new network: " + config.SSID);
                    return;
                }
            }
        }
        Log.w(logTag, "can not connect to new network: " + config.SSID);
        Log.i(logTag, "try to connect back to previous network");
        wifiManager.disableNetwork(config.networkId);
        wifiManager.removeNetwork(config.networkId);

        wifiManager.disconnect();
        wifiManager.enableNetwork(preNetId, true);
        wifiManager.reconnect();

        // FIXME If device fails to connect back to the previous network, it
        // will be off-line. However, not we just ignore this kind of condition
    }


}
