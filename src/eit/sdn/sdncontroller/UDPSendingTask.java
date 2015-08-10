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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import android.os.AsyncTask;
import android.util.Log;

/**
 * @author Yanhe Liu (yanhe.liu@cs.helsinki.fi)
 *
 */
/**
 * AsyncTask class for sending udp packets.
 *
 * Android requires to execute networking operations in a different
 * AsyncTask thread like this
 *
 */
class UDPSendingTask extends AsyncTask<Object, Void, Void> {
    String LOG_TAG = SDNCommonUtil.LOG_TAG;

    @Override
    protected Void doInBackground(Object... params) {
        String message = (String)params[0];
        InetAddress ip = (InetAddress)params[1];
        int port = (Integer)params[2];
        byte[] buf = message.getBytes();

        try {
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, ip, port);
            socket.send(packet);
            socket.close();
        } catch (SocketException e) {
            Log.e(LOG_TAG, "udp socket error");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to send udp packet");
            e.printStackTrace();
        }
        return null;
    }

}
