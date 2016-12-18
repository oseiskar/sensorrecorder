package xyz.osei.sensor.senders;

import java.io.IOException;

public class NullSender implements Sender {
    @Override
    public void sendEvent(String data) throws IOException {
    }
}
