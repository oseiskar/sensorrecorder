package xyz.osei.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import xyz.osei.sensor.senders.*;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.Context.SENSOR_SERVICE;
import static android.content.Context.TELEPHONY_SERVICE;

public class ListeningManager implements SensorRecorder.Listener {

    private SensorManager sensorManager;
    private TelephonyManager telephonyManager;
    private LocationManager locationManager;

    private MainActivity activity;

    private boolean listening = false;
    private boolean error = false;

    private SensorRecorder recorder;
    private SignalStrengthListener signalStrengthListener;

    private interface StartStop {
        void start();
        void stop();
    }

    private abstract class LocationRecorder implements LocationListener, StartStop {

        @Override
        public void onLocationChanged(Location location) {
            recorder.onLocationChanged(location);
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            recorder.onStatusChanged(s,i,bundle);

        }

        @Override
        public void onProviderEnabled(String s) {
            recorder.onProviderEnabled(s);
        }

        @Override
        public void onProviderDisabled(String s) {
            recorder.onProviderDisabled(s);
        }

        @Override
        public void stop() {
            locationManager.removeUpdates(this);
        }
    }

    private abstract class SensorListener implements SensorEventListener, StartStop {

        abstract protected int getSensorType();

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            recorder.onSensorChanged(sensorEvent);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
            recorder.onAccuracyChanged(sensor,i);
        }

        @Override
        public void start() {
            sensorManager.registerListener(this,
                    sensorManager.getDefaultSensor(getSensorType()),
                    SensorManager.SENSOR_DELAY_FASTEST);
        }

        @Override
        public void stop() {
            sensorManager.unregisterListener(this);
        }
    }

    private class GpsListener extends LocationRecorder {
        static final long MIN_TIME = 10 * 1000;
        static final float MIN_DISTANCE = 100;

        @Override
        public void start() {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME, MIN_DISTANCE, this);
        }
    }

    private class CellLocationListener extends LocationRecorder {

        @Override
        public void start() {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }
    }

    private class CellInfoListener implements StartStop {

        @Override
        public void start() {
            telephonyManager.listen(recorder, PhoneStateListener.LISTEN_CELL_INFO);
            telephonyManager.listen(signalStrengthListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            recorder.onCellInfoChanged(telephonyManager.getAllCellInfo());
        }

        @Override
        public void stop() {
            telephonyManager.listen(signalStrengthListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    private class BarometerListener extends SensorListener {

        @Override
        protected int getSensorType() {
            return Sensor.TYPE_PRESSURE;
        }
    }


    List<StartStop> sensorListeners;

    ListeningManager(MainActivity mainActivity) {

        System.out.println("new ListeningManager");
        this.activity = mainActivity;

        sensorManager = (SensorManager) activity.getSystemService(SENSOR_SERVICE);
        telephonyManager = (TelephonyManager) activity.getSystemService(TELEPHONY_SERVICE);
        locationManager = (LocationManager) activity.getSystemService(LOCATION_SERVICE);


        recorder = new SensorRecorder(this, new Sender.Supplier() {
            @Override
            public Sender get() throws Exception {
                //return new SocketSender("osei.xyz", 9000);

                String fn = "sensor-log-" + fileTimestamp() + ".jsonl";
                return new FileSender(new File(activity.getApplicationContext().getFilesDir(), fn));

                //return new NullSender();
            }
        });

        signalStrengthListener = new SignalStrengthListener();

        sensorListeners = new ArrayList<>();

        sensorListeners.add(new GpsListener());
        sensorListeners.add(new CellInfoListener());
        sensorListeners.add(new CellLocationListener());
        sensorListeners.add(new BarometerListener());
    }

    void setActivity(MainActivity newActivity) {
        if (newActivity != activity) {
            System.out.println("changed activity. finishing existing...");
            activity.finish();
        }
        activity = newActivity;
    }

    private class SignalStrengthListener extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength s) {
            recorder.onCellInfoChanged(telephonyManager.getAllCellInfo());
        }
    }

    void startListening() {
        if (listening || error) return;
        listening = true;
        System.out.println("starting listener...");

        for (StartStop listener : sensorListeners) listener.start();
    }

    void stopListening() {
        if (!listening) return;
        listening = false;
        System.out.println("stopping listener...");

        for (StartStop listener : sensorListeners) listener.stop();
    }

    @Override
    public void onEvent(long nEvents, long nBytes) {
        activity.onEvent(nEvents, nBytes);
    }

    @Override
    public void onError(Throwable err) {
        error = true;
        activity.onError(err);
    }

    public void recordButton() {
        recorder.recordButton();
    }

    private static String fileTimestamp() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
        df.setTimeZone(tz);
        return df.format(new Date());
    }
}
