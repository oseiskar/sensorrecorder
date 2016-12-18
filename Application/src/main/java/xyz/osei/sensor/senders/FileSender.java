package xyz.osei.sensor.senders;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileSender implements Sender {

    private OutputStream ostream;

    public FileSender(java.io.File file) throws IOException {
        System.out.println("saving to "+file.getAbsolutePath());
        ostream = new FileOutputStream(file);
    }

    @Override
    public void sendEvent(String data) throws IOException {
        ostream.write(data.getBytes());
    }
}
