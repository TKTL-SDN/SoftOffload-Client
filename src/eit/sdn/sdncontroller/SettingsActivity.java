package eit.sdn.sdncontroller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    // udp port
    private String UDP_PORT_KEY = "recv_udp_port";
    private String DEFAULT_UDP_PORT = "7755";
    // private String DEFAULT_SCAN_INTERVAL = "10";
    
    // downloading url
    private String DOWNLOADING_URL_KEY = "pref_downloading_url";
    private String DOWNLOADING_URL = "http://www.cs.helsinki.fi/group/eit-sdn/testing/tiny.tmp";
    private String urlPattern = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
    
    // others
    private String WIFI_SCAN_INTERVAL = "pref_wifi_scan_interval";
    private String CONNECTING_TEST_TIMEOUT = "pref_connecting_test_timeout";
    private String logTag = "SettingsActivity";

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        SharedPreferences pref = getPreferenceScreen().getSharedPreferences();

        ListPreference prefScanInterval  = (ListPreference) findPreference(WIFI_SCAN_INTERVAL);
        String interval = prefScanInterval.getValue();
        if (interval != null) {
            prefScanInterval.setSummary(interval + "s");
        }

        ListPreference prefConnectTimeout  = (ListPreference) findPreference(CONNECTING_TEST_TIMEOUT);
        String timeout = prefConnectTimeout.getValue();
        if (timeout != null) {
            prefConnectTimeout.setSummary(timeout + "ms");
        }

        // udp port
        EditTextPreference editTextPref = (EditTextPreference) findPreference(UDP_PORT_KEY);
        String portString = pref.getString(UDP_PORT_KEY, DEFAULT_UDP_PORT);
        int udpServerPort = Integer.parseInt(portString);
        if (1024 <= udpServerPort && udpServerPort <= 65535) {
            editTextPref.setSummary(portString);
        } else {
            editTextPref.setSummary(DEFAULT_UDP_PORT);

            // set back to default
            SharedPreferences settings = getSharedPreferences(UDP_PORT_KEY, MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(UDP_PORT_KEY, DEFAULT_UDP_PORT);
            editor.commit();
        }
        
        // url
        editTextPref = (EditTextPreference) findPreference(DOWNLOADING_URL_KEY);
        String url = pref.getString(DOWNLOADING_URL_KEY, DOWNLOADING_URL);
        
        Pattern r = Pattern.compile(urlPattern);
        Matcher m = r.matcher(url);
        if (m.find()) {
            editTextPref.setSummary(url);
        } else {
            editTextPref.setSummary(DOWNLOADING_URL);

            // set back to default
            SharedPreferences settings = getSharedPreferences(DOWNLOADING_URL_KEY, MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(DOWNLOADING_URL_KEY, DOWNLOADING_URL);
            editor.commit();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1) {
        @SuppressWarnings("deprecation")
        Preference pref = findPreference(arg1);

        if (pref instanceof EditTextPreference && arg1.equals(UDP_PORT_KEY)) {
            EditTextPreference text = (EditTextPreference) pref;

            String portString = text.getText();
            int udpServerPort = Integer.parseInt(portString);
            if (1024 <= udpServerPort && udpServerPort <= 65535) {
                pref.setSummary(text.getText());
                Log.i(logTag, "set udp port number to " + text.getText());

                // check whether udp server is running
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                if (preferences.getBoolean("sdnSwitch", false)) {
                    Intent udpListeningIntent = new Intent(this, UDPListeningService.class);

                    Log.d(logTag, "try to stop UDP server for resetting port number");
                    stopService(udpListeningIntent);
                    SystemClock.sleep(1000);
                    startService(udpListeningIntent);
                }

            } else {
                CharSequence message = "illegal port number, set back to default!";
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(this, message, duration);
                toast.show();

                // set back to default
                SharedPreferences settings = getSharedPreferences(UDP_PORT_KEY, MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(UDP_PORT_KEY, DEFAULT_UDP_PORT);
                editor.commit();
                pref.setSummary(DEFAULT_UDP_PORT);
            }
        
        } else if (pref instanceof EditTextPreference && arg1.equals(DOWNLOADING_URL_KEY)) {
            EditTextPreference text = (EditTextPreference) pref;

            String url = text.getText();
            Pattern r = Pattern.compile(urlPattern);
            Matcher m = r.matcher(url);
            if (m.find()) {
                pref.setSummary(text.getText());
                Log.i(logTag, "set downloading url to " + text.getText());
            } else {
                CharSequence message = "illegal url, set back to default!";
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(this, message, duration);
                toast.show();

                // set back to default
                SharedPreferences settings = getSharedPreferences(DOWNLOADING_URL_KEY, MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(DOWNLOADING_URL_KEY, DOWNLOADING_URL);
                editor.commit();
                pref.setSummary(DOWNLOADING_URL);
            }
            
        } else if (arg1.equals(WIFI_SCAN_INTERVAL)) {
            ListPreference prefScanInterval = (ListPreference) pref;

            String interval = prefScanInterval.getValue();
            prefScanInterval.setSummary(interval + "s");
        } else if (arg1.equals(CONNECTING_TEST_TIMEOUT)) {
            ListPreference prefConnectTimeout  = (ListPreference) pref;
            String timeout = prefConnectTimeout.getValue();
            prefConnectTimeout.setSummary(timeout + "ms");
        } else {
            CharSequence text = "New settings will take effect after restarting the service";
            int duration = Toast.LENGTH_LONG;

            Toast toast = Toast.makeText(this, text, duration);
            toast.show();
        }
    }



}
