package xyz.osei.sensor.senders;

import java.io.IOException;

public interface Sender {
    void sendEvent(String data) throws IOException;

    interface Supplier {
        Sender get() throws Exception;
    }
}
