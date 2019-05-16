package xyz.osei.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.PhoneStateListener;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import xyz.osei.sensor.senders.Sender;

public class SensorRecorder extends PhoneStateListener implements SensorEventListener, LocationListener {

    interface Listener {
        void onEvent(long nEvents, long nBytes);
        void onError(Throwable err);
    }

    private long nEvents = 0;
    private long nBytes = 0;
    private Listener listener;

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
                    //System.out.println("sending " + data + " with backlog of "+eventQueue.size());
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
        dto.timestamp = System.currentTimeMillis();
        recordJsonEvent(dto);
    }

    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    public static class JsonSensorEvent {
        public String sensor;
        public long timestamp;
        public float[] values;
        public Float value;

        public static class CellInfo {
            public String id;
            public int rssi;
        }

        public static class Location {
            public double latitude;
            public double longitude;
            public float accuracy;
            public float speed;
            public String provider;
        }

        public List<CellInfo> cellInfo;
        public Location location;
    }

    private static boolean isScalar(int sensorType) {
        switch (sensorType) {
            case Sensor.TYPE_PRESSURE:
                return true;
        }
        return false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        JsonSensorEvent dto = new JsonSensorEvent();

        dto.timestamp = event.timestamp;
        dto.sensor = event.sensor.getStringType();

        if (isScalar(event.sensor.getType())) {
            dto.value = event.values[0];
        } else {
            dto.values = event.values;
        }

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

    @Override
    public void onCellInfoChanged(List<CellInfo> cellInfoList) {

        if (cellInfoList == null) return;

        JsonSensorEvent ev = new JsonSensorEvent();

        ev.timestamp = System.currentTimeMillis();

        ev.cellInfo = new ArrayList<>();
        for (CellInfo cellInfo : cellInfoList) {
            JsonSensorEvent.CellInfo jsonInfo = new JsonSensorEvent.CellInfo();

            if (cellInfo instanceof CellInfoLte) {
                CellInfoLte cell = (CellInfoLte)cellInfo;
                jsonInfo.rssi = cell.getCellSignalStrength().getDbm();
                jsonInfo.id = cell.getCellIdentity().toString();
            }
            else if (cellInfo instanceof CellInfoCdma) {
                CellInfoCdma cell = (CellInfoCdma)cellInfo;
                jsonInfo.rssi = cell.getCellSignalStrength().getDbm();
                jsonInfo.id = cell.getCellIdentity().toString();
            }
            else if (cellInfo instanceof CellInfoGsm) {
                CellInfoGsm cell = (CellInfoGsm)cellInfo;
                jsonInfo.rssi = cell.getCellSignalStrength().getDbm();
                jsonInfo.id = cell.getCellIdentity().toString();
            }
            else if (cellInfo instanceof CellInfoWcdma) {
                CellInfoWcdma cell = (CellInfoWcdma)cellInfo;
                jsonInfo.rssi = cell.getCellSignalStrength().getDbm();
                jsonInfo.id = cell.getCellIdentity().toString();
            }
            else {
                System.err.println("unrecognized cell info");
                continue;
            }
            ev.cellInfo.add(jsonInfo);
        }

        recordJsonEvent(ev);
    }

    @Override
    public void onLocationChanged(Location location) {

        JsonSensorEvent ev = new JsonSensorEvent();
        ev.timestamp = location.getTime();
        ev.location = new JsonSensorEvent.Location();
        ev.location.accuracy = location.getAccuracy();
        ev.location.latitude = location.getLatitude();
        ev.location.longitude = location.getLongitude();
        ev.location.provider = location.getProvider();
        ev.location.speed = location.getSpeed();

        recordJsonEvent(ev);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {
    }

    @Override
    public void onProviderDisabled(String s) {
    }
}
