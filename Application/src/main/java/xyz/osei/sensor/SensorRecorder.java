package xyz.osei.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

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

    interface Sender {
        void sendEvent(String data) throws IOException;
    }

    private static class SocketSender implements Sender {

        private Socket socket;

        SocketSender() {
            try {
                InetSocketAddress address = new InetSocketAddress("osei.xyz", 9000);
                socket = new Socket(address.getAddress(), address.getPort());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void sendEvent(String data) throws IOException {
            socket.getOutputStream().write(data.getBytes());
        }
    }

    private class SensorSender implements Runnable {

        Throwable error = null;

        @Override
        public void run() {

            System.out.println("started sensor sender thread");
            try {
                Sender sender = new SocketSender();

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

    SensorRecorder(Listener listener) {
        this.listener = listener;
        this.sender = new SensorSender();
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
