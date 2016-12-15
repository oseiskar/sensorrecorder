package xyz.osei.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import org.codehaus.jackson.map.ObjectMapper;

public class SensorRecorder implements SensorEventListener {

    interface Listener {
        void onEvent(long nEvents, long nBytes);
    }

    private long nEvents = 0;
    private long nBytes = 0;
    private Listener listener;
    private long lastTimestamp = 0;

    private ObjectMapper objectMapper = new ObjectMapper();

    SensorRecorder(Listener listener) {
        this.listener = listener;
    }

    public void recordButton() {
        JsonSensorEvent dto = new JsonSensorEvent();
        dto.sensor = "button";
        dto.timestamp = lastTimestamp;
        recordJsonEvent(dto);
    }

    private static class JsonSensorEvent {
        String sensor;
        long timestamp;
        float[] values;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        JsonSensorEvent dto = new JsonSensorEvent();
        dto.sensor = event.sensor.getStringType();
        dto.values = event.values;
        dto.timestamp = event.timestamp;

        this.lastTimestamp = event.timestamp;

        recordJsonEvent(dto);
    }

    private void recordJsonEvent(JsonSensorEvent ev) {
        nEvents++;
        try {
            String serialized = objectMapper.writeValueAsString(ev);
            nBytes += serialized.length() + 1;

            System.out.println(serialized);

            listener.onEvent(nEvents, nBytes);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}
}
