package xyz.osei.sensor;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import xyz.osei.sensor.senders.*;

public class MainActivity extends Activity implements SensorRecorder.Listener {

    private SensorManager sensorManager;
    private TelephonyManager telephonyManager;
    private LocationManager locationManager;

    private SensorRecorder recorder;
    private TextView text, errorText;
    private boolean listening = false;
    private boolean hasPermissions = false;
    private boolean error = false;

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_LOCATION = 666;

    class SignalStrengthListener extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength s) {
            recorder.onCellInfoChanged(telephonyManager.getAllCellInfo());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_LOCATION);
        } else {
            hasPermissions = true;
        }

        recorder = new SensorRecorder(this, new Sender.Supplier() {
            @Override
            public Sender get() throws Exception {
                //return new SocketSender("osei.xyz", 9000);

                String fn = "sensor-log-" + fileTimestamp() + ".jsonl";
                return new FileSender(new File(getApplicationContext().getFilesDir(), fn));

                //return new NullSender();
            }
        });

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recorder.recordButton();
            }
        });

        text = (TextView) findViewById(R.id.counterText);
        errorText = (TextView) findViewById(R.id.errorText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startListening();
    }

    private int[] LISTENED_SENSORS = {
            //Sensor.TYPE_PRESSURE
    };

    private void startListening() {
        if (listening || error || !hasPermissions) return;
        listening = true;

        for (int sensor : LISTENED_SENSORS) {
            sensorManager.registerListener(recorder,
                    sensorManager.getDefaultSensor(sensor),
                    SensorManager.SENSOR_DELAY_FASTEST);
        }

        telephonyManager.listen(recorder, PhoneStateListener.LISTEN_CELL_INFO);

        long MIN_TIME = 10 * 1000;
        float MIN_DISTANCE = 100;

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME, MIN_DISTANCE, recorder);
        telephonyManager.listen(new SignalStrengthListener(), PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        recorder.onCellInfoChanged(telephonyManager.getAllCellInfo());
    }

    private void stopListening() {
        if (!listening) return;
        listening = false;

        if (LISTENED_SENSORS.length > 0) sensorManager.unregisterListener(recorder);
        telephonyManager.listen(recorder, PhoneStateListener.LISTEN_NONE);
    }

    private static String fileTimestamp() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
        df.setTimeZone(tz);
        return df.format(new Date());
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private long prevKb = 0;

    @Override
    public void onEvent(long nEvents, long nBytes) {
        long nKb = nBytes / 1024;
        if (nKb != prevKb) {
            text.setText(nKb+" kB / "+nEvents+" events");
            prevKb = nKb;
        }
    }

    @Override
    public void onError(Throwable err) {
        error = true;
        stopListening();

        errorText.setText(err.getMessage());
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    hasPermissions = true;
                    startListening();

                } else {
                    onError(new RuntimeException("permission denied"));
                }
                return;
            }
        }
    }
}
