package xyz.osei.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import org.codehaus.jackson.map.ObjectMapper;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import xyz.osei.sensor.senders.Sender;

public class SensorRecorder implements SensorEventListener {

    interface Listener {
        void onEvent(long nEvents, long nBytes);
        void onError(Throwable err);
    }

    private long nEvents = 0;
    private long nBytes = 0;
    private Listener listener;
    private long lastTimestamp = 0;

    private ObjectMapper objectMapper = new ObjectMapper();
    private BlockingDeque<String> eventQueue = new LinkedBlockingDeque<>();

    private class SensorSender implements Runnable {

        Throwable error = null;
        private Sender.Supplier senderSupplier;

        SensorSender(Sender.Supplier senderSupplier) {
            this.senderSupplier = senderSupplier;
        }

        @Override
        public void run() {

            System.out.println("started sensor sender thread");
            try {
                Sender sender = senderSupplier.get();

                while (true) {
                    String data = eventQueue.takeFirst();
                    System.out.println("sending " + data + " with backlog of "+eventQueue.size());
                    sender.sendEvent(data + "\n");
                }
            } catch (InterruptedException e) {
                System.out.println("sensor sender thread interrupted");
            } catch (Exception e) {
                this.error = e;
            }
        }
    }

    private SensorSender sender;

    SensorRecorder(Listener listener, Sender.Supplier senderSupplier) {
        this.listener = listener;
        this.sender = new SensorSender(senderSupplier);
        new Thread(sender).start();
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

        if (sender.error != null) {
            listener.onError(sender.error);
            return;
        }

        nEvents++;
        try {
            String serialized = objectMapper.writeValueAsString(ev);
            nBytes += serialized.length() + 1;
            eventQueue.addLast(serialized);

            listener.onEvent(nEvents, nBytes);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}
}
