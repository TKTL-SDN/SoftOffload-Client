/**
 *
 */
package eit.sdn.sdncontroller;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import android.os.AsyncTask;
import android.util.Log;

/**
 * @author yfliu
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
    String LOG_TAG = "UDPSendingTask";

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
