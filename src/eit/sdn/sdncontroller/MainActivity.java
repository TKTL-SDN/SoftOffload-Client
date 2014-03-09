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


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

/**
 * This is the Android client programme for EIT-SDN project
 *
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 **/

public class MainActivity extends Activity {

    private Intent udpListeningIntent;
    private Switch sdnSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        udpListeningIntent = null;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sdnSwitch = (Switch) findViewById(R.id.switch_sdn);
        sdnSwitch.setChecked(false);
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

        // Is the switch on?
        boolean on = ((Switch) view).isChecked();

        if (on) {
            Log.d("Main", "switch is checked on");

            // Is mobile connected to some network?
            if (!isOnline()) { // no connection, show warning messages
                Context context = getApplicationContext();
                CharSequence text = "This device is not connected to any " +
                                    "network, SDN controller can not really work";
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }

            startService(udpListeningIntent);
        } else {
            Log.d("Main", "switch is checked off");
            stopService(udpListeningIntent);
            udpListeningIntent = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_settings:
                // openSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStart(){
        super.onStart();

        // get previous state of the switch
        sdnSwitch.setChecked(getPreference("sdnSwitch", this));
    }


    @Override
    public void onStop(){
        super.onStop();

        // save current state of the switch
        setPreference("sdnSwitch", sdnSwitch.isChecked(), this);
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
    private static void setPreference(String key, Boolean value, Context context)
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
    private static Boolean getPreference(String key, Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(key, true);
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


}
