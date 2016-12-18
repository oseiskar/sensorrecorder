package xyz.osei.sensor;

import android.hardware.SensorManager;
import android.location.LocationManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    private int[] LISTENED_SENSORS = {
            //Sensor.TYPE_PRESSURE
    };

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
    }

    void setActivity(MainActivity newActivity) {
        if (newActivity != activity) {
            System.out.println("changed activity. finishing existing...");
            activity.finish();
        }
        activity = newActivity;
    }

    class SignalStrengthListener extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength s) {
            recorder.onCellInfoChanged(telephonyManager.getAllCellInfo());
        }
    }

    void startListening() {
        if (listening || error) return;
        listening = true;
        System.out.println("starting listener...");

        for (int sensor : LISTENED_SENSORS) {
            sensorManager.registerListener(recorder,
                    sensorManager.getDefaultSensor(sensor),
                    SensorManager.SENSOR_DELAY_FASTEST);
        }

        telephonyManager.listen(recorder, PhoneStateListener.LISTEN_CELL_INFO);

        long MIN_TIME = 10 * 1000;
        float MIN_DISTANCE = 100;

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME, MIN_DISTANCE, recorder);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, recorder);
        telephonyManager.listen(signalStrengthListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        recorder.onCellInfoChanged(telephonyManager.getAllCellInfo());
    }

    void stopListening() {
        if (!listening) return;
        listening = false;
        System.out.println("stopping listener...");

        if (LISTENED_SENSORS.length > 0) sensorManager.unregisterListener(recorder);
        locationManager.removeUpdates(recorder);
        telephonyManager.listen(recorder, PhoneStateListener.LISTEN_NONE);
        telephonyManager.listen(signalStrengthListener, PhoneStateListener.LISTEN_NONE);

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
