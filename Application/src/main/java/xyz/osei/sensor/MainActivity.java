
package xyz.osei.sensor;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
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
    private SensorRecorder recorder;
    private TextView text, errorText;
    private boolean listening = false;
    private boolean error = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        recorder = new SensorRecorder(this, new Sender.Supplier() {
            @Override
            public Sender get() throws Exception {
                //return new SocketSender("osei.xyz", 9000);

                //String fn = "sensor-log-" + fileTimestamp() + ".jsonl";
                //return new FileSender(new File(getApplicationContext().getFilesDir(), fn));

                return new NullSender();
            }
        });

        Button button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recorder.recordButton();
            }
        });

        text = (TextView)findViewById(R.id.counterText);
        errorText = (TextView)findViewById(R.id.errorText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startListening();
    }

    private void startListening() {
        if (listening || error) return;
        listening = true;

        sensorManager.registerListener(recorder,
                sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE),
                SensorManager.SENSOR_DELAY_FASTEST);

    }

    private void stopListening() {
        if (!listening) return;
        listening = false;

        sensorManager.unregisterListener(recorder);
        stopListening();
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
}
